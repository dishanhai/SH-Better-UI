// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.visual.RichTextExtensions;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichText;
import net.minecraft.network.chat.Component;

/**
 * Screenshot-inspired, trusted Java composition. Not a Markdown extension grammar.
 * Renderers ignore payloads and use only the explicit local sample data below: no evaluation,
 * resource fetching or arbitrary registry lookup is granted to document text.
 */
final class TrustedRichTooltipDemo {
    private static final UiId GRAPH = id("graph");
    private static final UiId BARS = id("bars");
    private static final UiId RULE = id("rule");
    private static final RichStyle TITLE = new RichStyle(0x83ded0, true, false, false, false, false, null);
    private static final RichStyle LABEL = new RichStyle(0xf0c986, true, false, false, false, false, null);
    private static final String[] MATERIALS = {"铜 / Copper", "铁 / Iron", "金 / Gold"};
    private static final int[] VALUES = {40, 80, 100};
    private static final int[] COLORS = {0xffd28c65, 0xffb8c8da, 0xffe4c05b};

    private TrustedRichTooltipDemo() {}
    static RichDocument document() {
        var doc = RichText.document(id("document"));
        doc.text("蒸汽锅炉 · 可信富提示示例", TITLE).paragraph(6)
                .text("本页为演示数据，不代表游戏配方。", RichStyle.EMPTY).paragraph(6)
                .add(item("boiler", "furnace", "熔炉图标")).text(" 设备：锅炉　")
                .add(new RichObjectNode(id("water"), new RichObjectNode.Fluid(UiId.parse("minecraft:water")), 16, 16, 16, "水：160 L/t"))
                .text(" 水：160 L/t").paragraph(8)
                .extension(RULE, 1, 320, 1, 1, "分隔线", "").paragraph(6)
                .text("温度与效率", LABEL).paragraph(4)
                .text("示例函数 η = (T / 100)²，T = 0…100；效率 0…100%。").paragraph(4)
                .extension(GRAPH, 1, 320, 136, 136,
                        "折线图：温度 0、25、50、75、100，效率 0%、6.25%、25%、56.25%、100%", "")
                .paragraph(6).text("材料产出比较", LABEL).paragraph(4)
                .add(item("copper", "copper_ingot", "铜锭")).text(" 铜：40 L/t　")
                .add(item("iron", "iron_ingot", "铁锭")).text(" 铁：80 L/t　")
                .add(item("gold", "gold_ingot", "金锭")).text(" 金：100 L/t").paragraph(4)
                .extension(BARS, 1, 320, 80, 80, "横向条形图：铜 40，铁 80，金 100 L/t", "")
                .paragraph(8).extension(RULE, 1, 320, 1, 1, "分隔线", "").paragraph(6)
                .text("物品与流体使用本地原生图标；图表由宿主显式注册。")
                .paragraph(4).text("Markdown 表格见返回后的 Markdown 示例。滚轮或 Page Up / Page Down 滚动。");
        return doc.build();
    }
    private static RichObjectNode item(String name, String item, String alt) {
        return new RichObjectNode(id(name), new RichObjectNode.Item(UiId.parse("minecraft:" + item), 1), 16, 16, 16, alt);
    }
    static RichTextExtensions attach(ScopeHandle scope) {
        var registry = RichTextExtensions.attach(scope);
        registry.register(RULE, 1, context -> {
            var b = context.bounds();
            context.graphics().fill(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(), 0xff62748b);
        });
        registry.register(GRAPH, 1, TrustedRichTooltipDemo::graph);
        registry.register(BARS, 1, TrustedRichTooltipDemo::bars);
        return registry;
    }
    private static void graph(RichTextExtensions.Context context) {
        var b = context.bounds(); var canvas = context.graphics();
        canvas.fill(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(), 0xe0141d29);
        // Small GUI sizes retain the text values outside this graphic rather than illegible axes.
        if (b.width() < 120 || b.height() < 60) {
            canvas.drawText(Component.literal("η = (T/100)²"), b.x() + 4, b.y() + 4, 0xff83ded0, false);
            return;
        }
        int left = b.x() + 28, right = b.x() + b.width() - 12;
        int top = b.y() + 14, bottom = b.y() + b.height() - 24;
        int w = right - left, h = bottom - top;
        for (int i = 0; i <= 4; i++) {
            int y = bottom - h * i / 4;
            canvas.fill(left, y, right + 1, y + 1, 0xff344255);
        }
        canvas.fill(left, top, left + 1, bottom + 1, 0xff9eafc4);
        canvas.fill(left, bottom, right + 1, bottom + 1, 0xff9eafc4);
        // 48 bounded segments: 96 curve commands, below the per-extension 128-command limit.
        for (int i = 1; i <= 48; i++) {
            int x0 = left + w * (i - 1) / 48, x1 = left + w * i / 48;
            int y0 = bottom - (int) Math.round(h * Math.pow((i - 1) / 48d, 2));
            int y1 = bottom - (int) Math.round(h * Math.pow(i / 48d, 2));
            canvas.fill(x0, y0 - 1, x1 + 1, y0 + 1, 0xff70dfbd);
            canvas.fill(x1, y1, x1 + 2, y0 + 1, 0xff70dfbd);
        }
        canvas.drawText(Component.literal("100%"), b.x() + 2, b.y() + 2, 0xffcbd8e8, false);
        canvas.drawText(Component.literal("0"), left - 10, bottom - 3, 0xffcbd8e8, false);
        canvas.drawText(Component.literal("T: 0"), left, bottom + 8, 0xffcbd8e8, false);
        canvas.drawText(Component.literal("100"), right - 18, bottom + 8, 0xffcbd8e8, false);
    }
    private static void bars(RichTextExtensions.Context context) {
        var b = context.bounds(); var canvas = context.graphics();
        canvas.fill(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(), 0xe0141d29);
        int labelWidth = Math.min(90, b.width() / 3);
        int rowHeight = b.height() / 3;
        for (int i = 0; i < VALUES.length; i++) {
            int y = b.y() + i * rowHeight + 4;
            int start = b.x() + labelWidth, available = Math.max(0, b.width() - labelWidth - 36);
            canvas.drawText(Component.literal(context.font().plainSubstrByWidth(MATERIALS[i], Math.max(0, labelWidth - 6))), b.x() + 3, y, COLORS[i], false);
            canvas.fill(start, y, start + available, y + 10, 0xff344255);
            canvas.fill(start, y, start + available * VALUES[i] / 100, y + 10, COLORS[i]);
            canvas.drawText(Component.literal(Integer.toString(VALUES[i])), start + available + 4, y, 0xffe6edf6, false);
        }
    }
    private static UiId id(String path) { return UiId.parse("sh_better_ui:trusted_tooltip/" + path); }
}
