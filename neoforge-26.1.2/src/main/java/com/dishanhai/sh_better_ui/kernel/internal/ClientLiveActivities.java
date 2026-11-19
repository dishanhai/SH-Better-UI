// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextLayout;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.live.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.dishanhai.sh_better_ui.kernel.internal.live.SurfaceTransition;
import java.time.Duration;
import java.util.List;

/** Native 26.x presentation for the shared, scope-owned local activity/notification runtime. */
public final class ClientLiveActivities implements AutoCloseable {
    private final LiveSurfaceRuntime runtime;
    private final KernelHost host;
    private final NativeActivityMotion motion = new NativeActivityMotion();
    private final NativeMusicArtwork artwork = new NativeMusicArtwork();
    private LiveSurfaceSettings settings;
    private SurfacePlacement.Result placement = SurfacePlacement.Result.HIDDEN;
    private NativeActivityScreen details;
    private boolean closed;
    private final MusicTypographyScope typography = new MusicTypographyScope();
    /** Identity ownership survives crossfade layers, without styling unrelated activities. */
    static final class MusicTypographyScope {
        private final java.util.IdentityHashMap<ActivityService.Content, Boolean> owned = new java.util.IdentityHashMap<>();
        void remember(ActivityService.Content content, com.dishanhai.sh_better_ui.kernel.api.UiId owner) {
            owned.put(content, com.dishanhai.sh_better_ui.kernel.api.UiId.parse("sh_better_ui:music_activity").equals(owner));
        }
        boolean music(ActivityService.Content content) { return Boolean.TRUE.equals(owned.get(content)); }
        void retain(java.util.Collection<ActivityService.Content> contents) {
            var keep = new java.util.IdentityHashMap<ActivityService.Content, Boolean>();
            for (var content : contents) if (owned.containsKey(content)) keep.put(content, owned.get(content));
            owned.clear(); owned.putAll(keep);
        }
    }

    ClientLiveActivities(KernelHost host, LiveSurfaceSettings settings) {
        this.host = host;
        runtime = new LiveSurfaceRuntime(host);
        settings(settings);
    }

    ActivityService activities() { return runtime; }
    NotificationService notifications() { return runtime; }
    LiveSurfaceService surfaces() { return runtime; }
    LiveSurfaceRuntime runtime() { return runtime; }

    public void settings(LiveSurfaceSettings next) {
        settings = java.util.Objects.requireNonNull(next);
        motion.configure(next.motion());
        var layout = next.layout();
        var notifications = next.notifications();
        // This first native host presents one surface at a time, not a misleading half-rendered split.
        runtime.policy(new LiveSurfaceRuntime.Policy(layout.enabled(), layout.compact(), layout.expanded(), false,
                notifications.enabled(), notifications.deduplicate(), notifications.quiet(), layout.maxActivities(),
                notifications.maxNotifications(), Duration.ofMillis(notifications.defaultDurationMs()).toNanos(),
                notifications.priorities()));
    }

    /** Call at MEASURE after the kernel has drained state mutations, including while the HUD is hidden. */
    public void advance() {
        if (closed) return;
        var client = Minecraft.getInstance();
        boolean dashboard = NativePort.screen(client) == details && details != null;
        runtime.advance(dashboard && details.interacting(), dashboard && !NativePort.hasOverlay(client));
        if (!dashboard && (NativePort.screen(client) != null || client.options.hideGui || NativePort.hasOverlay(client))) {
            suspend();
        }
    }

    public Screen details(Screen parent) {
        if (closed) throw new IllegalStateException("Activity host has stopped");
        return new NativeActivityScreen(this, parent);
    }

    public void openDetails(Screen parent) { NativePort.show(Minecraft.getInstance(), details(parent)); }

    void detailOpened(NativeActivityScreen screen) { details = screen; }

    void detailClosed(NativeActivityScreen screen) {
        if (details == screen) { details = null; runtime.collapse(); }
    }

    public void invalidateArtwork() { artwork.invalidate(); }

    NativeMusicArtwork artwork() { return artwork; }
    /** Single upload/release point before extracting either the music card or island. */
    void prepareArtwork(com.dishanhai.sh_better_ui.client.music.MusicService.Frame frame, boolean hudVisible, boolean sceneVisible) {
        var cover = frame.snapshot().session() == null ? null : frame.snapshot().session().artwork();
        var icon = NativeMusicArtwork.icon(cover);
        var primary = runtime.plan().primary();
        boolean islandNeeds = primary != null && icon != null && icon.equals(primary.content().icon()) && runtime.policy().enabled();
        artwork.beginFrame(sceneVisible && (hudVisible || islandNeeds) ? cover : null);
    }

    private void suspend() {
        motion.suspend(host.nowNanos() / 1_000_000L);
        runtime.present(new LiveSurfaceService.View(runtime.plan(), SurfacePresentation.SUSPENDED,
                SurfacePresentation.SUSPENDED, SurfaceGeometry.HIDDEN, SurfaceGeometry.HIDDEN, false));
    }

