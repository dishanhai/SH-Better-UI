// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback;
import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback.Status;
import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback.Tone;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiAttention;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Interactive gallery scenes for feedback, loading, attention and card lighting. */
final class GalleryControlSamples extends GallerySampleScreen {
    private static final String LABEL = "sh_better_ui.panel.developer.gallery.";
    private static final int CONTROL_HEIGHT = 22;
    private static final int GAP = 5;
    private static final long STATUS_DURATION = 4000L;
    private static final long RESPONSE_DELAY = 1400L;
    private static final long FEEDBACK_KEY = 1L;
    private static final Status[] LIVE_STATES = Status.values();
    private static final Status[] OUTCOMES = {Status.SUCCEEDED, Status.PARTIAL, Status.FAILED, Status.TIMED_OUT};

    private Bounds canvas;
    private long animationOrigin = Util.getMillis();
    private long pausedAt = -1L;
    private int percent = 45;
    private int ringPercent = -1;
    private FilledGeometry<Local> progressArc;
    private boolean loading = true;
    private Status liveStatus = Status.READY;
    private long liveStartedAt;
    private int livePercent;
    private final ButtonFeedback feedback = new ButtonFeedback();
    private int outcomeIndex;
    private int requestId;
    private long requestStartedAt;
    private Status activeOutcome = Status.SUCCEEDED;
    private Status feedbackStatus = Status.READY;
    private FeedbackButton feedbackButton;
    private UiButton cancelButton;
    private final UiAttention attention = new UiAttention();
    private final UiButton[] tabs = new UiButton[2];
    private int selectedTab;
    private Bounds gradientBounds;
    private GalleryControlPaint.CardEdge gradientEdge;

    GalleryControlSamples(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected void initSample() {
        int controlY = body.bottom() - CONTROL_HEIGHT;
        canvas = new Bounds(body.x(), body.y(), body.width(), controlY - GAP - body.y());
        Bounds controls = new Bounds(body.x(), controlY, body.width(), CONTROL_HEIGHT);
        switch (id) {
            case "dynamic_status" -> initStatus(controls);
            case "button_feedback" -> initFeedback(controls);
            case "loading_spinner", "loading_progress" -> initProgress(controls);
            case "loading_skeleton" -> initSkeleton(controls);
            case "attention_indicator" -> initAttention();
            case "gradient_card" -> initGradient(controls);
            default -> throw new IllegalArgumentException("Unknown control sample: " + id);
        }
    }

    private void initStatus(Bounds controls) {
        initialFocus = addButton(centered(controls, 160, CONTROL_HEIGHT), label("status_next"), ignored -> {
            long now = Util.getMillis();
            liveStatus = LIVE_STATES[(liveStatus.ordinal() + 1) % LIVE_STATES.length];
            liveStartedAt = now;
            livePercent = 0;
            triggerImmediateNarration(true);
        }, true);
    }

    private void initFeedback(Bounds controls) {
        var button = centered(canvas, 220, CONTROL_HEIGHT);
        feedbackButton = addRenderableWidget(new FeedbackButton(button));
        syncFeedbackCaption();
        initialFocus = feedbackButton;
        int leftWidth = (controls.width() - GAP) * 2 / 3;
        var outcome = new Bounds(controls.x(), controls.y(), leftWidth, controls.height());
        var cancel = new Bounds(outcome.right() + GAP, controls.y(),
                controls.right() - outcome.right() - GAP, controls.height());
        addButton(outcome, outcomeLabel(), pressed -> {
            outcomeIndex = (outcomeIndex + 1) % OUTCOMES.length;
            pressed.setMessage(outcomeLabel());
            triggerImmediateNarration(true);
        }, false);
        cancelButton = addButton(cancel, CommonComponents.GUI_CANCEL, ignored -> {
            long now = Util.getMillis();
            if (feedback.resolveCancelled(requestId, now)) setFeedbackStatus(Status.CANCELLED);
            else updateFeedback(now);
        }, false);
        cancelButton.active = feedbackStatus == Status.PENDING;
    }

    private void initProgress(Bounds controls) {
        Bounds transport = trailingControl(controls);
        var sliderArea = new Bounds(controls.x(), controls.y(), transport.x() - controls.x() - GAP, controls.height());
        UiSlider slider = ShBetterUiApi.controls().slider(sliderArea, 0, 100, 5, percent, preferences,
                GalleryControlSamples::progressLabel, value -> percent = value);
        slider.setOnCommit(ignored -> {}, value -> percent = value);
        initialFocus = addRenderableWidget(slider);
        addPause(transport);
    }

    private void initSkeleton(Bounds controls) {
        Bounds transport = trailingControl(controls);
        initialFocus = addButton(new Bounds(controls.x(), controls.y(), transport.x() - controls.x() - GAP,
                controls.height()), label("loading_toggle_card"), ignored -> {
            loading = !loading;
            triggerImmediateNarration(true);
        }, true);
        addPause(transport);
    }

    private void initAttention() {
        int leftWidth = (body.width() - GAP) / 2;
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            int x = i == 0 ? body.x() : body.x() + leftWidth + GAP;
            int width = i == 0 ? leftWidth : body.right() - x;
            tabs[i] = addButton(new Bounds(x, body.y(), width, CONTROL_HEIGHT),
                    page(i), ignored -> selectTab(index), false);
        }
        canvas = new Bounds(body.x(), body.y() + CONTROL_HEIGHT + 7, body.width(),
                body.height() - CONTROL_HEIGHT - 7);
        initialFocus = tabs[selectedTab];
        attention.clear();
        syncAttention();
    }

