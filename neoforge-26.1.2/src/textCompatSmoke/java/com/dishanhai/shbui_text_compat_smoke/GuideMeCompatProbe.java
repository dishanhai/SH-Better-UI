// Author: dishanhai
package com.dishanhai.shbui_text_compat_smoke;

import com.dishanhai.sh_better_ui.client.compat.text26.GuideMe26Text;

import guideme.color.ConstantColor;
import guideme.document.LytRect;
import guideme.document.block.LytDocument;
import guideme.document.block.LytParagraph;
import guideme.layout.LayoutContext;
import guideme.layout.MinecraftFontMetrics;
import guideme.render.SimpleRenderContext;
import guideme.style.TextStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Official GuideME document -> real LineBuilder -> LineTextRun -> native extraction probe. */
public final class GuideMeCompatProbe {
    private LytDocument document;
    private int beforeLayouts, beforeAttached, beforeRendered, beforeUnsupported;
    private int height;

    public void init(Font font, int width) {
        beforeLayouts = GuideMe26Text.LAYOUTS.get();
        beforeAttached = GuideMe26Text.ATTACHED.get();
        beforeRendered = GuideMe26Text.RENDERED.get();
        beforeUnsupported = GuideMe26Text.UNSUPPORTED.get();
        TextStyle style = TextStyle.builder().color(ConstantColor.WHITE).bold(true).fontScale(1.15f)
                .font(new FontDescription.Resource(Identifier.parse("minecraft:uniform"))).build();
        String[] text = {"山海 GuideME e\u0301 🌊  same same", "Repeated   whitespace\nwith wrapped lines 𠀀 e\u0301 and native bold font.",
                "Same words same words same words same words."};
        document = new LytDocument();
        var baseline = new LytDocument();
        for (String value : text) {
            var animated = LytParagraph.of("&~$rainbow-" + value);
            var plain = LytParagraph.of(value);
            animated.setStyle(style); plain.setStyle(style);
            document.append(animated); baseline.append(plain);
        }
        document.updateLayout(new LayoutContext(new MinecraftFontMetrics(font)), width);
        baseline.updateLayout(new LayoutContext(new MinecraftFontMetrics(font)), width);
        require(document.getContentHeight() == baseline.getContentHeight(), "markup changed native layout height");
        for (int i = 0; i < text.length; i++) {
            require(document.getBlocks().get(i).getBounds().equals(baseline.getBlocks().get(i).getBounds()),
                    "markup changed native paragraph bounds " + i);
        }
        require(GuideMe26Text.LAYOUTS.get() - beforeLayouts == text.length, "LineBuilder wrap did not execute");
        require(GuideMe26Text.ATTACHED.get() > beforeAttached, "LineTextRun association did not execute");
        require(GuideMe26Text.UNSUPPORTED.get() == beforeUnsupported, "supported Unicode/whitespace probe invalidated");
        height = document.getContentHeight();
        System.out.println("GUIDEME_LAYOUT " + evidence() + " bounds=" + document.getBlocks().stream().map(b -> b.getBounds().toString()).toList());
        require(height > 0, "native layout height empty");
    }

    /** Draw within a host-owned viewport; caller can take two real screenshots around this region. */
    public void render(GuiGraphicsExtractor graphics, int x, int y, int width) {
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            document.render(new SimpleRenderContext(new LytRect(x, y, width, height), graphics));
        } finally { graphics.pose().popMatrix(); }
        require(GuideMe26Text.RENDERED.get() > beforeRendered, "real GuideME run rendering did not execute");
    }
    public int height() { return height; }
    public String evidence() {
        return "GuideME official 26.1.17-beta layouts=" + (GuideMe26Text.LAYOUTS.get() - beforeLayouts)
                + " attached=" + (GuideMe26Text.ATTACHED.get() - beforeAttached)
                + " rendered=" + (GuideMe26Text.RENDERED.get() - beforeRendered)
                + " unsupported=" + (GuideMe26Text.UNSUPPORTED.get() - beforeUnsupported)
                + " nativeHeight=" + height;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("GuideME compatibility smoke: " + message);
    }
}
