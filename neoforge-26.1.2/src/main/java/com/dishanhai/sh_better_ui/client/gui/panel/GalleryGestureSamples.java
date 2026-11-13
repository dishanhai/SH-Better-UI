// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.collision.CollisionBody;
import com.dishanhai.sh_better_ui.client.gui.collision.CollisionShape;
import com.dishanhai.sh_better_ui.client.gui.collision.CollisionWorld;
import com.dishanhai.sh_better_ui.client.gui.collision.CompressionModel;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback;
import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback.Status;
import com.dishanhai.sh_better_ui.client.gui.scaled.PointerDrag;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/** The five gallery scenes share native focus and a single primary-button capture. */
final class GalleryGestureSamples extends GallerySampleScreen {
    private static final int ROW = 22;
    private static final int GAP = 4;
    private static final int KNOB = 18;
    private static final int SLIDE_MS = 600;
    private static final float[] DETENTS = {0.36f, 0.62f, 0.90f};
    private static final String[] DETENT_NAMES = {"low", "middle", "high"};
    private static final int SHEET_HANDLE_HEIGHT = 12;
    private static final int COLLISION_HANDLE_SIZE = 24;
    private static final int REFRESH_HEADER = 18;
    private static final int REFRESH_ROW = 15;
    private static final int REFRESH_ROWS = 8;
    private static final int REFRESH_LATENCY_MS = 650;

    private enum Gesture { SLIDE, SHEET, COLLISION, REFRESH }

    private final PointerDrag pointer = new PointerDrag();
    private final Spring slide = new Spring(Spring.HOVER);
    private final Spring sheet = new Spring(new Spring.Preset(0.24, 0.74, 0.00025f, 0.004f), DETENTS[1]);
    private final Spring reveal = new Spring(Spring.HOVER);
    private final UiButton[] modalButtons = new UiButton[4];
    private final UiButton[] detentButtons = new UiButton[DETENTS.length];
    private final boolean[] neighborSelected = new boolean[2];
    private final ButtonFeedback refreshFeedback = new ButtonFeedback();

    private Handle handle;
    private Bounds stage;
    private Bounds modalStatus;
    private UiButton reset;
    private UiButton modalCancel;
    private UiButton collisionButton;
    private UiButton refreshButton;
    private UiButton refreshFailureButton;
    private int modalSource = -1;
    private int confirmed;
    private int outsideClicks;
    private boolean modalBusy;
    private boolean slideCompleted;
    private boolean slideInside;
    private boolean announcedSlideArmed;
    private boolean swallowRelease;
    private long keyStart = -1;
    private float dragStart;
    private int sheetAnchor = 1;
    private float sheetDragHeight;
    private double sheetSpeed;
    private long sheetMotionTime;
    private CollisionWorld collisionWorld;
    private CollisionBody collisionHandle;
    private CollisionBody collisionCard;
    private boolean testCollision;
    private boolean refreshDown;
    private boolean refreshRejected;
    private boolean refreshInside;
    private double refreshScroll;
    private int refreshRevision;
    private int refreshRequest;
    private long refreshDue;
    private boolean failNextRefresh;
    private boolean refreshWillFail;

