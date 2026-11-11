package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 山海：在原画面内显示模态框、动作表和被动气泡。由接入方在客户端线程持有此实例。 */
public final class UiOverlayHost {
    private static final int Z = 450;
    private static final int MODAL_Z_STEP = 12;
    private static final int SCRIM = 0x840F172A;
    private static final int PANEL_WIDTH = 300;
    private static final int GAP = 4;
    private static final int ICON_INSET = GraphicIcon.PIXELS + GAP;
    private static final int MIN_ICON_TEXT_WIDTH = 24;
    private static final int PANEL_PRIORITY = 30;
    private static WeakReference<UiOverlayHost> active = new WeakReference<>(null);

    public record Action(Component label, Runnable onChoose, UiConfirmation.Prompt confirmation, GraphicIcon icon) {
        public Action {
            Objects.requireNonNull(label);
            Objects.requireNonNull(onChoose);
        }

        public Action(Component label, Runnable onChoose) {
            this(label, onChoose, null);
        }

        public Action(Component label, Runnable onChoose, UiConfirmation.Prompt confirmation) {
            this(label, onChoose, confirmation, confirmation == null ? null : GraphicIcon.WARNING);
        }
    }

    private final Screen owner;
    private final UiPreferences preferences;
    private final Set<Integer> capturedButtons = new HashSet<>();
    private final Set<Integer> capturedKeys = new HashSet<>();
    private Runnable modalOpened = () -> {};
    private Consumer<Boolean> modalClosed = activeOwner -> {};
    private GuiEventListener previousFocus;
    private boolean ownerClosing;
    private boolean settleHostFocus;
    private Layer modal;
    private com.dishanhai.sh_better_ui.kernel.api.OwnershipLease modalLease;

    private void ownModal() {
        if (modal == null || modalLease != null) return;
        modalLease = com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.ownNativeModal(owner, () -> {
            modalLease = null;
            if (modal != null) ownerClosed();
        });
    }

    private void releaseModal() {
        var lease = modalLease;
        modalLease = null;
        if (lease != null) lease.close();
    }
    private UiButton pressed;
    private Bubble bubble;

    public UiOverlayHost(Screen owner, UiPreferences preferences) {
        this.owner = Objects.requireNonNull(owner);
        this.preferences = Objects.requireNonNull(preferences);
    }

    /** 返回当前登记在指定画面上的宿主，包括仍需拦截按键放开的宿主。 */
    public static UiOverlayHost current(Screen screen) {
        UiOverlayHost host = active.get();
        return host != null && host.owner == screen ? host : null;
    }

    static boolean hidesSource(UiButton button) {
        UiOverlayHost host = active.get();
        return host != null && host.modal != null && host.modal.source == button
                && Minecraft.getInstance().screen == host.owner && host.sourceAvailable(button);
    }

    /** 附加平台焦点桥接。仅在模态开关时调用，关闭参数表示原画面仍可接收焦点。 */
    public void setModalLifecycle(Runnable opened, Consumer<Boolean> closed) {
        modalOpened = Objects.requireNonNull(opened);
        modalClosed = Objects.requireNonNull(closed);
    }

    public boolean isModalOpen() {
        return modal != null;
    }

    /** 供宿主的显式打开意图调用，保留正在收起的模态框和当前轮廓。 */
    public boolean reopenModal() {
        if (modal == null || !modal.closing() || Minecraft.getInstance().screen != owner) return false;
        show(modal);
        return true;
    }

    public void showNotice(Component title, Component details) {
        showNotice(title, details, GraphicIcon.INFO);
    }

    public void showNotice(Component title, Component details, GraphicIcon icon) {
        show(new Layer(title, Objects.requireNonNull(details), icon, this::dismissModal, List.of(),
                List.of(new Action(CommonComponents.GUI_OK, this::dismissModal, null, GraphicIcon.SUCCESS)), null));
    }

    public void showDialog(UiConfirmation.Prompt prompt, Runnable onConfirm) {
        showDialog(prompt, onConfirm, GraphicIcon.WARNING);
    }

