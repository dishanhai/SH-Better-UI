// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.config.ThemeSettings;
import java.util.List;

/** Raw field text survives resize and failed validation; no global preview mutation. */
final class NativeAppearanceDraft {
    record State(ThemeSettings.Mode mode,ThemeSettings.Base base,List<String> colors,String background) {
        State { colors=List.copyOf(colors); }
        static State of(ThemeSettings theme) { return new State(theme.mode(),theme.customBase(),
                List.of(theme.background(),theme.surface(),theme.border(),theme.text(),theme.accent()),theme.backgroundEffect()); }
    }
    private ThemeSettings template;
    private State state,saved,beforeReset;
    NativeAppearanceDraft(ThemeSettings initial,ThemeSettings saved) { template=initial;state=State.of(initial);this.saved=State.of(saved); }
    State state(){return state;}
    boolean dirty(){return !state.equals(saved);}
    boolean canUndoReset(){return beforeReset!=null;}
    void mode(ThemeSettings.Mode mode){beforeReset=null;state=new State(mode,state.base,state.colors,state.background);}
    void base(ThemeSettings.Base base){beforeReset=null;state=new State(state.mode,base,state.colors,state.background);}
    void background(String id){beforeReset=null;state=new State(state.mode,state.base,state.colors,id);}
    void color(int index,String text){beforeReset=null;var colors=new java.util.ArrayList<>(state.colors);colors.set(index,text);state=new State(state.mode,state.base,colors,state.background);}
    ThemeSettings value(){return value(template);}
    ThemeSettings value(ThemeSettings mergeBase){var c=state.colors;return mergeBase.withMode(state.mode).withPalette(state.base,c.get(0),c.get(1),c.get(2),c.get(3),c.get(4)).withBackgroundEffect(state.background);}
    void reset(){beforeReset=state;state=State.of(ControlPanelSettings.DEFAULT.theme());}
    void undoReset(){if(beforeReset!=null){state=beforeReset;beforeReset=null;}}
    void applied(ThemeSettings value){template=value;state=saved=State.of(value);beforeReset=null;}
}
