// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.display;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Render-thread cache: stable texture IDs, one raster per SVG and physical size tier. */
public final class SvgIconTexture {
    public record Region(Identifier id, int side) {}

    private static final Map<Identifier, String> ICON_NAMES = new HashMap<>();
    // Slots index by side / 16; the minimum 24px bucket has its own slot.
    private static final int SIZE_SLOTS = BuiltInSvgIcons.MAX_TEXTURE_SIDE / 16 + 1;
    private static final Map<String, Region[]> TEXTURES = new HashMap<>();

    private SvgIconTexture() {}

    public static void resourcesReloaded() {
        ICON_NAMES.clear();
    }

    public static Region find(Identifier source, double logicalSide) {
        var minecraft = Minecraft.getInstance();
        String name = ICON_NAMES.computeIfAbsent(source, key -> {
            String candidate = BuiltInSvgIcons.name(key.getNamespace(), key.getPath());
            if (!BuiltInSvgIcons.hasSource(candidate)) return "";
            var winner = minecraft.getResourceManager().getResource(key).orElse(null);
            String match = BuiltInSvgIcons.select(key.getNamespace(), key.getPath(), winner == null ? null : winner::open);
            return match == null ? "" : match;
        });
        if (name.isEmpty()) return null;
        int side = BuiltInSvgIcons.textureSide(logicalSide * minecraft.getWindow().getGuiScale());
        var tiers = TEXTURES.computeIfAbsent(name, ignored -> new Region[SIZE_SLOTS]);
        int slot = side / 16;
        var region = tiers[slot];
        if (region == null) {
            var id = Identifier.fromNamespaceAndPath("sh_better_ui", "svg_icon_cache/" + side + "/" + name);
            int[] argb = BuiltInSvgIcons.argbPixels(name, side);
            var pixels = new NativeImage(side, side, false);
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) {
                    int color = argb[y * side + x];
                    pixels.setPixel(x, y, color);
                }
            }
            var dynamic = new DynamicTexture(() -> "SHBUI SVG " + name, pixels);

            minecraft.getTextureManager().register(id, dynamic);
            region = new Region(id, side);
            tiers[slot] = region;
        }
        return region;
    }
}
