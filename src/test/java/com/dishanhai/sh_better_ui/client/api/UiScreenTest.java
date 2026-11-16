package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：界面构建快照、重布局和离开页面的资源生命周期。 */
class UiScreenTest {
    @Test
    void escapeStillCancelsIngredientDragAfterFocusClipGuardMerge() throws Exception {
        // Allocate a native widget shell without bootstrapping Minecraft's item registry.
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var slots = (com.dishanhai.sh_better_ui.client.gui.scaled.UiIngredientSlots)
                unsafe.allocateInstance(com.dishanhai.sh_better_ui.client.gui.scaled.UiIngredientSlots.class);
        slots.setX(0); slots.setY(0); slots.setWidth(40); slots.setHeight(20);
        slots.visible = true; slots.active = true;
        slots.setMessage(Component.literal("Ingredients"));
        var stateField = slots.getClass().getDeclaredField("state");
        stateField.setAccessible(true);
        stateField.set(slots, new com.dishanhai.sh_better_ui.kernel.api.controls.IngredientSlotsState(2, 2));
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onInit(page -> page.addClipped(slots, () -> new Bounds(0, 0, 60, 40))).build();
        screen.width = 60; screen.height = 40; screen.init();
        screen.setFocused(slots);
        var field = slots.getClass().getDeclaredField("state");
        field.setAccessible(true);
        var state = (com.dishanhai.sh_better_ui.kernel.api.controls.IngredientSlotsState) field.get(slots);
        state.press(0, 5, 5, true);
        screen.setDragging(true);
        assertTrue(screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertFalse(screen.isDragging());
        assertEquals(-1, state.pressed());
        assertTrue(slots.consumeCancelledRelease());
        state.press(0, 5, 5, true);
        screen.setDragging(true);
        slots.setY(80); // A scroll or relayout hides the dragged control.
        assertTrue(screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertFalse(screen.isDragging());
        assertEquals(-1, state.pressed());
        assertTrue(slots.consumeCancelledRelease());
        screen.removed();
    }

    @Test
    void detailResetLabelTracksEditsThatInvalidateUndo() {
        var session = new com.dishanhai.sh_better_ui.kernel.api.settings.SettingsDraftSession<>(
                java.util.Map.of("enabled", false), java.util.Map.of("enabled", true));
        var detail = new UiSettingsDetailPage(Component.literal("Detail"), java.util.List.of(),
                new UiSettingsDetailPage.Actions(
                        () -> Component.literal(session.canUndoReset() ? "Undo reset" : "Restore defaults"),
                        Component.literal("Cancel"), Component.literal("Apply"), session::resetDefaults,
                        session::cancel, () -> null, session::dirty));
        // Mount native controls without requesting the live Minecraft theme service.
        var screen = new UiScreenBuilder(Component.literal("Detail")).backButton(false)
                .onInit(detail::init).build();
        screen.width = 400; screen.height = 300; screen.init();
        var reset = screen.children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals("Restore defaults")).findFirst().orElseThrow();
        session.resetDefaults();
        detail.syncActionState();
        assertEquals("Undo reset", reset.getMessage().getString());
        session.edit("enabled", false);
        detail.syncActionState();
        assertEquals("Restore defaults", reset.getMessage().getString());
        screen.removed();
    }

    @Test
    void disposingCannotBeVetoedByAnEditingCloseGuard() {
        var screen = new UiScreenBuilder(Component.empty()).build();
        screen.onCloseRequest(() -> false);
        assertFalse(screen.permitsClose());
        screen.dispose();
        assertTrue(screen.permitsClose());
    }

    @Test
    void clippedFocusCannotReceiveKeyboardOrTextAfterViewportMovement() {
        var widget = new KeyboardWidget();
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onInit(page -> page.addClipped(widget, () -> new Bounds(0, 0, 60, 40))).build();
        screen.width = 60; screen.height = 40; screen.init();
        screen.setFocused(widget);
        widget.setY(50); // Equivalent to scrolling the field out of the viewport.
        assertTrue(screen.keyPressed(257, 0, 0));
        assertEquals(0, widget.keys);
        assertEquals(null, screen.getFocused());
        screen.setFocused(widget);
        assertTrue(screen.charTyped('x', 0));
        assertEquals(0, widget.characters);
        screen.removed();
    }

    @Test
    void focusVisibilityCallbackRunsBeforeAnotherKeyWithoutRendering() {
        var widget = new KeyboardWidget();
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onInit(page -> {
                    page.addClipped(widget, () -> new Bounds(0, 0, 60, 40));
                    page.onFocusChanged(target -> { if (target == widget) widget.setY(0); });
                }).build();
        screen.width = 60; screen.height = 40; screen.init();
        widget.setY(80);
        screen.setFocused(widget);
        assertEquals(0, widget.getY());
        assertTrue(screen.keyPressed(257, 0, 0));
        assertEquals(1, widget.keys);
        screen.removed();
    }

    private static final class KeyboardWidget extends AbstractWidget {
        int keys, characters;
        KeyboardWidget() { super(0, 0, 40, 20, Component.empty()); }
        @Override public boolean keyPressed(int key, int scanCode, int modifiers) { keys++; return true; }
        @Override public boolean charTyped(char character, int modifiers) { characters++; return true; }
        @Override protected void renderWidget(GuiGraphics graphics, int x, int y, float tick) {}
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {}
    }

    @Test
    void stackUsesTheSameTargetForHitsClicksScrollAndRelease() {
        var lower = new StackWidget();
        var upper = new StackWidget();
        var otherStack = new StackWidget();
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onInit(page -> {
                    page.addClipped(lower, () -> new Bounds(0, 0, 60, 40), "main");
                    page.addClipped(otherStack, () -> new Bounds(0, 0, 60, 40), "other");
                    page.addClipped(upper, () -> new Bounds(10, 0, 20, 40), "main");
                }).build();
        screen.width = 60;
        screen.height = 40;
        screen.init();

        assertSame(upper, screen.getChildAt(15, 10).orElseThrow());
        assertTrue(screen.mouseScrolled(15, 10, 0, -1));
        assertTrue(screen.mouseClicked(15, 10, 0));
        assertSame(upper, screen.getFocused());
        assertTrue(screen.mouseReleased(55, 35, 0));
        assertEquals(1, upper.clicks);
        assertEquals(1, upper.releases);
        assertEquals(1, upper.scrolls);
        assertEquals(0, lower.clicks);
        assertEquals(0, lower.scrolls);
        assertEquals(0, otherStack.clicks);
        assertSame(lower, screen.getChildAt(5, 10).orElseThrow());

        upper.transparent = true;
        assertSame(lower, screen.getChildAt(15, 10).orElseThrow());
        upper.transparent = false;
        upper.visible = false;
        assertSame(lower, screen.getChildAt(15, 10).orElseThrow());
        upper.visible = true;
        upper.active = false;
        assertSame(lower, screen.getChildAt(15, 10).orElseThrow());
        assertTrue(screen.mouseClicked(15, 10, 0));
        assertSame(lower, screen.getFocused());
        assertTrue(screen.mouseReleased(15, 10, 0));
        screen.removed();
    }

    private static final class StackWidget extends AbstractWidget {
        private int clicks;
        private int releases;
        private int scrolls;
        private boolean transparent;

        private StackWidget() {
            super(0, 0, 40, 30, Component.empty());
        }

        @Override
        public boolean isMouseOver(double x, double y) {
            return !transparent && super.isMouseOver(x, y);
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            if (!isMouseOver(x, y)) return false;
            clicks++;
            return true;
        }

        @Override
        public boolean mouseReleased(double x, double y, int button) {
            releases++;
            return true;
        }

        public boolean mouseScrolled(double x, double y, double vertical) {
            scrolls++;
            return true;
        }

        public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
            return mouseScrolled(x, y, vertical);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int x, int y, float partialTick) {}

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {}
    }

