// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_better_ui.theme.ThemeBinding;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;

/** 山海：以 Minecraft GUI 提取接口绘制，并沿用原生输入、焦点与旁白。 */
public final class UiButton extends Button {
    private static final int ICON_GAP = 4;
    private static final int LABEL_PADDING = 6;
    private static final int MODE_INDICATOR_WIDTH = 8;
    private static final int MODE_INDICATOR_GAP = 3;
    private static final int MODE_INDICATOR_RESERVE = 4;
    private static final int CONTROL_TRANSITION_MS = 200;
    private static final GlassOptics GLASS = GlassMaterial.preset(GlassVariant.REGULAR).optics();

    private final boolean primary;
    private final UiStyle style;
    private final Transition hoverMotion = new Transition();
    private final Transition pressMotion = new Transition();
    private UiPreferences preferences;
    private boolean justPressed;
    private boolean pointerHeld;
    private boolean held;
    private boolean selected;
    private boolean sampleBackground = true;
    private float backgroundBlurRadius = 12f;
    private long selectedAt;
    private int modeIndicatorCount;
    private int selectedModeIndex = -1;
    private @Nullable Tooltip tooltip;
    private Tooltip labelTooltip;
    private @Nullable GraphicIcon icon;
    private boolean iconWithLabel;
    private int iconOffsetY;
    private @Nullable ThemeButtonStyle themeBinding;

    public UiButton(int x, int y, int width, int height, Component label, OnPress onPress) {
        this(x, y, width, height, label, onPress, UiPreferences.configured(), false);
    }

    public UiButton(int x, int y, int width, int height, Component label, OnPress onPress,
                    UiPreferences preferences, boolean primary) {
        this(x, y, width, height, label, onPress, preferences, primary, UiStyle.DEFAULT);
    }