    /** Draws only on the in-game HUD, never over menus or blocking native overlays. */
    public void drawHud(GuiGraphicsExtractor graphics) {
        var client = Minecraft.getInstance();
        if (closed || client.level == null || NativePort.screen(client) != null || client.options.hideGui
                || NativePort.hasOverlay(client) || !client.isWindowActive()) { suspend(); return; }
        var plan = runtime.plan();
        var snapshot = plan.primary();
        var viewport = new SurfacePlacement.Rect(6, 6, Math.max(0, graphics.guiWidth() - 12), Math.max(0, graphics.guiHeight() - 12));
        SurfaceTransition.Target<ActivityService.Content> target;
        if (snapshot == null) {
            placement = SurfacePlacement.Result.HIDDEN;
            target = new SurfaceTransition.Target<>(SurfacePresentation.HIDDEN, SurfaceGeometry.HIDDEN, null);
        } else {
            typography.remember(snapshot.content(), snapshot.owner());
            var tokens = palette(snapshot.content());
            var candidate = candidate(plan.requested(), tokens, settings.layout(), graphics.guiWidth(), graphics.guiHeight());
            placement = SurfacePlacement.place(viewport, List.of(candidate), runtime.occupied(),
                    SurfacePlacement.CollisionPolicy.AVOID, placement);
            // Revisions update progress/lyrics without restarting geometry. A different song/title crossfades.
            target = new SurfaceTransition.Target<>(placement.presentation(), placement.geometry(), snapshot.content(),
                    new ContentKey(snapshot.instance(), snapshot.content().title()));
        }
        var frame = motion.step(target, viewport, runtime.occupied(), host.nowNanos() / 1_000_000L,
                UiPreferences.configured().reducedMotion());
        var retained = new java.util.ArrayList<ActivityService.Content>();
        retained.add(target.content());
        for (var layer : frame.contentMix()) retained.add(layer.content());
        typography.retain(retained);
        var area = frame.animated();
        runtime.present(new LiveSurfaceService.View(plan, frame.target().presentation(), frame.current().presentation(),
                area, SurfaceGeometry.HIDDEN, false, frame.running()));
        if (!area.visible()) return;
        var shellContent = target.content() != null ? target.content()
                : frame.contentMix().stream().map(SurfaceTransition.Layer::content).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (shellContent != null) paintShell(graphics, area, fade(palette(shellContent), area.opacity()));
        for (var layer : frame.contentMix()) if (layer.content() != null && layer.weight() > 0) {
            paintContent(graphics, layer.content(), area, fade(palette(layer.content()), area.opacity() * layer.weight()), layer.presentation(), typography.music(layer.content()));
        }
        if (snapshot != null && frame.input().content() != null) runtime.markPainted(snapshot);
    }

    private record ContentKey(long instance, ActivityService.Text title) {}

    static SurfaceTokens fade(SurfaceTokens colors, double opacity) {
        return new SurfaceTokens(alpha(colors.surface(), opacity), alpha(colors.title(), opacity), alpha(colors.detail(), opacity),
                alpha(colors.accent(), opacity), alpha(colors.progressTrack(), opacity), alpha(colors.button(), opacity),
                alpha(colors.buttonPressed(), opacity), alpha(colors.focus(), opacity), alpha(colors.shadow(), opacity),
                colors.highContrast(), colors.dimensions());
    }

    private static int alpha(int color, double opacity) {
        return ((int) Math.round((color >>> 24) * Math.clamp(opacity, 0, 1)) << 24) | (color & 0xffffff);
    }

    static SurfacePlacement.Candidate candidate(SurfacePresentation requested, SurfaceTokens tokens,
                                                LiveSurfaceSettings.Layout layout, int guiWidth, int guiHeight) {
        var mode = switch (requested) {
            case MINIMAL -> SurfacePresentation.MINIMAL;
            case EXPANDED, FLOATING -> SurfacePresentation.EXPANDED;
            default -> SurfacePresentation.COMPACT;
        };
        var sizing = tokens.dimensions().forPresentation(mode);
        int width = Math.min((int) Math.round(sizing.maxWidth() * layout.scale()), Math.max(0, guiWidth - 12));
        int height = (int) Math.round((mode == SurfacePresentation.EXPANDED ? Math.max(48, sizing.minHeight()) : sizing.minHeight()) * layout.scale());
        double x = switch (layout.anchor()) {
            case TOP_LEFT -> 6;
            case TOP_RIGHT -> guiWidth - width - 6;
            default -> (guiWidth - width) / 2d;
        };
        double y = layout.anchor() == LiveSurfaceSettings.Anchor.BOTTOM_CENTER ? guiHeight - height - 48 : 6;
        double radius = sizing.radius() < 0 ? height / 2d : sizing.radius() * layout.scale();
        return new SurfacePlacement.Candidate(mode, new SurfaceGeometry(x, y, width, height, radius, 1),
                Math.min(sizing.minWidth(), width), height);
    }

