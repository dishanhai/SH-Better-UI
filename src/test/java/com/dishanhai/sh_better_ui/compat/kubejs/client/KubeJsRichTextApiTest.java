// Author: dishanhai
package com.dishanhai.sh_better_ui.compat.kubejs.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KubeJsRichTextApiTest {
    @Test
    void passiveDocumentsRoundTripAndCachedFacadeExpiresWithTheScriptGeneration() {
        var generation = new KubeJsUiApi();
        var api = generation.richText();
        var document = api.markup("kubejs:sample",
                "[b]鑽石[/b] [img=minecraft:textures/item/diamond.png;16;16;Diamond]");
        assertEquals(document, api.json(api.encode(document)));
        assertThrows(IllegalArgumentException.class,
                () -> api.markup("kubejs:sample", "[ext=kubejs:active;1;1;1;1;alt;data]"));
        generation.close();
        assertThrows(IllegalStateException.class, generation::richText);
        assertThrows(IllegalStateException.class, () -> api.encode(document));
        assertThrows(IllegalStateException.class, () -> api.open(document));
    }
}
