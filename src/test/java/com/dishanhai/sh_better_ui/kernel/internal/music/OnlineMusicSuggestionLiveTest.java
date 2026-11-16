// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Public autocomplete only: no account cookies, mutations, QR images or audio devices. */
@EnabledIfEnvironmentVariable(named = "SHBUI_MUSIC_LIVE", matches = "1")
class OnlineMusicSuggestionLiveTest {
    @Test void bothPlatformsReturnSuggestionsForPinyin() throws Exception {
        assertFalse(new NeteaseSource().suggest("zhoujielun", 8).isEmpty());
        assertFalse(new QQMusicSource().suggest("zhoujielun", 8).isEmpty());
    }
}
