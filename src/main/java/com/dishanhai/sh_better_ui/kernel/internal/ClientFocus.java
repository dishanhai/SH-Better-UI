// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.kernel.api.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

import java.util.Objects;
import java.util.Optional;

final class ClientFocus implements FocusService {
    private final KernelHost host;
    private final ClientScreens screens;
    private final ClientInput input;
    private Focus active;

    ClientFocus(KernelHost host, ClientScreens screens, ClientInput input) {
        this.host = host;
        this.screens = screens;
        this.input = input;
    }

    @Override
    public Optional<OwnershipLease> request(ModuleContext module, ScopeHandle node, UiId id,
                                            GuiEventListener widget, int priority) {
        var target = host.ownedNode(module, node);
        var screen = requireScreen(target);
        Objects.requireNonNull(widget);
        if (!focusable(screen, widget) || covered(screen, widget)) return Optional.empty();
        reconcile();
        var focus = new Focus(target, screen, widget);
        var claim = new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.KEYBOARD_FOCUS, id);
        focus.lease = host.acquire(host.moduleAccess(module),
                new ResourceRequest(claim, target, priority, 0, ConflictPolicy.REPLACE), focus::release);
        try {
            if (focus.apply()) return Optional.of(focus.lease);
            focus.lease.close();
            return Optional.empty();
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            focus.lease.close();
            throw failure;
        }
    }

    @Override
    public Subscription target(ModuleContext module, ScopeHandle node, GuiEventListener widget, int layer) {
        var screen = requireScreen(host.ownedNode(module, node));
        if (!screen.children().contains(Objects.requireNonNull(widget))) {
            throw new IllegalArgumentException("Focus input target is not a child of the active Screen");
        }
        return input.target(module, node, layer, event -> !(event instanceof NativeInput.Pointer)
                && screen.getFocused() == widget && focusable(screen, widget) && !covered(screen, widget));
    }

    void reconcile() {
        var current = active;
        if (current != null && !current.isCurrent()) current.lease.close();
    }

    void windowLost() {
        if (active != null) active.lease.close();
    }

    private Screen requireScreen(ScopeLifecycle.Scope target) {
        var screen = screens.ownerOf(target);
        if (screen == null) throw new IllegalStateException("Focus requires a mounted native UI tree");
        return screen;
    }

    private static boolean focusable(Screen owner, GuiEventListener widget) {
        return widget != null && owner.children().contains(widget) && (!(widget instanceof AbstractWidget value)
                || value.active && value.visible);
    }

    private static boolean covered(Screen screen, GuiEventListener target) {
        var overlay = UiOverlayHost.current(screen);
        if (overlay != null && overlay.isModalOpen()) return true;
        var local = UiLocalModal.current(screen);
        return local != null && target instanceof AbstractWidget widget
                && local.blocksPointer(widget.getX() + widget.getWidth() * 0.5,
                widget.getY() + widget.getHeight() * 0.5);
    }

    private final class Focus {
        final ScopeLifecycle.Scope target;
        final Screen screen;
        final GuiEventListener widget;
        GuiEventListener previous;
        OwnershipLease lease;

        Focus(ScopeLifecycle.Scope target, Screen screen, GuiEventListener widget) {
            this.target = target;
            this.screen = screen;
            this.widget = widget;
        }

        boolean apply() {
            if (!isMounted() || !focusable(screen, widget) || covered(screen, widget)) return false;
            previous = screen.getFocused();
            if (!isMounted()) return false;
            active = this;
            screen.setFocused(widget);
            return isCurrent() && active == this;
        }

        boolean isCurrent() {
            return isMounted() && screen.getFocused() == widget && focusable(screen, widget)
                    && !covered(screen, widget);
        }

        private boolean isMounted() {
            return lease.isActive() && screens.ownerOf(target) == screen;
        }

        void release() {
            if (active != this) return;
            active = null;
            if (screens.ownerOf(target) == screen && screen.getFocused() == widget && !covered(screen, widget)) {
                screen.setFocused(focusable(screen, previous) ? previous : null);
            }
        }
    }
}
