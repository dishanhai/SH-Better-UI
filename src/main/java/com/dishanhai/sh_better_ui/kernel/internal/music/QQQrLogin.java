// Adapted by dishanhai from MuoniumPlayer MuQQApi. Copyright 2026 MuoniumPlayer Team.
// Apache-2.0; see META-INF/third-party/muonium-player.
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import static com.dishanhai.sh_better_ui.kernel.internal.music.SourceJson.*;

final class QQQrLogin implements OnlineMusicSource.QrChallenge {
    private static final String REFERER = "https://xui.ptlogin2.qq.com/";
    private static final Set<String> HOSTS = Set.of("ssl.ptlogin2.qq.com", "ssl.ptlogin2.graph.qq.com", "graph.qq.com", "y.qq.com", "u.y.qq.com");
    private static final Pattern CALLBACK = Pattern.compile("^\\s*ptuiCB\\((.*)\\);?\\s*$", Pattern.DOTALL);
    private static final Pattern ARG = Pattern.compile("'((?:\\\\.|[^'\\\\])*)'");
    private final LoginHttp http;
    private final String signature;
    private final OnlineMusicLogin.Image image;
    QQQrLogin() throws Exception { this(new LoginHttp(HOSTS)); }
    QQQrLogin(LoginHttp http) throws Exception {
        this.http = http;
        try {
            var response = get("https://ssl.ptlogin2.qq.com/ptqrshow", pairs("appid", "716027609", "e", "2", "l", "M",
                    "s", "3", "d", "72", "v", "4", "t", "0." + nonce(), "daid", "383", "pt_3rd_aid", "100497308"));
            signature = http.value("qrsig");
            if (signature.isBlank()) throw new IOException("QQ QR unavailable");
            image = LoginImages.png(response.bytes());
        } catch (Exception failure) { http.close(); throw failure; }
    }
    @Override public OnlineMusicLogin.Image image() { return image; }
    @Override public OnlineMusicSource.Poll poll() throws Exception {
        var response = get("https://ssl.ptlogin2.qq.com/ptqrlogin", pairs("u1", "https://graph.qq.com/oauth2.0/login_jump",
                "ptqrtoken", "" + hash33(signature, 0), "ptredirect", "0", "h", "1", "t", "1", "g", "1", "from_ui", "1",
                "ptlang", "2052", "action", "0-0-" + nonce(), "js_ver", "20102616", "js_type", "1", "login_sig", "",
                "pt_uistyle", "40", "aid", "716027609", "daid", "383", "pt_3rd_aid", "100497308", "has_onekey", "1"));
        var args = callback(response.text());
        String cookie = "";
        var state = switch (args.isEmpty() ? "" : args.get(0)) {
            case "66" -> OnlineMusicLogin.State.WAITING;
            case "67" -> OnlineMusicLogin.State.SCANNED;
            case "65" -> OnlineMusicLogin.State.EXPIRED;
            case "0" -> OnlineMusicLogin.State.CONFIRMED;
            default -> OnlineMusicLogin.State.FAILED;
        };
        if (state == OnlineMusicLogin.State.CONFIRMED) {
            if (args.size() < 3) throw new IOException("Missing QQ authorization");
            URI target = http.target(args.get(2));
            var query = query(target);
            String uin = query.getOrDefault("uin", ""), sigx = query.getOrDefault("ptsigx", "");
            if (!uin.matches("[0-9]{1,24}") || sigx.isBlank() || sigx.length() > 1024) throw new IOException("Invalid QQ authorization");
            cookie = authorize(uin, sigx);
        }
        return new OnlineMusicSource.Poll(state, cookie);
    }
    private String authorize(String uin, String sigx) throws Exception {
        get("https://ssl.ptlogin2.graph.qq.com/check_sig", pairs("uin", uin, "pttype", "1", "service", "ptqrlogin", "nodirect", "0",
                "ptsigx", sigx, "s_url", "https://graph.qq.com/oauth2.0/login_jump", "ptlang", "2052", "ptredirect", "100",
                "aid", "716027609", "daid", "383", "j_later", "0", "low_login_hour", "0", "regmaster", "0",
                "pt_login_type", "3", "pt_aid", "0", "pt_aaid", "16", "pt_light", "0", "pt_3rd_aid", "100497308"));
        // check_sig sets p_skey in its response headers; OAuth authorization is a separate request.
        String skey = http.value("p_skey");
        if (skey.isBlank()) throw new IOException("Missing QQ authorization cookie");
        String oauthState = UUID.randomUUID().toString();
        var form = pairs("response_type", "code", "client_id", "100497308", "redirect_uri",
                "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/", "scope", "get_user_info,get_app_friends",
                "state", oauthState, "switch", "", "from_ptlogin", "1", "src", "1", "update_auth", "1", "openapi", "1010_1030",
                "g_tk", "" + hash33(skey, 5381), "auth_time", nonce(), "ui", UUID.randomUUID().toString());
        URI auth = URI.create("https://graph.qq.com/oauth2.0/authorize");
        var response = http.request(auth.toString(), MusicHttp.form(form), "application/x-www-form-urlencoded", "https://graph.qq.com/");
        String code = follow(auth, response, oauthState);
        if (code.isBlank()) throw new IOException("Missing QQ authorization code");
        var comm = new LinkedHashMap<String, Object>(pairs("ct", "11", "cv", "12080008", "v", "12080008", "tmeAppID", "qqmusic", "format", "json",
                        "inCharset", "utf-8", "outCharset", "utf-8", "uin", "0"));
        comm.put("tmeLoginType", 2);
        var body = Map.of("comm", comm,
                "req", Map.of("module", "QQConnectLogin.LoginServer", "method", "QQLogin", "param", Map.of("code", code)));
        var json = http.request("https://u.y.qq.com/cgi-bin/musicu.fcg", new Gson().toJson(body), "application/json", "https://y.qq.com/").json();
        var req = object(json, "req");
        String id = text(object(req, "data"), "musicid"), key = text(object(req, "data"), "musickey");
        if (number(req, "code") != 0 || !id.matches("[0-9]{1,24}") || key.isBlank() || key.contains(";"))
            throw new IOException("QQ credential exchange failed");
        return MusicHttp.cookie("uin=" + id + "; qm_keyst=" + key);
    }
    private String follow(URI base, MusicHttp.Response response, String expectedState) throws Exception {
        for (int hop = 0; hop < 6; hop++) {
            String location = response.header("Location");
            if (location == null) return "";
            base = http.target(base.resolve(location).toString());
            var parameters = query(base);
            String code = parameters.getOrDefault("code", "");
            if (!code.isBlank()) {
                if (!expectedState.equals(parameters.get("state"))) throw new IOException("Mismatched QQ authorization state");
                if (!code.matches("[A-Za-z0-9_-]{1,1024}")) throw new IOException("Invalid QQ authorization code");
                return code;
            }
            response = http.request(base.toString(), null, null, "https://graph.qq.com/");
        }
        throw new IOException("Too many QQ authorization redirects");
    }
    private MusicHttp.Response get(String url, Map<String, String> values) throws IOException {
        return http.request(url + "?" + MusicHttp.form(values), null, null, REFERER);
    }
    static List<String> callback(String text) {
        var callback = CALLBACK.matcher(text);
        if (!callback.matches()) return List.of();
        var result = new ArrayList<String>();
        var args = ARG.matcher(callback.group(1));
        while (args.find()) result.add(args.group(1).replace("\\'", "'").replace("\\\\", "\\"));
        return List.copyOf(result);
    }
    static int hash33(String value, int initial) {
        int hash = initial;
        for (int i = 0; i < value.length(); i++) hash += (hash << 5) + value.charAt(i);
        return hash & 0x7fffffff;
    }
    private static Map<String, String> query(URI uri) throws IOException {
        var result = new LinkedHashMap<String, String>();
        try {
            for (String part : Objects.requireNonNullElse(uri.getRawQuery(), "").split("&")) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2) result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid QQ callback query"); }
        return result;
    }
    private static Map<String, String> pairs(String... values) {
        var result = new LinkedHashMap<String, String>();
        for (int i = 0; i < values.length; i += 2) result.put(values[i], values[i + 1]);
        return result;
    }
    private static String nonce() { return Long.toString(System.currentTimeMillis()); }
    @Override public void close() { http.close(); }
}
