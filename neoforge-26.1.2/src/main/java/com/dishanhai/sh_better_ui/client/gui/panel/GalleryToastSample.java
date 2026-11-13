// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

final class GalleryToastSample extends GallerySampleScreen {
    private static final int ROW = 22;
    private static final int GAP = 5;
    private static final int SWIPE = 12;
    private static final long DISPLAY_MS = 5000;
    private static final String GALLERY = "sh_better_ui.panel.developer.gallery.";
    private static final SystemToast.SystemToastId CLASSIC = new SystemToast.SystemToastId();

    private enum Kind { FLOATING, INLINE }

    private final ArrayDeque<Kind> queue = new ArrayDeque<>();
    private Kind current;
    private Kind undo;
    private long remaining;
    private long lastUpdate;
    private long undoUntil;
    private boolean hovered;
    private boolean draggingToast;
    private double dragStart;
    private int dragOffset;
    private Bounds noticeArea;
    private UiButton notice;
    private UiButton close;
    private UiButton undoButton;
    private Component status;

    GalleryToastSample(DeveloperGalleryScreen parent, String id) { super(parent, id); }

    @Override
    protected void initSample() {
        int cell = (body.width() - 2 * GAP) / 3;
        initialFocus = addButton(new Bounds(body.x(), body.y(), cell, ROW), label("toast_classic"), ignored -> classic(), true);
        addButton(new Bounds(body.x() + cell + GAP, body.y(), cell, ROW), label("toast_floating"),
                ignored -> enqueue(Kind.FLOATING), false);
        addButton(new Bounds(body.x() + 2 * (cell + GAP), body.y(), body.width() - 2 * (cell + GAP), ROW),
                label("toast_inline"), ignored -> enqueue(Kind.INLINE), false);
        noticeArea = new Bounds(body.x(), body.y() + ROW + GAP, body.width(), ROW);
        notice = addButton(new Bounds(noticeArea.x(), noticeArea.y(), noticeArea.width() - ROW - GAP, ROW),
                label("toast_acknowledge"), ignored -> acknowledge(), true);
        notice.setLeadingIcon(GraphicIcon.INFO);
        close = addButton(new Bounds(noticeArea.right() - ROW, noticeArea.y(), ROW, ROW),
                Component.translatable("sh_better_ui.swipe_toast.close"), ignored -> dismiss(), false);
        close.setIcon(GraphicIcon.COLLAPSE);
        int undoWidth = Math.min(96, (body.width() - GAP) / 2);
        undoButton = addButton(new Bounds(body.right() - undoWidth, body.bottom() + GAP, undoWidth, ROW),
                text("undo"), ignored -> undoDismissal(), false);
        undoButton.setLeadingIcon(GraphicIcon.UNDO);
        lastUpdate = Util.getMillis();
        updateWidgets();
    }

    private void classic() {
        SystemToast.add(ControlPanelPlatform.toasts(minecraft), CLASSIC, titleText(), description());
        report(titleText());
    }

    private void enqueue(Kind kind) {
        if (queue.size() >= 6) return;
        queue.addLast(kind);
        if (current == null) showNext();
        else report(text("queued", queue.size()));
    }

    private void showNext() {
        current = queue.pollFirst();
        remaining = DISPLAY_MS;
        lastUpdate = Util.getMillis();
        cancelInteraction();
        updateWidgets();
        if (current != null) report(titleText());
    }

    private void acknowledge() {
        showNext();
        report(label("toast_acknowledged"));
    }

    private void dismiss() {
        undo = current;
        undoUntil = Util.getMillis() + DISPLAY_MS;
        showNext();
        report(text("dismissed"));
        if (current == null) setInitialFocus(undoButton);
    }

    private void undoDismissal() {
        if (undo == null) return;
        if (current != null) queue.addFirst(current);
        queue.addFirst(undo);
        undo = null;
        showNext();
        setInitialFocus(notice);
    }

    private void report(Component message) {
        status = message;
        triggerImmediateNarration(true);
    }

