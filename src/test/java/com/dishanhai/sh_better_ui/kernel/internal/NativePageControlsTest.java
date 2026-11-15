// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.TabScrollState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageTextReadability;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValueSchema;
import com.dishanhai.sh_better_ui.kernel.internal.page.PageDataState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class NativePageControlsTest {
    @Test
    void glideMenuFlipsAndKeepsOnlyWholeRows() {
        var glide = new NativePageOptions.Glide(List.of(), 100, false, true, true);
        var clip = new Bounds(10, 20, 160, 160);
        assertEquals(new Bounds(55, 22, 100, 92),
                NativePageOptions.anchoredMenu(new Bounds(115, 120, 40, 22), clip, 112, glide));
        assertEquals(new Bounds(10, 62, 100, 112),
                NativePageOptions.anchoredMenu(new Bounds(12, 34, 40, 22), clip, 112, glide));
    }

    @Test
    void glideMenuCommitsTheReleaseRowAndCancelsOutside() throws ReflectiveOperationException {
        var source = new UiButton(10, 10, 40, 22, Component.empty(), button -> {});
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onInit(page -> page.add(source)).build();
        initScreen(screen);
        var constructor = UiLocalModal.class.getDeclaredConstructor(Screen.class, UiPreferences.class,
                DoubleSupplier.class, LongSupplier.class);
        constructor.setAccessible(true);
        long[] now = {0L};
        var modal = constructor.newInstance(screen, UiPreferences.DEFAULT, (DoubleSupplier) () -> 2d,
                (LongSupplier) () -> now[0]);
        var labels = List.<Component>of(Component.literal("Alpha"), Component.literal("Beta"), Component.literal("Gamma"));
        var glide = new NativePageOptions.Glide(List.of(), 100, false, false, true);
        var commits = new java.util.ArrayList<Integer>();
        Runnable open = () -> {
            screen.setFocused(source);
            NativePageOptions.open(screen, modal, source, labels, () -> 0, null, glide, commits::add);
            now[0] += 1_000L;
            modal.prepareRender();
        };
        try {
            open.run();
            assertTrue(modal.mouseClicked(35, 50, 0));
            assertTrue(modal.mouseDragged(35, 90, 0));
            assertTrue(modal.mouseReleased(35, 90, 0));
            assertEquals(List.of(2), commits);
            assertSame(source, screen.getFocused());
            now[0] += 1_000L;
            modal.prepareRender();

            open.run();
            assertTrue(modal.mouseClicked(35, 50, 0));
            assertTrue(modal.mouseDragged(190, 90, 0));
            assertTrue(modal.mouseReleased(190, 90, 0));
            assertEquals(List.of(2), commits);
            assertFalse(modal.mouseClicked(190, 90, 0));
            now[0] += 1_000L;
            modal.prepareRender();
            assertFalse(modal.blocksPointer(35, 50));

            open.run();
            assertTrue(modal.charTyped('b', 0));
            assertTrue(modal.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
            assertEquals(List.of(2, 1), commits);
        } finally {
            modal.interrupt();
            screen.removed();
        }
    }

    @Test
    void optionWheelKeepsTheKeyboardChoiceInTheVisibleRows() throws ReflectiveOperationException {
        var source = new UiButton(10, 10, 40, 22, Component.empty(), button -> {});
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onInit(page -> page.add(source)).build();
        initScreen(screen);
        var constructor = UiLocalModal.class.getDeclaredConstructor(Screen.class, UiPreferences.class,
                DoubleSupplier.class, LongSupplier.class);
        constructor.setAccessible(true);
        long[] now = {0L};
        var modal = constructor.newInstance(screen, UiPreferences.DEFAULT, (DoubleSupplier) () -> 2d,
                (LongSupplier) () -> now[0]);
        int[] selected = {-1};
        var labels = IntStream.range(0, 12).mapToObj(i -> (Component) Component.literal("Row " + i)).toList();
        try {
            screen.setFocused(source);
            NativePageOptions.open(screen, modal, source, labels, () -> 0, null, null, index -> selected[0] = index);
            now[0] = 1_000L;
            modal.prepareRender();
            assertTrue(modal.mouseScrolled(50, 60, 0, -1));
            assertTrue(modal.mouseScrolled(50, 60, 0, -1));
            assertTrue(modal.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
            assertEquals(2, selected[0]);
        } finally {
            modal.interrupt();
            screen.removed();
        }
    }

    @Test
    void sliderKeepsThePointerValueWhenAuthorityChangesDuringDrag() throws ReflectiveOperationException {
        var initial = Map.<String, UiValue>of("level", new UiValue.Decimal(0.2));
        var update = Map.<String, UiValue>of("level", new UiValue.Decimal(0.4));
        var definition = UiPageDefinition.builder(new UiId("example", "slider"))
                .field("level", UiValueSchema.decimal(0, 1))
                .root("level", "slider", node -> node.label("Level").bind("value", "level")).build();
        var page = new ClientPages(null, null, null).new OpenPage(null, null, UUID.randomUUID(), definition,
                new PageDataState(definition.dataSchema(), initial), false, (action, payload) -> {}, () -> {}, List.of());
        var view = new NativePageView(page, null, UiPageTextReadability.DEFAULT, null);
        var screen = new UiScreenBuilder(Component.empty()).backButton(false).build();
        initScreen(screen);
        try {
            var control = NativePageInputs.build(view, screen, definition.root(), List.of(), 108, 22);
            var slider = (UiSlider) control.widget();
            control.update().accept(initial);
            assertEquals(20, slider.value());
            slider.onClick(24, 10);
            slider.mouseDragged(64, 10, GLFW.GLFW_MOUSE_BUTTON_LEFT, 40, 0);
            assertEquals(60, slider.value());

            control.update().accept(update);
            assertEquals(60, slider.value());
            slider.setFocused(false);
            control.update().accept(update);
            assertEquals(40, slider.value());
        } finally {
            screen.removed();
        }
    }

    @Test
    void listScrollKeepsBoundsAfterLocalFilteringAndResize() {
        var state = new TabScrollState();
        var items = IntStream.range(0, 50).mapToObj(index -> "Row " + index).toList();
        var list = new NativePageList(100, 40, 10, state);
        list.update(items, "");
        assertEquals(460, state.maxOffset());
        assertTrue(list.mouseScrolled(15, 25, -5));
        assertEquals(50, state.offset());
        list.setFocused(true);
        assertTrue(list.keyPressed(GLFW.GLFW_KEY_END, 0, 0));
        assertEquals(460, state.offset());

        var resized = new NativePageList(100, 60, 10, state);
        resized.update(items, "");
        assertEquals(440, state.offset());
        resized.update(items, "ROW 1");
        assertEquals(50, state.maxOffset());
        assertEquals(50, state.offset());
        resized.update(List.of(), "");
        assertEquals(0, state.offset());
        assertFalse(resized.mouseScrolled(15, 25, -1));
    }

    private static void initScreen(UiScreen screen) throws ReflectiveOperationException {
        screen.width = 240;
        screen.height = 120;
        var init = UiScreen.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(screen);
    }
}
