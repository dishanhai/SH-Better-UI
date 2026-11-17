// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.render;

/** Standalone geometric contract; can run without Minecraft or a render context. */
public final class WorldOverlayGeometryTest {
    public static void main(String[] args) {
        var middle = WorldOverlayGeometry.project(new WorldOverlayGeometry.Clip(0, 0, 0, 1), 320, 180);
        require(middle != null && middle.x() == 160 && middle.y() == 90, "center projection");
        require(WorldOverlayGeometry.project(new WorldOverlayGeometry.Clip(0, 0, 0, -1), 320, 180) == null,
                "points behind camera must be hidden");
        require(WorldOverlayGeometry.project(new WorldOverlayGeometry.Clip(Double.NaN, 0, 0, 1), 320, 180) == null,
                "invalid coordinates must be hidden");
        var clipped = WorldOverlayGeometry.line(new WorldOverlayGeometry.Clip(-2, 0, 0, 1),
                new WorldOverlayGeometry.Clip(2, 0, 0, 1), 320, 180);
        require(clipped != null && clipped.a().x() == 0 && clipped.b().x() == 319,
                "crossing lines must clip to viewport");
        require(WorldOverlayGeometry.box(1, 2, 3, 4, 5, 6).size() == 12, "twelve box edges");
        require(WorldOverlayGeometry.clock(0, 0, 0, 0.4, 0).size() >= 26, "clock face and hands");
        int[] pixels = {0};
        WorldOverlayGeometry.raster(new WorldOverlayGeometry.Line(new WorldOverlayGeometry.Point(0, 0),
                new WorldOverlayGeometry.Point(3, 3)), (x, y) -> pixels[0]++);
        require(pixels[0] == 4, "diagonal raster is bounded and continuous");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
