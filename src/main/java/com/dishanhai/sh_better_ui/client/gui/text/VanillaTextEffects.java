package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/** 山海：原版与可选整合入口共用的旧格式所有权判断。 */
public final class VanillaTextEffects {
    private VanillaTextEffects() {}

    public static boolean handlesLegacy() {
        return PlatformUiApi.handlesLegacyText();
    }

    public static Component normalize(Component text) {
        return UiRichText.prepare(handlesLegacy() ? UiTextEffects.normalizeLegacy(text) : text);
    }

    public static FormattedText normalize(FormattedText text) {
        return text instanceof Component component ? normalize(component) : UiRichText.prepare(text);
    }

    public static Component parse(String raw) {
        if (raw == null) return null;
        Component legacy = handlesLegacy() ? LegacyTextParser.parse(raw, Style.EMPTY, true) : null;
        if (legacy == null && !raw.contains("<rich:")) return null;
        Component source = legacy == null ? Component.literal(raw) : legacy;
        Component prepared = UiRichText.prepare(source);
        return prepared != source ? prepared : legacy;
    }
}
