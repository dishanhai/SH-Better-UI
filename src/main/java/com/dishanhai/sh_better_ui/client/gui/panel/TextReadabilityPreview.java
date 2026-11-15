// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import net.minecraft.network.chat.Component;

/** Interactive declared-backdrop preview and Unified UI Kernel HUD example. */
final class TextReadabilityPreview implements PreviewScene {
    private static final String KEY = "sh_better_ui.panel.developer.gallery.text_readability.";
    private static final String SAMPLE = "sh_better_ui.panel.developer.gallery.sample.text_readability";
    private static final String CODE = """
            import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
            import com.dishanhai.sh_better_ui.kernel.api.UIApis;
            import com.dishanhai.sh_better_ui.kernel.api.UiId;
            import net.minecraft.client.Minecraft;
            import net.minecraft.network.chat.Component;

            private AutoCloseable hudLayer;

            private void mountStatus(ModuleContext module) {
                var hud = module.engine().require(UIApis.HUD);
                hudLayer = hud.mount(module, frame -> {
                    int x = 12, y = 12, width = 180, height = 28;
                    frame.graphics().fill(x, y, x + width, y + height, 0xB8202A38);
                    var palette = hud.textPalette(module, frame,
                            UiId.parse("example:status"), new Rect(x, y, width, height),
                            0xFF202A38, 0xFF465366);
                    int color = palette.color(0xFFE8EDF4);
                    frame.graphics().drawString(Minecraft.getInstance().font,
                            Component.translatable("example.status").getVisualOrderText(),
                            x + 6, y + 9, color, false);
                });
            }

            private void closeStatus() throws Exception {
                if (hudLayer != null) hudLayer.close();
            }
            """;

    private final TextReadabilityPalette palette = new TextReadabilityPalette();
    private boolean lightBackdrop;
    private Bounds canvas;
    private PreviewContext host;
    private UiButton backdropButton;

    static void registerInto(PreviewRegistry registry) {
        registry.register(new PreviewSpec("sh_better_ui:text_readability", PreviewSpec.Category.CONTROLS,
                8, SAMPLE, 96, 0, CODE, TextReadabilityPreview::new));
    }

    @Override
    public void layout(PreviewContext host) {
        this.host = host;
        var area = host.viewport();
        canvas = new Bounds(area.x() + 6, area.y() + 31, Math.max(0, area.width() - 12),
                Math.max(0, area.height() - 37));
        if (area.width() >= 100 && area.height() >= 56) {
            backdropButton = new UiButton(area.x() + 6, area.y() + 5, Math.min(140, area.width() - 12), 20,
                    Component.translatable(KEY + (lightBackdrop ? "dark_backdrop" : "light_backdrop")),
                    ignored -> {
                        if (!host.interactive()) return;
                        lightBackdrop = !lightBackdrop;
                        backdropButton.setMessage(Component.translatable(
                                KEY + (lightBackdrop ? "dark_backdrop" : "light_backdrop")));
                    }, host.preferences(), false);
            host.add(backdropButton);
        }
    }

    @Override
    public void render(UiFrame frame) {
        if (canvas.width() < 80 || canvas.height() < 46) return;
        var g = frame.graphics();
        int darkest = lightBackdrop ? 0xffaebdca : 0xff111a2a;
        int lightest = lightBackdrop ? 0xffedf2f5 : 0xff36485f;
        g.fillGradient(canvas.x(), canvas.y(), canvas.right(), canvas.bottom(), darkest, lightest);

        palette.update(null, darkest, lightest, frame.nowMs() * 1_000_000L);
        int x = canvas.x() + 10;
        int y = canvas.y() + 8;
        drawFit(g, KEY + "title", x, y,
                palette.color(0xfff5f7fa), false);
        drawFit(g, KEY + "body", x, y + 14,
                palette.color(0xffd6e3f2), false);
        drawFit(g, KEY + "caption", x, y + 28,
                palette.color(0xff75d7e8), false);
    }

    private void drawFit(net.minecraft.client.gui.GuiGraphics graphics, String key, int x, int y, int color,
                         boolean shadow) {
        var text = Component.translatable(key).getString();
        var visible = host.font().plainSubstrByWidth(text, Math.max(1, canvas.width() - 20));
        graphics.drawString(host.font(), visible, x, y, color, shadow);
    }
}
