// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLogin.State.*;

/** Opt-in probe: only obtains an unscanned code, never prints it or authenticates an account. */
@EnabledIfEnvironmentVariable(named = "SHBUI_MUSIC_LIVE", matches = "1")
class QrLoginLiveTest {
    @Test void neteaseCreatesAnUnscannedCodeAndPolls() throws Exception {
        try (var login = new NeteaseQrLogin()) {
            assertNotNull(login.image()); assertTrue(login.image().width() >= 21);
            assertEquals(WAITING, login.poll().state());
            System.out.println("NetEase: QR creation and waiting-state poll succeeded.");
        }
    }
    @Test void qqCreatesAnUnscannedCodeAndPolls() throws Exception {
        try (var login = new QQQrLogin()) {
            assertNotNull(login.image()); assertTrue(login.image().width() >= 21);
            assertEquals(WAITING, login.poll().state());
            System.out.println("QQ: QR PNG retrieval and waiting-state poll succeeded.");
        }
    }
}
