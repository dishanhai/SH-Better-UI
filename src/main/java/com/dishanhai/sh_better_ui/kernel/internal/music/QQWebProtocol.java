// Adapted by dishanhai from qmweb-sign. Copyright (c) 2024 Jixun Wu.
// MIT; see META-INF/third-party/qmweb-sign/LICENSE.
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource;
import com.google.gson.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** QQ's zzc/ag-1 wire format, scoped to its fixed official endpoints. */
final class QQWebProtocol {
    // Public wire-format key from the website protocol; account credentials remain per request.
    private static final byte[] REQUEST_KEY = Base64.getDecoder().decode("vTBfEND/dLbvVNq4NbXhzw==");
    private static final int[] RESPONSE_KEY = {122, 63, 140, 29, 94, 155, 47, 10, 108, 77, 126, 139, 31, 58, 92, 157, 14, 43, 111, 74, 129};
    private static final int[] SCRAMBLE = {89, 39, 179, 150, 218, 82, 58, 252, 177, 52, 186, 123, 120, 64, 242, 133, 143, 161, 121, 179};
    private static final SecureRandom RANDOM = new SecureRandom();

    static String sign(String body) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(body.getBytes(StandardCharsets.UTF_8)));
            var middle = new byte[SCRAMBLE.length];
            for (int i = 0; i < middle.length; i++) middle[i] = (byte) (SCRAMBLE[i] ^ Integer.parseInt(hash.substring(i * 2, i * 2 + 2), 16));
            return ("zzc" + pick(hash, 23, 14, 6, 36, 16, 7, 19)
                    + Base64.getEncoder().encodeToString(middle).replaceAll("[\\\\/+=]", "")
                    + pick(hash, 16, 1, 32, 12, 19, 27, 8, 5)).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-1 unavailable", impossible); }
    }
    private static String pick(String hash, int... indexes) {
        var out = new StringBuilder(); for (int index : indexes) out.append(hash.charAt(index)); return out.toString();
    }
    static String encode(String body) throws IOException {
        byte[] iv = new byte[12]; RANDOM.nextBytes(iv); return encode(body, iv);
    }
    static String encode(String body, byte[] iv) throws IOException {
        if (iv.length != 12) throw new IllegalArgumentException("Invalid QQ request nonce");
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(REQUEST_KEY, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(body.getBytes(StandardCharsets.UTF_8));
            byte[] result = Arrays.copyOf(iv, iv.length + encrypted.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException failure) { throw new IOException("QQ request encoding unavailable"); }
    }
    static JsonObject decode(byte[] response) throws IOException {
        var decoded = new byte[response.length];
        for (int i = 0; i < response.length; i++) decoded[i] = (byte) (response[i] ^ RESPONSE_KEY[i % RESPONSE_KEY.length]);
        return parse(decoded);
    }
    private static JsonObject parse(byte[] bytes) throws IOException {
        try { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException invalid) { throw new IOException("Invalid QQ response"); }
    }
    static JsonObject request(String uin, String key, String module, String method, JsonObject parameters, boolean write) throws IOException {
        return request(uin, key, module, method, parameters, write,
                (uri, body, type, referer, cookie) -> new MusicHttp().exchange(uri, body, type, referer, MusicHttp.JSON_LIMIT, cookie));
    }
    static JsonObject request(String uin, String key, String module, String method, JsonObject parameters, boolean write,
                              LoginHttp.Transport transport) throws IOException {
        checkCancelled();
        var comm = new JsonObject();
        comm.addProperty("uin", uin); comm.addProperty("authst", key); comm.addProperty("format", "json");
        comm.addProperty("ct", 24); comm.addProperty("cv", 4747474); comm.addProperty("platform", "yqq.json");
        comm.addProperty("g_tk", QQQrLogin.hash33(key, 5381)); comm.addProperty("g_tk_new_20200303", QQQrLogin.hash33(key, 5381));
        comm.addProperty("inCharset", "utf-8"); comm.addProperty("outCharset", "utf-8");
        comm.addProperty("notice", 0); comm.addProperty("needNewCode", 1);
        var req = new JsonObject(); req.addProperty("module", module); req.addProperty("method", method); req.add("param", parameters);
        var payload = new JsonObject(); payload.add("comm", comm); payload.add("req", req);
        // Sign and encrypt the identical serialization; never retry a write or fall back to plaintext.
        String json = new Gson().toJson(payload);
        URI uri = URI.create(write ? "https://u6.y.qq.com/cgi-bin/musics.fcg?" + MusicHttp.form(Map.of(
                "encoding", "ag-1", "sign", sign(json), "_", System.currentTimeMillis())) : "https://u.y.qq.com/cgi-bin/musicu.fcg");
        String cookie = MusicHttp.cookie("uin=" + uin + "; qqmusic_uin=" + uin + "; qm_keyst=" + key + "; qqmusic_key=" + key);
        var response = transport.send(uri, write ? encode(json) : json, write ? "text/plain" : "application/json", "https://y.qq.com/", cookie);
        checkCancelled();
        if (response.status() != 200) throw new IOException("QQ HTTP status " + response.status());
        if (response.bytes().length > MusicHttp.JSON_LIMIT) throw new IOException("QQ response too large");
        return data(write ? decode(response.bytes()) : parse(response.bytes()), write);
    }
    static JsonObject data(JsonObject root, boolean write) throws IOException {
        code(root, "code", true);
        var req = SourceJson.object(root, "req"); code(req, "code", true);
        if (!req.has("data") || !req.get("data").isJsonObject()) throw new IOException("QQ response missing data");
        var data = req.getAsJsonObject("data");
        code(data, "code", false); code(data, "subcode", false); code(data, "retCode", write);
        return data;
    }
    private static void code(JsonObject object, String field, boolean required) throws IOException {
        if (!object.has(field) && !required) return;
        long value = integer(object, field);
        if (value == 301 || value == 1000 || value == 104009 || value == 401) throw new OnlineMusicSource.LoginRequired();
        if (value != 0) throw new IOException("QQ request rejected: " + value);
    }
    static long integer(JsonObject object, String field) throws IOException {
        try {
            var value = object.get(field);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new NumberFormatException();
            return value.getAsBigDecimal().longValueExact();
        } catch (RuntimeException invalid) { throw new IOException("QQ response missing integer field"); }
    }
    static void checkCancelled() throws java.io.InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
    }
}
