// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

/** Uses the control panel's existing client settings persistence. */
public final class AssistantDisplaySettings {
    private AssistantDisplaySettings() {}

    public static boolean highContrast() { return ControlPanelSettings.current().highContrast(); }
    public static boolean reducedMotion() { return ControlPanelSettings.current().reducedMotion(); }
    public static boolean widgetBlur() { return ControlPanelSettings.current().widgetBlur(); }

    public static boolean save(boolean highContrast, boolean reducedMotion, boolean widgetBlur) {
        return ControlPanelSettings.save(ControlPanelSettings.current().withDisplay(highContrast, reducedMotion, widgetBlur));
    }
}
