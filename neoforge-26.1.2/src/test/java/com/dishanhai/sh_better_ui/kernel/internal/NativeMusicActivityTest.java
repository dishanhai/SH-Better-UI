// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.*;
import com.dishanhai.sh_better_ui.config.MusicOverlaySettings;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.live.*;
import org.junit.jupiter.api.*;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import java.util.*;
import com.dishanhai.sh_better_ui.kernel.internal.music.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

final class NativeMusicActivityTest {
    private final AtomicLong now = new AtomicLong();
    private final AtomicReference<MusicService.Frame> frame = new AtomicReference<>(sample("one", MusicSnapshot.PlaybackStatus.PLAYING));
    private final AtomicInteger opened = new AtomicInteger();
    private ModuleContext other;
    private OnlineMusicService.BackgroundControls transport;
    private OnlineMusicService.BackgroundCommand dispatched;
    private final List<SessionMusicOperations.Snapshot> operations = new ArrayList<>();
    private SessionMusicOperations registry;
    private final AtomicReference<MusicOperationNavigation.Target> openedOperation = new AtomicReference<>();
    private final class TestOnline implements OnlineMusicService, MusicOperationSource {
        public List<SessionMusicOperations.Snapshot> operationsSnapshot() { return List.copyOf(operations); }
        public Optional<MusicOperationNavigation.Target> operationTarget(long id) {
            return registry == null ? Optional.empty() : MusicOperationNavigation.target(registry, id);
        }
        public Optional<BackgroundControls> backgroundControls(BackgroundTarget target) {
            return transport != null && transport.target().equals(target) ? Optional.of(transport) : Optional.empty();
        }
        public boolean controlBackground(BackgroundTarget target, BackgroundCommand command, double fraction) {
            if (backgroundControls(target).isEmpty()) return false;
            dispatched = command; return true;
        }
        public OnlineMusicPlayer open() { throw new AssertionError("Activity must not create players"); }
        public OnlineMusicPlayer open(List<OnlineMusicSource> sources) { return open(); }
        public OnlineMusicView view() { throw new AssertionError("Activity must not mount music views"); }
        public OnlineMusicView view(OnlineMusicPlayer player) { return view(); }
    }
    private final OnlineMusicService online = new TestOnline();
    private KernelHost host;
    private LiveSurfaceRuntime live;
    private NativeMusicActivity music;
    @BeforeEach void start() {
        host = new KernelHost(NativePort.PLATFORM, new EnginePublication(), now::get, Runnable::run, SchedulerBudget.DEFAULT);
        live = new LiveSurfaceRuntime(host);
        music = new NativeMusicActivity(false, frame::get, now::get, () -> live.policy().notifications(),
                () -> live.diagnostics().activities() < live.policy().maxActivities(),
                () -> live.diagnostics().notifications() < live.policy().maxNotifications(), opened::incrementAndGet, openedOperation::set);
        var platforms = Set.of(NativePort.PLATFORM);
        host.start(List.of(BuiltinService.candidate(platforms, OnlineMusicService.KEY, online), BuiltinService.candidate(platforms, ActivityService.KEY, live, ActivityService.CAPABILITY),
                BuiltinService.candidate(platforms, NotificationService.KEY, live, NotificationService.CAPABILITY), music.candidate(),
                new ModuleDiscovery.Candidate("other", "test", new UIModule() {
                    public ModuleDescriptor descriptor() { return ModuleDescriptor.builder().id(UiId.parse("other:test"))
                            .version("1.0.0").platforms(platforms).build(); }
                    public void register(ModuleContext context) {}
                    public void start(ModuleContext context) { other = context; }
                    public void stop(ModuleContext context) {}
                }, Set.of(), false)));
        assertNotNull(other);
    }
    @AfterEach void stop() { host.close(); }
    void tick(long millis) {
        now.set(millis * 1_000_000L);
        host.runFrame(phase -> { if (phase == UIPhase.MEASURE) { music.advance(); live.advance(false, true); } });
    }
    void enable() { music.settings(MusicOverlaySettings.DEFAULT.withIsland(true)); tick(0); }
    static MusicService.Frame sample(String track, MusicSnapshot.PlaybackStatus status) {
        var session = new MusicSnapshot.Session("session", MusicSnapshot.Track.of(track, "Title " + track, "Artist", "Album"),
                status, OptionalLong.of(1000), OptionalLong.of(10000), OptionalDouble.of(1), 0, null, Set.of(MusicSnapshot.Capability.METADATA));
        return new MusicService.Frame(new MusicSnapshot("sh_better_ui:online_music", MusicSnapshot.State.AVAILABLE, session),
                new MusicService.Lyrics(MusicService.LyricsState.AVAILABLE,
                        new LyricTimeline(List.of(new LyricLine("Sung lyric", "", 0, 3000, List.of(), "")))), 1);
    }
    @Test void disabledByDefaultAndOptInPublishesRealContent() {
        tick(0); assertNull(live.plan().primary()); enable();
        var view = live.plan().primary(); assertNotNull(view);
        assertEquals(NativeMusicActivity.ID, view.id());
        assertEquals("Title one", view.content().title().value());
        assertEquals("Sung lyric", view.content().detail().value());
        assertEquals(.1, view.content().progress(), .00001);
        assertFalse(view.content().seekable()); assertTrue(view.content().rhythm().isEmpty());
    }
    @Test void pauseExpiresAfterThirtySecondsAndResumeRestores() {
        enable(); frame.set(sample("one", MusicSnapshot.PlaybackStatus.PAUSED)); tick(1000);
        tick(30999); assertNotNull(live.plan().primary());
        tick(31000); assertNull(live.plan().primary());
        frame.set(sample("one", MusicSnapshot.PlaybackStatus.PLAYING)); tick(32000); assertNotNull(live.plan().primary());
    }
    @Test void switchTrackReplacesInstanceAndStopClears() {
        enable(); long instance = live.plan().primary().instance();
        frame.set(sample("two", MusicSnapshot.PlaybackStatus.PLAYING)); tick(1);
        assertNotEquals(instance, live.plan().primary().instance());
        frame.set(sample("two", MusicSnapshot.PlaybackStatus.STOPPED)); tick(2); assertNull(live.plan().primary());
    }
    @Test void explicitDismissDoesNotReappearEveryFrame() {
        enable(); live.dismiss(live.plan().primary()); tick(1); tick(2); assertNull(live.plan().primary());
        frame.set(sample("two", MusicSnapshot.PlaybackStatus.PLAYING)); tick(3); assertNotNull(live.plan().primary());
    }
    @Test void disablingReleasesActivityAndShutdownCannotRepublish() {
        enable(); music.settings(music.settings().withIsland(false)); tick(1); assertNull(live.plan().primary());
        host.close(); music.advance(); assertFalse(live.hasResources());
    }
    @Test void metadataOnlySystemSourceHasNoOnlineControlsAndLyricGapsShowArtist() {
        var online = sample("one", MusicSnapshot.PlaybackStatus.PAUSED);
        var system = new MusicService.Frame(new MusicSnapshot("mpris", MusicSnapshot.State.AVAILABLE, online.snapshot().session()),
                new MusicService.Lyrics(MusicService.LyricsState.UNAVAILABLE, LyricTimeline.empty()), 1);
        var content = NativeMusicActivity.content(system, 0);
        assertTrue(content.controls().isEmpty()); assertEquals("Artist", content.detail().value());
    }
    @Test void openActionUsesCurrentOnlineIdentityAndRejectsTrackChangedBeforeDispatch() {
        enable();
        assertTrue(live.dispatch(live.plan().primary(), new ActivityService.Action(NativeMusicActivity.OPEN, -1)));
        tick(1); assertEquals(1, opened.get());
        assertTrue(live.dispatch(live.plan().primary(), new ActivityService.Action(NativeMusicActivity.OPEN, -1)));
        frame.set(sample("two", MusicSnapshot.PlaybackStatus.PLAYING));
        tick(2); assertEquals(1, opened.get());
    }
    @Test void taskNotificationsUseUnifiedQueueAndStopReleasesBoth() {
        music.settings(MusicOverlaySettings.DEFAULT.withIsland(true).withTaskNotices(true));
        var playing = sample("one", MusicSnapshot.PlaybackStatus.PLAYING);
        frame.set(new MusicService.Frame(playing.snapshot(), new MusicService.Lyrics(MusicService.LyricsState.LOADING, LyricTimeline.empty()), 1));
        tick(0);
        assertTrue(live.hasResources());
        assertNotNull(live.plan().primary());
        assertTrue(live.plan().waiting().contains(UiId.parse("sh_better_ui:music/notice/lyrics"))
                || live.plan().primary().id().equals(UiId.parse("sh_better_ui:music/notice/lyrics")));
        frame.set(sample("one", MusicSnapshot.PlaybackStatus.STOPPED)); tick(1);
        assertFalse(live.hasResources());
    }
    @Test void terminalOnlineFailureReleasesActivityButPublishesOneNoticeWithoutSession() {
        music.settings(MusicOverlaySettings.DEFAULT.withIsland(true).withTaskNotices(true));
        frame.set(sample("one", MusicSnapshot.PlaybackStatus.BUFFERING)); tick(0);
        frame.set(new MusicService.Frame(new MusicSnapshot("sh_better_ui:online_music", MusicSnapshot.State.TEMPORARILY_UNAVAILABLE, null),
                new MusicService.Lyrics(MusicService.LyricsState.NO_TRACK, LyricTimeline.empty()), 1));
        tick(50);
        assertEquals(0, live.diagnostics().activities());
        assertEquals(1, live.diagnostics().notifications());
        assertEquals("sh_better_ui.music_overlay.task.playback_failed", live.plan().primary().content().title().value());
        assertTrue(live.plan().primary().content().controls().isEmpty());
        long instance=live.plan().primary().instance(); tick(100);
        assertEquals(instance,live.plan().primary().instance());
        music.settings(music.settings().withIsland(false));tick(101);assertFalse(live.hasResources());
        host.close();music.advance();assertFalse(live.hasResources());
    }
    @Test void bufferingCompletionReplacesNoticeDespiteFastTransitionAndDisabledNoticesStayQuiet() {
        music.settings(MusicOverlaySettings.DEFAULT.withIsland(true).withTaskNotices(true));
        frame.set(sample("one", MusicSnapshot.PlaybackStatus.BUFFERING)); tick(0);
        frame.set(sample("one", MusicSnapshot.PlaybackStatus.PLAYING)); tick(50);
        assertEquals(1,live.diagnostics().notifications());
        assertTrue(live.plan().waiting().contains(UiId.parse("sh_better_ui:music/notice/playback"))
                || live.plan().primary().id().equals(UiId.parse("sh_better_ui:music/notice/playback")));
        music.settings(music.settings().withTaskNotices(false));tick(51);
        assertEquals(0,live.diagnostics().notifications());assertEquals(1,live.diagnostics().activities());
    }
    private void capacity(int activities, int notifications) {
        var p = live.policy();
        live.policy(new LiveSurfaceRuntime.Policy(p.enabled(), p.compact(), p.expanded(), p.split(), p.notifications(),
                p.deduplicate(), p.quiet(), activities, notifications, p.defaultDurationNanos(), p.priorities()));
    }
    @Test void fullActivityCapacityDefersAndRetriesWithoutClearingExistingMusic() {
        capacity(1, 1);
        var occupying = live.publish(other, other.scope(), new ActivityService.Request(UiId.parse("other:busy"),
                ActivityService.Content.label(ActivityService.Text.literal("Busy")), ActivityService.Priority.NORMAL,
                java.time.Duration.ZERO, true, null));
        enable(); assertEquals(1, live.diagnostics().activities());
        assertEquals(UiId.parse("other:busy"), live.plan().primary().id());
        occupying.cancel(); tick(1);
        assertEquals(NativeMusicActivity.ID, live.plan().primary().id());
        long instance = live.plan().primary().instance();
        tick(2); tick(3);
        assertEquals(instance, live.plan().primary().instance());
        assertEquals(1, live.diagnostics().activities());
    }
    @Test void fullNotificationCapacitySkipsOptionalNoticeWithoutBreakingMusic() {
        capacity(2, 1);
        live.publish(other, other.scope(), new NotificationService.Request(UiId.parse("other:notice"),
                ActivityService.Content.label(ActivityService.Text.literal("Notice")), ActivityService.Priority.LOW,
                java.time.Duration.ofSeconds(5), null, NotificationService.Merge.KEEP, null));
        music.settings(MusicOverlaySettings.DEFAULT.withIsland(true).withTaskNotices(true));
        var playing = sample("one", MusicSnapshot.PlaybackStatus.PLAYING);
        frame.set(new MusicService.Frame(playing.snapshot(), new MusicService.Lyrics(MusicService.LyricsState.LOADING, LyricTimeline.empty()), 1));
        tick(0); tick(1);
        assertEquals(1, live.diagnostics().activities());
        assertEquals(1, live.diagnostics().notifications());
        assertEquals(NativeMusicActivity.ID, live.plan().primary().id());
    }
    @Test void availableTransportAddsOnlyRealControlsAndSeek() {
        transport = new OnlineMusicService.BackgroundControls(new OnlineMusicService.BackgroundTarget("session", "one"),
                true, false, true, true, true);
        enable();
        var snapshot = live.plan().primary();
        assertEquals(4, snapshot.content().controls().size());
        assertTrue(snapshot.content().seekable());
        assertTrue(live.dispatch(snapshot, new ActivityService.Action(UiId.parse("sh_better_ui:music/pause"), -1)));
        tick(1); assertEquals(OnlineMusicService.BackgroundCommand.PAUSE, dispatched);
        dispatched = null;
        assertTrue(live.dispatch(live.plan().primary(), new ActivityService.Action(ActivityService.SEEK, .5)));
        frame.set(sample("two", MusicSnapshot.PlaybackStatus.PLAYING)); tick(2);
        assertNull(dispatched, "Queued old-track transport must not reach the service");
    }
    @Test void contentBoundsUserMetadataWithoutSplittingSurrogatePair() {
        var original = sample("one", MusicSnapshot.PlaybackStatus.PAUSED);
        var s = original.snapshot().session();
        var session = new MusicSnapshot.Session(s.id(), MusicSnapshot.Track.of("one", "x".repeat(511) + "😀", "a", ""),
                s.status(), s.positionMs(), s.durationMs(), s.rate(), 0, null, s.capabilities());
        var model = NativeMusicActivity.content(new MusicService.Frame(new MusicSnapshot("mpris", MusicSnapshot.State.AVAILABLE, session), original.lyrics(), 1), 0);
        assertEquals(511, model.title().value().length());
    }
    @Test void viewTaskDispatchIsExplicitAndRejectsReattachedTarget() {
        frame.set(new MusicService.Frame(new MusicSnapshot("system", MusicSnapshot.State.NO_SESSION, null),
                new MusicService.Lyrics(MusicService.LyricsState.NO_TRACK, LyricTimeline.empty()), 0));
        music.settings(MusicOverlaySettings.DEFAULT.withIsland(true).withTaskNotices(true));
        var owner = new java.util.concurrent.CompletableFuture<List<OnlineTrack>>();
        var player = (OnlineMusicPlayer) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{OnlineMusicPlayer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("search")) return owner;
                    throw new AssertionError(method.getName());
                });
        registry = MusicOperationFixtures.registry(player);
        try {
            var job = registry.startSearch("qq", "private", 1);
            job.detach(); operations.add(job.snapshot()); tick(0);
            var snapshot = live.plan().primary();
            assertEquals(List.of(NativeMusicActivity.VIEW_TASK),
                    snapshot.content().controls().stream().map(ActivityService.Control::id).toList());
            assertNull(openedOperation.get());
            assertFalse(live.isInteractivePrimary(snapshot), "A not-yet-painted action is not visible");
            live.markPainted(snapshot);
            assertTrue(live.isInteractivePrimary(snapshot));
            var viewTask = new ActivityService.Action(NativeMusicActivity.VIEW_TASK, -1);
            assertTrue(live.dispatch(snapshot, viewTask));
            tick(1);
            assertEquals(job.id(), openedOperation.get().id());
            assertFalse(owner.isDone());
            openedOperation.set(null);
            assertTrue(live.dispatch(snapshot, viewTask));
            job.attach();
            tick(2);
            assertNull(openedOperation.get());
            live.advance(false, false);
            assertFalse(live.isInteractivePrimary(snapshot), "The passive HUD cannot activate a task");
            live.advance(false, true);
            assertTrue(live.isInteractivePrimary(snapshot));
            now.set(8_000_000_000L);
            assertFalse(live.isInteractivePrimary(snapshot), "Expiry applies even before the next advance");
        } finally {
            registry.close();
        }
    }

    @Test void detachedReadNotifiesWithoutTrackAndDoesNotReplayWhenDisabled() {
        frame.set(new MusicService.Frame(new MusicSnapshot("system",MusicSnapshot.State.NO_SESSION,null),
                new MusicService.Lyrics(MusicService.LyricsState.NO_TRACK,LyricTimeline.empty()),0));
        music.settings(MusicOverlaySettings.DEFAULT.withIsland(true).withTaskNotices(true));
        operations.add(new SessionMusicOperations.Snapshot(99,SessionMusicOperations.Kind.SEARCH,"private",SessionMusicOperations.State.SUCCEEDED,true,SessionMusicOperations.Error.NONE));
        tick(0);
        assertEquals(0,live.diagnostics().activities());assertEquals(1,live.diagnostics().notifications());
        assertEquals("sh_better_ui.music_overlay.task.operation_search_succeeded",live.plan().primary().content().title().value());
        long instance=live.plan().primary().instance();tick(1);assertEquals(instance,live.plan().primary().instance());
        music.settings(music.settings().withIsland(false));tick(2);assertFalse(live.hasResources());
        music.settings(music.settings().withIsland(true));tick(3);assertFalse(live.hasResources());
    }
    @Test void detachedTerminalWaitsForNotificationCapacity() {
        frame.set(new MusicService.Frame(new MusicSnapshot("system",MusicSnapshot.State.NO_SESSION,null),
                new MusicService.Lyrics(MusicService.LyricsState.NO_TRACK,LyricTimeline.empty()),0));
        capacity(2,1);
        var occupying=live.publish(other,other.scope(),new NotificationService.Request(UiId.parse("other:busy"),
                ActivityService.Content.label(ActivityService.Text.literal("Busy")),ActivityService.Priority.LOW,
                java.time.Duration.ofSeconds(5),null,NotificationService.Merge.KEEP,null));
        music.settings(MusicOverlaySettings.DEFAULT.withIsland(true).withTaskNotices(true));
        operations.add(new SessionMusicOperations.Snapshot(100,SessionMusicOperations.Kind.LIKED,"private",SessionMusicOperations.State.FAILED,true,SessionMusicOperations.Error.LOGIN_REQUIRED));
        tick(0);assertEquals(UiId.parse("other:busy"),live.plan().primary().id());
        occupying.dismiss();tick(1);tick(2);
        assertEquals("sh_better_ui.music_overlay.task.operation_liked_login_required",live.plan().primary().content().title().value());
    }

    @Test void unrelatedPlaybackNoticeCannotCoalesceDetachedCompletionWithinOneSecond() {
        music.settings(MusicOverlaySettings.DEFAULT.withIsland(true).withTaskNotices(true));
        frame.set(sample("one",MusicSnapshot.PlaybackStatus.BUFFERING));tick(0);
        operations.add(new SessionMusicOperations.Snapshot(101,SessionMusicOperations.Kind.PLAYLISTS,"private",SessionMusicOperations.State.SUCCEEDED,true,SessionMusicOperations.Error.NONE));
        tick(50);
        var id=UiId.parse("sh_better_ui:music/notice/operation/101");
        assertTrue(live.plan().waiting().contains(id)||live.plan().primary().id().equals(id));
        int count=live.diagnostics().notifications();tick(51);assertEquals(count,live.diagnostics().notifications());
        music.settings(music.settings().withTaskNotices(false));tick(52);
        assertEquals(SessionMusicOperations.State.SUCCEEDED,operations.get(0).state());
        music.settings(music.settings().withTaskNotices(true));tick(53);
        assertFalse(live.plan().waiting().contains(id));assertNotEquals(id,live.plan().primary().id());
    }

}
