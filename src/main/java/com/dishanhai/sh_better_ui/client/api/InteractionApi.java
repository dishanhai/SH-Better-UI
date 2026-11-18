package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiRenderUtil;
import com.dishanhai.sh_better_ui.client.gui.scaled.PausableCountdown;
import com.dishanhai.sh_better_ui.client.gui.scaled.PointerDrag;
import com.dishanhai.sh_better_ui.client.gui.scaled.ScrollPageTurn;
import com.dishanhai.sh_better_ui.client.gui.scaled.SpringTable;
import com.dishanhai.sh_better_ui.client.gui.scaled.SwipeGesture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiBottomSheet;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiFullscreenPreview;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHistory;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerTracker;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPullRefresh;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlideConfirm;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSwipeToast;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast;
import com.dishanhai.sh_better_ui.client.gui.scaled.ViewportTransform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** 山海：覆层、手势、反馈和返回历史。宿主保留每个有状态的控制器。 */
public final class InteractionApi {
    InteractionApi() {}

    public UiOverlayHost overlays(Screen owner, UiPreferences preferences) {
        return new UiOverlayHost(owner, preferences);
    }

    public UiOverlayHost currentOverlays(Screen owner) {
        return UiOverlayHost.current(owner);
    }

    /** 每个 Screen 保留一个管理器，供布局内的多个按钮请求临时扩展。 */
    public UiLocalModal localModal(Screen owner, UiPreferences preferences) {
        return new UiLocalModal(owner, preferences);
    }

    /** 创建全屏预览页，调用方可用 Minecraft.setScreen 显示。 */
    public UiFullscreenPreview fullscreenPreview(Screen parent, Component title, UiPreferences preferences,
                                                 UiFullscreenPreview.Content content) {
        return new UiFullscreenPreview(parent, title, preferences, content);
    }

    public UiFullscreenPreview fullscreenPreview(Screen parent, Component title, UiPreferences preferences,
                                                 UiFullscreenPreview.Content content,
                                                 Supplier<? extends GuiEventListener> returnFocus) {
        return new UiFullscreenPreview(parent, title, preferences, content, returnFocus);
    }

    public boolean confirm(Screen parent, UiConfirmation.Prompt prompt, UiPreferences preferences,
                           Consumer<GuiGraphics> context, Supplier<? extends GuiEventListener> restoreFocus,
                           Runnable onConfirm) {
        return UiConfirmation.open(parent, prompt, preferences, context, restoreFocus, onConfirm);
    }

    public boolean confirm(Screen parent, UiConfirmation.Prompt prompt, UiPreferences preferences,
                           Consumer<GuiGraphics> context, Supplier<? extends GuiEventListener> restoreFocus,
                           Runnable onConfirm, GraphicIcon icon) {
        return UiConfirmation.open(parent, prompt, preferences, context, restoreFocus, onConfirm, icon);
    }

    /** Native isolated confirmation with an explicit optical surface; ordinary confirm is unchanged. */
    public boolean confirmGlass(Screen parent, UiConfirmation.Prompt prompt, UiPreferences preferences,
                           Consumer<GuiGraphics> context, Supplier<? extends GuiEventListener> restoreFocus,
                           Runnable onConfirm) {
        return UiConfirmation.openGlass(parent, prompt, preferences, context, restoreFocus, onConfirm);
    }

    public boolean confirmGlass(Screen parent, UiConfirmation.Prompt prompt, UiPreferences preferences,
                           Consumer<GuiGraphics> context, Supplier<? extends GuiEventListener> restoreFocus,
                           Runnable onConfirm, GraphicIcon icon) {
        return UiConfirmation.openGlass(parent, prompt, preferences, context, restoreFocus, onConfirm, icon);
    }

    public UiBottomSheet bottomSheet(int initialAnchor, float... anchorHeights) {
        return new UiBottomSheet(initialAnchor, anchorHeights);
    }

    public UiBottomSheet capsuleSheet(int initialAnchor, float... anchorHeights) {
        return UiBottomSheet.capsule(initialAnchor, anchorHeights);
    }

    public UiPullRefresh pullRefresh(IntConsumer onRefresh) {
        return new UiPullRefresh(onRefresh);
    }

