// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSloshGauge;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.kernel.internal.NativePort;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.components.AbstractWidget;

/** Native Screen with content and foreground extraction callbacks. */
public final class UiScreen extends Screen {
    UiTheme workbenchPalette;
    private Screen parent;
    private final boolean parentSpecified;
    private final boolean pausesGame;
    private final Consumer<UiScreen> init;
    private final Consumer<UiScreen> tick;
    private final Consumer<UiScreen> removed;
    private final Draw content;
    private final Draw foreground;
    private WidgetScroll scrollHandler;
    private Supplier<? extends GuiEventListener> returnFocus = () -> null;
    private BooleanSupplier closeRequested = () -> true;
    private final Map<GuiEventListener, Supplier<Bounds>> clips = new IdentityHashMap<>();
    private final Map<GuiEventListener, String> stacks = new IdentityHashMap<>();
    private GuiEventListener hovered;
    private Consumer<GuiEventListener> focusChanged = ignored -> {};
    public void onFocusChanged(Consumer<GuiEventListener> callback) { focusChanged=Objects.requireNonNull(callback); }

    public void returnFocus(Supplier<? extends GuiEventListener> target) { returnFocus = Objects.requireNonNull(target); }
    public void onCloseRequest(BooleanSupplier handler) { closeRequested = Objects.requireNonNull(handler); }
    /** Rebuild native controls without ending the page session. */
    public void refreshLayout(){rebuildWidgets();}
    public UiPreferences preferences() { return UiPreferences.configured(); }


    UiScreen(Component title, Screen parent, boolean parentSpecified, boolean pausesGame, Consumer<UiScreen> init,
             Consumer<UiScreen> tick, Consumer<UiScreen> removed, Draw content, Draw foreground) {
        super(title);
        this.parent = parent;
        this.parentSpecified = parentSpecified;
        this.pausesGame = pausesGame;
        this.init = init;
        this.tick = tick;
        this.removed = removed;
        this.content = content;
        this.foreground = foreground;
    }

    public static Screen currentScreen() { return NativePort.screen(Minecraft.getInstance()); }

    /** Unmounts a regular widget registered with add(). */
    public void remove(AbstractWidget widget) {
        if (widget instanceof com.dishanhai.sh_better_ui.client.gui.scaled.UiIngredientSlots slots) slots.cancelInteraction();
        if (getFocused() == widget) {
            setFocused(null);
            setDragging(false);
        }
        removeWidget(widget);
    }

    public void open() {
        var client = Minecraft.getInstance();
        if (!parentSpecified && NativePort.screen(client) != this) parent = NativePort.screen(client);
        NativePort.show(client, this);
    }

    @Override public void onClose() {
        var client = Minecraft.getInstance();
        closeIfCurrent(this, parent, () -> NativePort.screen(client), target -> NativePort.show(client, target),
                closeRequested, returnFocus);
    }

