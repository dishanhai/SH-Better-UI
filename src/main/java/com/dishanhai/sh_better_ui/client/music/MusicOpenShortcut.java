// Author: dishanhai
package com.dishanhai.sh_better_ui.client.music;

/** Coalesces explicit key presses into one deferred navigation, never an action on the music session. */
public final class MusicOpenShortcut {
    private boolean requested;
    private boolean candidateMouse;

    /** Snapshot the origin before vanilla or another listener can close its screen. */
    public void prepareMouse(boolean pressed, boolean matchesBinding, boolean allowed) {
        candidateMouse = pressed && matchesBinding && allowed;
    }

    /** All supported loaders skip post when a mouse pre event was cancelled. */
    public void finishMouse(boolean allowed) {
        if (candidateMouse && allowed) requested = true;
        candidateMouse = false;
    }


    public void offer(boolean pressed, boolean matchesBinding, boolean allowed) {
        if (pressed && matchesBinding && allowed) requested = true;
    }

    public boolean take(boolean allowed) {
        boolean open = requested && allowed;
        requested = false;
        candidateMouse = false;
        return open;
    }
}
