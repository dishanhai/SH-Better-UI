// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationNavigation;
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
    /** This native player route supplies the required after-widgets pass. */
    static boolean supportsMenuForeground(PreviewContext host){return host instanceof Context;}

    public static boolean available() {
        return UnifiedUI.isAvailable() && UnifiedUI.get().find(OnlineMusicService.KEY).isPresent();
    }

    public static UiScreen create(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        return create(parent, returnFocus, () -> UnifiedUI.get().require(OnlineMusicService.KEY).backgroundView());
    }

    /** A notice opens the matching history row, not an account or result action. */
    public static UiScreen operation(Screen parent, MusicOperationNavigation.Target target) {
        if (!available() || !target.valid() || !(UnifiedUI.get().require(OnlineMusicService.KEY)
                instanceof com.dishanhai.sh_better_ui.kernel.internal.music.ClientOnlineMusic music)) return null;
        return create(parent, () -> null, () -> music.backgroundOperationView(target).orElseThrow());
    }

    static UiScreen create(Screen parent, Supplier<? extends GuiEventListener> returnFocus,
                           Supplier<OnlineMusicView> factory) {
        var owner = new SessionOwner(factory);
        var result = ShBetterUiApi.screen(Component.translatable("sh_better_ui.online_music.view.brand"))
                .parent(parent)
                .onInit(screen -> {
                    try {
                        var exit = screen.add(new com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl(
                                new Bounds(8, 6, 24, 24), Component.translatable("gui.back"),
                                com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl.Glyph.BACK,
                                screen.preferences(), screen::onClose));
                        owner.mount(new Context(screen, owner, exit));
                        screen.onCloseRequest(() -> !(owner.view instanceof OnlineMusicViewImpl nativeView) || nativeView.permitsScreenClose());
                    }
                    catch (ServiceUnavailable failure) { owner.close(); }
                })
                .onRender((screen, frame) -> {
                    frame.graphics().fill(0, 0, screen.width, screen.height, UiTheme.current().background());
                    if (owner.view != null) owner.view.render(frame);
                    else frame.graphics().text(Minecraft.getInstance().font,
                            Component.translatable("sh_better_ui.online_music.view.unavailable"),
                            frame.viewport().x() + 8, frame.viewport().y() + 40, UiTheme.current().text(), false);
                })
                .onForeground((screen, frame) -> {
                    if (owner.view instanceof OnlineMusicViewImpl nativeView) nativeView.renderRouteHandoff(frame);
                })
                .onRemoved(screen -> owner.close()).build();
        result.returnFocus(Objects.requireNonNull(returnFocus));
        return result;
    }

    /** A resize remount keeps its view; removal disposes it once. The default route borrows persistent playback. */
    static final class SessionOwner implements AutoCloseable {
        private final Supplier<OnlineMusicView> factory;
        private OnlineMusicView view;
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
            if (previous != null) previous.close();
        }
    }

    private static final class ServiceUnavailable extends RuntimeException {
        ServiceUnavailable(RuntimeException cause) { super(cause); }
    }

    static GuiEventListener restoreExternalSearch(PreviewContext context, OnlineMusicViewImpl origin,
                                                   OnlineMusicViewImpl.ExternalSearchBookmark bookmark) {
        if (context instanceof Context nativeContext && nativeContext.owner.view instanceof OnlineMusicViewImpl next
                && next != origin) return next.restoreExternalSearch(bookmark);
        return origin.spotifySearchControl();
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
        @Override public Font font() { return Minecraft.getInstance().font; }
        @Override public UiPreferences preferences() { return screen.preferences(); }
        @Override public boolean interactive() { return Minecraft.getInstance().screen == screen; }
        @Override public <T extends AbstractWidget> T add(T widget) { return screen.add(widget); }
    }
}