    SurfaceTokens palette(ActivityService.Content content) {
        if (UiPreferences.configured().highContrast()) return SurfaceTokens.HIGH_CONTRAST;
        if (content.appearance() != null) return content.appearance();
        var defaults = SurfaceTokens.DARK;
        var theme = settings.theme();
        if (theme.mode() == LiveSurfaceSettings.ThemeMode.CUSTOM)
            return new SurfaceTokens(theme.background(), defaults.title(), defaults.detail(), theme.accent(), defaults.progressTrack(),
                    defaults.button(), defaults.buttonPressed(), defaults.focus(), defaults.shadow(), false, defaults.dimensions());
        return defaults;
    }

    static Component text(ActivityService.Text text) {
        return text.translated() ? Component.translatable(text.value()) : Component.literal(text.value());
    }

    /** The shell is painted once; content blending must never reduce its opacity. */
    static void paintShell(GuiGraphicsExtractor graphics, SurfaceGeometry area, SurfaceTokens tokens) {
        RoundedCorners.draw(graphics, (int) area.x(), (int) area.y(), (int) area.width(), (int) area.height(),
                (float) area.radius(), 1, tokens.focus(), tokens.surface());
    }

    void paintContent(GuiGraphicsExtractor graphics, ActivityService.Content content, SurfaceGeometry area, SurfaceTokens tokens, SurfacePresentation mode) {
        paintContent(graphics, content, area, tokens, mode, false);
    }
    private void paintContent(GuiGraphicsExtractor graphics, ActivityService.Content content, SurfaceGeometry area, SurfaceTokens tokens, SurfacePresentation mode, boolean music) {
        int x = (int) area.x(), y = (int) area.y(), width = (int) area.width(), height = (int) area.height();
        var font = Minecraft.getInstance().font;
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            if (mode == SurfacePresentation.MINIMAL) {
                int dot = Math.max(2, Math.min(width, height) / 3);
                RoundedCorners.draw(graphics, x + (width - dot) / 2, y + (height - dot) / 2,
                        dot, dot, dot / 2f, 0, 0, tokens.accent());
                return;
            }
            int left = x + 8;
            if (content.icon() != null && width >= 100) {
                var nativeCover = artwork.texture(content.icon());
                var id = nativeCover == null ? Identifier.tryParse(content.icon().toString()) : nativeCover.id();
                boolean knownResource = nativeCover == null && !NativeMusicArtwork.isCover(content.icon()) && id != null
                        && Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
                if (nativeCover != null || knownResource) {
                    int imageWidth = nativeCover == null ? 16 : Math.max(1, 16 * nativeCover.width() / Math.max(nativeCover.width(),nativeCover.height()));
                    int imageHeight = nativeCover == null ? 16 : Math.max(1, 16 * nativeCover.height() / Math.max(nativeCover.width(),nativeCover.height()));
                    graphics.blit(RenderPipelines.GUI_TEXTURED, id, left + (16-imageWidth)/2, y + Math.max(0, (height-imageHeight)/2),
                            0f, 0f, imageWidth, imageHeight, imageWidth, imageHeight, (tokens.title() & 0xff000000) | 0xffffff);
                    left += 22;
                }
            }
            int available = Math.max(1, x + width - left - 8);
            var title = music ? MusicTypography.bold(text(content.title())) : text(content.title()).copy().withStyle(net.minecraft.ChatFormatting.BOLD);
            var detail = music ? MusicTypography.text(text(content.detail())) : text(content.detail());
            if (mode == SurfacePresentation.EXPANDED) {
                graphics.text(font, clipped(font, title, available, music), left, y + 8, tokens.title(), false);
                graphics.text(font, clipped(font, detail, available, music), left, y + 23, tokens.detail(), false);
            } else {
                int titleWidth = detail.getString().isBlank() ? available : Math.min(font.width(title), Math.max(1, available / 2));
                int baseline = y + Math.max(0, (height - (music ? MusicTypography.LINE_HEIGHT : font.lineHeight)) / 2) - (content.progress() >= 0 ? 1 : 0);
                graphics.text(font, clipped(font, title, titleWidth, music), left, baseline, tokens.title(), false);
                int detailX = left + titleWidth + 8;
                if (detailX < x + width - 8)
                    graphics.text(font, clipped(font, detail, x + width - detailX - 8, music), detailX, baseline, tokens.detail(), false);
            }
            if (content.progress() >= 0) {
                int track = Math.max(0, width - 16);
                graphics.fill(x + 8, y + height - 3, x + 8 + track, y + height - 2, tokens.progressTrack());
                graphics.fill(x + 8, y + height - 3, x + 8 + (int) Math.round(track * content.progress()), y + height - 2, tokens.accent());
            }
        } finally { graphics.disableScissor(); }
    }

    private static net.minecraft.util.FormattedCharSequence clipped(net.minecraft.client.gui.Font font, Component text, int width, boolean music) {
        return music ? MusicTextLayout.ellipsize(font, text, width, MusicTypography.font())
                : net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(text, width));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        typography.retain(List.of());
        artwork.close();
        var screen = details;
        if (screen != null && NativePort.screen(Minecraft.getInstance()) == screen) screen.onClose();
        details = null;
        runtime.clearPresentation();
    }
}