    private void initGradient(Bounds controls) {
        gradientBounds = centered(canvas, 180, Math.min(42, canvas.height() - 7));
        gradientEdge = new GalleryControlPaint.CardEdge(gradientBounds);
        var pause = addPause(centered(controls, 120, CONTROL_HEIGHT));
        if (pause.active) initialFocus = pause;
    }

    private void selectTab(int index) {
        if (index == selectedTab) return;
        selectedTab = index;
        syncAttention();
        triggerImmediateNarration(true);
    }

    private void syncAttention() {
        for (int i = 0; i < tabs.length; i++) tabs[i].setSelected(i == selectedTab);
        var tab = tabs[selectedTab];
        attention.select(selectedTab, tab.getX(), tab.getY(), tab.getWidth(), tab.getHeight(), Util.getMillis());
    }

    private UiButton addPause(Bounds bounds) {
        UiButton button = addButton(bounds, pauseLabel(), pressed -> {
            long now = Util.getMillis();
            if (pausedAt >= 0) {
                animationOrigin += now - pausedAt;
                pausedAt = -1;
            } else {
                pausedAt = now;
            }
            pressed.setMessage(pauseLabel());
            triggerImmediateNarration(true);
        }, false);
        button.active = !preferences.reducedMotion();
        return button;
    }

    private Component pauseLabel() {
        return label(pausedAt >= 0 ? "effect_resume" : "effect_pause");
    }

    private static Bounds trailingControl(Bounds controls) {
        int width = Math.min(104, (controls.width() - GAP) / 2);
        return new Bounds(controls.right() - width, controls.y(), width, controls.height());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (tabs[0] != null && (getFocused() == tabs[0] || getFocused() == tabs[1])
                && (event.isLeft() || event.isRight())) {
            int index = event.isLeft() ? 0 : 1;
            setInitialFocus(tabs[index]);
            selectTab(index);
            return true;
        }
        if (feedbackButton != null && event.isEscape() && feedback.holding()) {
            cancelInteraction();
            setDragging(false);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);
        if (repairCancelFocus()) setDragging(false);
        return handled;
    }

    @Override
    public void tick() {
        super.tick();
        long now = Util.getMillis();
        if (liveStatus == Status.PENDING) {
            livePercent = (int) Math.clamp((now - liveStartedAt) * 100 / STATUS_DURATION, 0L, 100L);
            if (livePercent == 100) {
                liveStatus = Status.SUCCEEDED;
                triggerImmediateNarration(true);
            }
        }
        if (id.equals("button_feedback")) updateFeedback(now);
    }

