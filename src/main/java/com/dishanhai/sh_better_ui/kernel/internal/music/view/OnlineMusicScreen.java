// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.*;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Objects;
import java.util.function.Supplier;

/** Ordinary user-facing player route. No developer catalogue, sample or gallery is consulted. */
public final class OnlineMusicScreen {
    private OnlineMusicScreen() {}

    public static boolean available() {
        return UnifiedUI.isAvailable() && UnifiedUI.get().find(OnlineMusicService.KEY).isPresent();
    }

    public static UiScreen create(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        return create(parent, returnFocus, () -> UnifiedUI.get().require(OnlineMusicService.KEY).view());
    }

    static UiScreen create(Screen parent, Supplier<? extends GuiEventListener> returnFocus,
                           Supplier<OnlineMusicView> factory) {
        var owner = new SessionOwner(factory);
        return ShBetterUiApi.screen("sh_better_ui.online_music.view.brand")
                .parent(parent).returnFocus(Objects.requireNonNull(returnFocus)).themeBackground()
                .onInit(screen -> {
                    try { owner.mount(new Context(screen, owner)); }
                    catch (ServiceUnavailable failure) { owner.close(); }
                })
                .onRender((screen, frame) -> {
                    if (owner.view != null) owner.view.render(frame);
                    else frame.graphics().drawString(Minecraft.getInstance().font,
                            Component.translatable("sh_better_ui.online_music.view.unavailable"),
                            frame.viewport().x() + 8, frame.viewport().y() + 12, UiTheme.current().text(), false);
                })
                .onForeground((screen, frame) -> { if (owner.overlays != null) owner.overlays.render(frame.graphics(), frame.mouse() == null ? -1 : (int)frame.mouse().x(), frame.mouse() == null ? -1 : (int)frame.mouse().y(), frame.partialTick()); })
                .onRemoved(screen -> owner.close()).build();
    }

    /** A resize remount keeps its session; removal closes it once, re-entry creates a fresh session. */
    static final class SessionOwner implements AutoCloseable {
        private final Supplier<OnlineMusicView> factory;
        private OnlineMusicView view;
        private UiOverlayHost overlays;
        SessionOwner(Supplier<OnlineMusicView> factory) { this.factory = Objects.requireNonNull(factory); }
        void mount(PreviewContext host) {
            if (view == null) {
                try { view = Objects.requireNonNull(factory.get(), "Missing online music view"); }
                catch (RuntimeException failure) { throw new ServiceUnavailable(failure); }
            }
            try { view.layout(host); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        @Override public void close() {
            var previous = view; view = null;
            try { if (previous != null) previous.close(); }
            finally { if (overlays != null) { overlays.ownerClosed(); overlays = null; } }
        }
    }

    private static final class ServiceUnavailable extends RuntimeException {
        ServiceUnavailable(RuntimeException cause) { super(cause); }
    }

    private static final class Context implements PreviewContext {
        private final UiScreen screen;
        private final SessionOwner owner;
        Context(UiScreen screen, SessionOwner owner) { this.screen = screen; this.owner = owner; }
        @Override public Screen screen() { return screen; }
        @Override public Bounds viewport() { return screen.viewport(); }
        @Override public Bounds toolbar() { return new Bounds(0, 0, screen.width, screen.viewport().y()); }
        @Override public Font font() { return Minecraft.getInstance().font; }
        @Override public UiPreferences preferences() { return screen.context().preferences(); }
        // The player currently has no modal overlays; callers still receive the shared native host.
        @Override public UiOverlayHost overlays() { if (owner.overlays == null) owner.overlays = new UiOverlayHost(screen, preferences()); return owner.overlays; }
        @Override public UiApiContext context() { return screen.context(); }
        @Override public boolean interactive() { return Minecraft.getInstance().screen == screen; }
        @Override public <T extends AbstractWidget> T add(T widget) { return screen.add(widget); }
        @Override public <T extends AbstractWidget> T addOverlay(T widget) { return screen.add(widget); }
    }
}
