package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 山海：长文本的折叠预览、原地展开与独立详情页。 */
public final class UiTextDetails {
    private static final Component EXPAND = Component.translatable("sh_better_ui.details.expand");
    private static final Component COLLAPSE = Component.translatable("sh_better_ui.details.collapse");
    private static final Component OPEN_PAGE = Component.translatable("sh_better_ui.details.open_page");
    private final Component title;
    private final Component details;
    private final int collapsedLines;
    private final UiDisclosure disclosure = new UiDisclosure(false);

    public UiTextDetails(Component title, Component details, int collapsedLines) {
        if (collapsedLines < 1) throw new IllegalArgumentException("collapsedLines must be positive");
        this.title = Objects.requireNonNull(title);
        this.details = Objects.requireNonNull(details);
        this.collapsedLines = collapsedLines;
    }

    public boolean expanded() {
        return disclosure.expanded();
    }

    /** 窗口宽度或展开状态改变后重新布局，坐标与宽度使用 GUI 单位。 */
    public Layout layout(Font font, int width) {
        if (width < 1) throw new IllegalArgumentException("width must be positive");
        var splitter = font.getSplitter();
        var lines = splitter.splitLines(details, width, Style.EMPTY);
        boolean expandable = lines.size() > collapsedLines;
        boolean truncated = expandable && !expanded();
        var visible = truncated ? truncate(splitter, lines, width, collapsedLines) : lines;
        return new Layout(Language.getInstance().getVisualOrder(visible), font.lineHeight, expandable, truncated);
    }

    public record Layout(List<FormattedCharSequence> lines, int lineHeight, boolean expandable, boolean truncated) {
        public Layout {
            lines = List.copyOf(lines);
        }

        public int height() {
            return lines.size() * lineHeight;
        }

        public void render(GuiGraphics graphics, Font font, int x, int y, int color, boolean shadow,
                           UiPreferences preferences) {
            for (var line : lines) {
                UiFormattedText.draw(graphics, font, line, x, y, color, shadow, preferences);
                y += lineHeight;
            }
        }
    }

    /** 文本确实超出折叠行数时返回按钮。回调收到新状态，宿主可重新布局正文及后续控件。 */
    public Optional<UiButton> inlineButton(Layout layout, int x, int y, int width, int height,
                                           UiPreferences preferences, Consumer<Boolean> onChanged) {
        if (!layout.expandable()) return Optional.empty();
        requireIconSize(width, height);
        Objects.requireNonNull(onChanged);
        var button = disclosure.button(x, y, width, height, EXPAND, COLLAPSE, preferences, UiStyle.LIQUID_GLASS,
                (control, expanded) -> {
                    showIcon(control, expanded ? GraphicIcon.COLLAPSE : GraphicIcon.EXPAND);
                    onChanged.accept(expanded);
                });
        showIcon(button, expanded() ? GraphicIcon.COLLAPSE : GraphicIcon.EXPAND);
        return Optional.of(button);
    }

    /** 文本确实超出折叠行数时返回打开完整详情的按钮，局部展开状态保持原样。 */
    public Optional<UiButton> pageButton(Layout layout, Screen parent, int x, int y, int width, int height,
                                         UiPreferences preferences) {
        return pageButton(layout, parent, x, y, width, height, preferences, null);
    }

    /** 宿主重建控件时，可通过 returnFocus 提供返回后的新入口控件。 */
    public Optional<UiButton> pageButton(Layout layout, Screen parent, int x, int y, int width, int height,
                                         UiPreferences preferences, Supplier<? extends GuiEventListener> returnFocus) {
        if (!layout.expandable()) return Optional.empty();
        requireIconSize(width, height);
        var button = new UiButton(x, y, width, height, OPEN_PAGE, source -> {
            Supplier<? extends GuiEventListener> focus = returnFocus == null ? () -> source : returnFocus;
            Minecraft.getInstance().setScreen(pageFrom(parent, preferences, (UiButton) source, focus));
        }, preferences, false, UiStyle.LIQUID_GLASS);
        showIcon(button, GraphicIcon.EXPAND);
        return Optional.of(button);
    }

    /** 直接创建完整详情页，供已有自定义入口的画面使用。 */
    public Screen page(Screen parent, UiPreferences preferences) {
        return page(parent, preferences, () -> null);
    }

    /** 宿主重建控件时，由回调取得返回后的新入口控件。 */
    public Screen page(Screen parent, UiPreferences preferences, Supplier<? extends GuiEventListener> returnFocus) {
        return new UiTextDetailsScreen(parent, title, details, preferences, Objects.requireNonNull(returnFocus));
    }

    /** 使用已有胶囊按钮作为详情页的入场起点。 */
    public Screen pageFrom(Screen parent, UiPreferences preferences, UiButton source,
                           Supplier<? extends GuiEventListener> returnFocus) {
        var screen = new UiTextDetailsScreen(parent, title, details, preferences, Objects.requireNonNull(returnFocus));
        screen.revealFrom(source);
        return screen;
    }

    static List<FormattedText> truncate(StringSplitter splitter, List<FormattedText> lines, int width, int limit) {
        var visible = new ArrayList<>(lines.subList(0, limit));
        visible.set(limit - 1, UiFormattedText.ellipsize(splitter, visible.get(limit - 1), width));
        return visible;
    }

    private static void requireIconSize(int width, int height) {
        if (width < GraphicIcon.PIXELS || height < GraphicIcon.PIXELS) {
            throw new IllegalArgumentException("Detail buttons must be at least 16 GUI units");
        }
    }

    private static void showIcon(UiButton button, GraphicIcon icon) {
        int width = button.getWidth();
        int height = button.getHeight();
        button.setIcon(null);
        int fullLabelWidth = button.labelWidth(width, height);
        button.setLeadingIcon(icon);
        if (button.labelWidth(width, height) == fullLabelWidth) button.setIcon(icon);
    }
}
