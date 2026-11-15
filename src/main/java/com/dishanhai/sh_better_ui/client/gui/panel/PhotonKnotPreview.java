// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPhotonKnot;
import com.dishanhai.sh_better_ui.kernel.api.motion.PhotonKnot;
import net.minecraft.network.chat.Component;

final class PhotonKnotPreview implements PreviewScene {
    private static final String KEY = "sh_better_ui.panel.developer.gallery.";
    private static final int BUTTON_HEIGHT = 22;
    private final PhotonKnot knot = new PhotonKnot();
    private Bounds canvas;

    static void registerInto(PreviewRegistry registry) {
        registry.register(new PreviewSpec("sh_better_ui:photon_knot", PreviewSpec.Category.MATERIALS, 330,
                KEY + "sample.photon_knot", 146, 0, """
                import com.dishanhai.sh_better_ui.client.api.UiFrame;
                import com.dishanhai.sh_better_ui.client.gui.scaled.UiPhotonKnot;
                import com.dishanhai.sh_better_ui.kernel.api.motion.PhotonKnot;

                private final PhotonKnot knot = new PhotonKnot();

                private void drawBackground(UiFrame frame) {
                    UiPhotonKnot.draw(frame, frame.viewport(), knot);
                }
                """, PhotonKnotPreview::new));
    }

    @Override
    public void layout(PreviewContext host) {
        var area = host.viewport();
        int controls = area.width() >= 80 && area.height() >= 54 && !host.preferences().highContrast()
                ? BUTTON_HEIGHT + 4 : 0;
        canvas = new Bounds(area.x(), area.y(), area.width(), area.height() - controls);
        if (controls == 0) return;
        int width = Math.min(112, area.width() - 8);
        var toggle = new UiButton(area.x() + (area.width() - width) / 2, area.bottom() - BUTTON_HEIGHT,
                width, BUTTON_HEIGHT, actionLabel(), button -> {
                    if (!host.interactive()) return;
                    knot.setPaused(!knot.isPaused());
                    button.setMessage(actionLabel());
                }, host.preferences(), false);
        toggle.active = !host.preferences().reducedMotion();
        host.add(toggle);
    }

    private Component actionLabel() {
        return Component.translatable(KEY + (knot.isPaused() ? "photon_knot.resume" : "photon_knot.pause"));
    }

    @Override
    public void render(UiFrame frame) {
        UiPhotonKnot.draw(frame, canvas, knot);
    }

    @Override
    public void suspend() {
        knot.suspend();
    }
}
