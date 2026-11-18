// Author: dishanhai
// Original UUK implementation inspired by MuoniumPlayer RadioCardWidget's cover/title/subtitle hierarchy.
// No upstream rendering code, image, font or music-provider asset is embedded.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.Objects;

/** A single-focus media action card. Playback and selection are confirmed by the host, never guessed on click. */
public final class UiMediaCard extends AbstractWidget {
    public static final int MIN_WIDTH = 128, MIN_HEIGHT = 48;
    private Component subtitle;
    private UiPreferences preferences;
    private final Runnable onActivate;
    private boolean selected, playing, held;
    private boolean liquidGlass;
    private final GlassMediaStyle glassStyle = new GlassMediaStyle();
    /** Explicit kit opt-in; false preserves the global theme behavior. */
    public void setLiquidGlass(boolean enabled) { liquidGlass = enabled; }
    public boolean isLiquidGlass() { return liquidGlass; }

    public UiMediaCard(Bounds area, Component title, Component subtitle, UiPreferences preferences, Runnable onActivate) {
        super(area.x(), area.y(), area.width(), area.height(), Objects.requireNonNull(title));
        if (area.width() < MIN_WIDTH || area.height() < MIN_HEIGHT)
            throw new IllegalArgumentException("Media card requires at least 128 x 48 GUI units");
        this.subtitle = Objects.requireNonNull(subtitle);
        this.preferences = Objects.requireNonNull(preferences);
        this.onActivate = Objects.requireNonNull(onActivate);
        refreshTooltip();
    }
    public void setSubtitle(Component subtitle) { this.subtitle = Objects.requireNonNull(subtitle); refreshTooltip(); }
    public Component subtitle() { return subtitle; }
    public void setPlaying(boolean playing) { this.playing = playing; refreshTooltip(); }
    public boolean isPlaying() { return playing; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isSelected() { return selected; }
    public void setPreferences(UiPreferences preferences) { this.preferences = Objects.requireNonNull(preferences); }
    @Override public void setMessage(Component title) { super.setMessage(Objects.requireNonNull(title)); refreshTooltip(); }
    private Component action() { return Component.translatable("sh_better_ui.media_card." + (playing ? "pause" : "play")); }
    private Component description() { return Component.empty().append(getMessage()).append(". ").append(subtitle == null ? Component.empty() : subtitle).append(". ").append(action()); }
    private void refreshTooltip() { setTooltip(Tooltip.create(description())); }
    private boolean enabled() { return active && visible && width >= MIN_WIDTH && height >= MIN_HEIGHT; }
    public void cancelInteraction() { held = false; }
    @Override public void setFocused(boolean focused) { super.setFocused(focused); if (!focused) cancelInteraction(); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y();
        int button = event.button();
        if (button != 0 || !enabled() || !isMouseOver(x, y)) return false;
        held = true; return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double x = event.x(), y = event.y();
        int button = event.button();
        if (button != 0 || !held) return false;
        if (!enabled() || !isMouseOver(x, y)) cancelInteraction();
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        double x = event.x(), y = event.y();
        int button = event.button();
        if (button != 0 || !held) return false;
        cancelInteraction();
        if (enabled() && isMouseOver(x, y)) onActivate.run();
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE && held) { cancelInteraction(); return true; }
        if (!enabled() || !isFocused()) return false;
        if (key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER && key != GLFW.GLFW_KEY_SPACE) return false;
        cancelInteraction(); onActivate.run(); return true;
    }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (!enabled() || !Minecraft.getInstance().isWindowActive()) cancelInteraction();
        if (width < MIN_WIDTH || height < MIN_HEIGHT) return;
        drawGlass(g);
    }
    private void drawGlass(GuiGraphicsExtractor g) {
        int fill = active ? UiTheme.current().surface() : UiTheme.current().background();
        var palette = glassStyle.palette(fill, UiTheme.current().text(),
                GuiMotion.lerpColor(UiTheme.current().text(), fill, .22f), false);
        var settings = com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings.current();
        var draw = GlassMediaStyle.surface(getX(), getY(), width, height, palette.title());
        boolean optical = (liquidGlass || UiTheme.current().liquidGlass()) && preferences.useBackdropBlur()
                && settings.glassEnabled()
                && com.dishanhai.sh_better_ui.client.blur.BackdropCapture.request(g) != null
                && com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface.drawPrepared(g,
                    getX(), getY(), width, height, 9,
                    (draw.material().foreground().fallbackArgb() & 0xffffff) | 0x40000000, 0,
                    settings.optics(draw.material().optics()), preferences, 1);
        palette = glassStyle.palette(fill, palette.title(), palette.subtitle(), optical);
        int edge = isFocused() || selected || isHovered() || preferences.highContrast() ? palette.title() : UiTheme.current().border();
        RoundedCorners.draw(g, getX(), getY(), width, height, 9, isFocused() ? 2 : 1, edge, palette.fill());
        if (held) g.fill(getX() + 12, getY() + height - 3, getX() + width - 12, getY() + height - 2, palette.title());
        drawContent(g, palette.title(), palette.subtitle());
    }
    private void drawContent(GuiGraphicsExtractor g, int ink, int secondary) {
        var font = Minecraft.getInstance().font;
        int side = Math.min(48, height - 16), x = getX() + 8, y = getY() + (height - side) / 2;
        // Bounded vector cover; no texture loading, GPU allocation or network requests.
        RoundedCorners.draw(g, x, y, side, side, 5, 1, UiTheme.current().border(), UiTheme.current().background());
        int c = side / 2;
        RoundedCorners.draw(g, x + 5, y + 5, side - 10, side - 10, c, 2, UiTheme.current().accent(), UiTheme.current().surface());
        g.fill(x + c - 2, y + c - 2, x + c + 2, y + c + 2, ink);
        int textX = x + side + 8, available = Math.max(0, getX() + width - 34 - textX);
        int line = font.lineHeight;
        drawLabel(g, font, getMessage(), available, textX, getY() + height / 2 - line - 2, ink);
        drawLabel(g, font, subtitle, available, textX, getY() + height / 2 + 2, secondary);
        int ax = getX() + width - 23, ay = getY() + height / 2 - 5;
        if (playing) { g.fill(ax, ay, ax + 3, ay + 10, ink); g.fill(ax + 6, ay, ax + 9, ay + 10, ink); }
        else for (int i = 0; i < 9; i++) g.fill(ax + i, ay + i / 2, ax + i + 1, ay + 10 - i / 2, ink);
    }
    private void drawLabel(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, Component text,
                           int available, int x, int y, int color) {
        var glyphs = net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(text, available));
        graphics.text(font, GlassTextInk.apply(glyphs, color, liquidGlass || UiTheme.current().liquidGlass()),
                x, y, color, false);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, description());
        if (selected) output.add(NarratedElementType.HINT, Component.translatable("sh_better_ui.media_card.selected"));
    }
}
