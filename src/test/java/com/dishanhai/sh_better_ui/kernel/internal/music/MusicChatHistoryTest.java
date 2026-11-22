// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MusicChatHistoryTest {
    @TempDir Path directory;

    @Test void savesAProtectedArchiveThatCanBeReopenedAndDeleted() throws Exception {
        var store = new MusicChatHistory(directory);
        var profile = new MusicChatConfig(MusicChatConfig.endpoint("https://example.com/v1"), "model", "test-only-key");
        var turn = new MusicChatClient.Turn("播放将军", "**周杰伦**", 0);
        var first = new MusicChatHistory.Entry(MusicChatHistory.newId(), "播放将军", MusicChatHistory.providerId(profile),
                1, 0, List.of(turn));
        var other = new MusicChatHistory.Entry(MusicChatHistory.newId(), "第二个对话", MusicChatHistory.providerId(profile),
                2, 1, List.of(turn));
        store.save(first);
        store.save(other);
        var reopened = new MusicChatHistory(directory);
        assertEquals(List.of(other, first), reopened.load());
        var file = directory.resolve("conversations").resolve(first.id() + ".credential");
        assertFalse(new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8).contains("播放将军"));
        assertEquals(0, first.contextFor(profile));
        assertEquals(1, first.contextFor(MusicChatConfig.subscription("oaiapp_fixture", "model")));
        assertEquals(1, other.contextFor(profile));
        reopened.remove(first.id());
        assertEquals(List.of(other), new MusicChatHistory(directory).load());
    }
}