    public void showDialog(UiConfirmation.Prompt prompt, Runnable onConfirm, GraphicIcon icon) {
        Objects.requireNonNull(onConfirm);
        show(confirmation(prompt, this::dismissModal, onConfirm, icon, null));
    }

    /** 从以画面坐标绘制的原生液态玻璃胶囊按钮展开，收合后归还源按钮焦点。 */
    public void showDialogFrom(UiButton source, UiConfirmation.Prompt prompt, Runnable onConfirm) {
        showDialogFrom(source, prompt, onConfirm, GraphicIcon.WARNING);
    }

    /** 可指定标题图标。源按钮不可用时采用普通对话框。 */
    public void showDialogFrom(UiButton source, UiConfirmation.Prompt prompt, Runnable onConfirm, GraphicIcon icon) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(onConfirm);
        show(confirmation(prompt, this::dismissModal, onConfirm, icon, sourceAvailable(source) ? source : null));
    }

    public void showActions(Component title, List<Action> actions) {
        showActions(title, actions, GraphicIcon.LIST);
    }

    public void showActions(Component title, List<Action> actions, GraphicIcon icon) {
        show(new Layer(title, null, icon, this::dismissModal, List.copyOf(actions),
                List.of(new Action(CommonComponents.GUI_CANCEL, this::dismissModal, null, GraphicIcon.CLOSE)), null));
    }

    /** 锚点使用画面坐标。暂时不可见时可返回 null，气泡不接收焦点或输入。 */
    public void showBubble(Supplier<Rect2i> anchor, Component message) {
        showBubble(anchor, message, GraphicIcon.BUBBLE);
    }

    public void showBubble(Supplier<Rect2i> anchor, Component message, GraphicIcon icon) {
        Objects.requireNonNull(anchor);
        Objects.requireNonNull(message);
        if (register()) bubble = new Bubble(anchor, message, icon);
    }

    public void hideBubble() {
        bubble = null;
        unregisterIfIdle();
    }

    /** 主动关闭并归还焦点。已捕获的按下事件仍由本实例接收对应的放开。 */
    public void close() {
        bubble = null;
        dismissModal();
        unregisterIfIdle();
    }

    /** 画面退出时由平台调用。释放所有输入，放弃恢复已离开的画面焦点。 */
    public void ownerClosed() {
        if (ownerClosing) return;
        ownerClosing = true;
        boolean wasModal = modal != null;
        if (wasModal) modal.enter.close();
        modal = null;
        releaseModal();
        bubble = null;
        pressed = null;
        previousFocus = null;
        settleHostFocus = false;
        capturedButtons.forEach(UiPointerCapture::drainRelease);
        capturedButtons.clear();
        capturedKeys.clear();
        unregisterIfIdle();
        try {
            if (wasModal) modalClosed.accept(false);
        } finally {
            ownerClosing = false;
        }
    }

    private Layer confirmation(UiConfirmation.Prompt prompt, Runnable onCancel, Runnable onConfirm,
                               GraphicIcon icon, UiButton source) {
        Objects.requireNonNull(prompt);
        return new Layer(prompt.title(), prompt.details(), icon, onCancel, List.of(), List.of(
                new Action(prompt.confirm(), () -> finish(onConfirm), null, GraphicIcon.SUCCESS),
                new Action(prompt.cancel(), onCancel, null, GraphicIcon.CLOSE)), source);
    }

    private boolean sourceAvailable(UiButton source) {
        return source != null && source.isCapsule() && source.visible && source.active
                && owner.width >= 14 && owner.height >= 14 && owner.children().contains(source)
                && intersectsViewport(new Rect2i(source.getX(), source.getY(), source.getWidth(), source.getHeight()),
                        owner.width, owner.height);
    }

    private boolean register() {
        if (ownerClosing || Minecraft.getInstance().screen != owner) return false;
        UiOverlayHost existing = current(owner);
        if (existing != null && existing != this) throw new IllegalStateException();
        active = new WeakReference<>(this);
        return true;
    }

    private void unregisterIfIdle() {
        if (modal == null && bubble == null && capturedButtons.isEmpty() && capturedKeys.isEmpty()
                && active.get() == this) active.clear();
    }

    private void show(Layer layer) {
        // 已由底层控件接收的拖拽应先完成，再打开新的模态框。
        if (modal == null && owner.isDragging()) return;
        if (!register()) return;
        UiLocalModal.interrupt(owner);
        boolean opening = modal == null;
        if (!opening && modal != layer) {
            modal.buttons.get(modal.focused).setFocused(false);
            if (layer.source != null && modal.source == layer.source) layer.enter = modal.enter;
        }
        modal = layer;
        ownModal();
        layer.buttons.get(layer.focused).setFocused(true);
        var policy = layer.enter.target() == 0f && layer.enter.running()
                ? InterruptibleAnimation.Policy.REVERSE : InterruptibleAnimation.Policy.RETARGET;
        layer.enter.transitionTo(1f, PageTransition.nowMs(), policy, PANEL_PRIORITY);
        layer.closeRevision = 0L;
        pressed = null;
        if (opening) {
            UiPointerCapture.cancel(owner);
            previousFocus = owner.getFocused();
            settleHostFocus = true;
            owner.setFocused(null);
            owner.setDragging(false);
            modalOpened.run();
        }
        if (modal == layer) narrate(layer, true);
    }

    private void settleFocus() {
        // 父控件可能在触发回调返回后才成为焦点。
        GuiEventListener focus = owner.getFocused();
        if (focus != null) {
            if (settleHostFocus) previousFocus = focus;
            owner.setFocused(null);
        }
        settleHostFocus = false;
        owner.setDragging(false);
    }

    private void dismissModal() {
        if (modal == null) return;
        if (Minecraft.getInstance().screen == owner && !preferences.reducedMotion() && sourceAvailable(modal.source)) {
            if (!modal.closing()) {
                modal.closeRevision = modal.enter.transitionTo(0f, PageTransition.nowMs(),
                        InterruptibleAnimation.Policy.REVERSE, PANEL_PRIORITY);
                modal.buttons.get(modal.focused).setFocused(false);
                modal.rendered = false;
                pressed = null;
            }
            return;
        }
        completeDismissal();
    }

    private void completeDismissal() {
        boolean restore = Minecraft.getInstance().screen == owner;
        if (restore) settleFocus();
        GuiEventListener target = restore && sourceAvailable(modal.source) ? modal.source : previousFocus;
        modal.enter.close();
        modal = null;
        releaseModal();
        pressed = null;
        previousFocus = null;
        settleHostFocus = false;
        if (restore && target != null && owner.children().contains(target)
                && (!(target instanceof AbstractWidget widget) || (widget.visible && widget.active))) {
            owner.setFocused(target);
        }
        unregisterIfIdle();
        modalClosed.accept(restore);
    }

    private void finish(Runnable action) {
        dismissModal();
        if (Minecraft.getInstance().screen == owner) action.run();
    }

    private void choose(Layer sheet, Action action) {
        if (modal != sheet) return;
        if (action.confirmation() == null) finish(action.onChoose());
        else {
            Layer confirmation = confirmation(action.confirmation(), () -> show(sheet), action.onChoose(),
                    GraphicIcon.WARNING, null);
            confirmation.underlay = sheet;
            show(confirmation);
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (Minecraft.getInstance().screen != owner) return;
        ownModal();
        Layer layer = modal;
        boolean morph = layer != null && sourceAvailable(layer.source);
        if (layer != null && layer.closing() && !morph) {
            layer.enter.finish();
            queueDismissal(layer);
            return;
        }
        if (layer == null && bubble == null) return;
        graphics.flush();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, Z);
            if (layer != null) {
                settleFocus();
                long now = PageTransition.nowMs();
                if (layer.source != null && !morph) layer.enter.finish();
                float progress = layer.progress(now);
                if (layer.closing() && progress == 0f) layer.enter.finish();
                int scrim = morph ? GuiMotion.lerpColor(SCRIM & 0x00FFFFFF, SCRIM, progress) : SCRIM;
                if ((scrim >>> 24) != 0) graphics.fill(0, 0, owner.width, owner.height, scrim);
                Layer underlay = layer.underlay;
                if (underlay != null) {
                    underlay.render(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, partialTick,
                            now, underlay.progress(now), false);
                    graphics.flush();
                    graphics.pose().translate(0, 0, MODAL_Z_STEP);
                    graphics.fill(0, 0, owner.width, owner.height, 0x300F172A);
                }
                layer.render(graphics, mouseX, mouseY, partialTick, now, progress, morph);
            } else renderBubble(graphics, bubble);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
        if (layer != null && layer.closing() && !layer.enter.running()) queueDismissal(layer);
    }

    private void queueDismissal(Layer layer) {
        long revision = layer.closeRevision;
        UiFrameScheduler.afterFrame(owner, () -> {
            if (layer == modal && layer.closeRevision == revision && layer.enter.takeCompletion(revision)) {
                completeDismissal();
            }
        });
    }

    private void renderBubble(GuiGraphics graphics, Bubble bubble) {
        Rect2i anchor = bubble.anchor().get();
        if (anchor == null || !intersectsViewport(anchor, owner.width, owner.height)) return;
        int availableWidth = Math.min(220, owner.width - 2 * GAP);
        int availableHeight = owner.height - 2 * GAP;
        if (availableWidth <= 2 * GAP || availableHeight <= 2 * GAP) return;
        Font font = Minecraft.getInstance().font;
        int inset = iconInset(bubble.icon(), availableWidth - 2 * GAP, availableHeight - 2 * GAP);
        var lines = font.split(bubble.message(), availableWidth - 2 * GAP - inset);
        int width = Math.min(availableWidth, lines.stream().mapToInt(font::width).max().orElse(0) + inset + 2 * GAP);
        int textHeight = lines.size() * font.lineHeight;
        int height = Math.min(availableHeight, Math.max(textHeight, inset > 0 ? GraphicIcon.PIXELS : 0) + 2 * GAP);
        Bounds box = placeBubble(anchor, owner.width, owner.height, width, height);
        LiquidGlassTheme.popover(graphics, box.x(), box.y(), box.width(), box.height(), preferences);
        graphics.enableScissor(box.x(), box.y(), box.right(), box.bottom());
        try {
            int visible = Math.min(lines.size(), (height - 2 * GAP) / font.lineHeight);
            int textY = box.y() + (height - visible * font.lineHeight) / 2;
            if (inset > 0) {
                bubble.icon().draw(graphics, box.x() + GAP, box.y() + (height - GraphicIcon.PIXELS) / 2,
                        1, iconColor(), preferences);
            }
            for (int line = 0; line < visible; line++) {
                var text = line == visible - 1 && visible < lines.size()
                        ? CommonComponents.ELLIPSIS.getVisualOrderText() : lines.get(line);
                graphics.drawString(font, text, box.x() + GAP + inset, textY + line * font.lineHeight,
                        LiquidGlassTheme.textColor(), false);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    static int iconInset(GraphicIcon icon, int width, int height) {
        return icon != null && width >= ICON_INSET + MIN_ICON_TEXT_WIDTH && height >= GraphicIcon.PIXELS
                ? ICON_INSET : 0;
    }

    private int iconColor() {
        return preferences.highContrast() ? LiquidGlassTheme.textColor() : LiquidGlassTheme.accentColor();
    }

    static boolean intersectsViewport(Rect2i anchor, int width, int height) {
        return width > 0 && height > 0 && anchor.getWidth() > 0 && anchor.getHeight() > 0
                && anchor.getX() < width && anchor.getY() < height
                && (long) anchor.getX() + anchor.getWidth() > 0 && (long) anchor.getY() + anchor.getHeight() > 0;
    }

    static Bounds placeBubble(Rect2i anchor, int viewportWidth, int viewportHeight, int width, int height) {
        int x = UiMath.clamp(anchor.getX(), GAP, viewportWidth - GAP - width);
        long above = (long) anchor.getY() - GAP;
        long below = (long) anchor.getY() + anchor.getHeight() + GAP;
        long spaceBelow = viewportHeight - GAP - below;
        long spaceAbove = above - GAP;
        long y = spaceBelow >= height || spaceBelow >= spaceAbove ? below : above - height;
        return new Bounds(x, (int) UiMath.clamp(y, GAP, viewportHeight - GAP - height), width, height);
    }

    public boolean mouseClicked(double x, double y, int button) {
        Layer layer = modal;
        if (layer == null) {
            // 新按下已开始另一手势，旧放开可能在窗口失焦时丢失。
            capturedButtons.remove(button);
            unregisterIfIdle();
            return false;
        }
        capturedButtons.add(button);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressed = layer.buttonAt(x, y);
            if (pressed != null) layer.focus(pressed, false);
        }
        return true;
    }

    public boolean mouseReleased(double x, double y, int button) {
        Layer layer = modal;
        boolean captured = capturedButtons.remove(button);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            UiButton target = pressed;
            pressed = null;
            if (captured && target != null && layer != null && target == layer.buttonAt(x, y)) activate(target);
        }
        unregisterIfIdle();
        return layer != null || captured;
    }

    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return modal != null || capturedButtons.contains(button);
    }

    public boolean mouseScrolled(double x, double y, double deltaY) {
        Layer layer = modal;
        if (layer == null) return false;
        if (deltaY != 0 && pointerInViewport(x, y, owner.width, owner.height)
                && layer.rendered && layer.layout() && layer.body.contains(x, y)) {
            layer.scrollBy(deltaY > 0 ? -layer.lineHeight * 3 : layer.lineHeight * 3);
        }
        return true;
    }

    public boolean keyPressed(int key, int scan, int mods) {
        Layer layer = modal;
        if (layer == null) return capturedKeys.contains(key);
        boolean firstPress = capturedKeys.add(key);
        if (layer.closing() || (layer.source != null && !layer.rendered && key != GLFW.GLFW_KEY_ESCAPE)) return true;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> { if (firstPress) layer.cancel.run(); }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                if (firstPress) activate(layer.buttons.get(layer.focused));
            }
            case GLFW.GLFW_KEY_TAB -> layer.moveFocus((mods & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1);
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                int direction = key == GLFW.GLFW_KEY_UP ? -1 : 1;
                if (layer.sheet) layer.moveFocus(direction);
                else if (layer.layout()) layer.scrollBy(direction * layer.lineHeight);
            }
            case GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN -> {
                int direction = key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1;
                if (layer.layout()) layer.scrollBy(direction * Math.max(layer.lineHeight, layer.body.height()));
            }
            default -> {}
        }
        return true;
    }

    public boolean keyReleased(int key, int scan, int mods) {
        boolean captured = capturedKeys.remove(key);
        unregisterIfIdle();
        return modal != null || captured;
    }

    public boolean charTyped(char codePoint, int mods) {
        return modal != null || (codePoint == ' ' && capturedKeys.contains(GLFW.GLFW_KEY_SPACE));
    }

    private static void activate(UiButton button) {
        button.playDownSound(Minecraft.getInstance().getSoundManager());
        button.onPress();
    }

    private static void narrate(Layer layer, boolean includeHeading) {
        var narration = Component.empty();
        if (includeHeading) {
            narration.append(layer.title).append(CommonComponents.NEW_LINE);
            if (layer.details != null) narration.append(layer.details).append(CommonComponents.NEW_LINE);
        }
        narration.append(layer.buttons.get(layer.focused).getMessage());
        Minecraft.getInstance().getNarrator().sayNow(narration);
    }

    private record Bubble(Supplier<Rect2i> anchor, Component message, GraphicIcon icon) {}

    record Bounds(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
        Bounds offsetY(int delta) { return new Bounds(x, y + delta, width, height); }
    }

    static int entryOffset(float progress, boolean sheet) {
        return Math.round((1f - GuiMotion.clamp01(progress)) * (sheet ? 14 : 9));
    }

    static boolean pointerInViewport(double x, double y, int width, int height) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private final class Layer {
        private final Component title;
        private final Component details;
        private final GraphicIcon icon;
        private final Runnable cancel;
        private final boolean sheet;
        private final int rowCount;
        private final List<UiButton> buttons = new ArrayList<>();
        private final List<Integer> rowTops = new ArrayList<>();
        private final UiButton source;
        private InterruptibleAnimation enter;
        private long closeRevision;
        private Layer underlay;
        private List<FormattedCharSequence> titleLines;
        private List<FormattedCharSequence> detailLines;
        private Bounds panel;
        private Bounds body;
        private Font font;
        private Language language;
        private int width;
        private int height;
        private int lineHeight;
        private int headingHeight;
        private int headingInset;
        private int contentHeight;
        private int focused;
        private int scroll;
        private int offsetY;
        private boolean rendered;

        private Layer(Component title, Component details, GraphicIcon icon, Runnable cancel,
                      List<Action> rows, List<Action> footer, UiButton source) {
            this.title = Objects.requireNonNull(title);
            this.details = details;
            this.icon = icon;
            this.cancel = cancel;
            this.sheet = details == null;
            this.rowCount = rows.size();
            this.source = source;
            this.enter = new InterruptibleAnimation(source == null ? Spring.PANEL : Spring.FLUID_MORPH);
            for (Action row : rows) buttons.add(button(row, () -> choose(this, row), false));
            for (int index = 0; index < footer.size(); index++) {
                Action item = footer.get(index);
                buttons.add(button(item, item.onChoose(), footer.size() > 1 && index == 0));
            }
            focused = buttons.size() - 1;
        }

        private UiButton button(Action action, Runnable onPress, boolean primary) {
            UiButton result = new UiButton(0, 0, 0, 0, action.label(), button -> {
                if (modal == this) onPress.run();
            }, preferences, primary);
            result.setLeadingIcon(action.icon());
            result.setTooltip(null);
            result.setSampleBackground(false);
            return result;
        }

        private boolean layout() {
            Font currentFont = Minecraft.getInstance().font;
            Language currentLanguage = Language.getInstance();
            if (owner.width < 14 || owner.height < 14 || currentFont == null) return false;
            if (width == owner.width && height == owner.height && font == currentFont && language == currentLanguage) {
                return true;
            }
            width = owner.width;
            height = owner.height;
            font = currentFont;
            language = currentLanguage;
            lineHeight = font.lineHeight + 2;
            int margin = width >= 64 && height >= 64 ? 8 : 2;
            int padding = width >= 64 && height >= 64 ? 8 : 2;
            int panelWidth = Math.min(PANEL_WIDTH, width - 2 * margin);
            int innerWidth = panelWidth - 2 * padding;
            int maxHeight = height - 2 * margin;
            int footerCount = buttons.size() - rowCount;
            int columnGap = footerCount == 2 ? Math.min(GAP, innerWidth - 2) : 0;
            int footerWidth = (innerWidth - columnGap) / footerCount;
            int footerHeight = 18;
            for (int i = rowCount; i < buttons.size(); i++) {
                footerHeight = Math.max(footerHeight, buttonHeight(buttons.get(i), footerWidth));
            }
            footerHeight = Math.min(footerHeight, Math.max(1, (maxHeight - 2 * padding - 1) * 2 / 3));
            int verticalGap = Math.min(GAP, maxHeight - 2 * padding - footerHeight - 1);
            int maxBodyHeight = maxHeight - 2 * padding - verticalGap - footerHeight;
            headingInset = iconInset(icon, innerWidth, maxBodyHeight);
            titleLines = font.split(title, innerWidth - headingInset);
            detailLines = details == null ? List.of() : font.split(details, innerWidth);
            headingHeight = Math.max(titleLines.size() * lineHeight, headingInset > 0 ? GraphicIcon.PIXELS : 0);
            contentHeight = headingHeight + GAP + detailLines.size() * lineHeight;
            rowTops.clear();
            for (int i = 0; i < rowCount; i++) {
                UiButton row = buttons.get(i);
                rowTops.add(contentHeight);
                row.setHeight(Math.min(buttonHeight(row, innerWidth), maxBodyHeight));
                contentHeight += row.getHeight() + GAP;
            }
            int panelHeight = Math.min(maxHeight, 2 * padding + verticalGap + footerHeight + contentHeight);
            int x = (width - panelWidth) / 2;
            int y = (sheet ? height - margin - panelHeight : (height - panelHeight) / 2) + offsetY;
            panel = new Bounds(x, y, panelWidth, panelHeight);
            int footerY = panel.bottom() - padding - footerHeight;
            body = new Bounds(x + padding, y + padding, innerWidth, footerY - verticalGap - y - padding);
            for (int i = 0; i < footerCount; i++) {
                position(buttons.get(rowCount + i), body.x() + i * (footerWidth + columnGap), footerY,
                        footerWidth, footerHeight);
            }
            scrollBy(0);
            revealFocus();
            return true;
        }

        private int buttonHeight(UiButton button, int buttonWidth) {
            return Math.max(18, font.split(button.getMessage(), button.labelWidth(buttonWidth, 18)).size() * font.lineHeight + 6);
        }

        private void scrollBy(int delta) {
            scroll = UiMath.clamp(scroll + delta, 0, Math.max(0, contentHeight - body.height()));
            for (int i = 0; i < rowCount; i++) {
                UiButton button = buttons.get(i);
                position(button, body.x(), body.y() + rowTops.get(i) - scroll, body.width(), button.getHeight());
            }
        }

        private void revealFocus() {
            if (focused >= rowCount) return;
            int top = rowTops.get(focused);
            int bottom = top + buttons.get(focused).getHeight();
            if (top < scroll) scrollBy(top - scroll);
            else if (bottom > scroll + body.height()) scrollBy(bottom - scroll - body.height());
        }

        private void moveFocus(int step) {
            focus(buttons.get(Math.floorMod(focused + step, buttons.size())), true);
        }

        private void focus(UiButton target, boolean reveal) {
            int index = buttons.indexOf(target);
            if (index != focused) {
                buttons.get(focused).setFocused(false);
                focused = index;
                target.setFocused(true);
                narrate(this, false);
            }
            if (reveal && layout()) revealFocus();
        }

        private UiButton buttonAt(double x, double y) {
            if (!rendered || !pointerInViewport(x, y, owner.width, owner.height) || !layout()) return null;
            for (int i = 0; i < buttons.size(); i++) {
                UiButton button = buttons.get(i);
                if ((i >= rowCount || body.contains(x, y)) && button.isMouseOver(x, y)) return button;
            }
            return null;
        }

        private void moveTo(int offset) {
            int delta = offset - offsetY;
            if (delta == 0) return;
            offsetY = offset;
            if (panel == null) return;
            panel = panel.offsetY(delta);
            body = body.offsetY(delta);
            for (UiButton button : buttons) button.setY(button.getY() + delta);
        }

        private boolean closing() {
            return closeRevision != 0L;
        }

        private float progress(long now) {
            return GuiMotion.clamp01(enter.sample(now, preferences));
        }

        private void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, long now,
                            float progress, boolean morph) {
            moveTo(source == null ? entryOffset(progress, sheet) : 0);
            if (!layout()) return;
            rendered = !closing();
            if (morph) {
                var target = new GuiRect(panel.x(), panel.y(), panel.width(), panel.height());
                FluidMorph frame = source.renderMorph(graphics, target, preferences, progress, now);
                if (progress < 1f) {
                    renderMorphContents(graphics, mouseX, mouseY, partialTick, frame, progress);
                    return;
                }
            } else {
                LiquidGlassTheme.layeredPopover(graphics, panel.x(), panel.y(), panel.width(), panel.height(),
                        preferences, 0.65f + 0.35f * progress);
            }
            renderContents(graphics, rendered ? mouseX : Integer.MIN_VALUE,
                    rendered ? mouseY : Integer.MIN_VALUE, partialTick);
        }

        private void renderMorphContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                         FluidMorph frame, float progress) {
            rendered = false;
            float opacity = GuiMotion.clamp01((progress - 0.76f) / 0.22f);
            if (opacity <= 0f) return;
            GuiRect rect = frame.rect();
            // 内接矩形覆盖文本和按钮，过渡期间将尚未展开的部分裁掉。
            float inset = frame.radius() * 0.3f;
            int left = (int) Math.ceil(rect.getX() + inset);
            int top = (int) Math.ceil(rect.getY() + inset);
            int right = (int) Math.floor(rect.getX() + rect.getW() - inset);
            int bottom = (int) Math.floor(rect.getY() + rect.getH() - inset);
            if (right <= left || bottom <= top) return;
            int padding = body.x() - panel.x();
            rendered = !closing() && opacity >= 0.9f && left <= body.x() && right >= body.right()
                    && top <= body.y() && bottom >= panel.bottom() - padding;
            graphics.enableScissor(left, top, right, bottom);
            try {
                GuiRenderUtil.withAttentionContent(graphics, opacity, 0f,
                        () -> renderContents(graphics, rendered ? mouseX : Integer.MIN_VALUE,
                                rendered ? mouseY : Integer.MIN_VALUE, partialTick));
            } finally {
                graphics.disableScissor();
            }
        }

        private void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (body.height() > 0) {
                graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
                try {
                    int headingY = body.y() - scroll;
                    if (headingInset > 0 && headingY + headingHeight > body.y()) {
                        icon.draw(graphics, body.x(), headingY + (headingHeight - GraphicIcon.PIXELS) / 2,
                                1, iconColor(), preferences);
                    }
                    int titleY = headingY + (headingHeight - titleLines.size() * lineHeight) / 2;
                    drawLines(graphics, titleLines, body.x() + headingInset, titleY, DarkModeTheme.textColor());
                    drawLines(graphics, detailLines, body.x(), headingY + headingHeight + GAP, DarkModeTheme.secondaryTextColor());
                    for (int i = 0; i < rowCount; i++) {
                        UiButton row = buttons.get(i);
                        if (row.getY() < body.bottom() && row.getY() + row.getHeight() > body.y()) {
                            drawButton(graphics, row, mouseX, mouseY, partialTick);
                        }
                    }
                } finally {
                    graphics.disableScissor();
                }
            }
            for (int i = rowCount; i < buttons.size(); i++) {
                drawButton(graphics, buttons.get(i), mouseX, mouseY, partialTick);
            }
        }

        private void drawLines(GuiGraphics graphics, List<FormattedCharSequence> lines, int x, int y, int color) {
            for (FormattedCharSequence line : lines) {
                if (y >= body.bottom()) return;
                if (y + lineHeight > body.y()) graphics.drawString(font, line, x, y, color, false);
                y += lineHeight;
            }
        }

        private void drawButton(GuiGraphics graphics, UiButton button, int mouseX, int mouseY, float partialTick) {
            button.render(graphics, mouseX, mouseY, partialTick);
            if (button == pressed && button.isMouseOver(mouseX, mouseY)) {
                int x = button.getX() + 2;
                int y = button.getY() + button.getHeight() - 2;
                graphics.fill(x, y, Math.max(x, x + button.getWidth() - 4), y + 1, LiquidGlassTheme.accentColor());
            }
        }
    }

    private static void position(UiButton button, int x, int y, int width, int height) {
        button.setX(x);
        button.setY(y);
        button.setWidth(width);
        button.setHeight(height);
    }
}
