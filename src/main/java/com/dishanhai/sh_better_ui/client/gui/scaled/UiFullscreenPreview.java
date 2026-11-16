package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 山海：占满游戏 GUI 的独立预览页，使用返回按钮或 Esc 关闭。 */
public final class UiFullscreenPreview extends Screen {
    @FunctionalInterface
    public interface Content {
        /**
         * 每次初始化或调整 GUI 大小时重新布局，坐标使用 GUI 单位。
         * 将控件放在 viewport 内并通过 addWidget 注册，即可使用原生输入和旁白。
         * 返回在控件后方绘制的内容，只有控件时可返回 null。跨布局的内容状态由调用方持有。
         */
        Renderable layout(Bounds viewport, Consumer<AbstractWidget> addWidget);
    }

    private static final Component BACK = Component.translatable("gui.back");
    private final Screen parent;
    private final UiPreferences preferences;
    private final Content content;
    private final Supplier<? extends GuiEventListener> returnFocus;
    private final UiDotBackground background = new UiDotBackground();
    private UiButton back;
    private Bounds viewport;
    private Renderable preview;
    private FormattedCharSequence heading;
    private int headingX;

    /** 默认恢复创建预览时父界面的焦点。parent 可为 null，关闭时沿用原版返回行为。 */
    public UiFullscreenPreview(Screen parent, Component title, UiPreferences preferences, Content content) {
        this(parent, title, preferences, content, captureFocus(parent));
    }

    /** returnFocus 在父界面重新打开后取值，可返回重建后的入口控件。 */
    public UiFullscreenPreview(Screen parent, Component title, UiPreferences preferences, Content content,
                               Supplier<? extends GuiEventListener> returnFocus) {
        super(Objects.requireNonNull(title));
        this.parent = parent;
        this.preferences = Objects.requireNonNull(preferences);
        this.content = Objects.requireNonNull(content);
        this.returnFocus = Objects.requireNonNull(returnFocus);
    }

    private static Supplier<GuiEventListener> captureFocus(Screen parent) {
        GuiEventListener focused = parent == null ? null : parent.getFocused();
        return () -> focused;
    }

    @Override
    protected void init() {
        int inset = Math.min(6, Math.min(width, height) / 8);
        int backWidth = Math.min(100, width - 2 * inset);
        int backHeight = Math.min(22, height - 2 * inset);
        int barHeight = backHeight + 2 * inset;
        back = addWidget(new UiButton(inset, inset, backWidth, backHeight, BACK,
                button -> onClose(), preferences, false, UiStyle.LIQUID_GLASS));
        back.setLeadingIcon(GraphicIcon.BACK);
        back.setSampleBackground(false);

        headingX = back.getX() + backWidth + inset;
        int headingWidth = width - inset - headingX;
        heading = FormattedCharSequence.EMPTY;
        if (headingWidth > 0 && barHeight >= font.lineHeight) {
            var splitter = font.getSplitter();
            var lines = splitter.splitLines(getTitle(), headingWidth, Style.EMPTY);
            if (lines.size() > 1) lines = UiTextDetails.truncate(splitter, lines, headingWidth, 1);
            if (!lines.isEmpty()) heading = Language.getInstance().getVisualOrder(lines.get(0));
        }

        viewport = new Bounds(0, barHeight, width, height - barHeight);
        preview = content.layout(viewport, this::addRenderableWidget);
        setInitialFocus();
    }

    // Forge 由 init 设置焦点，NeoForge 还会调用无参方法。
    protected void setInitialFocus() {
        if (back != null && getFocused() != back) setInitialFocus(back);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (getFocused() instanceof UiCancelableInteraction interaction && interaction.releasePointer(x, y, button)) {
            setDragging(false); return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && getFocused() instanceof UiCancelableInteraction interaction
                && interaction.cancelInteraction()) {
            setDragging(false);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return parent != null && parent.isPauseScreen();
    }

    @Override
    public void onClose() {
        if (minecraft.screen != this) return;
        minecraft.setScreen(parent);
        if (parent == null || minecraft.screen != parent) return;
        var target = returnFocus.get();
        if (target != null && parent.children().contains(target)) parent.setFocused(target);
    }

    @Override
    public void removed() {
        background.reset();
        super.removed();
    }

    // NeoForge 的 Screen.render 会调用背景方法，预览底板由这里的 render 统一绘制。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Use the same theme backdrop as native UUK screens: clear HUD glass can expose
        // the world, selected theme effects render behind it, and high contrast/no-world
        // fallback stays owned by UiDotBackground rather than an unconditional opaque fill.
        background.renderThemed(this, graphics, width, height, preferences, true);
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        try {
            if (preview != null) preview.render(graphics, mouseX, mouseY, partialTick);
            super.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            graphics.disableScissor();
        }
        graphics.fill(0, 0, width, viewport.y(), DarkModeTheme.surfaceColor());
        UiFormattedText.draw(graphics, font, heading, headingX, (viewport.y() - font.lineHeight) / 2,
                DarkModeTheme.textColor(), false, preferences);
        back.render(graphics, mouseX, mouseY, partialTick);
    }
}
