// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.scaled;

/**
 * Native UUK treatments inspired by MuoniumPlayer's island catalogue.
 * Glass treatments are translucent native surfaces, not Skia refraction replicas.
 * IDs, rather than ordinal positions, are persisted; existing IDs must never change.
 */
public enum IslandShellStyle {
    CLASSIC("classic"), PILL("pill"), MINIMAL("minimal"),
    GLASS("glass"), COMPACT("compact"), CARD("card"), SYSTEM_CARD("system_card"),
    MUSIC_FOCUS("music_focus"), LIQUID_GLASS("liquid_glass"), BOUNCE("bounce"),
    MUSIC_CAPSULE("music_capsule");
    private final String id;
    IslandShellStyle(String id) { this.id = id; }
    public String id() { return id; }
    public IslandShellStyle next() { return values()[(ordinal() + 1) % values().length]; }
    public static boolean isKnownId(String id) {
        for (var style : values()) if (style.id.equals(id)) return true;
        return false;
    }
    public static IslandShellStyle fromId(String id) {
        for (var style : values()) if (style.id.equals(id)) return style;
        return CLASSIC;
    }
    public boolean glass() { return this == GLASS || this == LIQUID_GLASS; }
    public boolean musicCapsule() { return this == MUSIC_CAPSULE; }
    public boolean accentRail() { return this == CARD || this == SYSTEM_CARD || this == MUSIC_FOCUS; }
    public long hoverDelayMs() { return musicCapsule() ? 450 : 0; }
    public record Shell(float radius, float border, int edge, int fill) {}
    public Shell shell(float defaultRadius, float height, boolean hovered, boolean highContrast) {
        float radius = switch (this) {
            case PILL, BOUNCE, MUSIC_CAPSULE -> Math.min(20, height / 2);
            case MINIMAL, COMPACT -> 4;
            case CARD, SYSTEM_CARD -> 7;
            case GLASS, LIQUID_GLASS -> 14;
            default -> defaultRadius;
        };
        radius = Math.max(0, Math.min(radius, height / 2));
        if (highContrast) return new Shell(radius, 2, 0xFFFFFFFF, 0xFF08090C);
        return switch (this) {
            case CLASSIC -> new Shell(radius, 1, hovered ? 0xFF546478 : 0xFF344052,
                    hovered ? 0xF21A202B : 0xF0111620);
            case PILL, MUSIC_CAPSULE -> new Shell(radius, 1, hovered ? 0xFF72DDD0 : 0xFF252C35, 0xFA080B10);
            case MINIMAL -> new Shell(radius, 1, hovered ? 0xFF72DDD0 : 0xFF17222E, 0xFF17222E);
            case GLASS -> new Shell(radius, 1, hovered ? 0xFFD9F7FF : 0x889FBACB, 0xDA142B3A);
            case COMPACT -> new Shell(radius, 1, hovered ? 0xFFBFD2E6 : 0xFF4B596A, 0xFF121923);
            case CARD -> new Shell(radius, 1, hovered ? 0xFF9EBCDE : 0xFF3A4B62, 0xFA1C293A);
            case SYSTEM_CARD -> new Shell(radius, 1, hovered ? 0xFFD7E4F4 : 0xFF677587, 0xF52A3442);
            case MUSIC_FOCUS -> new Shell(radius, 1, hovered ? 0xFFDCCFFF : 0xFF716193, 0xF3251C38);
            case LIQUID_GLASS -> new Shell(radius, 1.5f, hovered ? 0xFFFFFFFF : 0xBBD7F3FF, 0xDA203444);
            case BOUNCE -> new Shell(radius, 1, hovered ? 0xFFB3FCE5 : 0xFF4DA58E, 0xF215302C);
        };
    }
}
