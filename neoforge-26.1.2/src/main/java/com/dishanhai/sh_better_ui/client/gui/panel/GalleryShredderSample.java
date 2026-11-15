// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.PointerDrag;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

final class GalleryShredderSample extends GallerySampleScreen {
    private static final int PRIMARY = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    private final ShredderDemo demo = new ShredderDemo();
    private final PointerDrag pointer = new PointerDrag();
    private boolean swallowRelease;
    private UiButton undo;

    GalleryShredderSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected void initSample() {
        int width = Math.min(320, body.width());
        int x = body.x() + (body.width() - width) / 2;
        int height = body.height() - 26;
        if (height < ShredderDemo.MIN_HEIGHT || width < 100) return;
        Bounds area = new Bounds(x, body.y(), width, Math.min(230, height));
        demo.layout(area);
        initialFocus = addRenderableWidget(new PaperStack(area));
        undo = addButton(new Bounds(x, area.bottom() + 4, 100, 22), ShredderDemo.text("undo"), ignored -> undo(), false);
        undo.setLeadingIcon(GraphicIcon.UNDO);
        undo.active = demo.canUndo();
    }

    @Override
    protected void beforeSampleInit() {
        undo = null;
    }

    private void undo() {
        if (demo.undo()) {
            undo.active = false;
            announce();
            setInitialFocus(initialFocus);
        }
    }

    private void announce() { minecraft.getNarrator().saySystemNow(demo.status()); }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (pointer.active()) return true;
        if (event.button() == PRIMARY) swallowRelease = false;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() != PRIMARY || !pointer.active() && !swallowRelease) return super.mouseDragged(event, dx, dy);
        if (!minecraft.isWindowActive()) cancelDrag();
        if (pointer.move(event.button(), event.x(), event.y()) != null) demo.drag(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != PRIMARY || !pointer.active() && !swallowRelease) return super.mouseReleased(event);
        setDragging(false);
        if (minecraft.isWindowActive() && pointer.release(event.button(), event.x(), event.y()) != null) {
            if (demo.release(event.x(), event.y(), Util.getMillis(), preferences.reducedMotion())) announce();
        } else {
            cancelDrag();
        }
        swallowRelease = false;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && pointer.active()) {
            cancelDrag();
            return true;
        }
        return super.keyPressed(event);
    }

    private void cancelDrag() {
        if (pointer.active()) {
            pointer.cancel();
            setDragging(false);
            swallowRelease = true;
        }
        demo.cancelDrag();
    }

    @Override
    protected void cancelInteraction() {
        cancelDrag();
        demo.cancel();
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (initialFocus == null) {
            PanelText.drawLine(graphics, font, Component.translatable("sh_better_ui.panel.compact"), body.x(),
                    body.y(), body.width(), theme.text(), mouseX, mouseY);
        }
    }

    private final class PaperStack extends AbstractWidget {
        private int shredKey = -1;
        PaperStack(Bounds area) {
            super(area.x(), area.y(), area.width(), area.height(), Component.translatable(ShredderDemo.TITLE));
            setTooltip(Tooltip.create(ShredderDemo.text("keys")));
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != PRIMARY || !minecraft.isWindowActive() || !demo.press(event.x(), event.y())) return false;
            if (pointer.begin(event.button(), event.x(), event.y())) return true;
            demo.cancelDrag();
            return false;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (!isFocused() || !minecraft.isWindowActive() || pointer.active()) return false;
            int key = event.key();
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                int direction = key == GLFW.GLFW_KEY_UP ? -1 : 1;
                if (event.hasShiftDown()) {
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
            if (key == GLFW.GLFW_KEY_Z && event.hasControlDown()) {
                undo();
                return true;
            }
            return false;
        }

        @Override
        public boolean keyReleased(KeyEvent event) {
            if (event.key() != shredKey) return false;
            shredKey = -1;
            if (isFocused() && minecraft.isWindowActive()
                    && demo.shred(Util.getMillis(), preferences.reducedMotion())) announce();
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
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            long now = Util.getMillis();
            if (demo.advance(now, preferences.reducedMotion())) announce();
            undo.active = demo.canUndo();
            int text = theme.text();
            var palette = new ShredderDemo.Palette(theme.background() | 0xFF000000, theme.surface() | 0xFF000000,
                    preferences.highContrast() ? text : theme.border(), text, preferences.highContrast() ? text : theme.accent());
            demo.paint(new ShredderDemo.Painter() {
                @Override public void fill(int x, int y, int right, int bottom, int color) {
                    graphics.fill(x, y, right, bottom, color);
                }
                @Override public void box(int x, int y, int w, int h, int radius, int edge, int fill) {
                    RoundedCorners.draw(graphics, x, y, w, h, radius, 1, edge, fill);
                }
                @Override public void text(Component message, int x, int y, int width, int color) {
                    PanelText.drawLine(graphics, font, message, x, y, width, color, -1, -1);
                }
                @Override public void clip(int x, int y, int right, int bottom) {
                    graphics.enableScissor(x, y, right, bottom);
                }
                @Override public void unclip() { graphics.disableScissor(); }
            }, palette, isFocused(), mouseX, mouseY, now);
        }
    }
}
