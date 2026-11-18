// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.config.ThemeSettings;
import java.util.List;

/** Raw field text survives resize and failed validation; no global preview mutation. */
final class NativeAppearanceDraft {
    record State(ThemeSettings.Mode mode,ThemeSettings.Base base,List<String> colors,String background,String themeId,com.dishanhai.sh_better_ui.config.ThemePaletteArchive archive) {
        State { colors=List.copyOf(colors); }
        static State of(ThemeSettings theme) { return new State(theme.mode(),theme.customBase(),
                List.of(theme.background(),theme.surface(),theme.border(),theme.text(),theme.accent()),theme.backgroundEffect(),theme.registeredThemeId(),theme.paletteArchive()); }
    }
    private ThemeSettings template;
    private State state,saved,beforeReset;
    NativeAppearanceDraft(ThemeSettings initial,ThemeSettings saved) { template=initial;state=State.of(initial);this.saved=State.of(saved); }
    State state(){return state;}
    boolean dirty(){return !state.equals(saved);}
    boolean canUndoReset(){return beforeReset!=null;}
    void mode(ThemeSettings.Mode mode){beforeReset=null;state=new State(mode,state.base,state.colors,state.background,state.themeId,state.archive);}
    void base(ThemeSettings.Base base){beforeReset=null;state=new State(state.mode,base,state.colors,state.background,state.themeId,state.archive);}
    void background(String id){beforeReset=null;state=new State(state.mode,state.base,state.colors,id,state.themeId,state.archive);}
    void color(int index,String text){beforeReset=null;var colors=new java.util.ArrayList<>(state.colors);colors.set(index,text);state=new State(state.mode,state.base,colors,state.background,state.themeId,state.archive);}
    void suite(String id){var next=State.of(value().withRegisteredTheme(id));beforeReset=null;state=next;}
    ThemeSettings value(){return value(template);}
    ThemeSettings value(ThemeSettings mergeBase){var c=state.colors;return new ThemeSettings(state.mode,state.base,c.get(0),c.get(1),c.get(2),c.get(3),c.get(4),mergeBase.effects(),state.background,mergeBase.hudBackgroundEffect(),mergeBase.hudBlurRadius(),state.themeId,state.archive);}
    void reset(){
        beforeReset=state;
        var defaults=ControlPanelSettings.DEFAULT.theme();
        // Keep inactive suites' saved palettes. The current suite's cleared fields remain
        // authoritative and are archived (or removed if empty) on the next suite switch.
        state=new State(defaults.mode(),defaults.customBase(),List.of("","","","",""),
                defaults.backgroundEffect(),state.themeId,state.archive);
    }
    void undoReset(){if(beforeReset!=null){state=beforeReset;beforeReset=null;}}
    void applied(ThemeSettings value){template=value;state=saved=State.of(value);beforeReset=null;}
}
