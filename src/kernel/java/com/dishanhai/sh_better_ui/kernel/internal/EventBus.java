// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class EventBus {
    private static final int MAX_DEPTH = 16;
    private static final int MAX_DISPATCHES = 256;
    private static final EventResult PASS = new EventResult(false, false, false, false, false, false);
    private static final Comparator<Listener> ORDER = Comparator.comparingInt(Listener::priority).reversed();

    private final ScopeLifecycle scopes;
    private final CentralScheduler scheduler;
    private final RuntimeStatistics metrics;
    private final ScopedSubscriptions<Address, Listener> listeners;
    private final BiConsumer<String, Throwable> failures;
    private int depth;
    private int delivered;

    EventBus(ScopeLifecycle scopes, CentralScheduler scheduler, MutationCoordinator mutations,
             BiConsumer<String, Throwable> failures, RuntimeStatistics metrics) {
        this.scopes = scopes;
        this.scheduler = scheduler;
        this.failures = failures;
        this.metrics = metrics;
        listeners = new ScopedSubscriptions<>(mutations::assertOutsideValidation, ORDER);
    }

    UIEventBus view(ModuleAccess access) {
        return new UIEventBus() {
            @Override
            public <T> Subscription subscribe(UIEventType<T> type, ScopeHandle scope, UIEventPhase phase, int priority,
                                              Consumer<UIEvent<T>> listener) {
                var issuer = access.requireIssuer();
                issuer.assertOwner();
                access.assertSideEffectAllowed();
                var target = access.write(scope);
                Objects.requireNonNull(type, "type");
                Objects.requireNonNull(phase, "phase");
                Objects.requireNonNull(listener, "listener");
                return listeners.add(new Address(type, target, phase), issuer, target,
                        new Listener(access, priority, (event, current, currentPhase) -> {
                            var delivery = new Delivery<>(access, type.type().cast(event.payload), event, current, currentPhase);
                            try {
                                listener.accept(delivery);
                            } finally {
                                delivery.active = false;
                            }
                        }));
            }

            @Override
            public <T> EventResult emit(UIEventType<T> type, ScopeHandle target, T payload) {
                var issuer = access.requireIssuer();
                issuer.assertOwner();
                access.assertSideEffectAllowed();
                return dispatch(issuer.owner(), type, access.write(target), payload, null);
            }

            @Override
            public <T> TaskHandle post(UIEventType<T> type, ScopeHandle target, T payload) {
                validatePayload(type, payload);
                return scheduler.submit(access, UITask.ui(target, UIPhase.EVENT_DISPATCH, UIPriority.NORMAL,
                        () -> emit(type, target, payload)));
            }
        };
    }

    <T> EventResult routeInput(UIEventType<T> type, ScopeHandle activeScreen, ScopeHandle target, T payload) {
        if (!scheduler.inPhase(UIPhase.INPUT)) {
            throw new IllegalStateException("Native UI input requires the INPUT phase");
        }
        if (activeScreen == null) {
            return PASS;
        }
        var screen = scopes.checkedScope(activeScreen);
        var destination = scopes.checkedScope(target);
        if (screen.kind() != ScopeKind.SCREEN || !screen.isOpen()
                || !destination.isOpen() || !destination.isWithin(screen)) {
            return PASS;
        }
        return dispatch(scopes.root().owner(), type, destination, payload, screen);
    }

    <T> void notifyScreen(UIEventType<T> type, ScopeLifecycle.Scope screen, T payload) {
        for (var tree : List.copyOf(screen.children())) {
            if (tree.isOpen()) dispatch(scopes.root().owner(), type, tree, payload, null);
        }
    }

    void remove(List<ScopeLifecycle.Scope> closing) {
        closing.forEach(listeners::remove);
    }

    void permissionsChanged(ModuleAccess access) {
        listeners.removeForIssuer(access.defaultScope(), target -> !access.allowsScope(target));
    }

    private EventResult dispatch(UiId source, UIEventType<?> type, ScopeLifecycle.Scope target, Object payload,
                                 ScopeLifecycle.Scope activeScreen) {
        validatePayload(type, payload);
        if (depth >= MAX_DEPTH || delivered >= MAX_DISPATCHES) {
            throw new IllegalStateException("UI event dispatch limit exceeded for " + type.id());
        }
        depth++;
        delivered++;
        try {
            var event = new Dispatch(source, type, target, payload, activeScreen);
            var path = new ArrayList<ScopeLifecycle.Scope>();
            for (var parent = target.parent(); parent != null; parent = parent.parent()) {
                path.add(parent);
            }
            for (int i = path.size() - 1; i >= 0 && event.propagates(); i--) {
                deliver(event, path.get(i), UIEventPhase.CAPTURE);
            }
            if (event.propagates()) {
                deliver(event, target, UIEventPhase.TARGET);
            }
            for (int i = 0; i < path.size() && event.propagates(); i++) {
                deliver(event, path.get(i), UIEventPhase.BUBBLE);
            }
            return event.result();
        } finally {
            if (--depth == 0) {
                delivered = 0;
            }
        }
    }

    private static void validatePayload(UIEventType<?> type, Object payload) {
        if (!type.type().isInstance(payload)) {
            throw new IllegalArgumentException("UI event " + type.id() + " requires " + type.type().getName());
        }
    }

    private void deliver(Dispatch event, ScopeLifecycle.Scope current, UIEventPhase phase) {
        var snapshot = listeners.forKey(new Address(event.type, current, phase));
        for (int i = 0; i < snapshot.size(); i++) {
            if (event.immediate || !event.target.isOpen()) {
                return;
            }
            var entry = snapshot.get(i);
            if (entry.isActive()) {
                Listener listener = entry.listener();
                if (listener.access().allowsScope(event.target)) {
                    try (var sample = metrics.begin(RuntimeMetrics.Category.EVENT)) {
                        listener.delivery().invoke(event, current, phase);
                    } catch (RuntimeException | LinkageError | AssertionError failure) {
                        metrics.failure(RuntimeMetrics.Category.EVENT);
                        failures.accept("UI event " + event.type.id() + " at " + current.id(), failure);
                    }
                }
            }
        }
    }

    private record Address(UIEventType<?> type, ScopeLifecycle.Scope scope, UIEventPhase phase) {}

    private record Listener(ModuleAccess access, int priority, Invoker delivery) {}

    @FunctionalInterface
    private interface Invoker {
        void invoke(Dispatch event, ScopeLifecycle.Scope current, UIEventPhase phase);
    }

    private static final class Dispatch {
        final UiId source;
        final UIEventType<?> type;
        final ScopeLifecycle.Scope target;
        final Object payload;
        final ScopeLifecycle.Scope activeScreen;
        boolean stopped;
        boolean immediate;
        boolean prevented;
        boolean handled;
        boolean consumed;
        ModuleAccess consumer;

        Dispatch(UiId source, UIEventType<?> type, ScopeLifecycle.Scope target, Object payload,
                 ScopeLifecycle.Scope activeScreen) {
            this.source = source;
            this.type = type;
            this.target = target;
            this.payload = payload;
            this.activeScreen = activeScreen;
        }

        boolean propagates() {
            return !stopped && target.isOpen();
        }

        EventResult result() {
            boolean block = consumer != null && activeScreen.isOpen() && type.cancelable()
                    && consumer.decision(UIPermission.BLOCK_VANILLA_INPUT, target).allowed();
            return new EventResult(stopped, immediate, prevented, handled, consumed, block);
        }
    }

    private static final class Delivery<T> implements UIEvent<T> {
        private final ModuleAccess access;
        private final T payload;
        private final Dispatch event;
        private final ScopeLifecycle.Scope current;
        private final UIEventPhase phase;
        private boolean active = true;

        Delivery(ModuleAccess access, T payload, Dispatch event, ScopeLifecycle.Scope current, UIEventPhase phase) {
            this.access = access;
            this.payload = payload;
            this.event = event;
            this.current = current;
            this.phase = phase;
        }

        @Override public T payload() { return payload; }
        @Override public UiId source() { return event.source; }
        @Override public ScopeHandle target() { return event.target.reference(); }
        @Override public ScopeHandle currentTarget() { return current.reference(); }
        @Override public UIEventPhase phase() { return phase; }

        @Override
        public void stopPropagation() {
            checkActive();
            event.stopped = true;
        }

        @Override
        public void stopImmediatePropagation() {
            stopPropagation();
            event.immediate = true;
        }

        @Override
        public void preventDefault() {
            checkActive();
            if (event.type.cancelable()) {
                event.prevented = true;
            }
        }

        @Override
        public void markHandled() {
            checkActive();
            event.handled = true;
        }

        @Override
        public void consume() {
            checkActive();
            if (event.activeScreen != null) {
                access.require(UIPermission.BLOCK_VANILLA_INPUT, event.target);
                event.consumer = access;
            }
            stopImmediatePropagation();
            event.consumed = true;
        }

        private void checkActive() {
            current.assertOwner();
            access.assertSideEffectAllowed();
            if (!active || !event.target.isOpen()) {
                throw new IllegalStateException("UI event delivery is closed");
            }
        }
    }
}
