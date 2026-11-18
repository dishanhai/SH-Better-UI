// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicIslandCapsuleState;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicIslandTaskAdapter;
import com.dishanhai.sh_better_ui.client.music.*;
import com.dishanhai.sh_better_ui.config.MusicOverlaySettings;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.live.*;
import com.google.gson.Gson;
import java.time.Duration;
import java.util.*;
import java.util.function.*;

/** Observes music without owning playback. The normal activity arbiter owns all presentation. */
public final class NativeMusicActivity implements UIModule {
    public static final UiId ID = UiId.parse("sh_better_ui:music/island");
    public static final UiId OPEN = UiId.parse("sh_better_ui:music/open");
    private static final String ONLINE = "sh_better_ui:online_music";
    private final Supplier<MusicService.Frame> frames;
    private final LongSupplier clock;
    private final Runnable openMusic;
    private final BooleanSupplier notificationsEnabled;
    private final boolean persistent;
    private final BooleanSupplier activityAdmission, notificationAdmission;
    private final MusicIslandCapsuleState visibility = new MusicIslandCapsuleState();
    private final MusicIslandTaskAdapter tasks = new MusicIslandTaskAdapter();
    private final Map<String, NotificationService.Handle> notices = new HashMap<>();
    private ModuleContext context;
    private ActivityService activities;
    private NotificationService notifications;
    private ActivityService.Handle handle;
    private ActivityService.Content published;
    private String identity;
    private long lastNotice = Long.MIN_VALUE;
    private boolean expanded;

