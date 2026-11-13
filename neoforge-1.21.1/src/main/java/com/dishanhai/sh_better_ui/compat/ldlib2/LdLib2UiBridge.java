package com.dishanhai.sh_better_ui.compat.ldlib2;

import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPullRefresh;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** 山海：由接入方在客户端显式使用的 NeoForge LDLib2 桥接。 */
public final class LdLib2UiBridge {
    private static final Component REFRESH_LABEL = Component.translatable("sh_better_ui.pull_refresh.label");
    private static final Component REFRESH_TOOLTIP = Component.translatable("sh_better_ui.pull_refresh.tooltip");

    private LdLib2UiBridge() {}

    public static UiOverlayHost overlay(Screen owner, UiPreferences preferences) {
        return new UiOverlayHost(owner, preferences);
    }

    /** 供 {@link UiOverlayHost#showBubble} 每帧读取已经裁切的绝对 GUI 坐标。 */
    public static Supplier<Rect2i> anchor(Screen owner, UIElement element) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(element);
        return () -> {
            ScreenRectangle bounds = visibleBounds(owner, element);
            return bounds == null ? null : rect(bounds);
        };
    }

    /** 将把手加入客户端 UI 树，并为其保留独立于列表和槽位的布局区域。 */
    public static PullRefreshHandle createPullRefreshHandle(Screen owner, UIElement viewport, UiPullRefresh refresh,
                                                            BooleanSupplier atTop, UiPreferences preferences) {
        return new PullRefreshHandle(owner, viewport, refresh, atTop, preferences);
    }

    private static ScreenRectangle visibleBounds(Screen owner, UIElement element) {
        var ui = element.getModularUI();
        if (ui == null || ui.isRemoved() || ui.getScreen() != owner) return null;

        ScreenRectangle bounds = transformedBounds(element, element.getPositionX(), element.getPositionY(),
                element.getSizeWidth(), element.getSizeHeight());
        bounds = bounds.intersection(new ScreenRectangle(0, 0, owner.width, owner.height));
        for (UIElement parent = element; bounds != null && parent != null; parent = parent.getParent()) {
            if (!parent.isVisible() || !parent.isDisplayed() || parent.getStyle().opacity() <= 0) return null;
            if (parent != element && !parent.getStyle().overflowVisible()) {
                bounds = bounds.intersection(transformedBounds(parent, parent.getContentX(), parent.getContentY(),
                        parent.getContentWidth(), parent.getContentHeight()));
            }
        }
        return bounds;
    }

    private static ScreenRectangle transformedBounds(UIElement element, float x, float y, float width, float height) {
        if (width <= 0 || height <= 0) return ScreenRectangle.empty();
        var topLeft = element.getWorldMouse(x, y);
        var topRight = element.getWorldMouse(x + width, y);
        var bottomLeft = element.getWorldMouse(x, y + height);
        var bottomRight = element.getWorldMouse(x + width, y + height);
        int left = Mth.floor(Math.min(Math.min(topLeft.x, topRight.x), Math.min(bottomLeft.x, bottomRight.x)));
        int top = Mth.floor(Math.min(Math.min(topLeft.y, topRight.y), Math.min(bottomLeft.y, bottomRight.y)));
        int right = Mth.ceil(Math.max(Math.max(topLeft.x, topRight.x), Math.max(bottomLeft.x, bottomRight.x)));
        int bottom = Mth.ceil(Math.max(Math.max(topLeft.y, topRight.y), Math.max(bottomLeft.y, bottomRight.y)));
        return new ScreenRectangle(left, top, right - left, bottom - top);
    }

    private static Rect2i rect(ScreenRectangle bounds) {
        return new Rect2i(bounds.left(), bounds.top(), bounds.width(), bounds.height());
    }

    private static boolean activationKey(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE;
    }

    public static final class PullRefreshHandle extends UIElement implements UiPointerCapture.Handler {
        private final Screen owner;
        private final UIElement viewport;
        private final UiPullRefresh refresh;
        private final BooleanSupplier atTop;
        private final UiPreferences preferences;
        private final FocusTarget focusTarget = new FocusTarget(this);
        private UiPointerCapture.Registration pressRegistration;
        private int pressedKey = GLFW.GLFW_KEY_UNKNOWN;

        private PullRefreshHandle(Screen owner, UIElement viewport, UiPullRefresh refresh, BooleanSupplier atTop,
                                  UiPreferences preferences) {
            this.owner = Objects.requireNonNull(owner);
            this.viewport = Objects.requireNonNull(viewport);
            this.refresh = Objects.requireNonNull(refresh);
            this.atTop = Objects.requireNonNull(atTop);
            this.preferences = Objects.requireNonNull(preferences);
            getLayout().height(18).widthPercent(100);
            style(style -> style.tooltips(REFRESH_TOOLTIP));
        }

        /** 可在宿主 Screen 的 init 中以 addWidget 注册，加入原生 Tab 顺序和旁白。 */
        public FocusTarget focusTarget() {
            return focusTarget;
        }

        @Override
        protected void onAdded() {
            super.onAdded();
            if (pressRegistration == null) pressRegistration = UiPointerCapture.registerPress(owner, this::press);
        }

        @Override
        public void initScreen(int screenWidth, int screenHeight) {
            super.initScreen(screenWidth, screenHeight);
            if (pressRegistration != null) pressRegistration.close();
            pressRegistration = UiPointerCapture.registerPress(owner, this::press);
        }

        @Override
        protected void onRemoved() {
            if (pressRegistration != null) {
                pressRegistration.close();
                pressRegistration = null;
            }
            if (refresh.isDragging()) UiPointerCapture.cancel(owner);
            refresh.reset(PageTransition.nowMs());
            pressedKey = GLFW.GLFW_KEY_UNKNOWN;
            super.onRemoved();
        }

        private boolean canActivate() {
            UiOverlayHost overlay = UiOverlayHost.current(owner);
            return Minecraft.getInstance().screen == owner && (overlay == null || !overlay.isModalOpen())
                    && isActiveInHierarchy() && visibleBounds(owner, this) != null;
        }

        private boolean press(double x, double y, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !canActivate()
                    || visibleBounds(owner, viewport) == null
                    || getModularUI().hitTestAtScreen((float) x, (float) y) != this) return false;
            if (!refresh.begin(button, x, y, atTop.getAsBoolean(), PageTransition.nowMs())) return false;
            if (!UiPointerCapture.start(owner, button, this)) {
                cancel();
                return false;
            }
            getModularUI().clearFocus();
            if (owner.children().contains(focusTarget)) owner.setFocused(focusTarget);
            return true;
        }

        @Override
        public void drag(double x, double y) {
            if (canActivate() && atTop.getAsBoolean() && visibleBounds(owner, viewport) != null) {
                refresh.drag(GLFW.GLFW_MOUSE_BUTTON_LEFT, x, y, insideViewport(x, y), PageTransition.nowMs());
            } else {
                UiPointerCapture.cancel(owner);
            }
        }

        @Override
        public void release(double x, double y) {
            refresh.release(GLFW.GLFW_MOUSE_BUTTON_LEFT, x, y,
                    canActivate() && atTop.getAsBoolean() && insideViewport(x, y), PageTransition.nowMs());
        }

        private boolean insideViewport(double x, double y) {
            ScreenRectangle visible = visibleBounds(owner, viewport);
            if (visible == null || x < visible.left() || x >= visible.right()
                    || y < visible.top() || y >= visible.bottom()) return false;
            var point = viewport.getLocalMouse((float) x, (float) y);
            return viewport.isIntersectWithPoint(point.x, point.y);
        }

        @Override
        public void cancel() {
            refresh.cancelGesture(PageTransition.nowMs());
        }

        private boolean pressKey(int key) {
            if (!activationKey(key) || !canActivate()) return false;
            pressedKey = key;
            return true;
        }

        private boolean releaseKey(int key) {
            if (pressedKey != key || !activationKey(key)) return false;
            pressedKey = GLFW.GLFW_KEY_UNKNOWN;
            if (canActivate()) refresh.requestRefresh(PageTransition.nowMs());
            return true;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            ScreenRectangle visible = visibleBounds(owner, this);
            if (visible == null) return;
            int x = Mth.floor(getPositionX());
            int y = Mth.floor(getPositionY());
            int width = Mth.ceil(getPositionX() + getSizeWidth()) - x;
            int height = Mth.ceil(getPositionY() + getSizeHeight()) - y;
            refresh.render(context.graphics, context.mc.font, new Rect2i(x, y, width, height), rect(visible),
                    PageTransition.nowMs(), preferences);
            if (width > 2 && height > 2 && (focusTarget.isFocused() || refresh.isDragging())) {
                context.graphics.renderOutline(x + 1, y + 1, width - 2, height - 2, DarkModeTheme.TEXT);
            }
        }
    }

    /** 由 Screen 负责键盘焦点；命中和绘制仍交给对应的 LDLib2 元素。 */
    public static final class FocusTarget implements GuiEventListener, NarratableEntry {
        private final PullRefreshHandle handle;
        private boolean focused;

        private FocusTarget(PullRefreshHandle handle) {
            this.handle = handle;
        }

        @Override
        public ComponentPath nextFocusPath(FocusNavigationEvent event) {
            return isActive() ? GuiEventListener.super.nextFocusPath(event) : null;
        }

        @Override
        public ScreenRectangle getRectangle() {
            ScreenRectangle bounds = visibleBounds(handle.owner, handle);
            return bounds == null ? ScreenRectangle.empty() : bounds;
        }

        @Override
        public boolean isActive() {
            return handle.canActivate();
        }

        @Override
        public void setFocused(boolean focused) {
            this.focused = focused;
            if (!focused) handle.pressedKey = GLFW.GLFW_KEY_UNKNOWN;
        }

        @Override
        public boolean isFocused() {
            return focused;
        }

        @Override
        public boolean keyPressed(int key, int scan, int modifiers) {
            return focused && handle.pressKey(key);
        }

        @Override
        public boolean keyReleased(int key, int scan, int modifiers) {
            return focused && handle.releaseKey(key);
        }

        @Override
        public NarrationPriority narrationPriority() {
            return focused && isActive() ? NarrationPriority.FOCUSED : NarrationPriority.NONE;
        }

        @Override
        public void updateNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, REFRESH_LABEL);
            output.add(NarratedElementType.USAGE, REFRESH_TOOLTIP);
        }
    }
}
