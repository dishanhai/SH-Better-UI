package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.SvgIconTexture;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** 山海：展示完整文字，可用滚轮、键盘或拖动滚动条阅读。 */
final class UiTextDetailsScreen extends Screen {
    private static final int MAX_WIDTH = 560;
    private static final int MAX_HEIGHT = 360;
    private static final int GAP = 8;
    private static final int TRACK_WIDTH = 4;
    private static final int ICON_SIZE = 28;
    private static final int LARGE_PIXELS = 64;
    private static final ResourceLocation EXPAND_LARGE =
            ResourceLocation.tryParse("sh_better_ui:textures/gui/display_64/expand.png");

    static {
        GaussBlur.allowScreen(screen -> screen instanceof UiTextDetailsScreen);
    }

    private final Screen parent;
    private final Component details;
    private final UiPreferences preferences;
    private final UiCapsuleReveal entrance = new UiCapsuleReveal();
    private final UiScreenReturn returning = new UiScreenReturn(this);
    private final Supplier<? extends GuiEventListener> returnFocus;
    private List<FormattedCharSequence> heading = List.of();
    private List<FormattedCharSequence> content = List.of();
    private UiButton back;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int textLeft;
    private int textRight;
    private int innerRight;
    private int viewTop;
    private int viewBottom;
    private int headingInset;
    private int headingHeight;
    private int contentHeight;
    private int scroll;
    private int dragOffset = -1;
    private boolean fromCapsule;

    UiTextDetailsScreen(Screen parent, Component title, Component details, UiPreferences preferences,
                        Supplier<? extends GuiEventListener> returnFocus) {
        super(title);
        this.parent = parent;
        this.details = details;
        this.preferences = Objects.requireNonNull(preferences);
        this.returnFocus = returnFocus;
    }

    void revealFrom(UiButton source) {
        fromCapsule = true;
        entrance.startFrom(() -> {
            if (parent == null || parent.children().contains(source)) return source;
            var current = returnFocus.get();
            return current instanceof AbstractWidget widget && parent.children().contains(widget) ? widget : source;
        });
        returning.beginHandoff(parent, preferences);
    }

    @Override
    protected void init() {
        int margin = Math.min(8, Math.min(width, height) / 8);
        panelWidth = Math.max(1, Math.min(width - 2 * margin, MAX_WIDTH));
        panelHeight = Math.max(1, Math.min(height - 2 * margin, MAX_HEIGHT));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int padding = Math.min(12, Math.min(panelWidth, panelHeight) / 8);
        int innerWidth = panelWidth - 2 * padding;
        int backWidth = Math.min(110, innerWidth);
        int backHeight = Math.min(22, panelHeight - 2 * padding);
        int backY = top + panelHeight - padding - backHeight;
        back = new UiButton(left + (panelWidth - backWidth) / 2, backY, backWidth, backHeight,
                Component.translatable("gui.back"), button -> onClose(), preferences, false, UiStyle.LIQUID_GLASS);
        back.setLeadingIcon(GraphicIcon.BACK);
        back.setSampleBackground(false);
        addRenderableWidget(back);
        setInitialFocus();

        textLeft = left + padding;
        innerRight = left + panelWidth - padding;
        textRight = innerRight - (innerWidth >= 32 ? GAP : 0);
        viewTop = top + padding;
        viewBottom = Math.max(viewTop, backY - GAP);
        int lineWidth = textRight - textLeft;
        headingInset = lineWidth >= 96 && viewHeight() >= ICON_SIZE ? ICON_SIZE + GAP : 0;
        heading = font.split(getTitle(), lineWidth - headingInset);
        content = font.split(details, lineWidth);
        headingHeight = Math.max(heading.size() * lineStep(), headingInset > 0 ? ICON_SIZE : 0);
        contentHeight = headingHeight + GAP + content.size() * lineStep();
        scrollTo(scroll);
        dragOffset = -1;
    }

    // NeoForge 会在 init 后另行设置初始焦点，Forge 由 init 直接调用。
    protected void setInitialFocus() {
        if (back != null && getFocused() != back) setInitialFocus(back);
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(getTitle(), details);
    }

    @Override
    public boolean isPauseScreen() {
        return parent != null && parent.isPauseScreen();
    }

    @Override
    public void onClose() {
        if (!fromCapsule) {
            returnToParent();
            return;
        }
        if (entrance.closing()) return;
        dragOffset = -1;
        if (!preferences.reducedMotion()) {
            returning.beginHandoff(this, preferences);
            returning.prepare(parent, width, height);
        }
        entrance.close(preferences, this::returnToParent);
    }

    private void returnToParent() {
        minecraft.setScreen(parent);
        if (parent == null || minecraft.screen != parent) return;
        var target = returnFocus.get();
        if (target != null && parent.children().contains(target)) parent.setFocused(target);
    }

