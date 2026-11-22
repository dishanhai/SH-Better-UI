// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import com.dishanhai.sh_better_ui.kernel.internal.music.OnlineMusicCatalog;

/** Public catalogue notes. Album notes and dates retain their original attribution. */
public record SongEncyclopedia(String songDate, String genre, String language, String label,
                               String introduction, String albumDate, String albumNotes) {
    /** Blocking public lookup by QQ song MID or NetEase song ID. Call on a worker. */
    public static SongEncyclopedia lookup(String source, String id) throws Exception {
        return OnlineMusicCatalog.encyclopedia(source, id);
    }
}
