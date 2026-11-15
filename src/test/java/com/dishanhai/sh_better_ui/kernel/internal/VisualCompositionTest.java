// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.Node;
import com.dishanhai.sh_better_ui.client.gui.layout.PixelAlignment;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.visual.FrameViewSnapshot;
import com.dishanhai.sh_better_ui.client.visual.RichTextEffects;
import com.dishanhai.sh_better_ui.client.visual.RichTextView;
import com.dishanhai.sh_better_ui.client.visual.VisualFrameCoordinator;
import com.dishanhai.sh_better_ui.client.visual.VisualFrameCoordinator.Frame;
import com.dishanhai.sh_better_ui.client.visual.VisualLayoutBinding;
import com.dishanhai.sh_better_ui.client.visual.VisualLayoutBinding.LayoutPass;
import com.dishanhai.sh_better_ui.client.visual.VisualNodePass;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class VisualCompositionTest {
    private static final Rect VIEWPORT = new Rect(0, 0, 80, 60);

    @Test
    void childRichTextViewsCanDrawInTheirParentPassAndCloseIndependently() throws Exception {
        withScreen((fixture, screen) -> {
            var screens = fixture.client.screens();
            var tree = screens.mountTrusted(fixture.module, screen, id("tree"));
            var firstScope = tree.openChild(ScopeKind.COMPONENT, id("first"));
            var secondScope = tree.openChild(ScopeKind.COMPONENT, id("second"));
            var otherTree = screens.mountTrusted(fixture.module, screen, id("other_tree"));
            var layout = new AlignmentLayout(VIEWPORT);
            var firstNode = layout.node("first", AlignmentFrame.box(new Rect(5, 5, 10, 10)));
            var secondNode = layout.node("second", AlignmentFrame.box(new Rect(25, 5, 10, 10)));
            var binding = bind(fixture, tree, layout);
            assertThrows(IllegalStateException.class, () -> bind(fixture, otherTree, layout));
            var firstView = prepare(fixture.module, firstScope, firstNode);
            var secondView = prepare(fixture.module, secondScope, secondNode);
            var otherView = prepare(fixture.module, otherTree, firstNode);
            var drawn = new ArrayList<Node>();
            var frame = begin(screen, binding, layout);
            var pass = new LayoutPass() {
                @Override public FrameViewSnapshot snapshot() { return frame.snapshot(); }
                @Override public void at(Node node, Consumer<VisualNodePass> draw) { drawn.add(node); }
            };

            draw(firstView, pass);
            draw(secondView, pass);
            assertEquals(List.of(firstNode, secondNode), drawn);
            assertThrows(IllegalArgumentException.class, () -> draw(otherView, pass));
            var builtinModule = (ModuleContext) field(ClientUiKernel.class, "theme").get(fixture.client);
            var builtinTree = screens.mountTrusted(builtinModule, screen, new UiId("sh_better_ui", "foreign_tree"));
            var foreignView = prepare(builtinModule, builtinTree, firstNode);
            assertThrows(IllegalArgumentException.class, () -> draw(foreignView, pass));
            try (var otherEngine = new ClientServiceFixture(Set.of(), Set.of())) {
                var unrelatedView = prepare(otherEngine.module, otherEngine.tree(), firstNode);
                assertThrows(IllegalArgumentException.class, () -> draw(unrelatedView, pass));
            }

            firstScope.close();
            assertFalse(firstView.isActive());
            assertThrows(IllegalStateException.class, () -> draw(firstView, pass));
            assertTrue(secondView.isActive());
            draw(secondView, pass);
            assertEquals(List.of(firstNode, secondNode, secondNode), drawn);

            screens.show(null);
            screens.show(screen);
            var replacementTree = screens.mountTrusted(fixture.module, screen, id("tree"));
            var replacementScope = replacementTree.openChild(ScopeKind.COMPONENT, id("second"));
            var replacement = prepare(fixture.module, replacementScope, secondNode);
            assertNotEquals(secondScope.generation(), replacementScope.generation());
            assertThrows(IllegalArgumentException.class, () -> draw(replacement, pass));
        });
    }

    @Test
    void compositionAndForegroundFailuresExcludeOnlyTheirNodesUntilTheNextFrame() throws Exception {
        withScreen((fixture, screen) -> {
            var tree = fixture.client.screens().mountTrusted(fixture.module, screen, id("tree"));
            var layout = new AlignmentLayout(VIEWPORT);
            var immediate = layout.node("immediate", AlignmentFrame.box(new Rect(5, 5, 10, 10)));
            var deferred = layout.node("deferred", AlignmentFrame.box(new Rect(25, 5, 10, 10)));
            var sibling = layout.node("sibling", AlignmentFrame.box(new Rect(45, 5, 10, 10)));
            var binding = bind(fixture, tree, layout);
            var frame = begin(screen, binding, layout);
            pass(frame.frame()).at(immediate, visual -> { throw new IllegalStateException("Injected node failure"); });
            deferFailure(frame.frame(), deferred);
            var hits = new ArrayList<Boolean>();
            frame.frame().deferForeground(1, graphics -> {
                hits.addAll(List.of(frame.snapshot().hitTest(immediate, 8, 8), frame.snapshot().hitTest(deferred, 28, 8),
                        frame.snapshot().hitTest(sibling, 48, 8)));
                // Prevent the native framebuffer publication check in this headless frame.
                clearCandidate(frame.frame());
            });
            VisualFrameCoordinator.prepareBlur(screen, null);
            VisualFrameCoordinator.drawForeground(screen, null);
            assertEquals(List.of(false, false, true), hits);

            // Supply the same presentation as publication without requiring the native framebuffer.
            field(binding.getClass(), "snapshot").set(binding, frame.snapshot());
            assertFalse(binding.hitTest(immediate, 8, 8));
            assertFalse(binding.hitTest(deferred, 28, 8));
            assertTrue(binding.hitTest(sibling, 48, 8));
            VisualFrameCoordinator.end();

            var next = begin(screen, binding, layout);
            assertTrue(next.snapshot().hitTest(immediate, 8, 8));
            assertTrue(next.snapshot().hitTest(deferred, 28, 8));
            assertFalse(frame.snapshot().hitTest(immediate, 8, 8));
        });
    }

    private static RichTextView prepare(ModuleContext module, ScopeHandle scope, Node node) {
        var view = RichTextView.attach(module, scope, node);
        view.prepare(new RichDocument(List.of()), new Font(ignored -> null, false), 10);
        return view;
    }

    private static void draw(RichTextView view, LayoutPass pass) {
        view.draw(pass, 0xffffffff, UiPreferences.DEFAULT, RichTextEffects.NONE, 0, Double.NaN, Double.NaN);
    }

    private static VisualLayoutBinding bind(ClientServiceFixture fixture, ScopeHandle scope, AlignmentLayout layout) {
        return fixture.module.engine().require(UIApis.LAYOUT).bindVisual(fixture.module, scope, layout, time -> {});
    }

    private static UiId id(String path) {
        return new UiId("example", path);
    }

    private static TestFrame begin(Screen screen, VisualLayoutBinding binding, AlignmentLayout layout) throws Exception {
        VisualFrameCoordinator.begin(screen, null, 0);
        var frame = VisualFrameCoordinator.screenFrame(screen);
        assertNotNull(frame);
        layout.resolve(frame.id(), true);
        var constructor = FrameViewSnapshot.class.getDeclaredConstructor(binding.getClass(), long.class, long.class,
                Rect.class, Map.class);
        constructor.setAccessible(true);
        var snapshot = constructor.newInstance(binding, frame.id(), frame.id(), VIEWPORT, layout.snapshot(PixelAlignment.ONE));
        field(Frame.class, "binding").set(frame, binding);
        field(Frame.class, "snapshot").set(frame, snapshot);
        return new TestFrame(frame, snapshot);
    }

    private static LayoutPass pass(Frame frame) throws Exception {
        var type = Class.forName(VisualFrameCoordinator.class.getName() + "$Pass");
        var constructor = type.getDeclaredConstructor(Frame.class);
        constructor.setAccessible(true);
        return (LayoutPass) constructor.newInstance(frame);
    }

    private static void clearCandidate(Frame frame) {
        try {
            field(Frame.class, "snapshot").set(frame, null);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void deferFailure(Frame frame, Node node) throws Exception {
        var type = Class.forName(VisualFrameCoordinator.class.getName() + "$Foreground");
        var constructor = type.getDeclaredConstructor(Node.class, int.class, Consumer.class);
        constructor.setAccessible(true);
        var entry = constructor.newInstance(node, 0, (Consumer<Frame>) ignored -> {
            throw new IllegalStateException("Injected foreground failure");
        });
        @SuppressWarnings("unchecked")
        var foreground = (List<Object>) field(Frame.class, "foreground").get(frame);
        foreground.add(entry);
    }

    private static void withScreen(ScreenTest test) throws Exception {
        var instance = field(Minecraft.class, "instance");
        var renderThread = field(RenderSystem.class, "renderThread");
        var previousClient = instance.get(null);
        var previousThread = renderThread.get(null);
        // Native constructors require a window and game registries. The test enters before GPU work.
        var unsafe = (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
        var client = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        client.screen = new Screen(Component.empty()) {};
        try {
            instance.set(null, client);
            renderThread.set(null, Thread.currentThread());
            try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
                fixture.client.screens().show(client.screen);
                try {
                    test.run(fixture, client.screen);
                } finally {
                    VisualFrameCoordinator.end();
                }
            }
        } finally {
            instance.set(null, previousClient);
            renderThread.set(null, previousThread);
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private record TestFrame(Frame frame, FrameViewSnapshot snapshot) {}

    private interface ScreenTest {
        void run(ClientServiceFixture fixture, Screen screen) throws Exception;
    }
}
