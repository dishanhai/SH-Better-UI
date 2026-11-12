package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.audio.ClientMusicPulse;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCapsuleReveal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScrollableSectionBar;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.TabScrollSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：主控面板切页时移除前一页的输入控件，并保留草稿。 */
class ControlPanelScreenTest {
    @Test
    void projectFeedbackTabShowsThreeLinksAndRetainsConfigurationDraft() {
        var screen = compactScreen();
        switchTab(screen, "feedback");
        button(screen, "hover_animation").onPress();
        var navigation = mainNavigation(screen);
        screen.setFocused(navigation);
        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0));
        assertSame(button(screen, "section.links"), navigation.focusedButton());
        assertTrue(navigation.scrollOffset() > 0);
        assertTrue(button(screen, "links.github").active);
        assertTrue(button(screen, "links.issues").active);
        assertTrue(button(screen, "links.afdian").active);
        assertNull(findButton(screen, "hover_animation"));

        switchTab(screen, "configuration");
        assertNull(findButton(screen, "links.github"));
        assertTrue(button(screen, "apply").active);
        assertNotNull(button(screen, "hover_animation"));
    }

    @Test
    void musicPulseCanBeToggledAcrossPanelInstancesAndTurnedOffWhileMotionIsReduced() {
        var pulse = ClientMusicPulse.global();
        var screen = compactScreen();
        switchTab(screen, "display");
        var music = button(screen, "background_music");
        var contrast = button(screen, "contrast");
        var motion = button(screen, "motion");
        if (contrast.isSelected()) contrast.onPress();
        if (motion.isSelected()) motion.onPress();
        assertTrue(music.active);
        assertFalse(music.isSelected());

        try {
            music.onPress();
            assertTrue(pulse.enabled());
            screen.removed();
            assertTrue(pulse.enabled());

            var reopened = compactScreen();
            switchTab(reopened, "display");
            var globalToggle = button(reopened, "background_music");
            var reducedMotion = button(reopened, "motion");
            if (!reducedMotion.isSelected()) reducedMotion.onPress();
            assertTrue(globalToggle.isSelected());
            assertTrue(globalToggle.active);
            globalToggle.onPress();
            assertFalse(pulse.enabled());
            assertFalse(globalToggle.active);
        } finally {
            pulse.close();
        }
    }

    @Test
    void primaryNavigationSeparatesSettingsDeveloperConfigurationAndOverview() {
        var screen = compactScreen();
        var navigation = mainNavigation(screen);
        assertEquals(4, navigation.buttons().size());
        assertNotNull(findButton(screen, "section.configuration"));
        switchTab(screen, "developer_config");
        assertNull(findButton(screen, "enabled"));
        assertNotNull(findButton(screen, "sampling"));
        assertNotNull(findButton(screen, "widget_blur"));
        assertNotNull(findButton(screen, "developer.title"));
        switchTab(screen, "overview");
        assertNull(findButton(screen, "sampling"));
        assertNull(findButton(screen, "widget_blur"));
        switchTab(screen, "configuration");
        assertNotNull(findButton(screen, "enabled"));
    }

    @Test
    void hiddenParentInitializationDoesNotConsumeThePendingReturnFocus() throws ReflectiveOperationException {
        var mapping = new KeyMapping("key.sh_better_ui.control_panel.test", InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_4, "key.categories.sh_better_ui");
        var screen = new ControlPanelScreen(null, mapping, settings -> true);
        screen.width = 420;
        screen.height = 240;
        screen.prepareReturnLayer();
        screen.init();
        var pendingFocus = ControlPanelScreen.class.getDeclaredField("restoreDeveloperFocus");
        pendingFocus.setAccessible(true);
        assertTrue(pendingFocus.getBoolean(screen), "hidden pre-render layout must retain focus for activation");
    }

    @Test
    void returningToAnInitializedParentKeepsItsPanelExpanded() throws ReflectiveOperationException {
        var screen = compactScreen();
        switchTab(screen, "developer_config");
        var field = ControlPanelScreen.class.getDeclaredField("entrance");
        field.setAccessible(true);
        var entrance = (UiCapsuleReveal) field.get(screen);
        assertFalse(entrance.active());

        screen.prepareReturnLayer();

        assertFalse(entrance.active());
        assertSame(button(screen, "developer.title"), screen.getFocused());
    }

    @Test
    void boundMouseShortcutReopensTheSamePanelDuringClosing() throws ReflectiveOperationException {
        var screen = compactScreen();
        switchTab(screen, "developer_config");
        var developer = button(screen, "developer.title");
        var field = ControlPanelScreen.class.getDeclaredField("entrance");
        field.setAccessible(true);
        var entrance = (UiCapsuleReveal) field.get(screen);

        screen.onClose();
        assertTrue(entrance.closing());
        assertTrue(screen.mouseClicked(0, 0, GLFW.GLFW_MOUSE_BUTTON_LEFT));
        assertTrue(entrance.closing());
        assertTrue(screen.mouseClicked(0, 0, GLFW.GLFW_MOUSE_BUTTON_4));
        assertFalse(entrance.closing());
        assertSame(developer, button(screen, "developer.title"));
    }

    @Test
    void escapeInterruptsOpeningAndOnlyTheBoundKeyReopens() throws ReflectiveOperationException {
        var binding = new KeyMapping("key.sh_better_ui.control_panel.test", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8, "key.categories.sh_better_ui");
        var screen = new ControlPanelScreen(null, binding, settings -> true);
        screen.width = 320;
        screen.height = 210;
        screen.init();
        var field = ControlPanelScreen.class.getDeclaredField("entrance");
        field.setAccessible(true);
        var entrance = (UiCapsuleReveal) field.get(screen);

        assertTrue(entrance.active());
        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertTrue(entrance.closing());
        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_F12, 0, 0));
        assertTrue(entrance.closing());
        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_F8, 0, 0));
        assertFalse(entrance.closing());
    }

    @Test
    void rapidTabChangesAndRowFocusKeepTheCurrentCapsuleProgress() throws ReflectiveOperationException {
        var screen = compactScreen();
        switchTab(screen, "display");
        var field = ControlPanelScreen.class.getDeclaredField("pageMorph");
        field.setAccessible(true);
        var morph = (InterruptibleAnimation) field.get(screen);
        float progress = morph.sample(PageTransition.nowMs() + 100L);
        assertTrue(progress > 0f && progress < 1f);

        switchTab(screen, "feedback");
        switchTab(screen, "display");
        screen.setFocused(button(screen, "contrast"));

        assertTrue(morph.value() >= progress && morph.value() < 1f);
        assertTrue(morph.running());
    }

    @Test
    void changingTabsRemovesInactiveControlsAndKeepsTheirDraftValues() {
        var mapping = new KeyMapping("key.sh_better_ui.control_panel.test", InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_4, "key.categories.sh_better_ui");
        var screen = new ControlPanelScreen(null, mapping, settings -> true);
        screen.width = 420;
        screen.height = 240;
        screen.init();

        var backdrop = button(screen, "enabled");
        boolean initialBlur = ClientUiSettings.current().backdrop().enabled();
        boolean initialDense = ClientUiSettings.current().backdrop().denseSampling();
        assertEquals(8, visibleSliders(screen));
        backdrop.onPress();
        assertEquals(!initialBlur, backdrop.isSelected());
        switchTab(screen, "developer_config");
        assertFalse(screen.children().contains(backdrop));
        var sampling = button(screen, "sampling");
        assertEquals(2, sampling.getModeIndicatorCount());
        assertEquals(initialDense ? 1 : 0, sampling.getSelectedModeIndex());
        sampling.onPress();
        assertEquals(initialDense ? 0 : 1, sampling.getSelectedModeIndex());
        switchTab(screen, "configuration");
        switchTab(screen, "display");
        assertFalse(screen.children().contains(backdrop));
        assertFalse(screen.children().contains(sampling));
        assertEquals(ClientUiSettings.current().theme().mode() == ThemeSettings.Mode.HUD_GLASS ? 1 : 0,
                visibleSliders(screen));
        assertNull(findButton(screen, "shortcut.binding"));
        var contrast = button(screen, "contrast");
        boolean initialContrast = ClientUiSettings.current().highContrast();
        contrast.onPress();
        assertEquals(!initialContrast, contrast.isSelected());

        switchTab(screen, "shortcut");
        assertNotNull(findButton(screen, "shortcut.binding"));
        assertFalse(screen.children().contains(contrast));
        assertEquals(0, visibleSliders(screen));

        switchTab(screen, "glass");
        assertEquals(8, visibleSliders(screen));
        assertEquals(!initialBlur, button(screen, "enabled").isSelected());
        assertTrue(button(screen, "apply").active);
        switchTab(screen, "developer_config");
        assertEquals(initialDense ? 0 : 1, button(screen, "sampling").getSelectedModeIndex());
        switchTab(screen, "configuration");
        switchTab(screen, "display");
        assertEquals(!initialContrast, button(screen, "contrast").isSelected());
    }

    private static long visibleSliders(ControlPanelScreen screen) {
        return screen.children().stream().filter(UiSlider.class::isInstance).map(UiSlider.class::cast)
                .filter(slider -> slider.visible).count();
    }

    private static void switchTab(ControlPanelScreen screen, String section) {
        var tab = button(screen, "section." + section);
        tab.onPress();
        screen.keyPressed(GLFW.GLFW_KEY_F12, 0, 0);
        assertInstanceOf(UiScrollableSectionBar.class, screen.getFocused());
        assertTrue(tab.isSelected());
    }

    private static UiButton button(ControlPanelScreen screen, String key) {
        return assertInstanceOf(UiButton.class, findButton(screen, key));
    }

    private static UiScrollableSectionBar mainNavigation(ControlPanelScreen screen) {
        var configuration = button(screen, "section.configuration");
        return screen.children().stream().filter(UiScrollableSectionBar.class::isInstance)
                .map(UiScrollableSectionBar.class::cast).filter(value -> value.buttons().contains(configuration))
                .findFirst().orElseThrow();
    }

    private static UiButton findButton(ControlPanelScreen screen, String key) {
        return screen.children().stream().filter(child -> !(child instanceof AbstractWidget widget) || widget.visible)
                .flatMap(child -> child instanceof UiScrollableSectionBar bar
                        ? bar.buttons().stream() : child instanceof UiButton button ? Stream.of(button) : Stream.empty())
                .filter(button -> button.getMessage().getContents() instanceof TranslatableContents text
                        && text.getKey().equals("sh_better_ui.panel." + key))
                .findFirst().orElse(null);
    }

    @Test
    void feedbackAndTabDraftsSurviveDisplayEditsResetUndoAndApply() {
        var saved = new AtomicReference<ClientUiSettings>();
        var previous = ClientUiSettings.current();
        try {
            ClientUiSettings.update(ClientUiSettings.DEFAULT);
            var mapping = new KeyMapping("key.sh_better_ui.control_panel.test", InputConstants.Type.MOUSE,
                    GLFW.GLFW_MOUSE_BUTTON_4, "key.categories.sh_better_ui");
            var screen = new ControlPanelScreen(null, mapping, settings -> { saved.set(settings); return true; });
            screen.width = 320;
            screen.height = 210;
            screen.init();
            switchTab(screen, "feedback");
            assertEquals(1, visibleSliders(screen));
            button(screen, "hover_animation").onPress();
            button(screen, "press_animation").onPress();
            switchTab(screen, "tabs");
            assertEquals(1, visibleSliders(screen));
            button(screen, "tab_wheel").onPress();
            button(screen, "tab_drag").onPress();
            assertEquals(ClientUiSettings.DEFAULT, ClientUiSettings.current());
            switchTab(screen, "display");
            button(screen, "contrast").onPress();
            button(screen, "reset").onPress();
            button(screen, "reset.undo").onPress();
            switchTab(screen, "feedback");
            assertFalse(button(screen, "hover_animation").isSelected());
            assertFalse(button(screen, "press_animation").isSelected());
            switchTab(screen, "tabs");
            assertFalse(button(screen, "tab_wheel").isSelected());
            assertFalse(button(screen, "tab_drag").isSelected());
            button(screen, "apply").onPress();
            assertFalse(saved.get().feedback().hoverAnimation());
            assertFalse(saved.get().tabs().wheelEnabled());
            assertTrue(saved.get().highContrast());
            assertFalse(button(screen, "apply").active);
        } finally {
            ClientUiSettings.update(previous);
        }
    }

    @Test
    void nativeMouseReleaseSwitchesThePageAndPreservesBarFocus() {
        var mapping = new KeyMapping("key.sh_better_ui.control_panel.test", InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_4, "key.categories.sh_better_ui");
        var screen = new ControlPanelScreen(null, mapping, settings -> true);
        screen.width = 320;
        screen.height = 210;
        screen.init();
        finishEntrance(screen);
        var bar = bar(screen);
        bar.reveal(2);
        var tab = bar.buttons().get(2);
        int x = tab.getX() + tab.getWidth() / 2, y = tab.getY() + tab.getHeight() / 2;
        assertTrue(screen.mouseClicked(x, y, 0));
        assertTrue(screen.mouseReleased(x, y, 0));
        assertSame(bar, screen.getFocused());
        assertNotNull(findButton(screen, "hover_animation"));
    }

    @Test
    void releasingOutsideTheBarFinishesTheCapturedDrag() {
        var screen = compactScreen();
        var bar = bar(screen);
        int x = bar.getX() + 200, y = bar.getY() + 10;
        assertTrue(screen.mouseClicked(x, y, 0));
        assertTrue(screen.mouseDragged(x - 60, y + 60, 0, -60, 60));
        assertTrue(screen.mouseReleased(x - 60, y + 60, 0));
        assertFalse(screen.isDragging());
        assertTrue(screen.mouseScrolled(bar.getX() + 5, y, -1));
        assertEquals(0, bar.selectedIndex());
    }

    @Test
    void mouseSelectionRespectsDisabledAutoReveal() {
        var previous = ClientUiSettings.current();
        try {
            ClientUiSettings.update(ClientUiSettings.DEFAULT.withTabs(new TabScrollSettings(true, true, false, 32)));
            var screen = compactScreen();
            var bar = bar(screen);
            var partial = bar.buttons().get(2);
            int x = partial.getX() + 5, y = partial.getY() + 10;
            assertTrue(x < bar.getX() + bar.getWidth());
            screen.mouseClicked(x, y, 0);
            screen.mouseReleased(x, y, 0);
            assertEquals(2, bar.selectedIndex());
            assertEquals(0, bar.scrollOffset());
        } finally {
            ClientUiSettings.update(previous);
        }
    }

    @Test
    void escapeCancelsTabDraggingWithoutClosingTheScreen() {
        var screen = compactScreen();
        var bar = bar(screen);
        bar.scrollTo(32);
        int x = bar.getX() + 200, y = bar.getY() + 10;
        screen.mouseClicked(x, y, 0);
        screen.mouseDragged(x - 60, y, 0, -60, 0);
        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertEquals(32, bar.scrollOffset());
        assertFalse(screen.isDragging());
    }

    private static ControlPanelScreen compactScreen() {
        var mapping = new KeyMapping("key.sh_better_ui.control_panel.test", InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_4, "key.categories.sh_better_ui");
        var screen = new ControlPanelScreen(null, mapping, settings -> true);
        screen.width = 320;
        screen.height = 210;
        screen.init();
        finishEntrance(screen);
        return screen;
    }

    private static void finishEntrance(ControlPanelScreen screen) {
        try {
            var field = ControlPanelScreen.class.getDeclaredField("entrance");
            field.setAccessible(true);
            ((UiCapsuleReveal) field.get(screen)).finish();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static UiScrollableSectionBar bar(ControlPanelScreen screen) {
        return screen.children().stream().filter(UiScrollableSectionBar.class::isInstance)
                .map(UiScrollableSectionBar.class::cast)
                .filter(value -> value.buttons().contains(button(screen, "section.glass"))).findFirst().orElseThrow();
    }

    @Test
    void primaryTabsPreserveTheConfigurationCategoryAndDeveloperDraftThroughResize() throws ReflectiveOperationException {
        var saved = new AtomicReference<ClientUiSettings>();
        var mapping = new KeyMapping("key.sh_better_ui.control_panel.test", InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_4, "key.categories.sh_better_ui");
        var screen = new ControlPanelScreen(null, mapping, settings -> { saved.set(settings); return true; });
        screen.width = 420;
        screen.height = 240;
        screen.init();
        switchTab(screen, "feedback");
        button(screen, "hover_animation").onPress();
        switchTab(screen, "developer_config");
        button(screen, "sampling").onPress();
        boolean sampled = button(screen, "sampling").getSelectedModeIndex() == 1;
        screen.width = 320;
        var clear = Screen.class.getDeclaredMethod("clearWidgets");
        clear.setAccessible(true);
        clear.invoke(screen);
        screen.init();
        assertEquals(sampled ? 1 : 0, button(screen, "sampling").getSelectedModeIndex());
        switchTab(screen, "overview");
        switchTab(screen, "configuration");
        assertNotNull(findButton(screen, "hover_animation"));
        assertEquals(!ClientUiSettings.current().feedback().hoverAnimation(), button(screen, "hover_animation").isSelected());
        button(screen, "apply").onPress();
        assertEquals(sampled, saved.get().backdrop().denseSampling());
    }

    @Test
    @SuppressWarnings("unchecked")
    void focusingAnOversizedInformationGroupKeepsItsVisibleStartAndDoesNotOscillate() throws ReflectiveOperationException {
        var screen = compactScreen();
        int bodyTop = button(screen, "enabled").getY() - 16;
        var group = new UiButton(30, bodyTop + 4, 100, 200, Component.empty(), ignored -> {});
        var rowsField = ControlPanelScreen.class.getDeclaredField("rows");
        rowsField.setAccessible(true);
        var rows = (Map<AbstractWidget, Integer>) rowsField.get(screen);
        rows.put(group, 4);
        var contentHeight = ControlPanelScreen.class.getDeclaredField("contentHeight");
        contentHeight.setAccessible(true);
        contentHeight.setInt(screen, 400);

        screen.setFocused(group);
        assertEquals(bodyTop + 4, group.getY(), "the group heading must stay in view when focusing a tall group");
        assertTrue(screen.mouseScrolled(40, bodyTop + 8, -1));
        int scrolledY = group.getY();
        assertTrue(scrolledY < bodyTop);
        screen.keyPressed(GLFW.GLFW_KEY_F12, 0, 0);
        assertEquals(scrolledY, group.getY(), "unrelated keys must not jump between the top and bottom of a tall group");
    }

    @Test
    void returningFromPrimaryTabsRestoresTheConfigurationReadingPosition() {
        var screen = compactScreen();
        int initialY = button(screen, "enabled").getY();
        assertTrue(screen.mouseScrolled(40, initialY + 4, -1));
        int scrolledY = button(screen, "enabled").getY();
        assertTrue(scrolledY < initialY);
        switchTab(screen, "developer_config");
        switchTab(screen, "overview");
        switchTab(screen, "configuration");
        assertEquals(scrolledY, button(screen, "enabled").getY());
    }
}
