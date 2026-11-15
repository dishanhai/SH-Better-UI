// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.api.controls.FolderFloatMotion;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Folder Float 的共享交互和绘制逻辑。原生入口为 ControlsApi.folderFloat。 */
public final class FolderFloatControl<T> {
    public enum Trigger { HOVER, CLICK }

    public record Item<T>(Component label, T value) {
        public Item { Objects.requireNonNull(label, "label"); }
    }

    public record Options(Trigger trigger, boolean defaultOpen, boolean closeOnSelect, boolean physics, double drift) {
        public static final Options DEFAULT = new Options(Trigger.HOVER, false, true, true, 0.5);

        public Options {
            Objects.requireNonNull(trigger, "trigger");
            if (!(drift >= 0 && drift <= 1)) throw new IllegalArgumentException("drift must be in [0, 1]");
        }
    }

    @FunctionalInterface
    interface ShapePainter {
        void draw(float x, float y, float width, float height, float radius, int edge, int fill);
    }

    @FunctionalInterface
    interface TextPainter {
        void draw(FormattedCharSequence text, int x, int y, int color);
    }

    public static final int MIN_WIDTH = 72;
    public static final int MIN_HEIGHT = 78;
    private static final int FOLDER_HEIGHT = 43;
    private static final int SIDE_PADDING = 4;
    private static final String KEY = "sh_better_ui.folder_float.";

    private final Font font;
    private final Component label;
    private final Component itemCount;
    private Component sublabel;
    private final UiPreferences preferences;
    private final List<Item<T>> items;
    private final Options options;
    private final BiConsumer<T, Integer> onSelect;
    private final Consumer<Boolean> onOpenChange;
    private final FolderFloatMotion motion;
    private final int width, height, cloudHeight;
    private final int folderX, folderWidth;
    private int keyboardIndex = -1;
    private boolean pinned;
    private boolean suppressHover;

    FolderFloatControl(Font font, int width, int height, Component label, Component sublabel,
                       List<Item<T>> items, Options options, UiPreferences preferences,
                       BiConsumer<T, Integer> onSelect, Consumer<Boolean> onOpenChange) {
        if (width < MIN_WIDTH || height < MIN_HEIGHT) throw new IllegalArgumentException("Folder Float area");
        this.font = Objects.requireNonNull(font);
        this.label = Objects.requireNonNull(label);
        this.sublabel = Objects.requireNonNull(sublabel);
        this.items = List.copyOf(items);
        int size = this.items.size();
        itemCount = size == 1 ? Component.translatable(KEY + "one_item")
                : Component.translatable(KEY + "item_count", size);
        this.options = Objects.requireNonNull(options);
        this.preferences = Objects.requireNonNull(preferences);
        this.onSelect = Objects.requireNonNull(onSelect);
        this.onOpenChange = Objects.requireNonNull(onOpenChange);
        this.width = width;
        this.height = height;
        cloudHeight = height - FOLDER_HEIGHT - 6;
        folderWidth = Math.min(136, width - 2 * SIDE_PADDING);
        folderX = (width - folderWidth) / 2;
        int[] widths = this.items.stream().mapToInt(item -> font.width(item.label()) + 18).toArray();
        pinned = options.defaultOpen();
        motion = new FolderFloatMotion(width - 2 * SIDE_PADDING, cloudHeight, widths, pinned);
    }

    void setSublabel(Component text) { sublabel = Objects.requireNonNull(text); }
    boolean isOpen() { return motion.isOpen(); }
    boolean isPressed() { return motion.isPressed(); }
    boolean reducedMotion() { return preferences.reducedMotion(); }
    boolean canDrag() { return options.physics() && !reducedMotion(); }
    int cloudHeight() { return cloudHeight; }
    static Component usage() { return Component.translatable(KEY + "keys"); }

    private boolean inside(double x, double y) { return x >= 0 && x < width && y >= 0 && y < height; }

    private boolean folder(double x, double y) {
        return x >= folderX && x < folderX + folderWidth && y >= height - FOLDER_HEIGHT && y < height;
    }

    boolean hit(double x, double y) { return folder(x, y) || pillAt(x, y) >= 0; }

    private int pillAt(double x, double y) { return motion.hit(x - SIDE_PADDING, y); }

    void update(double x, double y, long now, boolean active) {
        if (!active) {
            motion.pause();
            return;
        }
        if (options.trigger() == Trigger.HOVER && !isPressed()) {
            if (!inside(x, y)) suppressHover = false;
            if (!suppressHover && folder(x, y)) setOpen(true);
            if (!pinned && !inside(x, y)) setOpen(false);
        }
        motion.advance(now, options.physics(), options.drift(), reducedMotion());
    }

    boolean press(double x, double y, long now) {
        if (!motion.press(x - SIDE_PADDING, y, now)) return false;
        keyboardIndex = -1;
        return true;
    }

    void drag(double x, double y, long now) { motion.drag(x - SIDE_PADDING, y, now, canDrag()); }

    void release(double x, double y, long now) {
        int index = motion.release(x - SIDE_PADDING, y, now, canDrag());
        if (index >= 0) pick(index);
    }

