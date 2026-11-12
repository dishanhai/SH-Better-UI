// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.blur.BackdropCapture;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

final class GalleryMotionSamples extends GallerySampleScreen {
    private static final int GAP = 5;
    private static final int ROW = 22;
    private static final long FADE_MS = 380;
    private static final String GALLERY = "sh_better_ui.panel.developer.gallery.";
    private static final Identifier OAK_PLANKS = blockTexture("oak_planks");
    private static final Identifier COBBLESTONE = blockTexture("cobblestone");
    private static final Identifier CRAFTING_TABLE = blockTexture("crafting_table_front");
    private static final Identifier FURNACE = blockTexture("furnace_front");

    private enum Direction {
        LEFT(1, 0, "slide_left", GraphicIcon.PREVIOUS), UP(0, 1, "slide_up", GraphicIcon.COLLAPSE),
        DOWN(0, -1, "slide_down", GraphicIcon.EXPAND), RIGHT(-1, 0, "slide_right", GraphicIcon.NEXT);

        private final int x, y;
        private final String key;
        private final GraphicIcon icon;

        Direction(int x, int y, String key, GraphicIcon icon) {
            this.x = x;
            this.y = y;
            this.key = key;
            this.icon = icon;
        }
    }

    private final InterruptibleAnimation position = new InterruptibleAnimation(Spring.FLUID_PANEL);
    private final GalleryMotionSamples previousScreen;
    private Bounds canvas;
    private int page;
    private int axisX = 1;
    private int axisY;
    private boolean depth;
    private long fadeStarted = -1;
    private float fadeOrigin;

    GalleryMotionSamples(DeveloperGalleryScreen parent, String id) { this(parent, id, null); }

    private GalleryMotionSamples(DeveloperGalleryScreen parent, String id, GalleryMotionSamples previousScreen) {
        super(parent, id);
        this.previousScreen = previousScreen;
    }

    @Override
    protected void initSample() {
        canvas = new Bounds(body.x(), body.y(), body.width(), body.height() - ROW - GAP);
        int y = canvas.bottom() + GAP;
        var row = new Bounds(body.x(), y, body.width(), ROW);
        switch (id) {
            case "interruptible_animation" -> initialFocus = addButton(row, animationLabel(), button -> {
                position.toggle(Util.getMillis());
                button.setMessage(animationLabel());
            }, true);
            case "screen_crossfade" -> initialFocus = addButton(row, previousScreen == null
                    ? Component.translatable(GALLERY + "crossfade_open") : text("return"), button -> switchScreen(), true);
            default -> initSlideControls(y);
        }
    }

    private void initSlideControls(int y) {
        int x = body.x();
        for (var direction : Direction.values()) {
            UiButton button = addButton(new Bounds(x, y, ROW, ROW), Component.translatable(GALLERY + direction.key),
                    ignored -> changePage(direction), false);
            button.setIcon(direction.icon);
            if (direction == Direction.LEFT) initialFocus = button;
            x += ROW + GAP;
        }
        if (id.equals("page_slide")) {
            int width = Math.min(115, body.right() - x);
            UiButton mode = addButton(new Bounds(x, y, width, ROW), modeLabel(), button -> {
                depth = !depth;
                button.setMessage(modeLabel());
                ((UiButton) button).setSelected(depth);
            }, false);
            if (width < 60) mode.setIcon(GraphicIcon.SCALE);
            mode.setSelected(depth);
        }
    }

    private Component modeLabel() { return text(depth ? "depth" : "flat"); }

    private Component animationLabel() {
        return Component.translatable("sh_better_ui.layout.preview." + (position.target() < 0.5f ? "expand" : "collapse"));
    }

    private void changePage(Direction direction) {
        long now = Util.getMillis();
        float current = position.sample(now, preferences);
        if (current <= 0 || current >= 1) {
            int sign = current <= 0 ? 1 : -1;
            axisX = direction.x * sign;
            axisY = direction.y * sign;
        }
        // While both pages are visible, reverse along the active axis to preserve their positions.
        page = 1 - page;
        position.animateTo(page, now);
        triggerImmediateNarration(true);
    }

