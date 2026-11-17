// Author: dishanhai
package com.dishanhai.sh_better_ui.config;

/** Main control-center navigation only; independent of theme and music subpage styling. */
public enum ControlPanelLayout {
    SIDEBAR,
    CLASSIC;

    public int sidebarWidth(int panelWidth,int screenHeight) {
        return this==SIDEBAR && panelWidth>=500 && screenHeight>=300 ? 136 : 0;
    }
    public int headerOffset(boolean searchOpen) { return this==CLASSIC&&!searchOpen ? -30 : 0; }
}
