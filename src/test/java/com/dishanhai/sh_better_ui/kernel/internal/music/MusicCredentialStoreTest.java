// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MusicCredentialStoreTest {
    @TempDir Path temp;

    @Test void savesOnlyWhenRequestedAndKeepsCredentialsAcrossInstances() throws Exception {
        Path directory = temp.resolve("music");
        var store = new MusicCredentialStore(directory);
        assertEquals(Map.of(), store.load());
        assertFalse(Files.exists(directory));
        store.put("netease", "MUSIC_U=fixture-secret");
        store.put("qq", "uin=123; qm_keyst=another-secret");
        assertEquals(Map.of("netease", "MUSIC_U=fixture-secret", "qq", "uin=123; qm_keyst=another-secret"),
                new MusicCredentialStore(directory).load());
        try (var files = Files.list(directory)) {
            for (var file : files.toList()) {
                String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                assertFalse(bytes.contains("fixture-secret"));
                assertFalse(bytes.contains("another-secret"));
            }
        }
    }

    @Test void removingOneSourcePreservesOthersAndBlankValueRemoves() throws Exception {
        var store = new MusicCredentialStore(temp.resolve("music"));
        store.put("netease", "MUSIC_U=first");
        store.put("qq", "uin=123; qm_keyst=second");
        store.remove("netease");
        assertEquals(Map.of("qq", "uin=123; qm_keyst=second"), store.load());
        store.put("qq", "");
        assertTrue(store.load().isEmpty());
    }

    @Test void refusesUnsafeNamesAndCredentials() throws Exception {
        var store = new MusicCredentialStore(temp.resolve("music"));
        for (String id : new String[]{"../other", "", "UPPER", "a/b", "x\\y"}) {
            assertThrows(IllegalArgumentException.class, () -> store.put(id, "a=b"));
            assertThrows(IllegalArgumentException.class, () -> store.remove(id));
        }
        assertThrows(IllegalArgumentException.class, () -> store.put("qq", "a=b\r\nheader"));
        assertFalse(Files.exists(temp.resolve("music")));
    }

    @Test void corruptCiphertextFailsWithoutDisclosingSecrets() throws Exception {
        Path directory = temp.resolve("music");
        var store = new MusicCredentialStore(directory);
        store.put("qq", "uin=123; qm_keyst=secret-value");
        Files.write(directory.resolve("qq.credential"), new byte[]{1, 2, 3});
        var error = assertThrows(IOException.class, () -> store.load());
        assertFalse(error.toString().contains("secret-value"));
    }

    @Test void individualLoadsKeepOtherSourcesAvailableWhenOneFileIsCorrupt() throws Exception {
        Path directory = temp.resolve("music");
        var store = new MusicCredentialStore(directory);
        store.put("netease", "MUSIC_U=good");
        store.put("qq", "uin=123; qm_keyst=bad");
        Files.write(directory.resolve("qq.credential"), new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> store.load("qq"));
        assertEquals("MUSIC_U=good", store.load("netease"));
        assertEquals("", store.load("missing"));
        assertTrue(Files.exists(directory.resolve("qq.credential")));
    }

    @Test void revocationInvalidatesOtherInstancesOldLeasesOnlyForThatSource() throws Exception {
        Path directory = temp.resolve("music");
        var first = new MusicCredentialStore(directory);
        var other = new MusicCredentialStore(directory.resolve("..").resolve("music"));
        long old = first.revision("qq");
        long netease = first.revision("netease");
        assertTrue(first.putIfCurrent("qq", "old-cookie", old));
        var saved = other.loadWithRevision("qq");
        assertEquals("old-cookie", saved.cookie());
        assertEquals(old, saved.revision());
        assertFalse(saved.toString().contains("old-cookie"));
        other.revoke("qq");
        assertFalse(first.putIfCurrent("qq", "old-cookie", old));
        assertEquals("", first.load("qq"));
        assertTrue(first.putIfCurrent("netease", "other-cookie", netease));
        assertTrue(first.putIfCurrent("qq", "new-cookie", first.revision("qq")));
        assertEquals("new-cookie", other.load("qq"));
    }

    @Test void cookieByteAndCharacterLimitsMatchNetworkValidation() throws Exception {
        var store = new MusicCredentialStore(temp.resolve("music"));
        assertThrows(IllegalArgumentException.class, () -> store.put("qq", "a".repeat(8193)));
        assertThrows(IllegalArgumentException.class, () -> store.put("qq", "中".repeat(6000)));
        assertFalse(Files.exists(temp.resolve("music")));
    }
}
