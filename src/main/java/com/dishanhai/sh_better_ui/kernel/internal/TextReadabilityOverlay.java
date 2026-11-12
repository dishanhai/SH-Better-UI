// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiStateGuard;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Diagnostic;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.SamplingStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Collectors;

final class TextReadabilityOverlay {
    private static final String KEY = "sh_better_ui.text_readability.debug.";

    private TextReadabilityOverlay() {}

    static void render(GuiGraphics graphics, Diagnostic diagnostic) {
        var client = Minecraft.getInstance();
        var window = client.getWindow();
        var viewport = new Rect(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight());
        var font = client.font;
        var detail = diagnostic.gpu();
        var decision = detail.decision() == null ? diagnostic.decision() : detail.decision();
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable(KEY + "frame", diagnostic.sourceFrame(), diagnostic.result().name(),
                diagnostic.sampling().name(), ratio(diagnostic.targetContrast())));
        lines.add(Component.translatable(KEY + "geometry", describe(diagnostic.visibleBounds()),
                describe(diagnostic.effectiveClip())));
        if (diagnostic.sampling() == SamplingStatus.GPU) {
            lines.add(Component.translatable(KEY + "gpu", detail.status().name(), detail.sourceFrame(),
                    detail.pendingFrame(), detail.samples().size(), detail.attemptedSamples()));
        }
        if (decision != null) {
            lines.add(Component.translatable(KEY + "decision", decision.source().name(), decision.protection().name(),
                    ratio(decision.fillP10()), ratio(decision.finalP10()), String.format(Locale.ROOT, "%08X", decision.fillArgb())));
            if (!decision.diagnostics().isEmpty()) {
                lines.add(Component.translatable(KEY + "reasons", decision.diagnostics().stream()
                        .sorted().map(Enum::name).collect(Collectors.joining(", "))));
            }
        }
        int width = (int) viewport.width() - 16;
        if (width < 12) return;
        var wrapped = lines.stream().flatMap(line -> font.split(line, width - 8).stream()).toList();
        int height = Math.min((int) viewport.height() - 8, wrapped.size() * (font.lineHeight + 2) + 8);
        if (height <= 0) return;
        int panelY = diagnostic.visibleBounds().y() < viewport.height() / 2 ? (int) viewport.height() - height - 4 : 4;
        GuiStateGuard.draw(() -> {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0, 0, 600);
                outline(graphics, diagnostic.effectiveClip().intersect(viewport), 0xc0779fff);
                outline(graphics, diagnostic.visibleBounds().intersect(viewport), 0xff38d8e0);
                double scale = window.getGuiScale();
                for (var point : detail.samples()) {
                    int x = (int) (point.x() / scale);
                    int y = (int) (point.y() / scale);
                    int color = point.luminance() > 0.5 ? 0xff18354a : 0xfffdf585;
                    if (viewport.contains(x, y)) graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
                }
                graphics.fill(8, panelY, width + 8, panelY + height, 0xf2141923);
                int y = panelY + 4;
                for (var line : wrapped) {
                    if (y + font.lineHeight > panelY + height) break;
                    graphics.drawString(font, line, 12, y, 0xfff4f6fc, false);
                    y += font.lineHeight + 2;
                }
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        });
    }

    private static String ratio(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "—";
    }

    private static String describe(Rect rect) {
        return String.format(Locale.ROOT, "%.1f,%.1f %.1f×%.1f", rect.x(), rect.y(), rect.width(), rect.height());
    }

    private static void outline(GuiGraphics graphics, Rect rect, int color) {
        if (rect.empty()) return;
        int x = (int) Math.floor(rect.x());
        int y = (int) Math.floor(rect.y());
        int right = (int) Math.ceil(rect.right());
        int bottom = (int) Math.ceil(rect.bottom());
        graphics.fill(x, y, right, y + 1, color);
        graphics.fill(x, bottom - 1, right, bottom, color);
        graphics.fill(x, y, x + 1, bottom, color);
        graphics.fill(right - 1, y, right, bottom, color);
    }
}
