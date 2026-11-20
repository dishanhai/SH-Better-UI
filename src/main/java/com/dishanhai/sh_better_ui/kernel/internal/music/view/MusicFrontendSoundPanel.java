// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.api.music.control.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
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
    private enum Screen { SETTINGS, APPEARANCE, PLAYBACK, SOUND, VOLUME, FADE, SPEED, EQ, QQ }
    record ViewState(int first, String focus) {}
    private record Row(String id, Component title, Component value, boolean available, boolean selected, boolean submenu,
                       Supplier<MusicFrontendSound.Result> action) {}
    private static final int ROW_HEIGHT = 21, SPACING = 2, SURFACE = 0xff252528, INK = 0xfff5f5f7;
    private final MusicFrontendSound sound;
    private final Consumer<Route> navigate;
    private final BooleanSupplier historyAvailable;
    private final List<MusicTextControl> slots = new ArrayList<>();
    private PreviewContext host;
    private Bounds panel;
    private int capacity;
    private MusicTextControl back, utility;
    private OnlineMusicScrollRegion scroll;
    private MusicSeekControl slider;
    private List<Row> visibleRows = List.of();
    private MusicFrontendSound.State displayed;
    private Screen screen;
    private int offset;
    private String activatingRow, lastFocus;
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

    void mount(PreviewContext host, Bounds area, MusicTextControl back, MusicTextControl utility) {
        cancel();
        this.host = host;
        this.back = back;
        this.utility = utility;
        lastFocus = null;
        panel = fit(area);
        slots.clear();
        visibleRows = List.of();
        capacity = Math.max(0, rowCapacity(panel.height()));
        scroll = host.add(new OnlineMusicScrollRegion(panel.x(), panel.y(), panel.width(),
                Math.max(0, capacity * (ROW_HEIGHT + SPACING)), builtin("page.settings"), value -> {
                    slots.forEach(MusicTextControl::cancelInteraction);
                    offset = value;
                    refresh();
                }));
        scroll.setOnBrowse(() -> host.screen().setFocused(scroll));
        int spill = MusicScrollRows.spill(host, capacity);
        if (spill > 0) scroll.pixelRows(ROW_HEIGHT+SPACING, shift -> positionRows());
        for (int i = 0; i < capacity+spill; i++) {
            int index = i;
            var item = MusicScrollRows.add(host, new MusicTextControl(
                    new Bounds(panel.x(), panel.y() + i * (ROW_HEIGHT + SPACING), panel.width(), ROW_HEIGHT),
                    Component.empty(), host.preferences(), () -> apply(index)), this::listBounds);
            item.setVariant(MusicTextControl.Variant.ROW);
            slots.add(item);
        }
        slider = host.add(new MusicSeekControl(MusicSoundLayout.slider(panel),
                title(Screen.VOLUME), host.preferences(), edit -> {}, edit -> commitSlider(edit), edit -> {}));
    }

    private MusicTextControl button(Bounds bounds, Component text, Runnable action) {
        return host.add(new MusicTextControl(bounds, text, host.preferences(), action));
    }

    void cancel() {
        slots.forEach(MusicTextControl::cancelInteraction);
        if (scroll != null) scroll.cancelInteraction();
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
            case APPEARANCE -> MusicAppearanceLabels.label("title");
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
            case "appearance" -> Screen.APPEARANCE;
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
        return new Row("screen:" + detail.name(), title(detail), value, true, false, true, () -> {
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
                items.add(new Row("screen:ACCOUNTS", builtin("page.accounts"), blank, true, false, true,
                        () -> { navigate.accept(Route.of(Page.ACCOUNTS)); return null; }));
                items.add(link(Screen.PLAYBACK, blank));
                items.add(link(Screen.SOUND, blank));
                if (historyAvailable.getAsBoolean()) items.add(new Row("screen:HISTORY", builtin("page.history"), blank, true, false, true,
                        () -> { navigate.accept(Route.of(Page.HISTORY)); return null; }));
                items.add(link(Screen.APPEARANCE, MusicAppearanceLabels.current()));
                yield items;
            }
            case APPEARANCE -> java.util.Arrays.stream(MusicAppearanceStore.Preference.values()).map(value ->
                    new Row("appearance:" + value.id(), MusicAppearanceLabels.option(value), blank,
                            !MusicAppearance.locked(), MusicAppearance.current() == value, false, () -> {
                        feedback = MusicAppearance.select(value) ? Component.empty() : MusicAppearanceLabels.label("save_failed");
                        refresh();
                        return null;
                    })).toList();
            case PLAYBACK -> List.of(link(Screen.SPEED, s.speedAvailable() ? rate(s.speed()) : unavailable),
                    link(Screen.FADE, s.crossfadeAvailable() ? fade(s.crossfade()) : unavailable));
            case SOUND -> List.of(link(Screen.VOLUME, s.volumeAvailable() ? percent(s.volume()) : unavailable),
                    link(Screen.EQ, s.eqAvailable() ? eq(s.eq()) : unavailable),
                    link(Screen.QQ, s.qqAvailable() ? qq(s.qq()) : unavailable));
            case SPEED -> s.speedAvailable() ? PlaybackRateChoices.VALUES.stream().map(value ->
                    new Row("rate:" + value, rate(value), blank, true, Double.compare(value, s.speed()) == 0, false, () -> sound.speed(s, value))).toList() : List.of();
            case EQ -> s.eqAvailable() ? List.of(OnlineMusicEqualizerPreset.values()).stream().map(value ->
                    new Row("eq:" + value.name(), eq(value), blank, true, value == s.eq(), false, () -> sound.eq(s, value))).toList() : List.of();
            case QQ -> s.qqAvailable() ? List.of(OnlineMusicSoundEffect.values()).stream().map(value ->
                    new Row("qq:" + value.name(), qq(value), blank, true, value == s.qq(), false, () -> sound.qq(s, value))).toList() : List.of();
            default -> List.of();
        };
    }

    Component heading() { return screen == null ? null : title(screen); }

    ViewState bookmark() {
        if (screen == null || host == null) return null;
        return new ViewState(offset, activatingRow == null ? focusedId(host.screen().getFocused()) : activatingRow);
    }

    ViewState resizeBookmark() {
        if (screen == null || host == null) return null;
        var focused = host.screen().getFocused();
        return new ViewState(offset, focused == null ? lastFocus : focusedId(focused));
    }

    void rememberFocus(GuiEventListener focused) {
        // Native resize clears focus before layout is called. Keep the last logical target,
        // but don't replace unrelated focus (e.g. the mini player) during a remount.
        if (screen != null && focused != null) lastFocus = focusedId(focused);
    }

    private String focusedId(GuiEventListener focused) {
        if (focused == null) return null;
        if (focused == back) return "@back";
        if (focused == utility) return "@utility";
        if (focused == scroll) return "@scroll";
        if (focused == slider && slider.visible && slider.active) return "@slider";
        for (int i = 0; i < visibleRows.size(); i++) {
            if (focused == slots.get(i) && slots.get(i).active) return visibleRows.get(i).id();
        }
        return null;
    }

    private void focusControl(GuiEventListener target) {
        rememberFocus(target);
        if (host.screen() instanceof UiScreen nativeScreen) nativeScreen.restoreLayoutFocus(target);
        else host.screen().setFocused(target);
    }

    void restore(ViewState state) {
        if (screen == null || state == null) return;
        refresh(state);
        if (state.focus() != null) focus(state.focus());
    }

    void focusEntry() { if (screen != null) focus(null); }

    private void focus(String id) {
        if (id != null) {
            switch (id) {
                case "@back" -> { focusControl(back); return; }
                case "@utility" -> { focusControl(utility); return; }
                case "@scroll" -> { if (scroll.active) { focusControl(scroll); return; } }
                case "@slider" -> { if (slider.visible && slider.active) { focusControl(slider); return; } }
                default -> {
                    for (int i = 0; i < visibleRows.size(); i++) {
                        if (id.equals(visibleRows.get(i).id()) && slots.get(i).active) {
                            focusControl(slots.get(i));
                            return;
                        }
                    }
                }
            }
        }
        if (slider.visible && slider.active) { focusControl(slider); return; }
        for (var slot : slots) {
            if (slot.visible && slot.active) { focusControl(slot); return; }
        }
        focusControl(back);
    }

    void update(Route route) {
        var nextScreen = screen(route);
        var before = screen != null && screen == nextScreen ? focusedId(host.screen().getFocused()) : null;
        if (screen != nextScreen) {
            cancel();
            screen = nextScreen;
            lastFocus = null;
            offset = 0;
            feedback = Component.empty();
        }
        refresh();
        if (before != null && !before.equals(focusedId(host.screen().getFocused()))) {
            focus(before);
        }
    }

    private void refresh() { refresh(null); }

    private void refresh(ViewState restored) {
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
                ? List.of(new Row("mute", sound(now.volume() == 0 ? "unmute" : "mute"), Component.empty(), true, false, false,
                        () -> sound.toggleMute(now)))
                : rows(now);
        if (capacity == 0) return;
        if (restored != null) {
            offset = restored.first();
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                if (row.available() && row.id().equals(restored.focus())) { offset = i; break; }
            }
        }
        offset = Math.max(0, Math.min(offset, rows.size() - capacity));
        scroll.visible = screen != null && !slider.visible;
        scroll.configure(offset, rows.size(), capacity);
        visibleRows = rows.subList(offset, Math.min(rows.size(), offset + slots.size()));
        for (int i = 0; i < slots.size(); i++) {
            var item = slots.get(i);
            var row = i < visibleRows.size() ? visibleRows.get(i) : null;
            item.setVisible(row != null);
            item.setWidth(panel.width() - (scroll.visible && scroll.active ? 8 : 0));
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
        positionRows();
    }

    private Bounds listBounds() {
        return MusicSoundLayout.rows(panel, capacity * (ROW_HEIGHT + SPACING), screen == Screen.VOLUME);
    }

    private void positionRows() {
        var list = listBounds();
        for (int i = 0; i < slots.size(); i++) {
            var row = slots.get(i);
            int y = list.y() + i * (ROW_HEIGHT+SPACING) - scroll.rowShift();
            row.setY(y);
            row.setVisible(i < visibleRows.size() && MusicScrollRows.intersects(y, ROW_HEIGHT, list));
        }
    }

    private void apply(int index) {
        if (screen == null || index >= visibleRows.size() || !slots.get(index).visible) return;
        var row = visibleRows.get(index);
        if (row.selected()) return;
        if (row.submenu()) activatingRow = row.id();
        try {
            var result = row.action().get();
            if (result != null) finish(result);
        } finally {
            activatingRow = null;
        }
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
        scroll.tick();
        int top = listBounds().y();
        if (!visibleRows.isEmpty()) {
            int height = Math.min(listBounds().height(), visibleRows.size()*(ROW_HEIGHT+SPACING)-SPACING);
            if (screen == Screen.APPEARANCE && MusicGlass.active()) {
                MusicGlass.chrome(ui, new Bounds(panel.x(), top, panel.width(), height), null);
            } else {
                RoundedCorners.draw(ui.graphics(), panel.x(), top, panel.width(), height, 8,
                        ui.preferences().highContrast() ? 1 : 0, INK, SURFACE);
            }
        }
        for (var slot : slots) {
            slot.setPreferences(ui.preferences());
            slot.setInk(INK, muted);
        }
        slider.setPreferences(ui.preferences());
        slider.setInk(INK);
        if (slider.visible && slider.active && (screen != Screen.VOLUME || feedback.equals(Component.empty()))) {
            var label = screen == Screen.VOLUME ? percent(slider.previewPositionMs()) : fade(slider.previewPositionMs());
            OnlineMusicViewPaint.line(ui, host.font(), label, panel.x()+8, panel.y()+4, panel.width()-16, INK);
        }
        Component note = switch (screen) {
            case APPEARANCE -> MusicAppearance.locked() ? MusicAppearanceLabels.label("locked") : Component.empty();
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
                        panel.width()-16, muted);
            } else {
                int y = slider.visible && slider.active ? panel.y()+43 : panel.y()+5;
                if (screen == Screen.VOLUME && slider.active) y = panel.y()+4;
                OnlineMusicViewPaint.paragraph(ui, host.font(), note,
                        new Bounds(panel.x()+8, y, panel.width()-16, panel.bottom()-y), muted);
            }
        }
    }
}
