// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native ambient status and music paging, modelled after MP without its rendering dependencies. */
public final class IslandAmbientState {
    public enum Page { STATUS, NOW_PLAYING }
    public enum Health { NEUTRAL, GOOD, WARN, BAD }
    public record Chip(String value, String widthTemplate, String unit, Health health) {}
    public record Settings(boolean fps, boolean ping, boolean clock, boolean nowPlaying, boolean rotate,
                           long statusMs, long musicMs) {
        public Settings {
            if (statusMs < 1000 || statusMs > 60000 || musicMs < 1000 || musicMs > 60000)
                throw new IllegalArgumentException("page duration must be 1–60 seconds");
        }
        public static final Settings DISABLED = new Settings(false,false,false,true,false,5000,5000);
        public static final Settings DEFAULT = new Settings(true,true,true,true,true,5000,5000);
    }
    private Settings settings = Settings.DISABLED;
    private List<Chip> chips = List.of();
    private long epoch = Long.MIN_VALUE, sampledAt = Long.MIN_VALUE, lastTime = Long.MIN_VALUE;

    public Settings settings() { return settings; }
    public void configure(Settings value) {
        java.util.Objects.requireNonNull(value);
        if (settings.equals(value)) return;
        settings = value; reset();
    }
    public void reset() {
        chips = List.of(); epoch = sampledAt = lastTime = Long.MIN_VALUE;
    }
    /** Values are supplied by the host; unknown readings use -1 and never invent a healthy value. */
    public void update(int fps, int pingMillis, int hour, int minute, long nowMs) {
        clock(nowMs);
        if (sampledAt != Long.MIN_VALUE && nowMs - sampledAt < 250) return;
        sampledAt = nowMs;
        var next = new ArrayList<Chip>(3);
        if (settings.fps()) next.add(new Chip(number(fps,9999), "8888", "FPS",
                fps < 0 ? Health.NEUTRAL : fps >= 60 ? Health.GOOD : fps >= 30 ? Health.WARN : Health.BAD));
        if (settings.ping()) next.add(new Chip(number(pingMillis,9999), "8888", "ms",
                pingMillis < 0 ? Health.NEUTRAL : pingMillis <= 80 ? Health.GOOD : pingMillis <= 180 ? Health.WARN : Health.BAD));
        if (settings.clock()) next.add(new Chip(hour >= 0 && hour < 24 && minute >= 0 && minute < 60
                ? String.format(Locale.ROOT,"%02d:%02d",hour,minute) : "--:--", "88:88", "", Health.NEUTRAL));
        chips = List.copyOf(next);
    }
    public List<Chip> chips() { return chips; }
    public Page page(long nowMs, boolean hasTrack, boolean reducedMotion) {
        clock(nowMs);
        if (chips.isEmpty()) return Page.NOW_PLAYING;
        if (!hasTrack || !settings.nowPlaying()) return Page.STATUS;
        if (!settings.rotate() || reducedMotion) return Page.NOW_PLAYING;
        long period = settings.statusMs() + settings.musicMs();
        return (nowMs - epoch) % period < settings.statusMs() ? Page.STATUS : Page.NOW_PLAYING;
    }
    private void clock(long nowMs) {
        if (lastTime != Long.MIN_VALUE && nowMs < lastTime) {
            epoch = nowMs; sampledAt = Long.MIN_VALUE;
        }
        if (epoch == Long.MIN_VALUE) epoch = nowMs;
        lastTime = nowMs;
    }
    private static String number(int value, int limit) { return value < 0 ? "--" : Integer.toString(Math.min(limit,value)); }
}