    @Test
    void clipsWidgetHitsButDeliversReleaseToTheDraggingWidget() {
        int[] clicksAndReleases = new int[2];
        var widget = new AbstractWidget(60, 12, 40, 20, Component.empty()) {
            @Override
            public boolean mouseClicked(double x, double y, int button) {
                if (!isMouseOver(x, y)) return false;
                clicksAndReleases[0]++;
                return true;
            }

            @Override
            public boolean mouseReleased(double x, double y, int button) {
                clicksAndReleases[1]++;
                return true;
            }

            @Override
            protected void renderWidget(GuiGraphics graphics, int x, int y, float partialTick) {}

            @Override
            protected void updateWidgetNarration(NarrationElementOutput narration) {}
        };
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onInit(page -> page.add(widget)).build();
        screen.width = 80;
        screen.height = 60;
        screen.init();

        assertFalse(screen.mouseClicked(90, 18, 0));
        assertTrue(screen.getChildAt(90, 18).isEmpty());
        assertTrue(screen.mouseClicked(70, 18, 0));
        assertSame(widget, screen.getChildAt(70, 18).orElseThrow());
        assertTrue(screen.mouseReleased(90, 18, 0));
        assertFalse(screen.isDragging());
        assertEquals(1, clicksAndReleases[0]);
        assertEquals(1, clicksAndReleases[1]);
        screen.removed();
    }

