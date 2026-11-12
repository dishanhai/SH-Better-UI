// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Action;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Snapshot;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceSettings;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfaceGeometry;
import com.dishanhai.sh_better_ui.kernel.internal.live.LiveSurfacePainter;
import com.dishanhai.sh_better_ui.kernel.internal.live.LiveSurfacePainter.Control;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Native Screen input target. The kernel owns cancellation, the pointer lease and the screen generation. */
final class ClientLiveInput {
    static final UiId POINTER = UiId.parse("sh_better_ui:live/pointer");
    private static final UiId TREE = UiId.parse("sh_better_ui:live/input");
    private static final long FOCUS_PROTECTION_MS = 3_000;
    private final LiveSurfaceRuntime live;
    private final KernelHost host;
    private final ClientScreens screens;
    private final ClientInput input;
    private final Set<Integer> consumedKeys = new HashSet<>();
    private final Spring swipe = new Spring(Spring.INDICATOR);
    private ModuleContext module;
    private ScopeHandle tree;
    private Screen screen;
    private Hit primary, secondary;
    private Choice focused, hovered;
    private GuiEventListener previousNativeFocus;
    private Press active;
    private long lastInteraction, lastBodyClick = Long.MIN_VALUE, lastClickedInstance;
    private long swipeInstance;
    private LiveSurfaceSettings.Input settings = LiveSurfaceSettings.DEFAULT.input();

    ClientLiveInput(LiveSurfaceRuntime live, KernelHost host, ClientScreens screens, ClientInput input) {
        this.live = live;
        this.host = host;
        this.screens = screens;
        this.input = input;
    }

    void initialize(ModuleContext module) { this.module = module; }

    void bind(Screen current, boolean available, LiveSurfaceSettings.Input settings) {
        this.settings = settings;
        if (screen != current || tree != null && !tree.isOpen() || !available) {
            if (tree != null && tree.isOpen()) tree.close();
            tree = null;
            screen = null;
            clear();
        }
        if (focused != null && !(screen instanceof LiveActivityScreen) && screen.getFocused() != previousNativeFocus) {
            clearFocus();
        }
        if (!available || current == null || tree != null || screens.activeScope(current) == null) return;
        screen = current;
        tree = screens.mountTrusted(module, current, TREE);
        tree.track((AutoCloseable) this::clear);
        input.target(module, tree, 600, this::targets);
        var events = module.engine().events();
        events.subscribe(InputService.EVENT, tree, UIEventPhase.TARGET, 0, event -> {
            long start = System.nanoTime();
            try {
                var value = event.payload();
                boolean consumed = value instanceof NativeInput.Pointer pointer ? press(pointer)
                        : value instanceof NativeInput.Key key && key(key);
                if (consumed) event.consume();
            } finally {
                live.inputMeasured(System.nanoTime() - start);
            }
        });
        events.subscribe(InputService.POINTER_MOVED, tree, UIEventPhase.TARGET, 0, event -> {
            var pointer = (NativeInput.Pointer) event.payload();
            hovered = pick(pointer.x(), pointer.y());
        });
    }

    void observe(NativeInput event) {
        if (focused != null && !(screen instanceof LiveActivityScreen)
                && event instanceof NativeInput.Pointer pointer && pointer.action() == NativeInput.PointerAction.PRESS
                && pick(pointer.x(), pointer.y()) == null) {
            clearFocus();
        }
    }

    void painted(Hit first, Hit second, Snapshot intendedFirst, Snapshot intendedSecond) {
        primary = first;
        secondary = second;
        if (focused != null) {
            var current = locate(focused);
            var hit = hitFor(focused.snapshot);
            if (current != null) focused = current;
            else if (hit != null) focus(body(hit));
            else if (!sameInstance(focused.snapshot, intendedFirst) && !sameInstance(focused.snapshot, intendedSecond)) {
                clearFocus();
            }
        }
        if (hovered != null && locate(hovered) == null) hovered = null;
        if (active != null && !valid(active.choice)) {
            var pressed = active.choice;
            boolean changingShape = pressed.control == null && live.isInputCurrent(pressed.snapshot)
                    && (sameInstance(pressed.snapshot, intendedFirst) || sameInstance(pressed.snapshot, intendedSecond));
            if (!changingShape) UiPointerCapture.cancel(screen);
        }
        if (focused == null && primary != null && screen instanceof LiveActivityScreen dashboard
                && dashboard.takeInitialFocus()) {
            focus(body(primary));
            if (dashboard.expandsOnOpen(primary.snapshot.instance()) && live.isInputCurrent(primary.snapshot)) {
                live.expand(primary.snapshot);
            }
        }
    }

