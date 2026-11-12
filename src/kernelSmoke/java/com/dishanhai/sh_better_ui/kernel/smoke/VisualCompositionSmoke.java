// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur.RevealDirection;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.PixelRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.scaled.RevealProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.visual.FrameViewSnapshot;
import com.dishanhai.sh_better_ui.client.visual.VisualLayoutBinding;
import com.dishanhai.sh_better_ui.client.visual.VisualNodePass;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

import static com.dishanhai.sh_better_ui.client.visual.VisualEffectResult.*;
import static com.dishanhai.sh_better_ui.kernel.smoke.KernelClientSmoke.require;

final class VisualCompositionSmoke {
    private final Screen parent;
    private final Probe probe;
    private int step;
    private FrameViewSnapshot first;
    private String timings;

    String timings() { return timings; }

    VisualCompositionSmoke(Minecraft client, ModuleContext module) {
        parent = client.screen;
        ShBetterUiApi.blur().allowScreen(owner -> owner instanceof Probe);
        probe = new Probe(module);
        client.setScreen(probe);
    }

    boolean advance(Minecraft client) {
        if (probe.frames == 0) return false;
        if (probe.failure != null) throw new AssertionError("Composition callback failed", probe.failure);
        switch (step++) {
            case 0 -> {
                require(probe.samples == 1, "Expected one animation sample");
                require(probe.order.equals(List.of(1, 2, 3, 4)), "Unstable foreground order " + probe.order);
                first = probe.binding.currentSnapshot().orElseThrow();
                require(first.hitTest(probe.panel, 25, 25) && !first.hitTest(probe.panel, 90, 25),
                        "The presented hit test ignored inherited clip");
                require(probe.expired.requestBlur(1, 2, GlassStyle.NONE) == STALE_FRAME, "Old node pass accepted work");
                try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    int foreground = KernelClientSmoke.pixel(image, client, 26, 26) & 0xffffff;
                    require(foreground == 0x22e622, "Clear foreground was missing or blurred: " + foreground);
                    int blurred = KernelClientSmoke.pixel(image, client, 31, 77) & 255;
                    require(blurred > 2 && blurred < 253, "Managed blur did not change the current framebuffer: " + blurred);
                }
                probe.disabled = true;
            }
            case 1 -> {
                require(probe.samples == 2 && probe.order.equals(List.of(1, 2, 3, 4)),
                        "Disabled blur changed frame sampling or foreground delivery");
                var current = probe.binding.currentSnapshot().orElseThrow();
                require(current.frameId() > first.frameId() && !first.isCurrent()
                        && current.hitTest(probe.panel, 36, 25) && !current.hitTest(probe.panel, 25, 25),
                        "Published snapshot did not follow animation");
                client.setScreen(parent);
                require(!first.isValid() && !probe.binding.isActive(), "The old mount survived Screen closing");
                client.setScreen(probe);
            }
            case 2 -> {
                var remount = probe.binding.currentSnapshot().orElseThrow();
                require(remount.mountGeneration() != first.mountGeneration() && !first.isValid(),
                        "Remount reused old visual scope");
                probe.disabled = false;
                probe.legacyConflict = true;
            }
            case 3 -> {
                require(probe.order.equals(List.of(0, 1, 2, 3, 4)), "Legacy top layer ordering changed");
                probe.legacyConflict = false;
                probe.measure = true;
            }
            case 4 -> {
                if (probe.measurements.size() < 60) {
                    step--;
                    return false;
                }
                var sorted = probe.measurements.stream().sorted().toList();
                timings = "CPU time from binding entry through final foreground callback, GUI Scale 2, "
                        + "one panel with three additional effect nodes, samples=" + sorted.size()
                        + ", p50_us=" + sorted.get(sorted.size() / 2) / 1000
                        + ", p95_us=" + sorted.get(sorted.size() * 95 / 100) / 1000 + "\n";
                probe.measure = false;
                probe.failComposition = true;
            }
            case 5 -> {
                require(probe.order.isEmpty(), "Foreground survived a failed composition");
                require(probe.binding.currentSnapshot().isEmpty() && !probe.binding.hitTest(probe.panel, 60, 25)
                                && probe.rejected != null && !probe.rejected.hitTest(probe.panel, 60, 25),
                        "Failed composition published undrawn hit geometry");
                require(probe.widget.getX() == 0 && probe.widget.getY() == 0,
                        "Failed composition changed the native widget hit position");
                probe.failComposition = false;
            }
            case 6 -> {
                var recovered = probe.binding.currentSnapshot().orElseThrow();
                require(recovered.frameId() > probe.rejected.frameId() && recovered.hitTest(probe.panel, 36, 25)
                                && probe.order.equals(List.of(1, 2, 3, 4)),
                        "Hit geometry did not recover with the next successful composition");
                probe.releaseBeforePost = true;
            }
            case 7 -> {
                require(probe.order.isEmpty(), "Foreground survived release before Post");
                client.setScreen(parent);
                return true;
            }
            default -> throw new IllegalStateException();
        }
        return false;
    }

    private static final class Probe extends Screen {
        private final ModuleContext module;
        private final List<Integer> order = new ArrayList<>();
        private ScopeHandle scope;
        private VisualLayoutBinding binding;
        private AlignmentLayout.Node panel;
        private AlignmentLayout.Node backdrop;
        private AlignmentLayout.Node reveal;
        private AlignmentLayout.Node rotated;
        private AlignmentLayout.Node widgetNode;
        private Button widget;
        private VisualNodePass expired;
        private FrameViewSnapshot rejected;
        private int frames;
        private int samples;
        private boolean disabled;
        private boolean releaseBeforePost;
        private boolean legacyConflict;
        private boolean measure;
        private boolean failComposition;
        private final List<Long> measurements = new ArrayList<>();
        private Throwable failure;

        Probe(ModuleContext module) {
            super(Component.translatable("gui.done"));
            this.module = module;
        }

        @Override protected void init() {
            if (scope != null && scope.isOpen()) return;
            var engine = module.engine();
            scope = engine.require(UIApis.SCREENS).mount(module, this, UiId.parse("sh_better_ui:visual_smoke"));
            var layout = ShBetterUiApi.layout().create(new Bounds(0, 0, width, height));
            var parent = layout.node("container", AlignmentFrame.box(new Rect(20, 20, 100, 60))
                    .withClip(new Rect(0, 0, 60, 60)));
            panel = layout.node("panel", parent, geometry(0));
            backdrop = layout.node("backdrop", AlignmentFrame.box(new Rect(110, 20, 60, 40)));
            reveal = layout.node("reveal", AlignmentFrame.box(new Rect(110, 75, 60, 40))
                    .withClip(new Rect(8, 0, 48, 40)));
            rotated = layout.node("rotated", AlignmentFrame.box(new Rect(180, 20, 60, 40))
                    .withPose(com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Transform.rotation(0.2)));
            widget = Button.builder(Component.empty(), ignored -> {}).bounds(0, 0, 12, 12).build();
            widgetNode = layout.node("widget", AlignmentFrame.box(new Rect(110, 140, 12, 12)));
            binding = engine.require(UIApis.LAYOUT).bindVisual(module, scope, layout, time -> {
                int position = failComposition ? 36 : 12;
                panel.update(geometry(samples++ == 0 ? 0 : position));
            });
        }

        private AlignmentFrame geometry(int x) { return AlignmentFrame.box(new Rect(x, 0, 100, 60)); }

        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            frames++;
            order.clear();
            for (int x = 0; x < width; x++) {
                graphics.fill(x, 0, x + 1, height, x % 2 == 0 ? 0xff000000 : 0xffffffff);
            }
            var frame = new UiFrame(graphics, new Bounds(0, 0, width, height), UiMouseState.read(),
                    new UiPreferences(false, false, !disabled), UiFrameScheduler.nowMs(), partialTick);
            var blurApi = ShBetterUiApi.blur();
            if (legacyConflict) {
                blurApi.requestPixels(new PixelRect(10, 10, 100, 100), 1, 4, PixelRect.EMPTY, GlassStyle.NONE);
                blurApi.requestExtraPixels(new PixelRect(200, 10, 100, 40), 1, 4, PixelRect.EMPTY, GlassStyle.NONE, 0);
                blurApi.deferTopLayer(this, legacy -> order.add(0));
            }
            long start = System.nanoTime();
            binding.renderScreen(frame, pass -> {
                if (failComposition) {
                    rejected = pass.snapshot();
                    verify(() -> {
                        require(rejected.hitTest(panel, 60, 25), "Draw callback cannot inspect its resolved geometry");
                        require(binding.currentSnapshot().orElseThrow() != rejected,
                                "Undrawn geometry replaced the last presentation inside the callback");
                        rejected.placeWidget(widgetNode, widget);
                        require(widget.getX() == 110, "Native widget placement was unavailable during drawing");
                    });
                    pass.at(panel, visual -> visual.foreground(0, ignored -> order.add(5)));
                    throw new IllegalStateException("Intentional visual composition probe");
                }
                pass.at(backdrop, visual -> verify(() -> require(
                        visual.backdrop(0.8f, GlassStyle.OVERLAY, 7, 6) == (disabled ? DISABLED : DRAWN),
                        "Immediate rounded backdrop was unavailable")));
                pass.at(reveal, visual -> verify(() -> require(
                        visual.revealRegion(RevealProfile.DEFAULT, RevealDirection.RIGHT_TO_LEFT)
                                == (disabled ? DISABLED : DRAWN), "Clipped region reveal was unavailable")));
                pass.at(rotated, visual -> verify(() -> {
                    require(visual.requestBlur(1, 2, GlassStyle.NONE) == UNSUPPORTED_TRANSFORM,
                            "Rotated framebuffer blur was accepted");
                    require(visual.radialGradient(0.5f, 0.5f, 0.85f, 0x2275c2eb, 0x0075c2eb) == DRAWN,
                            "Rotated radial gradient was unavailable");
                }));
                pass.at(panel, visual -> verify(() -> {
                    var snapshot = pass.snapshot();
                    require(snapshot.view(panel) == visual.view(), "Effect received a different View");
                    try {
                        binding.renderScreen(frame, duplicate -> {});
                        throw new AssertionError("Recursive publication was accepted");
                    } catch (IllegalStateException expected) {
                        require(pass.snapshot() == snapshot, "Recursive attempt replaced the snapshot");
                    }
                    require(visual.radialGradient(0.5f, 0.5f, 0.85f, 0x2275c2eb, 0x0075c2eb) == DRAWN,
                            "Radial gradient shader was unavailable");
                    var texture = ResourceLocation.tryParse("sh_better_ui:textures/gui/display/bubble.png");
                    require(visual.revealTexture(texture, new Rect(16, 1, 14, 14), RevealProfile.DEFAULT,
                            RevealDirection.TOP_TO_BOTTOM) == (disabled ? FALLBACK : DRAWN),
                            "Texture reveal or its original-image fallback failed");
                    var missing = ResourceLocation.tryParse("sh_better_ui:missing_visual_probe.png");
                    require(visual.revealTexture(missing, new Rect(16, 1, 14, 14), RevealProfile.DEFAULT,
                            RevealDirection.LEFT_TO_RIGHT) == INVALID_TEXTURE, "Missing texture was accepted");
                    var blur = visual.requestBlur(1, 6, GlassStyle.NONE);
                    require(blur == (legacyConflict ? CONFLICT : disabled ? DISABLED : ACCEPTED),
                            "Unexpected blur outcome " + blur);
                    if (!disabled) require(ShBetterUiApi.blur().requestManagedPixels(graphics,
                            new PixelRect(0, 0, 5, 5), 1, 2, GlassStyle.NONE) == CONFLICT, "Duplicate blur was accepted");
                    visual.foreground(1, context -> order.add(2));
                    visual.foreground(0, context -> order.add(1));
                    if (frames == 1) visual.foreground(1, context -> {
                        order.add(3);
                        context.graphics().pose().pushPose();
                        context.graphics().pose().translate(50, 0, 0);
                        context.graphics().enableScissor(100, 100, 101, 101);
                        RenderSystem.disableBlend();
                        RenderSystem.disableDepthTest();
                        throw new IllegalStateException("Intentional visual foreground probe");
                    });
                    else visual.foreground(1, context -> order.add(3));
                    visual.foreground(2, context -> {
                        try {
                            require(context.snapshot() == snapshot && context.view() == snapshot.view(panel),
                                    "Foreground did not receive its registered snapshot");
                            require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                                            == Minecraft.getInstance().getMainRenderTarget().frameBufferId,
                                    "Foreground lost the native framebuffer");
                            order.add(4);
                            context.graphics().fill(3, 3, 9, 9, 0xff22e622);
                            if (measure) measurements.add(System.nanoTime() - start);
                        } catch (AssertionError error) {
                            failure = error;
                        }
                    });
                    expired = visual;
                    if (releaseBeforePost) scope.close();
                }));
            });
        }

        private void verify(Runnable check) {
            try {
                check.run();
            } catch (RuntimeException | AssertionError error) {
                failure = error;
            }
        }
    }
}
