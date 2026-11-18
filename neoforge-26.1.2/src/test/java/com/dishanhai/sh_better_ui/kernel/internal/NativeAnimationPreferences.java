// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings;
import java.lang.reflect.Field;

/** Isolates the player's static native options between headless tests without writing any settings file. */
final class NativeAnimationPreferences implements AutoCloseable {
    private final Field state, file;
    private final Object previousState, previousFile;
    NativeAnimationPreferences() throws Exception {
        state=ControlPanelSettings.class.getDeclaredField("settings");state.setAccessible(true);
        file=ControlPanelSettings.class.getDeclaredField("file");file.setAccessible(true);
        previousState=state.get(null);previousFile=file.get(null);
        state.set(null,ControlPanelSettings.DEFAULT);file.set(null,null);
    }
    @Override public void close() throws Exception {state.set(null,previousState);file.set(null,previousFile);}
}