    public UiPullRefresh pullRefresh(UiPullRefresh.Config config, IntConsumer onRefresh) {
        return new UiPullRefresh(config, onRefresh);
    }

    public UiPullRefresh pullRefresh(UiPullRefresh.Config config, IntConsumer onRefresh,
                                    Function<UiPullRefresh.Frame, GraphicIcon> frameIcon) {
        return new UiPullRefresh(config, onRefresh, frameIcon);
    }

    public UiSlideConfirm slideConfirm(Screen owner, Bounds area, UiPreferences preferences, Runnable onConfirm) {
        return new UiSlideConfirm(owner, area, preferences, onConfirm);
    }

    public UiSlideConfirm slideConfirm(Screen owner, Bounds area, Component label, UiPreferences preferences,
                                       Runnable onConfirm) {
        return new UiSlideConfirm(owner, area, label, preferences, onConfirm);
    }

    /** 将共享鼠标快照转换为区域局部坐标和按压／释放／取消状态。 */
    public UiPointerTracker pointerTracker() { return new UiPointerTracker(); }

    public PointerDrag pointerDrag() {
        return new PointerDrag();
    }

    public SwipeGesture swipeGesture() {
        return new SwipeGesture();
    }

    public PausableCountdown countdown(long durationMs, long nowMs) {
        return new PausableCountdown(durationMs, nowMs);
    }

    /** 在客户端线程采样，可在同一帧传给多个控件。 */
    public UiMouseState mouseState() {
        return UiMouseState.read();
    }

    public boolean capturePointer(Screen owner, int button, UiPointerCapture.Handler handler) {
        return UiPointerCapture.start(owner, button, handler);
    }

    public UiPointerCapture.Registration registerPointerPress(Screen owner, UiPointerCapture.PressHandler handler) {
        return UiPointerCapture.registerPress(owner, handler);
    }

    public boolean cancelPointer(Screen owner) {
        return UiPointerCapture.cancel(owner);
    }

    public ScrollPageTurn scrollPageTurn() {
        return new ScrollPageTurn();
    }

    /** 山海：独立的水平标签视口，可用于宿主自己的绘制和输入。 */
    public com.dishanhai.sh_better_ui.client.gui.scaled.TabScrollState tabScrollState() {
        return new com.dishanhai.sh_better_ui.client.gui.scaled.TabScrollState();
    }

    public double dragScrollOffset(double scrollOffset, double pointerDeltaY, double maxScroll) {
        return ScrollPageTurn.dragOffset(scrollOffset, pointerDeltaY, maxScroll);
    }

    public ViewportTransform viewport(double x, double y, double scale) {
        return new ViewportTransform(x, y, scale);
    }

    public void applyViewport(GuiGraphics graphics, ViewportTransform viewport) {
        GuiRenderUtil.applyViewport(graphics, viewport);
    }

    public <S> UiHistory<S> history(int capacity) {
        return new UiHistory<>(capacity);
    }

    public ButtonFeedback buttonFeedback() {
        return new ButtonFeedback();
    }

    public ButtonFeedback buttonFeedback(SpringTable<Long> hoverTable) {
        return new ButtonFeedback(hoverTable);
    }

    public void toast(UiToast.Kind kind, Component title, Component details) {
        UiToast.show(kind, title, details);
    }

    /** 创建可交互通知；由调用方注册到 owner。 */
    public UiSwipeToast swipeToast(Screen owner, UiSwipeToast.Placement placement, UiSwipeToast.Content content,
                                   UiSwipeToast.Options options, UiSwipeToast.Style style, UiPreferences preferences,
                                   Consumer<UiSwipeToast.CloseReason> onClose) {
        return new UiSwipeToast(owner, placement, content, options, style, preferences, onClose);
    }

    public void toast(UiToast.Kind kind, Component title, Component details, UiToast.Effect effect) {
        UiToast.show(kind, title, details, effect);
    }

    public void toast(UiToast.Kind kind, Component title, Component details, UiToast.Effect effect,
                      long holdMs, UiPreferences preferences) {
        UiToast.show(kind, title, details, effect, holdMs, preferences);
    }

    public void taskCompleted(Component details) {
        UiToast.taskCompleted(details);
    }
}
