// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendQueue.Action;

/** The Playing Next view owns its list, scroll rail and edit controls independently of other routes. */
final class MusicFrontendQueuePanel {
    private static final int ROW = 24;
    private static final int CONTROL = 22;
    private static final int GAP = 4;
    private static final int RAIL = 10;
    private final PreviewContext host;
    private final Bounds bounds, list;
    private final int capacity;
    private final MusicFrontendQueue model;
    private final OnlineMusicScrollRegion scroll;
    private final MusicTextControl edit, undo;
    private final List<Slot> slots = new ArrayList<>();
    private MusicFrontendQueue.State displayed;
    private boolean visible;
    private Component feedback = Component.empty();

    MusicFrontendQueuePanel(PreviewContext host, Bounds bounds, MusicFrontendQueue model) {
        this.host = host;
        this.bounds = bounds;
        this.model = model;
        int x = bounds.x() + GAP, width = bounds.width() - 2 * GAP;
        capacity = Math.min(20, Math.max(0, (bounds.height() - 46) / ROW));
        list = new Bounds(x, bounds.y() + 24, width, capacity * ROW);
        scroll = host.add(new OnlineMusicScrollRegion(list.x(), list.y(), list.width(), list.height(),
                queue("scroll"), offset -> {
                    if (!visible || displayed == null) return;
                    model.moveTo(displayed, capacity, offset);
                    refresh();
                }));
        scroll.setOnBrowse(() -> host.screen().setFocused(scroll));
        edit = button(new Bounds(x, bounds.y(), Math.min(90, width / 2), CONTROL), queue("edit"), () -> {
            if (!visible || displayed == null) return;
            model.toggleEditing(displayed);
            refresh();
        });
        undo = button(new Bounds(bounds.right() - 65, bounds.y(), 61, CONTROL), queue("undo"),
                () -> report(model.undo(displayed)));
        int spill = MusicScrollRows.spill(host, capacity);
        if (spill > 0) scroll.pixelRows(ROW, shift -> positionRows());
        for (int i = 0; i < capacity+spill; i++) slots.add(new Slot(i));
        refresh();
    }
    private MusicTextControl button(Bounds bounds, Component name, Runnable action) {
        return host.add(new MusicTextControl(bounds, name, host.preferences(), action));
    }
    private MusicTextControl rowButton(Bounds bounds, Component name, Runnable action) {
        return MusicScrollRows.add(host, new MusicTextControl(bounds, name, host.preferences(), action), () -> list);
    }
    private static Component music(String key, Object... args) {
        return Component.translatable("sh_better_ui.online_music." + key, args);
    }
    private static Component queue(String key, Object... args) { return music("queue." + key, args); }

