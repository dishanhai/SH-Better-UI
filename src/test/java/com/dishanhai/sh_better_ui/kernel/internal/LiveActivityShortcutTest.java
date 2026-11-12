// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import org.junit.jupiter.api.Test;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement.Rect;

import java.util.List;

import static com.dishanhai.sh_better_ui.kernel.internal.ClientLiveInput.DetailsShortcut.Result.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.glfw.GLFW.*;

class LiveActivityShortcutTest {
    @Test void eitherAltKeyTriggersOnceOnRelease() {
        var shortcut = new ClientLiveInput.DetailsShortcut();
        for (int key : new int[]{GLFW_KEY_LEFT_ALT, GLFW_KEY_RIGHT_ALT}) {
            assertEquals(NONE, shortcut.key(key, GLFW_PRESS, GLFW_MOD_ALT));
            assertEquals(NONE, shortcut.key(key, GLFW_REPEAT, GLFW_MOD_ALT));
            assertEquals(EXPAND, shortcut.key(key, GLFW_RELEASE, 0));
            assertEquals(NONE, shortcut.key(key, GLFW_RELEASE, 0));
        }
    }

    @Test void chordsAndFocusLossCancelTheShortcut() {
        var shortcut = new ClientLiveInput.DetailsShortcut();
        shortcut.key(GLFW_KEY_LEFT_ALT, GLFW_PRESS, GLFW_MOD_ALT);
        shortcut.key(GLFW_KEY_TAB, GLFW_PRESS, GLFW_MOD_ALT);
        assertEquals(NONE, shortcut.key(GLFW_KEY_LEFT_ALT, GLFW_RELEASE, 0));

        shortcut.key(GLFW_KEY_RIGHT_ALT, GLFW_PRESS, GLFW_MOD_ALT | GLFW_MOD_CONTROL);
        assertEquals(NONE, shortcut.key(GLFW_KEY_RIGHT_ALT, GLFW_RELEASE, 0));

        shortcut.key(GLFW_KEY_LEFT_ALT, GLFW_PRESS, GLFW_MOD_ALT);
        shortcut.reset();
        assertEquals(NONE, shortcut.key(GLFW_KEY_LEFT_ALT, GLFW_RELEASE, 0));
    }

    @Test void altGraveOpensPointerModeWithoutAlsoExpandingOnAltRelease() {
        var shortcut = new ClientLiveInput.DetailsShortcut();
        shortcut.key(GLFW_KEY_LEFT_ALT, GLFW_PRESS, GLFW_MOD_ALT);
        assertEquals(POINTER, shortcut.key(GLFW_KEY_GRAVE_ACCENT, GLFW_PRESS, GLFW_MOD_ALT));
        assertEquals(NONE, shortcut.key(GLFW_KEY_GRAVE_ACCENT, GLFW_REPEAT, GLFW_MOD_ALT));
        assertEquals(NONE, shortcut.key(GLFW_KEY_LEFT_ALT, GLFW_RELEASE, 0));
        assertEquals(POINTER, shortcut.key(GLFW_KEY_GRAVE_ACCENT, GLFW_PRESS, GLFW_MOD_ALT | GLFW_MOD_SHIFT));
        assertEquals(NONE, shortcut.key(GLFW_KEY_GRAVE_ACCENT, GLFW_PRESS, GLFW_MOD_ALT | GLFW_MOD_CONTROL));
        assertEquals(NONE, shortcut.key(GLFW_KEY_GRAVE_ACCENT, GLFW_PRESS, 0));
    }

    @Test void minimizedIslandPlacesBothHintsOutsideProtectedHudAreas() {
        var viewport = new Rect(6, 6, 188, 108);
        var island = new Rect(93, 21, 14, 14);
        assertEquals(new Rect(51, 38, 98, 12),
                ClientLivePresentation.shortcutBounds(viewport, island, 98, 12, List.of()));
        assertEquals(new Rect(51, 6, 98, 12),
                ClientLivePresentation.shortcutBounds(viewport, island, 98, 12, List.of(new Rect(48, 38, 104, 12))));
    }
}
