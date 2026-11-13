// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import com.dishanhai.sh_better_ui.kernel.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Physical Screen identity shared by input, focus, rendering and scoped module mounts. */
final class ClientScreens implements UIPlatform, ScreenService {
    private static final UiId SCREEN_ID = UiId.parse("sh_better_ui:native/screen");
    private static final UiId SESSION_ID = UiId.parse("sh_better_ui:native/session");
    private final KernelHost host;
    private final ModuleDescriptor.Platform platform;
    private final Map<UiScreen, ModuleContext> composed = new WeakHashMap<>();
    private volatile Viewport viewport = new Viewport(0, 0, 1, false);
    private volatile ScopeHandle session;
    private Object connection;
    private Screen screen;
    private Screen closing;
    private ScopeHandle scope;
    private long change;

    ClientScreens(KernelHost host, ModuleDescriptor.Platform platform) {
        this.host = host;
        this.platform = platform;
    }

    @Override public ModuleDescriptor.Platform info() { return platform; }
    @Override public Viewport viewport() { return viewport; }
    @Override public boolean isOnUIThread() { return host.isOnUIThread(); }
    @Override public boolean hasClientSession() {
        var current = session;
        return current != null && current.isOpen();
    }

    ScopeHandle activeScope() { return scope; }

    ScopeHandle activeScope(Screen owner) { return owner == screen ? scope : null; }

    boolean acceptsConnection(Object candidate) { return candidate != null && candidate == connection && hasClientSession(); }

    @Override public ScopeHandle session(ModuleContext module, UiId id) {
        host.assertOwner();
        if (!hasClientSession()) throw new IllegalStateException("No active native client session");
        return host.mountSession(module, session, id);
    }

    boolean ownsSession(ScopeLifecycle.Scope target) {
        return hasClientSession() && target.kind() == ScopeKind.CLIENT_SESSION && target.parent() == session;
    }

    ScopeHandle show(Screen next) {
        host.assertOwner();
        if (screen != next) {
            long revision = ++change;
            var previous = scope;
            screen = null;
            scope = null;
            if (previous != null) previous.close();
            if (revision != change) return scope;
            if (next != null) {
                var parent = session == null ? host.rootScope() : session;
                scope = parent.openChild(ScopeKind.SCREEN, SCREEN_ID);
                screen = next;
            }
        }
        return scope;
    }

    @Override
    public UiScreen create(ModuleContext module, UiScreenBuilder builder) {
        host.moduleAccess(module);
        var result = Objects.requireNonNull(builder).build();
        composed.put(result, module);
        return result;
    }

    @Override
    public ScopeHandle mount(ModuleContext module, Screen owner, UiId id) {
        host.moduleAccess(module);
        Objects.requireNonNull(owner);
        if (composed.get(owner) != module && !host.ownsNativeType(module, owner.getClass())) {
            throw new SecurityException("A UI module may only mount on a Screen from its own mod file");
        }
        var client = Minecraft.getInstance();
        if (client == null || NativePort.screen(client) != owner || closing == owner) {
            throw new IllegalStateException("A UI tree can only mount on the active native Screen");
        }
        show(owner);
        return mountTrusted(module, owner, id);
    }

    ScopeHandle mountTrusted(ModuleContext module, Screen owner, UiId id) {
        host.assertOwner();
        if (owner == null || owner != screen || scope == null) {
            throw new IllegalStateException("A UI tree can only mount on the active native Screen");
        }
        return host.mountTree(module, scope, id);
    }

    void sync(Minecraft client) {
        if (NativePort.screen(client) != closing || closing == null) {
            closing = null;
            show(NativePort.screen(client));
        }
        var window = client.getWindow();
        updateViewport(window.getGuiScaledWidth(), window.getGuiScaledHeight(), window.getGuiScale(),
                client.isWindowActive());
    }

    void initialized(Screen owner) {
        var client = Minecraft.getInstance();
        if (owner != NativePort.screen(client)) return;
        closing = null;
        sync(client);
    }

    void closing(Screen owner) {
        if (screen != owner) return;
        closing = owner;
        show(null);
    }

    void updateViewport(int width, int height, double scale, boolean focused) {
        var previous = viewport;
        if (previous.width() == width && previous.height() == height && previous.guiScale() == scale
                && previous.focused() == focused) return;
        viewport = new Viewport(width, height, scale, focused);
        if (scope != null && scope.isOpen()) host.notifyScreen(VIEWPORT_CHANGED, scope, viewport);
    }

    void login(Object nextConnection) {
        if (nextConnection == null || connection == nextConnection) return;
        logout(connection);
        connection = nextConnection;
        session = host.rootScope().openChild(ScopeKind.CLIENT_SESSION, SESSION_ID);
        show(null);
    }

    void logout(Object previousConnection) {
        if (connection == null || previousConnection != null && connection != previousConnection) return;
        var previous = session;
        session = null;
        connection = null;
        show(null);
        previous.close();
    }

    void clear() {
        screen = null;
        scope = null;
        closing = null;
        connection = null;
        session = null;
        composed.clear();
    }

    Screen ownerOf(ScopeLifecycle.Scope target) {
        return scope != null && scope.isOpen() && target.ancestor(ScopeKind.SCREEN) == scope ? screen : null;
    }
}
