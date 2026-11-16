// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;

/** Three-field patch: never overwrite unrelated theme, backdrop, feedback or shortcut edits. */
public record DisplayPreferenceEdits(boolean highContrast,boolean reducedMotion,boolean blur) {
    public static DisplayPreferenceEdits from(ClientUiSettings s){return new DisplayPreferenceEdits(s.highContrast(),s.reducedMotion(),s.widgetBackdropBlur());}
    public ClientUiSettings apply(ClientUiSettings s){return s.withDisplay(highContrast,reducedMotion,blur);}
    public boolean conflicts(DisplayPreferenceEdits base,DisplayPreferenceEdits current){
        return highContrast!=base.highContrast && current.highContrast!=base.highContrast
                || reducedMotion!=base.reducedMotion && current.reducedMotion!=base.reducedMotion
                || blur!=base.blur && current.blur!=base.blur;
    }
    public DisplayPreferenceEdits merge(DisplayPreferenceEdits base,DisplayPreferenceEdits current){
        return new DisplayPreferenceEdits(highContrast==base.highContrast?current.highContrast:highContrast,
                reducedMotion==base.reducedMotion?current.reducedMotion:reducedMotion,
                blur==base.blur?current.blur:blur);
    }
}
