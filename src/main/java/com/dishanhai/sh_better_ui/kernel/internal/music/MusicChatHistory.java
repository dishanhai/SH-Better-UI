// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Local conversation archive. It shares the encrypted user store with the AI preferences. */
final class MusicChatHistory {
    static final Comparator<Entry> RECENT_FIRST = Comparator.comparingLong(Entry::updated).reversed().thenComparing(Entry::id);
    static final MusicChatHistory LOCAL = new MusicChatHistory(MusicChatConfig.credentialDirectory());
    // Worst-case JSON escaping of the bounded transcript, plus metadata.
    private static final int RECORD_LIMIT = 600_000;
    private final MusicCredentialStore storage;
    private final Map<String, Entry> session = new HashMap<>();

    MusicChatHistory(Path root) {
        storage = root == null ? null : new MusicCredentialStore(root.resolve("conversations"), RECORD_LIMIT);
    }

    record Entry(String id, String title, String provider, long updated, int contextStart, List<MusicChatClient.Turn> turns) {
        Entry { turns = List.copyOf(turns); }

        int contextFor(MusicChatConfig profile) {
            return provider.equals(providerId(profile)) ? contextStart : turns.size();
        }
    }

    static String newId() { return UUID.randomUUID().toString(); }

    static String title(String firstQuestion) {
        String line = firstQuestion.replace("§", "\\u00a7").replaceAll("[\\s\\p{Cntrl}]+", " ").strip();
        int count = line.codePointCount(0, line.length());
        return count <= 64 ? line : line.substring(0, line.offsetByCodePoints(0, 63)) + "…";
    }

    static String providerId(MusicChatConfig profile) {
        if (profile == null) return "";
        return profile.subscription() ? "chatgpt:" + profile.account() : profile.baseUrl();
    }

    synchronized List<Entry> load() throws IOException {
        if (storage == null) return session.values().stream().sorted(RECENT_FIRST).toList();
        try {
            var entries = new ArrayList<Entry>();
            for (var item : storage.load().entrySet()) entries.add(decode(item.getKey(), item.getValue()));
            entries.sort(RECENT_FIRST);
            return entries;
        } catch (RuntimeException error) {
            throw new IOException("Invalid saved music chat history");
        }
    }

    synchronized void save(Entry entry) throws IOException {
        if (storage == null) session.put(entry.id(), entry);
        else storage.put(entry.id(), encode(entry));
    }

    synchronized void remove(String id) throws IOException {
        if (storage == null) session.remove(id);
        else storage.remove(id);
    }

    private static String encode(Entry entry) {
        var body = new JsonObject();
        body.addProperty("title", entry.title());
        body.addProperty("provider", entry.provider());
        body.addProperty("updated", entry.updated());
        body.addProperty("context_start", entry.contextStart());
        var turns = new JsonArray();
        for (var turn : entry.turns()) {
            var object = new JsonObject();
            object.addProperty("question", turn.question());
            object.addProperty("answer", turn.answer());
            object.addProperty("markdown_start", turn.markdownStart());
            turns.add(object);
        }
        body.add("turns", turns);
        return body.toString();
    }

    private static Entry decode(String id, String source) {
        if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException();
        var body = JsonParser.parseString(source).getAsJsonObject();
        var list = body.getAsJsonArray("turns");
        if (list.isEmpty() || list.size() > MusicChatClient.HISTORY_LIMIT) throw new IllegalArgumentException();
        var turns = new ArrayList<MusicChatClient.Turn>();
        for (var member : list) {
            var object = member.getAsJsonObject();
            String question = object.get("question").getAsString(), answer = object.get("answer").getAsString();
            int markdown = object.get("markdown_start").getAsInt();
            if (markdown < -1 || markdown > answer.length()) throw new IllegalArgumentException();
            turns.add(new MusicChatClient.Turn(question, answer, markdown));
        }
        int context = body.get("context_start").getAsInt();
        if (context < 0 || context > turns.size()) throw new IllegalArgumentException();
        return new Entry(id, body.get("title").getAsString(), body.get("provider").getAsString(),
                body.get("updated").getAsLong(), context, turns);
    }
}
