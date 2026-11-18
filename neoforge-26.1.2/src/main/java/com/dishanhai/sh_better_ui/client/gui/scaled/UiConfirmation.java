// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 山海：用原生模态确认控件处理有重要后果的操作。 */
public final class UiConfirmation {
    private UiConfirmation() {}

    public record Prompt(Component title, Component details, Component confirm, Component cancel) {
        public Prompt {
            Objects.requireNonNull(title);
            Objects.requireNonNull(details);
            Objects.requireNonNull(confirm);
            Objects.requireNonNull(cancel);
        }
    }

    /**
     * 仅从当前画面打开。背景回调只绘制已有内容；传 null 时用纯色底。
     * 恢复焦点的回调在父画面重建后查询控件，确认回调只执行一次。
     */
    public static boolean open(Screen parent, Prompt prompt, UiPreferences preferences,
                               Consumer<GuiGraphicsExtractor> context, Supplier<? extends GuiEventListener> restoreFocus,
                               Runnable onConfirm) {
        return open(parent, prompt, preferences, context, restoreFocus, onConfirm, GraphicIcon.WARNING);
    }

    public static boolean open(Screen parent, Prompt prompt, UiPreferences preferences,
                               Consumer<GuiGraphicsExtractor> context, Supplier<? extends GuiEventListener> restoreFocus,
                               Runnable onConfirm, GraphicIcon icon) {
        return openStyled(parent, prompt, preferences, context, restoreFocus, onConfirm, icon, false);
    }

    public static boolean openGlass(Screen parent, Prompt prompt, UiPreferences preferences,
                                    Consumer<GuiGraphicsExtractor> context, Supplier<? extends GuiEventListener> restoreFocus,
                                    Runnable onConfirm) {
        return openGlass(parent, prompt, preferences, context, restoreFocus, onConfirm, GraphicIcon.WARNING);
    }

    public static boolean openGlass(Screen parent, Prompt prompt, UiPreferences preferences,
                                    Consumer<GuiGraphicsExtractor> context, Supplier<? extends GuiEventListener> restoreFocus,
                                    Runnable onConfirm, GraphicIcon icon) {
        return openStyled(parent, prompt, preferences, context, restoreFocus, onConfirm, icon, true);
    }

    private static boolean openStyled(Screen parent, Prompt prompt, UiPreferences preferences,
                                    Consumer<GuiGraphicsExtractor> context, Supplier<? extends GuiEventListener> restoreFocus,
                                    Runnable onConfirm, GraphicIcon icon, boolean glass) {
        Minecraft client = Minecraft.getInstance();
        if (parent == null || client.screen != parent) return false;
        Objects.requireNonNull(prompt);
        Objects.requireNonNull(preferences);
        Objects.requireNonNull(restoreFocus);
        Objects.requireNonNull(onConfirm);
        var choice = new Choice(client, parent, restoreFocus, onConfirm);
        var screen = new ConfirmationScreen(prompt, preferences, context, choice.state, icon, glass);
        choice.state.attach(screen);
        client.setScreen(screen);
        return client.screen == screen;
    }

    private static final class Choice {
        private final ConfirmationChoice<Screen> state;
        private Choice(Minecraft client, Screen parent, Supplier<? extends GuiEventListener> restoreFocus,
                       Runnable onConfirm) {
            state = new ConfirmationChoice<>(parent, () -> client.screen, client::setScreen, () -> {
                GuiEventListener target = restoreFocus.get();
                if (client.screen == parent && target != null && parent.children().contains(target)
                        && (!(target instanceof net.minecraft.client.gui.components.AbstractWidget widget)
                            || widget.active && widget.visible)) parent.setFocused(target);
            }, onConfirm);
        }
        private void finish(boolean confirmed) { state.finish(confirmed); }
    }

    static final class ConfirmationScreen extends Screen {
        private final UiPreferences preferences;
        private final Consumer<GuiGraphicsExtractor> context;
        private final ConfirmationChoice<Screen> choice;
        private final Prompt prompt;
        private final GraphicIcon icon;
        private final boolean glass;
        private int titleInk, detailInk;
        private final ConfirmationGlassPaint painter = new ConfirmationGlassPaint();
        private Button confirmButton, cancelButton;
        private List<FormattedCharSequence> titleLines, detailLines;
        private int headingInset;
        private int headingX;
        private int buttonY;
        private int scrollLine;

        ConfirmationScreen(Prompt prompt, UiPreferences preferences, Consumer<GuiGraphicsExtractor> context,
                                   ConfirmationChoice<Screen> choice, GraphicIcon icon, boolean glass) {
            super(prompt.title());
            this.preferences = preferences;
            this.context = context;
            this.choice = choice;
            this.prompt = prompt;
            this.icon = icon;
            this.glass = glass;
        }

