// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/** Screen-local effect editing with explicit apply and cancel. */
final class GalleryAppearanceSamples extends GallerySampleScreen {
    static final String KEY = "sh_better_ui.panel.developer.gallery.appearance.";
    private static final int DEFAULT_STRENGTH = 65;

    private final boolean editor;
    private final int[] saved = new int[Effect.values().length];
    private final int[] draft = new int[saved.length];
    private Effect effect;
    private Bounds canvas;
    private Bounds target;
    private UiSlider strength;
    private UiButton apply;
    private UiButton cancel;
    private UiButton action;
    private Component status = CommonComponents.EMPTY;
    private long origin = Util.getMillis();
    private long pausedAt = -1;
    private long rippleAt = -1;
    private float rippleX = 0.5f;
    private float rippleY = 0.5f;
    private float revealFrom;
    private boolean revealed;

    GalleryAppearanceSamples(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
        editor = id.equals("effect_appearance");
        var options = GalleryPreviewSettings.read();
        for (var choice : Effect.values()) {
            saved[choice.ordinal()] = GalleryPreviewSettings.number(options, choice.group, DEFAULT_STRENGTH, 0, 100);
        }
        System.arraycopy(saved, 0, draft, 0, saved.length);
        effect = switch (id) {
            case "effect_appearance", "click_ripple" -> Effect.RIPPLE;
            case "flow_border" -> Effect.BORDER;
            case "breathing_glow" -> Effect.GLOW;
            case "capsule_reveal" -> Effect.CAPSULE;
            default -> throw new IllegalArgumentException("Unknown appearance sample: " + id);
        };
    }

    @Override
    protected void initSample() {
        int rows = editor ? 2 : 1;
        int rowHeight = body.height() >= 82 ? 22 : 16;
        int firstRow = body.bottom() - (rowHeight + 3) * rows + 3;
        canvas = new Bounds(body.x(), body.y(), body.width(), Math.max(0, firstRow - body.y() - 3));
        int previewWidth = Math.min(190, canvas.width() - 12);
        int previewHeight = Math.max(1, Math.min(46, canvas.height() - 6));
        target = new Bounds(canvas.x() + (canvas.width() - previewWidth) / 2,
                canvas.y() + (canvas.height() - previewHeight) / 2, previewWidth, previewHeight);
        Bounds row = new Bounds(body.x(), firstRow, body.width(), rowHeight);
        if (editor) {
            initialFocus = addButton(column(row, 0, 3), effect.title(), button -> {
                effect = Effect.values()[(effect.ordinal() + 1) % saved.length];
                button.setMessage(effect.title());
                strength.setValue(draft[effect.ordinal()]);
                origin = Util.getMillis();
                pausedAt = rippleAt = -1;
                revealed = false;
                revealFrom = 0;
                sync();
            }, true);
        }
        int count = editor ? 3 : 2;
        strength = addRenderableWidget(new UiSlider(column(row, count - 2, count), 0, 100, 5,
                draft[effect.ordinal()], preferences, value -> label("strength", value), this::setStrength));
        strength.setOnCommit(value -> {}, this::setStrength);
        action = addButton(column(row, count - 1, count), actionLabel(), ignored -> activate(), false);
        if (editor) {
            Bounds edits = new Bounds(body.x(), firstRow + rowHeight + 3, body.width(), rowHeight);
            apply = addButton(column(edits, 0, 2), Component.translatable("sh_better_ui.panel.apply"),
                    ignored -> apply(), true);
            cancel = addButton(column(edits, 1, 2), CommonComponents.GUI_CANCEL, ignored -> {
                System.arraycopy(saved, 0, draft, 0, saved.length);
                strength.setValue(draft[effect.ordinal()]);
                status = label("cancelled");
                sync();
                triggerImmediateNarration(true);
            }, false);
        }
        sync();
        if (!editor) initialFocus = action.active ? action : strength;
    }

    private void setStrength(int value) {
        draft[effect.ordinal()] = value;
        status = CommonComponents.EMPTY;
        sync();
    }

    private void apply() {
        boolean persisted = GalleryPreviewSettings.write(options -> {
            for (var choice : Effect.values()) {
                int index = choice.ordinal();
                if (draft[index] != saved[index]) options.setProperty(choice.group, Integer.toString(draft[index]));
            }
        });
        if (persisted) System.arraycopy(draft, 0, saved, 0, saved.length);
        status = label(persisted ? "saved" : "save_failed");
        sync();
        triggerImmediateNarration(true);
    }

