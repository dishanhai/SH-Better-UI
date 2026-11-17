package com.dishanhai.sh_better_ui.client.favorites;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FavoriteSettingsStoreTest {
    @TempDir Path directory;
    @Test void defaultsAndExplicitEmptyRemainDistinct() throws Exception {
        var file=directory.resolve("favorites.txt");var store=new FavoriteSettingsStore(file);store.load();
        assertEquals(FavoriteSettingsStore.DEFAULT,store.ids());store.save(List.of());
        var reopened=new FavoriteSettingsStore(file);reopened.load();assertTrue(reopened.ids().isEmpty());
    }
    @Test void stableOrderDedupeAndUnknownIdsAreNotExecutable() throws Exception {
        var file=directory.resolve("nested/favorites.txt");var store=new FavoriteSettingsStore(file);
        store.save(List.of("scale","hud","scale","arbitrary.command"));store.load();
        assertEquals(List.of("scale","hud"),store.ids());
        assertThrows(UnsupportedOperationException.class,()->store.ids().add("island"));
    }
    @Test void failedSaveLeavesMemoryAndExistingFileIntact() throws Exception {
        var parent=directory.resolve("file");Files.writeString(parent,"block");
        var store=new FavoriteSettingsStore(parent.resolve("favorites.txt"));
        assertThrows(java.io.IOException.class,()->store.save(List.of()));
        assertEquals(FavoriteSettingsStore.DEFAULT,store.ids());assertEquals("block",Files.readString(parent));
    }
    @Test void boundedInputAndUndoRoundTrip() throws Exception {
        var file=directory.resolve("favorites.txt");var store=new FavoriteSettingsStore(file);
        var before=store.ids();store.save(List.of("tasks"));store.save(before);store.load();
        assertEquals(before,store.ids());Files.writeString(file,"x".repeat(16385));
        assertThrows(java.io.IOException.class,store::load);assertEquals(before,store.ids());
    }
}
