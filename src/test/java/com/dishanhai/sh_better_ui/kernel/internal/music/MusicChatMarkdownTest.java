// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MusicChatMarkdownTest {
    @Test void usesTheCommonLibInlineParserWhenItsPublicApiIsInstalled() throws Exception {
        boolean linked;
        try {
            Class.forName("com.dishanhai.sh_common_lib.api.ulk.markdown.Markdown");
            linked = true;
        } catch (ClassNotFoundException | LinkageError absent) {
            linked = false;
        }
        assertEquals(linked ? "code" : " code ", MusicChatMarkdown.blocks("` code `").get(0).getString());
    }

    @Test void retainsInlineStylesAndPassivelyDisplaysCodeAndTables() {
        var blocks = MusicChatMarkdown.blocks("""
                # 建议
                **这是一段带有 Markdown 格式的语句。** 和 *斜体*、~~删除~~。
                - 歌曲
                ```
                **代码**
                ```
                | 歌曲 | 歌手 |
                | --- | --- |
                | 将军 | 周杰伦 |
                """);
        assertEquals(List.of("建议", "这是一段带有 Markdown 格式的语句。 和 斜体、删除。", "• 歌曲",
                "**代码**", "歌曲 │ 歌手", "将军 │ 周杰伦"),
                blocks.stream().map(block -> block.getString()).toList());
        blocks.get(1).visit((style, text) -> {
            if (text.startsWith("这是一段")) assertTrue(style.isBold());
            if (text.equals("斜体")) assertTrue(style.isItalic());
            if (text.equals("删除")) assertTrue(style.isStrikethrough());
            return Optional.empty();
        }, Style.EMPTY);
    }
}
