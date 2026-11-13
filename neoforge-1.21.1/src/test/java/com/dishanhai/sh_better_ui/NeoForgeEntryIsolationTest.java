package com.dishanhai.sh_better_ui;

import com.dishanhai.sh_better_ui.client.UiClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：平台入口可在專用伺服器使用，且不依賴 Forge 1.20.1。 */
class NeoForgeEntryIsolationTest {
    @Test
    void usesNeoForgeEntrypointAnnotation() {
        var mod = ShBetterUi.class.getAnnotation(net.neoforged.fml.common.Mod.class);
        assertEquals("sh_better_ui", mod.value());
        assertTrue(ShBetterUi.class.getDeclaredConstructors().length > 0);
    }

    @Test
    void resolvesClientEntrypointWithoutLdLib2() {
        ClassLoader loader = UiClient.class.getClassLoader();
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.lowdragmc.lowdraglib2.gui.ui.UIElement", false, loader));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.appliedenergistics.yoga.style.StyleSizeLength", false, loader));
        assertTrue(UiClient.class.getDeclaredMethods().length > 0);
    }
}