    private void startFeedback(long now) {
        updateFeedback(now);
        if (!feedback.tryBegin(FEEDBACK_KEY, now)) {
            feedback.nudge(FEEDBACK_KEY, now);
            return;
        }
        activeOutcome = OUTCOMES[outcomeIndex];
        requestStartedAt = now;
        feedback.pending(FEEDBACK_KEY, ++requestId, now);
        setFeedbackStatus(Status.PENDING);
    }

    private void updateFeedback(long now) {
        if (feedbackStatus == Status.PENDING) {
            if (activeOutcome != Status.TIMED_OUT && now - requestStartedAt >= RESPONSE_DELAY) {
                var tone = switch (activeOutcome) {
                    case SUCCEEDED -> Tone.SUCCESS;
                    case PARTIAL -> Tone.PARTIAL;
                    case FAILED -> Tone.FAILED;
                    default -> throw new IllegalStateException();
                };
                feedback.resolve(requestId, tone, requestStartedAt + RESPONSE_DELAY);
            }
            setFeedbackStatus(feedback.status(FEEDBACK_KEY, now));
        }
        feedback.tick(now);
    }

    private void setFeedbackStatus(Status status) {
        if (feedbackStatus == status) return;
        feedbackStatus = status;
        if (cancelButton != null) {
            cancelButton.active = status == Status.PENDING;
            repairCancelFocus();
        }
        syncFeedbackCaption();
        triggerImmediateNarration(true);
    }

    private boolean repairCancelFocus() {
        if (cancelButton == null || cancelButton.active || getFocused() != cancelButton) return false;
        setInitialFocus(feedbackButton);
        return true;
    }

    private void syncFeedbackCaption() {
        if (feedbackButton == null) return;
        Component message = feedbackStatus == Status.READY ? text("run")
                : CommonComponents.joinForNarration(text("run"), statusText(feedbackStatus));
        feedbackButton.setMessage(message);
        feedbackButton.setTooltip(Tooltip.create(message));
    }

    private Component outcomeLabel() {
        return text("outcome", statusText(OUTCOMES[outcomeIndex]));
    }

    @Override
    protected void cancelInteraction() {
        feedback.cancel(Util.getMillis());
    }

    @Override
    public Component getNarrationMessage() {
        Component state = switch (id) {
            case "dynamic_status" -> liveCaption();
            case "button_feedback" -> getFocused() == feedbackButton ? null : statusText(feedbackStatus);
            case "loading_spinner", "loading_progress" -> label("loading_progress.indeterminate");
            case "loading_skeleton" -> skeletonCaption();
            case "attention_indicator" -> page(selectedTab);
            default -> null;
        };
        return state == null ? super.getNarrationMessage()
                : CommonComponents.joinForNarration(super.getNarrationMessage(), state);
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        long time = Math.max(0L, (pausedAt >= 0 ? pausedAt : now) - animationOrigin);
        switch (id) {
            case "dynamic_status" -> extractStatus(graphics, mouseX, mouseY);
            case "button_feedback" -> updateFeedback(now);
            case "loading_spinner" -> extractSpinner(graphics, time, mouseX, mouseY);
            case "loading_progress" -> extractProgress(graphics, time, mouseX, mouseY);
            case "loading_skeleton" -> extractSkeleton(graphics, time, mouseX, mouseY);
            case "attention_indicator" -> extractAttention(graphics, now, mouseX, mouseY);
            case "gradient_card" -> {
                gradientEdge.extract(graphics, theme, preferences, time, textColor());
                caption(graphics, Component.translatable("gui.stats"), gradientBounds, textColor(), mouseX, mouseY);
            }
            default -> {}
        }
    }

    private void extractStatus(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var row = centered(canvas, 260, Math.min(canvas.height(), 46));
        box(graphics, row);
        int iconSize = GraphicIcon.PIXELS;
        int iconY = row.y() + (row.height() - iconSize) / 2;
        GalleryControlPaint.icon(liveStatus).draw(graphics, row.x() + 6, iconY, 1, statusColor(liveStatus), preferences);
        var copy = new Bounds(row.x() + iconSize + 12, row.y(), row.width() - iconSize - 18, row.height());
        caption(graphics, liveCaption(), copy, textColor(), mouseX, mouseY);
    }

