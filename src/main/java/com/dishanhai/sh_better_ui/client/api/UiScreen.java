package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotBackground;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** 山海：通过回调组合现有 API 的原生界面，每次打开独立持有并释放上下文。 */
public final class UiScreen extends Screen {
    @FunctionalInterface
    public interface Draw {
        void draw(UiScreen screen, UiFrame frame);
    }

    @FunctionalInterface
    public interface WidgetScroll {
        boolean scroll(double mouseX, double mouseY, double horizontal, double vertical);
    }

    private final UiScreenBuilder.Definition definition;
    private UiDotBackground backdrop;
    private Screen parent;
    private boolean parentResolved;
    private GuiEventListener returnFocus;
    private UiApiContext context;
    private boolean removed;
    private boolean disposed;
    private boolean handlingFailure;
    private UiButton back;
    private ThemeScope widgetThemes;
    private Bounds widgetViewport;
    private WidgetScroll widgetScroll;
    private UiFrame drawingFrame;
    private final Map<GuiEventListener, Supplier<Bounds>> widgetClips = new IdentityHashMap<>();
    private final Map<GuiEventListener, String> widgetStacks = new IdentityHashMap<>();
    private GuiEventListener hoveredStackWidget;

    UiScreen(UiScreenBuilder.Definition definition) {
        super(definition.title());
        this.definition = definition;
        parent = definition.parent();
        parentResolved = definition.parentSpecified();
        returnFocus = parent == null ? null : parent.getFocused();
    }

    /** 同一次打开及重布局共用上下文。离开后可检查已关闭的上下文，返回本页会创建新上下文。 */
    public UiApiContext context() {
        if (context == null) {
            if (disposed) throw new IllegalStateException("Disposed UI screen");
            context = Objects.requireNonNull(definition.contexts().get());
        }
        return context;
    }

    /** GUI 逻辑坐标中的内容区域，默认扣除返回顶栏，窗口缩小时保持非负尺寸。 */
    public Bounds viewport() {
        int top = barHeight();
        return new Bounds(0, top, Math.max(0, width), Math.max(0, height - top));
    }

    /** True only during this Screen's native draw callbacks for the exact frame. */
    public boolean isDrawing(UiFrame frame) {
        return frame != null && drawingFrame == frame && !disposed && !removed;
    }

    /** Clips native widgets and their pointer targets to a content area for this layout. */
    public void widgetViewport(Bounds area, WidgetScroll scrolling) {
        var content = viewport();
        Objects.requireNonNull(area, "area");
        if (area.x() < content.x() || area.y() < content.y() || area.right() > content.right()
                || area.bottom() > content.bottom() || area.width() < 0 || area.height() < 0) {
            throw new IllegalArgumentException("Widget viewport must be inside the screen content");
        }
        widgetViewport = area;
        widgetScroll = Objects.requireNonNull(scrolling, "scrolling");
    }

    public <T extends AbstractWidget> T add(T widget) {
        if (disposed) throw new IllegalStateException("Disposed UI screen");
        bindTheme(widget);
        return addRenderableWidget(Objects.requireNonNull(widget));
    }

    /** Adds a native widget under a moving nested viewport while keeping native keyboard focus. */
    public <T extends AbstractWidget> T addClipped(T widget, Supplier<Bounds> clip) {
        return addClipped(widget, clip, null);
    }

    /** Widgets sharing a stack ID are picked in reverse paint order within their stack. */
    public <T extends AbstractWidget> T addClipped(T widget, Supplier<Bounds> clip, String stackId) {
        if (disposed) throw new IllegalStateException("Disposed UI screen");
        Objects.requireNonNull(widget, "widget");
        Objects.requireNonNull(clip, "clip");
        bindTheme(widget);
        addWidget(widget);
        widgetClips.put(widget, clip);
        if (stackId != null) widgetStacks.put(widget, stackId);
        addRenderableOnly((graphics, mouseX, mouseY, partialTick) -> {
            Bounds bounds = clip.get();
            if (bounds.width() <= 0 || bounds.height() <= 0) return;
            graphics.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
            try {
                boolean inside = bounds.contains(mouseX, mouseY) && (stackId == null || hoveredStackWidget == widget);
                widget.render(graphics, inside ? mouseX : -1, inside ? mouseY : -1, partialTick);
            } finally {
                graphics.disableScissor();
            }
        });
        return widget;
    }