    private void updateWidgets() {
        if (notice == null) return;
        boolean shown = current != null;
        notice.visible = notice.active = shown;
        close.visible = close.active = shown;
        undoButton.visible = undoButton.active = undo != null;
        if (shown) {
            notice.setMessage(text("notice", titleText(), label("toast_acknowledge")));
            notice.setPreview(current == Kind.FLOATING ? text("swipe") : description());
            moveNotice(0);
        }
        restoreVisibleFocus();
    }

    private void restoreVisibleFocus() {
        if (getFocused() instanceof UiButton button && !button.visible) setInitialFocus();
    }

    @Override
    protected void tickSample() {
        if (compact) return;
        long now = Util.getMillis();
        if (undo != null && now >= undoUntil) {
            undo = null;
            updateWidgets();
        }
        if (current != null && !draggingToast && !hovered && getFocused() != notice && getFocused() != close) {
            remaining -= now - lastUpdate;
            if (remaining <= 0) showNext();
        }
        lastUpdate = now;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!compact && current == Kind.FLOATING && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && notice.isMouseOver(event.x(), event.y())) {
            draggingToast = true;
            dragStart = event.y();
            setInitialFocus(notice);
            return true;
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        restoreVisibleFocus();
        return handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!draggingToast) return super.mouseDragged(event, deltaX, deltaY);
        dragOffset = Math.clamp((int) Math.round(event.y() - dragStart), 0, 20);
        if (!preferences.reducedMotion()) moveNotice(Math.min(dragOffset, body.bottom() - activeArea().bottom()));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!draggingToast || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseReleased(event);
        int offset = dragOffset;
        cancelInteraction();
        if (offset >= SWIPE) dismiss();
        else if (offset == 0 && notice.isMouseOver(event.x(), event.y())) acknowledge();
        return true;
    }

    @Override
    protected void cancelInteraction() {
        draggingToast = false;
        dragOffset = 0;
        if (!compact && notice != null) moveNotice(0);
    }

    private void moveNotice(int offset) {
        var area = activeArea();
        notice.setX(area.x());
        notice.setWidth(area.width() - ROW - GAP);
        notice.setY(area.y() + offset);
        close.setX(area.right() - ROW);
        close.setY(area.y() + offset);
    }

    private Bounds activeArea() {
        if (current != Kind.FLOATING) return noticeArea;
        int width = Math.min(232, body.width() - 12);
        int y = Math.max(noticeArea.y(), Math.min(body.bottom() - ROW - 3, body.y() + body.height() / 2));
        return new Bounds(body.x() + (body.width() - width) / 2, y, width, ROW);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && draggingToast) {
            cancelInteraction();
            return true;
        }
        if (!compact && event.isEscape() && current == Kind.FLOATING && (getFocused() == notice || getFocused() == close)) {
            dismiss();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        hovered = current != null && (notice.isMouseOver(mouseX, mouseY) || close.isMouseOver(mouseX, mouseY));
        if (current == Kind.FLOATING) {
            var area = activeArea();
            int y = notice.getY();
            RoundedCorners.draw(graphics, area.x() - 3, y - 2, area.width() + 6,
                    Math.min(ROW + 5, body.bottom() - y + 2), 7, 1, theme.border(), theme.background() | 0xFF000000);
            graphics.nextStratum();
        }
        int y = body.bottom() - font.lineHeight;
        if (status != null && y >= activeArea().bottom() + GAP && !draggingToast) {
            PanelText.drawLine(graphics, font, status, body.x(), y, body.width(), theme.text(), mouseX, mouseY);
        }
    }

    @Override
    public Component getNarrationMessage() {
        return status == null ? getTitle() : CommonComponents.joinForNarration(getTitle(), status);
    }

    private static Component label(String key) { return Component.translatable(GALLERY + key); }
    private static Component titleText() { return Component.translatable("sh_better_ui.toast.task_completed"); }
    private static Component description() { return Component.translatable("sh_better_ui.panel.developer.demo.toast_details"); }
}
