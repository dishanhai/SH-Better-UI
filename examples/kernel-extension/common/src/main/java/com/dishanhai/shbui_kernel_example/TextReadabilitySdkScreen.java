// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Transform;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.text.TextId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Request;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Result;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.SamplingHint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

import static com.dishanhai.shbui_kernel_example.ExampleUiModule.id;
import static com.dishanhai.shbui_kernel_example.ExampleUiModule.label;

/** An ordinary Minecraft Screen integrating the optional UUK text readability service. */
final class TextReadabilitySdkScreen extends Screen {
    private static final TextId QUEUED = new TextId(id("readability/queued"));
    private static final TextId IMMEDIATE = new TextId(id("readability/immediate"));
    private static final int DARK = 0xff18222e;
    private static final int LIGHT = 0xffeef1f6;
    private static final int INK = 0xffffffff;
    private static final int MARGIN = 12;

    private final ModuleContext module;
    private final Screen parent;
    private final Supplier<? extends GuiEventListener> returnFocus;
    private final TextReadabilityService service;
    private final long startedAt = System.nanoTime();
    private ScopeHandle tree;

    TextReadabilitySdkScreen(ModuleContext module, Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        super(label("readability.title"));
        this.module = module;
        this.parent = parent;
        this.returnFocus = returnFocus;
        service = module.engine().require(UIApis.TEXT_READABILITY);
    }

    @Override protected void init() {
        if (tree == null || !tree.isOpen()) {
            tree = module.engine().require(UIApis.SCREENS).mount(module, this, id("readability_screen"));
            service.configure(module, tree, new TextReadabilityPolicy(true, true, null, null, null));
        }
        var back = addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(MARGIN, Math.max(MARGIN, height - 26), Math.min(120, Math.max(1, width - 2 * MARGIN)), 20)
                .build());
        setInitialFocus(back);
    }

    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, DARK);
        var preferences = UiPreferences.configured();
        int travel = Math.max(0, width - 2 * MARGIN);
        int stripeWidth = Math.min(travel, Math.max(56, travel / 2));
        double phase = (System.nanoTime() - startedAt) / 550_000_000.0;
        int left = MARGIN + (int) ((travel - stripeWidth) * (0.5 + 0.5 * Math.sin(phase)));
        if (!preferences.highContrast()) {
            if (preferences.reducedMotion()) left = MARGIN + (travel - stripeWidth) / 2;
            graphics.fill(left, 18, left + stripeWidth, Math.min(height, 76), LIGHT);
        }
        super.render(graphics, mouseX, mouseY, partialTick);

        // Draw a measured visual run. The declared color gives the service a fallback if sampling is unavailable.
        service.submit(module, request(QUEUED, label("readability.queued"), 27));
        // The callback receives the current foreground context for this exact scope.
        var immediate = request(IMMEDIATE, label("readability.immediate"), 52);
        service.foreground(module, tree, 1, context -> service.drawNow(module, context, immediate));
    }

    private Request request(TextId id, Component text, int y) {
        var glyphs = text.getVisualOrderText();
        var run = new TextReadabilityService.Run(glyphs, 0, 0, new Rect(0, 0, font.width(glyphs), font.lineHeight),
                font.lineHeight);
        var style = new TextReadabilityService.Style(INK, false, TextReadabilityPolicy.Usage.BODY);
        var clip = new Rect(MARGIN, 0, Math.max(0, width - 2 * MARGIN), height);
        return new Request(tree, id, run, style, Transform.translation(MARGIN, y), clip, 1, DARK)
                .withSampling(SamplingHint.IMPORTANT);
    }

    @Override public void onClose() {
        minecraft.setScreen(parent);
        var focus = returnFocus.get();
        if (parent.children().contains(focus)) parent.setFocused(focus);
    }

    @Override public void removed() {
        if (tree != null) tree.close();
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }

    boolean drawn() {
        return tree != null && drawn(QUEUED) && drawn(IMMEDIATE);
    }

    private boolean drawn(TextId id) {
        return service.diagnostics(module, tree, id)
                .filter(diagnostic -> diagnostic.result() == Result.DRAWN).isPresent();
    }

    boolean closed() { return tree != null && !tree.isOpen() && service.diagnostics(module, tree, QUEUED).isEmpty(); }
}
