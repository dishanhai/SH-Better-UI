// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Saved channels and the active profile stay in the OS user's protected store. */
record MusicChatConfig(URI endpoint, String model, String key, String account) {
    static final String DEFAULT_URL = "https://api.openai.com/v1";
    static final int URL_LIMIT = 512;
    static final int MODEL_LIMIT = 128;
    static final int KEY_LIMIT = 1024;
    private static final String CREDENTIAL = "ai-chat";
    static final String COMPLETIONS = "/chat/completions";
    private static final MusicCredentialStore STORE = credentialStore();
    private static JsonObject session = new JsonObject();

    MusicChatConfig(URI endpoint, String model, String key) {
        this(endpoint, model, key, "");
    }

    MusicChatConfig {
        model = model.strip();
        if (!validModel(model)) throw new IllegalArgumentException("Invalid chat model");
        if (account.isEmpty()) {
            Objects.requireNonNull(endpoint);
            key = validateKey(key);
        } else if (endpoint != null || !key.isEmpty() || !ChatGptLogin.validClient(account)) {
            throw new IllegalArgumentException("Invalid ChatGPT registration");
        }
    }

    static MusicChatConfig subscription(String account, String model) {
        return new MusicChatConfig(null, model, "", account);
    }

    boolean subscription() { return !account.isEmpty(); }

