// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.shbui_widgets_smoke;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.sync.TrackIdentity;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Isolated visual/interaction check with deterministic tracks/lyrics; never connects an account or opens audio. */
final class OnlineMusicLayoutSmoke {
    private static Probe screen;
    private static int stage, frames;
    private static boolean capturing;
    private static final String PREFIX = "sh_better_ui.online_music.";
    static void frame(Minecraft client) {
        if (capturing || WidgetSmokePlatform.hasOverlay(client)) return;
        if (screen == null) { screen = new Probe(); WidgetSmokePlatform.open(client, screen); return; }
        if (++frames < 12) return;
        frames = 0;
        if (stage == 0) {
            screen.query().setValue("layout fixture"); screen.setFocused(screen.query());
            if (!screen.query().keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0)))
                throw new IllegalStateException("Query did not submit through its native keyboard handler");
            stage++; return;
        }
        if (stage >= 7) {
            screen.onClose();
            if (!screen.fixture.closed || !screen.fixture.loginCancelled || screen.fixture.searches != 1)
                throw new IllegalStateException("Music view search/lifecycle checks did not finish");
            try { java.nio.file.Files.writeString(client.gameDirectory.toPath().resolve("online-music-layout.txt"), "PASS fixture=true search lyrics accounts qr back narrow light close\n"); }
            catch (java.io.IOException failure) { throw new UncheckedIOException(failure); }
            System.out.println("SHBUI_ONLINE_MUSIC_LAYOUT_PASS search lyrics accounts qr back narrow light close=true fixture=true");
            client.stop(); return;
        }
        String name = switch (stage) {
            case 1 -> "search"; case 2 -> "lyrics"; case 3 -> "accounts"; case 4 -> "qr"; case 5 -> "light"; default -> "compact";
        };
        capturing = true;
        Screenshot.takeScreenshot(WidgetSmokePlatform.target(client), image -> {
            try (image) { image.writeToFile(client.gameDirectory.toPath().resolve("online-music-" + name + ".png")); }
            catch (java.io.IOException failure) { throw new UncheckedIOException(failure); }
            client.schedule(() -> {
                try {
                switch (stage) {
                    case 1 -> screen.press("view.lyrics");
                    case 2 -> screen.press("view.accounts");
                    case 3 -> screen.press("source.netease");
                    case 4 -> {
                        screen.press("view.back"); screen.press("view.back");
                        if (!screen.query().getValue().equals("layout fixture")) throw new IllegalStateException("Search draft was lost");
                        screen.light = true;
                    }
                    case 5 -> { screen.compact = true; screen.rebuild(); }
                    default -> { }
                }
                stage++; capturing = false;
                } catch (RuntimeException failure) { screen.view.close(); client.stop(); throw failure; }
            });
        });
    }
    private static final class Probe extends Screen implements PreviewContext {
        final Fixture fixture = new Fixture();
        final OnlineMusicView view = UnifiedUI.get().require(OnlineMusicService.KEY).view(fixture);
        boolean light, compact;
        Probe() { super(Component.translatable(PREFIX + "view.brand")); }
        @Override protected void init() { view.layout(this); }
        void rebuild() { view.suspend(); clearWidgets(); init(); }
        UiTextField query() { return children().stream().filter(UiTextField.class::isInstance).map(UiTextField.class::cast).findFirst().orElseThrow(); }
        void press(String key) {
            var label = Component.translatable(PREFIX + key).getString();
            children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast)
                    .filter(b -> b.visible && b.active && b.getMessage().getString().equals(label))
                    .filter(b -> !key.equals("search") || b.getWidth() == 24)
                    .filter(b -> !key.startsWith("source.") || b.getHeight() == 44).findFirst().orElseThrow().onPress();
        }
        @Override public void extractBackground(GuiGraphicsExtractor graphics, int mx, int my, float delta) { graphics.fill(0, 0, width, height, 0xff111827); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
            var palette = light ? new UiTheme(true, 0xffe8eef5, 0xfff8fafc, 0xff64748b, 0xff172334, 0xff1755aa, 31)
                    : new UiTheme(false, 0xff0f172a, 0xff1e293b, 0xff334155, 0xfff1f5f9, 0xff3b82f6, 31);
            UiTheme.preview(palette, () -> {
                view.render(new UiFrame(graphics, viewport(), mx, my, preferences(), System.currentTimeMillis(), delta));
                super.extractRenderState(graphics, mx, my, delta);
            });
        }
        @Override public void onClose() { view.close(); super.onClose(); }
        @Override public Screen screen() { return this; }
        @Override public Bounds viewport() {
            int w = compact ? Math.min(300, width - 20) : width - 20;
            return new Bounds((width - w) / 2, 10, w, height - 20);
        }
        @Override public Font font() { return font; }
        @Override public UiPreferences preferences() { return new UiPreferences(false, true, false); }
        @Override public boolean interactive() { return true; }
        @Override public <T extends AbstractWidget> T add(T widget) { return addRenderableWidget(widget); }
    }
    private static final class Fixture implements OnlineMusicPlayer {
        final OnlineTrack track = new OnlineTrack("netease", "123", "123", "山海之间 · 窗口与留白", "SHBUI 音乐页面验收", "布局测试", 240000);
        final LyricTimeline lyrics = LrcParser.parse("[00:00]让音乐留在此刻\n[00:10]窗外的风吹过山海\n[00:20]每一句都有它的位置\n[00:30]听见时间慢慢走来\n[00:40]保留清晰的文字与留白\n[00:50]下一段旋律仍在等待", null, 240000);
        final LocalLyricFeed feed = new LocalLyricFeed("layout-fixture");
        boolean closed, loginCancelled; long position = 25000; int searches;
        State state = State.PAUSED;
        @Override public List<String> sources() { return List.of("netease", "qq"); }
        @Override public CompletableFuture<List<OnlineTrack>> search(String source, String query, int page) {
            searches++;
            return CompletableFuture.completedFuture(java.util.stream.IntStream.range(0, 20).mapToObj(i -> new OnlineTrack(source, "" + (123 + i), "" + (123 + i),
                    i == 0 ? track.title() : "测试歌曲 " + (i + 1), track.artist(), track.album(), 240000 + i * 1000L)).toList());
        }
        @Override public void credentials(String source, String cookie) { }
        @Override public boolean authenticated(String source) { return false; }
        @Override public OnlineMusicLogin login(String source) {
            var dark = new BitSet(80 * 80);
            for (int y = 8; y < 72; y++) for (int x = 8; x < 72; x++) if ((x / 3 + y / 3) % 2 == 0) dark.set(y * 80 + x);
            var image = new OnlineMusicLogin.Image(80, 80, dark);
            return new OnlineMusicLogin() {
                boolean cancelled;
                @Override public Snapshot snapshot() { return new Snapshot(cancelled ? OnlineMusicLogin.State.CANCELLED : OnlineMusicLogin.State.WAITING, cancelled ? null : image); }
                @Override public void close() { cancelled = true; loginCancelled = true; }
            };
        }
        @Override public void play(OnlineTrack track) { state = State.PLAYING; }
        @Override public void pause() { state = State.PAUSED; }
        @Override public void resume() { state = State.PLAYING; }
        @Override public void seek(long pos) { position = pos; }
        @Override public void stop() { state = State.IDLE; }
        @Override public Snapshot snapshot() { return new Snapshot(track, state, position, track.durationMs(), LyricsState.READY, lyrics, Failure.NONE, false); }
        @Override public LyricFeed lyricFeed() { feed.publish(new TrackIdentity(track.title(), track.artist(), track.album(), track.durationMs()), lyrics, position, false); return feed; }
        @Override public void close() { closed = true; state = State.CLOSED; }
    }
}
