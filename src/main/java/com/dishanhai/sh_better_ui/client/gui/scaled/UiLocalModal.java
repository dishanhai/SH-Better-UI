package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiExpansionController.Config;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiExpansionController.Placement;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiExpansionController.State;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** 山海：一个原生 Screen 持有一个局部模态管理器。在客户端线程调用，坐标使用绝对 GUI 单位。 */
public final class UiLocalModal {
    private static final int NO_BUTTON = -1;
    private static final UiMouseState BLOCKED_MOUSE = new UiMouseState(-1, -1, -1, -1, false, false, false, 0);
    private static WeakReference<UiLocalModal> current = new WeakReference<>(null);

    /** 前景内容的生命周期由 Session 管理。内容可用 session.confirm() 或 session.cancel() 结束交互。 */
    public interface Content {
        /** 绘制在 bounds 的圆角轮廓内。管理器负责底板、Z 层和外接矩形裁切。 */
        void render(GuiGraphics graphics, Frame frame, int mouseX, int mouseY, float partialTick);

        /** 返回 true 表示内容已绘制弹层底板。轮廓、裁切和输入仍由管理器负责。 */
        default boolean renderSurface(GuiGraphics graphics, GuiRect outline, Frame frame) { return false; }

        /** 允许前景内容在几何切换期间按目标坐标绘制，由管理器缩放并淡入淡出。 */
        default boolean animatesContents() { return false; }

        /** 锚定弹层可保留触发按钮，并在点击外部或离开焦点时收起。 */
        default boolean dismissesOnOutsidePress() { return false; }

        /** 返回 true 时获得该按键的拖动与放开，即使指针移出轮廓。 */
        default boolean mousePressed(double x, double y, int button, long now) { return false; }
        default void mouseDragged(double x, double y, int button, long now) {}
        default void mouseReleased(double x, double y, int button, long now) {}
        default void mouseScrolled(double x, double y, double deltaX, double deltaY) {}
        default boolean keyPressed(int key, int scanCode, int modifiers, long now) { return false; }
        default boolean charTyped(char character, int modifiers, long now) { return false; }
        default void keyReleased(int key, int scanCode, int modifiers, long now) {}
        default void cancel(long now) {}
        default void close() {}
    }

    public record Frame(Bounds bounds, State state, float radius, float phaseProgress, boolean focused,
                        long nowMs, UiPreferences preferences) {}

    private final Screen owner;
    private final UiPreferences preferences;
    private final DoubleSupplier activeGuiScale;
    private final LongSupplier clock;
    private final List<Session> sessions = new ArrayList<>();
    private final UiLocalInputRouter<Session> pointer = new UiLocalInputRouter<>(sessions, Session::contains);
    private final Set<Integer> swallowedKeys = new HashSet<>();
    private Session keyboard;
    private int contentKey;
    private long frameTime;
    private boolean creatingContent;
    private boolean interrupting;

    public UiLocalModal(Screen owner, UiPreferences preferences) {
        this(owner, preferences, () -> activeGuiScale(owner), PageTransition::nowMs);
    }

    UiLocalModal(Screen owner, UiPreferences preferences, DoubleSupplier activeGuiScale, LongSupplier clock) {
        this.owner = Objects.requireNonNull(owner);
        this.preferences = Objects.requireNonNull(preferences);
        this.activeGuiScale = activeGuiScale;
        this.clock = clock;
    }

    public boolean open(UiButton source, Bounds viewport, Placement placement, Config config,
                        Function<Session, Content> content, Runnable onConfirm) {
        return open(source, viewport, placement, config, UiLocalModal::hasArea, content, onConfirm);
    }

    public boolean openSlide(UiButton source, Bounds viewport, Placement placement, Config config,
                             UiSlideConfirm.LocalConfig slide, Component label, Runnable onConfirm) {
        Objects.requireNonNull(slide);
        Objects.requireNonNull(label);
        return open(source, viewport, placement, config, UiSlideConfirm::fitsLocal,
                session -> UiSlideConfirm.localContent(session, slide, label), onConfirm);
    }

