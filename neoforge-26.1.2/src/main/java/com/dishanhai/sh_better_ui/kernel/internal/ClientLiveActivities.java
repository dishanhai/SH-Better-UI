// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.live.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.time.Duration;
import java.util.List;

/** Native 26.x presentation for the shared, scope-owned local activity/notification runtime. */
public final class ClientLiveActivities implements AutoCloseable {
    private final LiveSurfaceRuntime runtime;
    private LiveSurfaceSettings settings;
    private SurfacePlacement.Result placement = SurfacePlacement.Result.HIDDEN;
    private NativeActivityScreen details;
    private boolean closed;

    ClientLiveActivities(KernelHost host, LiveSurfaceSettings settings) {
        runtime = new LiveSurfaceRuntime(host);
        settings(settings);
    }

    ActivityService activities() { return runtime; }
    NotificationService notifications() { return runtime; }
    LiveSurfaceService surfaces() { return runtime; }
    LiveSurfaceRuntime runtime() { return runtime; }

    public void settings(LiveSurfaceSettings next) {
        settings = java.util.Objects.requireNonNull(next);
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

    private void suspend() {
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
        if (snapshot == null) { runtime.present(LiveSurfaceService.View.EMPTY); placement = SurfacePlacement.Result.HIDDEN; return; }
        var tokens = palette(snapshot.content());
        int guiWidth = graphics.guiWidth(), guiHeight = graphics.guiHeight();
        var candidate = candidate(plan.requested(), tokens, settings.layout(), guiWidth, guiHeight);
        placement = SurfacePlacement.place(new SurfacePlacement.Rect(6, 6, Math.max(0, guiWidth - 12), Math.max(0, guiHeight - 12)),
                List.of(candidate), runtime.occupied(), SurfacePlacement.CollisionPolicy.AVOID, placement);
        var area = placement.geometry();
        runtime.present(new LiveSurfaceService.View(plan, placement.presentation(), placement.presentation(),
                area, SurfaceGeometry.HIDDEN, false));
        if (!area.visible()) return;
        paint(graphics, snapshot.content(), area, tokens, placement.presentation());
        runtime.markPainted(snapshot);
    }

    static SurfacePlacement.Candidate candidate(SurfacePresentation requested, SurfaceTokens tokens,
                                                LiveSurfaceSettings.Layout layout, int guiWidth, int guiHeight) {
        var mode = requested == SurfacePresentation.MINIMAL ? SurfacePresentation.MINIMAL : SurfacePresentation.COMPACT;
        var sizing = tokens.dimensions().forPresentation(mode);
        int width = Math.min((int) Math.round(sizing.maxWidth() * layout.scale()), Math.max(0, guiWidth - 12));
        int height = (int) Math.round((mode == SurfacePresentation.MINIMAL ? sizing.minHeight() : Math.max(34, sizing.minHeight())) * layout.scale());
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
        return SurfaceTokens.DARK;
    }

    static Component text(ActivityService.Text text) {
        return text.translated() ? Component.translatable(text.value()) : Component.literal(text.value());
    }

    static void paint(GuiGraphicsExtractor graphics, ActivityService.Content content, SurfaceGeometry area, SurfaceTokens tokens, SurfacePresentation mode) {
        int x = (int) area.x(), y = (int) area.y(), width = (int) area.width(), height = (int) area.height();
        var font = Minecraft.getInstance().font;
        RoundedCorners.draw(graphics, x, y, width, height, (float) area.radius(), 1, tokens.focus(), tokens.surface());
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
                var id = Identifier.tryParse(content.icon().toString());
                if (id != null && Minecraft.getInstance().getResourceManager().getResource(id).isPresent()) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, id, left, y + 8, 0f, 0f, 16, 16, 16, 16);
                    left += 22;
                }
            }
            int available = Math.max(1, x + width - left - 8);
            graphics.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(text(content.title()), available)), left, y + 6, tokens.title(), false);
            graphics.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(text(content.detail()), available)), left, y + 17, tokens.detail(), false);
            if (content.progress() >= 0) {
                int track = Math.max(0, width - 16);
                graphics.fill(x + 8, y + height - 5, x + 8 + track, y + height - 3, tokens.progressTrack());
                graphics.fill(x + 8, y + height - 5, x + 8 + (int) Math.round(track * content.progress()), y + height - 3, tokens.accent());
            }
        } finally { graphics.disableScissor(); }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        var screen = details;
        if (screen != null && NativePort.screen(Minecraft.getInstance()) == screen) screen.onClose();
        details = null;
        runtime.clearPresentation();
    }
}
