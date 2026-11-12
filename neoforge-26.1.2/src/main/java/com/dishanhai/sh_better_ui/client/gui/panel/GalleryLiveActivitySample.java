// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Presents the sample activity and its seek action inside the gallery screen. */
final class GalleryLiveActivitySample extends GallerySampleScreen {
    private static final String PREVIEW = "sh_better_ui.live.preview.";
    private static final String GALLERY = "sh_better_ui.panel.developer.gallery.";
    private static final int HEIGHT = 22;
    private static final int GAP = 5;

    private ActivityService.Content activity = content(0.4);
    private boolean expanded;
    private UiButton capsule;
    private UiSlider seek;

    GalleryLiveActivitySample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected void initSample() {
        int width = Math.min(220, body.width());
        int x = body.x() + (body.width() - width) / 2;
        capsule = addButton(new Bounds(x, body.y(), width, HEIGHT), capsuleLabel(percent()), ignored -> toggle(), false);
        seek = addRenderableWidget(ShBetterUiApi.controls().slider(new Bounds(x, body.y() + HEIGHT + GAP, width, HEIGHT),
                0, 100, 1, percent(), preferences, value -> Component.translatable(PREVIEW + "progress", value),
                value -> capsule.setMessage(capsuleLabel(value))));
        seek.setOnCommit(value -> seek(new ActivityService.Action(ActivityService.SEEK, value / 100.0)),
                value -> capsule.setMessage(capsuleLabel(percent())));
        updateExpansion();
        initialFocus = capsule;
    }

    private void toggle() {
        expanded = !expanded;
        if (!expanded) {
            seek.cancelEdit();
            setInitialFocus(capsule);
        }
        updateExpansion();
    }

    private void updateExpansion() {
        capsule.setSelected(expanded);
        capsule.setLeadingIcon(expanded ? GraphicIcon.COLLAPSE : GraphicIcon.EXPAND);
        capsule.setTooltip(Tooltip.create(Component.translatable(GALLERY + (expanded ? "collapse" : "expand"))));
        seek.visible = seek.active = expanded;
    }

    private void seek(ActivityService.Action action) {
        activity = content(action.fraction());
        capsule.setMessage(capsuleLabel(percent()));
    }

    private int percent() { return (int) Math.round(activity.progress() * 100); }

    private Component capsuleLabel(int percent) {
        return Component.translatable(PREVIEW + "capsule", Component.translatable(activity.title().value()), percent);
    }

    private static ActivityService.Content content(double progress) {
        return new ActivityService.Content(ActivityService.Text.key(PREVIEW + "activity"),
                ActivityService.Text.key(PREVIEW + "detail"), null, progress, true, List.of(), List.of());
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int y = body.y() + 2 * (HEIGHT + GAP);
        if (expanded && y + font.lineHeight <= body.bottom()) {
            PanelText.drawLine(graphics, font, Component.translatable(activity.detail().value()),
                    seek.getX(), y, seek.getWidth(), theme.text(), mouseX, mouseY);
        }
    }
}
