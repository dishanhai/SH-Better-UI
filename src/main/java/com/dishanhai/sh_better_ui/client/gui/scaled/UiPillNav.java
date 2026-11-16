// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.controls.PillNavState;
import com.dishanhai.sh_better_ui.kernel.api.controls.PillNavState.*;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Pill navigation port. Mount as one native widget; bounds reserve the maximum dropdown area. */
public final class UiPillNav extends UiPillNavPlatform {
    private static final String KEY = "sh_better_ui.pill_nav.";
    /** Value maps the source href to a host-owned navigation action. No URL is opened by this control. */
    public record Item(String value, Component label, GraphicIcon icon) {
        public Item { Objects.requireNonNull(value); Objects.requireNonNull(label); }
        public Item(String value, Component label) { this(value, label, null); }
    }
    public record Logo(GraphicIcon icon, Component label, Runnable action) {
        public Logo { Objects.requireNonNull(icon); Objects.requireNonNull(label); Objects.requireNonNull(action); }
    }
    /** ARGB source colors. withHighlight automatically chooses ink from relative luminance. */
    public record Palette(boolean light, int surface, int solid, int edge, int rim, int text, int strong,
                          int pillTop, int pillBottom, int pillInk, int dot, int rowHover, int tileTop, int tileBottom) {
        public static final Palette DARK = new Palette(false, 0xA826232E, 0xFF26232E, 0x14FFFFFF, 0x12FFFFFF,
                0xA3F4F4F5, 0xFFF4F4F5, 0xFFFFFFFF, 0xFFEBEBF0, 0xFF121016, 0xFFF4F4F5,
                0x0FFFFFFF, 0xFF3A3644, 0xFF27242F);
        public static final Palette LIGHT = new Palette(true, 0xCCF0F0F3, 0xFFF0F0F3, 0x1218181B, 0xF2FFFFFF,
                0x9918181B, 0xFF18181B, 0xFF2C2C31, 0xFF18181B, 0xFFFFFFFF, 0xFF18181B,
                0x0D18181B, 0xFFFFFFFF, 0xFFF6F6F8);
        public Palette withHighlight(int color, Integer ink) {
            int contrast = ink == null ? contrastInk(color) : ink;
            return new Palette(light, surface, solid, edge, rim, text, strong, color, color, contrast, color,
                    rowHover, tileTop, tileBottom);
        }
        public Palette withBarColor(int color) {
            return new Palette(light, color, color, edge, rim, text, strong, pillTop, pillBottom, pillInk, dot,
                    rowHover, tileTop, tileBottom);
        }
        public Palette withTextColor(int color) {
            return new Palette(light, surface, solid, edge, rim, UiPillNavPaint.alpha(color, .64f), color,
                    pillTop, pillBottom, pillInk, dot, rowHover, tileTop, tileBottom);
        }
        private static int contrastInk(int color) {
            double luminance = 0;
            double[] weights = {.0722, .7152, .2126};
            for (int i = 0; i < 3; i++) {
                double channel = (color >>> (8 * i) & 255) / 255.0;
                luminance += weights[i] * (channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4));
            }
            return luminance > .36 ? 0xFF121016 : 0xFFFFFFFF;
        }
    }

    private final Font font;
    private final List<Item> items;
    private final Logo logo;
    private final PillNavState state;
    private final UiPillNavPaint painter = new UiPillNavPaint();
    private final BiConsumer<String, Integer> onSelect;
    private final Consumer<Boolean> onMenuToggle;
    private final float[] fills;
    private UiPreferences preferences;
    private Palette palette;
    private boolean menuReported, logoFocused, wasLogoHover;
    private float trackOffset;
    private long lastPaint = -1, spinStart = -1;

    public UiPillNav(Font font, Bounds area, Component name, List<Item> items, String selected, Logo logo,
                     Options options, Palette palette, UiPreferences preferences,
                     BiConsumer<String, Integer> onSelect, Consumer<Boolean> onMenuToggle) {
        super(area, name);
        this.font = Objects.requireNonNull(font);
        this.items = List.copyOf(items);
        if (this.items.stream().map(Item::value).distinct().count() != items.size()) {
            throw new IllegalArgumentException("Duplicate PillNav value");
        }
        this.logo = logo;
        this.palette = Objects.requireNonNull(palette);
        this.preferences = Objects.requireNonNull(preferences);
        this.onSelect = Objects.requireNonNull(onSelect);
        this.onMenuToggle = Objects.requireNonNull(onMenuToggle);
        state = new PillNavState(items.size(), indexOf(selected), options);
        fills = new float[items.size()];
        measure();
    }
    public String selectedValue() { return state.selected() < 0 ? null : items.get(state.selected()).value(); }
    public boolean isOpen() { return state.isOpen(); }
    public boolean isCollapsed() { measure(); return state.metrics().collapsed(); }
    public Options options() { return state.options(); }
    public void setOptions(Options options) { dismiss(); state.setOptions(options); measure(); }
    public void setPalette(Palette palette) { this.palette = Objects.requireNonNull(palette); }
    public void setPreferences(UiPreferences preferences) { this.preferences = Objects.requireNonNull(preferences); }
    /** Controlled href equivalent. Unknown values clear the active indicator. */
    public void setActiveValue(String value) { state.setSelected(indexOf(value), true); }
    /** Uncontrolled selection, without firing the user callback. */
    public void select(String value) { state.setSelected(indexOf(value), false); }
    public boolean cancelPress() { return state.cancel(); }
    public boolean dismiss() { boolean closed = state.dismiss(); reportMenu(); return closed; }
    public void cancelInteraction() { state.cancel(); dismiss(); state.hover(-1); }
    @Override public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) { logoFocused = false; cancelInteraction(); }
    }
    private boolean interactive() { return visible && active && Minecraft.getInstance().isWindowActive(); }
    private void measure() {
        var size = state.options().size();
        float scale = size.font / (float) font.lineHeight;
        float[] widths = new float[items.size()];
        for (int i = 0; i < widths.length; i++) {
            var item = items.get(i);
            widths[i] = font.width(item.label()) * scale + (item.icon() == null ? 0 : size.font * 1.6f);
        }
        state.layout(getWidth(), getHeight(), logo != null, widths);
        reportMenu();
    }
    private void reportMenu() {
        if (menuReported == state.isOpen()) return;
        menuReported = state.isOpen(); onMenuToggle.accept(menuReported);
    }
    private int indexOf(String value) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).value().equals(value)) return i;
        return -1;
    }
    private int toggleIndex() { return items.size(); }
    private int logoIndex() { return items.size() + 1; }
    private int header() { return state.options().size().height; }
    private boolean focusVisible() { return isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard(); }
    private int toggleLeft() {
        return getX() + Math.max(0, Math.round(state.metrics().width()) - header());
    }
    private int hit(double x, double y) {
        if (!interactive()) return -1;
        float px = (float) x - getX(), py = (float) y - getY();
        var m = state.metrics();
        if (px < 0 || px >= Math.min(getWidth(), m.width()) || py < 0) return -1;
        if (py < header()) {
            int padding = state.options().layout() == Layout.JOINED ? state.options().size().padding : 0;
            int side = header() - 2 * padding;
            if (m.collapsed() && x >= toggleLeft() && PillNavState.containsPill(
                    x - toggleLeft() - padding, py - padding, side, side)) return toggleIndex();
            if (logo != null && PillNavState.containsPill(px - padding, py - padding, side, side)) return logoIndex();
            if (!m.collapsed()) {
                int pad = state.options().size().padding;
                if (py < pad || py >= header() - pad) return -1;
                for (int i = 0; i < items.size(); i++) {
                    var slot = m.slots().get(i);
                    if (px + trackOffset >= slot.left() && px + trackOffset < slot.left() + slot.width()
                            && px >= m.trackLeft() && PillNavState.containsPill(px + trackOffset - slot.left(),
                            py - pad, slot.width(), state.options().size().inner())) return i;
                }
            }
        } else if (m.collapsed() && state.isOpen() && py >= header() + 10) {
            int row = (int) (py - header() - 10) / m.rowHeight();
            if (row < m.visibleRows() && px >= 6 && px < m.width() - 6
                    && (py - header() - 10) % m.rowHeight() < header() + 2) return row + state.firstRow();
        }
        return -1;
    }
    @Override public boolean isMouseOver(double x, double y) { measure(); return hit(x, y) >= 0; }
    @Override boolean pointerDown(double x, double y) {
        measure();
        int hit = hit(x, y);
        if (hit < 0) { dismiss(); return false; }
        logoFocused = hit == logoIndex();
        return state.press(hit);
    }
    @Override boolean pointerUp(double x, double y) {
        if (state.pressed() < 0) return false;
        int index = state.release(hit(x, y));
        if (index >= 0) activate(index);
        return true;
    }
    @Override boolean pointerDrag(double x, double y) {
        if (state.pressed() < 0) return false;
        if (hit(x, y) != state.pressed()) state.cancel();
        return true;
    }
    @Override boolean scroll(double x, double y, double delta) {
        if (!state.isOpen() || hit(x, y) < 0 || delta == 0) return false;
        state.scroll(delta < 0 ? 1 : -1); return true;
    }
    @Override boolean key(int key) {
        if (!isFocused() || !interactive()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) return state.cancel() || dismiss();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            activate(logoFocused ? logoIndex() : isCollapsed() && !state.isOpen() ? toggleIndex() : state.focused());
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
            logoFocused = key == GLFW.GLFW_KEY_HOME && logo != null && !state.isOpen();
            state.focusEdge(key == GLFW.GLFW_KEY_END); return true;
        }
        int direction = switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> -1;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> 1;
            default -> 0;
        };
        if (direction == 0) return false;
        if (isCollapsed() && !state.isOpen() && !logoFocused) { state.toggle(); reportMenu(); }
        else if (logoFocused) logoFocused = false;
        else if (logo != null && !state.isOpen() && direction < 0 && state.focused() == 0) logoFocused = true;
        else state.moveFocus(direction);
        return true;
    }
    private void activate(int index) {
        if (index == toggleIndex()) { state.toggle(); reportMenu(); }
        else if (index == logoIndex() && logo != null) logo.action().run();
        else if (state.activate(index) >= 0) {
            reportMenu(); onSelect.accept(items.get(index).value(), index);
        }
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        Component selected = state.focused() < 0 ? getMessage() : items.get(state.focused()).label();
        output.add(NarratedElementType.TITLE, logoFocused && logo != null ? logo.label()
                : Component.translatable(KEY + "narration", getMessage(), selected,
                Component.translatable(KEY + (state.isOpen() ? "close" : isCollapsed() ? "open" : "navigation"))));
        output.add(NarratedElementType.USAGE, Component.translatable(KEY + "usage"));
    }

    @Override void paint(PillNavCanvas c, int mx, int my) {
        measure();
        if (!interactive()) cancelInteraction();
        long now = System.nanoTime() / 1_000_000;
        float dt = lastPaint < 0 ? 1 / 60f : Math.max(0, Math.min(.05f, (now - lastPaint) / 1000f));
        lastPaint = now;
        int hit = hit(mx, my);
        tooltip(hit == logoIndex() && logo != null ? logo.label()
                : hit >= 0 && hit < items.size() ? items.get(hit).label()
                : hit == toggleIndex() ? Component.translatable(KEY + (state.isOpen() ? "close" : "open")) : null,
                preferences.feedback().tooltipDelayMs());
        if (state.pressed() >= 0 && hit != state.pressed()) state.cancel();
        state.hover(!focusVisible() && hit < items.size() ? hit : -1);
        if (hit == logoIndex() && !wasLogoHover && (spinStart < 0 || now - spinStart >= 900)) spinStart = now;
        wasLogoHover = hit == logoIndex();
        var frame = state.sample(now, preferences.reducedMotion(), focusVisible() && !logoFocused);
        var m = state.metrics();
        var o = state.options();
        var s = o.size();
        int width = Math.min(getWidth(), Math.round(m.width()));
        if (width <= 0 || getHeight() < header()) return;
        int reveal = m.collapsed() ? width : Math.min(width, Math.round((o.layout() == Layout.JOINED ? 0 : m.barLeft())
                + header() + (width - (o.layout() == Layout.JOINED ? 0 : m.barLeft()) - header()) * frame.intro()));
        float panel = frame.panel();
        if (m.collapsed() && panel > .001f) paintPanel(c, frame, width, hit);
        if (o.layout() == Layout.JOINED) {
            if (panel < .001f) surface(c, getX(), getY(), reveal, header(), header() / 2f);
        } else if (!m.collapsed()) {
            surface(c, getX() + Math.round(m.barLeft()), getY(), reveal - Math.round(m.barLeft()), header(), header() / 2f);
        }
        if (logo != null) {
            int side = o.layout() == Layout.JOINED ? s.inner() : header();
            int pad = o.layout() == Layout.JOINED ? s.padding : 0;
            int x = getX() + pad, y = getY() + pad;
            if (o.layout() == Layout.SPLIT) surface(c, x, y, side, side, side / 2f);
            float grow = frame.intro();
            float mark = s.mark * (.6f + .4f * grow);
            float turn = preferences.reducedMotion() || spinStart < 0 || now - spinStart >= 900 ? 0
                    : 360 * (float) ((1 - Math.cos(Math.PI * (now - spinStart) / 900.0)) / 2);
            c.icon(logo.icon(), x + (side - mark) / 2, y + (side - mark) / 2, mark, turn,
                    UiPillNavPaint.alpha(palette.strong(), grow), preferences);
            if (focusVisible() && logoFocused) ring(c, x, y, side, side, palette.strong());
        }
        if (m.collapsed()) { paintToggle(c, width, panel, hit); return; }
        if (m.width() <= getWidth()) trackOffset = 0;
        else if (focusVisible() && state.focused() >= 0) {
            var slot = m.slots().get(state.focused());
            trackOffset = Math.max(slot.left() + slot.width() - width + s.padding,
                    Math.min(trackOffset, slot.left() - m.trackLeft()));
            trackOffset = Math.max(0, trackOffset);
        }
        if (reveal > m.trackLeft()) {
            c.clip(getX() + Math.round(m.trackLeft()), getY(), getX() + reveal, getY() + header(),
                    () -> paintTrack(c, frame, dt, hit));
        }
    }

    private void paintTrack(PillNavCanvas c, Frame f, float dt, int hit) {
        var o = state.options(); var s = o.size();
        int y = getY() + s.padding, h = s.inner();
        int lit = focusVisible() && !logoFocused ? state.focused() : hit >= 0 && hit < items.size() ? hit : -1;
        for (int i = 0; i < items.size(); i++) {
            var slot = state.metrics().slots().get(i);
            float entrance = !o.intro() || preferences.reducedMotion() ? 1
                    : PillNavState.ease((f.introAge() - 200 - i * 50) / 560f);
            int x = getX() + Math.round(slot.left() - trackOffset - 10 * (1 - entrance)), w = Math.round(slot.width());
            if (o.itemStyle() == ItemStyle.PILL) {
                painter.gradient(c, x, y, w, h, h / 2f,
                        UiPillNavPaint.alpha(palette.tileTop(), entrance), UiPillNavPaint.alpha(palette.tileBottom(), entrance));
                c.box(x, y, w, h, h / 2f, 1, UiPillNavPaint.alpha(palette.edge(), entrance), 0);
            }
            boolean filled = o.hoverEffect() == HoverEffect.FILL && (i == lit || o.indicator() == Indicator.PILL && i == state.selected());
            fills[i] = PillNavState.approach(fills[i], filled ? 1 : 0, dt, filled ? .09f : .07f, preferences.reducedMotion());
            if (o.hoverEffect() == HoverEffect.FILL) painter.fill(c, x, y, w, h, fills[i],
                    UiPillNavPaint.alpha(palette.pillTop(), entrance), UiPillNavPaint.alpha(palette.pillBottom(), entrance));
            float roll = (s.inner() / 2f + .7f * s.font) * PillNavState.ease(fills[i]);
            int ink = i == state.selected() || i == lit || o.itemStyle() == ItemStyle.PILL ? palette.strong() : palette.text();
            int index = i;
            c.clip(x, y, x + w, y + h, () -> {
                label(c, items.get(index), x, y - roll, w, h, UiPillNavPaint.alpha(readable(ink, palette.solid()), entrance));
                if (o.hoverEffect() == HoverEffect.FILL) label(c, items.get(index), x,
                        y + s.inner() / 2f + .7f * s.font - roll, w, h,
                        UiPillNavPaint.alpha(readable(palette.pillInk(), palette.pillBottom()), entrance));
            });
            if (focusVisible() && !logoFocused && state.focused() == i) ring(c, x, y, w, h, palette.strong());
        }
        if (f.alpha() > .001f) {
            float w = f.width() * f.scale(), h2 = h * f.scale() * f.squash();
            float x = getX() + f.left() - trackOffset + (f.width() - w) / 2, top = y + (h - h2) / 2;
            painter.shadow(c, x, top, w, h2, h2 / 2, palette.light());
            painter.gradient(c, x, top, Math.round(w), Math.round(h2), h2 / 2,
                    UiPillNavPaint.alpha(palette.pillTop(), f.alpha()), UiPillNavPaint.alpha(palette.pillBottom(), f.alpha()));
            UiPillNavPaint.clipPill(c, x, top, w, h2, () -> {
                for (int i = 0; i < items.size(); i++) {
                    var slot = state.metrics().slots().get(i);
                    float itemX = getX() + slot.left() - trackOffset;
                    if (itemX + slot.width() <= x || itemX >= x + w) continue;
                    label(c, items.get(i), itemX, y, slot.width(), h,
                            UiPillNavPaint.alpha(readable(palette.pillInk(), palette.pillBottom()), f.alpha()));
                }
            });
        }
        if (f.dotAlpha() > .001f) {
            float dotWidth = 4 * f.dotStretch();
            int dot = o.hoverEffect() == HoverEffect.FILL && lit == state.selected() ? palette.pillInk() : palette.dot();
            c.box(getX() + f.dotX() - trackOffset - dotWidth / 2, y + h - (h - s.font) / 4f - 2,
                    dotWidth, 4, 2, 0, 0, UiPillNavPaint.alpha(dot, f.dotAlpha()));
        }
    }
    private void paintToggle(PillNavCanvas c, int width, float panel, int hit) {
        var s = state.options().size();
        int pad = state.options().layout() == Layout.JOINED ? s.padding : 0;
        int side = header() - 2 * pad, x = getX() + width - header() + pad, y = getY() + pad;
        if (state.options().layout() == Layout.SPLIT) surface(c, x, y, side, side, side / 2f);
        int ink = palette.strong();
        float cx = x + side / 2f, cy = y + side / 2f;
        // The two lines converge into a cross, using small round segments on both backends.
        for (int line = 0; line < 2; line++) {
            float sign = line == 0 ? -1 : 1;
            for (int point = -8; point <= 8; point++) {
                c.box(cx + point - .75f, cy + sign * ((1 - panel) * 2.75f + panel * point * .7f) - .75f,
                        1.5f, 1.5f, .75f, 0, 0, ink);
            }
        }
        if (focusVisible() && !logoFocused && !state.isOpen()) ring(c, x, y, side, side, ink);
    }
    private void paintPanel(PillNavCanvas c, Frame frame, int width, int hit) {
        var m = state.metrics();
        int fullHeight = header() + 16 + m.visibleRows() * m.rowHeight();
        int height = Math.round(header() + (fullHeight - header()) * frame.panel());
        int left = state.options().layout() == Layout.JOINED ? getX()
                : getX() + Math.round((width - header()) * (1 - frame.panel()));
        c.clip(left, getY(), getX() + width, getY() + height, () -> {
            surface(c, getX(), getY(), width, fullHeight, 24);
            for (int row = 0; row < m.visibleRows(); row++) {
                int i = state.firstRow() + row;
                int y = getY() + header() + 10 + row * m.rowHeight();
                boolean selected = i == state.selected();
                if (selected) painter.gradient(c, getX() + 6, y, width - 12, header() + 2, 18, palette.pillTop(), palette.pillBottom());
                else if (i == hit) c.box(getX() + 6, y, width - 12, header() + 2, 18, 0, 0, palette.rowHover());
                label(c, items.get(i), getX() + 6, y - 8 * (1 - frame.panel()), width - 12, header() + 2,
                        readable(selected ? palette.pillInk() : palette.strong(), selected ? palette.pillBottom() : palette.solid()));
                if (focusVisible() && state.isOpen() && state.focused() == i) ring(c, getX() + 6, y, width - 12, header() + 2,
                        selected ? palette.pillInk() : palette.strong());
            }
        });
    }
    private void surface(PillNavCanvas c, int x, int y, int w, int h, float radius) {
        if (w <= 0 || h <= 0 || state.options().surface() == Surface.NONE) return;
        painter.shadow(c, x, y, w, h, radius, palette.light());
        boolean glass = state.options().surface() == Surface.GLASS && preferences.useBackdropBlur()
                && c.glass(x, y, w, h, radius, palette.surface(), preferences);
        int fill = glass ? palette.surface() : palette.solid();
        painter.gradient(c, x, y, w, h, radius, fill, fill);
        c.box(x, y, w, h, radius, 1, preferences.highContrast() ? palette.strong() : palette.edge(), 0);
        c.clip(x, y, x + w, y + 1, () -> c.box(x, y, w, h, radius, 1, palette.rim(), 0));
    }
    private void label(PillNavCanvas c, Item item, float x, float y, float w, float h, int ink) {
        if ((ink >>> 24) < 4) return;
        float scale = state.options().size().font / (float) font.lineHeight;
        float icon = item.icon() == null ? 0 : state.options().size().font * 1.15f;
        float gap = icon == 0 ? 0 : state.options().size().font * .45f;
        int budget = Math.max(0, (int) ((w - 12 - icon - gap) / scale));
        FormattedText text = item.label();
        if (font.width(text) > budget) {
            int ellipsis = font.width(CommonComponents.ELLIPSIS);
            text = budget < ellipsis ? Component.empty() : FormattedText.composite(
                    font.substrByWidth(item.label(), budget - ellipsis), CommonComponents.ELLIPSIS);
        }
        float textWidth = font.width(text) * scale;
        float left = x + (w - textWidth - icon - gap) / 2;
        if (icon > 0) c.icon(item.icon(), left, y + (h - icon) / 2, icon, 0, ink, preferences);
        c.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(text), left + icon + gap,
                y + (h - font.lineHeight * scale) / 2, scale, ink);
    }
    private int readable(int ink, int fill) {
        return preferences.highContrast() ? ContrastPolicy.readable(ink, fill, fill, ContrastPolicy.HIGH_CONTRAST_RATIO) : ink;
    }
    private static void ring(PillNavCanvas c, float x, float y, float w, float h, int ink) {
        c.box(x + 1, y + 1, w - 2, h - 2, h / 2f - 1, 2, ink, 0);
    }
}
