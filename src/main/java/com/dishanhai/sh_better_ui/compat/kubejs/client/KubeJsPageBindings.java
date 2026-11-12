// Author: dishanhai
package com.dishanhai.sh_better_ui.compat.kubejs.client;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class KubeJsPageBindings {
    private static final Set<String> EVENTS = Set.of("pageOpened", "pageClosed", "actionResult", "dataChanged");
    private static final int MAX_GENERATION_ENTRIES = 128;
    private final Map<UiId, KubeJsPageSchema.Definition> definitions = new HashMap<>();
    private final Map<UUID, OpenPage> instances = new HashMap<>();
    private final List<Subscription> listeners = new ArrayList<>();
    private final Set<UiId> registered = new HashSet<>();
    private ScopeHandle scope;
    private volatile boolean retired;

    void register(String id, Map<?, ?> source) {
        var definition = KubeJsPageSchema.defineWithText(id, source);
        UiPageDefinition page = definition.page();
        synchronized (this) {
            requireActive();
            if (definitions.size() >= MAX_GENERATION_ENTRIES) {
                throw new IllegalStateException("Client script page registration limit reached");
            }
            if (definitions.putIfAbsent(page.id(), definition) != null) {
                throw new IllegalArgumentException("Client script page already registered: " + page.id());
            }
        }
    }

    CompletionStage<Boolean> open(String pageId, Map<?, ?> raw) {
        UiId id = KubeJsPageSchema.scriptId(pageId);
        KubeJsPageSchema.Definition definition;
        synchronized (this) {
            requireActive();
            definition = definitions.get(id);
        }
        if (definition == null) {
            throw new IllegalArgumentException("Unknown page in this client script generation: " + id);
        }
        Map<String, UiValue> data = KubeJsPageSchema.data(definition.page().dataSchema(), raw, true);
        return submit(() -> openOnClient(definition, data));
    }

    private CompletionStage<Boolean> openOnClient(KubeJsPageSchema.Definition configured, Map<String, UiValue> data) {
        var definition = configured.page();
        ScopeHandle owner = clientScope();
        if (owner == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (!registered.contains(definition.id())) {
            ClientUiKernel.registerScriptPage(owner, definition, configured.text());
            registered.add(definition.id());
        }
        OpenPage opened = new OpenPage(definition);
        PageService.Instance instance = ClientUiKernel.openScriptPage(owner, definition.id(), data, action -> {
            if (opened.instance != null) {
                emit("actionResult", opened.instance.snapshot(), action.toString());
            }
        });
        opened.instance = instance;
        UUID id = instance.snapshot().instanceId();
        boolean current;
        synchronized (this) {
            current = !retired;
            if (current) {
                instances.put(id, opened);
            }
        }
        if (!current) {
            instance.close();
            return CompletableFuture.completedFuture(false);
        }
        instance.closed().thenAccept(snapshot -> {
            synchronized (this) {
                instances.remove(id, opened);
            }
            emit("pageClosed", snapshot, "");
        });
        return instance.displayed().thenApply(displayed -> {
            if (displayed) {
                emit("pageOpened", instance.snapshot(), "");
            }
            return displayed && !retired;
        });
    }

    CompletionStage<Boolean> update(String instanceId, Map<?, ?> raw) {
        UUID id = UUID.fromString(instanceId);
        OpenPage page = instance(id);
        if (page == null) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, UiValue> values = KubeJsPageSchema.data(page.definition.dataSchema(), raw, false);
        return submit(() -> {
            ScopeHandle owner;
            synchronized (this) {
                owner = scope;
            }
            boolean updated = owner != null && owner.isOpen() && ClientUiKernel.updateScriptPage(owner, id, values);
            if (updated) {
                emit("dataChanged", page.instance.snapshot(), "");
            }
            return CompletableFuture.completedFuture(updated);
        });
    }

    CompletionStage<Boolean> closePage(String instanceId) {
        UUID id = UUID.fromString(instanceId);
        return submit(() -> {
            OpenPage page = instance(id);
            if (page != null) {
                page.instance.close();
            }
            return CompletableFuture.completedFuture(page != null);
        });
    }

    synchronized KubeJsUiApi.PageSubscription on(String type, Consumer<KubeJsPageEvent> listener) {
        requireActive();
        if (!EVENTS.contains(type)) {
            throw new IllegalArgumentException("Unknown client script page event: " + type);
        }
        if (listeners.size() >= MAX_GENERATION_ENTRIES) {
            throw new IllegalStateException("Client script page listener limit reached");
        }
        Subscription subscription = new Subscription(type, Objects.requireNonNull(listener, "listener"));
        listeners.add(subscription);
        return subscription;
    }

    /** Returns the detached scope for cleanup on the client owner thread. */
    synchronized ScopeHandle retire() {
        retired = true;
        definitions.clear();
        instances.clear();
        listeners.clear();
        ScopeHandle previous = scope;
        scope = null;
        return previous;
    }

    private ScopeHandle clientScope() {
        synchronized (this) {
            if (retired) {
                return null;
            }
            if (scope == null || !scope.isOpen()) {
                scope = ClientUiKernel.openScriptPageScope();
                registered.clear();
            }
            return scope;
        }
    }

    private synchronized OpenPage instance(UUID id) {
        return instances.get(id);
    }

    private CompletionStage<Boolean> submit(Supplier<CompletionStage<Boolean>> action) {
        CompletableFuture<CompletionStage<Boolean>> scheduled = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            if (retired) {
                scheduled.complete(CompletableFuture.completedFuture(false));
                return;
            }
            try {
                scheduled.complete(action.get());
            } catch (RuntimeException error) {
                scheduled.completeExceptionally(error);
            }
        });
        return scheduled.thenCompose(stage -> stage);
    }

    private void emit(String type, PageService.Snapshot snapshot, String action) {
        List<Subscription> targets;
        synchronized (this) {
            if (retired) {
                return;
            }
            targets = listeners.stream().filter(subscription -> subscription.type.equals(type)).toList();
        }
        if (targets.isEmpty()) {
            return;
        }
        Map<String, Object> data = scriptValues(snapshot.data());
        KubeJsPageEvent event = new KubeJsPageEvent(type, snapshot.pageId().toString(), snapshot.instanceId().toString(),
                action, snapshot.state().name(), snapshot.revision(), data);
        for (Subscription target : targets) {
            if (retired || !target.active) {
                continue;
            }
            try {
                target.listener.accept(event);
            } catch (RuntimeException error) {
                ShBetterUi.LOGGER.error("KubeJS page event callback failed", error);
            }
        }
    }

    private static Map<String, Object> scriptValues(Map<String, UiValue> values) {
        Map<String, Object> result = new HashMap<>();
        values.forEach((key, value) -> result.put(key, scriptValue(value)));
        return Map.copyOf(result);
    }

    private static Object scriptValue(UiValue value) {
        return switch (value.type()) {
            case BOOLEAN -> ((UiValue.Bool) value).value();
            case INTEGER -> ((UiValue.Int) value).value();
            case LONG -> ((UiValue.LongInt) value).value();
            case DOUBLE -> ((UiValue.Decimal) value).value();
            case STRING -> ((UiValue.Text) value).value();
            case ENUM -> ((UiValue.Choice) value).value();
            case LIST -> ((UiValue.Sequence) value).value().stream().map(KubeJsPageBindings::scriptValue).toList();
            case OBJECT -> scriptValues(((UiValue.ObjectValue) value).value());
            case ITEM_REFERENCE -> {
                UiValue.ItemReference item = (UiValue.ItemReference) value;
                yield Map.of("id", item.itemId().toString(), "count", item.count());
            }
        };
    }

    private void requireActive() {
        if (retired) {
            throw new IllegalStateException("KubeJS UI script generation is closed");
        }
    }

    private static final class OpenPage {
        private final UiPageDefinition definition;
        private PageService.Instance instance;

        private OpenPage(UiPageDefinition definition) {
            this.definition = definition;
        }
    }

    private final class Subscription implements KubeJsUiApi.PageSubscription {
        private final String type;
        private final Consumer<KubeJsPageEvent> listener;
        private volatile boolean active = true;

        private Subscription(String type, Consumer<KubeJsPageEvent> listener) {
            this.type = type;
            this.listener = listener;
        }

        @Override
        public void close() {
            synchronized (KubeJsPageBindings.this) {
                active = false;
                listeners.remove(this);
            }
        }
    }
}
