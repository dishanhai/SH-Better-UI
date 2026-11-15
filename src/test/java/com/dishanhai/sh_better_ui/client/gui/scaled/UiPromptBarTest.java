// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class UiPromptBarTest {
    @Test
    void detectsLocalizedTrailingSuggestionsAtTokenBoundaries() {
        assertEquals(new UiPromptBar.Token('@', "web", 4), UiPromptBar.trailingToken("use @WEB"));
        assertEquals(new UiPromptBar.Token('/', "摘要", 6), UiPromptBar.trailingToken("hello\n/摘要"));
        assertEquals(new UiPromptBar.Token('@', "", 0), UiPromptBar.trailingToken("@"));
        assertNull(UiPromptBar.trailingToken("name@example"));
        assertNull(UiPromptBar.trailingToken("folder/file"));
        assertNull(UiPromptBar.trailingToken("/draft\n"));
    }

    @Test
    void preservesTheSubmittedAttachmentsWhenTheEditorClears() {
        var attachments = new ArrayList<>(List.of("notes.txt"));
        var submission = new UiPromptBar.Submission("summarize", attachments, "nova", 2);
        attachments.clear();
        assertEquals(List.of("notes.txt"), submission.attachments());
    }
}
