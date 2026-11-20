package com.muoniumplayer.core.ncm.apple;

import com.muoniumplayer.core.ncm.music.MusicPlatform;
import com.muoniumplayer.core.ncm.music.dto.Music;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppleMusicLinkTest {
    @Test
    void buildsOnlyOfficialCatalogSongLinks() {
        Music music = appleTrack("1613600188");
        assertEquals("https://music.apple.com/us/song/1613600188", AppleMusicLink.forTrack(music, "us"));
        assertEquals("https://music.apple.com/tw/song/1613600188", AppleMusicLink.forTrack(music, "tw"));
        assertEquals("", AppleMusicLink.forTrack(music, "zz"));
        assertEquals("", AppleMusicLink.forTrack(music, "US"));
        assertEquals("", AppleMusicLink.forTrack(music, "us/../../other"));
        assertEquals("", AppleMusicLink.forTrack(music, null));
        assertEquals("", AppleMusicLink.forTrack(null, "us"));
    }

    @Test
    void rejectsNonAppleTracksAndInvalidOrMissingCatalogIds() {
        Music music = appleTrack("12345");
        music.setSource(MusicPlatform.NETEASE);
        assertEquals("", AppleMusicLink.forTrack(music, "us"));
        music.setSource(MusicPlatform.APPLE);
        for (String invalid : new String[] {"i.abc", "0", "12/34", "12?x=1", "１２３", "123456789012345678901"}) {
            music.setSourceId(invalid);
            assertEquals("", AppleMusicLink.forTrack(music, "us"));
        }
        music.setSourceId(null);
        music.setSourceMid(null);
        assertEquals("", AppleMusicLink.forTrack(music, "us"));
        music.setSourceMid("12345");
        assertEquals("https://music.apple.com/us/song/12345", AppleMusicLink.forTrack(music, "us"));
    }

    private static Music appleTrack(String id) {
        AppleMusicService.Track track = new AppleMusicService.Track();
        track.id = id;
        return Music.fromAppleTrack(track);
    }
}
