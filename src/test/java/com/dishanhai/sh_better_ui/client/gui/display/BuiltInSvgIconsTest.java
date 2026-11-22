// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.display;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class BuiltInSvgIconsTest {
    @Test
    void allMasterSvgsShipAndPaintActualPixels() throws Exception {
        Path artwork = Path.of("artwork", "visual-icons");
        if (!Files.isDirectory(artwork)) artwork = Path.of("..").resolve(artwork);
        try (var files = Files.list(artwork)) {
            var icons = files.filter(file -> file.getFileName().toString().endsWith(".svg")).toList();
            assertTrue(icons.size() >= 529);
            for (var icon : icons) {
                String file = icon.getFileName().toString();
                String name = file.substring(0, file.length() - 4);
                assertTrue(BuiltInSvgIcons.hasSource(name), name);
                var raster = BuiltInSvgIcons.render(name, 64);
                assertEquals(64, raster.getWidth());
                assertEquals(64, raster.getHeight());
                boolean containsVisibleInk = false;
                for (int y = 0; y < 64 && !containsVisibleInk; y++)
                    for (int x = 0; x < 64 && !containsVisibleInk; x++)
                        containsVisibleInk = raster.getRGB(x, y) >>> 24 > 100;
                assertTrue(containsVisibleInk, name);
            }
        }
    }

    @Test
    void cachedDocumentProducesTheSamePixelsAcrossSizesAndThreads() throws Exception {
        String name = "compact_clock";
        var expected32 = BuiltInSvgIcons.render(name, 32).getRGB(0, 0, 32, 32, null, 0, 32);
        var expected96 = BuiltInSvgIcons.render(name, 96).getRGB(0, 0, 96, 96, null, 0, 96);
        var workers = Executors.newFixedThreadPool(2);
        try {
            for (int repeat = 0; repeat < 3; repeat++) {
                var first = workers.submit(() -> BuiltInSvgIcons.argbPixels(name, 32));
                var second = workers.submit(() -> BuiltInSvgIcons.argbPixels(name, 96));
                assertArrayEquals(expected32, first.get());
                assertArrayEquals(expected96, second.get());
            }
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void replacementPngWinsOverBundledSvg() throws Exception {
        String path = "textures/gui/display/info.png";
        byte[] bundled;
        try (var in = BuiltInSvgIcons.class.getResourceAsStream("/assets/sh_better_ui/" + path)) {
            bundled = in.readAllBytes();
        }
        assertEquals("info", BuiltInSvgIcons.select("sh_better_ui", path, () -> new ByteArrayInputStream(bundled)));
        var replacement = bundled.clone();
        replacement[replacement.length / 2] ^= 1;
        assertNull(BuiltInSvgIcons.select("sh_better_ui", path, () -> new ByteArrayInputStream(replacement)));
    }

    @Test
    void aliasesKeepTheirIdsWithoutClaimingExternalOrUnlistedImages() {
        assertEquals("github", BuiltInSvgIcons.name("sh_better_ui", "textures/gui/display/github.png"));
        assertEquals("github", BuiltInSvgIcons.name("sh_better_ui", "textures/gui/display_64/github.png"));
        assertEquals("shop", BuiltInSvgIcons.name("sh_better_ui", "textures/gui/rich/material-shop.png"));
        assertNull(BuiltInSvgIcons.name("example", "textures/gui/display/github.png"));
        assertNull(BuiltInSvgIcons.name("sh_better_ui", "textures/gui/display/../github.png"));
        assertFalse(BuiltInSvgIcons.hasSource("other_asset"));
        assertEquals(48, BuiltInSvgIcons.textureSide(48));
        assertEquals(128, BuiltInSvgIcons.textureSide(128));
    }
}