    public UiButton(int x, int y, int width, int height, Component label, OnPress onPress,
                    UiPreferences preferences, boolean primary, UiStyle style) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.primary = primary;
        this.style = Objects.requireNonNull(style);
        setPreferences(preferences);
        setPreview(null);
    }

    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
        setTooltipDelay(Duration.ofMillis(preferences.feedback().tooltipDelayMs()));
    }

    public void setSampleBackground(boolean enabled) { sampleBackground = enabled; }

    public void setBackgroundBlurRadius(float radius) {
        if (!Float.isFinite(radius) || radius < 0) throw new IllegalArgumentException("Invalid blur radius");
        backgroundBlurRadius = Math.min(32f, radius);
    }

    public float getBackgroundBlurRadius() { return backgroundBlurRadius; }

    public boolean supportsThemeBinding() {
        return true;
    }

    public void bindTheme(ThemeScope scope) {
        bindTheme(scope, ThemeBinding.of(primary ? ThemeTokens.PRIMARY : ThemeTokens.SURFACE),
                ThemeBinding.of(primary ? ThemeTokens.ON_PRIMARY : ThemeTokens.TEXT));
    }

    public void bindTheme(ThemeScope scope, ThemeBinding<ColorValue> background, ThemeBinding<ColorValue> textColor) {
        var replacement = new ThemeButtonStyle(scope, style, background, textColor);
        unbindTheme();
        themeBinding = replacement;
    }

    public boolean hasThemeBinding() {
        return themeBinding != null && themeBinding.active();
    }

    public void unbindTheme() {
        if (themeBinding != null) themeBinding.close();
        themeBinding = null;
    }

    /** 传入 null 使用完整按钮名称。 */
    public void setPreview(@Nullable Component preview) {
        if (preview == null) refreshLabelTooltip();
        else setTooltip(Tooltip.create(preview));
    }

    @Override
    public void setTooltip(@Nullable Tooltip tooltip) {
        super.setTooltip(tooltip);
        this.tooltip = tooltip;
    }

    public @Nullable Tooltip getTooltip() {
        return tooltip;
    }

    public void setIcon(@Nullable GraphicIcon icon) {
        this.icon = icon;
        iconWithLabel = false;
    }

    public void setLeadingIcon(@Nullable GraphicIcon icon) {
        this.icon = icon;
        iconWithLabel = true;
    }

    public void setIconOffsetY(int offset) {
        iconOffsetY = UiMath.clamp(offset, -2, 2);
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        if (getTooltip() == labelTooltip) refreshLabelTooltip();
    }

    private void refreshLabelTooltip() {
        labelTooltip = Tooltip.create(getMessage(), null);
        setTooltip(labelTooltip);
    }

    public void setSelected(boolean selected) {
        if (this.selected == selected) return;
        this.selected = selected;
        selectedAt = Util.getMillis();
    }

    public boolean isSelected() {
        return selected;
    }

    public void setModeIndicators(int count, int selectedIndex) {
        if (count < 1 || selectedIndex < 0 || selectedIndex >= count) throw new IllegalArgumentException();
        modeIndicatorCount = count;
        selectedModeIndex = selectedIndex;
    }

    public void clearModeIndicators() {
        modeIndicatorCount = 0;
        selectedModeIndex = -1;
    }

    public int getModeIndicatorCount() {
        return modeIndicatorCount;
    }

    public int getSelectedModeIndex() {
        return selectedModeIndex;
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        var narration = super.createNarrationMessage();
        return selected ? CommonComponents.joinForNarration(narration,
                Component.translatable("sh_better_ui.control.selected")) : narration;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress();
    }

    public void onPress() {
        justPressed = true;
        onPress.onPress(this);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        pointerHeld = true;
        super.onClick(event, doubleClick);
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        pointerHeld = false;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) pointerHeld = false;
    }

    /** 供聚合控件同步按住状态，独立于动作提交。 */
    void setHeld(boolean held) {
        this.held = held;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0 || alpha <= 0f) return;

        var binding = hasThemeBinding() ? themeBinding : null;
        var effective = binding == null ? preferences : binding.preferences(preferences);
        var feedback = effective.feedback();
        boolean enabled = isActive();
        boolean focused = enabled && isFocused();
        if (!enabled || pointerHeld && !Minecraft.getInstance().isWindowActive()) pointerHeld = false;
        boolean pressed = enabled && (justPressed || pointerHeld || held);
        justPressed = false;
        long now = Util.getMillis();
        boolean immediate = effective.reducedMotion() || !enabled;
        float hover = hoverMotion.sample(enabled && isHovered(), immediate || !feedback.hoverAnimation(), now);
        float press = pressMotion.sample(pressed, immediate || pressed || !feedback.pressAnimation(), now);
        var paint = binding == null
                ? palette(effective, focused, enabled, hover, press, now)
                : binding.sample(isHovered(), pressed, focused, enabled, effective.highContrast(), selected, hover, press);

        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            if (!effective.reducedMotion() && feedback.pressAnimation() && press > 0f) {
                float centerX = x + width * 0.5f, centerY = y + height * 0.5f;
                pose.translate(centerX, centerY).scale(1f - 0.02f * press).translate(-centerX, -centerY);
            }
            drawButton(graphics, x, y, width, height, paint, effective, focused);
        } finally {
            pose.popMatrix();
        }
    }

    private ThemeButtonStyle.Paint palette(UiPreferences effective, boolean focused, boolean enabled,
                                          float hover, float press, long now) {
        var theme = UiTheme.current();
        boolean contrast = effective.highContrast();
        boolean glass = style == UiStyle.LIQUID_GLASS || style == UiStyle.DEFAULT && theme.liquidGlass();
        int background = theme.background(0xFF0F172A) | 0xFF000000;
        int fill = theme.surface(style == UiStyle.NETWORK ? 0xFF162737 : 0xFF1E293B);
        if (enabled) {
            if (primary) fill = theme.controlFill(0xFF1D4ED8, 0.18f);
            int accent = theme.accent(0xFF3B82F6);
            float pulse = selected && !effective.reducedMotion() && effective.feedback().selectionPulse()
                    ? 1f - GuiMotion.clamp01((now - selectedAt) / 360f) : 0f;
            if (selected) fill = GuiMotion.lerpColor(fill, accent, 0.22f + 0.18f * pulse);
            fill = GuiMotion.lerpColor(fill, accent, 0.18f * hover);
            fill = GuiMotion.lerpColor(fill, background, 0.24f * press);
        }
        fill = fill & 0x00FFFFFF | (glass && !contrast ? 0xEC000000 : 0xFF000000);
        int text = enabled ? theme.text(0xFFF1F5F9) : theme.secondaryText(0xFF94A3B8, 0.38f);
        if (enabled || contrast) {
            text = ContrastPolicy.readable(text, fill, background,
                    contrast ? ContrastPolicy.HIGH_CONTRAST_RATIO : ContrastPolicy.TEXT_RATIO);
        }
        int focus = contrast ? ContrastPolicy.readable(theme.accent(0xFF93C5FD), fill, background, 3)
                : theme.accent(0xFF93C5FD);
        int border = selected || focused ? focus : contrast ? text
                : GuiMotion.lerpColor(theme.border(0xFF334155), theme.accent(0xFF64748B), hover);
        return new ThemeButtonStyle.Paint(fill, text, border, focus, theme.accent(0xFF3B82F6), background,
                glass ? getHeight() * 0.5f : 4f, 1, glass);
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            ThemeButtonStyle.Paint paint, UiPreferences effective, boolean focused) {
        float stroke = focused ? Math.max(2, paint.borderWidth()) : paint.borderWidth();
        int border = focused ? paint.focus() : paint.border();
        boolean optical = paint.glass() && sampleBackground && effective.useBackdropBlur()
                && drawGlass(graphics, x, y, width, height, paint, effective);
        RoundedCorners.draw(graphics, x, y, width, height, paint.radius(), stroke, applyAlpha(border),
                optical ? 0 : applyAlpha(paint.fill()));
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            var mode = modeIndicatorLayout();
            int contentHeight = mode == null ? height : height - MODE_INDICATOR_RESERVE;
            int text = packedFGColor == UNSET_FG_COLOR ? paint.text() : packedFGColor | 0xFF000000;
            drawContent(graphics, x, y, width, contentHeight, applyAlpha(text), effective);
            int indicator = effective.highContrast() || !isActive() ? text : paint.accent();
            if (mode != null) {
                drawModeIndicators(graphics, mode, text, indicator, effective);
            } else if (selected && width > 10 && height > 5) {
                int halfWidth = Math.min(4, width / 4);
                int center = x + width / 2;
                graphics.fill(center - halfWidth, y + height - 3, center + halfWidth, y + height - 2,
                        applyAlpha(indicator));
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private boolean drawGlass(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              ThemeButtonStyle.Paint paint, UiPreferences effective) {
        var settings = ControlPanelSettings.current();
        if (!settings.glassEnabled()) return false;
        var material = new GlassOptics(GLASS.transmission(), GLASS.refraction(), GLASS.lensing(), GLASS.scattering(),
                GLASS.edgeHighlight(), GLASS.thickness(), backgroundBlurRadius);
        int tint = 0x84000000 | (paint.fill() & 0xFFFFFF);
        return LiquidGlassSurface.drawPrepared(graphics, x, y, width, height, paint.radius(), tint, 0,
                settings.optics(material), effective, alpha);
    }

    @Nullable ModeIndicatorLayout modeIndicatorLayout() {
        int width = getWidth(), height = getHeight();
        if (modeIndicatorCount == 0 || width < 3 || height < 14) return null;
        int gapCount = modeIndicatorCount - 1;
        long minimumWidth = (long) modeIndicatorCount + gapCount;
        if (minimumWidth > width - 2) return null;

        int padding = Math.min(LABEL_PADDING, (width - (int) minimumWidth) / 2);
        int available = width - 2 * padding;
        int gap = gapCount == 0 ? 0 : Math.min(MODE_INDICATOR_GAP, (available - modeIndicatorCount) / gapCount);
        int barWidth = Math.min(MODE_INDICATOR_WIDTH, (available - gapCount * gap) / modeIndicatorCount);
        int stripWidth = modeIndicatorCount * barWidth + gapCount * gap;
        return new ModeIndicatorLayout(getX() + (width - stripWidth) / 2, getY() + height - 2,
                barWidth, barWidth + gap);
    }

    private void drawModeIndicators(GuiGraphicsExtractor graphics, ModeIndicatorLayout layout, int textColor,
                                    int selectedColor, UiPreferences effective) {
        float opacity = effective.highContrast() ? 0.75f : isActive() ? 0.55f : 0.4f;
        int idleColor = GuiMotion.lerpColor(textColor & 0x00FFFFFF, textColor, opacity);
        for (int i = 0; i < modeIndicatorCount; i++) {
            boolean current = i == selectedModeIndex;
            int barX = layout.firstX() + i * layout.step();
            int bottom = layout.bottomY();
            graphics.fill(barX, bottom - (current ? 2 : 1), barX + layout.barWidth(), bottom,
                    applyAlpha(current ? selectedColor : idleColor));
        }
    }

    record ModeIndicatorLayout(int firstX, int bottomY, int barWidth, int step) {}

    private void drawContent(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                             int textColor, UiPreferences effective) {
        int iconSize = GraphicIcon.PIXELS;
        if (!iconWithLabel && icon != null && width >= iconSize && height >= iconSize) {
            drawIcon(graphics, x + (width - iconSize) / 2, y, height, textColor, effective);
            return;
        }
        var font = Minecraft.getInstance().font;
        int inset = leadingInset(width, height);
        int available = labelWidth(width, height);
        var lines = font.splitIgnoringLanguage(message, available);
        int count = Math.min(lines.size(), Math.max(1, (height - 4) / font.lineHeight));
        var language = Language.getInstance();
        var visibleLines = language.getVisualOrder(lines.subList(0, count));
        int ellipsisWidth = font.width(CommonComponents.ELLIPSIS);
        if (count < lines.size() && available >= ellipsisWidth) {
            var last = font.substrByWidth(lines.get(count - 1), available - ellipsisWidth);
            visibleLines = new ArrayList<>(visibleLines);
            visibleLines.set(count - 1, language.getVisualOrder(FormattedText.composite(last, CommonComponents.ELLIPSIS)));
        }
        int contentX = x;
        if (inset > 0 && icon != null) {
            int textWidth = 0;
            for (var line : visibleLines) textWidth = Math.max(textWidth, font.width(line));
            contentX += (width - inset - textWidth) / 2;
            drawIcon(graphics, contentX, y, height, textColor, effective);
        }
        int textY = y + (height - count * font.lineHeight) / 2;
        for (int i = 0; i < count; i++) {
            var line = visibleLines.get(i);
            int textX = inset > 0 ? contentX + inset : x + (width - font.width(line)) / 2;
            graphics.text(font, line, textX, textY + i * font.lineHeight, textColor, false);
        }
    }

    private void drawIcon(GuiGraphicsExtractor graphics, int x, int y, int height, int color, UiPreferences effective) {
        if (icon == null) return;
        int padding = (height - GraphicIcon.PIXELS) / 2;
        int travel = Math.max(0, padding - 1);
        int offset = effective.reducedMotion() || effective.highContrast() ? 0
                : UiMath.clamp(iconOffsetY, -travel, travel);
        icon.draw(graphics, x, y + padding + offset, 1, color, effective);
    }

    int labelWidth(int width, int height) {
        return Math.max(1, width - 2 * LABEL_PADDING - leadingInset(width, height));
    }

    private int leadingInset(int width, int height) {
        int inset = GraphicIcon.PIXELS + ICON_GAP;
        return iconWithLabel && icon != null && width >= 2 * LABEL_PADDING + inset + 24
                && height >= GraphicIcon.PIXELS + 2 ? inset : 0;
    }

    private int applyAlpha(int argb) {
        return argb & 0x00FFFFFF | Math.round((argb >>> 24) * GuiMotion.clamp01(alpha)) << 24;
    }

    private static final class Transition {
        private float start;
        private float target;
        private long startedAt;

        private float sample(boolean on, boolean immediate, long now) {
            float next = on ? 1f : 0f;
            if (immediate) {
                start = target = next;
                startedAt = now;
                return next;
            }
            float remaining = 1f - GuiMotion.clamp01((now - startedAt) / (float) CONTROL_TRANSITION_MS);
            float value = GuiMotion.lerp(start, target, 1f - remaining * remaining * remaining);
            if (next != target) {
                start = value;
                target = next;
                startedAt = now;
            }
            return value;
        }
    }
}
