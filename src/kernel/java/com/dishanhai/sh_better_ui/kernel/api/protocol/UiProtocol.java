// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.protocol;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;

/** The experimental page transport contract. Schema versions are negotiated independently. */
public final class UiProtocol {
    public static final String CHANNEL = "sh_better_ui:pages";
    public static final UiVersion VERSION = new UiVersion(1, 0, 0);
    public static final UiId CONFIRMED_ACTION = new UiId("sh_better_ui", "confirmed_action");
    public static final UiId INTERACTION_OPEN = new UiId("sh_better_ui", "interaction_open");
    public static final UiId PRIORITY_MODAL = new UiId("sh_better_ui", "priority_modal");
    public static final int MAX_FRAME_BYTES = 24 * 1024;
    public static final int MAX_MESSAGE_BYTES = 256 * 1024;

    private UiProtocol() {}
}