    // 两端的 Screen 使用不同背景签名，背景由 render 一次绘制。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (entrance.closing()) returning.renderBackdrop(graphics, width, height, partialTick);
        else DarkModeTheme.background(graphics, 0, 0, width, height);
        var panel = new Bounds(left, top, panelWidth, panelHeight);
        if (!returning.renderTransition(entrance, graphics, panel, preferences,
                () -> renderContent(graphics, -1, -1, partialTick))) {
            LiquidGlassTheme.popover(graphics, left, top, panelWidth, panelHeight, preferences);
            renderContent(graphics, mouseX, mouseY, partialTick);
        }
        returning.renderHandoff(graphics, preferences);
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (viewHeight() > 0) {
            int headingY = viewTop - scroll;
            graphics.enableScissor(textLeft, viewTop, textRight, viewBottom);
            try {
                if (headingInset > 0 && scroll < ICON_SIZE) {
                    var svg = SvgIconTexture.find(EXPAND_LARGE, ICON_SIZE);
                    int texels = svg == null ? LARGE_PIXELS : svg.side();
                    graphics.blit(svg == null ? EXPAND_LARGE : svg.id(), textLeft, headingY, ICON_SIZE, ICON_SIZE,
                            0f, 0f, texels, texels, texels, texels);
                }
                int titleY = headingY + (headingHeight - heading.size() * lineStep()) / 2;
                drawLines(graphics, heading, textLeft + headingInset, titleY,
                        preferences.highContrast() ? LiquidGlassTheme.textColor() : LiquidGlassTheme.accentColor());
                drawLines(graphics, content, textLeft, headingY + headingHeight + GAP, LiquidGlassTheme.textColor());
            } finally {
                graphics.disableScissor();
            }
            if (hasTrack()) {
                int thumb = thumbTop();
                graphics.fill(innerRight - TRACK_WIDTH, viewTop, innerRight, viewBottom, 0xFF394657);
                graphics.fill(innerRight - TRACK_WIDTH, thumb, innerRight, thumb + thumbHeight(), LiquidGlassTheme.accentColor());
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawLines(GuiGraphics graphics, List<FormattedCharSequence> lines, int x, int y, int color) {
        int step = lineStep();
        int start = Math.max(0, Math.floorDiv(viewTop - y, step));
        int end = Math.min(lines.size(), UiMath.ceilDiv(viewBottom - y, step));
        for (int i = start; i < end; i++) {
            UiFormattedText.draw(graphics, font, lines.get(i), x, y + i * step, color, false, preferences);
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (entrance.closing()) {
            if (entrance.reopenFromKey(key)) returning.beginHandoff(this, preferences);
            return true;
        }
        if (!entrance.allowsActivation(key, getFocused())) return true;
        int offset = switch (key) {
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> maxScroll();
            case GLFW.GLFW_KEY_UP -> scroll - lineStep();
            case GLFW.GLFW_KEY_DOWN -> scroll + lineStep();
            case GLFW.GLFW_KEY_PAGE_UP -> scroll - Math.max(lineStep(), viewHeight() - lineStep());
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll + Math.max(lineStep(), viewHeight() - lineStep());
            default -> scroll;
        };
        return scrollTo(offset) || super.keyPressed(key, scanCode, modifiers);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return entrance.allowsPointer(mouseX, mouseY)
                && mouseX >= textLeft && mouseX < innerRight && mouseY >= viewTop && mouseY < viewBottom
                && scrollTo(scroll - (int) Math.round(delta * lineStep() * 3));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        return mouseScrolled(mouseX, mouseY, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (entrance.closing()) {
            if (entrance.reopenFromSource(mouseX, mouseY, button)) returning.beginHandoff(this, preferences);
            return true;
        }
        if (!entrance.allowsPointer(mouseX, mouseY)) return false;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !hasTrack()
                || mouseX < textRight || mouseX >= innerRight || mouseY < viewTop || mouseY >= viewBottom) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int thumbY = thumbTop();
        int thumbHeight = thumbHeight();
        dragOffset = mouseY >= thumbY && mouseY < thumbY + thumbHeight ? (int) mouseY - thumbY : thumbHeight / 2;
        scrollThumb(mouseY);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || dragOffset < 0) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        scrollThumb(mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || dragOffset < 0) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        dragOffset = -1;
        return true;
    }

    @Override
    public void removed() {
        returning.clearHandoff();
        super.removed();
    }

    private int lineStep() { return font.lineHeight + 2; }

    private int viewHeight() { return viewBottom - viewTop; }

    private int maxScroll() { return Math.max(0, contentHeight - viewHeight()); }

    private boolean hasTrack() { return textRight < innerRight && viewHeight() > 12 && maxScroll() > 0; }

    private int thumbHeight() {
        return UiMath.clamp(Math.round((float) viewHeight() * viewHeight() / contentHeight), 12, viewHeight() - 1);
    }

    private int thumbTop() {
        return viewTop + Math.round((float) scroll / maxScroll() * (viewHeight() - thumbHeight()));
    }

    private void scrollThumb(double mouseY) {
        double fraction = (mouseY - viewTop - dragOffset) / (viewHeight() - thumbHeight());
        scrollTo((int) Math.round(maxScroll() * UiMath.clamp(fraction, 0, 1)));
    }

    private boolean scrollTo(int offset) {
        int next = UiMath.clamp(offset, 0, maxScroll());
        if (next == scroll) return false;
        scroll = next;
        return true;
    }
}