    @Test
    void changingBuilderDoesNotChangeAnAlreadyBuiltScreen() {
        int[] calls = new int[2];
        var builder = new UiScreenBuilder(Component.empty()).backButton(false).onInit(screen -> calls[0]++);
        var first = builder.build();
        builder.onInit(screen -> calls[1]++).pausesGame(true);
        var second = builder.build();

        first.init();
        second.init();

        assertEquals(1, calls[0]);
        assertEquals(1, calls[1]);
        assertFalse(first.isPauseScreen());
        assertTrue(second.isPauseScreen());
        first.removed();
        second.removed();
    }

    @Test
    void relayoutKeepsContextAndReturningAfterRemovalCreatesAnother() {
        int[] removals = new int[1];
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onRemoved(page -> removals[0]++).build();
        screen.init();
        var first = screen.context();
        screen.init();
        assertSame(first, screen.context());

        screen.removed();
        screen.removed();
        assertEquals(1, removals[0]);
        assertTrue(first.isClosed());

        screen.init();
        assertNotSame(first, screen.context());
        assertFalse(screen.context().isClosed());
        screen.removed();
        assertEquals(2, removals[0]);
    }

    @Test
    void relayoutRetargetsTheSameTrackAndReopeningRejectsOldResponses() {
        var screen = new UiScreenBuilder(Component.empty()).backButton(false).build();
        screen.width = 400;
        screen.init();
        var first = screen.context();
        var motion = first.animation(Spring.INDICATOR, 0f);
        motion.transitionTo(screen.viewport().width() / 2f, 100L, InterruptibleAnimation.Policy.RETARGET, 30);
        float before = motion.sample(150L);

        screen.width = 240;
        screen.init();
        assertSame(first, screen.context());
        long resized = motion.transitionTo(screen.viewport().width() / 2f, 150L,
                InterruptibleAnimation.Policy.RETARGET, 30);
        assertEquals(before, motion.value());
        assertEquals(120f, motion.target());

        var clientTasks = new ArrayDeque<Runnable>();
        Object connection = new Object();
        var oldQuery = first.manage(new UiRequest<String>(UiRequest.Mode.QUERY,
                clientTasks::add, () -> connection, () -> 150L));
        var oldId = oldQuery.begin(1_000L).orElseThrow();
        oldQuery.receive(oldId, true, "stale");
        var operation = new UiRequest<String>(UiRequest.Mode.OPERATION, clientTasks::add, () -> connection, () -> 150L);
        var operationId = operation.begin(1_000L).orElseThrow();
        screen.removed();
        operation.receive(operationId, true, "committed");
        screen.init();
        var newQuery = screen.context().manage(new UiRequest<String>(UiRequest.Mode.QUERY,
                clientTasks::add, () -> connection, () -> 150L));
        var newId = newQuery.begin(1_000L).orElseThrow();
        newQuery.receive(oldId, true, "stale");
        newQuery.receive(newId, true, "current");
        while (!clientTasks.isEmpty()) clientTasks.remove().run();

        assertFalse(motion.running());
        assertFalse(motion.takeCompletion(resized));
        assertEquals(UiRequest.Status.CLOSED, oldQuery.snapshot().status());
        assertEquals("current", newQuery.snapshot().value());
        assertEquals(new UiRequest.Snapshot<>(UiRequest.Status.SUCCEEDED, operationId, "committed"), operation.snapshot());
        assertTrue(operation.acknowledgeReceipt(operationId));
        screen.removed();
        operation.close();
    }

    @Test
    void rejectingASlowOperationRollsBackTheVisualTrackWithoutDuplicateSubmission() {
        var screen = new UiScreenBuilder(Component.empty()).backButton(false).build();
        screen.init();
        var progress = screen.context().animation(Spring.PANEL, 0.2f);
        var clientTasks = new ArrayDeque<Runnable>();
        Object connection = new Object();
        long[] now = {100L};
        var operation = new UiRequest<Float>(UiRequest.Mode.OPERATION, clientTasks::add, () -> connection, () -> now[0]);
        var id = operation.begin(1_200L).orElseThrow();
        progress.transitionTo(0.8f, now[0], InterruptibleAnimation.Policy.RETARGET, 20);
        for (long delay : new long[]{50L, 200L, 1_000L}) {
            now[0] = 100L + delay;
            assertTrue(progress.sample(now[0]) > 0.2f);
            assertTrue(operation.begin(1_200L).isEmpty());
            assertEquals(UiRequest.Status.PENDING, operation.snapshot().status());
        }
        float before = progress.value();
        operation.receive(id, false, 0.2f);
        operation.receive(id, true, 0.8f);
        while (!clientTasks.isEmpty()) clientTasks.remove().run();
        var rejected = operation.snapshot();
        assertEquals(UiRequest.Status.REJECTED, rejected.status());
        assertSame(rejected, operation.snapshot());
        progress.transitionTo(rejected.value(), now[0], InterruptibleAnimation.Policy.BLEND, 30);
        assertEquals(before, progress.sample(now[0]));
        assertEquals(0.2f, progress.sample(now[0] + 180L));
        assertTrue(operation.acknowledgeReceipt(id));
        screen.removed();
        operation.close();
    }