    GalleryGestureSamples(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected void initSample() {
        switch (id) {
            case "slide_confirm" -> initSlide();
            case "local_modal" -> initModal();
            case "bottom_sheet" -> initSheet();
            case "elastic_collision" -> initCollision();
            case "pull_refresh" -> initRefresh();
            default -> throw new IllegalArgumentException(id);
        }
    }

    private void initSlide() {
        int width = Math.min(288, body.width());
        Bounds track = centered(body.y() + 13, width, ROW);
        Bounds resetBounds = centered(track.bottom() + GAP, Math.min(96, width), ROW);
        if (resetBounds.bottom() > body.bottom()) {
            int resetWidth = Math.min(72, width / 3);
            resetBounds = new Bounds(track.right() - resetWidth, track.y(), resetWidth, ROW);
            track = new Bounds(track.x(), track.y(), width - resetWidth - GAP, ROW);
        }
        handle = addHandle(Gesture.SLIDE, track, slideText("label"), slideText("usage"));
        reset = addButton(resetBounds, text("reset"), ignored -> {
                    resetSlide();
                    setInitialFocus(handle);
                }, false);
        reset.setLeadingIcon(GraphicIcon.RESET);
        updateSlideCompletion();
        initialFocus = handle;
    }

    private void initModal() {
        int rowWidth = Math.min(336, body.width());
        int cellWidth = (rowWidth - 3 * GAP) / modalButtons.length;
        Bounds row = centered(body.y(), cellWidth * modalButtons.length + 3 * GAP, ROW);
        for (int i = 0; i < modalButtons.length; i++) {
            int index = i;
            Bounds cell = new Bounds(row.x() + i * (cellWidth + GAP), row.y(), cellWidth, ROW);
            if (i == 0 || i == 3) {
                var source = addButton(cell, gallery(i == 0 ? "local_modal_left" : "local_modal_right"),
                        ignored -> openModal(index), true);
                if (cellWidth < 60) source.setIcon(i == 0 ? GraphicIcon.NEXT : GraphicIcon.PREVIOUS);
                modalButtons[i] = source;
            } else {
                var neighbor = addButton(cell, gallery("local_modal_neighbor", i), ignored -> {
                    neighborSelected[index - 1] = !neighborSelected[index - 1];
                    modalButtons[index].setSelected(neighborSelected[index - 1]);
                }, false);
                neighbor.setSelected(neighborSelected[i - 1]);
                if (cellWidth < 46) neighbor.setIcon(i == 1 ? GraphicIcon.SWITCH : GraphicIcon.SUCCESS);
                modalButtons[i] = neighbor;
            }
        }
        boolean stack = body.height() >= 2 * (ROW + GAP) + font.lineHeight;
        int outsideWidth = stack ? Math.min(112, row.width()) : Math.min(112, row.width() / 2);
        var outsideBounds = stack ? centered(row.bottom() + GAP, outsideWidth, ROW)
                : new Bounds(row.x(), row.bottom() + GAP, outsideWidth, ROW);
        modalStatus = stack ? new Bounds(body.x(), outsideBounds.bottom() + GAP, body.width(), font.lineHeight)
                : new Bounds(outsideBounds.right() + GAP, outsideBounds.y() + 6, row.width() - outsideWidth - GAP,
                        font.lineHeight);
        addButton(outsideBounds,
                gallery("local_modal_outside", outsideClicks),
                pressed -> pressed.setMessage(gallery("local_modal_outside", ++outsideClicks)), false);
        handle = addHandle(Gesture.SLIDE, row, slideText("label"), text("controls"));
        handle.visible = false;
        modalCancel = addButton(new Bounds(row.x(), row.y(), ROW, ROW), CommonComponents.GUI_CANCEL,
                ignored -> closeModal(), false);
        modalCancel.setIcon(GraphicIcon.CLOSE);
        modalCancel.visible = false;
        initialFocus = modalButtons[0];
    }

    private void openModal(int source) {
        if (modalSource >= 0) {
            if (!modalBusy) narrate(gallery("local_modal_busy"));
            modalBusy = true;
            return;
        }
        modalSource = source;
        modalBusy = false;
        int first = source == 0 ? 0 : 1;
        int last = first + 2;
        var left = modalButtons[first];
        var right = modalButtons[last];
        for (int i = first; i <= last; i++) modalButtons[i].visible = false;
        int x = left.getX();
        int y = left.getY();
        int width = right.getX() + right.getWidth() - x;
        handle.setRectangle(width - ROW - GAP, ROW, x, y);
        modalCancel.setPosition(x + width - ROW, y);
        handle.visible = modalCancel.visible = true;
        resetSlide();
        setInitialFocus(handle);
    }

    private void closeModal() {
        int source = modalSource;
        if (source < 0) return;
        modalSource = -1;
        modalBusy = false;
        resetSlide();
        handle.visible = modalCancel.visible = false;
        for (var button : modalButtons) button.visible = true;
        setInitialFocus(modalButtons[source]);
    }

    private void initSheet() {
        boolean showButtons = body.height() >= 76;
        int cell = (body.width() - 2 * GAP) / DETENTS.length;
        for (int i = 0; i < DETENTS.length; i++) {
            int anchor = i;
            var bounds = new Bounds(body.x() + i * (cell + GAP), body.y(), cell, ROW);
            detentButtons[i] = showButtons
                    ? addButton(bounds, sheetText(DETENT_NAMES[i]), ignored -> settleSheet(anchor, Util.getMillis()), false)
                    : null;
            if (showButtons) detentButtons[i].setSelected(i == sheetAnchor);
        }
        int top = showButtons ? ROW + GAP : 0;
        stage = new Bounds(body.x(), body.y() + top, body.width(), body.height() - top - 13);
        sheet.snap(DETENTS[sheetAnchor]);
        handle = addHandle(Gesture.SHEET, stage, sheetStatus("current", sheetAnchor), sheetText("handle_usage"));
        updateSheetHandle();
        initialFocus = handle;
    }

    private void initCollision() {
        int width = Math.min(320, body.width());
        stage = centered(body.y(), width, COLLISION_HANDLE_SIZE + 6);
        var viewport = new CollisionShape(stage.x() + 2, stage.y() + 2, width - 4, stage.height() - 4);
        collisionWorld = new CollisionWorld(viewport).axis(CollisionWorld.Axis.HORIZONTAL);
        collisionHandle = collisionWorld.add(new CollisionShape(viewport.x(), stage.y() + 3,
                COLLISION_HANDLE_SIZE, COLLISION_HANDLE_SIZE)).strategy(CollisionBody.Strategy.RESIST).priority(1);
        float cardWidth = viewport.width() * 0.59f;
        collisionCard = collisionWorld.add(new CollisionShape(viewport.right() - cardWidth, stage.y() + 3,
                cardWidth, COLLISION_HANDLE_SIZE)).strategy(CollisionBody.Strategy.ELASTIC)
                .anchor(CollisionBody.Anchor.END, CollisionBody.Anchor.CENTER)
                .compression(new CompressionModel(30, COLLISION_HANDLE_SIZE, cardWidth, COLLISION_HANDLE_SIZE, 1));
        handle = addHandle(Gesture.COLLISION, stage, gallery("collision_toggle"), gallery("collision_hint"));
        int buttonTop = Math.min(stage.bottom() + GAP, body.bottom() - ROW);
        collisionButton = addButton(centered(buttonTop, Math.min(130, width), ROW),
                gallery("collision_toggle"), ignored -> toggleCollision(), false);
        collisionButton.setSelected(testCollision);
        updateCollision(Util.getMillis());
        initialFocus = handle;
    }

    private void initRefresh() {
        int cell = (body.width() - GAP) / 2;
        refreshButton = addButton(new Bounds(body.x(), body.y(), cell, ROW), refreshText("label"),
                ignored -> requestRefresh(), true);
        refreshButton.setLeadingIcon(GraphicIcon.REFRESH);
        refreshButton.setPreview(refreshText("widget_tooltip"));
        refreshFailureButton = addButton(new Bounds(body.x() + cell + GAP, body.y(), cell, ROW), text("fail_next"),
                ignored -> {
                    failNextRefresh = !failNextRefresh;
                    refreshFailureButton.setSelected(failNextRefresh);
                }, false);
        refreshFailureButton.setSelected(failNextRefresh);
        stage = new Bounds(body.x(), body.y() + ROW + GAP, body.width(), body.height() - ROW - GAP);
        handle = addHandle(Gesture.REFRESH, new Bounds(stage.x(), stage.y(), stage.width(), REFRESH_HEADER),
                refreshText("hint"), refreshText("widget_tooltip"));
        refreshScroll = Math.clamp(refreshScroll, 0, maxRefreshScroll());
        refreshButton.active = refreshFailureButton.active = refreshDue == 0;
        initialFocus = refreshButton;
    }

    private Handle addHandle(Gesture gesture, Bounds bounds, Component message, Component usage) {
        return addRenderableWidget(new Handle(gesture, bounds, message, usage));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (pointer.active()) return true;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) swallowRelease = false;
        int sourceBeforeClick = modalSource;
        boolean cancelWasFocused = modalCancel != null && getFocused() == modalCancel;
        boolean handled = super.mouseClicked(event, doubleClick);
        // Native mouse dispatch sets the clicked widget's focus after its callback.
        if (modalSource >= 0 && getFocused() != handle && getFocused() != modalCancel) {
            setInitialFocus(cancelWasFocused ? modalCancel : handle);
            setDragging(false);
        } else if (modalSource < 0 && sourceBeforeClick >= 0 && getFocused() == modalCancel) {
            setInitialFocus(modalButtons[sourceBeforeClick]);
            setDragging(false);
        } else if (reset != null && getFocused() == reset && !reset.active) {
            setInitialFocus(handle);
            setDragging(false);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (swallowRelease && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (!pointer.active() || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseDragged(event, dx, dy);
        if (!minecraft.isWindowActive()) cancelInteraction();
        else if (pointer.move(event.button(), event.x(), event.y()) != null) follow(event);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (swallowRelease && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            swallowRelease = false;
            return true;
        }
        if (!pointer.active() || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseReleased(event);
        setDragging(false);
        if (!minecraft.isWindowActive() || pointer.release(event.button(), event.x(), event.y()) == null) {
            cancelCapture();
            swallowRelease = false;
            return true;
        }
        follow(event);
        long now = Util.getMillis();
        switch (handle.gesture) {
            case SLIDE -> {
                if (slideInside && slide.value() >= 1f) completeSlide();
                else retarget(slide, 0, now);
            }
            case SHEET -> settleSheet(projectedAnchor(now), now);
            case COLLISION -> collisionHandle.direct(false);
            case REFRESH -> {
                boolean request = refreshArmed();
                clearPull(now);
                if (request) requestRefresh();
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (ready(Gesture.REFRESH) && stage.contains(x, y) && dy != 0 && !pointer.active()) {
            refreshScroll = Math.clamp(refreshScroll - dy * REFRESH_ROW, 0, maxRefreshScroll());
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            if (modalSource >= 0) {
                closeModal();
                return true;
            }
            if (pointer.active() || keyStart >= 0) {
                cancelCapture();
                return true;
            }
        }
        if (modalSource >= 0) {
            if (event.key() == GLFW.GLFW_KEY_TAB) {
                setInitialFocus(getFocused() == handle ? modalCancel : handle);
                return true;
            }
            if (getFocused() == handle) return handle.keyPressed(event);
            return getFocused() == modalCancel && modalCancel.keyPressed(event);
        }
        if (ready(Gesture.REFRESH) && event.key() == GLFW.GLFW_KEY_F5) {
            requestRefresh();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void cancelInteraction() {
        cancelCapture();
        if (modalSource >= 0) closeModal();
    }

    @Override
    protected void repositionElements() {
        int source = modalSource;
        var focused = getFocused();
        boolean focusedHandle = focused != null && focused == handle;
        int focusedDetent = -1;
        if (id.equals("bottom_sheet") && focused != null) {
            for (int index = 0; index < detentButtons.length; index++) {
                if (focused == detentButtons[index]) focusedDetent = index;
            }
        }
        super.repositionElements();
        if (compact) return;
        if (source >= 0) setInitialFocus(modalButtons[source]);
        else if (focusedHandle && handle.visible && handle.active) setInitialFocus(handle);
        else if (focusedDetent >= 0) {
            var button = detentButtons[focusedDetent];
            setInitialFocus(button == null ? handle : button);
        }
    }

    @Override
    protected void tickSample() {
        long now = Util.getMillis();
        if (ready(Gesture.SLIDE) && handle.visible) {
            boolean armed = slideArmed(now);
            if (armed && !announcedSlideArmed) narrate(slideText("armed"));
            announcedSlideArmed = armed;
        }
        if (refreshDue == 0 || now < refreshDue) return;
        refreshDue = 0;
        var tone = refreshWillFail ? ButtonFeedback.Tone.FAILED : ButtonFeedback.Tone.SUCCESS;
        if (refreshFeedback.resolve(refreshRequest, tone, now) && !refreshWillFail) {
            refreshRevision++;
            refreshScroll = 0;
        }
        if (ready(Gesture.REFRESH)) {
            refreshButton.active = refreshFailureButton.active = true;
            narrate(refreshStatus().message());
        }
    }

    @Override
    public void removed() {
        if (refreshDue != 0) {
            refreshFeedback.resolveCancelled(refreshRequest, Util.getMillis());
            refreshDue = 0;
        }
        super.removed();
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        switch (id) {
            case "slide_confirm" -> line(graphics,
                    gallery(slideCompleted ? "slide_confirm_completed_hint" : "slide_confirm_hint"),
                    body, body.y(), mouseX, mouseY);
            case "local_modal" -> line(graphics, modalBusy ? gallery("local_modal_busy")
                            : gallery("local_modal_count", confirmed), modalStatus, modalStatus.y(), mouseX, mouseY);
            case "bottom_sheet" -> extractSheet(graphics, now, mouseX, mouseY);
            case "elastic_collision" -> extractCollision(graphics, now, mouseX, mouseY);
            case "pull_refresh" -> extractRefresh(graphics, now);
        }
    }

    private void follow(MouseButtonEvent event) {
        long now = Util.getMillis();
        switch (handle.gesture) {
            case SLIDE -> {
                slide.snap(Math.clamp(dragStart + (float) pointer.offsetX() / slideTravel(), 0, 1));
                slideInside = handle.bounds().contains(event.x(), event.y());
            }
            case SHEET -> followSheet(now);
            case COLLISION -> collisionHandle.moveTo(clampCollisionX(dragStart + (float) pointer.offsetX()),
                    collisionHandle.desired().y());
            case REFRESH -> followRefresh(event.x(), event.y());
        }
    }

    private void cancelCapture() {
        boolean held = pointer.active();
        pointer.cancel();
        setDragging(false);
        long now = Util.getMillis();
        if (keyStart >= 0 || held && handle.gesture == Gesture.SLIDE) {
            if (keyStart >= 0) slide.snap(keyProgress(now));
            keyStart = -1;
            retarget(slide, 0, now);
        }
        if (!held) return;
        swallowRelease = true;
        switch (handle.gesture) {
            case SLIDE -> slideInside = false;
            case SHEET -> settleSheet(sheetAnchor, now);
            case COLLISION -> collisionHandle.direct(false);
            case REFRESH -> clearPull(now);
        }
    }

    private void resetSlide() {
        cancelCapture();
        slide.snap(0);
        slideCompleted = false;
        announcedSlideArmed = false;
        if (reset != null) updateSlideCompletion();
        else handle.setMessage(slideText("label"));
    }

    private void completeSlide() {
        if (modalSource >= 0) {
            confirmed++;
            closeModal();
            narrate(gallery("local_modal_count", confirmed));
        } else {
            slide.snap(1);
            slideCompleted = true;
            updateSlideCompletion();
            narrate(handle.getMessage());
        }
    }

    private void updateSlideCompletion() {
        reset.active = slideCompleted;
        handle.setMessage(slideText(slideCompleted ? "completed" : "label"));
        handle.setUsage(slideCompleted ? gallery("slide_confirm_completed_hint") : slideText("usage"));
    }

    private float keyProgress(long now) {
        return Math.clamp((float) (now - keyStart) / SLIDE_MS, 0, 1);
    }

    private boolean slideArmed(long now) {
        return !slideCompleted && (keyStart >= 0 ? keyProgress(now) >= 1
                : pointer.active() && slideInside && slide.value() >= 1);
    }

    private int slideTravel() { return handle.getWidth() - KNOB - 4; }

    private Bounds slideKnob(float progress) {
        return new Bounds(handle.getX() + 2 + Math.round(progress * slideTravel()),
                handle.getY() + 2, KNOB, KNOB);
    }

    private void extractSlide(GuiGraphicsExtractor graphics, long now, int mouseX, int mouseY) {
        slide.update(now);
        float progress = keyStart >= 0 ? keyProgress(now) : Math.clamp(slide.value(), 0, 1);
        boolean armed = slideArmed(now);
        Bounds track = handle.bounds();
        Bounds knob = slideKnob(progress);
        box(graphics, track, 11, handle.isFocused() || armed, theme.background());
        if (progress > 0) {
            graphics.fill(track.x() + 3, track.bottom() - 4, knob.x() + KNOB / 2, track.bottom() - 2, accent());
        }
        int left = progress < 0.5f ? knob.right() + GAP : track.x() + 5;
        int right = progress < 0.5f ? track.right() - 5 : knob.x() - GAP;
        Component label = armed ? slideText("armed") : handle.getMessage();
        line(graphics, label, new Bounds(left, track.y(), right - left, ROW), track.y() + 6, mouseX, mouseY);
        boolean held = pointer.active() || keyStart >= 0;
        box(graphics, knob, 9, held, held || armed || slideCompleted ? accent() : theme.surface());
        (slideCompleted ? GraphicIcon.SUCCESS : GraphicIcon.NEXT).draw(graphics, knob.x() + 1, knob.y() + 1, 1,
                held || armed || slideCompleted ? theme.background() | 0xFF000000 : theme.text(), preferences);
    }

    private void followSheet(long now) {
        float height = dragStart - (float) pointer.offsetY() / stage.height();
        if (height != sheetDragHeight) {
            long elapsed = Math.max(1, now - sheetMotionTime);
            sheetSpeed = elapsed > 100 ? 0 : Math.clamp((height - sheetDragHeight) * 1000 / elapsed, -6, 6);
            sheetMotionTime = now;
            sheetDragHeight = height;
        }
        float bounded = Math.clamp(height, DETENTS[0], DETENTS[DETENTS.length - 1]);
        float overshoot = preferences.reducedMotion() ? 0 : Math.clamp((height - bounded) * 0.3f, -0.10f, 0.10f);
        sheet.snap(bounded + overshoot);
    }

    private int projectedAnchor(long now) {
        double projection = sheetDragHeight + (now - sheetMotionTime > 100 ? 0 : sheetSpeed * 0.16);
        int nearest = 0;
        for (int i = 1; i < DETENTS.length; i++) {
            if (Math.abs(DETENTS[i] - projection) < Math.abs(DETENTS[nearest] - projection)) nearest = i;
        }
        return nearest;
    }

    private void settleSheet(int anchor, long now) {
        pointer.cancel();
        setDragging(false);
        boolean changed = sheetAnchor != anchor;
        sheetAnchor = anchor;
        retarget(sheet, DETENTS[anchor], now);
        for (int i = 0; i < DETENTS.length; i++) {
            if (detentButtons[i] != null) detentButtons[i].setSelected(i == anchor);
        }
        if (changed) narrate(sheetStatus("current", anchor));
    }

    private int sheetY() {
        int height = Math.clamp(Math.round(sheet.value() * stage.height()), SHEET_HANDLE_HEIGHT, stage.height() - 2);
        return stage.bottom() - height;
    }

    private void updateSheetHandle() {
        int width = Math.min(48, stage.width() / 2);
        handle.setRectangle(width, SHEET_HANDLE_HEIGHT, stage.x() + (stage.width() - width) / 2, sheetY());
    }

    private void extractSheet(GuiGraphicsExtractor graphics, long now, int mouseX, int mouseY) {
        sheet.update(now);
        updateSheetHandle();
        boolean dragging = pointer.active();
        int anchor = dragging ? projectedAnchor(now) : sheetAnchor;
        box(graphics, stage, 5, false, theme.background());
        Bounds panel = new Bounds(stage.x() + 18, sheetY(), stage.width() - 22, stage.bottom() - sheetY() - 2);
        box(graphics, panel, 4, dragging, theme.surface());
        for (int i = 0; i < DETENTS.length; i++) {
            int y = stage.bottom() - Math.round(DETENTS[i] * stage.height());
            graphics.fill(stage.x() + 4, y, stage.x() + 14, y + (anchor == i ? 2 : 1),
                    anchor == i ? accent() : border());
        }
        if (panel.height() >= SHEET_HANDLE_HEIGHT + font.lineHeight + 2) {
            line(graphics, sheetText("content"), new Bounds(panel.x() + 4, panel.y(), panel.width() - 8, panel.height()),
                    panel.y() + SHEET_HANDLE_HEIGHT, mouseX, mouseY);
        }
        Component status = sheetStatus(dragging ? "preview" : sheet.settled() ? "current" : "settling", anchor);
        if (!status.equals(handle.getMessage())) handle.setMessage(status);
        line(graphics, status, body, stage.bottom() + 3, mouseX, mouseY);
    }

    private float clampCollisionX(float x) {
        return Math.clamp(x, collisionWorld.viewport().x(),
                collisionWorld.viewport().right() - COLLISION_HANDLE_SIZE - 32);
    }

    private void toggleCollision() {
        testCollision = !testCollision;
        collisionButton.setSelected(testCollision);
    }

    private void updateCollision(long now) {
        if (!pointer.active()) {
            float desired = testCollision ? collisionCard.desired().x() + collisionCard.desired().width() * 0.34f
                    : collisionWorld.viewport().x();
            collisionHandle.moveTo(clampCollisionX(desired), collisionHandle.desired().y());
        }
        collisionWorld.update(now, preferences.reducedMotion());
        handle.setPosition(Math.round(collisionHandle.visualX()), Math.round(collisionHandle.visualY()));
        handle.setSize(COLLISION_HANDLE_SIZE, COLLISION_HANDLE_SIZE);
    }

    private void extractCollision(GuiGraphicsExtractor graphics, long now, int mouseX, int mouseY) {
        updateCollision(now);
        box(graphics, stage, 5, false, theme.background());
        int x = Math.round(collisionCard.visualX());
        int right = Math.round(collisionCard.visualRight());
        Bounds card = new Bounds(x, stage.y() + 3, right - x, COLLISION_HANDLE_SIZE);
        boolean compressed = collisionCard.visualRight() - collisionCard.visualX() < collisionCard.desired().width() - 1;
        box(graphics, card, 4, compressed, theme.surface());
        line(graphics, text("card"), new Bounds(card.x() + 4, card.y(), card.width() - 8, card.height()),
                card.y() + 8, mouseX, mouseY);
    }

    private double maxRefreshScroll() {
        return Math.max(0, REFRESH_ROWS * REFRESH_ROW - (stage.height() - REFRESH_HEADER - 2));
    }

    private double pullThreshold() { return Math.min(48, stage.height() - REFRESH_HEADER - 1); }

    private void followRefresh(double x, double y) {
        if (!refreshDown && !refreshRejected && Math.max(Math.abs(pointer.offsetX()), Math.abs(pointer.offsetY())) >= 6) {
            refreshDown = pointer.offsetY() > Math.abs(pointer.offsetX());
            refreshRejected = !refreshDown;
        }
        refreshInside = stage.contains(x, y);
        reveal.snap(refreshDown ? Math.clamp((float) (pointer.offsetY() / pullThreshold()), 0, 1) : 0);
    }

    private boolean refreshArmed() { return refreshDown && refreshInside && reveal.value() >= 1; }

    private void clearPull(long now) {
        refreshDown = refreshRejected = refreshInside = false;
        retarget(reveal, 0, now);
    }

    private void requestRefresh() {
        long now = Util.getMillis();
        if (pointer.active() || !refreshFeedback.tryBegin(0, now)) return;
        refreshWillFail = failNextRefresh;
        failNextRefresh = false;
        refreshFailureButton.setSelected(false);
        refreshDue = now + REFRESH_LATENCY_MS;
        refreshFeedback.pending(0, ++refreshRequest, now);
        refreshButton.active = refreshFailureButton.active = false;
        narrate(refreshStatus().message());
    }

    private record RefreshStatus(Component message, GraphicIcon icon) {}

    private RefreshStatus refreshStatus() {
        Status status = refreshFeedback.status(0, Util.getMillis());
        if (pointer.active() && !refreshRejected) {
            return refreshArmed() ? new RefreshStatus(refreshText("armed"), GraphicIcon.REFRESH)
                    : new RefreshStatus(refreshText("pulling"), GraphicIcon.PULL_DOWN);
        }
        GraphicIcon icon = switch (status) {
            case READY -> GraphicIcon.PULL_DOWN;
            case PENDING -> GraphicIcon.STATUS_PENDING;
            case SUCCEEDED -> GraphicIcon.STATUS_SUCCEEDED;
            case FAILED -> GraphicIcon.STATUS_FAILED;
            case CANCELLED -> GraphicIcon.STATUS_CANCELLED;
            case PARTIAL -> GraphicIcon.STATUS_PARTIAL;
            case TIMED_OUT -> GraphicIcon.STATUS_TIMED_OUT;
        };
        return new RefreshStatus(status == Status.READY ? refreshText("hint") : Component.translatable(status.translationKey()), icon);
    }

    private void extractRefresh(GuiGraphicsExtractor graphics, long now) {
        reveal.update(now);
        int headerEnd = stage.y() + REFRESH_HEADER;
        int pullOffset = Math.round(Math.clamp(reveal.value(), 0, 1) * Math.min(30, stage.height() - REFRESH_HEADER - 2));
        box(graphics, stage, 5, false, theme.background());
        graphics.enableScissor(stage.x() + 1, headerEnd, stage.right() - 1, stage.bottom() - 1);
        try {
            for (int row = 0; row < REFRESH_ROWS; row++) {
                int y = headerEnd + row * REFRESH_ROW + pullOffset - (int) refreshScroll;
                if (y + REFRESH_ROW <= headerEnd || y >= stage.bottom()) continue;
                if (row % 2 == 0) graphics.fill(stage.x() + 1, y, stage.right() - 1, y + REFRESH_ROW, theme.surface());
                Component message = row == 0 ? text("result_count", refreshRevision)
                        : text("row", refreshRevision * (REFRESH_ROWS - 1) + row);
                line(graphics, message, stage, y + 3, -1, -1);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void extractRefreshHandle(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var status = refreshStatus();
        if (!status.message().equals(handle.getMessage())) handle.setMessage(status.message());
        box(graphics, handle.bounds(), 4, handle.isFocused() || refreshArmed(), theme.surface());
        status.icon().draw(graphics, stage.x() + 2, stage.y() + 1, 1, accent(), preferences);
        line(graphics, status.message(), new Bounds(stage.x() + 21, stage.y(), stage.width() - 23, REFRESH_HEADER),
                stage.y() + 5, mouseX, mouseY);
        if (reveal.value() > 0) {
            int end = stage.x() + 2 + Math.round((stage.width() - 4) * Math.clamp(reveal.value(), 0, 1));
            graphics.fill(stage.x() + 2, stage.y() + REFRESH_HEADER - 2, end, stage.y() + REFRESH_HEADER - 1, accent());
        }
    }

    private boolean ready(Gesture gesture) {
        return !compact && handle != null && handle.gesture == gesture && children().contains(handle);
    }

    private void retarget(Spring spring, float target, long now) {
        if (preferences.reducedMotion()) spring.snap(target);
        else spring.setTarget(target, now);
    }

    private Bounds centered(int y, int width, int height) {
        return new Bounds(body.x() + (body.width() - width) / 2, y, width, height);
    }

    private int accent() { return preferences.highContrast() ? theme.text() : theme.accent(); }
    private int border() { return preferences.highContrast() ? theme.text() : theme.border(); }

    private void box(GuiGraphicsExtractor graphics, Bounds area, int radius, boolean highlighted, int fill) {
        RoundedCorners.draw(graphics, area.x(), area.y(), area.width(), area.height(), radius, highlighted ? 2 : 1,
                highlighted ? accent() : border(), fill | 0xFF000000);
    }

    private void line(GuiGraphicsExtractor graphics, Component message, Bounds area, int y, int mouseX, int mouseY) {
        if (area.width() < font.width(CommonComponents.ELLIPSIS)) return;
        PanelText.drawLine(graphics, font, message, area.x(), y, area.width(), theme.text(), mouseX, mouseY);
    }

    private void narrate(Component component) { minecraft.getNarrator().saySystemNow(component); }

    private static Component slideText(String suffix) { return Component.translatable("sh_better_ui.slide_confirm." + suffix); }
    private static Component refreshText(String suffix) { return Component.translatable("sh_better_ui.pull_refresh." + suffix); }
    private static Component gallery(String key, Object... args) {
        return Component.translatable("sh_better_ui.panel.developer.gallery." + key, args);
    }
    private static Component sheetText(String suffix, Object... args) {
        return Component.translatable("sh_better_ui.panel.developer.demo.bottom_sheet." + suffix, args);
    }
    private static Component sheetStatus(String state, int anchor) { return sheetText(state, sheetText(DETENT_NAMES[anchor])); }

    private final class Handle extends AbstractWidget {
        private final Gesture gesture;
        private Component usage;

        private Handle(Gesture gesture, Bounds area, Component message, Component usage) {
            super(area.x(), area.y(), area.width(), area.height(), message);
            this.gesture = gesture;
            setUsage(usage);
        }

        private void setUsage(Component usage) {
            this.usage = usage;
            setTooltip(Tooltip.create(usage));
        }

        private Bounds bounds() { return new Bounds(getX(), getY(), getWidth(), getHeight()); }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!isActive() || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !minecraft.isWindowActive()
                    || !bounds().contains(event.x(), event.y())) return false;
            long now = Util.getMillis();
            switch (gesture) {
                case SLIDE -> {
                    if (slideCompleted || keyStart >= 0 || !slideKnob(slide.value()).contains(event.x(), event.y())) return false;
                    dragStart = slide.value();
                    slide.snap(dragStart);
                    slideInside = true;
                }
                case SHEET -> {
                    dragStart = sheetDragHeight = sheet.value();
                    sheet.snap(dragStart);
                    sheetSpeed = 0;
                    sheetMotionTime = now;
                }
                case COLLISION -> {
                    dragStart = collisionHandle.visualX();
                    collisionHandle.moveTo(dragStart, collisionHandle.desired().y()).direct(true);
                }
                case REFRESH -> {
                    if (refreshScroll > 0 || refreshDue != 0) return false;
                    reveal.snap(0);
                }
            }
            return pointer.begin(event.button(), event.x(), event.y());
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (!isFocused() || !isActive() || !minecraft.isWindowActive() || pointer.active()) return false;
            return switch (gesture) {
                case SLIDE -> {
                    if (event.key() != GLFW.GLFW_KEY_RIGHT || slideCompleted) yield false;
                    if (keyStart < 0) keyStart = Util.getMillis();
                    yield true;
                }
                case SHEET -> {
                    int direction = event.key() == GLFW.GLFW_KEY_UP ? 1 : event.key() == GLFW.GLFW_KEY_DOWN ? -1 : 0;
                    if (direction == 0) yield false;
                    settleSheet(Math.clamp(sheetAnchor + direction, 0, DETENTS.length - 1), Util.getMillis());
                    yield true;
                }
                case COLLISION -> {
                    if (!event.isSelection()) yield false;
                    toggleCollision();
                    yield true;
                }
                case REFRESH -> {
                    if (!event.isSelection()) yield false;
                    requestRefresh();
                    yield true;
                }
            };
        }

        @Override
        public boolean keyReleased(KeyEvent event) {
            if (gesture != Gesture.SLIDE || event.key() != GLFW.GLFW_KEY_RIGHT || keyStart < 0) return false;
            if (isFocused() && minecraft.isWindowActive() && keyProgress(Util.getMillis()) >= 1) {
                keyStart = -1;
                completeSlide();
            } else {
                cancelCapture();
            }
            return true;
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (!focused && (pointer.active() || keyStart >= 0)) cancelCapture();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            Component title = gesture == Gesture.SLIDE && slideArmed(Util.getMillis())
                    ? slideText("armed") : getMessage();
            output.add(NarratedElementType.TITLE, title);
            output.add(NarratedElementType.USAGE, usage);
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            switch (gesture) {
                case SLIDE -> extractSlide(graphics, Util.getMillis(), mouseX, mouseY);
                case SHEET -> {
                    box(graphics, bounds(), 5, isFocused() || pointer.active(), theme.surface());
                    int x = getX() + (getWidth() - 18) / 2;
                    graphics.fill(x, getY() + 5, x + 18, getY() + 7, accent());
                }
                case COLLISION -> {
                    box(graphics, bounds(), 4, isFocused() || pointer.active(), theme.surface());
                    GraphicIcon.NEXT.draw(graphics, getX() + 4, getY() + 4, 1, accent(), preferences);
                }
                case REFRESH -> extractRefreshHandle(graphics, mouseX, mouseY);
            }
        }
    }
}
