// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * UUK center-out menu for the native GUI extractor. Register the trigger as a widget,
 * extract the menu after the host's widgets, and route its input before the host's input.
 */
public final class UiCenteredMenu {
    private static final int ROW_HEIGHT = 20;
    private static final int PADDING = 6;
    private static final GlassOptics GLASS = GlassMaterial.preset(GlassVariant.REGULAR).optics();

    private final Bounds source;
    private final Bounds target;
    private final List<Component> options;
    private final UiPreferences preferences;
    private final UiStyle style;
    private final IntConsumer onSelect;
    private final UiButton trigger;
    private UiExpansionController expansion;
    private int selected;
    private int cursor;
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private boolean captured;
    private boolean draggingOption;

    public UiCenteredMenu(Bounds source, Bounds target, List<Component> options, int selected,
                          UiPreferences preferences, UiStyle style, IntConsumer onSelect) {
        this.source = Objects.requireNonNull(source);
        this.target = Objects.requireNonNull(target);
        this.options = List.copyOf(options);
        Objects.checkIndex(selected, this.options.size());
        if (source.width() <= 0 || source.height() <= 0 || target.width() < 40
                || target.height() < menuHeight(this.options.size())) {
            throw new IllegalArgumentException("Bounds must fit the trigger and all menu rows");
        }
        this.selected = selected;
        this.preferences = Objects.requireNonNull(preferences);
        this.style = Objects.requireNonNull(style);
        this.onSelect = Objects.requireNonNull(onSelect);
        trigger = new UiButton(source.x(), source.y(), source.width(), source.height(), this.options.get(selected),
                ignored -> open(), preferences, false, style);
        trigger.setLeadingIcon(GraphicIcon.EXPAND);
        trigger.setSampleBackground(false);
    }

    public static int menuHeight(int optionCount) {
        return Math.addExact(2 * PADDING, Math.multiplyExact(ROW_HEIGHT, optionCount));
    }

    public UiButton trigger() { return trigger; }

    /** Immediately releases input and discards the current animation on layout changes or removal. */
    public void reset() {
        expansion = null;
        captured = false;
        draggingOption = false;
        trigger.setLeadingIcon(GraphicIcon.EXPAND);
    }

    private void open() {
        if (expansion != null) return;
        expansion = new UiExpansionController(source, target, UiExpansionController.Config.CENTERED_MENU,
                preferences.reducedMotion(), Util.getMillis());
        cursor = selected;
        lastMouseX = Integer.MIN_VALUE;
        lastMouseY = Integer.MIN_VALUE;
        trigger.setLeadingIcon(GraphicIcon.COLLAPSE);
        narrate();
    }

    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (expansion == null) return;
        expansion.advance(Util.getMillis());
        if (expansion.state() == UiExpansionController.State.IDLE) {
            expansion = null;
            return;
        }
        if (active() && (mouseX != lastMouseX || mouseY != lastMouseY)) {
            int row = rowAt(mouseX, mouseY);
            if (row >= 0) cursor = row;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        var outline = expansion.outline();
        // Round each pair of insets together to retain the target's center at odd GUI sizes.
        int insetX = Math.round(outline.getX() - target.x());
        int insetY = Math.round(outline.getY() - target.y());
        var area = new Bounds(target.x() + insetX, target.y() + insetY,
                target.width() - 2 * insetX, target.height() - 2 * insetY);
        var theme = UiTheme.current();
        graphics.nextStratum();
        extractSurface(graphics, theme, area);
        float opacity = UiExpansionController.menuContentOpacity(expansion.progress());
        if (opacity >= 4f / 255f) extractOptions(graphics, theme, area, opacity);
    }

    private void extractSurface(GuiGraphicsExtractor graphics, UiTheme theme, Bounds area) {
        float radius = expansion.radius();
        int edge = preferences.highContrast() ? theme.text() : theme.border();
        boolean glass = style == UiStyle.LIQUID_GLASS || style == UiStyle.DEFAULT && theme.liquidGlass();
        if (!glass) {
            RoundedCorners.draw(graphics, area.x(), area.y(), area.width(), area.height(), radius,
                    preferences.highContrast() ? 2f : 1f, edge, theme.surface() | 0xFF000000);
            return;
        }
        float bloom = UiExpansionController.menuBloom(expansion.progress(), preferences);
        var optics = new GlassOptics(GLASS.transmission(), GLASS.refraction(), GLASS.lensing(),
                GLASS.scattering(), GLASS.edgeHighlight(), GLASS.thickness(), UiExpansionController.menuBlurRadius(bloom));
        int tint = theme.surface();
        tint = preferences.useBackdropBlur() ? (tint & 0xFFFFFF) | 0xBF000000 : tint | 0xFF000000;
        LiquidGlassSurface.draw(graphics, area.x(), area.y(), area.width(), area.height(),
                radius, tint, edge, optics, preferences);
        if (bloom <= 0f) return;
        graphics.nextStratum();
        int glow = theme.accent(0xFF9ACDFF) & 0xFFFFFF;
        RoundedCorners.draw(graphics, area.x(), area.y(), area.width(), area.height(), radius, 3f,
                glow | Math.round(20f * bloom) << 24, 0);
        RoundedCorners.draw(graphics, area.x(), area.y(), area.width(), area.height(), radius, 1.5f,
                glow | Math.round(76f * bloom) << 24, 0);
    }

