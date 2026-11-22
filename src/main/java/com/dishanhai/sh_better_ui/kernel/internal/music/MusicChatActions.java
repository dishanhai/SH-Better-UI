// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import static com.dishanhai.sh_better_ui.kernel.internal.music.MusicChatClient.text;

/** The only playback commands accepted from the chat provider. */
final class MusicChatActions {
    private static final String FUNCTION = "control_player";
    private static final String ASK_USER = "ask_user";
    static final String NAMESPACE = "shbui_player";
    private static final int TEXT_LIMIT = 160;

    enum Action {
        PLAY, QUEUE_NEXT, PAUSE, RESUME, NEXT, PREVIOUS, SET_VOLUME, SEEK, PLAY_MODE,
        OPEN_LYRICS, TRANSLATED_LYRICS, FLOATING_LYRICS, FAVORITE, ADD_TO_PLAYLIST,
        OPEN_CONTROL_CENTER, MUSIC_HUD, THEME, LIST_SETTINGS, READ_SETTING, SET_SETTING;

        String id() { return name().toLowerCase(Locale.ROOT); }
    }

    sealed interface Tool permits Command, Question {}

    enum Purpose { CLARIFY, PLAY, QUEUE }

    record Option(String label, String title, String artist) {}

    record Question(String prompt, boolean multiple, Purpose purpose, List<Option> options) implements Tool {
        Question { options = List.copyOf(options); }
    }

    record Command(Action action, String target, String value, int number) implements Tool {
        boolean needsSearch() { return action == Action.PLAY || action == Action.QUEUE_NEXT; }
        boolean explainsSettings() { return action == Action.LIST_SETTINGS || action == Action.READ_SETTING; }
        String query() { return value.isEmpty() ? target : target + " " + value; }
    }

    private static JsonObject function() {
        var actions = new JsonArray();
        Arrays.stream(Action.values()).map(Action::id).forEach(actions::add);
        JsonObject action = property("string", "Playback operation to perform.");
        action.add("enum", actions);
        var properties = new JsonObject();
        properties.add("action", action);
        properties.add("target", property("string", "Exact song title for play/queue_next; existing playlist name for "
                + "add_to_playlist; theme ID for theme; setting ID for read_setting/set_setting."));
        properties.add("value", property("string", "Artist if specified for play/queue_next; "
                + "sequential, shuffle, repeat_all or repeat_one for play_mode; on/off for translated_lyrics, "
                + "floating_lyrics, favorite or music_hud; the requested value for set_setting."));
        properties.add("number", property("integer", "Absolute volume percentage (0–100) for set_volume; "
                + "absolute position in seconds for seek."));
        var required = new JsonArray();
        required.add("action");
        var parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.addProperty("additionalProperties", false);
        parameters.add("properties", properties);
        parameters.add("required", required);
        var function = new JsonObject();
        function.addProperty("name", FUNCTION);
        function.addProperty("description", "Control the in-game player or client UI at the user's request. "
                + "favorite and add_to_playlist use the current track. list_settings retrieves supported SHBUI/SHCLIB "
                + "setting IDs and values; read_setting retrieves details about a setting.");
        function.add("parameters", parameters);
        return function;
    }

    static JsonArray tools() {
        var tools = new JsonArray();
        for (var definition : List.of(function(), questionFunction())) {
            var tool = new JsonObject();
            tool.addProperty("type", "function");
            tool.add("function", definition);
            tools.add(tool);
        }
        return tools;
    }

    static JsonArray responsesTools() {
        var nested = new JsonArray();
        for (var definition : List.of(function(), questionFunction())) {
            definition.addProperty("type", "function");
            definition.addProperty("strict", false);
            nested.add(definition);
        }
        var namespace = new JsonObject();
        namespace.addProperty("type", "namespace");
        namespace.addProperty("name", NAMESPACE);
        namespace.addProperty("description", "SHBUI music player and client settings.");
        namespace.add("tools", nested);
        var tools = new JsonArray();
        tools.add(namespace);
        return tools;
    }

    private static JsonObject questionFunction() {
        var option = new JsonObject();
        option.add("label", property("string", "Short choice shown to the player."));
        option.add("title", property("string", "Exact song title for play or queue; omit for clarify."));
        option.add("artist", property("string", "Exact artist for play or queue; omit for clarify."));
        var choices = property("array", "Provide 2 to 6 distinct choices. Queue order follows this list.");
        choices.add("items", objectSchema(option, "label"));
        choices.addProperty("minItems", 2);
        choices.addProperty("maxItems", 6);
        var properties = new JsonObject();
        properties.add("prompt", property("string", "Ask one concise question in the player's language."));
        properties.add("multiple", property("boolean", "Allow choosing multiple options. Use false for play."));
        var purpose = property("string", "clarify returns the selections to you; play or queue resolves and plays selected songs after confirmation.");
        var values = new JsonArray();
        for (Purpose value : Purpose.values()) values.add(value.name().toLowerCase(Locale.ROOT));
        purpose.add("enum", values);
        properties.add("purpose", purpose);
        properties.add("options", choices);
        var function = new JsonObject();
        function.addProperty("name", ASK_USER);
        function.addProperty("description", "Show a question with selectable answers in the chat. Use it for ambiguous songs, "
                + "missing preferences and choosing songs to queue. Labels and choices must be relevant to the latest request.");
        function.add("parameters", objectSchema(properties, "prompt", "multiple", "purpose", "options"));
        return function;
    }

