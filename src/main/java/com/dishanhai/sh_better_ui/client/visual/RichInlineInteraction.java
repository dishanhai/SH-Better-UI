// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.gui.selection.IngredientValues;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Fragment;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Button;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Item;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

/** Per-view native input state. The owning Screen or UUK input target delivers events once, in host z order. */
public final class RichInlineInteraction {
    private static final double DRAG_DISTANCE_SQUARED = 16;
    private static final long DOUBLE_CLICK_MS = 350;
    private static final long LONG_PRESS_MS = 500;

    interface Geometry {
        Optional<Fragment> hit(double x, double y);
        List<Fragment> objects();
        double localX(double x, double y);
        default Optional<Fragment> object(UiId id) {
            return objects().stream().filter(fragment -> fragment.node().id().equals(id)).findFirst();
        }
        default void clear() {}
    }

    public enum Gesture { SECONDARY, DOUBLE_CLICK, LONG_PRESS }
    public record Drop(UiId source, UiId target) {}

    public record PaintState(boolean hovered, boolean focused, boolean pressed, boolean enabled,
                             boolean dropTarget, int sliderValue) {}

    private record Drag(Fragment fragment, int button, double x, double y, long startMillis) {
        RichObjectNode object() { return (RichObjectNode) fragment.node(); }
    }
    private record Click(UiId id, long time, double x, double y) {}
    private record Value(Slider slider, int value) {}
    private record NativeCapture(Screen screen, int button) {}

    private final Geometry geometry;
    private final LongSupplier time;
    private final Map<UiId, Runnable> buttons = new HashMap<>();
    private final Map<UiId, IntConsumer> sliders = new HashMap<>();
    private final Map<UiId, Value> values = new HashMap<>();
    private final Map<UiId, Consumer<Gesture>> gestures = new HashMap<>();
    private final Map<UiId, Consumer<Drop>> dropHandlers = new HashMap<>();
    private final Set<UiId> dragSources = new HashSet<>();
    private final Map<UiId, Ingredient> itemValues = new HashMap<>();
    private final RichInlineObjects objects = new RichInlineObjects();
    private UiId focus;
    private UiId hover;
    private UiId dropTarget;
    private Drag drag;
    private NativeCapture nativeCapture;
    private Click click;
    private boolean moved;
    private int drainingButtons;
    private final Set<Integer> activationKeys = new HashSet<>();
    private int preview;
    private boolean closed;

    public RichInlineInteraction(RichTextView view) {
        this(geometry(Objects.requireNonNull(view)), () -> System.nanoTime() / 1_000_000L);
    }

    RichInlineInteraction(Geometry geometry, LongSupplier time) {
        this.geometry = geometry;
        this.time = time;
    }

    private static Geometry geometry(RichTextView view) {
        return new Geometry() {
            private FrameViewSnapshot frame;
            private final Map<UiId, Fragment> index = new LinkedHashMap<>();

            @Override public Optional<Fragment> hit(double x, double y) { return view.hitTest(x, y); }
            @Override public List<Fragment> objects() {
                updateIndex();
                return index.values().stream().filter(view::isVisible).toList();
            }
            @Override public Optional<Fragment> object(UiId id) {
                updateIndex();
                return Optional.ofNullable(index.get(id)).filter(view::isVisible);
            }
            @Override public double localX(double x, double y) { return view.localX(x, y); }
            @Override public void clear() {
                frame = null;
                index.clear();
            }

            private void updateIndex() {
                var current = view.currentSnapshot().map(RichTextView.Presentation::frame).orElse(null);
                if (current == frame) return;
                clear();
                frame = current;
                if (frame == null) return;
                for (var line : view.visibleLines()) {
                    for (var fragment : line.fragments()) {
                        if (fragment.node() instanceof RichObjectNode object) index.put(object.id(), fragment);
                    }
                }
            }
        };
    }

    public void onButton(UiId id, Runnable action) {
        requireOpen();
        buttons.put(Objects.requireNonNull(id), Objects.requireNonNull(action));
    }

    public void onSlider(UiId id, IntConsumer action) {
        requireOpen();
        sliders.put(Objects.requireNonNull(id), Objects.requireNonNull(action));
    }

    /** Binds optional right, double, and long press actions. Long presses dispatch once on release. */
    public void onGesture(UiId id, Consumer<Gesture> action) {
        requireOpen();
        gestures.put(Objects.requireNonNull(id), Objects.requireNonNull(action));
    }

    public void draggable(UiId id) {
        requireOpen();
        dragSources.add(Objects.requireNonNull(id));
    }

