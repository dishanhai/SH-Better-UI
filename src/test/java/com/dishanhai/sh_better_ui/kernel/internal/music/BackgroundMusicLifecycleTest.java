// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.internal.music.view.OnlineMusicViewImpl;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundMusicLifecycleTest {
    private Object previousRenderThread;
    private java.lang.reflect.Field renderThread;
    @org.junit.jupiter.api.BeforeEach void bindRenderThread() throws Exception {
        renderThread = com.mojang.blaze3d.systems.RenderSystem.class.getDeclaredField("renderThread");
        renderThread.setAccessible(true); previousRenderThread = renderThread.get(null);
        renderThread.set(null, Thread.currentThread());
    }
    @org.junit.jupiter.api.AfterEach void restoreRenderThread() throws Exception {
        renderThread.set(null, previousRenderThread);
    }

    @Test void borrowedViewNeverPausesOrClosesPlaybackOnRemoval() {
        var pauses = new AtomicInteger(); var closes = new AtomicInteger();
        OnlineMusicPlayer player = (OnlineMusicPlayer) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{OnlineMusicPlayer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("pause")) pauses.incrementAndGet();
                    if (method.getName().equals("close")) closes.incrementAndGet();
                    return null;
                });
        var view = new OnlineMusicViewImpl(player, false);
        view.suspend(); view.close(); view.close();
        assertEquals(0, pauses.get()); assertEquals(0, closes.get());
    }
    @Test void independentPreviewStillOwnsPlayback() {
        var pauses = new AtomicInteger(); var closes = new AtomicInteger();
        OnlineMusicPlayer player = (OnlineMusicPlayer) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{OnlineMusicPlayer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("pause")) pauses.incrementAndGet();
                    if (method.getName().equals("close")) closes.incrementAndGet();
                    return null;
                });
        var view = new OnlineMusicViewImpl(player);
        view.close(); view.close();
        assertEquals(1, pauses.get()); assertEquals(1, closes.get());
    }
    @Test void screenReentryBorrowsSamePlayerAndKernelShutdownReleasesIt() {
        var service = new ClientOnlineMusic();
        var first = service.backgroundView(); var player = first.player();
        first.close();
        assertEquals(OnlineMusicPlayer.State.IDLE, player.snapshot().state());
        var second = service.backgroundView();
        assertSame(player, second.player());
        second.close(); service.close(); service.close();
        assertEquals(OnlineMusicPlayer.State.CLOSED, player.snapshot().state());
        assertThrows(IllegalStateException.class, service::backgroundView);
    }
    @Test void explicitlyClosedBorrowedPlayerIsReplaced() {
        try (var service = new ClientOnlineMusic()) {
            var first = service.backgroundView(); var player = first.player();
            player.close(); first.close();
            var second = service.backgroundView();
            assertNotSame(player, second.player()); second.close();
        }
    }
}