    private void extractOptions(GuiGraphicsExtractor graphics, UiTheme theme, Bounds area, float opacity) {
        var font = Minecraft.getInstance().font;
        int text = fade(theme.text(), opacity);
        graphics.nextStratum();
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(area.x(), area.y());
            pose.scale((float) area.width() / target.width(), (float) area.height() / target.height());
            for (int i = 0; i < options.size(); i++) {
                int y = PADDING + i * ROW_HEIGHT;
                int width = target.width() - 2 * PADDING;
                if (i == cursor) {
                    int focus = preferences.highContrast() ? theme.text() : theme.accent(0xFF9ACDFF);
                    RoundedCorners.draw(graphics, PADDING, y, width, ROW_HEIGHT, 5f,
                            1f, fade(focus, opacity), fade(theme.surface(), opacity));
                }
                var label = font.substrByWidth(options.get(i), width - 26);
                int textY = y + (ROW_HEIGHT - font.lineHeight) / 2;
                graphics.text(font, Language.getInstance().getVisualOrder(label), 2 * PADDING, textY, text, false);
                if (i == selected) {
                    GraphicIcon.SUCCESS.draw(graphics, target.width() - PADDING - 16, y + 2, 1, text, preferences);
                }
            }
        } finally {
            pose.popMatrix();
            graphics.disableScissor();
        }
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        if (expansion == null || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        captured = true;
        draggingOption = false;
        boolean inside = UiExpansionController.contains(expansion.outline(), expansion.radius(), event.x(), event.y());
        if (!inside || source.contains(event.x(), event.y())) {
            close(false);
        } else if (active()) {
            int row = rowAt(event.x(), event.y());
            if (row >= 0) {
                draggingOption = true;
                cursor = row;
            }
        }
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (!captured || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        captured = false;
        if (draggingOption && active()) {
            int row = rowAt(event.x(), event.y());
            if (row >= 0) commit(row);
        }
        draggingOption = false;
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (expansion == null) return false;
        if (event.isEscape()) {
            close(false);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            close(false);
            return false;
        }
        switch (event.key()) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END -> {
                if (active()) {
                    int next = switch (event.key()) {
                        case GLFW.GLFW_KEY_UP -> Math.max(0, cursor - 1);
                        case GLFW.GLFW_KEY_DOWN -> Math.min(options.size() - 1, cursor + 1);
                        case GLFW.GLFW_KEY_HOME -> 0;
                        default -> options.size() - 1;
                    };
                    if (cursor != next) {
                        cursor = next;
                        narrate();
                    }
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                if (active()) commit(cursor);
            }
            default -> { return false; }
        }
        return true;
    }

    private boolean active() {
        return expansion != null && expansion.state() == UiExpansionController.State.ACTIVE;
    }

    private int rowAt(double x, double y) {
        if (!target.contains(x, y) || x < target.x() + PADDING || x >= target.right() - PADDING
                || y < target.y() + PADDING) return -1;
        int row = (int) ((y - target.y() - PADDING) / ROW_HEIGHT);
        return row < options.size() ? row : -1;
    }

    private void close(boolean confirm) {
        long now = Util.getMillis();
        boolean closing = confirm ? expansion.confirm(now) : expansion.cancel(now);
        if (closing) {
            draggingOption = false;
            trigger.setLeadingIcon(GraphicIcon.EXPAND);
        }
    }

    private void commit(int index) {
        close(true);
        if (selected == index) return;
        selected = index;
        cursor = index;
        trigger.setMessage(options.get(index));
        narrate();
        onSelect.accept(index);
    }

    private void narrate() {
        Minecraft.getInstance().getNarrator().saySystemNow(options.get(cursor));
    }

    private static int fade(int color, float opacity) {
        return (color & 0xFFFFFF) | Math.round((color >>> 24) * opacity) << 24;
    }
}
