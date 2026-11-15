// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

/** FuseButton palette and geometry. Distances are in Minecraft GUI units. */
public record FuseButtonStyle(int text, int background, int fuseColor, float radius,
                              Fuse fuse, float fuseThickness, long crossfadeMs) {
    public enum Fuse { OUTLINE, BOTTOM, TOP }

    public enum Size {
        SM(36), MD(44), LG(52);
        private final int height;
        Size(int height) { this.height = height; }
        public int height() { return height; }
    }

    public static final FuseButtonStyle DEFAULT = new FuseButtonStyle(
            0xFFF5F5F5, 0xFF27272A, 0xFFF5A524, 22, Fuse.OUTLINE, 1.5f, 200);

    public FuseButtonStyle {
        java.util.Objects.requireNonNull(fuse, "fuse");
        if (!Float.isFinite(radius) || radius < 0 || !Float.isFinite(fuseThickness)
                || fuseThickness <= 0 || crossfadeMs < 0) throw new IllegalArgumentException("Invalid FuseButton style");
    }

    public FuseButtonStyle withFuse(Fuse placement) {
        return new FuseButtonStyle(text, background, fuseColor, radius, placement, fuseThickness, crossfadeMs);
    }
}
