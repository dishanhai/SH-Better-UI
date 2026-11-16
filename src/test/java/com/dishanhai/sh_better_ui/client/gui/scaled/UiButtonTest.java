package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：公开按钮的提示、选择与原生回调契约。 */
class UiButtonTest {
    @Test
    void textIconsOnlyReserveRoomWhenTheyAndTheLabelCanFit() {
        var button = new UiButton(0, 0, 100, 20, Component.translatable("gui.yes"), ignored -> {});
        var tooltip = button.getTooltip();
        button.setLeadingIcon(GraphicIcon.SUCCESS);
        assertEquals(68, button.labelWidth(100, 20));
        assertEquals(43, button.labelWidth(55, 20));
        assertEquals(88, button.labelWidth(100, 17));
        assertSame(tooltip, button.getTooltip());
        button.setTrailingIcon(GraphicIcon.SUCCESS);
        assertEquals(68, button.labelWidth(100, 20));
        assertEquals(43, button.labelWidth(55, 20));
        assertEquals(88, button.labelWidth(100, 17));
        assertSame(tooltip, button.getTooltip());
        button.setIcon(GraphicIcon.SUCCESS);
        assertEquals(88, button.labelWidth(100, 20));
    }

    @Test
    void defaultPreviewFollowsTheLocalizedLabel() {
        var button = new UiButton(0, 0, 100, 20, Component.translatable("gui.yes"), ignored -> {});
        var initial = button.getTooltip();
        assertNotNull(initial);
        button.setMessage(Component.translatable("gui.no"));
        assertNotNull(button.getTooltip());
        assertNotSame(initial, button.getTooltip());
    }

    @Test
    void explicitPreviewSurvivesLabelChangesAndCanReturnToDefault() {
        var button = new UiButton(0, 0, 100, 20, Component.translatable("gui.yes"), ignored -> {});
        button.setPreview(Component.translatable("sh_better_ui.jei.choose.preview"));
        var explicit = button.getTooltip();
        button.setMessage(Component.translatable("gui.no"));
        assertSame(explicit, button.getTooltip());
        button.setPreview(null);
        assertNotSame(explicit, button.getTooltip());
    }

    @Test
    void onlyCallerConfirmedStateChangesSelectionAndPressCallsActionOnce() {
        var calls = new AtomicInteger();
        var button = new UiButton(0, 0, 100, 20, Component.translatable("gui.yes"), ignored -> calls.incrementAndGet());
        button.onPress();
        assertEquals(1, calls.get());
        assertFalse(button.isSelected());
        button.setSelected(true);
        button.setSelected(true);
        assertTrue(button.isSelected());
        assertEquals(1, calls.get());
        button.setSelected(false);
        assertFalse(button.isSelected());
    }

    @Test
    void liquidGlassKeepsTheOriginalActionAndSelectionContract() {
        var calls = new AtomicInteger();
        var button = new UiButton(0, 0, 100, 20, Component.translatable("gui.yes"),
                ignored -> calls.incrementAndGet(), UiPreferences.DEFAULT, true, UiStyle.LIQUID_GLASS);
        button.onPress();
        assertEquals(1, calls.get());
        assertFalse(button.isSelected());
        assertNotNull(button.getTooltip());
    }

    @Test
    void modeIndicatorsUseTheConfirmedIndexIndependentlyOfButtonSelection() {
        var button = new UiButton(0, 0, 100, 22, Component.translatable("gui.yes"), ignored -> {});
        assertEquals(0, button.getModeIndicatorCount());
        assertEquals(-1, button.getSelectedModeIndex());

        button.setModeIndicators(3, 1);
        button.setSelected(true);
        button.onPress();
        assertEquals(3, button.getModeIndicatorCount());
        assertEquals(1, button.getSelectedModeIndex());
        assertThrows(IllegalArgumentException.class, () -> button.setModeIndicators(3, 3));
        assertEquals(1, button.getSelectedModeIndex());

        button.clearModeIndicators();
        assertEquals(0, button.getModeIndicatorCount());
        assertEquals(-1, button.getSelectedModeIndex());
        assertTrue(button.isSelected());
    }

    @Test
    void modeIndicatorsRemainDistinctAndCenteredAtNarrowWidths() {
        var button = new UiButton(10, 20, 20, 22, Component.translatable("gui.yes"), ignored -> {});
        button.setModeIndicators(3, 0);
        var narrow = button.modeIndicatorLayout();
        assertNotNull(narrow);
        assertTrue(narrow.barWidth() >= 1);
        assertTrue(narrow.step() > narrow.barWidth());
        assertEquals(40, narrow.firstX() * 2 + narrow.step() * 2 + narrow.barWidth(), 1);
        assertEquals(40, narrow.bottomY());

        button.setWidth(100);
        var wide = button.modeIndicatorLayout();
        assertNotNull(wide);
        assertTrue(wide.barWidth() > narrow.barWidth());
        assertEquals(120, wide.firstX() * 2 + wide.step() * 2 + wide.barWidth());
    }

    @Test
    void blurRadiusSupportsDisablingAndBoundsItsSamplingCost() {
        var button = new UiButton(0, 0, 24, 24, Component.translatable("gui.yes"),
                ignored -> {}, UiPreferences.DEFAULT, false, UiStyle.LIQUID_GLASS);
        assertEquals(12f, button.getBackgroundBlurRadius());
        button.setBackgroundBlurRadius(0f);
        assertEquals(0f, button.getBackgroundBlurRadius());
        button.setBackgroundBlurRadius(8.5f);
        assertEquals(8.5f, button.getBackgroundBlurRadius());
        button.setBackgroundBlurRadius(100f);
        assertEquals(32f, button.getBackgroundBlurRadius());
        for (float invalid : new float[]{-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> button.setBackgroundBlurRadius(invalid));
            assertEquals(32f, button.getBackgroundBlurRadius());
        }
    }
}
