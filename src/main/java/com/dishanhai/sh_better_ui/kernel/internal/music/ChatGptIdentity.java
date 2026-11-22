// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import static com.dishanhai.sh_better_ui.kernel.internal.music.MusicChatClient.text;

/** Verifies only OpenAI's published RS256 ID tokens using the fixed OpenAI JWKS endpoint. */
final class ChatGptIdentity {
    static final String ISSUER = "https://auth.openai.com";

    static JsonObject verify(String token, JsonObject jwks, String client, String nonce, long now)
            throws GeneralSecurityException {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) throw invalid();
        var decoder = Base64.getUrlDecoder();
        var header = MusicChatClient.json(decoder.decode(parts[0]));
        String kid = text(header.get("kid"));
        if (!"RS256".equals(text(header.get("alg"))) || kid.isBlank() || header.has("crit")) throw invalid();

        JsonObject jwk = null;
        for (JsonElement key : jwks.getAsJsonArray("keys")) {
            var candidate = key.getAsJsonObject();
            if (!kid.equals(text(candidate.get("kid")))) continue;
            if (jwk != null || !"RSA".equals(text(candidate.get("kty")))
                    || candidate.has("alg") && !"RS256".equals(text(candidate.get("alg")))
                    || candidate.has("use") && !"sig".equals(text(candidate.get("use")))) throw invalid();
            jwk = candidate;
        }
        if (jwk == null) throw invalid();
        var modulus = new BigInteger(1, decoder.decode(text(jwk.get("n"))));
        var exponent = new BigInteger(1, decoder.decode(text(jwk.get("e"))));
        if (modulus.bitLength() < 2048 || modulus.bitLength() > 8192 || exponent.bitLength() > 32) throw invalid();
        var rsa = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(rsa);
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!signature.verify(decoder.decode(parts[2]))) throw invalid();

        var claims = MusicChatClient.json(decoder.decode(parts[1]));
        if (!ISSUER.equals(text(claims.get("iss"))) || !audience(claims, client)
                || text(claims.get("sub")).isBlank() || timestamp(claims.get("exp")) <= now - 5
                || timestamp(claims.get("iat")) > now + 5
                || claims.has("nbf") && timestamp(claims.get("nbf")) > now + 5
                || !nonce.equals(text(claims.get("nonce")))) throw invalid();
        return claims;
    }

    private static boolean audience(JsonObject claims, String client) {
        JsonElement audience = claims.get("aud");
        String authorizedParty = text(claims.get("azp"));
        if (claims.has("azp") && !client.equals(authorizedParty)) return false;
        if (audience == null || !audience.isJsonArray()) return client.equals(text(audience));
        var audiences = audience.getAsJsonArray();
        if (audiences.size() > 1 && !client.equals(authorizedParty)) return false;
        for (var value : audiences) if (client.equals(text(value))) return true;
        return false;
    }

    private static long timestamp(JsonElement value) throws GeneralSecurityException {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid();
        return value.getAsBigDecimal().longValueExact();
    }

    private static GeneralSecurityException invalid() { return new GeneralSecurityException("Invalid OpenAI ID token"); }
}
