// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

/** Controlled value and cancelable pointer gesture, independent of network and rendering. */
public final class SwitchRowState {
    private boolean value, pending, pressed;
    public SwitchRowState(boolean value) { this.value = value; }
    public boolean value() { return value; }
    public boolean pending() { return pending; }
    public boolean pressed() { return pressed; }
    public void setValue(boolean value) { this.value = value; }
    public void setPending(boolean pending) { this.pending = pending; if (pending) cancel(); }
    public boolean press(boolean inside) { pressed = inside && !pending; return pressed; }
    public boolean release(boolean inside) {
        boolean activate = pressed && inside && !pending;
        pressed = false;
        return activate;
    }
    public void cancel() { pressed = false; }
}