    static URI endpoint(String input) {
        String url = input.strip();
        if (url.length() > URL_LIMIT) throw new IllegalArgumentException("Chat URL too long");
        URI uri = URI.create(url).normalize();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("Invalid chat URL");
        }
        url = uri.toString();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (officialOpenAi(uri)) {
            String path = uri.getPath().replaceFirst("/+$", "");
            if (path.isEmpty() || path.equals("/v1") || path.equals("/models") || path.equals(COMPLETIONS)
                    || path.equals("/responses") || path.equals("/v1/models")
                    || path.equals("/v1/responses") || path.equals("/v1" + COMPLETIONS)) {
                url = DEFAULT_URL;
            }
        }
        return URI.create(url.endsWith(COMPLETIONS) ? url : url + COMPLETIONS);
    }

    static boolean officialOpenAi(URI uri) {
        return "api.openai.com".equalsIgnoreCase(uri.getHost()) && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    boolean usesResponses() {
        return subscription() || officialOpenAi(endpoint) && model.startsWith("gpt-6");
    }

    String baseUrl() {
        String url = endpoint.toString();
        return url.substring(0, url.length() - COMPLETIONS.length());
    }

    static boolean validModel(String model) {
        return !model.isBlank() && model.length() <= MODEL_LIMIT && model.chars().noneMatch(Character::isISOControl);
    }

    static String validateKey(String value) {
        String key = value.strip();
        if (key.isEmpty() || key.length() > KEY_LIMIT || key.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new IllegalArgumentException("Invalid chat credentials");
        }
        return key;
    }

    static boolean persistent() { return STORE != null; }

    static synchronized MusicChatConfig load() throws IOException {
        return activeProfile(readConfig());
    }

    static synchronized MusicChatConfig loadApi() throws IOException {
        return apiProfile(readConfig());
    }

    static synchronized List<MusicChatConfig> loadProfiles() throws IOException {
        return savedProfiles(readConfig());
    }

    private static JsonObject readConfig() throws IOException {
        if (!persistent()) return session.deepCopy();
        String saved = STORE.load(CREDENTIAL);
        return saved.isEmpty() ? new JsonObject() : JsonParser.parseString(saved).getAsJsonObject();
    }

    private static MusicChatConfig activeProfile(JsonObject json) {
        if (json.has("account")) {
            return subscription(json.get("account").getAsString(), json.get("subscription_model").getAsString());
        }
        return apiProfile(json);
    }

    private static MusicChatConfig apiProfile(JsonObject json) {
        if (!json.has("url")) return null;
        return new MusicChatConfig(endpoint(json.get("url").getAsString()), json.get("model").getAsString(),
                json.get("key").getAsString());
    }

    private static JsonObject apiRecord(MusicChatConfig profile) {
        var json = new JsonObject();
        json.addProperty("url", profile.baseUrl());
        json.addProperty("model", profile.model());
        json.addProperty("key", profile.key());
        return json;
    }

    static List<MusicChatConfig> savedProfiles(JsonObject json) {
        var profiles = new LinkedHashMap<URI, MusicChatConfig>();
        MusicChatConfig api = apiProfile(json);
        if (api != null) profiles.put(api.endpoint(), api);
        for (var entry : object(json, "api_profiles").entrySet()) {
            var profile = apiProfile(entry.getValue().getAsJsonObject());
            profiles.putIfAbsent(profile.endpoint(), profile);
        }
        var plans = new LinkedHashMap<String, MusicChatConfig>();
        if (json.has("account")) {
            var active = activeProfile(json);
            plans.put(active.account(), active);
        }
        for (var entry : object(json, "subscription_models").entrySet()) {
            plans.putIfAbsent(entry.getKey(), subscription(entry.getKey(), entry.getValue().getAsString()));
        }
        var list = new ArrayList<>(profiles.values());
        list.addAll(plans.values());
        return List.copyOf(list);
    }

    private static JsonObject object(JsonObject json, String key) {
        return json.has(key) ? json.getAsJsonObject(key) : new JsonObject();
    }

    static void activate(JsonObject json, MusicChatConfig profile) {
        var apis = object(json, "api_profiles");
        var plans = object(json, "subscription_models");
        MusicChatConfig lastApi = apiProfile(json);
        if (lastApi != null) apis.add(lastApi.baseUrl(), apiRecord(lastApi));
        if (json.has("account")) {
            plans.addProperty(json.get("account").getAsString(), json.get("subscription_model").getAsString());
        }
        if (profile.subscription()) {
            json.addProperty("account", profile.account());
            json.addProperty("subscription_model", profile.model());
            plans.addProperty(profile.account(), profile.model());
        } else {
            var api = apiRecord(profile);
            for (var entry : api.entrySet()) json.add(entry.getKey(), entry.getValue());
            apis.add(profile.baseUrl(), api);
            json.remove("account");
            json.remove("subscription_model");
        }
        json.add("api_profiles", apis);
        json.add("subscription_models", plans);
    }

    static synchronized void save(MusicChatConfig profile) throws IOException {
        update(() -> {
            var json = readConfig();
            activate(json, profile);
            writeConfig(json);
            return null;
        });
    }

    static synchronized void select(MusicChatConfig profile) throws IOException {
        update(() -> {
            var json = readConfig();
            if (!savedProfiles(json).contains(profile)) throw new IOException("Chat profile changed");
            activate(json, profile);
            writeConfig(json);
            return null;
        });
    }

    static synchronized MusicChatConfig removeApi(MusicChatConfig profile) throws IOException {
        return update(() -> {
            var json = readConfig();
            if (!savedProfiles(json).contains(profile)) throw new IOException("Chat profile changed");
            object(json, "api_profiles").remove(profile.baseUrl());
            var api = apiProfile(json);
            if (api != null && api.endpoint().equals(profile.endpoint())) {
                json.remove("url");
                json.remove("model");
                json.remove("key");
            }
            writeConfig(json);
            return activeProfile(json);
        });
    }

    private static <T> T update(MusicCredentialStore.Update<T> action) throws IOException {
        return persistent() ? STORE.locked(CREDENTIAL, action) : action.run();
    }

    private static void writeConfig(JsonObject json) throws IOException {
        if (persistent()) STORE.put(CREDENTIAL, json.toString());
        else session = json;
    }

    private static MusicCredentialStore credentialStore() {
        Path directory = credentialDirectory();
        return directory == null ? null : new MusicCredentialStore(directory, 128 * 1024);
    }

    static boolean windowsStorage() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    static Path credentialDirectory() {
        if (!windowsStorage() && !java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return null;
        }
        String root = windowsStorage() ? System.getenv("LOCALAPPDATA") : System.getProperty("user.home");
        if (root == null || root.isBlank()) return null;
        Path directory = Path.of(root);
        if (!directory.isAbsolute()) return null;
        if (!windowsStorage()) directory = directory.resolve(".config");
        return directory.resolve("SHBUI").resolve("music-ai");
    }

    @Override public String toString() { return "MusicChatConfig[redacted]"; }
}
