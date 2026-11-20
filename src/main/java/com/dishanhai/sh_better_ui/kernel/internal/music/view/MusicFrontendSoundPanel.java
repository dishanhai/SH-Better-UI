// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.api.music.control.*;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Page;
import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Route;

final class MusicFrontendSoundPanel {
    private enum Screen { SETTINGS, PLAYBACK, SOUND, VOLUME, FADE, SPEED, EQ, QQ }
    private record Row(Component title, Component value, boolean available, boolean selected, boolean submenu,
                       Supplier<MusicFrontendSound.Result> action) {}
    private static final int ROW_HEIGHT = 21, SPACING = 2, SURFACE = 0xff252528, INK = 0xfff5f5f7;
    private final MusicFrontendSound sound;
    private final Consumer<Route> navigate;
    private final BooleanSupplier historyAvailable;
    private final List<MusicTextControl> slots = new ArrayList<>();
    private PreviewContext host;
    private Bounds panel;
    private MusicTextControl previous, next;
    private MusicSeekControl slider;
    private List<Row> visibleRows = List.of();
    private MusicFrontendSound.State displayed;
    private Screen screen;
    private int offset;
    private Component feedback = Component.empty();

    MusicFrontendSoundPanel(OnlineMusicPlayer player, Consumer<Route> navigate) {
        this(player, navigate, () -> false);
    }

    MusicFrontendSoundPanel(OnlineMusicPlayer player, Consumer<Route> navigate, BooleanSupplier historyAvailable) {
        sound = new MusicFrontendSound(player);
        this.navigate = navigate;
        this.historyAvailable = historyAvailable;
    }

    static Bounds fit(Bounds body) {
        int width = Math.min(440, body.width() - 8);
        return new Bounds(body.x() + (body.width() - width) / 2, body.y(), width, body.height());
    }

    static int rowCapacity(int height) { return Math.min(7, (height - 23) / (ROW_HEIGHT + SPACING)); }

    void mount(PreviewContext host, Bounds area) {
        cancel();
        this.host = host;
        panel = fit(area);
        slots.clear();
        int capacity = rowCapacity(panel.height());
        for (int i = 0; i < capacity; i++) {
            int index = i;
            var item = button(new Bounds(panel.x(), panel.y() + i * (ROW_HEIGHT + SPACING), panel.width(), ROW_HEIGHT),
                    Component.empty(), () -> apply(index));
            item.setVariant(MusicTextControl.Variant.ROW);
            slots.add(item);
        }
        int footer = panel.bottom() - 22;
        previous = button(new Bounds(panel.right() - 50, footer, 22, 22), builtin("search_previous"), () -> page(-1));
        previous.setGlyphOnly(MusicTransportControl.Glyph.BACK);
        next = button(new Bounds(panel.right() - 22, footer, 22, 22), builtin("search_next"), () -> page(1));
        next.setGlyphOnly(MusicTransportControl.Glyph.NEXT);
        slider = host.add(new MusicSeekControl(new Bounds(panel.x()+8, panel.y()+19, panel.width()-16, 22),
                title(Screen.VOLUME), host.preferences(), edit -> {}, edit -> commitSlider(edit), edit -> {}));
    }

    private MusicTextControl button(Bounds bounds, Component text, Runnable action) {
        return host.add(new MusicTextControl(bounds, text, host.preferences(), action));
    }

    void cancel() {
        slots.forEach(MusicTextControl::cancelInteraction);
        if (previous != null) previous.cancelInteraction();
        if (next != null) next.cancelInteraction();
        if (slider != null) slider.cancelInteraction();
    }

    private static Component builtin(String key) { return Component.translatable("sh_better_ui.online_music.redesign." + key); }
    private static Component sound(String key, Object... args) {
        return Component.translatable("sh_better_ui.online_music.redesign.sound." + key, args);
    }
    private static Component music(String key, Object... args) {
        return Component.translatable("sh_better_ui.online_music." + key, args);
    }
    private static Component title(Screen screen) {
        return switch (screen) {
            case SETTINGS -> builtin("page.settings");
            case SPEED -> music("menu.playback_rate");
            case EQ -> music("menu.equalizer");
            case QQ -> music("menu.qq_effects");
            case FADE -> music("crossfade.title");
            default -> sound(screen.name().toLowerCase(Locale.ROOT));
        };
    }

    private static Screen screen(Route route) {
        if (route.page() != Page.SETTINGS) return null;
        return switch (route.id()) {
            case "playback" -> Screen.PLAYBACK;
            case "sound" -> Screen.SOUND;
            case "volume" -> Screen.VOLUME;
            case "fade" -> Screen.FADE;
            case "speed" -> Screen.SPEED;
            case "eq" -> Screen.EQ;
            case "qq" -> Screen.QQ;
            default -> Screen.SETTINGS;
        };
    }