    boolean available() { return tree != null && tree.isOpen(); }

    void resetPresentation() {
        if (active != null && screen != null) UiPointerCapture.cancel(screen);
        primary = secondary = null;
        clearFocus();
        hovered = null;
        swipe.snap(0);
        swipeInstance = 0;
    }

    boolean protectedInteraction() {
        return active != null || focused != null && now() - lastInteraction < FOCUS_PROTECTION_MS;
    }

    void advance() {
        swipe.update(now());
        var press = active;
        if (press == null || press.button != GLFW.GLFW_MOUSE_BUTTON_LEFT || press.choice.control != null
                || press.dragged || press.longPress || now() - press.at < settings.longPressMs()) return;
        press.longPress = true;
        if (valid(press.choice)) live.toggleExpanded(press.choice.snapshot);
    }

    UiId focused(long instance) { return id(focused, instance); }
    UiId hovered(long instance) { return id(hovered, instance); }
    UiId pressed(long instance) { return active == null ? null : id(active.choice, instance); }
    float swipeOffset(long instance) { return swipeInstance == instance ? swipe.value() : 0; }
    Double seekPreview(long instance) {
        return active != null && active.choice.snapshot.instance() == instance ? active.seekPreview : null;
    }

    private boolean targets(NativeInput event) {
        if (event instanceof NativeInput.Pointer pointer) {
            if (pointer.action() == NativeInput.PointerAction.MOVE) return primary != null || hovered != null;
            return pointer.action() == NativeInput.PointerAction.PRESS
                    && (pointer.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || pointer.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                    && pick(pointer.x(), pointer.y()) != null;
        }
        if (!(event instanceof NativeInput.Key key)) return false;
        return consumedKeys.contains(key.key()) || focused != null || screen instanceof LiveActivityScreen && primary != null;
    }

    private boolean press(NativeInput.Pointer pointer) {
        var choice = pick(pointer.x(), pointer.y());
        if (choice == null || !valid(choice)) return false;
        var press = new Press(choice, pointer.button(), pointer.x(), pointer.y(), now());
        if (!input.capture(module, tree, POINTER, pointer.button(), press)) return false;
        active = press;
        focus(choice);
        if (pointer.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && choice.control != null && choice.control.seek()) {
            press.seekPreview = choice.control.fraction(pointer.x());
        }
        return true;
    }

    private boolean key(NativeInput.Key key) {
        if (key.action() == NativeInput.KeyAction.RELEASE) return consumedKeys.remove(key.key());
        if (key.action() == NativeInput.KeyAction.REPEAT) return consumedKeys.contains(key.key());
        if ((key.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) return false;
        if (focused == null && !(screen instanceof LiveActivityScreen) || primary == null) return false;
        var choice = focused == null ? body(primary) : locate(focused);
        if (choice == null || !live.isInputCurrent(choice.snapshot)) return false;
        boolean handled = switch (key.key()) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (live.collapse()) yield true;
                if (screen instanceof LiveActivityScreen) yield false;
                clearFocus();
                yield true;
            }
            case GLFW.GLFW_KEY_TAB -> moveFocus(choice, (key.modifiers() & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1, false);
            case GLFW.GLFW_KEY_UP -> moveFocus(choice, -1, true);
            case GLFW.GLFW_KEY_DOWN -> moveFocus(choice, 1, true);
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> adjustOrNavigate(choice, key.key() == GLFW.GLFW_KEY_RIGHT ? 1 : -1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> activate(choice);
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> live.dismiss(choice.snapshot);
            case GLFW.GLFW_KEY_P -> {
                live.togglePin(choice.snapshot);
                yield true;
            }
            default -> false;
        };
        if (handled) {
            consumedKeys.add(key.key());
            lastInteraction = now();
        }
        return handled;
    }

    private boolean moveFocus(Choice from, int direction, boolean wrap) {
        var choices = new java.util.ArrayList<Choice>();
        appendChoices(choices, primary);
        appendChoices(choices, secondary);
        int position = 0;
        while (position < choices.size() && !same(choices.get(position), from)) position++;
        int target = position + direction;
        if (target < 0 || target >= choices.size()) {
            if (!wrap && !(screen instanceof LiveActivityScreen)) {
                clearFocus();
                return false;
            }
            target = Math.floorMod(target, choices.size());
        }
        focus(choices.get(target));
        return true;
    }

    private boolean adjustOrNavigate(Choice choice, int direction) {
        if (choice.control != null && choice.control.seek()) {
            double next = Math.max(0, Math.min(1, choice.snapshot.content().progress() + .05 * direction));
            return live.dispatch(choice.snapshot, new Action(ActivityService.SEEK, next));
        }
        return moveFocus(choice, direction, true);
    }

    private boolean activate(Choice choice) {
        if (choice.control == null) return live.toggleExpanded(choice.snapshot);
        double value = choice.control.seek() ? choice.snapshot.content().progress() : -1;
        return live.dispatch(choice.snapshot, new Action(choice.control.id(), value));
    }

    private void focus(Choice choice) {
        lastInteraction = now();
        if (focused != null && same(focused, choice)) return;
        focused = choice;
        previousNativeFocus = screen.getFocused();
        var narrator = Minecraft.getInstance().getNarrator();
        if (narrator.isActive()) {
            var label = choice.control == null ? choice.snapshot.content().title() : choice.control.label();
            narrator.sayNow(LiveSurfacePainter.component(label));
        }
    }

    private void clearFocus() {
        focused = null;
        previousNativeFocus = null;
    }

    private static void appendChoices(List<Choice> choices, Hit hit) {
        if (hit == null) return;
        choices.add(body(hit));
        for (var control : hit.controls) choices.add(new Choice(hit.snapshot, control));
    }

    private Choice pick(double x, double y) {
        var hit = primary != null && primary.geometry.contains(x, y) ? primary
                : secondary != null && secondary.geometry.contains(x, y) ? secondary : null;
        if (hit == null) return null;
        for (var control : hit.controls) if (control.contains(x, y)) return new Choice(hit.snapshot, control);
        return body(hit);
    }

    private boolean valid(Choice choice) {
        return locate(choice) != null && (choice.control == null
                ? live.isInputCurrent(choice.snapshot) : live.hasActions(choice.snapshot));
    }

    private Choice locate(Choice choice) {
        var hit = hitFor(choice.snapshot);
        if (hit == null) return null;
        if (choice.control == null) return body(hit);
        for (var control : hit.controls) if (control.id().equals(choice.control.id())) return new Choice(hit.snapshot, control);
        return null;
    }

    private Hit hitFor(Snapshot snapshot) {
        if (primary != null && sameInstance(snapshot, primary.snapshot)) return primary;
        return secondary != null && sameInstance(snapshot, secondary.snapshot) ? secondary : null;
    }

    private static boolean sameInstance(Snapshot a, Snapshot b) { return b != null && a.instance() == b.instance(); }

    private static Choice body(Hit hit) { return new Choice(hit.snapshot, null); }
    private static boolean same(Choice a, Choice b) { return a.snapshot.instance() == b.snapshot.instance() && a.id().equals(b.id()); }
    private static UiId id(Choice value, long instance) { return value != null && value.snapshot.instance() == instance ? value.id() : null; }
    private long now() { return host.nowNanos() / 1_000_000L; }

    private void clear() {
        resetPresentation();
        active = null;
        consumedKeys.clear();
        lastBodyClick = Long.MIN_VALUE;
        lastClickedInstance = 0;
        live.collapse();
    }

    record Hit(Snapshot snapshot, SurfaceGeometry geometry, List<Control> controls) {}

    static final class DetailsShortcut {
        enum Result { NONE, EXPAND, POINTER }

        private int pendingAlt = GLFW.GLFW_KEY_UNKNOWN;

        Result key(int key, int action, int modifiers) {
            boolean plain = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) == 0;
            if (action == GLFW.GLFW_PRESS) {
                boolean alt = key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT;
                pendingAlt = plain && alt && pendingAlt == GLFW.GLFW_KEY_UNKNOWN
                        && (modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? key : GLFW.GLFW_KEY_UNKNOWN;
                if (plain && key == GLFW.GLFW_KEY_GRAVE_ACCENT && (modifiers & GLFW.GLFW_MOD_ALT) != 0) {
                    return Result.POINTER;
                }
            } else if (action == GLFW.GLFW_RELEASE && pendingAlt == key && key != GLFW.GLFW_KEY_UNKNOWN) {
                reset();
                if (plain && (modifiers & GLFW.GLFW_MOD_SHIFT) == 0) return Result.EXPAND;
            }
            return Result.NONE;
        }

        void reset() { pendingAlt = GLFW.GLFW_KEY_UNKNOWN; }
    }

    private record Choice(Snapshot snapshot, Control control) {
        UiId id() { return control == null ? LiveSurfacePainter.SURFACE_FOCUS : control.id(); }
    }

    private final class Press implements UiPointerCapture.Handler {
        final Choice choice;
        final int button;
        final double x, y;
        final long at;
        boolean dragged, longPress;
        Double seekPreview;

        Press(Choice choice, int button, double x, double y, long at) {
            this.choice = choice;
            this.button = button;
            this.x = x;
            this.y = y;
            this.at = at;
        }

        @Override public void drag(double pointerX, double pointerY) {
            dragged |= Math.hypot(pointerX - x, pointerY - y) >= settings.dragThreshold();
            if (seekPreview != null) {
                var located = locate(choice);
                if (located != null) seekPreview = located.control.fraction(pointerX);
            }
            double dx = pointerX - x;
            if (dragged && choice.control == null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && (swipeInstance == choice.snapshot.instance() || Math.abs(dx) > Math.abs(pointerY - y))) {
                swipeInstance = choice.snapshot.instance();
                swipe.snap((float) Math.max(-72, Math.min(72, dx)));
            }
            lastInteraction = now();
        }

        @Override public void release(double pointerX, double pointerY) {
            active = null;
            swipe.setTarget(0, now());
            if (!valid(choice)) return;
            if (seekPreview != null) {
                var current = locate(choice);
                live.dispatch(choice.snapshot, new Action(ActivityService.SEEK, current.control.fraction(pointerX)));
                return;
            }
            if (longPress) return;
            if (dragged) {
                double dx = Math.abs(pointerX - x), dy = Math.abs(pointerY - y);
                if (choice.control == null && dx >= Math.max(26, settings.dragThreshold() * 3) && dx > dy) live.dismiss(choice.snapshot);
                return;
            }
            var over = pick(pointerX, pointerY);
            if (over == null || !same(choice, over)) return;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                live.dismiss(choice.snapshot);
            } else if (choice.control != null) {
                activate(choice);
            } else {
                long time = now();
                if (lastClickedInstance == choice.snapshot.instance() && lastBodyClick != Long.MIN_VALUE
                        && time - lastBodyClick <= settings.doubleClickMs()) {
                    live.togglePin(choice.snapshot);
                    lastBodyClick = Long.MIN_VALUE;
                } else {
                    lastBodyClick = time;
                    lastClickedInstance = choice.snapshot.instance();
                    live.toggleExpanded(choice.snapshot);
                }
            }
        }

        @Override public void cancel() {
            if (active != this) return;
            active = null;
            swipe.setTarget(0, now());
        }
    }
}
