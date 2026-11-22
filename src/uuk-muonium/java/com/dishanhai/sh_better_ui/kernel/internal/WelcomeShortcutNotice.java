// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.text.UiShortcutKeycaps;
import com.muoniumplayer.MuoniumPlayerClient;
import com.muoniumplayer.core.screens.GuiFfmpegNotice;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** The first launch shortcut notice that UUK draws for MuoniumConfig. */
public final class WelcomeShortcutNotice {
    private static final long DURATION_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final String TEXT = "sh_better_ui.muonium.welcome_shortcuts.";
    private static final int MARGIN = 6, PADDING = 8, MIN_WIDTH = 160, MAX_WIDTH = 264;
    private static final int BAR_GAP = 4, BAR_HEIGHT = 2;
    private static Notice active;
    private static Screen pressedScreen;

    private WelcomeShortcutNotice() {}

    public static void show(Runnable onClick) {
        Minecraft.getInstance().execute(() -> {
            pressedScreen = null;
            active = new Notice(onClick);
        });
    }

    public static void dismiss() {
        Minecraft.getInstance().execute(() -> {
            pressedScreen = null;
            active = null;
        });
    }

    static boolean pending() {
        return active != null;
    }

    static void draw(Font font, int screenWidth, int screenHeight, Screen screen, double mouseX, double mouseY,
                     boolean highContrast, Plate plate, Fill fill, TextLine drawText, Consumer<Component> narrate) {
        var notice = active;
        if (notice == null) return;
        notice.frame = null;
        long now = System.nanoTime();
        if (expire(notice, now) || !canDraw(notice, screen)) return;

        int availableWidth = Math.min(MAX_WIDTH, screenWidth - 2 * MARGIN);
        if (availableWidth < 32 + 2 * PADDING) return;
        if (notice.lines == null || notice.availableWidth != availableWidth) {
            var text = messages(true);
            int measured = text.stream().mapToInt(font::width).max().orElse(0) + 2 * PADDING;
            notice.width = Math.min(availableWidth, Math.max(MIN_WIDTH, measured));
            int contentWidth = notice.width - 2 * PADDING;
            notice.lines = text.stream().flatMap(line -> font.split(line, contentWidth).stream()).toList();
            notice.availableWidth = availableWidth;
        }
        int lineHeight = font.lineHeight + 3;
        int height = 2 * PADDING + notice.lines.size() * lineHeight + BAR_GAP + BAR_HEIGHT;
        if (height > screenHeight - 2 * MARGIN) return;
        if (notice.startedAt == null) {
            notice.startedAt = now;
            narrate.accept(CommonComponents.joinForNarration(messages(false).toArray(Component[]::new)));
        }

        int width = notice.width;
        int innerWidth = width - 2 * PADDING;
        int x = screenWidth - MARGIN - width;
        int y = screenHeight - MARGIN - height;
        var frame = new Frame(screen, x, y, width, height);
        notice.frame = frame;
        boolean hovered = screen != null && frame.contains(mouseX, mouseY);
        boolean pressed = hovered && pressedScreen == screen;
        int textColor = highContrast ? 0xFFFFFFFF : 0xFFE6EDF5;
        int border = highContrast || pressed ? textColor : hovered ? 0xFF8799AF : 0xFF3F4C5D;
        int background = highContrast ? (pressed ? 0xFF333333 : 0xFF000000)
                : pressed ? 0xF039475B : hovered ? 0xF02B3645 : 0xEB181F29;
        plate.draw(x, y, width, height, 7, 1, border, background);
        int textX = x + PADDING, textY = y + PADDING;
        for (var line : notice.lines) {
            drawText.draw(font, line, textX, textY, textColor, false);
            textY += lineHeight;
        }
        int barY = textY + BAR_GAP;
        int remaining = (int) Math.ceil(innerWidth * (1 - (now - notice.startedAt) / (double) DURATION_NANOS));
        fill.draw(textX, barY, textX + innerWidth, barY + BAR_HEIGHT, 0xFF425064);
        fill.draw(textX, barY, textX + remaining, barY + BAR_HEIGHT, highContrast ? textColor : 0xFF9EC6FF);
    }

    public static boolean press(Screen screen, double x, double y, int button) {
        if (button != 0) return false;
        pressedScreen = hit(screen, x, y) ? screen : null;
        return pressedScreen != null;
    }

    public static boolean release(Screen screen, double x, double y, int button) {
        if (button != 0) return false;
        boolean pressed = pressedScreen != null && pressedScreen == screen;
        pressedScreen = null;
        if (!pressed) return false;
        if (hit(screen, x, y)) {
            Runnable action = active.onClick;
            dismiss();
            Minecraft.getInstance().execute(action);
        }
        return true;
    }

    public static void screenClosing(Screen screen) {
        if (pressedScreen == screen) pressedScreen = null;
    }

    private static boolean hit(Screen screen, double x, double y) {
        var notice = active;
        return notice != null && WelcomeShortcutNoticeRenderer.visibleIn(Minecraft.getInstance(), screen)
                && !expire(notice, System.nanoTime()) && canDraw(notice, screen) && notice.frame != null
                && notice.frame.screen == screen && notice.frame.contains(x, y);
    }

    private static boolean canDraw(Notice notice, Screen screen) {
        return screen == null || notice.startedAt != null && !(screen instanceof GuiFfmpegNotice);
    }

    private static boolean expire(Notice notice, long now) {
        if (notice.startedAt == null || now - notice.startedAt < DURATION_NANOS) return false;
        active = null;
        return true;
    }

    private static List<Component> messages(boolean keycaps) {
        var hud = MuoniumPlayerClient.keyEditHud;
        var player = MuoniumPlayerClient.keyOpenMusic;
        boolean hasHud = hud != null && !hud.isUnbound();
        boolean hasPlayer = player != null && !player.isUnbound();
        var message = hasHud
                ? Component.translatable(TEXT + "hud", keyText(hud, keycaps))
                : Component.translatable(TEXT + "hud_unbound");
        return hasPlayer
                ? List.of(message, Component.translatable(TEXT + "player", keyText(player, keycaps)))
                : List.of(message);
    }

    private static Component keyText(KeyMapping key, boolean keycaps) {
        return keycaps ? UiShortcutKeycaps.binding(key) : key.getTranslatedKeyMessage();
    }

    private static final class Notice {
        private final Runnable onClick;
        private Long startedAt;
        private List<FormattedCharSequence> lines;
        private int availableWidth;
        private int width;
        private Frame frame;

        private Notice(Runnable onClick) {
            this.onClick = onClick;
        }
    }

    private record Frame(Screen screen, int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    @FunctionalInterface
    interface Plate {
        void draw(float x, float y, float width, float height, float radius, float stroke, int border, int fill);
    }

    @FunctionalInterface
    interface Fill {
        void draw(int left, int top, int right, int bottom, int color);
    }

    @FunctionalInterface
    interface TextLine {
        void draw(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow);
    }
}
