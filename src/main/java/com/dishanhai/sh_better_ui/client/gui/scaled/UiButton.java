package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.text.TextLayoutMotion;
import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.internal.NativeTextReadability;
import com.dishanhai.sh_better_ui.theme.ThemeBinding;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;

/** 山海：保留原生输入、焦点、功能提示和旁白的共用按钮。 */
public final class UiButton extends Button {
    private static final int ICON_GAP = 4;
    private static final int LABEL_PADDING = 6;
    private static final int MODE_INDICATOR_WIDTH = 8;
    private static final int MODE_INDICATOR_GAP = 3;
    private static final int MODE_INDICATOR_RESERVE = 4;
    private UiPreferences preferences;
    private final boolean primary;
    private final UiStyle style;
    private final DarkModeTheme.Interaction motion = DarkModeTheme.Interaction.control();
    private boolean justPressed;
    private boolean held;
    private boolean selected;
    private long selectedAt;
    private int modeIndicatorCount;
    private int selectedModeIndex = -1;
    private Component preview;
    private GraphicIcon icon;
    private boolean iconWithLabel;
    private int iconOffsetY;
    private boolean sampleBackground = true;
    private float backgroundBlurRadius = LiquidGlassTheme.DEFAULT_BUTTON_BLUR_RADIUS;
    private ThemeButtonStyle themeBinding;
    private NativeTextReadability.Label readableLabel;
    private final TextLayoutMotion labelMotion = new TextLayoutMotion();

    public UiButton(int x, int y, int width, int height, Component label, OnPress onPress) {
        this(x, y, width, height, label, onPress, UiPreferences.configured(), false);
    }

    /** Binds the native page's positioned label to its owned text scope. */
    public void bindTextReadability(NativeTextReadability.Label label) {
        readableLabel = label;
        labelMotion.reset();
    }

    /** Applies the owned text scope's policy to this label under the supplied effective GUI clip. */
    public void bindTextReadability(ModuleContext module, ScopeHandle scope, UiId textId, Supplier<Bounds> clip) {
        var mount = new NativeTextReadability.Mount(module, scope);
        mount.service().policy(module, scope);
        bindTextReadability(new NativeTextReadability.Label(mount, Objects.requireNonNull(textId, "textId"),
                NativeTextReadability.Options.INHERIT, Objects.requireNonNull(clip, "clip")));
    }

    public UiButton(int x, int y, int width, int height, Component label, OnPress onPress,
                    UiPreferences preferences, boolean primary) {
        this(x, y, width, height, label, onPress, preferences, primary, UiStyle.DEFAULT);
    }