    void cancel() { motion.cancel(); }

    boolean activate() {
        if (isPressed()) return false;
        if (isOpen() && keyboardIndex >= 0) pick(keyboardIndex);
        else toggle();
        return true;
    }

    void toggle() {
        if (isOpen() && !pinned && options.trigger() == Trigger.HOVER) {
            pinned = true;
        } else if (isOpen()) {
            close();
        } else {
            pinned = true;
            suppressHover = false;
            setOpen(true);
        }
    }

    boolean dismiss() {
        if (isPressed()) cancel();
        else if (isOpen()) close();
        else return false;
        return true;
    }

    boolean moveFocus(int direction) {
        if (!isOpen() || items.isEmpty() || isPressed()) return false;
        pinned = true;
        keyboardIndex = keyboardIndex < 0 ? (direction > 0 ? 0 : items.size() - 1)
                : Math.floorMod(keyboardIndex + direction, items.size());
        return true;
    }

    Component narration() {
        Component status = Component.translatable(KEY + (isOpen() ? "expanded" : "collapsed"));
        if (isOpen() && keyboardIndex >= 0) {
            return CommonComponents.joinForNarration(label, status, items.get(keyboardIndex).label(),
                    Component.translatable(KEY + "position", keyboardIndex + 1, items.size()));
        }
        return CommonComponents.joinForNarration(label, status, subtitle());
    }

    private Component subtitle() {
        return sublabel.getString().isEmpty() ? itemCount : sublabel;
    }

    private void close() {
        pinned = false;
        suppressHover = true;
        setOpen(false);
    }

    private void pick(int index) {
        if (options.closeOnSelect()) close();
        onSelect.accept(items.get(index).value(), index);
    }

    private void setOpen(boolean open) {
        if (isOpen() == open) return;
        motion.setOpen(open);
        if (!open) keyboardIndex = -1;
        onOpenChange.accept(open);
    }

    void drawPills(int left, int top, double mouseX, double mouseY, boolean focused, UiTheme theme,
                   ShapePainter shape, TextPainter text) {
        int over = pillAt(mouseX, mouseY);
        for (int i = items.size() - 1; i >= 0; i--) {
            var pill = motion.pill(i);
            if (pill.progress() == 0) continue;
            float x = left + SIDE_PADDING + pill.x();
            float y = top + pill.y();
            boolean keyboardFocus = focused && i == keyboardIndex;
            boolean highlighted = keyboardFocus || i == over || i == motion.pressedIndex();
            int edge = outline(theme, highlighted);
            int fill = theme.controlFill(0xFF34343D, i == motion.pressedIndex() ? 0.42f : highlighted ? 0.20f : 0);
            shape.draw(x, y, pill.width(), FolderFloatMotion.PILL_HEIGHT, 10, edge, fill);
            if (keyboardFocus) shape.draw(x + 4, y + 8, 4, 4, 2, edge, edge);
            drawText(items.get(i).label(), (int) x, (int) y + 6, pill.width(), theme.text(0xFFF5F5F5), text);
        }
    }

    void drawFolder(int left, int top, double mouseX, double mouseY, boolean focused, UiTheme theme,
                    ShapePainter shape, TextPainter text) {
        int x = left + folderX, y = top + height - FOLDER_HEIGHT;
        float flap = motion.flap();
        boolean highlighted = focused || folder(mouseX, mouseY);
        int edge = outline(theme, highlighted);
        int back = theme.surface(0xFF3F3F49);
        int front = theme.controlFill(0xFF545462, highlighted ? 0.16f : 0);
        shape.draw(x, y, folderWidth * 0.41f, 14, 4, edge, back);
        shape.draw(x, y + 6, folderWidth, FOLDER_HEIGHT - 6, 7, edge, back);
        shape.draw(x + 7, y + 8 - flap * 8, folderWidth - 14, 23, 4, edge, theme.text(0xFFF5F5F5));
        shape.draw(x, y + 10 + flap * 8, folderWidth, FOLDER_HEIGHT - 10 - flap * 8, 7, edge, front);
        int titleY = y + 14 + Math.round(7 * flap);
        drawText(label, x, titleY, folderWidth, theme.text(0xFFF5F5F5), text);
        drawText(subtitle(), x, titleY + 10, folderWidth,
                preferences.highContrast() ? theme.text(0xFFF5F5F5) : theme.secondaryText(0xFFDADAE3, 0.25f), text);
    }

    private int outline(UiTheme theme, boolean highlighted) {
        if (preferences.highContrast()) return theme.text(0xFFFFFFFF);
        return highlighted ? theme.accent(0xFFA3B8FF) : theme.border(0xFF787887);
    }

    private void drawText(Component label, int x, int y, int width, int color, TextPainter paint) {
        int available = Math.max(1, width - 12);
        var text = label.getVisualOrderText();
        if (font.width(text) > available) {
            var lines = font.split(label, available);
            if (lines.isEmpty()) return;
            text = lines.get(0);
        }
        paint.draw(text, x + (width - font.width(text)) / 2, y, color);
    }
}