    private Component liveCaption() {
        var status = statusText(liveStatus);
        if (liveStatus != Status.PENDING) return status;
        return Component.translatable("sh_better_ui.status.progress", status, livePercent);
    }

    private void extractSpinner(GuiGraphicsExtractor graphics, long time, int mouseX, int mouseY) {
        int half = canvas.width() / 2;
        var left = new Bounds(canvas.x(), canvas.y(), half - GAP / 2, canvas.height());
        var right = new Bounds(canvas.x() + half + GAP / 2, canvas.y(),
                canvas.width() - half - GAP / 2, canvas.height());
        box(graphics, left);
        box(graphics, right);
        if (ringPercent != percent) {
            ringPercent = percent;
            progressArc = GalleryControlPaint.arc(percent / 100f);
        }
        int diameter = Math.min(32, left.height() - 6);
        boolean captions = left.height() >= 49;
        float ringY = left.y() + (left.height() - (captions ? font.lineHeight + 4 : 0)) * 0.5f;
        GalleryControlPaint.ring(graphics, left.x() + left.width() * 0.5f, ringY, diameter,
                0f, progressArc, trackColor(), accentColor());
        boolean reduced = preferences.reducedMotion();
        float spinnerX = right.x() + right.width() * 0.5f;
        GalleryControlPaint.spinner(graphics, spinnerX, right.y() + (captions ? 10 : right.height() * 0.5f),
                12, time, reduced, trackColor(), accentColor());
        if (captions) {
            GalleryControlPaint.spinner(graphics, spinnerX, right.bottom() - 10, 12, time + 450,
                    reduced, trackColor(), accentColor());
            var progress = new Bounds(left.x(), left.bottom() - font.lineHeight - 3, left.width(), font.lineHeight);
            caption(graphics, progressLabel(percent), progress, textColor(), mouseX, mouseY);
            caption(graphics, label("loading_progress.indeterminate"), right, textColor(), mouseX, mouseY);
        }
    }

    private void extractProgress(GuiGraphicsExtractor graphics, long time, int mouseX, int mouseY) {
        var view = centered(canvas, 330, canvas.height());
        box(graphics, view);
        float phase = GalleryControlPaint.cycle(time, 1600L);
        float travel = preferences.reducedMotion() ? 0.5f : 1f - Math.abs(2f * phase - 1f);
        float eased = travel * travel * (3f - 2f * travel);
        GalleryControlPaint.bar(graphics, new Bounds(view.x() + 4, view.y() + 4, view.width() - 8, 3),
                0.75f * eased, 0.25f, trackColor(), accentColor());
        int y = view.y() + view.height() / 2;
        var track = new Bounds(view.x() + 8, y, view.width() - 16, 5);
        GalleryControlPaint.bar(graphics, track, 0f, percent / 100f, trackColor(), accentColor());
        if (view.height() >= 56) {
            caption(graphics, label("loading_progress.indeterminate"),
                    new Bounds(view.x(), view.y() + 8, view.width(), y - view.y() - 8), textColor(), mouseX, mouseY);
            caption(graphics, progressLabel(percent),
                    new Bounds(view.x(), track.bottom(), view.width(), view.bottom() - track.bottom()),
                    textColor(), mouseX, mouseY);
        }
    }