    @Test
    void removalCallbackFailureStillClosesContext() {
        var failure = new IllegalStateException("removal");
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onRemoved(page -> { throw failure; }).build();
        screen.init();
        screen.context().manage(() -> { throw new IllegalArgumentException("resource cleanup"); });

        assertSame(failure, assertThrows(IllegalStateException.class, screen::removed));
        assertTrue(screen.context().isClosed());
        assertEquals(1, failure.getSuppressed().length);
        screen.removed();
    }

    @Test
    void initializationFailureClosesContextAndKeepsOriginalFailure() {
        var failure = new IllegalStateException("initialization");
        var screen = new UiScreenBuilder(Component.empty())
                .backButton(false)
                .onInit(page -> { throw failure; })
                .onRemoved(page -> { throw new IllegalArgumentException("cleanup"); })
                .build();

        assertSame(failure, assertThrows(IllegalStateException.class, screen::init));
        assertTrue(screen.context().isClosed());
        assertEquals(1, failure.getSuppressed().length);
    }

    @Test
    void viewportReservesBackBarAndRemainsInsideSmallWindows() {
        var screen = new UiScreenBuilder(Component.empty()).build();
        screen.width = 400;
        screen.height = 240;
        assertEquals(34, screen.viewport().y());
        assertEquals(206, screen.viewport().height());
        screen.width = 1;
        screen.height = 1;
        assertEquals(1, screen.viewport().y());
        assertEquals(0, screen.viewport().height());

        var full = new UiScreenBuilder(Component.empty()).backButton(false).build();
        full.width = 400;
        full.height = 240;
        assertEquals(0, full.viewport().y());
        assertEquals(240, full.viewport().height());
    }

    @Test
    void disposedScreensCannotRunOldCallbacksOrOpenAgain() {
        int[] calls = new int[3];
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onInit(page -> calls[0]++)
                .onTick(page -> calls[1]++)
                .onRemoved(page -> calls[2]++)
                .build();
        screen.init();
        var context = screen.context();
        screen.dispose();
        screen.dispose();
        screen.init();
        screen.tick();
        screen.render(null, 0, 0, 0);

        assertEquals(1, calls[0]);
        assertEquals(0, calls[1]);
        assertEquals(1, calls[2]);
        assertTrue(context.isClosed());
        assertTrue(screen.isDisposed());
        assertThrows(IllegalStateException.class, screen::open);
    }

    @Test
    void suppliedContextFactoryIsLazyAndBuiltScreensAreTracked() {
        int[] creations = new int[1];
        var built = new java.util.ArrayList<UiScreen>();
        var builder = new UiScreenBuilder(Component.empty(), () -> {
            creations[0]++;
            return new UiApiContext(new UiExtensionRegistry());
        }, built::add).backButton(false);
        var screen = builder.build();
        assertEquals(1, built.size());
        assertSame(screen, built.getFirst());
        assertEquals(0, creations[0]);

        screen.init();
        screen.init();
        assertEquals(1, creations[0]);
        screen.removed();
        screen.init();
        assertEquals(2, creations[0]);
        screen.removed();
    }

    @Test
    void disposingAnEntireScriptGenerationSkipsItsOldParentChain() {
        var external = new Screen(Component.empty()) {};
        var first = new UiScreenBuilder(Component.empty()).parent(external).build();
        var second = new UiScreenBuilder(Component.empty()).parent(first).build();
        var child = new UiScreenBuilder(Component.empty()).parent(second).build();

        assertSame(second, UiScreen.returnTarget(second, null).screen());
        UiScreen.disposeAll(java.util.List.of(first, second, child));
        assertSame(external, UiScreen.returnTarget(second, null).screen());
        assertTrue(first.isDisposed());
        assertTrue(second.isDisposed());
        assertTrue(child.isDisposed());
    }

