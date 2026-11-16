package com.muoniumplayer.core.settings;
/** Offline fixture only. No production MP dependency or filesystem write. */
public final class HudConfig {
    public static int saves;
    public static void save() { saves++; }
}
