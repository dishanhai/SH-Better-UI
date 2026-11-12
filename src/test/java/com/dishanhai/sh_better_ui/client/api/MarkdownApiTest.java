package com.dishanhai.sh_better_ui.client.api;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：覆盖面板用到的 Markdown 格式和网页目标边界。 */
class MarkdownApiTest {
    private final MarkdownApi markdown = new MarkdownApi();

    @Test
    void rendersBlocksAndInlineStylesWithNativeComponents() {
        var result = markdown.parse("# 标题 #\r\n- **粗体 _嵌套_** 和 ~~删除~~\r\n"
                + "> ***强调*** 和 `**原样**`\r\n```java\r\n**代码**\r\n```");

        assertEquals("标题\n• 粗体 嵌套 和 删除\n│ 强调 和 **原样**\n**代码**", result.getString());
        assertTrue(run(result, "标题").getStyle().isBold());
        var nested = run(result, "嵌套").getStyle();
        assertTrue(nested.isBold());
        assertTrue(nested.isItalic());
        assertTrue(run(result, "删除").getStyle().isStrikethrough());
        assertTrue(run(result, "强调").getStyle().isBold());
        assertTrue(run(result, "强调").getStyle().isItalic());
        assertEquals(ChatFormatting.GRAY.getColor(), run(result, "**原样**").getStyle().getColor().getValue());
        assertFalse(run(result, "**代码**").getStyle().isBold());
    }

    @Test
    void onlyWebDestinationsReceiveClickActions() {
        String afdian = "https://afdian.com/a/dishanhai?utm_source=copylink&utm_medium=link";
        var result = markdown.parse("[**支持**](" + afdian.replace("&", "\\&") + ") "
                + "[文件](file:///example) [命令](javascript:alert(1)) [相对路径](/issues) "
                + "![封面](https://example.org/image.png) <https://example.org/issues>");

        assertEquals("支持 文件 命令 相对路径 封面 https://example.org/issues", result.getString());
        var support = run(result, "支持").getStyle();
        assertTrue(support.isBold());
        assertTrue(support.isUnderlined());
        assertEquals(new ClickEvent(ClickEvent.Action.OPEN_URL, afdian), support.getClickEvent());
        for (String plain : new String[]{"文件", "命令", "相对路径", "封面"}) {
            assertNull(run(result, plain).getStyle().getClickEvent());
        }
        assertNotNull(run(result, "https://example.org/issues").getStyle().getClickEvent());
    }

    @Test
    void preservesLiteralSyntaxAndBoundsInput() {
        var result = markdown.parse("foo_bar_baz \\*原样\\* *未关闭\\* <b>文本</b>");
        assertEquals("foo_bar_baz *原样* *未关闭* <b>文本</b>", result.getString());
        assertTrue(result.getSiblings().stream().noneMatch(part -> part.getStyle().isItalic()));
        assertThrows(IllegalArgumentException.class, () -> markdown.parse("x".repeat(65_537)));
    }

    @Test
    void checksBrowserTargetsBeforeDispatch() {
        String address = "https://github.com/dishanhai/SH-Better-UI/issues?filter=a%20b#top";
        assertEquals(URI.create(address), LinkApi.webUri(address));
        assertEquals(URI.create("http://example.org/"), LinkApi.webUri("http://example.org/"));
        for (String invalid : new String[]{"//example.org", "https:///issues", "https://user@example.org",
                "https://exa mple.org", "file:///example", "mailto:mail@example.org", "https://example.org/\\path"}) {
            assertNull(LinkApi.webUri(invalid), invalid);
        }
    }

    private static Component run(Component result, String text) {
        return result.getSiblings().stream().filter(part -> part.getString().equals(text)).findFirst().orElseThrow();
    }
}