    private void extractSkeleton(GuiGraphicsExtractor graphics, long time, int mouseX, int mouseY) {
        var card = centered(canvas, 252, Math.min(64, canvas.height()));
        box(graphics, card);
        int size = Math.min(36, card.height() - 12);
        int x = card.x() + 6;
        int y = card.y() + (card.height() - size) / 2;
        var thumbnail = new Bounds(x, y, size, size);
        int textX = thumbnail.right() + 6;
        int textWidth = card.right() - 6 - textX;
        if (!loading) {
            GraphicIcon.STATUS_SUCCEEDED.draw(graphics, x + (size - GraphicIcon.PIXELS) / 2,
                    y + (size - GraphicIcon.PIXELS) / 2, 1, statusColor(Status.SUCCEEDED), preferences);
            caption(graphics, skeletonCaption(), new Bounds(textX, card.y(), textWidth, card.height()),
                    textColor(), mouseX, mouseY);
            return;
        }
        var firstLine = new Bounds(textX, card.y() + (card.height() - 12) / 2, textWidth * 4 / 5, 4);
        var secondLine = new Bounds(textX, firstLine.bottom() + 4, textWidth / 2, 4);
        skeletonBlock(graphics, card, thumbnail, time, 5);
        skeletonBlock(graphics, card, firstLine, time, 2);
        skeletonBlock(graphics, card, secondLine, time, 2);
    }

    private void skeletonBlock(GuiGraphicsExtractor graphics, Bounds card, Bounds block, long time, int radius) {
        float center = (block.x() - card.x() + block.width() * 0.5f) / card.width();
        float light = GalleryControlPaint.highlight(center, time, preferences.reducedMotion());
        int color = GuiMotion.lerpColor(trackColor(), textColor(), light * 0.42f);
        RoundedCorners.draw(graphics, block.x(), block.y(), block.width(), block.height(), radius, 0, 0, color);
    }

    private Component skeletonCaption() {
        return loading ? text("waiting") : label("loading_card_ready");
    }

    private void extractAttention(GuiGraphicsExtractor graphics, long now, int mouseX, int mouseY) {
        var frame = attention.sample(now, preferences);
        float y = frame.y() + frame.height() + 2;
        float inset = frame.width() * 0.18f;
        RoundedCorners.draw(graphics, frame.x() + inset, y, frame.width() - 2 * inset, 2,
                1, 0, 0, accentColor());
        box(graphics, canvas);
        int offset = Math.round(frame.contentOffsetY());
        int alpha = Math.round(255 * frame.contentAlpha());
        if (alpha == 0) return;
        graphics.enableScissor(canvas.x(), canvas.y(), canvas.right(), canvas.bottom());
        try {
            caption(graphics, page(selectedTab), new Bounds(canvas.x(), canvas.y() + offset,
                    canvas.width(), canvas.height()), alpha << 24 | textColor() & 0xFFFFFF, mouseX, mouseY);
        } finally {
            graphics.disableScissor();
        }
    }