    private Row link(Screen detail, Component value) {
        return new Row(title(detail), value, true, false, true, () -> {
            navigate.accept(new Route(Page.SETTINGS, "", detail.name().toLowerCase(Locale.ROOT)));
            return null;
        });
    }

    private static Component rate(double value) { return music("menu.playback_rate_value", PlaybackRateChoices.format(value)); }
    private static Component eq(OnlineMusicEqualizerPreset value) { return music("menu.eq." + value.name().toLowerCase(Locale.ROOT)); }
    private static Component qq(OnlineMusicSoundEffect value) { return music("menu.qq_effect." + value.name().toLowerCase(Locale.ROOT)); }
    private static Component percent(long value) { return sound("percent", value); }
    private static Component fade(long value) { return value == 0 ? music("crossfade.off") : music("crossfade.seconds", value); }

    private List<Row> rows(MusicFrontendSound.State s) {
        Component blank = Component.empty(), unavailable = sound("unavailable");
        return switch (screen) {
            case SETTINGS -> {
                var items = new ArrayList<Row>();
                items.add(new Row(builtin("page.accounts"), blank, true, false, true,
                        () -> { navigate.accept(Route.of(Page.ACCOUNTS)); return null; }));
                items.add(link(Screen.PLAYBACK, blank));
                items.add(link(Screen.SOUND, blank));
                if (historyAvailable.getAsBoolean()) items.add(new Row(builtin("page.history"), blank, true, false, true,
                        () -> { navigate.accept(Route.of(Page.HISTORY)); return null; }));
                yield items;
            }
            case PLAYBACK -> List.of(link(Screen.SPEED, s.speedAvailable() ? rate(s.speed()) : unavailable),
                    link(Screen.FADE, s.crossfadeAvailable() ? fade(s.crossfade()) : unavailable));
            case SOUND -> List.of(link(Screen.VOLUME, s.volumeAvailable() ? percent(s.volume()) : unavailable),
                    link(Screen.EQ, s.eqAvailable() ? eq(s.eq()) : unavailable),
                    link(Screen.QQ, s.qqAvailable() ? qq(s.qq()) : unavailable));
            case SPEED -> s.speedAvailable() ? PlaybackRateChoices.VALUES.stream().map(value ->
                    new Row(rate(value), blank, true, Double.compare(value, s.speed()) == 0, false, () -> sound.speed(s, value))).toList() : List.of();
            case EQ -> s.eqAvailable() ? List.of(OnlineMusicEqualizerPreset.values()).stream().map(value ->
                    new Row(eq(value), blank, true, value == s.eq(), false, () -> sound.eq(s, value))).toList() : List.of();
            case QQ -> s.qqAvailable() ? List.of(OnlineMusicSoundEffect.values()).stream().map(value ->
                    new Row(qq(value), blank, true, value == s.qq(), false, () -> sound.qq(s, value))).toList() : List.of();
            default -> List.of();
        };
    }

    Component heading() { return screen == null ? null : title(screen); }

    void update(Route route) {
        var nextScreen = screen(route);
        if (screen != nextScreen) {
            cancel();
            screen = nextScreen;
            offset = 0;
            feedback = Component.empty();
        }
        refresh();
    }

    private void refresh() {
        var now = screen == null ? null : sound.read();
        if (now != null && displayed != null &&
                (!now.sameOccurrence(displayed) || now.speedAvailable() != displayed.speedAvailable()
                        || now.eqAvailable() != displayed.eqAvailable() || now.qqAvailable() != displayed.qqAvailable()))
            slots.forEach(MusicTextControl::cancelInteraction);
        displayed = now;
        boolean volume = screen == Screen.VOLUME;
        slider.visible = volume || screen == Screen.FADE;
        boolean enabled = slider.visible && (volume ? now.volumeAvailable() : now.crossfadeAvailable());
        if (slider.visible) {
            slider.setMessage(title(screen));
            slider.setKeyboardStep(volume ? 5 : 1, 1);
            slider.setValueFormatter((value, total) -> volume ? percent(value) : fade(value));
        }
        slider.sync(slider.visible ? screen.name() : null, !slider.visible ? 0 : volume ? now.volume() : now.crossfade(),
                volume ? 100 : 12, enabled);

        var rows = screen == null ? List.<Row>of() : volume && now.volumeAvailable()
                ? List.of(new Row(sound(now.volume() == 0 ? "unmute" : "mute"), Component.empty(), true, false, false,
                        () -> sound.toggleMute(now)))
                : rows(now);
        offset = Math.max(0, Math.min(offset, rows.size() - 1));
        int capacity = slots.size();
        boolean paged = rows.size() > capacity;
        visibleRows = rows.subList(offset, Math.min(rows.size(), offset + capacity));
        for (int i = 0; i < capacity; i++) {
            var item = slots.get(i);
            var row = i < visibleRows.size() ? visibleRows.get(i) : null;
            item.setY(panel.y() + (volume ? 45 : 0) + i * (ROW_HEIGHT + SPACING));
            item.setVisible(row != null);
            item.setAvailable(row != null && row.available());
            if (row == null) continue;
            var label = row.value().equals(Component.empty()) ? row.title() : sound("summary", row.title(), row.value());
            if (!label.equals(item.getMessage())) {
                item.rebind(label);
                item.setPreview(label);
            }
            item.setSelected(row.selected());
            item.setTrailingGlyph(row.submenu() ? MusicTransportControl.Glyph.NEXT : null);
        }
        previous.setVisible(paged); previous.setAvailable(offset > 0);
        next.setVisible(paged); next.setAvailable(offset + capacity < rows.size());
    }

