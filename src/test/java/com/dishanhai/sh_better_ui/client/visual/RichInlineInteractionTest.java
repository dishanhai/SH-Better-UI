// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.api.InteractionApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.kernel.api.APIRegistry;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityResolver;
import com.dishanhai.sh_better_ui.kernel.api.InputService;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.NativeInput;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.ServiceRegistrar;
import com.dishanhai.sh_better_ui.kernel.api.Subscription;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIEngine;
import com.dishanhai.sh_better_ui.kernel.api.UILifecycle;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Fragment;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Rect;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Button;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Icon;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Slider;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Symbol;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class RichInlineInteractionTest {
    private static final RichObjectNode BUTTON = node("button", new Button("Press"));
    private static final RichObjectNode SLIDER = node("slider", new Slider("Level", 0, 100, 40, 10));
    private static final RichObjectNode ICON = node("icon", new Icon(Symbol.CHECK));
    private static final int LEFT = GLFW_MOUSE_BUTTON_LEFT;
    private static final int RIGHT = GLFW_MOUSE_BUTTON_RIGHT;

    @Test void clippingOcclusionAndScopeChangesDoNotActivateTheUnderlyingNode() {
        var geometry = new Geometry();
        var interaction = new RichInlineInteraction(geometry, () -> 0);
        var calls = new AtomicInteger();
        interaction.onButton(BUTTON.id(), calls::incrementAndGet);

        geometry.clip = 20;
        assertFalse(interaction.press(30, 8, LEFT));
        assertTrue(interaction.press(16, 8, LEFT));
        assertTrue(interaction.release(30, 8, LEFT));
        assertEquals(0, calls.get());

        geometry.front = new Rect(10, 0, 10, 16);
        assertFalse(interaction.press(16, 8, LEFT));
        assertTrue(interaction.press(5, 8, LEFT));
        assertTrue(interaction.press(5, 8, LEFT), "repeated forwarding is still the same press");
        assertTrue(interaction.release(5, 8, LEFT));
        assertFalse(interaction.release(5, 8, LEFT));
        assertEquals(1, calls.get());

        assertTrue(interaction.press(5, 8, LEFT));
        geometry.clip = 0;
        assertTrue(interaction.release(5, 8, LEFT));
        assertEquals(1, calls.get());
        assertFalse(interaction.hasFocus());

        geometry.clip = 20;
        interaction.clear();
        assertFalse(interaction.press(5, 8, LEFT));
        assertThrows(IllegalStateException.class, () -> interaction.onButton(BUTTON.id(), calls::incrementAndGet));
    }

    @Test void sliderPreviewCommitsOnceOrReturnsToItsPreviousValueOnCancel() {
        var interaction = new RichInlineInteraction(new Geometry(), () -> 0);
        var values = new ArrayList<Integer>();
        interaction.onSlider(SLIDER.id(), values::add);
        assertEquals(40, interaction.state(SLIDER).sliderValue());

        assertTrue(interaction.press(104, 8, LEFT));
        assertTrue(interaction.drag(160, 50, LEFT));
        assertEquals(100, interaction.state(SLIDER).sliderValue());
        assertTrue(values.isEmpty());
        assertTrue(interaction.keyPressed(GLFW_KEY_ESCAPE, 0));
        assertTrue(interaction.release(160, 50, LEFT));
        assertEquals(40, interaction.state(SLIDER).sliderValue());
        assertTrue(values.isEmpty());

        assertTrue(interaction.press(104, 8, LEFT));
        assertTrue(interaction.drag(160, 50, LEFT));
        assertTrue(interaction.release(160, 50, LEFT));
        assertFalse(interaction.release(160, 50, LEFT));
        assertEquals(List.of(100), values);
        assertEquals(100, interaction.state(SLIDER).sliderValue());
    }

    @Test void scopedCaptureUsesTheClaimAndCancelsWhenTheServiceRevokesIt() {
        var interaction = new RichInlineInteraction(new Geometry(), () -> 0);
        var values = new ArrayList<Integer>();
        interaction.onSlider(SLIDER.id(), values::add);
        var input = new CaptureService();
        var module = new CaptureModule(input);

        assertFalse(interaction.capture(module, module.scope(), input.claim, LEFT));
        assertTrue(interaction.press(104, 8, LEFT));
        assertTrue(interaction.capture(module, module.scope(), input.claim, LEFT));
        assertSame(module, input.module);
        assertSame(module.scope(), input.node);
        assertEquals(LEFT, input.button);
        assertFalse(interaction.capture(module, module.scope(), input.claim, LEFT));

        var revoked = input.handler;
        revoked.drag(160, 50);
        assertEquals(100, interaction.state(SLIDER).sliderValue());
        revoked.cancel();
        assertEquals(40, interaction.state(SLIDER).sliderValue());
        assertTrue(values.isEmpty());

        assertTrue(interaction.press(104, 8, LEFT));
        assertTrue(interaction.capture(module, module.scope(), input.claim, LEFT));
        int nextPreview = interaction.state(SLIDER).sliderValue();
        revoked.drag(160, 50);
        revoked.release(160, 50);
        assertEquals(nextPreview, interaction.state(SLIDER).sliderValue());
        assertTrue(interaction.release(160, 50, LEFT));
        assertTrue(values.isEmpty());
        input.handler.release(160, 50);
        input.handler.release(160, 50);
        assertEquals(List.of(100), values);
    }

    @Test void rejectedScopedCaptureRollsBackAndDoesNotCommitOnRelease() {
        var interaction = new RichInlineInteraction(new Geometry(), () -> 0);
        var values = new ArrayList<Integer>();
        interaction.onSlider(SLIDER.id(), values::add);
        var input = new CaptureService();
        var module = new CaptureModule(input);

        input.accepted = false;
        assertTrue(interaction.press(120, 8, LEFT));
        assertFalse(interaction.capture(module, module.scope(), input.claim, LEFT));
        assertFalse(interaction.release(160, 50, LEFT));
        assertEquals(40, interaction.state(SLIDER).sliderValue());

        input.denial = new SecurityException("missing pointer grant");
        assertTrue(interaction.press(120, 8, LEFT));
        assertSame(input.denial, assertThrows(SecurityException.class,
                () -> interaction.capture(module, module.scope(), input.claim, LEFT)));
        assertFalse(interaction.release(160, 50, LEFT));
        assertEquals(40, interaction.state(SLIDER).sliderValue());
        assertTrue(values.isEmpty());
    }

    @Test void visibleFocusOrderLetsTabLeaveTheViewAndIgnoresButtonKeyRepeat() {
        var interaction = new RichInlineInteraction(new Geometry(), () -> 0);
        var calls = new AtomicInteger();
        interaction.onButton(BUTTON.id(), calls::incrementAndGet);
        assertTrue(interaction.focusFirst(false));
        assertTrue(interaction.state(BUTTON).focused());
        assertTrue(interaction.keyPressed(GLFW_KEY_ENTER, 0));
        assertTrue(interaction.keyPressed(GLFW_KEY_ENTER, 0));
        assertEquals(1, calls.get());
        assertTrue(interaction.keyReleased(GLFW_KEY_ENTER));
        assertTrue(interaction.keyPressed(GLFW_KEY_ENTER, 0));
        assertEquals(2, calls.get());

        assertTrue(interaction.keyPressed(GLFW_KEY_TAB, 0));
        assertTrue(interaction.state(SLIDER).focused());
        assertTrue(interaction.keyPressed(GLFW_KEY_LEFT, 0));
        assertEquals(30, interaction.state(SLIDER).sliderValue());
        assertFalse(interaction.keyPressed(GLFW_KEY_TAB, 0));
        assertFalse(interaction.hasFocus());
        assertTrue(interaction.focusFirst(true));
        assertTrue(interaction.state(SLIDER).focused());
    }

    @Test void dropPreviewsOnlyVisibleTargetsAndCommitsOnMatchingRelease() {
        var geometry = new Geometry();
        var interaction = new RichInlineInteraction(geometry, () -> 0);
        var drops = new ArrayList<RichInlineInteraction.Drop>();
        interaction.draggable(ICON.id());
        interaction.onDrop(BUTTON.id(), drops::add);
        assertTrue(interaction.press(150, 8, LEFT));
        assertTrue(interaction.drag(10, 8, LEFT));
        assertTrue(interaction.state(BUTTON).dropTarget());
        assertTrue(drops.isEmpty());
        assertFalse(interaction.release(10, 8, RIGHT));
        assertTrue(interaction.release(10, 8, LEFT));
        assertEquals(List.of(new RichInlineInteraction.Drop(ICON.id(), BUTTON.id())), drops);
        assertFalse(interaction.state(BUTTON).dropTarget());

        assertTrue(interaction.press(150, 8, LEFT));
        assertTrue(interaction.drag(10, 8, LEFT));
        geometry.front = new Rect(0, 0, 16, 16);
        assertTrue(interaction.release(10, 8, LEFT));
        assertEquals(1, drops.size());
    }

    @Test void boundGesturesAreDispatchedOncePerRelease() {
        var time = new AtomicLong();
        var interaction = new RichInlineInteraction(new Geometry(), time::get);
        var calls = new AtomicInteger();
        var gestures = new ArrayList<RichInlineInteraction.Gesture>();
        interaction.onButton(BUTTON.id(), calls::incrementAndGet);
        interaction.onGesture(BUTTON.id(), gestures::add);

        assertTrue(interaction.press(12, 8, LEFT));
        time.set(50);
        assertTrue(interaction.release(12, 8, LEFT));
        time.set(100);
        assertTrue(interaction.press(12, 8, LEFT));
        assertTrue(interaction.release(12, 8, LEFT));
        time.set(120);
        assertTrue(interaction.press(12, 8, LEFT));
        time.set(660);
        assertTrue(interaction.release(12, 8, LEFT));
        assertTrue(interaction.press(12, 8, RIGHT));
        assertTrue(interaction.release(12, 8, RIGHT));
        assertEquals(1, calls.get());
        assertEquals(List.of(RichInlineInteraction.Gesture.DOUBLE_CLICK, RichInlineInteraction.Gesture.LONG_PRESS,
                RichInlineInteraction.Gesture.SECONDARY), gestures);
    }

    private static RichObjectNode node(String name, RichObjectNode.Content content) {
        return new RichObjectNode(new UiId("test", name), content, 1, 1, 16, name);
    }

    private record CaptureModule(CaptureService input) implements ModuleContext, UIEngine {
        @Override public UIEngine engine() { return this; }
        @Override public ScopeHandle scope() { return input.scope; }
        @Override public ModuleDescriptor descriptor() { throw new UnsupportedOperationException(); }
        @Override public ServiceRegistrar services() { throw new UnsupportedOperationException(); }
        @Override public APIRegistry registry() { throw new UnsupportedOperationException(); }
        @Override public CapabilityResolver capabilities() { throw new UnsupportedOperationException(); }
        @Override public UILifecycle lifecycle() { throw new UnsupportedOperationException(); }

        @Override public <T extends UIService> T require(ServiceKey<T> key) {
            assertEquals(UIApis.INPUT, key);
            return key.type().cast(input);
        }
    }

    private static final class CaptureService implements InputService {
        private final ScopeHandle scope = (ScopeHandle) java.lang.reflect.Proxy.newProxyInstance(
                ScopeHandle.class.getClassLoader(), new Class<?>[]{ScopeHandle.class}, (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
        private final UiId claim = new UiId("test", "rich_pointer");
        private ModuleContext module;
        private ScopeHandle node;
        private int button;
        private UiPointerCapture.Handler handler;
        private boolean accepted = true;
        private SecurityException denial;

        @Override public InteractionApi interactions() { throw new UnsupportedOperationException(); }
        @Override public Subscription target(ModuleContext module, ScopeHandle node, int layer,
                                             Predicate<NativeInput> hit) {
            throw new UnsupportedOperationException();
        }

        @Override public boolean capture(ModuleContext module, ScopeHandle node, UiId claim, int button,
                                         UiPointerCapture.Handler handler) {
            this.module = module;
            this.node = node;
            assertEquals(this.claim, claim);
            this.button = button;
            if (denial != null) throw denial;
            if (accepted) this.handler = handler;
            return accepted;
        }
    }

    private static final class Geometry implements RichInlineInteraction.Geometry {
        private final List<Fragment> fragments = List.of(new Fragment(BUTTON, "", new Rect(0, 0, 40, 16)),
                new Fragment(SLIDER, "", new Rect(100, 0, 40, 16)),
                new Fragment(ICON, "", new Rect(148, 0, 16, 16)));
        private int clip = 180;
        private Rect front;

        @Override public Optional<Fragment> hit(double x, double y) {
            if (x < 0 || x >= clip || front != null && front.contains(x, y)) return Optional.empty();
            return fragments.stream().filter(fragment -> fragment.bounds().contains(x, y)).findFirst();
        }

        @Override public List<Fragment> objects() {
            return fragments.stream().filter(fragment -> fragment.bounds().x() < clip).toList();
        }

        @Override public double localX(double x, double y) { return x; }
    }
}
