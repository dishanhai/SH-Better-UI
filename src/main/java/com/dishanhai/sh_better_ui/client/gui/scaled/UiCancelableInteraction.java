// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Hosts offer Escape to a focused in-progress interaction before closing the page. */
public interface UiCancelableInteraction {
    /** Cancel the active interaction, returning false when Escape should keep its normal meaning. */
    boolean cancelInteraction();
}
