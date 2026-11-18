// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMediaCard;
import net.minecraft.network.chat.Component;
import java.util.function.Supplier;

final class MediaCardPreview implements PreviewScene {
    private boolean playing;
    private UiMediaCard card;
    private com.dishanhai.sh_better_ui.client.gui.scaled.GlassControls glass;
    @Override public void layout(PreviewContext host) {
        suspend();
        var area = host.viewport();
        if (area.width() < UiMediaCard.MIN_WIDTH || area.height() < UiMediaCard.MIN_HEIGHT) return;
        int width = Math.min(280, area.width()), height = Math.min(64, area.height());
        glass = ShBetterUiApi.controls().glass(host.preferences());
        card = host.add(glass.mediaCard(new Bounds(area.x() + (area.width() - width) / 2,
                area.y() + (area.height() - height) / 2, width, height),
                Component.translatable("sh_better_ui.media_card.demo_title"),
                Component.translatable("sh_better_ui.media_card.demo_artist"), () -> {
                    if (host.interactive()) { playing = !playing; card.setPlaying(playing); card.setSelected(playing); }
                }));
        card.setPlaying(playing); card.setSelected(playing);
    }
    @Override public void render(com.dishanhai.sh_better_ui.client.api.UiFrame frame) {
        if (card != null) GalleryPreviewLayout.drawBackdrop(frame.graphics(), card.getX(), card.getY(),
                card.getWidth(), card.getHeight());
    }
    @Override public void suspend() { if (glass != null) glass.close(); glass = null; card = null; }
    @Override public void close() { suspend(); }
    @Override public Supplier<? extends PreviewScene> saveForRemount() {
        boolean saved = playing;
        return () -> { var scene = new MediaCardPreview(); scene.playing = saved; return scene; };
    }
}
