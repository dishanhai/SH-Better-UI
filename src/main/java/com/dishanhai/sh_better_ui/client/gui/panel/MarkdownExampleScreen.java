// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.visual.MarkdownView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Runnable block-richtext demonstration, reachable from /shbetterrichtext. */
public final class MarkdownExampleScreen extends Screen {
    public static final String EXAMPLE = """
            # 蒸汽锅炉 · Markdown 富文本
            **基础产出**：160 L/t　 *效率随温度变化*　 ~~已停用旧参数~~
            > 保留清晰的分组与真实换行，面板缩小时自动重排。

            ---
            ## 性能参数
            | 档位 | 产出 | 效率 |
            | :--- | ---: | ---: |
            | 低温 | 40 L/t | 25% |
            | 标准 | **160 L/t** | **100%** |
            | 高温 | 240 L/t | 150% |

            ## 操作提示
            - [x] 已安装安全阀
            - [ ] 检查供水
            1. 连接输入管道
            2. 设置工作温度

            `Shift` 查看详细参数，方向键 / Page Up / Page Down 滚动。
            ```text
            output = base × efficiency
            <HTML> 与命令只作为文本，不执行。
            ```
            [项目文档](https://github.com/dishanhai/SH-Better-UI)

            图片 ![本地图标应由宿主注入](https://example.invalid/icon.png) 仅显示替代文字。
            图表、游戏物品图标及交互控件使用 UUK 显式类型 API，不将它们伪装成 Markdown。
            """;
    private final Screen parent;
    private final String source;
    private MarkdownView view;
    private int scroll, maxScroll, left, top, bottom, contentWidth;
    private int linkIndex;

    public MarkdownExampleScreen(Screen parent) { this(parent, EXAMPLE); }
    public MarkdownExampleScreen(Screen parent, String source) {
        super(Component.literal("Markdown 富文本"));
        this.parent = parent;
        this.source = java.util.Objects.requireNonNull(source);
    }

    @Override protected void init() {
        contentWidth = Math.max(32, Math.min(520, width - 32));
        left = Math.max(0, (width - contentWidth) / 2);
        top = 56;
        bottom = Math.max(top, height - 34);
        view = new MarkdownView(font, ShBetterUiApi.markdown().document(source), contentWidth);
        var links = view.links();
        if (!links.isEmpty()) {
            linkIndex = Math.min(linkIndex, links.size() - 1);
            int linkWidth = Math.max(20, width - 108);
            var open = addRenderableWidget(Button.builder(Component.literal(links.get(linkIndex)),
                    button -> ShBetterUiApi.links().open(this, links.get(linkIndex)))
                    .bounds(8, 28, linkWidth, 20).build());
            addRenderableWidget(Button.builder(Component.literal("下一链接"), button -> {
                linkIndex = (linkIndex + 1) % links.size();
                open.setMessage(Component.literal(links.get(linkIndex)));
            }).bounds(16 + linkWidth, 28, 84, 20).build());
        }
        maxScroll = Math.max(0, view.height() - (bottom - top));
        scroll = Math.min(scroll, maxScroll);
        int buttonWidth = Math.min(100, Math.max(20, (width - 24) / 2));
        var back = addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(8, Math.max(0, height - 26), buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("复制 Markdown"), b -> minecraft.keyboardHandler.setClipboard(source))
                .bounds(16 + buttonWidth, Math.max(0, height - 26), buttonWidth, 20).build());
        setInitialFocus(back);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xee151b25);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xffe7edf7);
        graphics.enableScissor(left, top, Math.min(width, left + contentWidth), bottom);
        view.render(graphics, left, top - scroll, top, bottom, 0xffe0e6ef);
        graphics.disableScissor();
        if (maxScroll > 0 && bottom > top) {
            int track = bottom - top;
            int thumb = Math.max(8, track * track / Math.max(1, view.height()));
            int y = top + (track - thumb) * scroll / maxScroll;
            graphics.fill(Math.min(width - 3, left + contentWidth + 3), y,
                    Math.min(width, left + contentWidth + 5), y + thumb, 0xff879dbd);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && x >= left && x < left + contentWidth && y >= top && y < bottom) {
            var style = view.styleAt(x - left, y - top + scroll);
            if (style != null && style.getClickEvent() != null) {
                // The parser creates HTTP(S) only. Revalidate at the action boundary, respecting link settings.
                return ShBetterUiApi.links().open(this, style.getClickEvent().getValue());
            }
        }
        return super.mouseClicked(x, y, button);
    }

    public boolean mouseScrolled(double x, double y, double dy) {
        if (x < left || x >= left + contentWidth || y < top || y >= bottom || dy == 0) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (dy * 24)));
        return true;
    }
    public boolean mouseScrolled(double x, double y, double dx, double dy) { return mouseScrolled(x, y, dy); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        int delta = switch (key) {
            case GLFW.GLFW_KEY_DOWN -> 18;
            case GLFW.GLFW_KEY_UP -> -18;
            case GLFW.GLFW_KEY_PAGE_DOWN -> Math.max(18, bottom - top - 18);
            case GLFW.GLFW_KEY_PAGE_UP -> -Math.max(18, bottom - top - 18);
            case GLFW.GLFW_KEY_HOME -> -maxScroll;
            case GLFW.GLFW_KEY_END -> maxScroll;
            default -> 0;
        };
        if (delta != 0) { scroll = Math.max(0, Math.min(maxScroll, scroll + delta)); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
