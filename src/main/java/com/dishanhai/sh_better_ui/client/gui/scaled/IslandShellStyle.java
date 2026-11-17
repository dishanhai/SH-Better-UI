// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Native shell treatments, not replicas of the complete MuoniumPlayer style catalogue. */
public enum IslandShellStyle {
    CLASSIC("classic"), PILL("pill"), MINIMAL("minimal");
    private final String id;
    IslandShellStyle(String id) { this.id = id; }
    public String id() { return id; }
    public static IslandShellStyle fromId(String id) {
        for (var style : values()) if (style.id.equals(id)) return style;
        return CLASSIC;
    }
    public record Shell(float radius, float border, int edge, int fill) {}
    public Shell shell(float defaultRadius, float height, boolean hovered, boolean highContrast) {
        float radius = this == PILL ? Math.min(20, height / 2) : this == MINIMAL ? 4 : defaultRadius;
        if (highContrast) return new Shell(radius, 2, 0xFFFFFFFF, 0xFF08090C);
        return switch (this) {
            case CLASSIC -> new Shell(radius, 1, hovered ? 0xFF546478 : 0xFF344052,
                    hovered ? 0xF21A202B : 0xF0111620);
            case PILL -> new Shell(radius, 1, hovered ? 0xFF72DDD0 : 0xFF252C35, 0xFA080B10);
            case MINIMAL -> new Shell(radius, 1, hovered ? 0xFF72DDD0 : 0xFF17222E, 0xFF17222E);
        };
    }
}
