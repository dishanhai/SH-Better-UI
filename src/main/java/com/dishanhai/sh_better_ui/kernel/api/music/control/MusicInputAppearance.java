// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField;
import net.minecraft.network.chat.Style;
import java.util.Objects;

/** Music search/account input skin; retains UUK editing, selection, clipboard and IME adapters. */
public final class MusicInputAppearance {
    private MusicInputAppearance() {}
    public static void apply(UiTextField field) {
        Objects.requireNonNull(field).setAppearance(UiTextField.Appearance.QUIET);
        field.setTextStyle(Style.EMPTY.withFont(MusicTypography.font()), MusicTypography.LINE_HEIGHT);
    }
}