    static void closeIfCurrent(Screen owner, Screen parent, Supplier<Screen> current, Consumer<Screen> show,
                               BooleanSupplier guard, Supplier<? extends GuiEventListener> focus) {
        if (current.get() != owner || !guard.getAsBoolean() || current.get() != owner) return;
        show.accept(parent);
        if (parent != null && current.get() == parent) {
            var target = focus.get();
            if (current.get() == parent && target != null && parent.children().contains(target)) parent.setFocused(target);
        }
    }
    @Override public boolean isPauseScreen() { return pausesGame; }
    @Override protected void init() { clips.clear(); stacks.clear(); hovered = null; scrollHandler = null; init.accept(this); }
    @Override protected void repositionElements() {
        if (getFocused() instanceof UiSloshGauge gauge) gauge.cancelDrag();
        setFocused(null);
        setDragging(false);
        rebuildWidgets();
    }
    @Override protected void setInitialFocus(){if(getFocused()==null)super.setInitialFocus();}
    @Override public void tick() { tick.accept(this); }
    @Override public void removed() {
        try {
            if (getFocused() instanceof UiSloshGauge gauge) gauge.cancelDrag();
            setFocused(null);
            setDragging(false);
        } finally {
            removed.accept(this);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        clearClippedFocus();
        var focused = getFocused();
        if (event.isEscape() && focused instanceof com.dishanhai.sh_better_ui.client.gui.scaled.UiCancelableInteraction interaction
                && interaction.cancelInteraction()) { setDragging(false); return true; }
        if (event.isEscape() && (focused instanceof com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSeekControl
                || focused instanceof com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl
                || focused instanceof com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSegmentedControl
                || focused instanceof com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl
                || focused instanceof com.dishanhai.sh_better_ui.kernel.api.music.MusicLyricsView)
                && focused.keyPressed(event)) { setDragging(false); return true; }
        if (event.isEscape() && getFocused() instanceof com.dishanhai.sh_better_ui.client.gui.scaled.UiIngredientSlots slots && slots.cancelDrag()) {
            setDragging(false);
            return true;
        }
        if (event.isEscape() && getFocused() instanceof UiSloshGauge gauge && gauge.cancelDrag()) {
            setDragging(false);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (getFocused() instanceof com.dishanhai.sh_better_ui.client.gui.scaled.UiCancelableInteraction interaction
                && interaction.releasePointer(event.x(), event.y(), event.button())) {
            setDragging(false);
            return true;
        }
        if (event.button() == 0 && getFocused() instanceof com.dishanhai.sh_better_ui.client.gui.scaled.UiIngredientSlots slots
                && slots.consumeCancelledRelease()) return true;
        if (getFocused() instanceof UiSloshGauge gauge && gauge.mouseReleased(event)) {
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }

    public Bounds viewport() { return new Bounds(0, 0, width, height); }
    public void onScroll(WidgetScroll handler) { scrollHandler = java.util.Objects.requireNonNull(handler); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        var target = getChildAt(x, y);
        return target.isPresent() && target.get().mouseScrolled(x, y, horizontal, vertical)
                || scrollHandler != null && scrollHandler.scroll(x, y, horizontal, vertical);
    }
    public <T extends GuiEventListener & Renderable & NarratableEntry> T add(T widget) {
        return addRenderableWidget(widget);
    }

    public <T extends AbstractWidget> T addClipped(T widget, Supplier<Bounds> clip, String stackId) {
        Objects.requireNonNull(widget); Objects.requireNonNull(clip);
        addWidget(widget);
        clips.put(widget, clip);
        if (stackId != null) stacks.put(widget, stackId);
        addRenderableOnly((graphics, mouseX, mouseY, partialTick) -> {
            var bounds = clip.get();
            if (bounds.width() <= 0 || bounds.height() <= 0) return;
            graphics.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
            try {
                boolean inside = bounds.contains(mouseX, mouseY) && (stackId == null || hovered == widget);
                widget.extractRenderState(graphics, inside ? mouseX : -1, inside ? mouseY : -1, partialTick);
            } finally { graphics.disableScissor(); }
        });
        return widget;
    }

    /** Native initial focus already honors the logical target restored during layout. */
    public void restoreLayoutFocus(GuiEventListener target) { setFocused(target); }

    @Override public void setFocused(GuiEventListener listener) {
        if (getFocused() != listener) { super.setFocused(listener); focusChanged.accept(listener); }
    }

    @Override public Optional<GuiEventListener> getChildAt(double x, double y) {
        return Optional.ofNullable(findWidgetTarget(x, y, child -> true));
    }

    private GuiEventListener findWidgetTarget(double x, double y, java.util.function.Predicate<GuiEventListener> accept) {
        var children = children();
        var testedStacks = new HashSet<String>();
        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            if (!hit(child, x, y)) continue;
            var stack = stacks.get(child);
            if (stack != null) {
                if (!testedStacks.add(stack)) continue;
                for (int j = children.size() - 1; j > i; j--) {
                    var candidate = children.get(j);
                    if (stack.equals(stacks.get(candidate)) && hit(candidate, x, y)) { child = candidate; break; }
                }
            }
            if (accept.test(child)) return child;
        }
        return null;
    }

    private void clearClippedFocus() {
        if (!(getFocused() instanceof AbstractWidget widget)) return;
        var supplier = clips.get(widget);
        if (supplier == null) return;
        var bounds = supplier.get();
        if (!widget.visible || bounds.width() <= 0 || bounds.height() <= 0
                || widget.getX() >= bounds.right() || widget.getX() + widget.getWidth() <= bounds.x()
                || widget.getY() >= bounds.bottom() || widget.getY() + widget.getHeight() <= bounds.y()) {
            setFocused(null);
            setDragging(false);
        }
    }

    private boolean hit(GuiEventListener child, double x, double y) {
        var clip = clips.get(child);
        return (clip == null || clip.get().contains(x, y)) && child.isMouseOver(x, y);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return findWidgetTarget(event.x(), event.y(), child -> {
            if (!child.mouseClicked(event, doubleClick)) return false;
            if (NativePort.screen(Minecraft.getInstance()) == this && children().contains(child)) {
                setFocused(child);
                if (event.button() == 0) setDragging(true);
            }
            return true;
        }) != null;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (workbenchPalette == null) drawContents(graphics, mouseX, mouseY, partialTick);
        else UiTheme.preview(workbenchPalette, () -> drawContents(graphics, mouseX, mouseY, partialTick));
    }

    private void drawContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        clearClippedFocus();
        hovered = getChildAt(mouseX, mouseY).orElse(null);
        var frame = new UiFrame(graphics, viewport(), mouseX, mouseY, UiPreferences.configured(),
                System.nanoTime() / 1_000_000, partialTick);
        content.draw(this, frame);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        foreground.draw(this, frame);
    }

    @FunctionalInterface public interface Draw { void draw(UiScreen screen, UiFrame frame); }
    @FunctionalInterface public interface WidgetScroll { boolean scroll(double x, double y, double horizontal, double vertical); }
}
