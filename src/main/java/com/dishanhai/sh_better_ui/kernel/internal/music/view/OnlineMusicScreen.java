// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.*;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.internal.music.ClientOnlineMusic;
import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationNavigation;
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
        return create(parent, returnFocus, () -> UnifiedUI.get().require(OnlineMusicService.KEY).backgroundView());
    }

    /** A notice opens the matching history row, not an account or result action. */
    public static UiScreen operation(Screen parent, MusicOperationNavigation.Target target) {
        if (!available() || !target.valid() || !(UnifiedUI.get().require(OnlineMusicService.KEY)
                instanceof ClientOnlineMusic music)) return null;
        return create(parent, () -> null, () -> music.backgroundOperationView(target).orElseThrow());
    }

    static UiScreen create(Screen parent, Supplier<? extends GuiEventListener> returnFocus,
                           Supplier<OnlineMusicView> factory) {
        var owner = new SessionOwner(factory);
        return ShBetterUiApi.screen("sh_better_ui.online_music.view.brand")
                .backButton(false).parent(parent).returnFocus(Objects.requireNonNull(returnFocus)).themeBackground()
                .onInit(screen -> {
                    try {
                        var exit = screen.add(new com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl(
                                new Bounds(8, 6, 24, 24), Component.translatable("gui.back"),
                                com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl.Glyph.BACK,
                                screen.context().preferences(), screen::onClose));
                        owner.mount(new Context(screen, owner, exit));
                        screen.onCloseRequest(() -> !(owner.view instanceof OnlineMusicViewImpl nativeView) || nativeView.permitsScreenClose());
                    }
                    catch (ServiceUnavailable failure) { owner.close(); }
                })
                .onRender((screen, frame) -> {
                    if (owner.view != null) owner.view.render(frame);
                    else frame.graphics().drawString(Minecraft.getInstance().font,
                            Component.translatable("sh_better_ui.online_music.view.unavailable"),
                            frame.viewport().x() + 8, frame.viewport().y() + 40, UiTheme.current().text(), false);
                })
                .onForeground((screen, frame) -> {
                    if (owner.view instanceof OnlineMusicViewImpl nativeView) nativeView.renderRouteHandoff(frame);
                    if (owner.overlays != null) owner.overlays.render(frame.graphics(), frame.mouse() == null ? -1 : (int)frame.mouse().x(), frame.mouse() == null ? -1 : (int)frame.mouse().y(), frame.partialTick());
                })
                .onRemoved(screen -> owner.close()).build();
    }

    /** A resize remount keeps its view; removal disposes it once. The default route borrows persistent playback. */
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

    static GuiEventListener restorePlaylistDiscovery(PreviewContext context, OnlineMusicViewImpl origin,
                                                      String source, String link) {
        if (!(context instanceof Context nativeContext)) return origin.restorePlaylistDiscovery(source, link);
        return nativeContext.owner.view instanceof OnlineMusicViewImpl next ? next.restorePlaylistDiscovery(source, link) : null;
    }

    static GuiEventListener restoreExternalSearch(PreviewContext context, OnlineMusicViewImpl origin,
                                                   OnlineMusicViewImpl.ExternalSearchBookmark bookmark, boolean madeForYou) {
        if (context instanceof Context nativeContext && nativeContext.owner.view instanceof OnlineMusicViewImpl next
                && next != origin) return next.restoreExternalSearch(bookmark, madeForYou);
        return origin.spotifyExternalControl(madeForYou);
    }

    /** The dedicated player owns its chrome; there is no native header over the artwork stage. */
    static GuiEventListener shellBack(PreviewContext context) {
        return null;
    }
    static com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl exitControl(PreviewContext context) {
        return context instanceof Context nativeContext ? nativeContext.exit : null;
    }
    static boolean supportsLyricsStack(PreviewContext context) { return context instanceof Context; }
    static <T extends AbstractWidget> T addLyricsLayer(PreviewContext context,T widget,Supplier<Bounds> clip) {
        return context instanceof Context nativeContext ? addLyricsLayer(nativeContext.screen,widget,clip) : context.add(widget);
    }
    static <T extends AbstractWidget> T addLyricsLayer(UiScreen screen,T widget,Supplier<Bounds> clip) {
        return screen.addClipped(widget,clip,"online-music-lyrics");
    }
    private static final class Context implements PreviewContext {
        private final UiScreen screen;
        private final SessionOwner owner;
        private final com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl exit;
        Context(UiScreen screen, SessionOwner owner, com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl exit) {
            this.screen = screen; this.owner = owner;
            this.exit = exit;
        }
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