    public UiButton(int x, int y, int width, int height, Component label, OnPress onPress,
                    UiPreferences preferences, boolean primary, UiStyle style) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.preferences = preferences;
        this.primary = primary;
        this.style = Objects.requireNonNull(style);
        PlatformUiApi.setTooltipDelay(this, preferences.feedback().tooltipDelayMs());
        setPreview(null);
    }

    /** 更新正在显示的控件，不重建按钮或移动焦点。 */
    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
        PlatformUiApi.setTooltipDelay(this, preferences.feedback().tooltipDelayMs());
    }

    /** Whether this material exposes the complete dynamic button style. */
    public boolean supportsThemeBinding() { return true; }

    /** Binds this button to a scope using semantic background and text roles. */
    public void bindTheme(ThemeScope scope) {
        bindTheme(scope, ThemeBinding.of(primary ? ThemeTokens.PRIMARY : ThemeTokens.SURFACE),
                ThemeBinding.of(primary ? ThemeTokens.ON_PRIMARY : ThemeTokens.TEXT));
    }

    /** Replaces this button's bindings. Closing its scope automatically releases the properties. */
    public void bindTheme(ThemeScope scope, ThemeBinding<ColorValue> background, ThemeBinding<ColorValue> textColor) {
        var replacement = new ThemeButtonStyle(scope, style, background, textColor);
        unbindTheme();
        themeBinding = replacement;
    }

    public boolean hasThemeBinding() { return themeBinding != null && themeBinding.active(); }

    /** Releases a binding before a standalone widget is removed or recycled. */
    public void unbindTheme() {
        if (themeBinding != null) themeBinding.close();
        themeBinding = null;
    }

    ThemeButtonStyle.Paint themePaint(DarkModeTheme.State state, boolean pressed) {
        return hasThemeBinding() ? themeBinding.sample(isHovered(), pressed, state.focused(), state.enabled(),
                state.highContrast(), selected, state.hover(), state.press()) : null;
    }

    /** 位于已有玻璃工具列内时可跳过液态玻璃的重复背景取样。 */
    public void setSampleBackground(boolean sampleBackground) {
        this.sampleBackground = sampleBackground;
    }

    /** 液态玻璃背景模糊半径，单位为 GUI 单位。0 关闭，最大 32；负数与非有限值无效。 */
    public void setBackgroundBlurRadius(float radius) {
        backgroundBlurRadius = LiquidGlassTheme.normalizeBlurRadius(radius);
    }

    public float getBackgroundBlurRadius() {
        return backgroundBlurRadius;
    }

    /** 本地化功能提示；null 时显示完整按钮名称，键盘焦点沿用原生 tooltip。 */
    public void setPreview(Component preview) {
        this.preview = preview;
        setTooltip(Tooltip.create(preview == null ? getMessage() : preview));
    }

    /** 设置居中图标，原名称仍用于提示和旁白。传 null 恢复文字绘制。 */
    public void setIcon(GraphicIcon icon) {
        this.icon = icon;
        iconWithLabel = false;
    }

    /** 在可见文字左侧显示 16×16 图标。空间不足时保留文字。 */
    public void setLeadingIcon(GraphicIcon icon) {
        this.icon = icon;
        iconWithLabel = true;
    }

    /** 仅移动图标，单位为 GUI 像素。绘制时限制在按钮内；文字、焦点和点击范围保持原位。 */
    public void setIconOffsetY(int offset) {
        iconOffsetY = UiMath.clamp(offset, -2, 2);
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        if (preview == null) setTooltip(Tooltip.create(message));
    }

    /** 由消费画面的实际状态设置，点击不会自行宣告服务器状态已改变。 */
    public void setSelected(boolean selected) {
        if (this.selected == selected) return;
        this.selected = selected;
        selectedAt = PageTransition.nowMs();
    }

    public boolean isSelected() { return selected; }

    /**
     * 在按钮内容下方显示每个模式的短条，并高亮已确认的零基索引。
     * 每条及条间至少需要一个 GUI 单位。空间不足时保留按钮的常规绘制。
     */
    public void setModeIndicators(int count, int selectedIndex) {
        if (count < 1 || selectedIndex < 0 || selectedIndex >= count) throw new IllegalArgumentException();
        modeIndicatorCount = count;
        selectedModeIndex = selectedIndex;
    }

    /** 恢复常规按钮的底边绘制。 */
    public void clearModeIndicators() {
        modeIndicatorCount = 0;
        selectedModeIndex = -1;
    }

    public int getModeIndicatorCount() {
        return modeIndicatorCount;
    }

    /** 关闭模式条时返回 -1。 */
    public int getSelectedModeIndex() {
        return selectedModeIndex;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        super.updateWidgetNarration(output);
        if (selected) output.add(NarratedElementType.HINT, Component.translatable("sh_better_ui.control.selected"));
    }

    @Override
    public void onPress() {
        justPressed = true;
        super.onPress();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (UiLocalModal.hidesSource(this)) return false;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled) UiLocalModal.captureSourcePress(this, button);
        return handled;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (UiLocalModal.hidesSource(this)) {
            isHovered = false;
            justPressed = false;
            return;
        }
        if (isHovered && UiLocalModal.coversMouse(mouseX, mouseY)) isHovered = false;
        if (isCapsule() && UiOverlayHost.hidesSource(this)) return;
        long now = PageTransition.nowMs();
        boolean pressed = justPressed || held;
        var effective = effectivePreferences();
        var state = sampleMotion(now, effective);
        var paint = themePaint(state, pressed);
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        boolean network = style == UiStyle.NETWORK;
        if (network && (width <= 2 || height <= 2)) return;
        var modeLayout = modeIndicatorLayout();
        UiStyle renderStyle = style == UiStyle.DEFAULT && (paint == null ? UiTheme.current().liquidGlass() : paint.glass())
                ? UiStyle.LIQUID_GLASS : style;
        int textColor = paint != null ? paint.text() : switch (renderStyle) {
            case DEFAULT -> state.controlText();
            case LIQUID_GLASS -> LiquidGlassTheme.controlText(state.enabled());
            case NETWORK -> NetworkTheme.controlText(state, selected);
        };
        int indicator = modeLayout == null ? 0 : paint == null ? materialAccent(renderStyle, state) : paint.accent();
        Runnable content = () -> drawContent(graphics, x, y, width, height, textColor, state, modeLayout, indicator, effective, now);
        if (network) {
            NetworkTheme.drawTab(graphics, x, y, width, height, state, selected, paint);
            graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
            try {
                content.run();
            } finally {
                graphics.disableScissor();
            }
            return;
        }
        float emphasis = selectionEmphasis(now, effective);
        boolean showSelectionMarker = modeLayout == null;
        if (renderStyle == UiStyle.LIQUID_GLASS) {
            LiquidGlassTheme.button(graphics, x, y, width, height, state, effective, sampleBackground,
                    backgroundBlurRadius, primary, selected, emphasis, showSelectionMarker, paint, content);
        } else {
            DarkModeTheme.button(graphics, x, y, width, height, state, primary, selected, emphasis,
                    showSelectionMarker, paint, content);
        }
    }

    boolean isCapsule() {
        return style == UiStyle.LIQUID_GLASS;
    }

    int expansionFill(long now) {
        boolean pressed = justPressed || held;
        var effective = effectivePreferences();
        var state = sampleMotion(now, effective);
        var paint = themePaint(state, pressed);
        if (paint != null) return paint.fill();
        float emphasis = selectionEmphasis(now, effective);
        return isCapsule() ? LiquidGlassTheme.buttonFill(state, primary, selected, emphasis)
                : state.buttonFill(primary, selected, emphasis);
    }

    FluidMorph renderMorph(GuiGraphics graphics, GuiRect target, UiPreferences panelPreferences,
                           float progress, long now) {
        boolean pressed = justPressed || held;
        var effective = effectivePreferences();
        var state = sampleMotion(now, effective);
        var paint = themePaint(state, pressed);
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        float scale = state.buttonScale();
        if (Math.abs(1f - scale) < 0.001f) scale = 1f;
        float centerX = x + width * 0.5f, centerY = y + height * 0.5f;
        var capsule = new GuiRect(centerX - width * scale * 0.5f, centerY - height * scale * 0.5f,
                width * scale, height * scale);
        var frame = paint == null ? FluidMorph.between(capsule, target, LiquidGlassTheme.POPOVER_RADIUS, progress)
                : FluidMorph.between(capsule, target, paint.radius(), LiquidGlassTheme.POPOVER_RADIUS, progress);
        var panel = hasThemeBinding() ? themeBinding.preferences(panelPreferences) : panelPreferences;
        LiquidGlassTheme.morphPopover(graphics, frame, panel, state, primary, selected,
                selectionEmphasis(now, effective), sampleBackground, backgroundBlurRadius, progress, paint);
        float labelOpacity = 1f - GuiMotion.clamp01(progress / 0.36f);
        if (labelOpacity > 0f) {
            GuiRect rect = frame.rect();
            float labelScale = Math.min(scale, Math.min(rect.getW() / width, rect.getH() / height));
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(rect.getX() + rect.getW() * 0.5f - centerX,
                        rect.getY() + rect.getH() * 0.5f - centerY, 0f);
                var modeLayout = modeIndicatorLayout();
                GuiRenderUtil.withButtonMotion(graphics, x, y, width, height, labelScale, 0f,
                        () -> GuiRenderUtil.withAttentionContent(graphics, labelOpacity, 0f, () -> {
                            if (selected && modeLayout == null) {
                                int marker = paint == null ? (state.enabled() ? LiquidGlassTheme.accentColor()
                                        : LiquidGlassTheme.mutedTextColor()) : (state.enabled() ? paint.accent() : paint.text());
                                LiquidGlassTheme.drawSelectionMarker(graphics, x, y, width, height, marker);
                            }
                            int text = paint == null ? LiquidGlassTheme.controlText(state.enabled()) : paint.text();
                            drawContent(graphics, x, y, width, height, text, state, modeLayout,
                                    paint == null ? LiquidGlassTheme.accentColor() : paint.accent(), effective, now);
                        }));
            } finally {
                graphics.pose().popPose();
            }
        }
        return frame;
    }

    private UiPreferences effectivePreferences() {
        return hasThemeBinding() ? themeBinding.preferences(preferences) : preferences;
    }

    private DarkModeTheme.State sampleMotion(long now, UiPreferences effective) {
        var state = motion.sample(isHovered(), justPressed || held, isFocused(), active, effective, now);
        justPressed = false;
        return state;
    }

    /** 山海：聚合控件在提交点击前同步按住状态，不触发操作回调。 */
    void setHeld(boolean held) {
        this.held = held;
    }

    private float selectionEmphasis(long now, UiPreferences effective) {
        return selected && !effective.reducedMotion() && effective.feedback().selectionPulse()
                ? 1f - GuiMotion.clamp01((now - selectedAt) / 360f) : 0f;
    }

    private void drawContent(GuiGraphics graphics, int x, int y, int width, int height, int textColor,
                             DarkModeTheme.State state, ModeIndicatorLayout modeLayout, int indicator,
                             UiPreferences effective, long now) {
        int contentHeight = modeLayout == null ? height : height - MODE_INDICATOR_RESERVE;
        drawContent(graphics, x, y, width, contentHeight, textColor, effective, now);
        if (modeLayout != null) drawModeIndicators(graphics, modeLayout, state, textColor, indicator);
    }

    ModeIndicatorLayout modeIndicatorLayout() {
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

    private static int materialAccent(UiStyle renderStyle, DarkModeTheme.State state) {
        return switch (renderStyle) {
            case DEFAULT -> state.focusColor();
            case LIQUID_GLASS -> LiquidGlassTheme.accentColor();
            case NETWORK -> NetworkTheme.outline(true);
        };
    }

    private void drawModeIndicators(GuiGraphics graphics, ModeIndicatorLayout layout,
                                    DarkModeTheme.State state, int textColor, int accent) {
        int selectedColor = state.enabled() && !state.highContrast() ? accent : textColor;
        float opacity = state.highContrast() ? 0.75f : state.enabled() ? 0.55f : 0.4f;
        int idleColor = GuiMotion.lerpColor(textColor & 0x00FFFFFF, textColor, opacity);
        for (int i = 0; i < modeIndicatorCount; i++) {
            boolean current = i == selectedModeIndex;
            int barX = layout.firstX() + i * layout.step();
            int bottom = layout.bottomY();
            graphics.fill(barX, bottom - (current ? 2 : 1), barX + layout.barWidth(), bottom,
                    current ? selectedColor : idleColor);
        }
    }

    record ModeIndicatorLayout(int firstX, int bottomY, int barWidth, int step) {}

    private void drawContent(GuiGraphics graphics, int x, int y, int width, int height, int textColor,
                             UiPreferences effective, long now) {
        int iconSize = GraphicIcon.PIXELS;
        if (!iconWithLabel && icon != null && width >= iconSize && height >= iconSize) {
            labelMotion.reset();
            drawIcon(graphics, x + (width - iconSize) / 2, y, height, textColor, effective);
            return;
        }
        var font = Minecraft.getInstance().font;
        int inset = leadingInset(width, height);
        var lines = font.split(getMessage(), labelWidth(width, height));
        int count = Math.min(lines.size(), Math.max(1, (height - 4) / font.lineHeight));
        int contentX = x;
        if (inset > 0) {
            int textWidth = 0;
            for (int i = 0; i < count; i++) textWidth = Math.max(textWidth, font.width(lines.get(i)));
            contentX += (width - inset - textWidth) / 2;
            drawIcon(graphics, contentX, y, height, textColor, effective);
        }
        int textY = y + (height - count * font.lineHeight) / 2;
        var target = readableLabel == null ? new ArrayList<TextLayoutMotion.Line>(count) : null;
        for (int i = 0; i < count; i++) {
            var line = lines.get(i);
            int textX = inset > 0 ? contentX + inset : x + (width - font.width(line)) / 2;
            int lineY = textY + i * font.lineHeight;
            if (target != null) {
                target.add(new TextLayoutMotion.Line(line, textX - x, lineY - y, 1));
            } else if (!readableLabel.draw(graphics, font, line, textX, lineY, textColor,
                    false, i, isHovered, isFocused())) {
                UiFormattedText.draw(graphics, font, line, textX, lineY, textColor, false, effective);
            }
        }
        if (target != null) {
            var frame = labelMotion.update(getMessage(), target, now, effective.reducedMotion() || effective.highContrast());
            int left = inset > 0 ? contentX + inset : x + LABEL_PADDING;
            var clip = new Bounds(left, y, Math.max(0, x + width - LABEL_PADDING - left), height);
            TextLayoutMotion.draw(graphics, font, frame, x, y, clip, textColor, false, effective);
        }
    }

    private void drawIcon(GuiGraphics graphics, int x, int y, int height, int color, UiPreferences effective) {
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
}
