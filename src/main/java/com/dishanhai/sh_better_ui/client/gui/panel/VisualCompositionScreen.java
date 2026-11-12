// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur.RevealDirection;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Transform;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.RevealProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.visual.VisualLayoutBinding;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static com.dishanhai.sh_better_ui.client.visual.VisualEffectResult.DRAWN;

/** The complete public API example linked from the API reference. */
public final class VisualCompositionScreen extends Screen {
    private static final ResourceLocation ICON = ResourceLocation.tryParse(
            "sh_better_ui:textures/gui/display/bubble.png");
    private static final int PANEL_WIDTH = 104;
    private static final int PANEL_HEIGHT = 64;

    static { ShBetterUiApi.blur().allowScreen(VisualCompositionScreen.class::isInstance); }

    private final ModuleContext module;
    private final Screen parent;
    private ScopeHandle tree;
    private VisualLayoutBinding binding;
    private AlignmentLayout.Node panel;
    private InterruptibleAnimation motion;
    private RevealProfile reveal;

    public VisualCompositionScreen(ModuleContext module, Screen parent) {
        super(Component.translatable("sh_better_ui.visual_composition.title"));
        this.module = module;
        this.parent = parent;
    }

    @Override protected void init() {
        if (tree == null || !tree.isOpen()) {
            var engine = module.engine();
            tree = engine.require(UIApis.SCREENS).mount(module, this, UiId.parse("sh_better_ui:visual_example"));
            var layout = ShBetterUiApi.layout().create(viewport());
            panel = layout.node("panel", geometry(0));
            motion = new InterruptibleAnimation(Spring.PANEL, 0);
            tree.track(motion);
            binding = engine.require(UIApis.LAYOUT).bindVisual(module, tree, layout, time -> {
                double progress = Math.max(0, Math.min(1, motion.sample(time.nowMs(), time.reducedMotion())));
                panel.update(geometry(progress));
                reveal = new RevealProfile((float) (12 * (1 - progress)), 0.15f, 0.85f);
            });
        }
        addRenderableWidget(Button.builder(Component.translatable("sh_better_ui.visual_composition.move"),
                button -> toggle()).bounds(8, height - 26, 112, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                button -> onClose()).bounds(width - 78, height - 26, 70, 20).build());
    }

    private Bounds viewport() { return new Bounds(0, 0, width, Math.max(0, height - 30)); }

    private AlignmentFrame geometry(double progress) {
        double maxScale = Math.max(0.1, Math.min(1.4, Math.min((width - 16.0) / PANEL_WIDTH,
                (height - 52.0) / PANEL_HEIGHT)));
        double scale = maxScale * (0.66 + 0.34 * progress);
        double x = 8 + Math.max(0, width - 16 - PANEL_WIDTH * maxScale) * progress;
        double y = 18 + Math.max(0, height - 58 - PANEL_HEIGHT * maxScale) * 0.5 * progress;
        return AlignmentFrame.box(new Rect(x, y, PANEL_WIDTH, PANEL_HEIGHT))
                .withPose(Transform.scale(scale, scale));
    }

    private void toggle() {
        motion.animateTo(motion.target() > 0.5f ? 0f : 1f, UiFrameScheduler.nowMs());
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button != 0 || binding == null || !binding.hitTest(panel, x, y)) return false;
        toggle();
        return true;
    }

    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xff152c3a, 0xff392445);
        for (int x = 0; x < width; x += 12) graphics.fill(x, 0, x + 1, height, 0x3375c2eb);
        var frame = new UiFrame(graphics, viewport(), UiMouseState.read(), UiPreferences.configured(),
                UiFrameScheduler.nowMs(), partialTick);
        binding.renderScreen(frame, pass -> pass.at(panel, visual -> {
            boolean sampled = visual.backdrop(0.8f, GlassStyle.OVERLAY, 7, 10) == DRAWN;
            boolean hovered = pass.snapshot().hitTest(panel, mouseX, mouseY);
            visual.draw(local -> RoundedCorners.draw(local, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, 10, 1, 0x508ed6f0,
                    sampled ? (hovered ? 0x80719dbd : 0x704b728f) : 0xe0314c65));
            visual.radialGradient(0.68f, 0.28f, 0.85f, 0x5583d9f0, 0x00457692);
            visual.revealTexture(ICON, new Rect(35, 8, 34, 34), reveal, RevealDirection.LEFT_TO_RIGHT);
            visual.requestBlur(0.48f, 3f, GlassStyle.NONE);
            visual.foreground(0, front -> front.graphics().drawCenteredString(font, title, 52, 49, 0xfff4fbff));
        }));
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }

    @Override public void removed() {
        if (tree != null) tree.close();
        super.removed();
    }
}