    private void switchScreen() {
        if (previousScreen != null) {
            onClose();
            return;
        }
        var next = new GalleryMotionSamples(parent, id, this);
        next.startFade(fadeWeight());
        ControlPanelPlatform.show(minecraft, next);
    }

    private void startFade(float origin) {
        fadeOrigin = origin;
        fadeStarted = Util.getMillis();
    }

    private float fadeWeight() {
        float target = previousScreen == null ? 0f : 1f;
        if (fadeStarted < 0 || preferences.reducedMotion()) return target;
        float t = Math.clamp((Util.getMillis() - fadeStarted) / (float) FADE_MS, 0f, 1f);
        return fadeOrigin + (target - fadeOrigin) * t * t * (3f - 2f * t);
    }

    @Override
    public void onClose() {
        if (previousScreen == null) {
            super.onClose();
        } else {
            previousScreen.startFade(fadeWeight());
            ControlPanelPlatform.show(minecraft, previousScreen);
            previousScreen.setInitialFocus();
        }
    }

    @Override
    public Component getNarrationMessage() {
        return switch (id) {
            case "page_slide", "recipe_transition" -> CommonComponents.joinForNarration(getTitle(), text("page", page + 1));
            case "screen_crossfade" -> CommonComponents.joinForNarration(getTitle(), text("page", previousScreen == null ? 1 : 2));
            default -> super.getNarrationMessage();
        };
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(canvas.x(), canvas.y(), canvas.right(), canvas.bottom(), theme.background() | 0xFF000000);
        graphics.enableScissor(canvas.x(), canvas.y(), canvas.right(), canvas.bottom());
        try {
            float progress = Math.clamp(position.sample(Util.getMillis(), preferences), 0f, 1f);
            switch (id) {
                case "interruptible_animation" -> drawSpring(graphics, progress, mouseX, mouseY);
                case "screen_crossfade" -> drawFade(graphics, fadeWeight(), mouseX, mouseY);
                case "recipe_transition" -> drawRecipe(graphics, progress, mouseX, mouseY);
                default -> drawSlide(graphics, canvas, progress, false, mouseX, mouseY);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawSpring(GuiGraphicsExtractor graphics, float progress, int mouseX, int mouseY) {
        int cardHeight = Math.min(46, canvas.height() - 8);
        int cardWidth = Math.min(148, canvas.width() - 18);
        int x = canvas.x() + 5 + Math.round((canvas.width() - cardWidth - 10) * progress);
        int y = canvas.y() + (canvas.height() - cardHeight) / 2;
        int fill = ARGB.multiplyAlpha(theme.accent(), preferences.highContrast() ? 1f : 0.24f + progress * 0.45f);
        RoundedCorners.draw(graphics, x, y, cardWidth, cardHeight, 7, 1, theme.text(), fill);
        PanelText.drawLine(graphics, font, text("position", Math.round(progress * 100)), x + 5,
                y + (cardHeight - font.lineHeight) / 2, cardWidth - 10, theme.text(), mouseX, mouseY);
    }

    private void drawFade(GuiGraphicsExtractor graphics, float weight, int mouseX, int mouseY) {
        if (weight < 1) drawPage(graphics, canvas, 0, 1f, false, mouseX, mouseY);
        if (weight > 0 && weight < 1) graphics.nextStratum();
        drawPage(graphics, canvas, 1, weight, false, mouseX, mouseY);
    }

    private void drawSlide(GuiGraphicsExtractor graphics, Bounds area, float progress, boolean recipe, int mouseX, int mouseY) {
        if (progress < 1) drawSlidePage(graphics, area, 0, -progress, recipe, mouseX, mouseY);
        if (progress > 0) drawSlidePage(graphics, area, 1, 1 - progress, recipe, mouseX, mouseY);
    }

    private void drawSlidePage(GuiGraphicsExtractor graphics, Bounds area, int index, float offset,
                               boolean recipe, int mouseX, int mouseY) {
        float distance = depth && !recipe ? Math.abs(offset) : 0;
        int insetX = Math.round(area.width() * distance * (axisX == 0 ? 0.05f : 0.14f));
        int insetY = Math.round(area.height() * distance * (axisY == 0 ? 0.05f : 0.14f));
        int x = area.x() + insetX + Math.round(axisX * offset * area.width());
        int y = area.y() + insetY + Math.round(axisY * offset * area.height());
        var target = new Bounds(x, y, area.width() - 2 * insetX, area.height() - 2 * insetY);
        drawPage(graphics, target, index, 1 - 0.38f * distance, recipe, mouseX, mouseY);
    }

    private void drawRecipe(GuiGraphicsExtractor graphics, float progress, int mouseX, int mouseY) {
        int sidebarWidth = canvas.width() / 3;
        var pages = new Bounds(canvas.x(), canvas.y(), canvas.width() - sidebarWidth, canvas.height());
        graphics.enableScissor(pages.x(), pages.y(), pages.right(), pages.bottom());
        try {
            drawSlide(graphics, pages, progress, true, mouseX, mouseY);
        } finally {
            graphics.disableScissor();
        }
        int blur = Math.min(80, Math.round(Math.abs(position.velocity()) * 16));
        if (blur > 0 && !preferences.reducedMotion() && preferences.useBackdropBlur()) {
            var backdrop = BackdropCapture.request(graphics);
            if (backdrop != null) {
                GalleryMaterialEffects.drawBackdrop(graphics, pages, backdrop,
                        GalleryMaterialEffects.Effect.DIRECTIONAL_BLUR, blur, axisX == 0 ? 90 : 0);
                graphics.nextStratum();
            }
        }
        int x = pages.right();
        graphics.fill(x, canvas.y(), canvas.right(), canvas.bottom(), theme.surface() | 0xFF000000);
        graphics.fill(x, canvas.y(), x + 1, canvas.bottom(), theme.border());
        float visibility = Math.abs(progress * 2 - 1);
        var result = Component.translatable(progress < 0.5f ? "block.minecraft.crafting_table" : "block.minecraft.furnace");
        PanelText.drawLine(graphics, font, result, x + 4, canvas.y() + 7 + Math.round((1 - visibility) * 3),
                sidebarWidth - 8, ARGB.multiplyAlpha(theme.text(), visibility), mouseX, mouseY);
    }

    private void drawPage(GuiGraphicsExtractor graphics, Bounds area, int index, float opacity,
                          boolean recipe, int mouseX, int mouseY) {
        if (opacity <= 0) return;
        int color = preferences.highContrast() ? 0xFF000000
                : ARGB.srgbLerp(index == 0 ? 0.28f : 0.48f, theme.surface() | 0xFF000000, theme.accent() | 0xFF000000);
        graphics.fill(area.x() + 2, area.y() + 2, area.right() - 2, area.bottom() - 2, ARGB.multiplyAlpha(color, opacity));
        if (preferences.highContrast()) graphics.outline(area.x() + 2, area.y() + 2, area.width() - 4, area.height() - 4,
                ARGB.multiplyAlpha(theme.text(), opacity));
        if (recipe) {
            int y = area.y() + (area.height() - 16) / 2;
            drawIngredient(graphics, index == 0 ? OAK_PLANKS : COBBLESTONE, area.x() + 6, y);
            GraphicIcon.FORWARD.draw(graphics, area.x() + 26, y, 1, theme.text(), preferences);
            drawIngredient(graphics, index == 0 ? CRAFTING_TABLE : FURNACE, area.x() + 46, y);
            if (area.height() < 42) return;
        }
        PanelText.drawLine(graphics, font, text("page", index + 1), area.x() + 5, area.y() + 5,
                area.width() - 10, ARGB.multiplyAlpha(theme.text(), opacity), mouseX, mouseY);
    }

    private static Identifier blockTexture(String name) {
        return Identifier.withDefaultNamespace("textures/block/" + name + ".png");
    }

    private static void drawIngredient(GuiGraphicsExtractor graphics, Identifier texture, int x, int y) {
        // World item components have not been bound when the gallery opens from the mod list.
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, 16, 16, 16, 16);
    }
}