    private final class Slot {
        MusicFrontendQueue.Row binding;
        final MusicTextControl track, up, down, remove;
        Slot(int index) {
            int y = list.y() + index * ROW, right = list.right() - RAIL;
            track = rowButton(new Bounds(list.x(), y, right - list.x(), CONTROL), Component.empty(), () -> action(Action.PLAY));
            track.setVariant(MusicTextControl.Variant.ROW);
            up = rowButton(new Bounds(right - 72, y, CONTROL, CONTROL), Component.literal("↑"), () -> action(Action.UP));
            down = rowButton(new Bounds(right - 48, y, CONTROL, CONTROL), Component.literal("↓"), () -> action(Action.DOWN));
            remove = rowButton(new Bounds(right - 24, y, CONTROL, CONTROL), queue("remove"), () -> action(Action.REMOVE));
            remove.setGlyphOnly(MusicTransportControl.Glyph.REMOVE);
        }
        void action(Action action) { if (visible && track.visible) report(model.invoke(binding, action)); }
        void position(int y, boolean editing) {
            track.setY(y); up.setY(y); down.setY(y); remove.setY(y);
            boolean shown = visible && binding != null && MusicScrollRows.intersects(y, CONTROL, list);
            track.setVisible(shown);
            up.setVisible(shown && editing);
            down.setVisible(shown && editing);
            remove.setVisible(shown && editing);
        }
        void bind(MusicFrontendQueue.Row row, boolean editing) {
            if (binding == null ? row != null : row == null || binding.index() != row.index()
                    || !MusicFrontendQueue.sameSnapshot(binding.state(), row.state())) cancel();
            binding = row;
            boolean shown = visible && row != null;
            track.setVisible(shown);
            up.setVisible(shown && editing);
            down.setVisible(shown && editing);
            remove.setVisible(shown && editing);
            if (!shown) return;
            track.setWidth(list.width() - RAIL - (editing ? 74 : 0));
            track.setAvailable(!editing);
            track.setSelected(row.index() == row.state().activeIndex());
            var title = MusicTypography.text(row.track().title());
            if (!title.equals(track.getMessage())) track.rebind(title);
            track.setSecondary(MusicTypography.text(row.track().artist()));
            up.setAvailable(editing && row.index() > 0);
            down.setAvailable(editing && row.index() + 1 < row.state().tracks().size());
            remove.setAvailable(editing);
            up.setPreview(queue("move_up", title)); down.setPreview(queue("move_down", title));
            remove.setPreview(queue("remove_track", title));
        }
        void cancel() { track.cancelInteraction(); up.cancelInteraction(); down.cancelInteraction(); remove.cancelInteraction(); }
    }
    void updateVisible(boolean nextVisible) {
        if (visible == nextVisible) return;
        visible = nextVisible;
        if (!visible) {
            scroll.cancelDrag();
            feedback = Component.empty();
            model.reset();
        }
        refresh();
    }
    void cancelInteraction() {
        scroll.cancelDrag();
        slots.forEach(Slot::cancel);
        for (var item : List.of(edit, undo)) item.cancelInteraction();
    }
    private void report(MusicFrontendQueue.Result result) {
        feedback = switch (result) {
            case APPLIED -> Component.empty();
            case BUSY -> music("redesign.busy");
            case CHANGED, UNAVAILABLE -> music("feedback.failed");
        };
        refresh();
    }
    private void refresh() {
        if (!visible) {
            for (var item : List.of(edit, undo)) item.setVisible(false);
            scroll.visible = false;
            slots.forEach(slot -> slot.bind(null, false));
            displayed = null;
            return;
        }
        var state = model.read();
        if (displayed != null && !MusicFrontendQueue.sameSnapshot(displayed, state)) {
            edit.cancelInteraction();
            undo.cancelInteraction();
        }
        displayed = state;
        int count = state.tracks().size(), offset = model.offset(state, capacity);
        boolean editing = model.editing();
        edit.setVisible(state.supportsEditing());
        edit.setAvailable(editing || count > 0);
        var title = queue(editing ? "done" : "edit");
        if (!title.equals(edit.getMessage())) edit.rebind(title);
        undo.setVisible(editing);
        undo.setAvailable(state.canUndo());
        scroll.visible = true;
        scroll.configure(offset, count, capacity);
        for (int i = 0; i < slots.size(); i++) {
            int index = offset + i;
            slots.get(i).bind(index < count ? new MusicFrontendQueue.Row(state, index, state.tracks().get(index)) : null, editing);
        }
        positionRows();
    }
    private void positionRows() {
        for (int i = 0; i < slots.size(); i++)
            slots.get(i).position(list.y() + i * ROW - scroll.rowShift(), model.editing());
    }

    void render(UiFrame frame, int muted) {
        if (!visible) return;
        refresh();
        scroll.tick();
        int count = displayed.tracks().size();
        Component status = !feedback.getString().isEmpty() ? feedback : count == 0 ? queue("empty") : queue("count", count);
        OnlineMusicViewPaint.line(frame, host.font(), status, bounds.x() + 4,
                bounds.bottom() - CONTROL + 6, bounds.width() - 8, muted);
    }
}
