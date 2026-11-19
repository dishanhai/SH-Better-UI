package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class Listen1BackupTest {
    private static final Set<String> SOURCES = Set.of("qq","netease");
    private static final String UPSTREAM_SHAPE = """
            {
              "playerlists": ["myplaylist_second", "myplaylist_first", "myplaylist_second"],
              "favoriteplayerlists": ["neplaylist_333"],
              "neplaylist_333": {"info":{"id":"neplaylist_333","title":"Remote only"}},
              "myplaylist_first": {"info":{"id":"myplaylist_first","title":"Mixed"},"tracks":[
                 {"source":"netease","id":"netrack_123","title":"晚安","artist":"甲","url":"http://private.test/a"},
                 {"source":"qq","id":"qqtrack_004xxxx","title":"Orbit","album":"Sky","img_url":"https://private.test/a"},
                 {"source":"kuwo","id":"kwtrack_12","title":"Not supported"},
                 {"source":"qq","id":"qqtrack_../not_an_id","title":"Invalid id"},
                 {"source":"netease","id":"qqtrack_abc","title":"Wrong source"}
              ]},
              "myplaylist_second": {"info":{"id":"myplaylist_second","title":"Empty"},"tracks":[]},
              "github_token":"do not retain this",
              "current-playing":[{"id":"qqtrack_abc","source":"qq"}]
            }
            """;

    @Test void readsLocallyCreatedPlaylistsInSavedOrderAndUsesOnlySupportedProviderIdentities() throws IOException {
        var result=Listen1Backup.parse(UPSTREAM_SHAPE,SOURCES);
        assertEquals(List.of("Empty","Mixed"), result.playlists().stream().map(p->p.name()).toList());
        assertEquals(2,result.supportedTracks());
        assertEquals(3,result.skippedTracks());
        assertEquals(List.of(new OnlineTrack("netease","123","123","晚安","甲","",0),
                        new OnlineTrack("qq","004xxxx","004xxxx","Orbit","","Sky",0)),result.tracks(result.playlists().get(1)));
        assertFalse(result.toString().contains("private.test"));
        assertFalse(result.toString().contains("do not retain this"));
        assertThrows(UnsupportedOperationException.class,()->result.tracks(result.playlists().get(1)).clear());
    }

    @Test void ignoresProviderNotInstalledAndFavoriteOnlyExportsAreEmpty() throws IOException {
        var result=Listen1Backup.parse(UPSTREAM_SHAPE,Set.of("qq"));
        assertEquals(1,result.supportedTracks());
        assertEquals(4,result.skippedTracks());
        assertEquals(List.of(),Listen1Backup.parse("{\"favoriteplayerlists\":[\"neplaylist_123\"]}",SOURCES).playlists());
    }

    @Test void rejectsMalformedOrDeepJsonAndDoesNotExposeValues() {
        for (String bad : List.of("{'playerlists':[]}", "{\"playerlists\":[]}{\"secret\":\"do not reveal\"}", "[]", "{}",
                "{\"playerlists\":null}", "{\"playerlists\":[],\"unused\":"+"[".repeat(40)+"]".repeat(40)+"}")) {
            var error=assertThrows(IOException.class,()->Listen1Backup.parse(bad,SOURCES));
            assertEquals("Invalid or oversized Listen 1 backup",error.getMessage());
            assertNull(error.getCause());
        }
    }

    @Test void filtersControlsButKeepsLiteralDelimiters() throws IOException {
        var json=UPSTREAM_SHAPE.replace("Mixed","Mix \\\"quoted\\\" { [ } ] \\u001B §");
        var result=Listen1Backup.parse(json,SOURCES);
        assertEquals("Mix \"quoted\" { [ } ]",result.playlists().get(1).name());
    }

    @Test void enforcesPlaylistAndTrackBudgets() {
        String index="{\"playerlists\":["+"null,".repeat(Listen1Backup.MAX_PLAYLISTS)+"null]}";
        assertThrows(IOException.class,()->Listen1Backup.parse(index,SOURCES));
        String tracks="{\"playerlists\":[\"myplaylist_1\"],\"myplaylist_1\":{\"info\":{\"id\":\"myplaylist_1\"},\"tracks\":["
                +"null,".repeat(Listen1Backup.MAX_TRACKS_PER_PLAYLIST)+"null]}}";
        assertThrows(IOException.class,()->Listen1Backup.parse(tracks,SOURCES));
    }

    @Test void selectedFileMustBeRegularBoundedUtf8Json(@TempDir Path directory) throws IOException {
        var file=Files.writeString(directory.resolve("listen1_backup.json"),UPSTREAM_SHAPE);
        assertEquals(2,Listen1Backup.read(file,SOURCES).supportedTracks());
        assertThrows(IOException.class,()->Listen1Backup.read(Path.of("relative.json"),SOURCES));
        assertThrows(IOException.class,()->Listen1Backup.read(directory,SOURCES));
        var wrongExtension=Files.writeString(directory.resolve("backup.txt"),UPSTREAM_SHAPE);
        assertThrows(IOException.class,()->Listen1Backup.read(wrongExtension,SOURCES));
        Files.write(file,new byte[]{(byte)0xc0});
        assertThrows(IOException.class,()->Listen1Backup.read(file,SOURCES));
        Files.write(file,new byte[Listen1Backup.MAX_BYTES+1]);
        assertThrows(IOException.class,()->Listen1Backup.read(file,SOURCES));
    }

    @Test void sessionIsIsolatedUntilExplicitReplacement() throws IOException {
        var left=new Listen1Backup.Session();
        var right=new Listen1Backup.Session();
        left.replace(Listen1Backup.parse(UPSTREAM_SHAPE,SOURCES));
        assertEquals(2,left.imported().supportedTracks());
        assertNull(right.imported());
        assertThrows(IOException.class,()->Listen1Backup.parse("invalid", SOURCES));
        assertEquals(2,left.imported().supportedTracks());
        left.replace(new Listen1Backup.Imported(List.of(),Map.of(),0,0));
        assertTrue(left.imported().playlists().isEmpty());
    }
}
