// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.benchmark.client;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Point;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.ShadowPath;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;
import com.dishanhai.sh_better_ui.spatial.SpatialScene;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.*;
import com.mojang.blaze3d.platform.NativeImage;

import java.util.List;

/** Samples stable interiors of the rendered frame, away from driver-dependent antialiasing. */
final class SpatialVisualCheck {
    static final int BACKGROUND = 0xff68788a;
    static final int MARKER = 0xff68e69b;
    private static final int SURFACE = 0xffeff5ff;
    private static final int TOP = 0xffee785b;
    private static final int CLIPPED = 0xff8bd4ff;

    private SpatialVisualCheck() {}

    static void install(SpatialScene scene) {
        scene.settings(new Settings(true, true, false, false, Quality.HIGH));
        var back = node(scene, "back", null, Shape.rectangle(46, 32), 18, 18, 0, SURFACE, false);
        back.shadows(List.of(new Shadow(ShadowType.DROP, 0, 9, 7, 1, .9, 0xff121925, true)));
        node(scene, "top", null, Shape.rounded(32, 22, 6), 46, 25, 1, TOP, false);
        var clip = node(scene, "clip", null, Shape.rounded(34, 32, 7), 92, 18, 0, 0, true);
        node(scene, "clip_child", clip.id(), Shape.rectangle(48, 19), -7, 8, 2, CLIPPED, false);
        var rotation = node(scene, "rotation", null, Shape.rectangle(26, 8), 142, 18, 0, SURFACE, false);
        rotation.transform(new Transform(142, 18, 1, 1, Math.PI / 2, 0, 0));
        var inset = node(scene, "inset", null, Shape.rounded(36, 25, 4), 158, 20, 0, SURFACE, false);
        inset.shadows(List.of(new Shadow(ShadowType.INNER, 0, 4, 4, 0, .9, 0xff121925, true)));
        var path = node(scene, "path", null, Shape.rectangle(46, 38), 68, 83, 0, 0, false);
        var outline = List.of(point(0, 0), point(46, 0), point(46, 13),
                point(42, 13), point(42, 38), point(0, 38));
        var hole = List.of(point(12, 8), point(34, 8), point(34, 30), point(12, 30));
        path.shadowPath(new ShadowPath(List.of(outline, hole)));
        path.shadows(List.of(new Shadow(ShadowType.DROP, 7, 0, 3, 0, .95, 0xff121925, true)));
    }

    private static Point<Local> point(double x, double y) { return new Point<>(x, y); }

    private static SpatialScene.Node node(SpatialScene scene, String name, UiId parent, Shape shape,
                                          double x, double y, int z, int color, boolean clip) {
        var id = new UiId("sh_better_ui", "bench/visual/" + name);
        var transform = new Transform(x, y, 1, 1, 0, 0, 0);
        var node = scene.create(new SpatialScene.Spec(id, parent, shape, transform, z, 0, false, clip));
        node.material(new Material(color, 1, 1, 0, 0, 0, 0));
        return node;
    }

    static void verify(NativeImage image, int guiWidth, int guiHeight, boolean sdf) {
        near("back surface", SURFACE, pixel(image, guiWidth, guiHeight, 29, 28));
        near("foreground occlusion", TOP, pixel(image, guiWidth, guiHeight, 55, 36));
        near("child inside clip", CLIPPED, pixel(image, guiWidth, guiHeight, 112, 33));
        near("child outside clip", BACKGROUND, pixel(image, guiWidth, guiHeight, 89, 33));
        near("rotated surface", SURFACE, pixel(image, guiWidth, guiHeight, 138, 32));
        near("rotation leaves original area", BACKGROUND, pixel(image, guiWidth, guiHeight, 149, 24));
        near("vanilla fill after spatial draw", MARKER, pixel(image, guiWidth, guiHeight, 42, 79));
        if (sdf) {
            darker("drop shadow", BACKGROUND, pixel(image, guiWidth, guiHeight, 33, 55));
            darker("inner shadow", SURFACE, pixel(image, guiWidth, guiHeight, 178, 22));
            darker("path outer shadow", BACKGROUND, pixel(image, guiWidth, guiHeight, 116, 87));
            darker("path hole shadow", BACKGROUND, pixel(image, guiWidth, guiHeight, 81, 102));
        } else {
            near("fallback outside surface", BACKGROUND, pixel(image, guiWidth, guiHeight, 33, 55));
            near("fallback inside surface", SURFACE, pixel(image, guiWidth, guiHeight, 178, 22));
            near("fallback without path shadow", BACKGROUND, pixel(image, guiWidth, guiHeight, 116, 87));
        }
        near("path hole center remains open", BACKGROUND, pixel(image, guiWidth, guiHeight, 94, 102));
        near("path drop stays outside its outline", BACKGROUND, pixel(image, guiWidth, guiHeight, 105, 89));
        near("inner shadow remains inside", BACKGROUND, pixel(image, guiWidth, guiHeight, 178, 17));
    }

    private static int pixel(NativeImage image, int guiWidth, int guiHeight, int x, int y) {
        int px = (int) ((x + .5) * image.getWidth() / guiWidth);
        int py = (int) ((y + .5) * image.getHeight() / guiHeight);
        int abgr = image.getPixelRGBA(px, py);
        return abgr & 0xff00ff00 | (abgr & 255) << 16 | (abgr >>> 16 & 255);
    }

    private static void near(String label, int expected, int actual) {
        for (int shift = 0; shift <= 16; shift += 8) {
            int delta = (expected >>> shift & 255) - (actual >>> shift & 255);
            if (Math.abs(delta) > 8) throw new AssertionError(label + " expected "
                    + Integer.toHexString(expected) + " got " + Integer.toHexString(actual));
        }
    }

    private static void darker(String label, int base, int actual) {
        if ((base >>> 16 & 255) - (actual >>> 16 & 255) < 12) {
            throw new AssertionError(label + " did not darken the sampled pixel: " + Integer.toHexString(actual));
        }
    }
}
