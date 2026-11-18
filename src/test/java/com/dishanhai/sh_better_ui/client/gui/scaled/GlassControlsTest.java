// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GlassControlsTest {
    private static final Bounds AREA = new Bounds(0, 0, 120, 24);

    @Test void confirmedToggleStateIsNotOptimisticAndDisabledStateStaysNative() {
        var requests = new ArrayList<Boolean>();
        try (var kit = new GlassControls(UiPreferences.DEFAULT)) {
            var toggle = kit.toggle(AREA, false, value -> Component.literal(value ? "On" : "Off"), requests::add);
            toggle.button().onPress();
            toggle.button().onPress();
            assertEquals(List.of(true, true), requests);
            assertFalse(toggle.value());
            assertEquals(0, toggle.button().getSelectedModeIndex());
            toggle.setValue(true);
            assertEquals(1, toggle.button().getSelectedModeIndex());
            toggle.button().active = false;
            toggle.button().onPress();
            assertEquals(2, requests.size());
        }
    }

    @Test void preferenceUpdatePreservesWidgetsAndApplicationState() {
        try (var kit = new GlassControls(UiPreferences.DEFAULT)) {
            var button = kit.button(AREA, Component.literal("Play"), true, ignored -> {});
            var slider = kit.slider(AREA, 0, 100, 5, 35, value -> Component.literal("Volume " + value), ignored -> {});
            var ordinary = new UiSlider(AREA, 0, 100, 5, 35, UiPreferences.DEFAULT,
                    value -> Component.literal("Ordinary"), ignored -> {});
            assertTrue(slider.isLiquidGlass());
            assertFalse(ordinary.isLiquidGlass());
            button.setSelected(true);
            kit.setPreferences(new UiPreferences(true, true, false, UiPreferences.DEFAULT.feedback()));
            assertTrue(button.isSelected());
            assertEquals(35, slider.value());
            assertTrue(button.visible);
            assertTrue(slider.active);
        }
    }

    @Test void closeDisablesOldLayoutAndRejectsReuse() {
        var kit = new GlassControls(UiPreferences.DEFAULT);
        var button = kit.button(AREA, Component.literal("Action"), false, ignored -> {});
        var slider = kit.slider(AREA, 0, 10, 3, 10, value -> Component.literal("Value"), ignored -> {});
        kit.close();
        kit.close();
        assertTrue(kit.isClosed());
        assertFalse(button.visible);
        assertFalse(button.active);
        assertFalse(slider.visible);
        assertFalse(slider.active);
        assertThrows(IllegalStateException.class, () -> kit.setPreferences(UiPreferences.DEFAULT));
        assertThrows(IllegalStateException.class, () -> kit.button(AREA, Component.empty(), false, ignored -> {}));
    }

    @Test void sliderStillQuantizesAndProgrammaticChangesRemainSilent() {
        var changes = new ArrayList<Integer>();
        try (var kit = new GlassControls(UiPreferences.DEFAULT)) {
            var slider = kit.slider(AREA, 0, 10, 3, 4, value -> Component.literal("" + value), changes::add);
            assertEquals(3, slider.value());
            slider.setValue(10);
            assertEquals(10, slider.value());
            assertTrue(changes.isEmpty());
        }
    }
    @Test void segmentedOptInSelectionAndLifetimeAreOwnedByTheSuite() {
        var changes = new ArrayList<Integer>();
        var options = List.of(new UiSectionBar.Section(Component.literal("One"),
                        com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon.DOCUMENT),
                new UiSectionBar.Section(Component.literal("Two"),
                        com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon.ORGANIZE));
        var kit = new GlassControls(UiPreferences.DEFAULT);
        var segment = kit.segmented(AREA, Component.literal("Period"), options, 0, changes::add);
        var ordinary = new UiRubberSegment(AREA, Component.literal("Period"), options, 0,
                UiPreferences.DEFAULT, changes::add);
        assertTrue(segment.isLiquidGlass());
        assertFalse(ordinary.isLiquidGlass());
        segment.select(1);
        kit.setPreferences(new UiPreferences(true, true, false, UiPreferences.DEFAULT.feedback()));
        assertEquals(1, segment.selectedIndex());
        assertTrue(changes.isEmpty());
        kit.close();
        assertFalse(segment.visible);
        assertFalse(segment.active);
        assertFalse(segment.cancelDrag());
        assertThrows(IllegalStateException.class,
                () -> kit.segmented(AREA, Component.literal("Closed"), options, 0, changes::add));
    }
    @Test void mediaCardOptInAndConfirmedStateSurvivePreferencesButNotClosedLayout() {
        var area = new Bounds(0, 0, 180, 64);
        var kit = new GlassControls(UiPreferences.DEFAULT);
        var card = kit.mediaCard(area, Component.literal("Title"), Component.literal("Artist"), () -> {});
        var ordinary = new UiMediaCard(area, Component.empty(), Component.empty(), UiPreferences.DEFAULT, () -> {});
        assertTrue(card.isLiquidGlass()); assertFalse(ordinary.isLiquidGlass());
        card.setPlaying(true); card.setSelected(true);
        kit.setPreferences(new UiPreferences(true, true, false, UiPreferences.DEFAULT.feedback()));
        assertTrue(card.isPlaying()); assertTrue(card.isSelected());
        kit.close(); kit.close();
        assertFalse(card.active); assertFalse(card.visible);
        assertThrows(IllegalStateException.class, () -> kit.mediaCard(area, Component.empty(), Component.empty(), () -> {}));
    }
}
