// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTimeline;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.Locale;
import java.util.function.Consumer;

/** Keeps the playback position when the gallery rebuilds its widgets. */
final class GalleryFrameTimeline {
    private static final int GAP = 5;
    private static final UiTimeline.Sequence CAPTION = new UiTimeline.Sequence(30, 90);
    private final UiTimeline timeline = new UiTimeline(30, 120);
    private UiPreferences preferences;
    private Bounds canvas;
    private UiSlider scrubber;
    private UiButton transport;
    private UiTimeline.State shownState;

    UiSlider layout(Bounds area, UiPreferences preferences, Consumer<AbstractWidget> add) {
        this.preferences = preferences;
        if (preferences.reducedMotion()) timeline.pause(Util.getMillis());
        var frame = timeline.sample(Util.getMillis());
        int rowHeight = area.height() < 80 ? 16 : 22;
        int buttonsY = area.bottom() - GAP - rowHeight;
        int sliderY = buttonsY - GAP - rowHeight;
        canvas = new Bounds(area.x(), area.y(), area.width(), sliderY - GAP - area.y());

        var controls = ShBetterUiApi.controls();
        int last = frame.durationFrames() - 1;
        scrubber = controls.slider(new Bounds(area.x(), sliderY, area.width(), rowHeight), 0, last, 1,
                frame.index(), preferences, index -> label("frame", index, last, frame.fps()), ignored -> {});
        scrubber.setOnCommit(index -> {
            timeline.seek(index);
            sync(timeline.sample(Util.getMillis()));
        }, ignored -> sync(timeline.sample(Util.getMillis())));
        int resetWidth = Math.min(80, area.width() / 3);
        transport = controls.button(new Bounds(area.x(), buttonsY, area.width() - resetWidth - GAP, rowHeight),
                label("play"), ignored -> togglePlayback(), preferences, true, UiStyle.DEFAULT);
        transport.setPreview(label(preferences.reducedMotion() ? "reduced_motion" : "hint"));
        var reset = controls.button(new Bounds(area.right() - resetWidth, buttonsY, resetWidth, rowHeight),
                Component.translatable("controls.reset"), ignored -> {
                    timeline.reset();
                    sync(timeline.sample(Util.getMillis()));
                }, preferences, false, UiStyle.DEFAULT);
        reset.setLeadingIcon(GraphicIcon.RESET);
        add.accept(scrubber);
        add.accept(transport);
        add.accept(reset);
        shownState = null;
        sync(frame);
        return scrubber;
    }

    void pause() { timeline.pause(Util.getMillis()); }

    private void togglePlayback() {
        long now = Util.getMillis();
        if (timeline.sample(now).state() == UiTimeline.State.PLAYING) timeline.pause(now);
        else if (preferences.reducedMotion()) timeline.seek(timeline.durationFrames() - 1);
        else timeline.play(now);
        sync(timeline.sample(now));
    }

    private void sync(UiTimeline.Frame frame) {
        if (!scrubber.isPointerEditing()) scrubber.setValue(frame.index());
        if (shownState != frame.state()) {
            shownState = frame.state();
            var action = label(shownState == UiTimeline.State.PLAYING ? "pause" : "play");
            transport.setMessage(label("transport", action, label(shownState.name().toLowerCase(Locale.ROOT))));
        }
    }

    void extract(GuiGraphicsExtractor graphics, Font font, UiTheme theme, int mouseX, int mouseY) {
        var frame = timeline.sample(Util.getMillis());
        sync(frame);
        if (scrubber.isPointerEditing()) {
            frame = new UiTimeline.Frame(scrubber.value(), frame.fps(), frame.durationFrames(), frame.state());
        }
        boolean contrast = preferences.highContrast();
        graphics.fill(canvas.x(), canvas.y(), canvas.right(), canvas.bottom(),
                contrast ? 0xFF000000 : theme.background() | 0xFF000000);
        boolean roomy = canvas.height() >= 42;
        int cardY = canvas.y() + (roomy ? 15 : 2);
        int size = Math.min(24, canvas.bottom() - cardY - (roomy ? 13 : 2));
        float progress = frame.interpolate(0, 90, 0f, 1f);
        int cardX = canvas.x() + 5 + Math.round(progress * (canvas.width() - size - 10));
        int alpha = contrast ? 255 : Math.max(8, Math.round(frame.interpolate(0, 20, 0f, 255f)));
        RoundedCorners.draw(graphics, cardX, cardY, size, size, Math.min(5, size / 2), 1,
                theme.text(), alpha << 24 | theme.accent() & 0xFFFFFF);
        if (roomy) {
            PanelText.drawLine(graphics, font, label("idea"), canvas.x() + 5, canvas.y() + 2,
                    canvas.width() - 10, theme.text(), mouseX, mouseY);
            Component caption = CAPTION.contains(frame) ? label("sequence", CAPTION.localFrame(frame)) : label("sequence_wait");
            PanelText.drawLine(graphics, font, caption, canvas.x() + 5, canvas.bottom() - font.lineHeight - 1,
                    canvas.width() - 10, theme.text(), mouseX, mouseY);
        }
    }

    private static Component label(String key, Object... args) {
        return Component.translatable("sh_better_ui.panel.developer.gallery.frame_timeline." + key, args);
    }
}