        @Override
        protected void init() {
            initButtons();
            headingInset = icon != null && width >= 120 && buttonY - textTop() >= 28 ? GraphicIcon.PIXELS + 4 : 0;
            titleLines = net.minecraft.locale.Language.getInstance().getVisualOrder(font.splitIgnoringLanguage(prompt.title(), Math.max(1, width - 40 - headingInset)));
            headingX = (width - headingInset - titleLines.stream().mapToInt(font::width).max().orElse(0)) / 2;
            detailLines = net.minecraft.locale.Language.getInstance().getVisualOrder(font.splitIgnoringLanguage(prompt.details(), Math.max(1, width - 40)));
            scrollLine = Math.min(scrollLine, maxScrollLine());
        }

        void initButtons() {
            int y = Math.max(48, height * 2 / 3);
            buttonY = Math.max(0, Math.min(y, height - 34));
            int buttonWidth = Math.max(1, Math.min(150, (width - 36) / 2));
            var confirm = new UiButton(width / 2 - buttonWidth - 6, buttonY, buttonWidth, 26, prompt.confirm(),
                    button -> choice.finish(true), preferences, true);
            var cancel = new UiButton(width / 2 + 6, buttonY, buttonWidth, 26, prompt.cancel(),
                    button -> choice.finish(false), preferences, false);
            confirm.setLeadingIcon(GraphicIcon.SUCCESS);
            cancel.setLeadingIcon(GraphicIcon.CLOSE);
            confirmButton = confirm;
            cancelButton = cancel;
            addRenderableWidget(confirmButton);
            addRenderableWidget(cancelButton);
            setInitialFocus();
        }

        @Override public Component getNarrationMessage() {
            return net.minecraft.network.chat.CommonComponents.joinForNarration(prompt.title(), prompt.details());
        }

        @Override
        public void onClose() { choice.finish(false); }

        @Override public void removed() { choice.abandon(); super.removed(); }

        // NeoForge 还会在 init 后调用此方法，保留取消按钮作为安全的默认焦点。
        protected void setInitialFocus() {
            if (cancelButton != null && getFocused() != cancelButton) setInitialFocus(cancelButton);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            drawLayer(graphics);
            if (titleLines == null) return;
            if (headingInset > 0 && scrollLine == 0) {
                icon.draw(graphics, headingX, textTop(), 1,
                        titleInk, preferences);
            }
            int rows = Math.min(visibleLines(), titleLines.size() + 1 + detailLines.size() - scrollLine);
            for (int row = 0; row < rows; row++) {
                int index = scrollLine + row;
                if (index == titleLines.size()) continue;
                boolean heading = index < titleLines.size();
                var line = heading ? titleLines.get(index) : detailLines.get(index - titleLines.size() - 1);
                if (glass) line = ConfirmationGlassStyle.ink(line, heading ? titleInk : detailInk);
                int x = heading && headingInset > 0 ? headingX + headingInset : (width - font.width(line)) / 2;
                graphics.text(font, line, x,
                        textTop() + row * lineHeight(), heading ? titleInk : detailInk,
                        false);
            }
            confirmButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
            cancelButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
            int key = event.key();
            if (event.isEscape()) { choice.finish(false); return true; }
            if (key == GLFW.GLFW_KEY_TAB) {
                setFocused(getFocused() == cancelButton ? confirmButton : cancelButton);
                return true;
            }
            if (key == GLFW.GLFW_KEY_PAGE_UP && scroll(-visibleLines())) return true;
            if (key == GLFW.GLFW_KEY_PAGE_DOWN && scroll(visibleLines())) return true;
            return super.keyPressed(event);
        }

        // 两个平台的 Screen 分别提供垂直滚轮和双轴滚轮签名。
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            return mouseY >= textTop() && mouseY < buttonY - 6 && delta != 0 && scroll(delta > 0 ? -1 : 1);
        }

        public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
            return mouseScrolled(mouseX, mouseY, deltaY);
        }

        private int textTop() { return Math.max(8, Math.min(68, buttonY - 44)); }

        private int lineHeight() { return font.lineHeight + 2; }

        private int visibleLines() { return Math.max(1, (buttonY - 8 - textTop()) / lineHeight()); }

        private int maxScrollLine() { return Math.max(0, titleLines.size() + 1 + detailLines.size() - visibleLines()); }

        private boolean scroll(int delta) {
            if (titleLines == null) return false;
            int next = UiMath.clamp(scrollLine + delta, 0, maxScrollLine());
            if (next == scrollLine) return false;
            scrollLine = next;
            return true;
        }

        private void drawLayer(GuiGraphicsExtractor graphics) {
            titleInk = UiTheme.current().text();
            detailInk = UiTheme.current().text();
            graphics.fill(0, 0, width, height, UiTheme.current().background());
            if (context != null) context.accept(graphics);
            graphics.fill(0, 0, width, height, 0xA00F172A);
            int top = Math.max(2, textTop() - 10);
            int bottom = Math.min(height - 2, buttonY + 34);
            if (bottom <= top || width <= 16) return;
            if (glass) {
                var palette = painter.draw(graphics, 8, top, width - 16, bottom - top, preferences);
                titleInk = palette.title(); detailInk = palette.details();
                return;
            }
            RoundedCorners.draw(graphics, 8, top, width - 16, bottom - top, 9, 1,
                    preferences.highContrast() ? titleInk : UiTheme.current().border(), UiTheme.current().surface() | 0xff000000);
        }
    }
}
