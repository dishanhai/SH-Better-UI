// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.dock.indicator.CompactIndicator;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Supplier;

/** Immutable window definition. All dimensions are in GUI px. Factories run on the client thread. */
public final class MiniWindowSpec {
    private final Builder value;

    private MiniWindowSpec(Builder source) {
        value = new Builder(source);
        dimension(value.width, value.height);
        dimension(value.compactWidth, value.compactHeight);
        dimension(value.minWidth, value.minHeight);
        dimension(value.maxWidth, value.maxHeight);
        if (value.animation.controlled()) {
            throw new IllegalArgumentException("Mini-windows own their expansion progress");
        }
        if (value.minWidth > value.maxWidth || value.minHeight > value.maxHeight
                || value.compactWidth > value.minWidth || value.compactHeight > value.minHeight
                || value.width < value.minWidth || value.width > value.maxWidth
                || value.height < value.minHeight || value.height > value.maxHeight) {
            throw new IllegalArgumentException("Inconsistent mini-window dimensions");
        }
    }

    private static void dimension(int width, int height) {
        if (width < 1 || height < 1 || width > 8192 || height > 8192) {
            throw new IllegalArgumentException("Mini-window dimensions must be in [1,8192]");
        }
    }

    public static Builder builder(String id) { return builder(UiId.parse(id)); }
    public static Builder builder(UiId id) { return new Builder(id); }
    public UiId id() { return value.id; }
    public Component title() { return value.title.copy(); }
    public DockEdge anchor() { return value.anchor; }
    public int width() { return value.width; }
    public int height() { return value.height; }
    public int compactWidth() { return value.compactWidth; }
    public int compactHeight() { return value.compactHeight; }
    public int minWidth() { return value.minWidth; }
    public int minHeight() { return value.minHeight; }
    public int maxWidth() { return value.maxWidth; }
    public int maxHeight() { return value.maxHeight; }
    public boolean autoHide() { return value.autoHide; }
    public boolean expandOnHover() { return value.expandOnHover; }
    public boolean outsideClickCloses() { return value.outsideClickCloses; }
    public boolean persistent() { return value.persistent; }
    public boolean draggable() { return value.draggable; }
    public long openDelayMs() { return value.openDelayMs; }
    public long closeDelayMs() { return value.closeDelayMs; }
    public int priority() { return value.priority; }
    public int shortcut() { return value.shortcut; }
    public int shortcutModifiers() { return value.shortcutModifiers; }
    public UiId group() { return value.group; }
    public ExpansionLayoutMode expansionLayout() { return value.expansionLayout; }
    public CollapseSpec animation() { return value.animation; }
    public GlassMaterial glass() { return value.glass; }
    public Supplier<? extends CompactIndicator> compactIndicator() { return value.indicator; }
    public Supplier<? extends WindowContent> content() { return value.content; }

    /** Default left-side auto-hide, overlay, 260×180 panel and 28×80 indicator. */
    public static final class Builder {
        private final UiId id;
        private Component title = Component.empty();
        private DockEdge anchor = DockEdge.LEFT;
        private int width = 260, height = 180, compactWidth = 28, compactHeight = 80;
        private int minWidth = 90, minHeight = 80, maxWidth = 1024, maxHeight = 1024;
        private boolean autoHide = true, expandOnHover = true, outsideClickCloses, persistent, draggable = true;
        private long openDelayMs = 120, closeDelayMs = 350;
        private int priority, shortcut = -1, shortcutModifiers;
        private UiId group;
        private ExpansionLayoutMode expansionLayout = ExpansionLayoutMode.OVERLAY;
        private CollapseSpec animation = new CollapseSpec();
        private GlassMaterial glass;
        private Supplier<? extends CompactIndicator> indicator;
        private Supplier<? extends WindowContent> content = () -> (draw, view) -> {};

        private Builder(UiId id) { this.id = Objects.requireNonNull(id); }
        private Builder(Builder other) {
            id = other.id; title = other.title.copy(); anchor = other.anchor;
            width = other.width; height = other.height; compactWidth = other.compactWidth;
            compactHeight = other.compactHeight; minWidth = other.minWidth; minHeight = other.minHeight;
            maxWidth = other.maxWidth; maxHeight = other.maxHeight; autoHide = other.autoHide;
            expandOnHover = other.expandOnHover; outsideClickCloses = other.outsideClickCloses;
            persistent = other.persistent; draggable = other.draggable; openDelayMs = other.openDelayMs;
            closeDelayMs = other.closeDelayMs; priority = other.priority; shortcut = other.shortcut;
            shortcutModifiers = other.shortcutModifiers;
            group = other.group; expansionLayout = other.expansionLayout; animation = other.animation;
            glass = other.glass; indicator = other.indicator; content = other.content;
        }

        public Builder title(Component title) { this.title = Objects.requireNonNull(title).copy(); return this; }
        public Builder anchor(DockEdge anchor) { this.anchor = Objects.requireNonNull(anchor); return this; }
        public Builder size(int width, int height) { this.width = width; this.height = height; return this; }
        public Builder compactSize(int width, int height) { compactWidth = width; compactHeight = height; return this; }
        public Builder limits(int minWidth, int minHeight, int maxWidth, int maxHeight) {
            this.minWidth = minWidth; this.minHeight = minHeight; this.maxWidth = maxWidth; this.maxHeight = maxHeight;
            return this;
        }
        public Builder autoHide(boolean enabled) { autoHide = enabled; return this; }
        public Builder expandOnHover(boolean enabled) { expandOnHover = enabled; return this; }
        public Builder outsideClickCloses(boolean enabled) { outsideClickCloses = enabled; return this; }
        public Builder persistent(boolean enabled) { persistent = enabled; return this; }
        public Builder draggable(boolean enabled) { draggable = enabled; return this; }
        public Builder openDelayMs(long millis) { openDelayMs = delay(millis); return this; }
        public Builder closeDelayMs(long millis) { closeDelayMs = delay(millis); return this; }
        public Builder priority(int priority) {
            if (priority < -100 || priority > 100) throw new IllegalArgumentException("Invalid dock priority");
            this.priority = priority; return this;
        }
        /** Optional Screen key code. Unmodified typing keys respect the active native focus. */
        public Builder shortcut(int key) {
            return shortcut(key, 0);
        }
        /** Optional shortcut with GLFW modifier bits for Shift, Control, Alt and Super. */
        public Builder shortcut(int key, int modifiers) {
            if (key < -1 || key > 348 || key == 256 || key == 258 || (modifiers & ~15) != 0) {
                throw new IllegalArgumentException("Invalid window shortcut");
            }
            shortcut = key;
            shortcutModifiers = modifiers;
            return this;
        }
        public Builder group(UiId group) { this.group = group; return this; }
        public Builder expansionLayout(ExpansionLayoutMode mode) {
            expansionLayout = Objects.requireNonNull(mode); return this;
        }
        public Builder animation(CollapseSpec spec) { animation = Objects.requireNonNull(spec); return this; }
        /** Enables the shared Liquid Glass backend for this window. */
        public Builder glass(GlassMaterial material) { glass = Objects.requireNonNull(material); return this; }
        public Builder compactIndicator(Supplier<? extends CompactIndicator> factory) {
            indicator = Objects.requireNonNull(factory); return this;
        }
        public Builder content(Supplier<? extends WindowContent> factory) {
            content = Objects.requireNonNull(factory); return this;
        }
        public MiniWindowSpec build() { return new MiniWindowSpec(this); }

        private static long delay(long value) {
            if (value < 0 || value > 60_000) throw new IllegalArgumentException("Invalid hover delay");
            return value;
        }
    }
}
