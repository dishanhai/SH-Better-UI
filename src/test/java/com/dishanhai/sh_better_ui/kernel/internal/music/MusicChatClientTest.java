// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.SongEncyclopedia;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MusicChatClientTest {
    private static final String TEST_KEY = "test-only-music-chat-credential";
    private static final MusicChatConfig PROFILE = new MusicChatConfig(
            MusicChatConfig.endpoint("https://music-ai.example/v1"), "chat-model", TEST_KEY);

    @Test void automaticSongReviewsRequireOptInForTheActiveChannel() {
        var saved = new JsonObject();
        MusicChatConfig.activate(saved, PROFILE);
        assertFalse(MusicChatConfig.automaticSongReview(saved));
        saved.addProperty("automatic_song_review", "true");
        assertFalse(MusicChatConfig.automaticSongReview(saved));
        saved.addProperty("automatic_song_review", true);
        MusicChatConfig.activate(saved, PROFILE);
        assertTrue(MusicChatConfig.automaticSongReview(saved));
        assertFalse(new SongAiReview(null, true).automatic());

        var other = MusicChatConfig.subscription("oaiapp_demo", "review-model");
        MusicChatConfig.activate(saved, other);
        assertFalse(MusicChatConfig.automaticSongReview(saved));
        MusicChatConfig.activate(saved, PROFILE);
        assertFalse(MusicChatConfig.automaticSongReview(saved));
    }

    @Test void songReviewSharesOnlyBoundedCatalogueFields() {
        var notes = new SongEncyclopedia("2006-09-05", "Hip hop", "Mandarin", "Label",
                "a".repeat(299) + "🎵", "2006-09-05", "Album notes");
        var song = SongAiReview.trackData(" Song ", "Artist", "Album", notes);
        assertEquals(Set.of("title", "artist", "album", "genre", "introduction"), song.keySet());
        assertEquals("Song", song.get("title").getAsString());
        assertEquals("a".repeat(299), song.get("introduction").getAsString());
        assertEquals(Set.of("title", "artist", "album"), SongAiReview.trackData("Song", "", "", null).keySet());
    }

    @Test void fetchesModelsWithGetOnTheSameApiBase() {
        var base = MusicChatConfig.endpoint("https://music-ai.example:8443/api/v1/chat/completions/");
        var request = MusicChatClient.modelsRequest(base, TEST_KEY);
        assertEquals(URI.create("https://music-ai.example:8443/api/v1/models"), request.uri());
        assertEquals("GET", request.method());
        assertTrue(request.bodyPublisher().isEmpty());
        assertEquals("Bearer " + TEST_KEY, request.headers().firstValue("Authorization").orElseThrow());
    }

    @Test void normalizesOpenAiRootAndKeepsCompatibleBasesIntact() {
        for (String value : List.of("https://api.openai.com", "https://api.openai.com/v1/",
                "https://api.openai.com:443/v1/responses", "https://api.openai.com/v1/chat/completions")) {
            var endpoint = MusicChatConfig.endpoint(value);
            assertEquals(URI.create("https://api.openai.com/v1/chat/completions"), endpoint);
            assertEquals(URI.create("https://api.openai.com/v1/models"), MusicChatClient.modelsRequest(endpoint, TEST_KEY).uri());
        }
        assertEquals(URI.create("https://api.deepseek.com/chat/completions"),
                MusicChatConfig.endpoint("https://api.deepseek.com"));
    }

    @Test void routesNewOpenAiToolModelsAndSubscriptionRequestsThroughResponses() throws Exception {
        var openAi = new MusicChatConfig(MusicChatConfig.endpoint("https://api.openai.com"), "gpt-6.2-sol", TEST_KEY);
        var api = MusicChatClient.chatRequest(openAi, List.of(), "播放周杰伦的红模仿", false, null, true);
        assertEquals(URI.create("https://api.openai.com/v1/responses"), api.uri());
        assertFalse(body(api).has("messages"));
        assertFalse(body(api).has("stream"));

        var plan = MusicChatConfig.subscription("oaiapp_demo", "gpt-6.2-sol");
        assertEquals("", plan.key());
        var chat = MusicChatClient.chatRequest(plan, List.of(new MusicChatClient.Turn("问", "答")), "下一首",
                "Changing playback requires permission.", true, TEST_KEY);
        var request = body(chat);
        assertEquals(api.uri(), chat.uri());
        assertEquals("Bearer " + TEST_KEY, chat.headers().firstValue("Authorization").orElseThrow());
        assertTrue(request.get("stream").getAsBoolean());
        assertFalse(request.get("store").getAsBoolean());
        assertFalse(request.has("previous_response_id"));
        assertFalse(request.toString().contains(TEST_KEY));
        var input = request.getAsJsonArray("input");
        assertEquals("developer", input.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("下一首", input.get(3).getAsJsonObject().get("content").getAsString());
        var namespace = request.getAsJsonArray("tools").get(0).getAsJsonObject();
        assertEquals("namespace", namespace.get("type").getAsString());
        assertEquals("control_player", namespace.getAsJsonArray("tools").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("ask_user", namespace.getAsJsonArray("tools").get(1).getAsJsonObject().get("name").getAsString());
        assertFalse(body(MusicChatClient.chatRequest(plan, List.of(), "hello", "Be brief.", false, TEST_KEY)).has("tools"));
    }

    @Test void choosesOnlyVisibleSubscriptionModelsAndKeepsServerOrder() {
        var list = MusicChatClient.subscriptionModels(bytes("""
                {"models":[
                  {"slug":"active-z","display_name":"Preferred","visibility":"list"},
                  {"slug":"internal","display_name":"Internal","visibility":"hide"},
                  {"slug":"active-a","display_name":"Second","visibility":"list"},
                  {"slug":"active-z","display_name":"Duplicate","visibility":"list"}
                ]}
                """), TEST_KEY);
        assertEquals(List.of(new MusicChatClient.Model("active-z", "Preferred"),
                new MusicChatClient.Model("active-a", "Second")), list);
    }

    @Test void acceptsLargeSubscriptionCatalogWithoutRaisingChatLimit() throws Exception {
        var catalog = bytes("{\"models\":[{\"slug\":\"demo\",\"display_name\":\"Demo\",\"visibility\":\"list\","
                + "\"metadata\":\"" + "x".repeat(640 * 1024) + "\"}]}");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, catalog.length);
                exchange.getResponseBody().write(catalog);
            }
        });
        server.start();
        try {
            var url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/models");
            var request = MusicChatClient.request(url).GET().build();
            assertEquals(List.of(new MusicChatClient.Model("demo", "Demo")),
                    MusicChatClient.fetchSubscriptionModels(request, TEST_KEY).get(10, TimeUnit.SECONDS));
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> MusicChatClient.send(request).get(10, TimeUnit.SECONDS));
            assertEquals("response_too_large", MusicChatClient.describe(failure.getCause()).key);
        } finally {
            server.stop(0);
        }
    }

    @Test void retainsLegacyCredentialsAndRemembersModelForEachSavedChannel() {
        var saved = new JsonObject();
        saved.addProperty("url", PROFILE.baseUrl());
        saved.addProperty("model", PROFILE.model());
        saved.addProperty("key", PROFILE.key());
        var plan = MusicChatConfig.subscription("oaiapp_one", "plan-model");
        var otherPlan = MusicChatConfig.subscription("oaiapp_two", "other-plan-model");
        var otherApi = new MusicChatConfig(MusicChatConfig.endpoint("https://another-ai.example/v1"),
                "another-model", "test-only-another-credential");
        assertEquals(List.of(PROFILE), MusicChatConfig.savedProfiles(saved));

        MusicChatConfig.activate(saved, plan);
        MusicChatConfig.activate(saved, otherApi);
        MusicChatConfig.activate(saved, otherPlan);
        MusicChatConfig.activate(saved, PROFILE);
        assertEquals(Set.of(PROFILE, otherApi, plan, otherPlan), Set.copyOf(MusicChatConfig.savedProfiles(saved)));

        var updated = new MusicChatConfig(PROFILE.endpoint(), "updated", TEST_KEY);
        MusicChatConfig.activate(saved, updated);
        assertEquals(Set.of(updated, otherApi, plan, otherPlan), Set.copyOf(MusicChatConfig.savedProfiles(saved)));
    }

    @Test void executesOnlyValidatedToolsFromCompletedResponses() {
        String function = """
                {"type":"function_call","name":"control_player","namespace":"shbui_player","status":"completed",
                 "arguments":{"action":"play","target":"红模仿","value":"周杰伦"}}
                """;
        String complete = "{\"status\":\"completed\",\"output\":[" + function + "]}";
        assertEquals("红模仿 周杰伦", MusicChatClient.responsesReply(bytes(complete), TEST_KEY, true, false).command().query());
        byte[] streamed = event("response.completed", complete);
        assertEquals("红模仿 周杰伦", MusicChatClient.responsesReply(streamed, TEST_KEY, true, true).command().query());
        assertThrows(RuntimeException.class, () -> MusicChatClient.responsesReply(streamed, TEST_KEY, false, true));
        assertThrows(RuntimeException.class, () -> MusicChatClient.responsesReply(bytes(complete.replace(
                "shbui_player", "wrong_namespace")), TEST_KEY, true, false));
        assertThrows(RuntimeException.class, () -> MusicChatClient.responsesReply(bytes(complete.replace(function,
                function + "," + function)), TEST_KEY, true, false));
        assertEquals("response_incomplete", assertThrows(MusicChatClient.Failure.class,
                () -> MusicChatClient.responsesReply(event("response.output_item.done", complete), TEST_KEY, true, true)).key);
        assertEquals("subscription_limit", assertThrows(MusicChatClient.Failure.class,
                () -> MusicChatClient.responsesReply(event("response.failed",
                        "{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\"}}"),
                        TEST_KEY, true, true)).key);
        String text = """
                {"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"你好"}]}]}
                """;
        assertEquals("你好", MusicChatClient.responsesReply(event("response.completed", text), TEST_KEY, true, true).text());
    }

    @Test void classifiesRequestErrorsWithoutEchoingProviderSecrets() {
        var provider = JsonParser.parseString("{\"error\":{\"param\":\"tools\",\"message\":\"" + TEST_KEY + "\"}}")
                .getAsJsonObject();
        var rejected = MusicChatClient.responseFailure(provider, 400, false);
        assertEquals("tools_unsupported", rejected.key);
        assertEquals(400, rejected.status);
        assertFalse(rejected.toString().contains(TEST_KEY));
        assertEquals("bad_request", MusicChatClient.responseFailure(new JsonObject(), 400, false).key);
        assertEquals("subscription_sign_in_required", MusicChatClient.responseFailure(new JsonObject(), 401, true).key);
    }

    private static byte[] event(String event, String response) {
        String compact = JsonParser.parseString(response).toString();
        return bytes("event: " + event + "\r\ndata: {\"type\":\"" + event + "\",\"response\":" + compact + "}\r\n\r\n");
    }

    @Test void rejectsEndpointsAndKeysThatCouldLeakCredentials() {
        for (String url : List.of("http://ai.example/v1", "https://user:pass@ai.example/v1",
                "https://ai.example/v1?key=123", "https://ai.example/v1#fragment", "https:///v1")) {
            assertThrows(IllegalArgumentException.class, () -> MusicChatConfig.endpoint(url));
        }
        assertThrows(IllegalArgumentException.class, () -> MusicChatConfig.validateKey("bad\r\nkey"));
        assertFalse(PROFILE.toString().contains(TEST_KEY));
    }

    @Test void sendsOnlyTheAllowedSongFieldsAndOnlyWithOptIn() throws Exception {
        var song = new MusicChatClient.Song("Track", "Artist", "Album", "LOCAL", 50, 300, true);
        var enabled = MusicChatClient.chatRequest(PROFILE, List.of(), "What is playing?", true, song, false);
        assertEquals(URI.create("https://music-ai.example/v1/chat/completions"), enabled.uri());
        JsonObject request = body(enabled);
        assertFalse(request.get("store").getAsBoolean());
        String system = request.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString();
        var snapshot = JsonParser.parseString(system.substring(system.indexOf('{'))).getAsJsonObject();
        assertEquals(Set.of("title", "artist", "album", "source", "position_ms", "duration_ms", "playing"),
                snapshot.keySet());
        assertEquals("Track", snapshot.get("title").getAsString());
        assertEquals(50, snapshot.get("position_ms").getAsLong());
        assertTrue(snapshot.get("playing").getAsBoolean());

        var disabled = body(MusicChatClient.chatRequest(PROFILE, List.of(), "Hello " + TEST_KEY, false, song, false));
        assertFalse(disabled.toString().contains("Track"));
        assertFalse(disabled.toString().contains("Playback snapshot (JSON)"));
        assertFalse(disabled.toString().contains(TEST_KEY));
        assertTrue(body(MusicChatClient.chatRequest(PROFILE, List.of(), "Playing?", true, null, false))
                .toString().contains("Playback snapshot (JSON): null"));
    }

    @Test void sendsRecentTurnsAndParsesOnlyExpectedResponseFields() throws Exception {
        var turns = IntStream.range(0, 20).mapToObj(i -> new MusicChatClient.Turn("question-" + i, "reply-" + i)).toList();
        var messages = body(MusicChatClient.chatRequest(PROFILE, turns, "Next", false, null, false))
                .getAsJsonArray("messages");
        assertEquals(2 + 2 * MusicChatClient.HISTORY_LIMIT, messages.size());
        assertEquals("question-8", messages.get(1).getAsJsonObject().get("content").getAsString());
        assertEquals("Next", messages.get(messages.size() - 1).getAsJsonObject().get("content").getAsString());
        assertEquals(List.of("a", "z"), MusicChatClient.modelIds(bytes("""
                {"data":[{"id":"z"},{"id":"a"},{"id":"z"},{"id":3},null,{}]}
                """), TEST_KEY));
        assertEquals("Hello [redacted]", MusicChatClient.reply(bytes(
                "{\"choices\":[{\"message\":{\"content\":\"Hello " + TEST_KEY + "\"}}]}"), TEST_KEY, false).text());
        assertThrows(RuntimeException.class, () -> MusicChatClient.reply(bytes("{\"choices\":[]}"), TEST_KEY, false));
    }

    @Test void advertisesControlsOnlyWhenAuthorizedAndReadsOneTypedCommand() throws Exception {
        assertFalse(body(MusicChatClient.chatRequest(PROFILE, List.of(), "Play a song", false, null, false))
                .has("tools"));
        var enabled = body(MusicChatClient.chatRequest(PROFILE, List.of(), "帮我播放夜曲", false, null, true));
        assertFalse(enabled.toString().contains("Playback snapshot"));
        assertEquals("control_player", enabled.getAsJsonArray("tools").get(0).getAsJsonObject()
                .getAsJsonObject("function").get("name").getAsString());

        var response = call("control_player", "{\"action\":\"play\",\"target\":\"夜曲\",\"value\":\"周杰伦\"}");
        var reply = MusicChatClient.reply(response, TEST_KEY, true);
        assertEquals(MusicChatActions.Action.PLAY, reply.command().action());
        assertEquals("夜曲 周杰伦", reply.command().query());
        assertThrows(RuntimeException.class, () -> MusicChatClient.reply(response, TEST_KEY, false));
        var setting = MusicChatClient.reply(call("control_player",
                "{\"action\":\"set_setting\",\"target\":\"sh_better_ui:high_contrast\",\"value\":\"true\"}"),
                TEST_KEY, true).command();
        assertEquals(MusicChatActions.Action.SET_SETTING, setting.action());
        assertEquals("true", setting.value());
        var compatible = JsonParser.parseString(new String(response, StandardCharsets.UTF_8)).getAsJsonObject();
        var function = compatible.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message")
                .getAsJsonArray("tool_calls").get(0).getAsJsonObject().getAsJsonObject("function");
        function.add("arguments", JsonParser.parseString(function.get("arguments").getAsString()));
        assertEquals(reply.command(), MusicChatClient.reply(bytes(compatible.toString()), TEST_KEY, true).command());
    }

    @Test void rejectsInvalidArgumentsAndNeverTreatsChatTextAsACommand() {
        for (String args : List.of(
                "{\"action\":\"pause\",\"number\":1}",
                "{\"action\":\"set_volume\",\"number\":101}",
                "{\"action\":\"set_volume\",\"number\":1.5}",
                "{\"action\":\"set_volume\",\"number\":\"10\"}",
                "{\"action\":\"play\",\"target\":\" " + TEST_KEY + "\"}",
                "{\"action\":\"play\",\"target\":\" \"}",
                "{\"action\":\"play_mode\",\"value\":\"invalid\"}",
                "{\"action\":\"music_hud\",\"value\":\"toggle\"}",
                "{\"action\":\"unsupported\"}")) {
            assertThrows(RuntimeException.class, () -> MusicChatClient.reply(call("control_player", args), TEST_KEY, true));
        }
        assertThrows(RuntimeException.class, () -> MusicChatClient.reply(
                call("other_function", "{\"action\":\"pause\"}"), TEST_KEY, true));
        String encoded = new String(call("control_player", "{\"action\":\"pause\"}"), StandardCharsets.UTF_8);
        var truncated = bytes(encoded.replace("\"finish_reason\":\"tool_calls\"", "\"finish_reason\":\"length\""));
        assertThrows(RuntimeException.class, () -> MusicChatClient.reply(truncated, TEST_KEY, true));
        var repeated = JsonParser.parseString(encoded).getAsJsonObject();
        var calls = repeated.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").getAsJsonArray("tool_calls");
        calls.add(calls.get(0).deepCopy());
        assertThrows(RuntimeException.class, () -> MusicChatClient.reply(bytes(repeated.toString()), TEST_KEY, true));
        var prose = MusicChatClient.reply(bytes("{\"choices\":[{\"message\":{\"content\":\"control_player pause\"}}]}"),
                TEST_KEY, true);
        assertNull(prose.command());
        assertEquals("control_player pause", prose.text());
    }

    @Test void acceptsOneBoundedQuestionFromEitherTransport() throws Exception {
        var tools = body(MusicChatClient.chatRequest(PROFILE, List.of(), "播放将军", false, null, true)).getAsJsonArray("tools");
        assertEquals("ask_user", tools.get(1).getAsJsonObject().getAsJsonObject("function").get("name").getAsString());
        String args = """
                {"prompt":"你想听哪首？","multiple":false,"purpose":"play","options":[
                    {"label":"将军","title":"将军","artist":"周杰伦"},
                    {"label":"将军令","title":"将军令","artist":"吴克群"}]}
                """;
        var reply = MusicChatClient.reply(call("ask_user", args), TEST_KEY, true);
        assertNull(reply.command());
        assertEquals(MusicChatActions.Purpose.PLAY, reply.question().purpose());
        assertEquals(List.of("周杰伦", "吴克群"), reply.question().options().stream().map(MusicChatActions.Option::artist).toList());
        assertThrows(RuntimeException.class, () -> MusicChatClient.reply(call("ask_user", args), TEST_KEY, false));
        String complete = """
                {"status":"completed","output":[{"type":"function_call","name":"ask_user","namespace":"shbui_player",
                 "status":"completed","arguments":%s}]}
                """.formatted(args);
        assertEquals(reply.question(), MusicChatClient.responsesReply(event("response.completed", complete), TEST_KEY, true, true).question());
        String multi = args.replace("\"play\"", "\"queue\"").replace("false", "true");
        assertTrue(MusicChatClient.reply(call("ask_user", multi), TEST_KEY, true).question().multiple());
        for (String malformed : List.of(args.replace("false", "true"), args.replace("将军令", "将军").replace("吴克群", "周杰伦"),
                args.replace("你想听哪首？", TEST_KEY), args.replace("\"label\":\"将军\"", "\"label\":\"将军\",\"action\":\"next\""))) {
            assertThrows(RuntimeException.class, () -> MusicChatClient.reply(call("ask_user", malformed), TEST_KEY, true));
        }
    }

    private static byte[] call(String name, String arguments) {
        var function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("arguments", arguments);
        return bytes("{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"content\":null,\"tool_calls\":["
                + "{\"type\":\"function\",\"function\":" + function + "}]}}]}");
    }

    private static JsonObject body(HttpRequest request) throws Exception {
        var answer = new CompletableFuture<String>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer buffer) {
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            @Override public void onError(Throwable error) { answer.completeExceptionally(error); }
            @Override public void onComplete() { answer.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return JsonParser.parseString(answer.get(5, TimeUnit.SECONDS)).getAsJsonObject();
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
