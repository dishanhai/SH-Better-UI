// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;
import com.dishanhai.sh_better_ui.config.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlPanelLayoutCodecTest {
    @Test void layoutRoundTripsAndMissingLegacyValueRetainsSidebarDefault() {
        for(var layout:ControlPanelLayout.values()) {
            var settings=ClientUiSettings.DEFAULT.withControlPanelLayout(layout);
            assertEquals(settings,ClientSettingsCodec.CODEC.decode(ClientSettingsCodec.CODEC.encode(settings)));
        }
        var legacy=JsonParser.parseString(ClientSettingsCodec.CODEC.encode(ClientUiSettings.DEFAULT)).getAsJsonObject();
        legacy.remove("controlPanelLayout");
        assertEquals(ControlPanelLayout.SIDEBAR,ClientSettingsCodec.CODEC.decode(legacy.toString()).controlPanelLayout());
        legacy.addProperty("controlPanelLayout","future-mode");
        assertEquals(ControlPanelLayout.SIDEBAR,ClientSettingsCodec.CODEC.decode(legacy.toString()).controlPanelLayout());
    }
}
