// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.api.controls.CardNavMotion;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Shared CardNav data, measured layout, input and painting. Dimensions are GUI units (half CSS pixels). */
public final class CardNavControl {
    public enum Theme { DARK, LIGHT }
    public enum Surface { GLASS, SOLID }
    public enum Trigger { CLICK, HOVER }

    /** The host owns navigation, including URL validation and confirmation where appropriate. */
    public record Link(Component label, Component ariaLabel, GraphicIcon icon, Runnable action) {
        public Link { Objects.requireNonNull(label); Objects.requireNonNull(ariaLabel); Objects.requireNonNull(action); }
        public Link(Component label, Runnable action) { this(label, label, null, action); }
        public Link(Component label, Component ariaLabel, Runnable action) { this(label, ariaLabel, null, action); }
        public Link(Component label, GraphicIcon icon, Runnable action) { this(label, label, icon, action); }
    }

    public record Item(Component label, Component description, List<Link> links, Integer background, Integer textColor) {
        public Item {
            Objects.requireNonNull(label); Objects.requireNonNull(description);
            links = List.copyOf(links);
        }
        public Item(Component label, Component description, List<Link> links) { this(label, description, links, null, null); }
    }

    public record Options(Theme theme, Surface surface, Trigger trigger, CardNavMotion.Settings motion,
                          int cardHeight, int maxWidth, Integer accentColor, boolean menuLabel,
                          boolean intro, boolean defaultOpen) {
        public static final Options DEFAULT = new Options(Theme.DARK, Surface.GLASS, Trigger.CLICK,
                CardNavMotion.Settings.DEFAULT, 88, 400, null, true, true, false);
        public Options {
            Objects.requireNonNull(theme); Objects.requireNonNull(surface); Objects.requireNonNull(trigger);
            Objects.requireNonNull(motion);
            if (cardHeight < 1 || maxWidth < 80) throw new IllegalArgumentException("CardNav dimensions");
        }
    }

    interface Painter {
        void shape(float x, float y, float w, float h, float radius, int edge, int fill);
        void text(FormattedCharSequence text, int x, int y, int color);
        void icon(GraphicIcon icon, int x, int y, int color);
        void clip(int x, int y, int right, int bottom);
        void unclip();
        void begin(float x, float y, float sx, float sy, float opacity, float blur);
        void end();
    }

    record Rect(int x, int y, int w, int h) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private record Target(Rect area, Link link, int card) {}
    private record Tile(Rect area, Item item, List<FormattedCharSequence> description) {}

    static final int BAR = 30;
    private static final int INSET = 4, GAP = 4, PAD = 8, LINK_HEIGHT = 15;
    private static final String KEY = "sh_better_ui.card_nav.";
    private final Font font;
    private final Component name;
    private final Link logo, cta;
    private final List<Item> items;
    private Options options;
    private final UiPreferences preferences;
    private final Consumer<Boolean> onOpenChange;
    private final CardNavMotion motion;
    private final List<Tile> tiles = new ArrayList<>();
    private final List<Target> targets = new ArrayList<>();
    private int width, viewportHeight, contentHeight, scroll;
    private boolean narrow, suppressHover, keyboard, wasInside;
    private int focus, pressed = -1, hovered = -1;
    private Rect menu;
    private long leave = -1, introStart = -1;
    private BooleanSupplier openController;
    private double[] hoverMotion = new double[0];
    private long lastHoverFrame = -1;

    CardNavControl(Font font, int width, int height, Component name, Link logo, List<Item> items, Link cta,
                   Options options, UiPreferences preferences, Consumer<Boolean> onOpenChange) {
        this.font = Objects.requireNonNull(font);
        this.name = Objects.requireNonNull(name);
        this.logo = logo;
        this.cta = cta;
        this.items = List.copyOf(items);
        this.options = Objects.requireNonNull(options);
        this.preferences = Objects.requireNonNull(preferences);
        this.onOpenChange = Objects.requireNonNull(onOpenChange);
        motion = new CardNavMotion(options.defaultOpen());
        resize(width, height);
    }