    /** Theme parent for widgets and containers created during this init/resize. */
    public ThemeScope widgetThemeScope() {
        if (widgetThemes == null || widgetThemes.isClosed()) throw new IllegalStateException("Inactive dynamic UI");
        return widgetThemes;
    }

    private void bindTheme(AbstractWidget widget) {
        if (widgetThemes != null && widget instanceof UiButton button && button.supportsThemeBinding()
                && !button.hasThemeBinding()) button.bindTheme(widgetThemes);
    }

    public UiButton button(int x, int y, int width, int height, String translationKey, Runnable onPress) {
        return button(x, y, width, height, Component.translatable(translationKey), onPress);
    }

    public UiButton button(int x, int y, int width, int height, Component label, Runnable onPress) {
        Objects.requireNonNull(onPress);
        var api = context();
        return add(api.controls().button(new Bounds(x, y, width, height), Objects.requireNonNull(label),
                button -> runCallback(onPress), api.preferences(), false, UiStyle.DEFAULT));
    }

    /** 在客户端执行阶段取得默认父页，保持显式 parent(null) 的返回游戏含义。 */
    public void open() {
        if (disposed) throw new IllegalStateException("Disposed UI screen");
        var client = Minecraft.getInstance();
        client.execute(() -> {
            if (disposed || client.screen == this) return;
            if (!parentResolved) {
                parent = client.screen;
                parentResolved = true;
            }
            returnFocus = parent == null ? null : parent.getFocused();
            client.setScreen(this);
        });
    }

