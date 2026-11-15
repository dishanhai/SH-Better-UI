// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class ShredderGalleryPreview implements PreviewScene {
    static final int PREFERRED_HEIGHT = ShredderDemo.MIN_HEIGHT + 26;

    private final ShredderDemo demo = new ShredderDemo();
    private PreviewContext host;
    private UiButton undo;
    private PaperStack stack;

    @Override
    public void layout(PreviewContext host) {
        suspend();
        this.host = host;
        Bounds viewport = host.viewport();
        if (viewport.height() < PREFERRED_HEIGHT || viewport.width() < 100) return;
        int width = Math.min(320, viewport.width());
        int x = viewport.x() + (viewport.width() - width) / 2;
        int height = Math.min(230, viewport.height() - 26);
        Bounds area = new Bounds(x, viewport.y(), width, height);
        demo.layout(area);
        stack = host.add(new PaperStack(area));
        undo = GalleryPreviewLayout.addButton(host, new Bounds(x, area.bottom() + 4, Math.min(width, 100), 22),
                ShredderDemo.text("undo"), GraphicIcon.UNDO, this::undo);
        undo.active = demo.canUndo();
    }

    private void undo() {
        if (!demo.undo()) return;
        undo.active = false;
        announce();
    }

    private void announce() {
        Minecraft.getInstance().getNarrator().say(demo.status());
    }

    private void cancelDrag() {
        if (host != null && demo.dragging()) UiPointerCapture.cancel(host.screen());
        demo.cancelDrag();
    }

    @Override
    public void suspend() {
        cancelDrag();
        demo.cancel();
        host = null;
        undo = null;
        stack = null;
    }

    private final class PaperStack extends AbstractWidget {
        private int shredKey = -1;
        PaperStack(Bounds area) {
            super(area.x(), area.y(), area.width(), area.height(), Component.translatable(ShredderDemo.TITLE));
            setTooltip(Tooltip.create(ShredderDemo.text("keys")));
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !host.interactive() || !demo.press(x, y)) return false;
            if (UiPointerCapture.start(host.screen(), button, new UiPointerCapture.Handler() {
                @Override public void drag(double x, double y) { demo.drag(x, y); }

                @Override public void release(double x, double y) {
                    if (demo.release(x, y, PageTransition.nowMs(), host.preferences().reducedMotion())) announce();
                }

                @Override public void cancel() { demo.cancelDrag(); }
            })) return true;
            demo.cancelDrag();
            return false;
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (!isFocused() || !host.interactive() || demo.dragging()) return false;
            boolean arrow = key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN;
            if (arrow) {
                int direction = key == GLFW.GLFW_KEY_UP ? -1 : 1;
                if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                    if (demo.move(direction)) announce();
                } else {
                    demo.select(direction);
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                if (shredKey < 0) shredKey = key;
                return true;
            }
            if (key == GLFW.GLFW_KEY_Z && (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) {
                undo();
                return true;
            }
            return false;
        }

        @Override
        public boolean keyReleased(int key, int scanCode, int modifiers) {
            if (key != shredKey) return false;
            shredKey = -1;
            if (isFocused() && host.interactive()
                    && demo.shred(PageTransition.nowMs(), host.preferences().reducedMotion())) announce();
            return true;
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (!focused) {
                shredKey = -1;
                cancelDrag();
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, demo.narration());
            output.add(NarratedElementType.USAGE, ShredderDemo.text("keys"));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            long now = PageTransition.nowMs();
            if (demo.advance(now, host.preferences().reducedMotion()) && host.interactive()) announce();
            undo.active = demo.canUndo();
            if (!undo.active && undo.isFocused() && host.interactive()) host.screen().setFocused(stack);
            int text = DarkModeTheme.textColor();
            boolean contrast = host.preferences().highContrast();
            var palette = new ShredderDemo.Palette(DarkModeTheme.backgroundColor() | 0xFF000000,
                    DarkModeTheme.surfaceColor() | 0xFF000000, contrast ? text : DarkModeTheme.borderColor(),
                    text, contrast ? text : DarkModeTheme.accentColor());
            var font = host.font();
            demo.paint(new ShredderDemo.Painter() {
                @Override public void fill(int x, int y, int right, int bottom, int color) {
                    graphics.fill(x, y, right, bottom, color);
                }
                @Override public void box(int x, int y, int w, int h, int radius, int edge, int fill) {
                    RoundedCorners.draw(graphics, x, y, w, h, radius, 1, edge, fill);
                }
                @Override public void text(Component message, int x, int y, int width, int color) {
                    var visible = Language.getInstance().getVisualOrder(font.substrByWidth(message, width));
                    graphics.drawString(font, visible, x, y, color, false);
                }
                @Override public void clip(int x, int y, int right, int bottom) {
                    graphics.enableScissor(x, y, right, bottom);
                }
                @Override public void unclip() { graphics.disableScissor(); }
            }, palette, isFocused(), mouseX, mouseY, now);
        }
    }
}