    void resize(int availableWidth, int availableHeight) {
        if (availableWidth < 80 || availableHeight < BAR) throw new IllegalArgumentException("CardNav area");
        width = Math.min(availableWidth, options.maxWidth());
        viewportHeight = availableHeight;
        narrow = width < 280 || !items.isEmpty() && (width - 2 * INSET - GAP * (items.size() - 1)) / items.size() < 76;
        menu = new Rect(narrow ? width - menuWidth() - INSET : INSET, INSET, menuWidth(), BAR - 2 * INSET);
        tiles.clear();
        targets.clear();
        targets.add(new Target(menu, null, -1));
        if (logo != null) {
            int left = narrow ? INSET + 7 : menu.x + menu.w + GAP;
            int right = narrow ? menu.x - GAP : width - INSET - (cta == null ? 0 : ctaWidth() + GAP);
            int w = Math.max(0, Math.min(logo.icon() == null ? font.width(logo.label()) : 12, right - left));
            int x = narrow ? left : Math.max(left, Math.min((width - w) / 2, right - w));
            targets.add(new Target(new Rect(x, INSET, w, BAR - 2 * INSET), logo, -1));
        }
        if (cta != null && !narrow) targets.add(new Target(new Rect(width - ctaWidth() - INSET, INSET,
                ctaWidth(), BAR - 2 * INSET), cta, -1));
        int inner = width - 2 * INSET;
        int cardWidth = narrow ? inner : Math.max(1, (inner - GAP * (items.size() - 1)) / Math.max(1, items.size()));
        int[] heights = new int[items.size()];
        int maxHeight = 0;
        for (int i = 0; i < items.size(); i++) {
            var description = font.split(items.get(i).description(), Math.max(1, cardWidth - 2 * PAD));
            int linkHeight = narrow ? linkRows(items.get(i), cardWidth - 2 * PAD) * LINK_HEIGHT
                    : items.get(i).links().size() * LINK_HEIGHT;
            heights[i] = Math.max(narrow ? 0 : options.cardHeight(),
                    2 * PAD + font.lineHeight + 3 + description.size() * font.lineHeight + 9 + linkHeight);
            maxHeight = Math.max(maxHeight, heights[i]);
        }
        int y = BAR;
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            int x = INSET + (narrow ? 0 : i * (cardWidth + GAP));
            int w = !narrow && i == items.size() - 1 ? width - INSET - x : cardWidth;
            int h = narrow ? heights[i] : maxHeight;
            var rect = new Rect(x, y, w, h);
            tiles.add(new Tile(rect, item, font.split(item.description(), Math.max(1, w - 2 * PAD))));
            int lx = PAD, ly = h - PAD - (narrow ? linkRows(item, w - 2 * PAD) : item.links().size()) * LINK_HEIGHT;
            for (var link : item.links()) {
                int lw = narrow ? Math.min(w - 2 * PAD, font.width(link.label()) + 14) : w - 2 * PAD;
                if (narrow && lx > PAD && lx + lw > w - PAD) { lx = PAD; ly += LINK_HEIGHT; }
                targets.add(new Target(new Rect(x + lx, y + ly, lw, LINK_HEIGHT), link, i));
                if (narrow) lx += lw + 8;
                else ly += LINK_HEIGHT;
            }
            if (narrow) y += h + GAP;
        }
        int bottom = items.isEmpty() ? BAR : narrow ? y - GAP : BAR + maxHeight;
        if (cta != null && narrow) {
            int cy = bottom + (items.isEmpty() ? 0 : GAP);
            targets.add(new Target(new Rect(INSET, cy, inner, 22), cta, items.size()));
            bottom = cy + 22;
        }
        contentHeight = bottom - BAR + INSET;
        scroll = Math.min(scroll, maxScroll());
        hoverMotion = new double[targets.size()];
        focus = Math.min(focus, targets.size() - 1);
        cancel();
    }

    private int menuWidth() { return options.menuLabel() ? Math.min(width / 3, 26 + Math.max(font.width(label("menu")), font.width(label("close")))) : 22; }
    private int ctaWidth() { return Math.min(width / 3, font.width(cta.label()) + 16); }
    private int linkRows(Item item, int available) {
        int used = 0, rows = item.links().isEmpty() ? 0 : 1;
        for (var link : item.links()) {
            int w = Math.min(available, font.width(link.label()) + 14);
            if (used > 0 && used + w > available) { rows++; used = 0; }
            used += w + 8;
        }
        return rows;
    }

    boolean isOpen() { return motion.isOpen(); }
    void setOpenController(BooleanSupplier controller) { openController = controller; }
    void setOptions(Options options, int width, int height) {
        this.options = Objects.requireNonNull(options);
        resize(width, height);
    }
    int width() { return width; }
    int shownHeight() { return Math.min(viewportHeight, BAR + (int) Math.ceil(Math.max(0, motion.progress()) * contentHeight)); }
    int maxScroll() { return Math.max(0, contentHeight - (viewportHeight - BAR)); }
    void cancel() { pressed = -1; leave = -1; keyboard = false; motion.pause(); }
    void setOpen(boolean value) {
        if (isOpen() == value) return;
        if (openController != null) { onOpenChange.accept(value); return; }
        applyOpen(value);
        onOpenChange.accept(value);
    }
    private void applyOpen(boolean value) {
        motion.setOpen(value);
        pressed = -1;
        leave = -1;
        if (!value) { focus = 0; suppressHover = true; }
    }

    void update(double x, double y, long now, boolean enabled) {
        if (introStart < 0) introStart = now;
        if (openController != null && openController.getAsBoolean() != isOpen()) applyOpen(openController.getAsBoolean());
        if (!enabled) { cancel(); hovered = -1; return; }
        hovered = hit(x, y);
        double hoverStep = lastHoverFrame < 0 ? 1.0 / 60 : Math.max(0, Math.min(.05, (now - lastHoverFrame) / 1000.0));
        lastHoverFrame = now;
        for (int i = 0; i < hoverMotion.length; i++) {
            double target = hovered == i || keyboard && focus == i ? 1 : 0;
            hoverMotion[i] += (target - hoverMotion[i]) * (preferences.reducedMotion() ? 1 : 1 - Math.exp(-hoverStep * 14));
        }
        boolean inside = x >= 0 && x < width && y >= 0 && y < shownHeight();
        if (options.trigger() == Trigger.HOVER) {
            if (inside) {
                leave = -1;
                if (!suppressHover && !wasInside) setOpen(true);
            } else {
                suppressHover = false;
                if (isOpen() && !keyboard) {
                    if (leave < 0) leave = now;
                    if (now - leave >= 160) setOpen(false);
                }
            }
        }
        wasInside = inside;
        motion.advance(now, options.motion(), preferences.reducedMotion());
    }

    int hit(double x, double y) {
        if (x < 0 || x >= width || y < 0 || y >= shownHeight()) return -1;
        for (int i = 0; i < targets.size(); i++) {
            var t = targets.get(i);
            if (t.card >= 0 && (!isOpen() || y < BAR || motion.card(t.card, cardCount(), options.motion()).opacity() < .98)) continue;
            if (t.area.contains(x, y + (t.card < 0 ? 0 : scroll))) return i;
        }
        return -1;
    }

    boolean press(double x, double y) {
        keyboard = false;
        pressed = hit(x, y);
        if (pressed >= 0) focus = pressed;
        return pressed >= 0;
    }

    boolean release(double x, double y) {
        int saved = pressed;
        pressed = -1;
        if (saved < 0) return false;
        if (saved == hit(x, y)) activate(saved);
        return true;
    }

    boolean dismiss() {
        if (pressed >= 0) { cancel(); return true; }
        if (!isOpen()) return false;
        setOpen(false);
        return true;
    }

    boolean scroll(double amount) {
        if (!isOpen() || maxScroll() == 0) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(amount * 20)));
        return true;
    }

    boolean moveFocus(int direction) {
        keyboard = true;
        int next = focus;
        do { next = Math.floorMod(next + direction, targets.size()); }
        while (!isOpen() && targets.get(next).card >= 0);
        focus = next;
        var t = targets.get(focus);
        if (t.card >= 0) scroll = Math.max(0, Math.min(maxScroll(), Math.max(t.area.y + t.area.h - viewportHeight,
                Math.min(scroll, t.area.y - BAR))));
        return true;
    }

    boolean activate() { keyboard = true; activate(focus); return true; }
    private void activate(int index) {
        if (index == 0) setOpen(!isOpen());
        else {
            var t = targets.get(index);
            if (t.card >= 0 && !isOpen()) return;
            if (t.card >= 0) setOpen(false);
            t.link.action().run();
        }
    }

    Component narration() {
        var target = targets.get(focus);
        return CommonComponents.joinForNarration(name, label(isOpen() ? "expanded" : "collapsed"),
                target.link == null ? label(isOpen() ? "close" : "menu") : target.link.ariaLabel());
    }
    static Component usage() { return label("keys"); }
    private static Component label(String key) { return Component.translatable(KEY + key); }
    private int cardCount() { return items.size() + (cta != null && narrow ? 1 : 0); }

    int panelColor() { return options.theme() == Theme.DARK ? 0xFF26232E : 0xFFF4F4F6; }
    int ink() { return options.theme() == Theme.DARK ? 0xFFF4F4F5 : 0xFF18181B; }
    boolean glass() { return options.surface() == Surface.GLASS && preferences.useBackdropBlur(); }
    int introInset(long now) {
        if (!options.intro() || preferences.reducedMotion()) return 0;
        double progress = Math.min(1, Math.max(0, (now - introStart) / 820.0));
        return (int) ((width - BAR) * .5 * Math.pow(1 - progress, 3));
    }

    /** Shared drawing; native adapters supply pose, clipping and the actual glass background. */
    void draw(Painter p, boolean focused, long now) {
        int edge = preferences.highContrast() ? ink() : alpha(ink(), .08);
        int height = shownHeight();
        int inset = introInset(now);
        p.clip(inset, 0, width - inset, height);
        for (int i = 0; i < targets.size(); i++) {
            var target = targets.get(i);
            if (target.card >= 0) continue;
            var r = target.area;
            double intro = !options.intro() || preferences.reducedMotion() ? 1
                    : Math.max(0, Math.min(1, (now - introStart - 220 - i * 70) / 560.0));
            float barProgress = (float) (1 - Math.pow(1 - intro, 3));
            p.begin(0, (1 - barProgress) * 4, 1, 1, barProgress, 0);
            if (i == 0) {
                if (hovered == 0 || pressed == 0) p.shape(r.x, r.y, r.w, r.h, 5, 0, alpha(ink(), pressed == 0 ? .14 : .06));
                double v = Math.max(0, Math.min(1, motion.progress()));
                // Two rounded strokes converge into the close glyph.
                for (int stroke = 0; stroke < 2; stroke++) {
                    for (int n = 0; n < 18; n++) {
                        float dx = n * .5f;
                        float dy = (float) ((stroke == 0 ? -1 : 1) * ((1 - v) * 1.5 + v * (dx - 4.25)));
                        p.shape(r.x + 7 + dx, r.y + r.h / 2f + dy, 1, .8f, .4f, 0, ink());
                    }
                }
                if (options.menuLabel()) {
                    int textY = r.y + (r.h - font.lineHeight) / 2;
                    p.clip(r.x + 21, textY, r.x + r.w, textY + font.lineHeight);
                    text(p, label("menu").getVisualOrderText(), r.x + 21,
                            textY - (int) (v * font.lineHeight * 1.1), r.w - 23, ink());
                    text(p, label("close").getVisualOrderText(), r.x + 21,
                            textY + (int) Math.ceil((1 - v) * font.lineHeight * 1.1), r.w - 23, ink());
                    p.unclip();
                }
            } else drawTarget(p, target, i, focused);
            if (focused && focus == i) p.shape(r.x, r.y, r.w, r.h, 5, alpha(ink(), .6), 0);
            p.end();
        }
        if (motion.shown()) {
            p.clip(0, BAR, width, height);
            for (int i = 0; i < tiles.size(); i++) {
                var tile = tiles.get(i);
                var r = tile.area;
                var card = motion.card(i, cardCount(), options.motion());
                begin(p, r, card);
                int fill = tile.item.background() == null ? (options.theme() == Theme.DARK ? 0xFF302C39 : 0xFFFCFCFD) : tile.item.background();
                int color = tile.item.textColor() == null ? (tile.item.background() == null ? ink() : contrastInk(fill)) : tile.item.textColor();
                if (preferences.highContrast()) color = contrastInk(fill);
                p.shape(0, 0, r.w, r.h, 5, preferences.highContrast() ? color : edge, fill);
                text(p, tile.item.label().getVisualOrderText(), PAD, PAD, r.w - 2 * PAD, color);
                int ty = PAD + font.lineHeight + 3;
                for (var line : tile.description) {
                    text(p, line, PAD, ty, r.w - 2 * PAD, preferences.highContrast() ? color : alpha(color, .7));
                    ty += font.lineHeight;
                }
                for (int j = 0; j < targets.size(); j++) {
                    var target = targets.get(j);
                    if (target.card == i) drawLink(p, target, j, r, color, focused);
                }
                p.end();
            }
            if (cta != null && narrow) {
                int index = targets.size() - 1;
                var target = targets.get(index);
                begin(p, target.area, motion.card(items.size(), cardCount(), options.motion()));
                drawTarget(p, new Target(new Rect(0, 0, target.area.w, target.area.h), cta, target.card), index, focused);
                p.end();
            }
            p.unclip();
        }
        p.unclip();
    }

    private void begin(Painter p, Rect area, CardNavMotion.Card card) {
        p.begin(area.x + area.w * (1 - (float) card.scale()) / 2,
                area.y - scroll + (float) card.translateY() / 2, (float) card.scale(),
                (float) (card.scale() * Math.cos(Math.toRadians(card.rotateX()))),
                (float) card.opacity(), (float) card.blur() / 2);
    }

    private void drawTarget(Painter p, Target target, int index, boolean focused) {
        var r = target.area;
        boolean isCta = target.link == cta;
        int fill = options.accentColor() == null ? (options.theme() == Theme.DARK ? 0xFFF4F4F8 : 0xFF222227) : options.accentColor() | 0xFF000000;
        int color = isCta ? contrastInk(fill) : ink();
        if (isCta) p.shape(r.x, r.y, r.w, r.h, 5, alpha(color, .12), fill);
        if (hovered == index || pressed == index) p.shape(r.x, r.y, r.w, r.h, 5, 0, alpha(color, pressed == index ? .2 : .08));
        if (!isCta && target.link.icon() != null) {
            p.icon(target.link.icon(), r.x + (r.w - 12) / 2, r.y + (r.h - 12) / 2, color);
        } else {
            int textWidth = Math.min(font.width(target.link.label()), Math.max(0, r.w - (isCta ? 10 : 0)));
            text(p, target.link.label().getVisualOrderText(), r.x + (r.w - textWidth) / 2,
                    r.y + (r.h - font.lineHeight) / 2, textWidth, color);
        }
        if (focused && focus == index) p.shape(r.x, r.y, r.w, r.h, 5, color, 0);
    }

    private void drawLink(Painter p, Target t, int index, Rect tile, int color, boolean focused) {
        int x = t.area.x - tile.x, y = t.area.y - tile.y;
        boolean hot = hovered == index || focused && focus == index;
        if (pressed == index) p.shape(x, y, t.area.w, t.area.h, 3, 0, alpha(color, .12));
        // Arrow up/right, clipped to its own slot while two copies exchange places.
        p.clip(x, y, x + 9, y + LINK_HEIGHT);
        double v = hoverMotion[index];
        for (int copy = 0; copy < 2; copy++) {
            int dx = (int) ((v - copy) * 11), dy = -dx;
            for (int n = 0; n < 6; n++) p.shape(x + 1 + n + dx, y + 10 - n + dy, 1, 1, 0, 0, color);
            p.shape(x + 3 + dx, y + 5 + dy, 4, 1, 0, 0, color);
            p.shape(x + 6 + dx, y + 5 + dy, 1, 4, 0, 0, color);
        }
        p.unclip();
        text(p, t.link.label().getVisualOrderText(), x + 12, y + (LINK_HEIGHT - font.lineHeight) / 2,
                t.area.w - 12, preferences.highContrast() || hot ? color : alpha(color, .82));
        if (focused && focus == index) p.shape(x, y, t.area.w, t.area.h, 3, color, 0);
    }

    private void text(Painter p, FormattedCharSequence line, int x, int y, int width, int color) {
        if (width <= 0) return;
        p.clip(x, y, x + width, y + font.lineHeight);
        p.text(line, x, y, color);
        p.unclip();
    }

    static int alpha(int color, double opacity) { return ((int) ((color >>> 24) * opacity) << 24) | (color & 0xFFFFFF); }
    static int contrastInk(int color) {
        double luminance = .2126 * channel(color >> 16 & 255) + .7152 * channel(color >> 8 & 255) + .0722 * channel(color & 255);
        return luminance > .36 ? 0xFF121016 : 0xFFFFFFFF;
    }
    private static double channel(int c) { double v = c / 255.0; return v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4); }
}