    @Override
    protected void init() {
        if (disposed) {
            onClose();
            return;
        }
        parentResolved = true;
        removed = false;
        try {
            if (context == null || context.isClosed()) {
                context = Objects.requireNonNull(definition.contexts().get());
            }
            if (definition.theme() != null) {
                if (widgetThemes != null) widgetThemes.close();
                var theme = context.themeScope();
                theme.configure(definition.theme());
                widgetThemes = theme.child("widgets");
            }
            back = null;
            widgetViewport = null;
            widgetScroll = null;
            widgetClips.clear();
            widgetStacks.clear();
            if (definition.backButton()) {
                int inset = inset();
                var area = new Bounds(inset, inset, Math.min(100, Math.max(0, width - 2 * inset)),
                        Math.min(22, Math.max(0, height - 2 * inset)));
                back = addWidget(context.controls().button(area, Component.translatable("gui.back"),
                        button -> onClose(), context.preferences(), false, UiStyle.DEFAULT));
                back.setLeadingIcon(GraphicIcon.BACK);
                bindTheme(back);
                back.setSampleBackground(false);
                setInitialFocus(back);
            }
            definition.onInit().accept(this);
        } catch (RuntimeException | Error failure) {
            if (failure instanceof RuntimeException runtime && definition.onError() != null) {
                handleFailure(runtime);
                return;
            }
            try {
                removed();
            } catch (RuntimeException | Error cleanupFailure) {
                preserveCleanupFailure(failure, cleanupFailure);
            }
            throw failure;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return definition.pausesGame();
    }

    @Override
    public void onClose() {
        if (minecraft == null || minecraft.screen != this) return;
        try {
            removed();
        } finally {
            var target = returnTarget(parent, returnFocus);
            if (minecraft.screen == this) minecraft.setScreen(target.screen());
            if (target.screen() != null && minecraft.screen == target.screen()) {
                try {
                    var focus = target.focus();
                    if (!disposed && target.screen() == parent && definition.returnFocus() != null) {
                        focus = definition.returnFocus().get();
                    }
                    if (focus != null && target.screen().children().contains(focus)) target.screen().setFocused(focus);
                } catch (RuntimeException failure) {
                    handleFailure(failure);
                }
            }
        }
    }

    @Override
    public void tick() {
        if (disposed || removed) return;
        super.tick();
        runCallback(() -> definition.onTick().accept(this));
    }

    /** 在客户端线程永久释放页面，用于脚本重载。活动页面会返回父页。 */
    public void dispose() {
        disposed = true;
        disposeMarked();
    }

    /** 在客户端线程先标记整批页面失效，再释放资源，返回时跳过同批旧页面。 */
    public static void disposeAll(Collection<? extends UiScreen> screens) {
        List<UiScreen> snapshot = List.copyOf(screens);
        for (var screen : snapshot) screen.disposed = true;
        Throwable failure = null;
        for (var screen : snapshot) {
            try {
                screen.disposeMarked();
            } catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
    }

    private void disposeMarked() {
        try {
            removed();
        } finally {
            onClose();
        }
    }

    public boolean isDisposed() {
        return disposed;
    }

    record ReturnTarget(Screen screen, GuiEventListener focus) {}

    static ReturnTarget returnTarget(Screen parent, GuiEventListener returnFocus) {
        var visited = Collections.newSetFromMap(new IdentityHashMap<Screen, Boolean>());
        while (parent instanceof UiScreen screen && screen.disposed) {
            if (!visited.add(parent)) return new ReturnTarget(null, null);
            parent = screen.parent;
            returnFocus = screen.returnFocus;
        }
        return new ReturnTarget(parent, returnFocus);
    }

    @Override
    public void removed() {
        if (removed) return;
        removed = true;
        var previousBackdrop = backdrop;
        backdrop = null;
        try {
            Throwable failure = null;
            try {
                if (previousBackdrop != null) previousBackdrop.reset();
                if (context != null) definition.onRemoved().accept(this);
            } catch (RuntimeException | Error error) {
                failure = error;
                throw error;
            } finally {
                try {
                    if (context != null) context.close();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (failure == null) throw cleanupFailure;
                    preserveCleanupFailure(failure, cleanupFailure);
                } finally {
                    super.removed();
                }
            }
        } catch (RuntimeException failure) {
            handleFailure(failure);
        }
    }

    // NeoForge 的 Screen.render 会调用背景方法，背景由当前 render 统一绘制。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public Optional<GuiEventListener> getChildAt(double mouseX, double mouseY) {
        if (disposed || removed) return Optional.empty();
        if (viewport().contains(mouseX, mouseY)) {
            if (widgetViewport != null && !widgetViewport.contains(mouseX, mouseY)) return Optional.empty();
            if (widgetClips.isEmpty()) return super.getChildAt(mouseX, mouseY);
            var widgets = children();
            for (int i = 0; i < widgets.size(); i++) {
                var child = widgets.get(i);
                if (!isWidgetTarget(child, mouseX, mouseY)) continue;
                String stack = widgetStacks.get(child);
                if (stack != null) {
                    for (int layer = widgets.size() - 1; layer > i; layer--) {
                        var candidate = widgets.get(layer);
                        if (stack.equals(widgetStacks.get(candidate)) && isWidgetTarget(candidate, mouseX, mouseY)) {
                            return Optional.of(candidate);
                        }
                    }
                }
                return Optional.of(child);
            }
            return Optional.empty();
        }
        if (back != null && back.isMouseOver(mouseX, mouseY)) return Optional.of(back);
        return Optional.empty();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (disposed || removed) return false;
        if (viewport().contains(mouseX, mouseY)) {
            if (widgetViewport != null && !widgetViewport.contains(mouseX, mouseY)) return false;
            if (widgetClips.isEmpty()) return super.mouseClicked(mouseX, mouseY, button);
            for (var child : children()) {
                if (!inWidgetClip(child, mouseX, mouseY) || !child.mouseClicked(mouseX, mouseY, button)) continue;
                if (!disposed && !removed) {
                    setFocused(child);
                    if (button == 0) setDragging(true);
                }
                return true;
            }
            return false;
        }
        if (back == null || !back.mouseClicked(mouseX, mouseY, button)) return false;
        if (!disposed && !removed) {
            setFocused(back);
            if (button == 0) setDragging(true);
        }
        return true;
    }

    private boolean inWidgetClip(GuiEventListener child, double x, double y) {
        var clip = widgetClips.get(child);
        return clip == null || clip.get().contains(x, y);
    }

    private boolean isWidgetTarget(GuiEventListener child, double x, double y) {
        if (!inWidgetClip(child, x, y)) return false;
        if (widgetStacks.containsKey(child) && child instanceof AbstractWidget widget) {
            return widget.visible && x >= widget.getX() && x < widget.getX() + widget.getWidth()
                    && y >= widget.getY() && y < widget.getY() + widget.getHeight();
        }
        return child.isMouseOver(x, y);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        return mouseScrolled(mouseX, mouseY, 0, vertical);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (disposed || removed) return false;
        var child = getChildAt(mouseX, mouseY);
        if (child.isPresent() && PlatformUiApi.scrollWidget(child.get(), mouseX, mouseY, horizontal, vertical)) {
            return true;
        }
        return widgetViewport != null && widgetViewport.contains(mouseX, mouseY)
                && widgetScroll.scroll(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (disposed || removed) return false;
        if (button == 0 && isDragging() && getFocused() != null) {
            setDragging(false);
            return getFocused().mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (disposed || removed) return;
        if (definition.theme() == null) renderContents(graphics, mouseX, mouseY, partialTick);
        else UiTheme.preview(UiTheme.fromScope(context().themeScope()),
                () -> renderContents(graphics, mouseX, mouseY, partialTick));
    }

    private void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var original = context().frame(graphics, viewport(), partialTick);
        var modal = UiLocalModal.current(this);
        var mouse = modal == null ? original.mouse() : modal.filterPointer(original.mouse());
        var frame = mouse == original.mouse() ? original
                : new UiFrame(graphics, original.viewport(), mouse, original.preferences(), original.nowMs(), partialTick);
        boolean blocked = modal != null && modal.blocksPointer(mouseX, mouseY);
        var backgroundMode = definition.backgroundMode();
        if (backgroundMode == UiScreenBuilder.BackgroundMode.DOTS) {
            if (backdrop == null) backdrop = new UiDotBackground();
            backdrop.render(this, graphics, width, height, frame.preferences());
        } else if (backgroundMode == UiScreenBuilder.BackgroundMode.THEME
                || definition.onRender() == null && ClientUiKernel.hasNativeAuthority()) {
            if (backdrop == null) backdrop = new UiDotBackground();
            backdrop.renderThemed(this, graphics, width, height, frame.preferences(), true);
        } else if (definition.onRender() == null) {
            DarkModeTheme.background(graphics, 0, 0, width, height);
        }
        var viewport = frame.viewport();
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        drawingFrame = frame;
        try {
            if (definition.onRender() != null) runCallback(() -> definition.onRender().draw(this, frame));
            if (disposed || removed) return;
            var widgetArea = widgetViewport == null ? viewport : widgetViewport;
            boolean overContent = !blocked && widgetArea.contains(mouseX, mouseY);
            if (widgetViewport != null) {
                graphics.enableScissor(widgetArea.x(), widgetArea.y(), widgetArea.right(), widgetArea.bottom());
            }
            try {
                if (overContent && !widgetStacks.isEmpty()) hoveredStackWidget = getChildAt(mouseX, mouseY).orElse(null);
                super.render(graphics, overContent ? mouseX : -1, overContent ? mouseY : -1, partialTick);
            } finally {
                hoveredStackWidget = null;
                if (widgetViewport != null) graphics.disableScissor();
            }
            runCallback(() -> definition.onForeground().draw(this, frame));
        } finally {
            drawingFrame = null;
            graphics.disableScissor();
        }
        if (disposed || removed) return;
        if (back != null) {
            graphics.fill(0, 0, width, barHeight(), DarkModeTheme.surfaceColor());
            back.render(graphics, blocked ? -1 : mouseX, blocked ? -1 : mouseY, partialTick);
            int titleX = back.getX() + back.getWidth() + inset();
            int availableWidth = width - inset() - titleX;
            if (availableWidth > 0 && barHeight() >= font.lineHeight) {
                var title = Language.getInstance().getVisualOrder(font.substrByWidth(getTitle(), availableWidth));
                UiFormattedText.draw(graphics, font, title, titleX,
                        (barHeight() - font.lineHeight) / 2, DarkModeTheme.textColor(), false, frame.preferences());
            }
        }
    }

    private int inset() {
        return Math.min(6, Math.max(0, Math.min(width, height)) / 8);
    }

    private int barHeight() {
        if (!definition.backButton()) return 0;
        return Math.min(22, Math.max(0, height - 2 * inset())) + 2 * inset();
    }

    private void runCallback(Runnable callback) {
        if (disposed || removed) return;
        try {
            callback.run();
        } catch (RuntimeException failure) {
            handleFailure(failure);
        }
    }

    private void handleFailure(RuntimeException failure) {
        if (definition.onError() == null || handlingFailure) throw failure;
        handlingFailure = true;
        try {
            try {
                dispose();
            } catch (RuntimeException | Error cleanupFailure) {
                preserveCleanupFailure(failure, cleanupFailure);
            }
            definition.onError().accept(this, failure);
        } finally {
            handlingFailure = false;
        }
    }

    private static void preserveCleanupFailure(Throwable failure, Throwable cleanupFailure) {
        if (failure == cleanupFailure) return;
        if (cleanupFailure instanceof Error error && !(failure instanceof Error)) {
            error.addSuppressed(failure);
            throw error;
        }
        failure.addSuppressed(cleanupFailure);
    }
}
