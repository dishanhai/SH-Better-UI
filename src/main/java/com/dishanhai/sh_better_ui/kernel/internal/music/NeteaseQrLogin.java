// Adapted by dishanhai from MuoniumPlayer MuNeteaseApi. Copyright 2026 MuoniumPlayer Team.
// Apache-2.0; see META-INF/third-party/muonium-player.
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.*;
import static com.dishanhai.sh_better_ui.kernel.internal.music.SourceJson.*;

final class NeteaseQrLogin implements OnlineMusicSource.QrChallenge {
    private final LoginHttp http;
    private final String key;
    private final OnlineMusicLogin.Image image;
    NeteaseQrLogin() throws Exception { this(new LoginHttp(Set.of("music.163.com"))); }
    NeteaseQrLogin(LoginHttp http) throws Exception {
        this.http = http;
        try {
            http.seed("https://music.163.com/", "os=pc; Path=/", "appver=8.9.70; Path=/");
            var data = request("unikey", Map.of("type", "1")).json();
            key = text(data, "unikey");
            if (number(data, "code") != 200 || !key.matches("[A-Za-z0-9_-]{8,512}")) throw new IOException("NetEase QR unavailable");
            image = LoginImages.encode("https://music.163.com/login?codekey=" + key);
        } catch (Exception failure) { http.close(); throw failure; }
    }
    private MusicHttp.Response request(String action, Map<String, String> values) throws Exception {
        var encrypted = MuWeapi.weapi(new Gson().toJson(values));
        return http.request("https://music.163.com/weapi/login/qrcode/" + action,
                MusicHttp.form(Map.of("params", encrypted.getParams(), "encSecKey", encrypted.getEncSecKey())),
                "application/x-www-form-urlencoded", "https://music.163.com/");
    }
    @Override public OnlineMusicLogin.Image image() { return image; }
    @Override public OnlineMusicSource.Poll poll() throws Exception {
        var response = request("client/login", Map.of("key", key, "type", "1"));
        var state = switch ((int) number(response.json(), "code")) {
            case 801 -> OnlineMusicLogin.State.WAITING;
            case 802 -> OnlineMusicLogin.State.SCANNED;
            case 803 -> OnlineMusicLogin.State.CONFIRMED;
            case 800 -> OnlineMusicLogin.State.EXPIRED;
            default -> OnlineMusicLogin.State.FAILED;
        };
        // A success code without the account cookie is not a usable login.
        if (state == OnlineMusicLogin.State.CONFIRMED && http.value("MUSIC_U").isBlank()) state = OnlineMusicLogin.State.FAILED;
        return new OnlineMusicSource.Poll(state, state == OnlineMusicLogin.State.CONFIRMED ? http.credentials() : "");
    }
    @Override public void close() { http.close(); }
}