    private static JsonObject objectSchema(JsonObject properties, String... fields) {
        var required = new JsonArray();
        for (String field : fields) required.add(field);
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    static Tool parse(JsonArray calls, String key) {
        if (calls.size() != 1) throw invalid();
        JsonObject call = calls.get(0).getAsJsonObject();
        JsonObject function = call.getAsJsonObject("function");
        if (!"function".equals(text(call.get("type")))) throw invalid();
        return parseFunction(function, key);
    }

    static Tool parseFunction(JsonObject function, String key) {
        String name = text(function.get("name"));
        if (!FUNCTION.equals(name) && !ASK_USER.equals(name)) throw invalid();
        JsonElement rawArguments = function.get("arguments");
        // Some compatible providers expose arguments as a JSON object.
        var arguments = rawArguments != null && rawArguments.isJsonObject() ? rawArguments.getAsJsonObject()
                : JsonParser.parseString(text(rawArguments)).getAsJsonObject();
        if (ASK_USER.equals(name)) return parseQuestion(arguments, key);
        String id = text(arguments.get("action"));
        Action action = Arrays.stream(Action.values()).filter(value -> value.id().equals(id))
                .findFirst().orElseThrow(MusicChatActions::invalid);
        return switch (action) {
            case PLAY, QUEUE_NEXT -> {
                checkKeys(arguments, "target", "value");
                String title = argumentText(arguments.get("target"), key);
                String artist = arguments.has("value") ? argumentText(arguments.get("value"), key) : "";
                yield new Command(action, title, artist, 0);
            }
            case SET_VOLUME, SEEK -> {
                checkKeys(arguments, "number");
                int number = argumentNumber(arguments.get("number"), action == Action.SET_VOLUME ? 100 : 86_400);
                yield new Command(action, "", "", number);
            }
            case PLAY_MODE, TRANSLATED_LYRICS, FLOATING_LYRICS, FAVORITE, MUSIC_HUD -> {
                checkKeys(arguments, "value");
                String value = argumentText(arguments.get("value"), key);
                boolean valid = action == Action.PLAY_MODE
                        ? switch (value) {
                            case "sequential", "shuffle", "repeat_all", "repeat_one" -> true;
                            default -> false;
                        }
                        : value.equals("on") || value.equals("off");
                if (!valid) throw invalid();
                yield new Command(action, "", value, 0);
            }
            case THEME, ADD_TO_PLAYLIST, READ_SETTING -> {
                checkKeys(arguments, "target");
                yield new Command(action, argumentText(arguments.get("target"), key), "", 0);
            }
            case SET_SETTING -> {
                checkKeys(arguments, "target", "value");
                yield new Command(action, argumentText(arguments.get("target"), key),
                        argumentText(arguments.get("value"), key), 0);
            }
            default -> {
                checkKeys(arguments);
                yield new Command(action, "", "", 0);
            }
        };
    }

    private static Question parseQuestion(JsonObject args, String key) {
        if (!args.keySet().equals(java.util.Set.of("prompt", "multiple", "purpose", "options"))) throw invalid();
        String prompt = argumentText(args.get("prompt"), key);
        var flag = args.get("multiple");
        if (!flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()) throw invalid();
        boolean multiple = flag.getAsBoolean();
        Purpose purpose = switch (text(args.get("purpose"))) {
            case "clarify" -> Purpose.CLARIFY;
            case "play" -> Purpose.PLAY;
            case "queue" -> Purpose.QUEUE;
            default -> throw invalid();
        };
        var choices = args.getAsJsonArray("options");
        if (choices.size() < 2 || choices.size() > 6 || multiple && purpose == Purpose.PLAY) throw invalid();
        var options = new ArrayList<Option>();
        var distinct = new HashSet<String>();
        for (var choice : choices) {
            var option = choice.getAsJsonObject();
            String label = argumentText(option.get("label"), key);
            String title = "", artist = "";
            if (purpose == Purpose.CLARIFY) {
                if (!option.keySet().equals(java.util.Set.of("label"))) throw invalid();
            } else {
                if (!option.keySet().equals(java.util.Set.of("label", "title", "artist"))) throw invalid();
                title = argumentText(option.get("title"), key);
                artist = argumentText(option.get("artist"), key);
            }
            String identity = purpose == Purpose.CLARIFY ? label : title + "\n" + artist;
            if (!distinct.add(identity.toLowerCase(Locale.ROOT))) throw invalid();
            options.add(new Option(label, title, artist));
        }
        return new Question(prompt, multiple, purpose, options);
    }

    private static void checkKeys(JsonObject arguments, String... permitted) {
        for (String name : arguments.keySet()) {
            if (!name.equals("action") && Arrays.stream(permitted).noneMatch(name::equals)) throw invalid();
        }
    }

    private static String argumentText(JsonElement value, String key) {
        String result = text(value).strip();
        if (result.isEmpty() || result.length() > TEXT_LIMIT || !key.isEmpty() && result.contains(key)
                || result.indexOf('§') >= 0 || result.chars().anyMatch(Character::isISOControl)) throw invalid();
        return result;
    }

    private static int argumentNumber(JsonElement value, int max) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid();
        int number = value.getAsBigDecimal().intValueExact();
        if (number < 0 || number > max) throw invalid();
        return number;
    }

    private static JsonObject property(String type, String description) {
        var property = new JsonObject();
        property.addProperty("type", type);
        property.addProperty("description", description);
        return property;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid music chat tool call");
    }
}
