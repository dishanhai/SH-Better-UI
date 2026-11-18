// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.GlassControls;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMediaCard;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** The host confirms demo state; the card never pretends a playback request succeeded. */
final class GalleryMediaCardSample extends GallerySampleScreen {
    private GlassControls glass;
    private UiMediaCard card;
    private boolean playing;

    GalleryMediaCardSample(DeveloperGalleryScreen parent, String id) { super(parent, id); }

    @Override protected String sampleCode() {
        return """
                var glass = ShBetterUiApi.controls().glass(preferences);
                var card = glass.mediaCard(new Bounds(x, y, 240, 64),
                        Component.literal("Album title"), Component.literal("Artist"),
                        this::requestPlayback);
                addRenderableWidget(card);
                // Reflect confirmed playback, not merely a click.
                card.setPlaying(player.isPlaying());
                // Close glass when the layout is removed or rebuilt.
                """;
    }

    @Override protected int minimumBodyHeight() { return UiMediaCard.MIN_HEIGHT; }

    @Override protected void beforeSampleInit() {
        if (glass != null) glass.close();
        glass = ShBetterUiApi.controls().glass(preferences);
        card = null;
    }

    @Override protected void initSample() {
        int width = Math.min(280, body.width()), height = Math.min(64, body.height());
        if (width < UiMediaCard.MIN_WIDTH || height < UiMediaCard.MIN_HEIGHT) return;
        card = addRenderableWidget(glass.mediaCard(new Bounds(body.x() + (body.width() - width) / 2,
                body.y() + (body.height() - height) / 2, width, height),
                Component.translatable("sh_better_ui.media_card.demo_title"),
                Component.translatable("sh_better_ui.media_card.demo_artist"), () -> {
                    playing = !playing;
                    card.setPlaying(playing);
                    card.setSelected(playing);
                }));
        card.setPlaying(playing);
        card.setSelected(playing);
        initialFocus = card;
    }

    @Override protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (card == null) return;
        int x = card.getX(), y = card.getY(), w = card.getWidth(), h = card.getHeight();
        graphics.fill(x, y, x + w, y + h, 0xff153f79);
        graphics.fill(x + w / 5, y, x + w * 2 / 5, y + h, 0xff85c9d7);
        graphics.fill(x + w * 3 / 5, y, x + w * 4 / 5, y + h, 0xffd79d66);
    }

    @Override public void removed() {
        try { super.removed(); } finally { if (glass != null) glass.close(); }
    }

    @Override protected void cancelInteraction() {
        if (card != null) card.cancelInteraction();
    }
}
