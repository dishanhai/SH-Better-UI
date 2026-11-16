// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OnlineMusicBackendContractTest {
    @Test void exposesReusableAccountAndSearchBackend() {
        assertDoesNotThrow(() -> OnlineMusicPlayer.class.getMethod("account", String.class));
        assertDoesNotThrow(() -> OnlineMusicPlayer.class.getMethod("playlists", String.class, int.class));
        assertDoesNotThrow(() -> OnlineMusicPlayer.class.getMethod("liked", String.class, int.class));
        assertDoesNotThrow(() -> OnlineMusicPlayer.class.getMethod("suggest", String.class, String.class));
        assertDoesNotThrow(() -> OnlineMusicPlayer.class.getMethod("rememberCredentials", String.class, boolean.class));
    }
}
