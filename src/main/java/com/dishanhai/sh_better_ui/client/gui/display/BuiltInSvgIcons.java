// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.display;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** SVG sources are shipped with the mod, not read from untrusted image URLs. */
public final class BuiltInSvgIcons {
    public static final int MAX_TEXTURE_SIDE = 128;
    private static final String NAMESPACE = "sh_better_ui";
    private static final String SOURCE = "/assets/sh_better_ui/visual_icons/";
    private static final String DISPLAY = "textures/gui/display/";
    private static final String DISPLAY_64 = "textures/gui/display_64/";
    private static final Pattern ICON_NAME = Pattern.compile("[a-z0-9_-]+");
    private static final Map<String, Boolean> EXISTS = new ConcurrentHashMap<>();
    private static final Map<String, SVGDocument> DOCUMENTS = new ConcurrentHashMap<>();

    private BuiltInSvgIcons() {}

    public static String name(String namespace, String path) {
        if (!NAMESPACE.equals(namespace) || !path.endsWith(".png")) return null;
        if (path.equals("textures/gui/rich/material-shop.png")) return "shop";
        String prefix = path.startsWith(DISPLAY) ? DISPLAY : path.startsWith(DISPLAY_64) ? DISPLAY_64 : null;
        if (prefix == null) return null;
        String name = path.substring(prefix.length(), path.length() - 4);
        return ICON_NAME.matcher(name).matches() ? name : null;
    }

    public static boolean hasSource(String name) {
        return name != null && EXISTS.computeIfAbsent(name, key ->
                BuiltInSvgIcons.class.getResource(SOURCE + key + ".svg") != null);
    }

    @FunctionalInterface
    public interface PackTexture {
        InputStream open() throws IOException;
    }

    /** Select SVG only in place of the project's own compatibility PNG. */
    public static String select(String namespace, String path, PackTexture winningTexture) {
        String name = name(namespace, path);
        if (!hasSource(name)) return null;
        if (winningTexture == null) return name;
        try (var bundled = BuiltInSvgIcons.class.getResourceAsStream("/assets/" + namespace + "/" + path);
             var selected = winningTexture.open()) {
            if (bundled == null) return null;
            byte[] baseline = bundled.readAllBytes();
            return Arrays.equals(baseline, selected.readNBytes(baseline.length + 1)) ? name : null;
        } catch (IOException unreadable) {
            // Leave bad pack files to Minecraft's existing texture error handling.
            return null;
        }
    }

    /** Quantize demand so continuous HUD scale animations don't allocate a texture each frame. */
    public static int textureSide(double physicalSide) {
        return Math.max(24, (int) Math.ceil(Math.min(MAX_TEXTURE_SIDE, physicalSide) / 16) * 16);
    }

    public static BufferedImage render(String name, int side) {
        if (!hasSource(name)) throw new IllegalArgumentException("Unknown built-in SVG: " + name);
        var document = DOCUMENTS.computeIfAbsent(name, BuiltInSvgIcons::load);
        var result = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        var graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            // Inline media jobs and the render thread share masters, but may draw at different sizes.
            synchronized (document) {
                document.render(null, graphics, new ViewBox(0, 0, side, side));
            }
        } finally {
            graphics.dispose();
        }
        return result;
    }

    /** Direct ARGB data of the fresh, contiguous TYPE_INT_ARGB image rendered here. */
    public static int[] argbPixels(String name, int side) {
        return ((DataBufferInt) render(name, side).getRaster().getDataBuffer()).getData();
    }

    private static SVGDocument load(String name) {
        try (var in = BuiltInSvgIcons.class.getResourceAsStream(SOURCE + name + ".svg")) {
            var document = new SVGLoader().load(in, null, LoaderContext.createDefault());
            if (document == null) throw new IllegalArgumentException("Invalid bundled SVG: " + name);
            return document;
        } catch (IOException problem) {
            throw new UncheckedIOException("Cannot read bundled SVG " + name, problem);
        }
    }
}
