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
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicService;
import java.time.Duration;
import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationSource;
import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationNotices;
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
    private final MusicOperationNotices operationTasks = new MusicOperationNotices();
    private final Map<String, MusicOperationNotices.Update> pendingOperations = new LinkedHashMap<>();
    private final Map<String, NotificationService.Handle> operationNotices = new HashMap<>();
    private ModuleContext context;
    private ActivityService activities;
    private NotificationService notifications;
    private OnlineMusicService online;
    private LiveSurfaceService liveSurfaces;
    private LiveSurfaceService.Reservation hudReservation;
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
        online = context.engine().find(OnlineMusicService.KEY).orElse(null);
        liveSurfaces = context.engine().find(LiveSurfaceService.KEY).orElse(null);
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
    /** Reservation is independent of the island publication and follows the actual HUD bounds. */
    boolean hudBounds(com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds bounds) {
        if (bounds == null || !available() || liveSurfaces == null) {
            if (hudReservation != null) hudReservation.close(); hudReservation = null; return false;
        }
        var rect = new SurfacePlacement.Rect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        if (hudReservation == null) {
            if (liveSurfaces instanceof LiveSurfaceRuntime runtime && runtime.occupied().size() >= 128) return false;
            hudReservation = liveSurfaces.reserve(context, context.scope(), UiId.parse("sh_better_ui:music/hud"), rect);
        } else hudReservation.update(rect);
        return true;
    }
    @Override public void stop(ModuleContext ignored) { hudBounds(null); clear(); clearOperationNotices(); operationTasks.reset(); context = null; }

    /** UI-thread MEASURE, before LiveSurfaceRuntime.advance. No network or playback commands. */
    public void advance() {
        if (context == null || !context.scope().isOpen()) return;
        var settings = context.engine().configs().get(MusicOverlaySettings.KEY);
        publishOperationNotices(settings);
        if (!settings.islandEnabled()) { clear(); return; }
        var frame = frames.get();
        long now = clock.getAsLong();
        String next = identity(frame);
        if (!Objects.equals(identity, next)) {
            endActivity(); clearNotices(); identity = next;
        }
        publishNotices(frame, settings, now);
        var session = frame.snapshot().session();
        visibility.observe(next, session != null && session.status() == MusicSnapshot.PlaybackStatus.PLAYING, now / 1_000_000);
        if (next == null || !visibility.active()) { endActivity(); return; }
        var content = withControls(content(frame, now), frame);
        // Respect explicit dismissal until a new track/source or disable/re-enable cycle.
        if (handle == null && activityAdmission.getAsBoolean()) {
            String issuedIdentity = next;
            handle = activities.publish(context, context.scope(), new ActivityService.Request(ID, content,
                    ActivityService.Priority.NORMAL, Duration.ZERO, true, action -> {
                var current = frames.get();
                if (!issuedIdentity.equals(identity(current)) || !ONLINE.equals(current.snapshot().providerId())) return;
                if (action.id().equals(OPEN)) { openMusic.run(); return; }
                if (online == null) return;
                var command = action.id().equals(ActivityService.SEEK) ? OnlineMusicService.BackgroundCommand.SEEK : command(action.id());
                if (command != null) online.controlBackground(target(current), command, action.fraction());
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
    }
    private void clearOperationNotices() {
        for(var notice:operationNotices.values())if(!notice.snapshot().lifecycle().terminal())notice.dismiss();
        operationNotices.clear(); pendingOperations.clear();
    }
    private void publishOperationNotices(MusicOverlaySettings settings) {
        boolean enabled=settings.islandEnabled() && settings.taskNotices() && notificationsEnabled.getAsBoolean();
        var snapshots=online instanceof MusicOperationSource source ? source.operationsSnapshot() : java.util.List.<com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.Snapshot>of();
        var updates=operationTasks.observe(snapshots,enabled);
        if(!enabled){clearOperationNotices();return;}
        operationNotices.values().removeIf(handle->handle.snapshot().lifecycle().terminal());
        for(var update:updates) {
            var previous=operationNotices.remove(update.id());
            if(previous!=null&&!previous.snapshot().lifecycle().terminal())previous.dismiss();
            pendingOperations.remove(update.id());
            if(!update.cancel())pendingOperations.put(update.id(),update);
        }
        for(var update:List.copyOf(pendingOperations.values())) {
            if(!notificationAdmission.getAsBoolean())break;
            pendingOperations.remove(update.id());
            var id=UiId.parse("sh_better_ui:music/notice/"+update.id().substring("music:".length()));
            var content=new ActivityService.Content(ActivityService.Text.key("sh_better_ui.music_overlay.task."+update.key()),
                    ActivityService.Text.literal(""),null,-1,false,List.of(),List.of());
            operationNotices.put(update.id(),notifications.publish(context,context.scope(),new NotificationService.Request(id,
                    content,update.error()?ActivityService.Priority.NORMAL:ActivityService.Priority.LOW,
                    Duration.ofMillis(update.ttlMs()),id,NotificationService.Merge.REPLACE,null)));
        }
    }
    private void publishNotices(MusicService.Frame frame, MusicOverlaySettings settings, long now) {
        var updates = tasks.observe(frame);
        if (!settings.taskNotices() || !notificationsEnabled.getAsBoolean()) { clearNotices(); return; }
        var replaced = new HashSet<String>();
        for (var update : updates) {
            var previous = notices.get(update.id());
            if (update.cancel()) {
                notices.remove(update.id());
                if (previous != null) replaced.add(update.id());
                if (previous != null && !previous.snapshot().lifecycle().terminal()) previous.dismiss();
                continue;
            }
            if (!notificationAdmission.getAsBoolean()) continue;
            // Coalesce bursty status changes; the optional notice must not flood the shared queue.
            if (previous == null && !replaced.contains(update.id()) && !update.messageKey().equals("playback_failed")
                    && lastNotice != Long.MIN_VALUE && now - lastNotice < 1_000_000_000L) continue;
            var id = UiId.parse("sh_better_ui:music/notice/" + update.id().substring(update.id().indexOf(':') + 1));
            var notice = new ActivityService.Content(ActivityService.Text.key("sh_better_ui.music_overlay.task." + update.messageKey()),
                    ActivityService.Text.literal(bounded(update.detail())), null, -1, false, List.of(), List.of());
            if (previous != null && !previous.snapshot().lifecycle().terminal()) previous.dismiss();
            notices.put(update.id(), notifications.publish(context, context.scope(), new NotificationService.Request(id,
                    notice, ActivityService.Priority.LOW, Duration.ofMillis(update.ttlMs()), id, NotificationService.Merge.REPLACE, null)));
            lastNotice = now;
        }
    }
    private static OnlineMusicService.BackgroundTarget target(MusicService.Frame frame) {
        var session = frame.snapshot().session();
        return new OnlineMusicService.BackgroundTarget(session.id(), session.track().key());
    }
    private static UiId commandId(OnlineMusicService.BackgroundCommand command) {
        return UiId.parse("sh_better_ui:music/" + command.name().toLowerCase(java.util.Locale.ROOT));
    }
    private static OnlineMusicService.BackgroundCommand command(UiId id) {
        for (var command : OnlineMusicService.BackgroundCommand.values()) if (commandId(command).equals(id)) return command;
        return null;
    }
    private ActivityService.Content withControls(ActivityService.Content content, MusicService.Frame frame) {
        if (online == null || !ONLINE.equals(frame.snapshot().providerId())) return content;
        var available = online.backgroundControls(target(frame)).orElse(null);
        if (available == null) return content;
        var controls = new ArrayList<>(content.controls());
        if (available.previous()) controls.add(control(OnlineMusicService.BackgroundCommand.PREVIOUS, "previous"));
        if (available.pause()) controls.add(control(OnlineMusicService.BackgroundCommand.PAUSE, "pause"));
        else if (available.resume()) controls.add(control(OnlineMusicService.BackgroundCommand.RESUME, "play"));
        if (available.next()) controls.add(control(OnlineMusicService.BackgroundCommand.NEXT, "next"));
        return new ActivityService.Content(content.title(), content.detail(), content.icon(), content.progress(),
                content.progress() >= 0 && available.seek(), controls, content.rhythm(), content.appearance());
    }
    private static ActivityService.Control control(OnlineMusicService.BackgroundCommand command, String label) {
        return new ActivityService.Control(commandId(command), ActivityService.Text.key("sh_better_ui.online_music." + label));
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
        return new ActivityService.Content(title, ActivityService.Text.literal(bounded(detail)), NativeMusicArtwork.icon(session.artwork()), progress, false, controls, List.of());
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
