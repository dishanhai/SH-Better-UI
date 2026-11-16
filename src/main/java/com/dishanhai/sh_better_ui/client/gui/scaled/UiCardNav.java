// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Native CardNav. Bounds reserve its maximum open viewport, while the closed bar occupies 30 GUI units. */
public final class UiCardNav extends AbstractWidget {
    private final CardNavControl control;
    private final Font font;
    private BooleanSupplier inputEnabled = () -> true;
    private int layoutWidth, layoutHeight;
    private Screen captureOwner;

    public UiCardNav(Font font, Bounds area, Component name, CardNavControl.Link logo,
                     List<CardNavControl.Item> items, CardNavControl.Link cta, CardNavControl.Options options,
                     UiPreferences preferences, Consumer<Boolean> onOpenChange) {
        super(area.x(), area.y(), area.width(), area.height(), name);
        this.font = font;
        layoutWidth = area.width(); layoutHeight = area.height();
        control = new CardNavControl(font, area.width(), area.height(), name, logo, items, cta, options,
                preferences, onOpenChange);
    }

    public boolean isOpen() { return control.isOpen(); }
    public void setOpen(boolean open) { control.setOpen(open); }
    public void setOpenController(BooleanSupplier controller) { control.setOpenController(controller); }
    public void setOptions(CardNavControl.Options options) { control.setOptions(options, getWidth(), getHeight()); }
    public void setInputEnabled(BooleanSupplier enabled) { inputEnabled = Objects.requireNonNull(enabled); }
    public void cancelInteraction() {
        if (captureOwner != null) UiPointerCapture.cancel(captureOwner);
        captureOwner = null;
        control.cancel();
    }

    /** Forward every host pointer-down here before normal dispatch, including clicks outside this widget. */
    public void pointerDown(double x, double y) {
        if (isOpen() && !isMouseOver(x, y)) control.setOpen(false);
    }

    private boolean interactive() {
        return visible && active && Minecraft.getInstance().isWindowActive() && inputEnabled.getAsBoolean();
    }

    private int left() { return getX() + (getWidth() - control.width()) / 2; }

    @Override
    public boolean isMouseOver(double x, double y) {
        return interactive() && x >= left() && x < left() + control.width()
                && y >= getY() && y < getY() + control.shownHeight();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !isMouseOver(x, y)) return false;
        if (!control.press(x - left(), y - getY())) return true;
        var client = Minecraft.getInstance();
        if (client.screen == null || !UiPointerCapture.start(client.screen, button, new UiPointerCapture.Handler() {
            @Override public void drag(double x, double y) {}
            @Override public void release(double x, double y) {
                captureOwner = null;
                if (interactive()) control.release(x - left(), y - getY());
                else control.cancel();
            }
            @Override public void cancel() { captureOwner = null; control.cancel(); }
        })) { control.cancel(); return false; }
        captureOwner = client.screen;
        playDownSound(client.getSoundManager());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!isFocused() || !interactive()) return false;
        boolean handled = switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> control.dismiss();
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> control.moveFocus(-1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> control.moveFocus(1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> control.activate();
            default -> false;
        };
        if (handled) Minecraft.getInstance().getNarrator().say(control.narration());
        return handled;
    }

    // Both signatures keep the shared adapter compatible with Forge 1.20 and NeoForge 1.21.
    public boolean mouseScrolled(double x, double y, double amount) {
        return isMouseOver(x, y) && control.scroll(amount);
    }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return mouseScrolled(x, y, vertical);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) cancelInteraction();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, control.narration());
        output.add(NarratedElementType.USAGE, CardNavControl.usage());
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = PageTransition.nowMs();
        if (!interactive()) cancelInteraction();
        if (layoutWidth != getWidth() || layoutHeight != getHeight()) {
            control.resize(getWidth(), getHeight());
            layoutWidth = getWidth(); layoutHeight = getHeight();
        }
        control.update(mouseX - left(), mouseY - getY(), now, interactive());
        int x = left(), y = getY(), w = control.width(), h = control.shownHeight();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        int inset = control.introInset(now);
        graphics.enableScissor(inset, 0, w - inset, h + 3);
        try {
            RoundedCorners.draw(graphics, 0, 3, w, h, 9, 0, 0, 0x30000000);
            boolean glass = control.glass() && LiquidGlassTheme.clearSurface(graphics,
                    GlassRegion.rounded(0, 0, w, h, 9), new GlassDraw.ForegroundBounds(4, 4, w - 8, h - 8), control.ink());
            RoundedCorners.draw(graphics, 0, 0, w, h, 9, 1,
                    CardNavControl.alpha(control.ink(), .12), CardNavControl.alpha(control.panelColor(), glass ? .72 : 1));
            control.draw(new CardNavControl.Painter() {
                private float opacity = 1, blur;
                @Override public void icon(GraphicIcon icon, int x, int y, int color) {
                    pose.pushPose();
                    try {
                        pose.translate(x, y, 0);
                        float scale = 12f / GraphicIcon.PIXELS;
                        pose.scale(scale, scale, 1);
                        icon.draw(graphics, 0, 0, 1, CardNavControl.alpha(color, opacity));
                    } finally { pose.popPose(); }
                }
                @Override public void shape(float x, float y, float w, float h, float radius, int edge, int fill) {
                    RoundedCorners.draw(graphics, x, y, w, h, radius, .5f,
                            CardNavControl.alpha(edge, opacity), CardNavControl.alpha(fill, opacity));
                }
                @Override public void text(FormattedCharSequence line, int x, int y, int color) {
                    int ink = CardNavControl.alpha(color, opacity);
                    if ((ink >>> 24) < 4) return;
                    // Native soft fade: small translucent glyph taps approximate the source's CSS blur.
                    if (blur > .1f) {
                        int soft = CardNavControl.alpha(ink, .12);
                        int radius = Math.max(1, Math.round(blur));
                        if ((soft >>> 24) >= 4) {
                            graphics.drawString(font, line, x - radius, y, soft, false);
                            graphics.drawString(font, line, x + radius, y, soft, false);
                            graphics.drawString(font, line, x, y - radius, soft, false);
                            graphics.drawString(font, line, x, y + radius, soft, false);
                        }
                    }
                    graphics.drawString(font, line, x, y, ink, false);
                }
                @Override public void clip(int x, int y, int right, int bottom) { graphics.enableScissor(x, y, right, bottom); }
                @Override public void unclip() { graphics.disableScissor(); }
                @Override public void begin(float x, float y, float sx, float sy, float opacity, float blur) {
                    pose.pushPose();
                    pose.translate(x, y, 0);
                    pose.scale(sx, sy, 1);
                    this.opacity = opacity;
                    this.blur = blur;
                }
                @Override public void end() { pose.popPose(); opacity = 1; blur = 0; }
            }, isFocused(), now);
        } finally { graphics.disableScissor(); pose.popPose(); }
    }
}
