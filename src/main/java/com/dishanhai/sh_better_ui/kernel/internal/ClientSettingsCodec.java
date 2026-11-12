// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ThemeOverrides;
import com.dishanhai.sh_better_ui.kernel.api.ConfigCodec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializer;

final class ClientSettingsCodec {
    private static final Gson JSON = new GsonBuilder()
            .registerTypeAdapter(ThemeOverrides.class,
                    (JsonSerializer<ThemeOverrides>) (value, type, context) -> JsonParser.parseString(value.json()))
            .registerTypeAdapter(ThemeOverrides.class,
                    (JsonDeserializer<ThemeOverrides>) (value, type, context) -> new ThemeOverrides(value.toString()))
            .create();

    static final ConfigCodec<ClientUiSettings> CODEC = ConfigCodec.of(
            JSON::toJson, encoded -> JSON.fromJson(encoded, ClientUiSettings.class));

    private ClientSettingsCodec() {}
}
