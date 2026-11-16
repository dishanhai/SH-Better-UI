// Author: dishanhai
// Original UUK implementation inspired by MuoniumPlayer RadioCardWidget's cover/title/subtitle hierarchy.
// No upstream rendering code, image, font or music-provider asset is embedded.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
    private final DarkModeTheme.Interaction motion = DarkModeTheme.Interaction.control();
    private boolean selected, playing, held;

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
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || !enabled() || !isMouseOver(x, y)) return false;
        held = true; return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 0 || !held) return false;
        if (!enabled() || !isMouseOver(x, y)) cancelInteraction();
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button != 0 || !held) return false;
        cancelInteraction();
        if (enabled() && isMouseOver(x, y)) onActivate.run();
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && held) { cancelInteraction(); return true; }
        if (!enabled() || !isFocused()) return false;
        if (key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER && key != GLFW.GLFW_KEY_SPACE) return false;
        cancelInteraction(); onActivate.run(); return true;
    }
    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        if (!enabled() || !Minecraft.getInstance().isWindowActive()) cancelInteraction();
        if (width < MIN_WIDTH || height < MIN_HEIGHT) return;
        var state = motion.sample(isHovered(), held, isFocused(), active, preferences, PageTransition.nowMs());
        if (UiTheme.current().liquidGlass()) {
            LiquidGlassTheme.button(g, getX(), getY(), width, height, state, preferences, true,
                    false, selected, 0, () -> drawContent(g));
        } else {
            DarkModeTheme.button(g, getX(), getY(), width, height, state, false, selected, 0, () -> drawContent(g));
        }
    }
    private void drawContent(GuiGraphics g) {
        var font = Minecraft.getInstance().font;
        int side = Math.min(48, height - 16), x = getX() + 8, y = getY() + (height - side) / 2;
        int ink = active ? DarkModeTheme.textColor() : DarkModeTheme.mutedTextColor();
        // Bounded vector cover; no texture loading, GPU allocation or network requests.
        DarkModeTheme.rounded(g, x, y, side, side, 5, 1, DarkModeTheme.borderColor(), DarkModeTheme.backgroundColor());
        int c = side / 2;
        DarkModeTheme.rounded(g, x + 5, y + 5, side - 10, side - 10, c, 2, DarkModeTheme.accentColor(), DarkModeTheme.surfaceColor());
        g.fill(x + c - 2, y + c - 2, x + c + 2, y + c + 2, ink);
        int textX = x + side + 8, available = Math.max(0, getX() + width - 34 - textX);
        int line = font.lineHeight;
        g.drawString(font, font.plainSubstrByWidth(getMessage().getString(), available), textX, getY() + height / 2 - line - 2, ink, false);
        g.drawString(font, font.plainSubstrByWidth(subtitle.getString(), available), textX, getY() + height / 2 + 2, active ? DarkModeTheme.secondaryTextColor() : ink, false);
        int ax = getX() + width - 23, ay = getY() + height / 2 - 5;
        if (playing) { g.fill(ax, ay, ax + 3, ay + 10, ink); g.fill(ax + 6, ay, ax + 9, ay + 10, ink); }
        else for (int i = 0; i < 9; i++) g.fill(ax + i, ay + i / 2, ax + i + 1, ay + 10 - i / 2, ink);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, description());
        if (selected) output.add(NarratedElementType.HINT, Component.translatable("sh_better_ui.media_card.selected"));
    }
}
