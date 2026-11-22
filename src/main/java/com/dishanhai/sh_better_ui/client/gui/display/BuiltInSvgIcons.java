// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.display;

import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/** SVG sources are shipped with the mod, not read from untrusted image URLs. */
public final class BuiltInSvgIcons {
    private static final String NAMESPACE = "sh_better_ui";
    private static final String SOURCE = "/assets/sh_better_ui/visual_icons/";
    private static final String DISPLAY = "textures/gui/display/";
    private static final String DISPLAY_64 = "textures/gui/display_64/";
    private static final Map<String, Boolean> EXISTS = new HashMap<>();

    private BuiltInSvgIcons() {}

    public static String name(String namespace, String path) {
        if (!NAMESPACE.equals(namespace) || !path.endsWith(".png")) return null;
        if (path.equals("textures/gui/rich/material-shop.png")) return "shop";
        String prefix = path.startsWith(DISPLAY) ? DISPLAY : path.startsWith(DISPLAY_64) ? DISPLAY_64 : null;
        if (prefix == null) return null;
        String name = path.substring(prefix.length(), path.length() - 4);
        return name.matches("[a-z0-9_-]+") ? name : null;
    }

    public static synchronized boolean hasSource(String name) {
        return name != null && EXISTS.computeIfAbsent(name, key ->
                BuiltInSvgIcons.class.getResource(SOURCE + key + ".svg") != null);
    }

    /** Quantize demand so continuous HUD scale animations don't allocate a texture each frame. */
    public static int textureSide(double physicalSide) {
        return Math.min(128, Math.max(24, (int) Math.ceil(physicalSide / 16) * 16));
    }

    public static BufferedImage render(String name, int side) {
        if (!hasSource(name)) throw new IllegalArgumentException("Unknown built-in SVG: " + name);
        try (var in = BuiltInSvgIcons.class.getResourceAsStream(SOURCE + name + ".svg")) {
            var document = new SVGLoader().load(in, null, LoaderContext.createDefault());
            if (document == null) throw new IllegalArgumentException("Invalid bundled SVG: " + name);
            var result = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
            var graphics = result.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                document.render(null, graphics, new ViewBox(0, 0, side, side));
            } finally {
                graphics.dispose();
            }
            return result;
        } catch (IOException problem) {
            throw new UncheckedIOException("Cannot read bundled SVG " + name, problem);
        }
    }
}