    private void box(GuiGraphicsExtractor graphics, Bounds bounds) {
        RoundedCorners.draw(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), 6, 1,
                preferences.highContrast() ? textColor() : theme.border(), backgroundColor());
    }

    private void caption(GuiGraphicsExtractor graphics, Component component, Bounds bounds, int color, int mouseX, int mouseY) {
        int width = Math.max(1, bounds.width() - 8);
        int x = bounds.x() + (bounds.width() - Math.min(width, font.width(component))) / 2;
        int y = bounds.y() + (bounds.height() - font.lineHeight) / 2;
        PanelText.drawLine(graphics, font, component, x, y, width, color, mouseX, mouseY);
    }

    private int textColor() {
        int background = backgroundColor();
        return ContrastPolicy.readable(theme.text(), background, background,
                preferences.highContrast() ? ContrastPolicy.HIGH_CONTRAST_RATIO : ContrastPolicy.TEXT_RATIO);
    }

    private int statusColor(Status status) {
        return preferences.highContrast() ? textColor()
                : GalleryControlPaint.statusColor(status, textColor(), accentColor(), backgroundColor());
    }

    private int trackColor() {
        return preferences.highContrast() ? GuiMotion.lerpColor(backgroundColor(), textColor(), 0.38f)
                : ContrastPolicy.readable(theme.border(), backgroundColor(), backgroundColor(), 2);
    }

    private int accentColor() {
        return preferences.highContrast() ? textColor()
                : ContrastPolicy.readable(theme.accent(), backgroundColor(), backgroundColor(), 3);
    }

    private int backgroundColor() {
        return theme.background() | 0xFF000000;
    }

    private static Bounds centered(Bounds area, int width, int height) {
        int w = Math.min(width, area.width());
        int h = Math.min(height, area.height());
        return new Bounds(area.x() + (area.width() - w) / 2, area.y() + (area.height() - h) / 2, w, h);
    }

    private static Component statusText(Status status) {
        return Component.translatable(status.translationKey());
    }

    private static Component page(int tab) {
        return label("slide_page", tab + 1);
    }

    private static Component progressLabel(int value) {
        return label("loading_progress.value", value);
    }

    private static Component label(String key, Object... args) {
        return Component.translatable(LABEL + key, args);
    }

    /** Native focus, keyboard and narration with the shared feedback spring. */
    private final class FeedbackButton extends Button {
        private final Bounds hit;

        FeedbackButton(Bounds hit) {
            super(hit.x(), hit.y(), hit.width(), hit.height(), text("run"),
                    ignored -> startFeedback(Util.getMillis()), DEFAULT_NARRATION);
            this.hit = hit;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            feedback.press(FEEDBACK_KEY, hit.x(), hit.y(), hit.width(), hit.height(), true, Util.getMillis());
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            if (feedback.release(Util.getMillis()) && hit.contains(event.x(), event.y())) onPress(event);
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (!focused) cancelInteraction();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            long now = Util.getMillis();
            feedback.pointer(mouseX, mouseY, now);
            var visual = feedback.visual(FEEDBACK_KEY, FEEDBACK_KEY, true, now, preferences);
            int background = backgroundColor();
            int fill = theme.surface() | 0xFF000000;
            if (isHovered()) fill = GuiMotion.lerpColor(fill, accentColor(), 0.15f);
            if (feedback.holding() && hit.contains(mouseX, mouseY)) {
                fill = GuiMotion.lerpColor(fill, background, 0.25f);
            }
            fill = ButtonFeedback.shade(fill, visual);
            boolean contrast = preferences.highContrast();
            if (!contrast && visual.flashMix() > 0f) {
                fill = GuiMotion.lerpColor(fill, ButtonFeedback.toneColor(visual.tone()), visual.flashMix() * 0.28f);
            }
            int edge = isFocused() || contrast ? ContrastPolicy.readable(theme.text(), fill, background, 3)
                    : theme.border();
            if (!contrast && !isFocused()) edge = ButtonFeedback.tint(edge, visual);
            int color = ContrastPolicy.readable(theme.text(), fill, background,
                    contrast ? ContrastPolicy.HIGH_CONTRAST_RATIO : ContrastPolicy.TEXT_RATIO);
            float centerX = hit.x() + hit.width() * 0.5f;
            float centerY = hit.y() + hit.height() * 0.5f;
            var pose = graphics.pose();
            pose.pushMatrix();
            try {
                pose.translate(centerX + visual.shakeX(), centerY).scale(visual.scale()).translate(-centerX, -centerY);
                RoundedCorners.draw(graphics, hit.x(), hit.y(), hit.width(), hit.height(), 5,
                        isFocused() ? 2 : 1, edge, fill);
                if (visual.pending()) {
                    int width = Math.max(1, (hit.width() - 12) / 4);
                    int x = hit.x() + 6 + Math.round((hit.width() - 12 - width) * visual.pendingPhase());
                    graphics.fill(x, hit.bottom() - 3, x + width, hit.bottom() - 2, accentColor());
                }
                int iconY = hit.y() + (hit.height() - GraphicIcon.PIXELS) / 2;
                GalleryControlPaint.icon(feedbackStatus).draw(graphics, hit.x() + 5, iconY, 1,
                        statusColor(feedbackStatus), preferences);
                Component label = feedbackStatus == Status.READY ? text("run") : statusText(feedbackStatus);
                caption(graphics, label, new Bounds(hit.x() + 21, hit.y(), hit.width() - 26, hit.height()),
                        color, mouseX, mouseY);
            } finally {
                pose.popMatrix();
            }
        }
    }
}
