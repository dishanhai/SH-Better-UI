// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.HudService;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.BackgroundSample;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Source;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Stage;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Validity;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.SamplingStatus;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Owns native HUD callbacks and their stable per-panel color state. */
final class ClientHud implements HudService {
    private static final int MAX_PANELS = 128;
    private static final long IDLE_NANOS = 5_000_000_000L;
    private final KernelHost host;
    private final ClientReadabilitySamples samples;
    private final ArrayList<Mount> mounts = new ArrayList<>();
    private final LinkedHashMap<PanelId, Panel> panels = new LinkedHashMap<>(16, .75f, true);
    private Mount active;
    private Frame current;
    private Object level;
    private long nextMount;

    ClientHud(KernelHost host, ClientReadabilitySamples samples) {
        this.host = host;
        this.samples = samples;
    }

    @Override public AutoCloseable mount(ModuleContext module, Consumer<Frame> draw) {
        var access = host.moduleAccess(module);
        var scope = access.write(module.scope());
        access.require(UIPermission.REGISTER_RENDER_PASS, scope);
        var entry = new Mount(++nextMount, module, scope, Objects.requireNonNull(draw));
        mounts.add(entry);
        var lifetime = host.onScopeClose(scope, () -> remove(entry));
        return () -> { host.assertOwner(); remove(entry); lifetime.close(); };
    }

    private void remove(Mount mount) {
        mounts.remove(mount);
        panels.keySet().removeIf(id -> id.mount == mount.id);
    }

    void draw(GuiGraphicsExtractor graphics, float partialTick, Object nextLevel) {
        if (level != nextLevel) {
            panels.clear();
            level = nextLevel;
        }
        long now = host.nowNanos();
        panels.values().removeIf(panel -> now - panel.seen >= IDLE_NANOS);
        for (var mount : List.copyOf(mounts)) {
            if (!mount.module.scope().isOpen() || !mounts.contains(mount)) continue;
            active = mount;
            current = new Frame(graphics, UiPreferences.configured(), partialTick);
            graphics.pose().pushMatrix();
            try {
                mount.draw.accept(current);
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                host.report("UUK HUD " + mount.module.descriptor().id(), failure);
            } finally {
                graphics.pose().popMatrix();
                current = null;
                active = null;
            }
        }
    }

    @Override public TextReadabilityPalette textPalette(ModuleContext module, Frame frame, UiId panelId, Rect local,
                                                       int darkestArgb, int lightestArgb) {
        var access = host.moduleAccess(module);
        if (frame == null || frame != current || active == null || !mounts.contains(active)) {
            throw new IllegalStateException("Expired HUD frame");
        }
        if (active.module != module) throw new SecurityException("HUD frame belongs to another module");
        access.require(UIPermission.REGISTER_RENDER_PASS, active.scope);
        var id = new PanelId(active.id, Objects.requireNonNull(panelId));
        if (!panels.containsKey(id) && panels.size() >= MAX_PANELS) panels.pollFirstEntry();
        var panel = panels.computeIfAbsent(id, ignored -> new Panel());
        long now = host.nowNanos();
        panel.seen = now;
        var graphics = frame.graphics();
        var bounds = ClientTextPaint.transform(graphics).map(Objects.requireNonNull(local));
        var region = samples.region(ClientTextPaint.clip(graphics, bounds));
        BackgroundSample sample = null;
        if (region != null) {
            var status = panel.capture.pending == samples.frame() ? SamplingStatus.GPU
                    : samples.schedule(graphics, panel.capture, region, 1, List.of(), true);
            var value = samples.current(panel.capture, region);
            if (value != null) {
                sample = BackgroundSample.pixels(region, value.columns(), value.rows(), value.pixels(),
                        Stage.POST_MATERIAL_PRE_TEXT, Source.GPU_DOWNSAMPLED_PIXELS, value.frame(), value.nanos(),
                        value.epoch());
            } else {
                sample = BackgroundSample.unavailable(region, Stage.POST_MATERIAL_PRE_TEXT,
                        status == SamplingStatus.GPU ? Validity.PENDING : Validity.UNAVAILABLE,
                        samples.frame(), now, samples.epoch());
            }
        }
        panel.palette.update(sample, darkestArgb, lightestArgb, now);
        return panel.palette;
    }

    private record Mount(long id, ModuleContext module, ScopeLifecycle.Scope scope, Consumer<Frame> draw) {}
    private record PanelId(long mount, UiId id) {}
    private static final class Panel {
        final TextReadabilityPalette palette = new TextReadabilityPalette();
        final ClientReadabilitySamples.Ticket capture = new ClientReadabilitySamples.Ticket();
        long seen;
    }
}