    @Test
    void batchDisposalMarksEverythingFirstAndContinuesAfterCleanupFailures() {
        var pages = new java.util.ArrayList<UiScreen>();
        int[] removals = new int[1];
        var failure = new IllegalStateException("first cleanup");
        var first = new UiScreenBuilder(Component.empty()).backButton(false).onRemoved(page -> {
            assertTrue(pages.stream().allMatch(UiScreen::isDisposed));
            throw failure;
        }).build();
        var second = new UiScreenBuilder(Component.empty()).backButton(false)
                .onRemoved(page -> removals[0]++).build();
        pages.add(first);
        pages.add(second);
        first.init();
        second.init();

        assertSame(failure, assertThrows(IllegalStateException.class, () -> UiScreen.disposeAll(pages)));
        assertTrue(first.context().isClosed());
        assertTrue(second.context().isClosed());
        assertEquals(1, removals[0]);
        UiScreen.disposeAll(pages);
        assertEquals(1, removals[0]);
    }

    @Test
    void initializationErrorHandlerRunsAfterDisposalAndResourceCleanup() {
        int[] calls = new int[3];
        var failure = new IllegalArgumentException("script initialization");
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onInit(page -> {
                    calls[0]++;
                    page.context().manage(() -> calls[1]++);
                    throw failure;
                })
                .onError((page, error) -> {
                    calls[2]++;
                    assertTrue(page.isDisposed());
                    assertTrue(page.context().isClosed());
                    assertEquals(1, calls[1]);
                    assertSame(failure, error);
                }).build();

        assertDoesNotThrow(() -> screen.init());
        screen.init();
        screen.tick();
        screen.render(null, 0, 0, 0);
        assertEquals(1, calls[0]);
        assertEquals(1, calls[2]);
    }

    @Test
    void tickErrorsAreReportedOnceWithRemovalFailuresSuppressed() {
        var failure = new IllegalStateException("script tick");
        var cleanupFailure = new IllegalArgumentException("script removal");
        int[] reports = new int[1];
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onTick(page -> { throw failure; })
                .onRemoved(page -> { throw cleanupFailure; })
                .onError((page, error) -> {
                    reports[0]++;
                    assertSame(failure, error);
                    assertTrue(page.context().isClosed());
                    assertEquals(1, error.getSuppressed().length);
                    assertSame(cleanupFailure, error.getSuppressed()[0]);
                }).build();
        screen.init();

        assertDoesNotThrow(screen::tick);
        screen.tick();
        screen.removed();
        assertTrue(screen.isDisposed());
        assertEquals(1, reports[0]);
    }

    @Test
    void removalErrorsCanBeReportedAfterAllContextResourcesClose() {
        int[] calls = new int[2];
        var failure = new IllegalStateException("script removal");
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onRemoved(page -> { throw failure; })
                .onError((page, error) -> {
                    calls[1]++;
                    assertSame(failure, error);
                    assertEquals(1, calls[0]);
                    assertTrue(page.context().isClosed());
                }).build();
        screen.init();
        screen.context().manage(() -> calls[0]++);

        assertDoesNotThrow(screen::removed);
        screen.removed();
        assertTrue(screen.isDisposed());
        assertEquals(1, calls[1]);
    }

    @Test
    void errorHandlerFailuresAndJvmErrorsContinueToPropagate() {
        var handlerFailure = new IllegalArgumentException("error handler");
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onTick(page -> { throw new IllegalStateException("script tick"); })
                .onError((page, error) -> { throw handlerFailure; }).build();
        screen.init();
        assertSame(handlerFailure, assertThrows(IllegalArgumentException.class, screen::tick));
        assertTrue(screen.context().isClosed());

        var fatal = new AssertionError("fatal callback");
        int[] reports = new int[1];
        var other = new UiScreenBuilder(Component.empty()).backButton(false)
                .onTick(page -> { throw fatal; })
                .onError((page, error) -> reports[0]++).build();
        other.init();
        assertSame(fatal, assertThrows(AssertionError.class, other::tick));
        assertEquals(0, reports[0]);
        other.removed();
    }

    @Test
    void fatalCleanupErrorsArePropagatedWithTheScriptFailureAttached() {
        var failure = new IllegalStateException("script tick");
        var fatal = new AssertionError("fatal resource cleanup");
        int[] reports = new int[1];
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onTick(page -> { throw failure; })
                .onError((page, error) -> reports[0]++).build();
        screen.init();
        screen.context().manage(() -> { throw fatal; });

        assertSame(fatal, assertThrows(AssertionError.class, screen::tick));
        assertEquals(0, reports[0]);
        assertEquals(1, fatal.getSuppressed().length);
        assertSame(failure, fatal.getSuppressed()[0]);
        assertTrue(screen.isDisposed());
        assertTrue(screen.context().isClosed());
    }
}