    private boolean open(UiButton source, Bounds viewport, Placement placement, Config config,
                         Predicate<Bounds> validTarget, Function<Session, Content> factory, Runnable onConfirm) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(viewport);
        Objects.requireNonNull(placement);
        Objects.requireNonNull(config);
        Objects.requireNonNull(factory);
        Objects.requireNonNull(onConfirm);
        double scale = activeGuiScale.getAsDouble();
        if (creatingContent || interrupting || !(scale > 0) || owner.isDragging() || !available(source)) return false;
        validate(scale);
        var existing = current.get();
        if (existing != null && existing != this) return false;
        Bounds clip = clipToScreen(viewport);
        Bounds start = new Bounds(source.getX(), source.getY(), source.getWidth(), source.getHeight());
        if (!hasArea(start) || !hasArea(clip) || !start.equals(UiExpansionController.fit(start, clip))) return false;
        Bounds target = UiExpansionController.fit(Objects.requireNonNull(placement.place(start, clip)), clip);
        if (!validTarget.test(target)) return false;
        var expansion = new UiExpansionController(start, target, config, preferences.reducedMotion(),
                clock.getAsLong());
        for (var active : sessions) {
            if (active.source == source || UiExpansionController.overlaps(expansion.envelope(), active.expansion.envelope())) {
                return false;
            }
        }
        var session = new Session(source, start, scale, expansion, onConfirm);
        creatingContent = true;
        try {
            session.content = Objects.requireNonNull(factory.apply(session));
        } finally {
            creatingContent = false;
        }
        if (!session.available(activeGuiScale.getAsDouble())) {
            session.expansion.interrupt();
            session.content.close();
            return false;
        }
        sessions.add(session);
        current = new WeakReference<>(this);
        return true;
    }

    /** 自定义绘制或轮询输入的宿主用此命中结果屏蔽底层 hover。 */
    public boolean blocksPointer(double x, double y) { return pointer.hit(x, y) != null; }

    /** 为底层轮询式控件屏蔽覆盖区域内的鼠标快照。 */
    public UiMouseState filterPointer(UiMouseState mouse) {
        return blocksPointer(mouse.x(), mouse.y()) ? BLOCKED_MOUSE : mouse;
    }

    public static UiLocalModal current(Screen screen) {
        var modal = current.get();
        if (modal == null) return null;
        modal.validate(modal.activeGuiScale.getAsDouble());
        return modal.owner == screen && current.get() == modal ? modal : null;
    }

    public static void interrupt(Screen screen) {
        var modal = current.get();
        if (modal != null && modal.owner == screen) modal.interrupt();
    }

    /** 页面切换或移除这一组控件时调用。宿主可在新布局中继续使用管理器。 */
    public void interrupt() {
        if (interrupting) return;
        interrupting = true;
        swallowedKeys.clear();
        Throwable failure = null;
        try {
            while (!sessions.isEmpty()) {
                try {
                    finish(sessions.get(sessions.size() - 1), true);
                } catch (RuntimeException | Error error) {
                    if (failure == null) failure = error;
                    else if (failure != error) failure.addSuppressed(error);
                }
            }
        } finally {
            unregisterIfIdle();
            interrupting = false;
        }
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }

    public void prepareRender() {
        frameTime = clock.getAsLong();
        // 从 Screen.Render.Pre 到 Post 使用同一快照。输入事件也只读取这组已采样的轮廓。
        for (int i = sessions.size() - 1; i >= 0; i--) {
            var session = sessions.get(i);
            try {
                session.expansion.advance(frameTime);
                if (session.state() == State.IDLE) finish(session, false);
            } catch (RuntimeException | Error error) {
                fail(session, error);
                throw error;
            }
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < sessions.size(); i++) {
            var session = sessions.get(i);
            try {
                UiExpansionOverlay.render(graphics, session.source, session.content,
                        session.frame(frameTime), session.expansion.outline(), session.start,
                        session.expansion.progress(), session.expansion.centeredMenu(), mouseX, mouseY, partialTick);
            } catch (RuntimeException | Error error) {
                fail(session, error);
                throw error;
            }
            // 内容绘制期间也可能触发页面切换或主动移除。
            if (i >= sessions.size() || sessions.get(i) != session) i--;
        }
    }

    public boolean mouseClicked(double x, double y, int button) {
        var previous = pointer.forget(button);
        if (previous != null) {
            clearNativeDrag(previous, button);
            if (previous.contentButton == button) previous.cancel();
        }
        var session = pointer.hit(x, y);
        var popup = outsidePopup(session);
        if (popup != null) {
            popup.cancel();
            if (popup.start.contains(x, y)) {
                pointer.capture(button, popup);
                return true;
            }
        }
        if (session == null) return false;
        pointer.capture(button, session);
        if (session.state() == State.ACTIVE && session.contentButton == NO_BUTTON) {
            session.dispatch(() -> {
                if (session.content.mousePressed(x, y, button, clock.getAsLong())
                        && session.state() == State.ACTIVE) {
                    session.contentButton = button;
                    if (!owner.isDragging()) owner.setFocused(session.source);
                }
            });
        }
        return true;
    }

    public boolean mouseDragged(double x, double y, int button) {
        var session = pointer.dragTarget(button);
        if (session == null) return false;
        if (session.contentButton == button && session.state() == State.ACTIVE) {
            session.dispatch(() -> session.content.mouseDragged(x, y, button, clock.getAsLong()));
        }
        return true;
    }

    public boolean mouseReleased(double x, double y, int button) {
        var session = pointer.forget(button);
        if (session == null) return false;
        clearNativeDrag(session, button);
        if (session.contentButton == button && session.state() == State.ACTIVE) {
            session.contentButton = NO_BUTTON;
            session.dispatch(() -> session.content.mouseReleased(x, y, button, clock.getAsLong()));
        }
        return true;
    }

    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        var session = pointer.hit(x, y);
        if (session == null) return false;
        if (session.state() == State.ACTIVE) {
            session.dispatch(() -> session.content.mouseScrolled(x, y, deltaX, deltaY));
        }
        return true;
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (swallowedKeys.contains(key)) return true;
        var focused = owner.getFocused();
        var session = forSource(focused);
        if (key == GLFW.GLFW_KEY_TAB && session != null && session.content.dismissesOnOutsidePress()) {
            session.cancel();
            return false;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && !sessions.isEmpty()) {
            swallowedKeys.add(key);
            (session == null ? sessions.get(sessions.size() - 1) : session).cancel();
            return true;
        }
        if (session != null) {
            if (session.state() == State.ACTIVE && keyboard == null) {
                Session target = session;
                target.dispatch(() -> {
                    if (target.content.keyPressed(key, scanCode, modifiers, clock.getAsLong())) {
                        swallowedKeys.add(key);
                        if (target.state() == State.ACTIVE) {
                            keyboard = target;
                            contentKey = key;
                        }
                    }
                });
            }
            if (swallowedKeys.contains(key)) return true;
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
                swallowedKeys.add(key);
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_TAB) return false;
        if (!focusedCovered()) return false;
        swallowedKeys.add(key);
        return true;
    }

    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (!swallowedKeys.remove(key)) return false;
        if (keyboard != null && contentKey == key) {
            var session = keyboard;
            keyboard = null;
            session.dispatch(() -> session.content.keyReleased(key, scanCode, modifiers, clock.getAsLong()));
        }
        unregisterIfIdle();
        return true;
    }

    public boolean charTyped(char character, int modifiers) {
        var session = forSource(owner.getFocused());
        if (session == null || session.state() != State.ACTIVE) return focusedCovered();
        return session.dispatch(() -> session.content.charTyped(character, modifiers, clock.getAsLong()));
    }

    public boolean focusedCovered() {
        if (!(owner.getFocused() instanceof AbstractWidget widget) || forSource(widget) != null) return false;
        return blocksPointer(widget.getX() + widget.getWidth() * 0.5, widget.getY() + widget.getHeight() * 0.5);
    }

    public boolean hidesFocusedTooltip() {
        return forSource(owner.getFocused()) != null || focusedCovered();
    }

    static boolean hidesSource(UiButton button) {
        var modal = current.get();
        var session = modal == null ? null : modal.forSource(button);
        return session != null && !session.content.dismissesOnOutsidePress();
    }

    static boolean coversMouse(double x, double y) {
        var modal = current.get();
        return modal != null && modal.blocksPointer(x, y);
    }

    static void captureSourcePress(UiButton button, int mouseButton) {
        var modal = current.get();
        if (modal == null) return;
        var session = modal.forSource(button);
        if (session == null) return;
        session.sourceButton = mouseButton;
        modal.pointer.capture(mouseButton, session);
    }

    private void validate(double scale) {
        if (!(scale > 0)) {
            interrupt();
            return;
        }
        for (int i = sessions.size() - 1; i >= 0; i--) {
            var session = sessions.get(i);
            if (!session.available(scale)) finish(session, true);
            else if (session.content.dismissesOnOutsidePress() && owner.getFocused() != session.source) session.cancel();
        }
        if (keyboard != null && owner.getFocused() != keyboard.source) keyboard.cancel();
    }

    private static double activeGuiScale(Screen owner) {
        var client = Minecraft.getInstance();
        if (client == null || client.screen != owner || !client.isWindowActive() || client.getOverlay() != null) return 0;
        var full = UiOverlayHost.current(owner);
        return full == null || !full.isModalOpen() ? client.getWindow().getGuiScale() : 0;
    }

    private boolean available(UiButton source) {
        return source.visible && source.active && owner.children().contains(source);
    }

    private Session forSource(Object source) {
        for (int i = 0; i < sessions.size(); i++) {
            var session = sessions.get(i);
            if (session.source == source) return session;
        }
        return null;
    }

    private Session outsidePopup(Session hit) {
        for (int i = sessions.size() - 1; i >= 0; i--) {
            var session = sessions.get(i);
            if (session != hit && session.content.dismissesOnOutsidePress()) return session;
        }
        return null;
    }

    private Bounds clipToScreen(Bounds viewport) {
        int x = UiMath.clamp(viewport.x(), 0, owner.width);
        int y = UiMath.clamp(viewport.y(), 0, owner.height);
        return new Bounds(x, y, Math.max(0, Math.min(owner.width, viewport.right()) - x),
                Math.max(0, Math.min(owner.height, viewport.bottom()) - y));
    }

    private static boolean hasArea(Bounds bounds) { return bounds.width() > 0 && bounds.height() > 0; }

    private void clearNativeDrag(Session session, int button) {
        if (session.sourceButton != button) return;
        session.sourceButton = NO_BUTTON;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && owner.getFocused() == session.source) owner.setDragging(false);
    }

    private void finish(Session session, boolean interrupted) {
        if (!sessions.remove(session)) return;
        if (interrupted) session.expansion.interrupt();
        if (keyboard == session) keyboard = null;
        pointer.drain(session, button -> {
            clearNativeDrag(session, button);
            UiPointerCapture.drainRelease(button);
        });
        unregisterIfIdle();
        try {
            if (interrupted) session.content.cancel(clock.getAsLong());
        } finally {
            session.content.close();
        }
    }

    private void unregisterIfIdle() {
        if (sessions.isEmpty() && swallowedKeys.isEmpty() && current.get() == this) current.clear();
    }

    private void fail(Session session, Throwable error) {
        try {
            finish(session, true);
        } catch (RuntimeException | Error cleanup) {
            if (cleanup != error) error.addSuppressed(cleanup);
        }
    }

    public final class Session {
        private final UiButton source;
        private final Bounds start;
        private final int screenWidth = owner.width;
        private final int screenHeight = owner.height;
        private final double guiScale;
        private final UiExpansionController expansion;
        private final Runnable onConfirm;
        private Content content;
        private int sourceButton = NO_BUTTON;
        private int contentButton = NO_BUTTON;

        private Session(UiButton source, Bounds start, double guiScale, UiExpansionController expansion,
                        Runnable onConfirm) {
            this.source = source;
            this.start = start;
            this.guiScale = guiScale;
            this.expansion = expansion;
            this.onConfirm = onConfirm;
        }

        public State state() { return expansion.state(); }
        public Bounds target() { return expansion.target(); }
        public UiPreferences preferences() { return preferences; }

        public boolean confirm() {
            if (state() != State.ACTIVE || !sessions.contains(this)) return false;
            if (!available(activeGuiScale.getAsDouble())) {
                finish(this, true);
                return false;
            }
            if (!expansion.confirm(clock.getAsLong())) return false;
            if (keyboard == this) keyboard = null;
            contentButton = NO_BUTTON;
            dispatch(onConfirm);
            return true;
        }

        public boolean cancel() {
            long now = clock.getAsLong();
            if (!expansion.cancel(now)) return false;
            if (keyboard == this) keyboard = null;
            contentButton = NO_BUTTON;
            dispatch(() -> content.cancel(now));
            return true;
        }

        private Frame frame(long now) {
            return new Frame(expansion.target(), state(), expansion.radius(), expansion.phaseProgress(), source.isFocused(),
                    now, preferences);
        }

        private boolean contains(double x, double y) {
            return UiExpansionController.contains(expansion.outline(), expansion.radius(), x, y);
        }

        private boolean available(double scale) {
            return screenWidth == owner.width && screenHeight == owner.height
                    && guiScale == scale
                    && UiLocalModal.this.available(source) && start.x() == source.getX() && start.y() == source.getY()
                    && start.width() == source.getWidth() && start.height() == source.getHeight();
        }

        private void dispatch(Runnable action) {
            dispatch(() -> {
                action.run();
                return null;
            });
        }

        private <T> T dispatch(java.util.function.Supplier<T> action) {
            try {
                return action.get();
            } catch (RuntimeException | Error error) {
                fail(this, error);
                throw error;
            }
        }
    }
}