    private void sync() {
        if (action == null) return;
        action.setMessage(actionLabel());
        action.active = !effect.continuous || !preferences.reducedMotion() && !preferences.highContrast();
        if (editor && apply != null) {
            boolean dirty = !java.util.Arrays.equals(saved, draft);
            apply.active = cancel.active = dirty;
        }
    }

    private Component actionLabel() {
        return switch (effect) {
            case RIPPLE -> label("pulse");
            case BORDER, GLOW -> label(pausedAt < 0 ? "pause" : "resume");
            case CAPSULE -> label(revealed ? "hide" : "reveal");
        };
    }

    private void activate() {
        long now = Util.getMillis();
        switch (effect) {
            case RIPPLE -> trigger(0.5f, 0.5f);
            case BORDER, GLOW -> {
                if (pausedAt < 0) pausedAt = now;
                else {
                    origin += now - pausedAt;
                    pausedAt = -1;
                }
            }
            case CAPSULE -> {
                revealFrom = reveal(now);
                revealed = !revealed;
                origin = now;
            }
        }
        sync();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!compact && effect == Effect.RIPPLE && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.x() >= target.x() && event.x() < target.right()
                && event.y() >= target.y() && event.y() < target.bottom()) {
            trigger((float) ((event.x() - target.x()) / target.width()),
                    (float) ((event.y() - target.y()) / target.height()));
            setInitialFocus(action);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void trigger(float x, float y) {
        rippleX = x;
        rippleY = y;
        rippleAt = Util.getMillis();
    }

    @Override
    protected void cancelInteraction() {
        rippleAt = -1;
    }

    @Override
    public Component getNarrationMessage() {
        return super.getNarrationMessage().copy().append(" ").append(status);
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractFooterStatus(graphics, status, mouseX, mouseY);
        if (canvas.height() <= 0) return;
        int accent = preferences.highContrast() ? theme.text() : theme.accent();
        float amount = draft[effect.ordinal()] / 100f;
        long now = Util.getMillis();
        graphics.fill(canvas.x(), canvas.y(), canvas.right(), canvas.bottom(), theme.background() | 0xFF000000);
        graphics.enableScissor(canvas.x(), canvas.y(), canvas.right(), canvas.bottom());
        try {
            RoundedCorners.draw(graphics, target.x(), target.y(), target.width(), target.height(),
                    effect == Effect.CAPSULE ? target.height() / 2f : 8, 1,
                    preferences.highContrast() ? theme.text() : theme.border(), theme.surface() | 0xFF000000);
            switch (effect) {
                case RIPPLE -> ripple(graphics, accent, amount, now);
                case BORDER -> border(graphics, accent, amount, phase(1800, now));
                case GLOW -> glow(graphics, accent, amount, phase(2400, now));
                case CAPSULE -> capsule(graphics, accent, amount, reveal(now));
            }
            if (target.height() >= 13) {
                var caption = effect == Effect.CAPSULE && revealed ? label("revealed") : effect.title();
                int width = Math.min(font.width(caption), target.width() - 12);
                PanelText.drawLine(graphics, font, caption, target.x() + (target.width() - width) / 2,
                        target.y() + (target.height() - font.lineHeight) / 2, width, theme.text(), mouseX, mouseY);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void ripple(GuiGraphicsExtractor graphics, int color, float amount, long now) {
        float progress = (now - rippleAt) / 460f;
        if (rippleAt < 0 || progress >= 1 || amount == 0) return;
        int fill = alpha(color, amount * (1 - progress) * 0.65f);
        if (preferences.reducedMotion() || preferences.highContrast()) {
            RoundedCorners.draw(graphics, target.x(), target.y(), target.width(), target.height(), 8, 2, fill, 0);
            return;
        }
        int x = target.x() + Math.round(rippleX * target.width());
        int y = target.y() + Math.round(rippleY * target.height());
        int radius = Math.max(2, Math.round((float) Math.hypot(target.width(), target.height())
                * (1 - (1 - progress) * (1 - progress))));
        graphics.enableScissor(target.x(), target.y(), target.right(), target.bottom());
        try {
            RoundedCorners.draw(graphics, x - radius, y - radius, 2 * radius, 2 * radius, radius, 3, fill, 0);
        } finally {
            graphics.disableScissor();
        }
    }

    private void border(GuiGraphicsExtractor graphics, int color, float amount, float phase) {
        int width = target.width();
        int height = target.height();
        int perimeter = 2 * (width + height);
        for (int side = 0, offset = 0; side < 4; side++) {
            boolean horizontal = side % 2 == 0;
            int length = horizontal ? width : height;
            drawBorderSide(graphics, color, amount, phase, side, offset, length, perimeter);
            offset += length;
        }
    }

    private void drawBorderSide(GuiGraphicsExtractor graphics, int color, float amount, float phase,
                                int side, int offset, int length, int perimeter) {
        for (int start = 0; start < length; start += 5) {
            int end = Math.min(start + 5, length);
            float position = (offset + (start + end) / 2f) / perimeter;
            float distance = Math.abs(position - phase);
            float intensity = Math.max(0, 1 - Math.min(distance, 1 - distance) * 5);
            if (preferences.reducedMotion() || preferences.highContrast()) intensity = 0.75f;
            int argb = alpha(color, intensity * amount);
            if ((argb >>> 24) == 0) continue;
            switch (side) {
                case 0 -> graphics.fill(target.x() + start, target.y(), target.x() + end, target.y() + 1, argb);
                case 1 -> graphics.fill(target.right() - 1, target.y() + start, target.right(), target.y() + end, argb);
                case 2 -> graphics.fill(target.right() - end, target.bottom() - 1, target.right() - start, target.bottom(), argb);
                case 3 -> graphics.fill(target.x(), target.bottom() - end, target.x() + 1, target.bottom() - start, argb);
                default -> throw new IllegalArgumentException();
            }
        }
    }

    private void glow(GuiGraphicsExtractor graphics, int color, float amount, float phase) {
        float intensity = preferences.reducedMotion() || preferences.highContrast() ? 0.7f
                : (float) (0.52 + Math.cos(phase * Math.TAU) * 0.35);
        for (int i = 4; i >= 0; i--) {
            if (preferences.highContrast() && i > 0) continue;
            RoundedCorners.draw(graphics, target.x() - i, target.y() - i, target.width() + i * 2,
                    target.height() + i * 2, 8 + i, 1, alpha(color, amount * intensity / (i + 1)), 0);
        }
    }

    private void capsule(GuiGraphicsExtractor graphics, int color, float amount, float progress) {
        int inset = 3;
        int width = target.width() - 2 * inset;
        int height = target.height() - 2 * inset;
        int shown = Math.round(width * progress);
        if (shown <= 0 || height <= 0) return;
        graphics.enableScissor(target.x() + inset, target.y() + inset, target.x() + inset + shown, target.bottom() - inset);
        try {
            RoundedCorners.draw(graphics, target.x() + inset, target.y() + inset, width, height, height / 2f,
                    1, alpha(color, amount), alpha(color, amount * 0.35f));
        } finally {
            graphics.disableScissor();
        }
    }

    private float reveal(long now) {
        if (preferences.reducedMotion() || preferences.highContrast()) return revealed ? 1 : 0;
        float progress = GuiMotion.clamp01((now - origin) / 460f);
        return GuiMotion.lerp(revealFrom, revealed ? 1f : 0f, progress * progress * (3 - 2 * progress));
    }

    private float phase(int period, long now) {
        long elapsed = Math.max(0, (pausedAt < 0 ? now : pausedAt) - origin);
        return (elapsed % period) / (float) period;
    }

    static Bounds column(Bounds row, int index, int count) {
        int start = row.x() + row.width() * index / count;
        int end = row.x() + row.width() * (index + 1) / count;
        return new Bounds(start, row.y(), end - start - (index + 1 < count ? 4 : 0), row.height());
    }

    static Component label(String suffix, Object... values) { return Component.translatable(KEY + suffix, values); }

    private static int alpha(int color, float opacity) {
        return Math.round(255 * GuiMotion.clamp01(opacity)) << 24 | color & 0xFFFFFF;
    }

    private enum Effect {
        RIPPLE("clickRipple", false), BORDER("flowBorder", true), GLOW("breathingGlow", true), CAPSULE("capsule", false);

        private final String group;
        private final boolean continuous;

        Effect(String group, boolean continuous) {
            this.group = group;
            this.continuous = continuous;
        }

        Component title() { return Component.translatable("sh_better_ui.panel.theme.group." + group); }
    }
}