    private void page(int direction) {
        cancel();
        offset = Math.max(0, offset + direction * slots.size());
        refresh();
        if (direction > 0 && !next.active && previous.active) host.screen().setFocused(previous);
        if (direction < 0 && !previous.active && next.active) host.screen().setFocused(next);
    }

    private void apply(int index) {
        if (screen == null || index >= visibleRows.size()) return;
        var row = visibleRows.get(index);
        if (row.selected()) return;
        var result = row.action().get();
        if (result != null) finish(result);
        else if (slider.visible && slider.active) host.screen().setFocused(slider);
        else if (!visibleRows.isEmpty()) host.screen().setFocused(slots.get(0));
    }

    private void commitSlider(MusicSeekModel.Edit edit) {
        if (screen == null || !screen.name().equals(edit.trackKey())) return;
        if (screen == Screen.VOLUME) finish(sound.volume((int) edit.positionMs()));
        else if (screen == Screen.FADE) finish(sound.fade((int) edit.positionMs()));
    }

    private void finish(MusicFrontendSound.Result result) {
        feedback = switch (result) {
            case APPLIED -> Component.empty();
            case BUSY -> music("crossfade.wait_short");
            case CHANGED -> sound("changed");
            case UNAVAILABLE -> sound("unavailable");
        };
        refresh();
    }

    void render(UiFrame ui, int muted) {
        if (screen == null) return;
        int top = panel.y() + (screen == Screen.VOLUME && !visibleRows.isEmpty() ? 45 : 0);
        if (!visibleRows.isEmpty()) {
            RoundedCorners.draw(ui.graphics(), panel.x(), top, panel.width(),
                    visibleRows.size() * (ROW_HEIGHT + SPACING) - SPACING, 8, 0, 0, SURFACE);
            for (int i = 1; i < visibleRows.size(); i++) {
                int y = top + i * (ROW_HEIGHT + SPACING) - 1;
                ui.graphics().fill(panel.x()+10, y, panel.right()-10, y+1, 0xff3a3a3c);
            }
        }
        for (var slot : slots) {
            slot.setPreferences(ui.preferences());
            slot.setInk(INK, muted);
        }
        previous.setPreferences(ui.preferences());
        next.setPreferences(ui.preferences());
        previous.setInk(INK, muted);
        next.setInk(INK, muted);
        slider.setPreferences(ui.preferences());
        slider.setInk(INK);
        if (slider.visible && slider.active && (screen != Screen.VOLUME || feedback.equals(Component.empty()))) {
            var label = screen == Screen.VOLUME ? percent(slider.previewPositionMs()) : fade(slider.previewPositionMs());
            OnlineMusicViewPaint.line(ui, host.font(), label, panel.x()+8, panel.y()+4, panel.width()-16, INK);
        }
        Component note = switch (screen) {
            case SPEED -> displayed.speedAvailable() ? music("menu.playback_rate_pitch") : sound("native_only");
            case EQ -> !displayed.eqAvailable() ? sound("native_only") : Component.empty();
            case QQ -> !displayed.qqAvailable() ? sound("qq_only") : Component.empty();
            case FADE -> sound("native_crossfade");
            case VOLUME -> !displayed.volumeAvailable() ? sound("unavailable") : Component.empty();
            default -> Component.empty();
        };
        if (!feedback.equals(Component.empty())) note = feedback;
        if (!note.equals(Component.empty())) {
            if (screen == Screen.SPEED && displayed.speedAvailable() || !visibleRows.isEmpty() && !slider.visible) {
                OnlineMusicViewPaint.line(ui, host.font(), note, panel.x()+8, panel.bottom()-16,
                        panel.width()-(previous.visible ? 66 : 16), muted);
            } else {
                int y = slider.visible && slider.active ? panel.y()+43 : panel.y()+5;
                if (screen == Screen.VOLUME && slider.active) y = panel.y()+4;
                OnlineMusicViewPaint.paragraph(ui, host.font(), note,
                        new Bounds(panel.x()+8, y, panel.width()-16, panel.bottom()-y), muted);
            }
        }
    }
}