    public void onDrop(UiId id, Consumer<Drop> action) {
        requireOpen();
        dropHandlers.put(Objects.requireNonNull(id), Objects.requireNonNull(action));
    }

    /** Copies the native stack once. The node's registry ID must match the bound stack. */
    public void bindItem(UiId id, ItemStack stack) {
        requireOpen();
        Objects.requireNonNull(id);
        Objects.requireNonNull(stack);
        if (stack.isEmpty()) itemValues.remove(id);
        else itemValues.put(id, IngredientValues.item(stack));
    }

    public void updateHover(double x, double y) {
        hover = geometry.hit(x, y).map(fragment -> fragment.node().id()).orElse(null);
    }

    public boolean press(double x, double y, int button) {
        if (closed || button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
        drainingButtons &= ~(1 << button);
        if (drag != null) return drag.button() == button;
        var fragment = geometry.hit(x, y).orElse(null);
        if (fragment == null || !(fragment.node() instanceof RichObjectNode object)) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (!gestures.containsKey(object.id())) return false;
        } else if (!enabled(object)) {
            return false;
        }
        hover = object.id();
        focus = object.id();
        drag = new Drag(fragment, button, x, y, time.getAsLong());
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && object.content() instanceof Slider slider) {
            preview = value(object.id(), slider);
            updatePreview(fragment, slider, x, y);
        }
        return true;
    }

    /** Attach an accepted press to the host's existing native capture, when the host is a Screen. */
    public boolean capture(Screen screen, int button) {
        Objects.requireNonNull(screen);
        if (drag == null || drag.button() != button || nativeCapture != null) return false;
        var capture = new NativeCapture(screen, button);
        var weak = new WeakReference<>(this);
        boolean accepted = UiPointerCapture.start(screen, button, new UiPointerCapture.Handler() {
            @Override public void drag(double x, double y) {
                var active = weak.get();
                if (active != null) active.drag(x, y, button);
            }

            @Override public void release(double x, double y) {
                var active = weak.get();
                try {
                    if (active != null && active.nativeCapture == capture) {
                        active.nativeCapture = null;
                        active.releaseLocal(x, y, button);
                    }
                } finally {
                    screen.setDragging(false);
                }
            }

            @Override public void cancel() {
                var active = weak.get();
                if (active != null && active.nativeCapture == capture) {
                    active.nativeCapture = null;
                    active.cancel();
                }
                screen.setDragging(false);
            }
        });
        if (accepted) nativeCapture = capture;
        return accepted;
    }

    public boolean drag(double x, double y, int button) {
        if (draining(button)) return true;
        if (drag == null || drag.button() != button) return false;
        updateHover(x, y);
        moved |= distanceSquared(x, y, drag.x(), drag.y()) >= DRAG_DISTANCE_SQUARED;
        var fragment = currentDragFragment(drag);
        if (fragment == null) {
            cancel();
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (drag.object().content() instanceof Slider slider) {
                updatePreview(fragment, slider, x, y);
            } else if (dragSources.contains(drag.object().id())) {
                dropTarget = moved ? dropTarget(x, y, drag.object().id()) : null;
            }
        }
        return true;
    }

    public boolean release(double x, double y, int button) {
        var capture = nativeCapture;
        if (capture != null && capture.button() == button && UiPointerCapture.release(capture.screen(), button, x, y)) {
            drainingButtons &= ~(1 << button);
            return true;
        }
        return releaseLocal(x, y, button);
    }

    private boolean releaseLocal(double x, double y, int button) {
        if (draining(button)) {
            drainingButtons &= ~(1 << button);
            return true;
        }
        var capture = drag;
        if (capture == null || capture.button() != button) return false;
        var fragment = currentDragFragment(capture);
        var object = capture.object();
        boolean withinSlop = !moved && distanceSquared(x, y, capture.x(), capture.y()) < DRAG_DISTANCE_SQUARED;
        boolean dropped = button == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragSources.contains(object.id())
                && !withinSlop;
        resetDrag();
        if (fragment == null) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && object.content() instanceof Slider slider) {
            updatePreview(fragment, slider, x, y);
            commit(object.id(), slider, preview);
        } else if (dropped) {
            UiId target = dropTarget(x, y, object.id());
            if (target != null) dropHandlers.get(target).accept(new Drop(object.id(), target));
        } else if (geometry.hit(x, y).filter(hit -> hit.node().equals(object)).isPresent()) {
            activatePointer(capture, x, y, withinSlop);
        }
        return true;
    }

    private void activatePointer(Drag capture, double x, double y, boolean withinSlop) {
        var id = capture.object().id();
        var action = gestures.get(id);
        long now = time.getAsLong();
        if (capture.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            click = null;
            if (action != null) action.accept(Gesture.SECONDARY);
        } else if (action != null && withinSlop && now - capture.startMillis() >= LONG_PRESS_MS) {
            click = null;
            action.accept(Gesture.LONG_PRESS);
        } else if (action != null && withinSlop && click != null && id.equals(click.id()) && now - click.time() <= DOUBLE_CLICK_MS
                && distanceSquared(x, y, click.x(), click.y()) < DRAG_DISTANCE_SQUARED) {
            click = null;
            action.accept(Gesture.DOUBLE_CLICK);
        } else {
            click = action == null || !withinSlop ? null : new Click(id, now, x, y);
            Runnable button = buttons.get(id);
            if (button != null && capture.object().content() instanceof Button) button.run();
        }
    }

    private Fragment currentDragFragment(Drag capture) {
        return geometry.object(capture.object().id()).filter(fragment -> fragment.node().equals(capture.object()))
                .orElse(null);
    }

    private UiId dropTarget(double x, double y, UiId source) {
        return geometry.hit(x, y).map(Fragment::node).filter(RichObjectNode.class::isInstance)
                .map(node -> node.id()).filter(id -> !id.equals(source) && dropHandlers.containsKey(id)).orElse(null);
    }

    private static double distanceSquared(double x, double y, double x0, double y0) {
        double dx = x - x0;
        double dy = y - y0;
        return dx * dx + dy * dy;
    }

    private void updatePreview(Fragment fragment, Slider slider, double x, double y) {
        double local = geometry.localX(x, y);
        if (Double.isFinite(local)) {
            var box = fragment.bounds();
            int inset = sliderInset(box.width());
            preview = slider.valueAt((local - box.x() - inset) / (box.width() - 2d * inset));
        }
    }

    static int sliderInset(int width) { return Math.min(4, (width - 1) / 2); }

    private void commit(UiId id, Slider slider, int value) {
        if (value == value(id, slider)) return;
        values.put(id, new Value(slider, value));
        var action = sliders.get(id);
        if (action != null) action.accept(value);
    }

    private int value(UiId id, Slider slider) {
        var value = values.get(id);
        return value != null && value.slider().equals(slider) ? value.value() : slider.initial();
    }

    /** Roll back the preview and consume the outstanding native release. */
    public boolean cancel() {
        click = null;
        if (drag == null) return false;
        drainingButtons |= 1 << drag.button();
        resetDrag();
        return true;
    }

    private void resetDrag() {
        drag = null;
        moved = false;
        dropTarget = null;
    }

    private boolean draining(int button) {
        return button >= 0 && button <= GLFW.GLFW_MOUSE_BUTTON_RIGHT && (drainingButtons & (1 << button)) != 0;
    }

    public void clearFocus() {
        cancel();
        focus = null;
        hover = null;
        activationKeys.clear();
    }

    public boolean hasFocus() { return focusedObject() != null; }

    public boolean focusFirst(boolean reverse) {
        if (closed) return false;
        var controls = focusOrder();
        if (controls.isEmpty()) return false;
        focus = controls.get(reverse ? controls.size() - 1 : 0).id();
        return true;
    }

    public boolean keyPressed(int key, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return cancel();
        var object = focusedObject();
        if (object == null) return false;
        if (key == GLFW.GLFW_KEY_TAB) {
            cancel();
            return moveFocus((modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1);
        }
        if (drag != null) return false;
        if (object.content() instanceof Slider slider) {
            Integer next = switch (key) {
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN -> slider.stepFrom(value(object.id(), slider), false);
                case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP -> slider.stepFrom(value(object.id(), slider), true);
                case GLFW.GLFW_KEY_HOME -> slider.min();
                case GLFW.GLFW_KEY_END -> slider.max();
                default -> null;
            };
            if (next != null) {
                commit(object.id(), slider, next);
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_F10 && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            var action = gestures.get(object.id());
            if (action != null) return activateKey(key, () -> action.accept(Gesture.SECONDARY));
        }
        var action = buttons.get(object.id());
        return action != null && object.content() instanceof Button && (key == GLFW.GLFW_KEY_ENTER
                || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) && activateKey(key, action);
    }

    private boolean activateKey(int key, Runnable action) {
        if (activationKeys.add(key)) action.run();
        return true;
    }

    public boolean keyReleased(int key) {
        return activationKeys.remove(key);
    }

    private boolean moveFocus(int direction) {
        var controls = focusOrder();
        for (int i = 0; i < controls.size(); i++) {
            if (!controls.get(i).id().equals(focus)) continue;
            int next = i + direction;
            if (next >= 0 && next < controls.size()) {
                focus = controls.get(next).id();
                return true;
            }
            break;
        }
        focus = null;
        return false;
    }

    private List<RichObjectNode> focusOrder() {
        return geometry.objects().stream().map(fragment -> (RichObjectNode) fragment.node())
                .filter(this::enabled).toList();
    }

    private RichObjectNode focusedObject() {
        if (focus == null) return null;
        return geometry.object(focus).map(fragment -> (RichObjectNode) fragment.node()).filter(this::enabled).orElse(null);
    }

    private boolean enabled(RichObjectNode object) {
        return object.content() instanceof Slider || object.content() instanceof Button && buttons.containsKey(object.id())
                || gestures.containsKey(object.id()) || dragSources.contains(object.id());
    }

    public PaintState state(RichObjectNode object) { return state(object, object.id().equals(hover)); }

    public PaintState state(RichObjectNode object, boolean hovered) {
        UiId id = object.id();
        boolean captured = drag != null && object.equals(drag.object());
        int value = object.content() instanceof Slider slider
                ? (captured && drag.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT ? preview : value(id, slider)) : 0;
        return new PaintState(hovered, id.equals(focus), captured && (hovered || object.content() instanceof Slider),
                enabled(object), id.equals(dropTarget), value);
    }

    public Optional<Component> tooltip(double x, double y) {
        return geometry.hit(x, y).flatMap(fragment -> {
            if (fragment.node() instanceof RichObjectNode object) {
                return Optional.of(semanticLabel(object)).filter(label -> !label.getString().isEmpty());
            }
            if (fragment.node() instanceof ImageNode image && !image.altText().isEmpty()) {
                return Optional.of(Component.literal(image.altText()));
            }
            if (fragment.node() instanceof RichExtensionNode extension && !extension.altText().isEmpty()) {
                return Optional.of(Component.literal(extension.altText()));
            }
            return Optional.empty();
        });
    }

    public boolean renderTooltip(GuiGraphics graphics, int x, int y) {
        var hit = geometry.hit(x, y).orElse(null);
        if (hit != null && hit.node() instanceof RichObjectNode object) {
            var ingredient = ingredient(object);
            if (ingredient.isPresent()) {
                ingredient.orElseThrow().renderTooltip(graphics, x, y);
                return true;
            }
        }
        var label = tooltip(x, y);
        label.ifPresent(value -> graphics.renderTooltip(Minecraft.getInstance().font, value, x, y));
        return label.isPresent();
    }

    public boolean narrate(NarrationElementOutput output) {
        var object = focusedObject();
        if (object == null) return false;
        var label = semanticLabel(object);
        if (object.content() instanceof Slider) {
            output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.slider", label));
            output.add(NarratedElementType.USAGE, Component.translatable("narration.slider.usage.focused"));
        } else if (object.content() instanceof Button) {
            output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.button", label));
            output.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage.focused"));
        } else {
            output.add(NarratedElementType.TITLE, label);
        }
        return true;
    }

    private Component semanticLabel(RichObjectNode object) {
        if (object.content() instanceof Slider slider) {
            return Component.translatable("options.generic_value", slider.label(), state(object).sliderValue());
        }
        if (object.content() instanceof Button button) return Component.literal(button.label());
        return Component.literal(object.altText());
    }

    Optional<Ingredient> ingredient(RichObjectNode object) {
        var bound = itemValues.get(object.id());
        if (bound != null && object.content() instanceof Item item
                && item.item().namespace().equals(bound.id().getNamespace())
                && item.item().path().equals(bound.id().getPath())) {
            return Optional.of(bound);
        }
        return objects.ingredient(object.content());
    }

    RichInlineObjects objects() { return objects; }

    void clearResources() { objects.clear(); }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Inline interaction scope is closed");
    }

    void clear() {
        closed = true;
        clearFocus();
        if (nativeCapture != null) {
            UiPointerCapture.cancel(nativeCapture.screen());
            nativeCapture = null;
        }
        drainingButtons = 0;
        geometry.clear();
        buttons.clear();
        sliders.clear();
        values.clear();
        gestures.clear();
        dropHandlers.clear();
        dragSources.clear();
        itemValues.clear();
        clearResources();
    }
}
