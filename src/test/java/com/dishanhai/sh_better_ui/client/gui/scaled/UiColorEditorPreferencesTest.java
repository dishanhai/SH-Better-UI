package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.config.ControlFeedbackSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiColorEditorPreferencesTest {
    private static UiPreferences preferences(boolean contrast, int delay) {
        return new UiPreferences(contrast, true, false, new ControlFeedbackSettings(false, false, false, delay));
    }
    private static UiColorEditor editor(UiPreferences preferences) {
        return new UiColorEditor(new Bounds(10, 20, 180, 132), Component.literal("Color"), 0x80808080, 0xff00ff00,
                preferences, c -> {}, c -> {});
    }
    @Test void highContrastIsReadableOnLightAndDarkChromeIncludingDisabledAndPressedReset() {
        for (var mode : new ThemeSettings.Mode[]{ThemeSettings.Mode.DARK, ThemeSettings.Mode.LIGHT}) {
            UiTheme.preview(UiTheme.resolve(ThemeSettings.DEFAULT.withMode(mode)), () -> {
                var widget = editor(preferences(true, 0)); widget.setFocused(true);
                var paint = widget.paint();
                assertEquals(255, paint.background() >>> 24);
                assertEquals(21, ContrastPolicy.ratio(paint.text(), paint.background(), paint.background()), .001);
                assertEquals(2, paint.stroke());
                assertTrue(ContrastPolicy.ratio(paint.border(), paint.background(), paint.background()) >= 7);
                assertTrue(widget.mouseClicked(100, 137, 0));
                paint = widget.paint();
                assertEquals(21, ContrastPolicy.ratio(paint.resetText(), paint.resetFill(), paint.background()), .001);
                widget.cancelInteraction(); widget.active = false;
                paint = widget.paint();
                assertEquals(21, ContrastPolicy.ratio(paint.text(), paint.background(), paint.background()), .001);
            });
        }
    }
    @Test void changingPreferencesDoesNotChangeEditedColorOrTransaction() {
        var widget = editor(preferences(false, 0));
        widget.state().preview(0, .7f);
        int color = widget.state().color(), ramp = widget.state().rampColor(0, .5f);
        widget.setPreferences(preferences(true, 800));
        assertEquals(color, widget.state().color()); assertEquals(ramp, widget.state().rampColor(0, .5f));
        assertTrue(widget.hasPendingChange());
        widget.setPreferences(preferences(false, 0)); assertEquals(1, widget.paint().stroke());
    }
    @Test void tooltipDelayReachesNativeWidgetAtConstructionAndOnPreferenceUpdate() throws Exception {
        var widget = editor(preferences(false, 350));
        assertEquals(350, nativeDelay(widget, 0));
        widget.setPreferences(preferences(true, 1250));
        assertEquals(1250, nativeDelay(widget, 0));
        widget.setPreferences(preferences(false, 0));
        assertEquals(0, nativeDelay(widget, 0));
    }
    // Forge stores milliseconds directly; NeoForge stores a Duration in WidgetTooltipHolder.
    private static long nativeDelay(Object object, int depth) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                if (!name.contains("delay") && !(depth == 0 && name.contains("tooltip"))) continue;
                field.setAccessible(true); Object value = field.get(object);
                if (name.contains("delay")) {
                    if (value instanceof Number number) return number.longValue();
                    if (value instanceof java.time.Duration duration) return duration.toMillis();
                }
                if (depth == 0 && value != null && value.getClass().getSimpleName().contains("TooltipHolder"))
                    return nativeDelay(value, 1);
            }
        }
        throw new AssertionError("Native tooltip delay storage was not found");
    }
}
