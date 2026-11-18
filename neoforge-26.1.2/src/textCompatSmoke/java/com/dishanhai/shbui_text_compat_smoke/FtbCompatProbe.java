// Author: dishanhai
package com.dishanhai.shbui_text_compat_smoke;

import dev.ftb.mods.ftblibrary.util.TextComponentParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.ftb.mods.ftblibrary.client.gui.theme.Theme;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Run on the client thread with the real FTB Library jar and mixins loaded, not a parser fixture. */
public final class FtbCompatProbe {
    private FtbCompatProbe() {}

    public static String run(Font font) {
        AtomicInteger substitutions = new AtomicInteger();
        var plain = TextComponentParser.parse("plain\\ntext", key -> {
            substitutions.incrementAndGet(); return Component.literal(key);
        });
        require(plain.getString().equals("plain\ntext"), "native newline handling");
        require(!hasMarker(plain), "plain text unexpectedly marked");
        require(substitutions.get() == 0, "plain text called substitutes");

        var nativeBold = TextComponentParser.parse("&lNative", null);
        require(nativeBold.getString().equals("Native"), "native formatting text");
        require(allVisibleBold(nativeBold), "native bold formatting lost");
        require(!hasMarker(nativeBold), "native formatting unexpectedly marked");

        var effects = TextComponentParser.parse("&~$rainbow-A𠀀e\u0301", null);
        require(effects.getString().equals("A𠀀e\u0301"), "FCS prefix leaked; injection not applied");
        require(hasMarker(effects), "FCS effect marker missing");
        require(font.width(effects) == font.width("A𠀀e\u0301"), "FCS width contains syntax");
        require(hasMarker(effects.getVisualOrderText()), "visual ordering dropped marker");

        var composed = TextComponentParser.parse("&~$rainbow-&lHi {name}\\nend", key -> {
            substitutions.incrementAndGet();
            require(key.equals("name"), "substitution key changed");
            return Component.literal("玩家");
        });
        require(substitutions.get() == 1, "substitutes must be delegated exactly once");
        require(composed.getString().equals("Hi 玩家\nend"), "vendor formatting/substitution/newline changed");
        require(allVisibleBold(composed), "vendor bold style lost under FCS");
        require(hasMarker(composed), "composed effect marker lost");
        var rows = font.split(composed, 200);
        require(rows.size() == 2, "native newline split count");
        for (var row : rows) require(hasMarker(row), "wrapped row lost FCS marker");
        return "PASS FTB Library actual parser: plain/native formatting/FCS width/Unicode/substitutes/wrapping";
    }

    /** Exercises the vendor Component and FCS drawing branches, including vendor font measurement. */
    public static void render(GuiGraphicsExtractor graphics, Font font, int x, int y) {
        var component = TextComponentParser.parse("&~$rainbow-FTB native A𠀀e\u0301 {name}",
                key -> Component.literal("玩家"));
        var theme = Theme.DEFAULT;
        require(theme.getStringWidth(component) == font.width(component), "vendor component font metrics");
        int end = theme.drawString(graphics, component, x, y, Color4I.WHITE, Theme.SHADOW);
        require(end == x + font.width(component), "vendor component draw advance");
        var visual = component.getVisualOrderText();
        int visualEnd = theme.drawString(graphics, visual, x, y + 16, Color4I.WHITE, Theme.SHADOW);
        require(visualEnd == x + font.width(visual), "vendor FCS draw advance");
    }

    private static boolean hasMarker(Component text) { return hasMarker(text.getVisualOrderText()); }
    private static boolean hasMarker(net.minecraft.util.FormattedCharSequence text) {
        return !text.accept((index, style, cp) -> !marker(style));
    }
    private static boolean marker(Style style) {
        return style.getInsertion() != null && style.getInsertion().startsWith("shbui:fcs:");
    }
    private static boolean allVisibleBold(Component text) {
        return text.visit((style, string) -> !string.isEmpty() && !style.isBold()
                ? Optional.of(Boolean.FALSE) : Optional.empty(), Style.EMPTY).isEmpty();
    }
    private static void require(boolean passed, String message) {
        if (!passed) throw new IllegalStateException("FTB text compatibility: " + message);
    }
}
