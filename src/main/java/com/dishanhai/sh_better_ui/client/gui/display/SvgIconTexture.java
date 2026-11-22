// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.display;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Render-thread cache: stable texture IDs, one raster per SVG and physical size tier. */
public final class SvgIconTexture {
    public record Region(ResourceLocation id, int side) {}

    private static final Map<ResourceLocation, String> ICON_NAMES = new HashMap<>();
    private static final Map<ResourceLocation, Region> TEXTURES = new HashMap<>();

    private SvgIconTexture() {}

    public static Region find(ResourceLocation source, double logicalSide) {
        String name = ICON_NAMES.computeIfAbsent(source, key -> {
            String match = BuiltInSvgIcons.name(key.getNamespace(), key.getPath());
            return BuiltInSvgIcons.hasSource(match) ? match : "";
        });
        if (name.isEmpty()) return null;
        var minecraft = Minecraft.getInstance();
        int side = BuiltInSvgIcons.textureSide(logicalSide * minecraft.getWindow().getGuiScale());
        var id = ResourceLocation.fromNamespaceAndPath("sh_better_ui", "svg_icon_cache/" + side + "/" + name);
        var region = TEXTURES.get(id);
        if (region == null) {
            var raster = BuiltInSvgIcons.render(name, side);
            var pixels = new NativeImage(side, side, false);
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) {
                    int color = raster.getRGB(x, y);
                    pixels.setPixelRGBA(x, y, (color & 0xff00ff00) | ((color & 0xff) << 16) | ((color >>> 16) & 0xff));
                }
            }
            var dynamic = new DynamicTexture(pixels);
            dynamic.setFilter(true, false);
            minecraft.getTextureManager().register(id, dynamic);
            region = new Region(id, side);
            TEXTURES.put(id, region);
        }
        return region;
    }
}