    public NativeMusicActivity(boolean persistent, BooleanSupplier notificationsEnabled, Runnable openMusic) {
        this(persistent, notificationsEnabled, () -> true, () -> true, openMusic);
    }
    public NativeMusicActivity(boolean persistent, BooleanSupplier notificationsEnabled,
                               BooleanSupplier activityAdmission, BooleanSupplier notificationAdmission, Runnable openMusic) {
        this(persistent, () -> ShBetterUiApi.music().getFrame(), System::nanoTime, notificationsEnabled,
                activityAdmission, notificationAdmission, openMusic);
    }
    NativeMusicActivity(boolean persistent, Supplier<MusicService.Frame> frames, LongSupplier clock,
                        BooleanSupplier notificationsEnabled, Runnable openMusic) {
        this(persistent, frames, clock, notificationsEnabled, () -> true, () -> true, openMusic);
    }
    NativeMusicActivity(boolean persistent, Supplier<MusicService.Frame> frames, LongSupplier clock,
                        BooleanSupplier notificationsEnabled, BooleanSupplier activityAdmission,
                        BooleanSupplier notificationAdmission, Runnable openMusic) {
        this.activityAdmission = activityAdmission; this.notificationAdmission = notificationAdmission;
        this.persistent = persistent; this.frames = frames; this.clock = clock;
        this.notificationsEnabled = notificationsEnabled; this.openMusic = openMusic;
    }
    public ModuleDiscovery.Candidate candidate() {
        return new ModuleDiscovery.Candidate("sh_better_ui", getClass().getName(), this,
                Set.of(UIPermission.MODIFY_GLOBAL_CONFIG), false, true);
    }
    @Override public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(UiId.parse("sh_better_ui:music_activity")).version("1.0.0")
                .platforms(Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2))
                .initializationPhase(ModuleDescriptor.InitializationPhase.EXTENSION)
                .requiredCapability(ActivityService.CAPABILITY, VersionRange.ANY)
                .requiredCapability(NotificationService.CAPABILITY, VersionRange.ANY)
                .requestPermission(UIPermission.MODIFY_GLOBAL_CONFIG).build();
    }
    @Override public void register(ModuleContext context) {}
    @Override public void start(ModuleContext context) {
        this.context = context;
        activities = context.engine().require(ActivityService.KEY);
        notifications = context.engine().require(NotificationService.KEY);
        var configs = context.engine().configs();
        configs.define(MusicOverlaySettings.KEY);
        if (persistent) {
            var gson = new Gson();
            context.scope().track(configs.persist(MusicOverlaySettings.KEY,
                    ConfigCodec.of(gson::toJson, json -> gson.fromJson(json, MusicOverlaySettings.class))));
        }
    }
    public boolean available() { return context != null && context.scope().isOpen(); }

    public MusicOverlaySettings settings() {
        if (context == null) throw new IllegalStateException("Music activity has not started");
        return context.engine().configs().get(MusicOverlaySettings.KEY);
    }
    public void settings(MusicOverlaySettings value) {
        if (context == null) throw new IllegalStateException("Music activity has not started");
        var configs = context.engine().configs();
        configs.beginTransaction().set(configs.userGlobal(), MusicOverlaySettings.KEY, value).commit();
    }
    @Override public void stop(ModuleContext ignored) { clear(); context = null; }

    /** UI-thread MEASURE, before LiveSurfaceRuntime.advance. No network or playback commands. */
    public void advance() {
        if (context == null || !context.scope().isOpen()) return;
        var settings = context.engine().configs().get(MusicOverlaySettings.KEY);
        if (!settings.islandEnabled()) { clear(); return; }
        var frame = frames.get();
        long now = clock.getAsLong();
        String next = identity(frame);
        if (!Objects.equals(identity, next)) {
            endActivity(); clearNotices(); identity = next;
        }
        var session = frame.snapshot().session();
        visibility.observe(next, session != null && session.status() == MusicSnapshot.PlaybackStatus.PLAYING, now / 1_000_000);
        if (next == null || !visibility.active()) { endActivity(); clearNotices(); return; }
        var content = content(frame, now);
        // Respect explicit dismissal until a new track/source or disable/re-enable cycle.
        if (handle == null && activityAdmission.getAsBoolean()) {
            String issuedIdentity = next;
            handle = activities.publish(context, context.scope(), new ActivityService.Request(ID, content,
                    ActivityService.Priority.NORMAL, Duration.ZERO, true, action -> {
                var current = frames.get();
                if (action.id().equals(OPEN) && issuedIdentity.equals(identity(current))
                        && ONLINE.equals(current.snapshot().providerId())) openMusic.run();
            }));
            published = content; expanded = settings.expanded();
            handle.requestPresentation(expanded ? SurfacePresentation.EXPANDED : SurfacePresentation.COMPACT);
        } else if (handle != null && !handle.snapshot().lifecycle().terminal()) {
            if (!content.equals(published)) { handle.update(content); published = content; }
            if (expanded != settings.expanded()) {
                expanded = settings.expanded();
                handle.requestPresentation(expanded ? SurfacePresentation.EXPANDED : SurfacePresentation.COMPACT);
            }
        }
        var updates = tasks.observe(frame);
        if (!settings.taskNotices() || !notificationsEnabled.getAsBoolean()) { clearNotices(); return; }
        for (var update : updates) {
            var previous = notices.get(update.id());
            if (update.cancel()) {
                notices.remove(update.id());
                if (previous != null && !previous.snapshot().lifecycle().terminal()) previous.dismiss();
                continue;
            }
            if (!notificationAdmission.getAsBoolean()) continue;
            // Coalesce bursty status changes; the optional notice must not flood the shared queue.
            if (lastNotice != Long.MIN_VALUE && now - lastNotice < 1_000_000_000L) continue;
            var id = UiId.parse("sh_better_ui:music/notice/" + update.id().substring(update.id().indexOf(':') + 1));
            var notice = new ActivityService.Content(ActivityService.Text.key("sh_better_ui.music_overlay.task." + update.messageKey()),
                    ActivityService.Text.literal(bounded(update.detail())), null, -1, false, List.of(), List.of());
            if (previous != null && !previous.snapshot().lifecycle().terminal()) previous.dismiss();
            notices.put(update.id(), notifications.publish(context, context.scope(), new NotificationService.Request(id,
                    notice, ActivityService.Priority.LOW, Duration.ofMillis(update.ttlMs()), id, NotificationService.Merge.REPLACE, null)));
            lastNotice = now;
        }
    }
    static String identity(MusicService.Frame frame) {
        var snapshot = frame.snapshot(); var session = snapshot.session();
        if (snapshot.state() != MusicSnapshot.State.AVAILABLE || session == null || session.track() == null
                || session.status() == MusicSnapshot.PlaybackStatus.STOPPED || session.status() == MusicSnapshot.PlaybackStatus.UNKNOWN) return null;
        return snapshot.providerId() + "\0" + session.id() + "\0" + session.track().key();
    }
    static ActivityService.Content content(MusicService.Frame frame, long now) {
        var session = frame.snapshot().session(); var track = session.track();
        long position = session.positionAt(now);
        String detail = track.artist().orElse("");
        if (position >= 0 && frame.lyrics().state() == MusicService.LyricsState.AVAILABLE) {
            var line = frame.lyrics().timeline().lineAt(position);
            if (line != null && position < line.endMs() && !line.text().isBlank()) detail = line.text();
        }
        double progress = position < 0 || session.durationMs().isEmpty() ? -1
                : MusicIslandCapsuleState.progress(position, session.durationMs().getAsLong());
        var title = track.title().filter(s -> !s.isBlank()).map(s -> ActivityService.Text.literal(bounded(s)))
                .orElseGet(() -> ActivityService.Text.key("sh_better_ui.music.unknown_title"));
        var controls = ONLINE.equals(frame.snapshot().providerId())
                ? List.of(new ActivityService.Control(OPEN, ActivityService.Text.key("sh_better_ui.panel.developer.gallery.sample.online_music")))
                : List.<ActivityService.Control>of();
        return new ActivityService.Content(title, ActivityService.Text.literal(bounded(detail)), null, progress, false, controls, List.of());
    }
    private static String bounded(String value) {
        if (value.length() <= 512) return value;
        int end = Character.isHighSurrogate(value.charAt(511)) ? 511 : 512;
        return value.substring(0, end);
    }
    private void endActivity() {
        if (handle != null && !handle.snapshot().lifecycle().terminal()) handle.cancel();
        handle = null; published = null;
    }
    private void clearNotices() {
        for (var notice : notices.values()) if (!notice.snapshot().lifecycle().terminal()) notice.dismiss();
        notices.clear();
    }
    private void clear() {
        endActivity(); clearNotices(); identity = null; visibility.reset(); tasks.reset(); lastNotice = Long.MIN_VALUE;
    }
}
