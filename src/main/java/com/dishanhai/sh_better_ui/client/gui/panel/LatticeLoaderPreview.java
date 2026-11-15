// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLatticeLoader;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLatticeLoader.Labels;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLatticeLoader.Style;
import com.dishanhai.sh_better_ui.kernel.api.motion.LatticeLoader;
import com.dishanhai.sh_better_ui.kernel.api.motion.LatticeLoader.Preset;
import com.dishanhai.sh_better_ui.kernel.api.motion.LatticeLoader.Status;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

final class LatticeLoaderPreview implements PreviewScene {
    private static final String KEY = "sh_better_ui.panel.developer.gallery.lattice_loader.";
    private static final int HEIGHT = 110;
    private static final int BUTTON_HEIGHT = 22;
    private static final int GAP = 4;
    private static final Style SQUARE = new Style(UiLatticeLoader.Shape.SQUARE, 6, 2, 14, 90, .15f,
            null, Style.DEFAULT.doneColor(), Style.DEFAULT.errorColor(), true, null, true);

    private final Consumer<Component> narrate;
    private Font font;
    private LatticeLoader loader;
    private Status requested;

    private LatticeLoaderPreview(Consumer<Component> narrate) {
        this.narrate = narrate;
    }

    static void registerInto(PreviewRegistry registry, Consumer<Component> narrate) {
        registry.register(new PreviewSpec("sh_better_ui:lattice_loader", PreviewSpec.Category.CONTROLS, 33,
                "sh_better_ui.panel.developer.gallery.sample.lattice_loader", HEIGHT, 0, """
                import com.dishanhai.sh_better_ui.client.api.UiFrame;
                import com.dishanhai.sh_better_ui.client.gui.scaled.UiLatticeLoader;
                import com.dishanhai.sh_better_ui.kernel.api.motion.LatticeLoader;
                import net.minecraft.client.gui.Font;

                private LatticeLoader loader;

                private void startLoading(long nowMs) {
                    loader = new LatticeLoader(nowMs);
                }

                private void finishLoading(long nowMs) {
                    loader.setStatus(LatticeLoader.Status.DONE, nowMs);
                }

                private void drawLoading(UiFrame frame, Font font, int x, int y) {
                    if (loader == null) return;
                    UiLatticeLoader.draw(frame, font, x, y, loader, LatticeLoader.Preset.ORBIT.pattern(3),
                            UiLatticeLoader.Style.DEFAULT, UiLatticeLoader.Labels.DEFAULT, null);
                }
                """, () -> new LatticeLoaderPreview(narrate)));
    }

    @Override
    public void layout(PreviewContext host) {
        font = host.font();
        var area = host.viewport();
        if (area.height() < BUTTON_HEIGHT || area.width() < 3 * 24 + 2 * GAP) return;
        int width = Math.min(78, (area.width() - 2 * GAP) / 3);
        int x = area.x() + (area.width() - 3 * width - 2 * GAP) / 2;
        int y = area.bottom() - BUTTON_HEIGHT;
        addButton(host, x, y, width, "restart", Status.WORKING);
        addButton(host, x + width + GAP, y, width, "finish", Status.DONE);
        addButton(host, x + 2 * (width + GAP), y, width, "fail", Status.ERROR);
    }

    private void addButton(PreviewContext host, int x, int y, int width, String label, Status status) {
        host.add(new UiButton(x, y, width, BUTTON_HEIGHT, Component.translatable(KEY + label), ignored -> {
            if (host.interactive()) requested = status;
        }, host.preferences(), false));
    }

    @Override
    public void render(UiFrame frame) {
        if (loader == null) loader = new LatticeLoader(frame.nowMs());
        if (requested != null) {
            Status next = requested;
            requested = null;
            if (next == Status.WORKING) {
                loader.restart(frame.nowMs());
                narrate.accept(UiLatticeLoader.narration(loader, frame.nowMs(), Style.DEFAULT, Labels.DEFAULT, null));
            } else if (next != loader.status()) {
                loader.setStatus(next, frame.nowMs());
                narrate.accept(UiLatticeLoader.narration(loader, frame.nowMs(), Style.DEFAULT, Labels.DEFAULT, null));
            }
        }
        var area = frame.viewport();
        int x = area.x() + 4;
        int y = area.y() + 4;
        var orbit = Preset.ORBIT.pattern(3);
        int contentHeight = area.height() - BUTTON_HEIGHT;
        if (contentHeight < UiLatticeLoader.gridSize(orbit, Style.DEFAULT) + 4) return;
        UiLatticeLoader.draw(frame, font, x + 4, y, loader, orbit, Style.DEFAULT, Labels.DEFAULT, null);
        var sweep = Preset.SWEEP.pattern(4);
        int secondY = UiLatticeLoader.gridSize(orbit, Style.DEFAULT) + 7;
        if (contentHeight >= secondY + UiLatticeLoader.gridSize(sweep, SQUARE) + 4) {
            UiLatticeLoader.draw(frame, font, x, y + secondY, loader, sweep, SQUARE, Labels.DEFAULT, null);
        }
    }

    @Override
    public void suspend() {
        requested = null;
    }
}
