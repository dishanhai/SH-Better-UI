// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/** Measures animated dock solve and submission in a real development client at GUI scale 1. */
final class DockBenchmarkSmoke {
    private static final int[] COUNTS = {0, 1, 10, 50};
    private static final int WARMUP = 24;
    private static final int SAMPLES = 60;
    private final ModuleContext module;
    private final Screen parent;
    private final Path output;
    private final DockService dock;
    private final WindowService windows;
    private final StringBuilder rows = new StringBuilder("windows\tmetric\tmean_ms\tp95_ms\tmax_ms\n");
    private final long[] intervals = new long[SAMPLES], solves = new long[SAMPLES], paints = new long[SAMPLES];
    private final ArrayList<MiniWindowHandle> handles = new ArrayList<>();
    private int sample, group;
    private long previousFrame, previousSolves;

    DockBenchmarkSmoke(Minecraft client, ModuleContext module, Path output) {
        this.module = module;
        this.output = output;
        parent = client.screen;
        dock = module.engine().require(UIApis.DOCK);
        windows = module.engine().require(UIApis.WINDOWS);
        client.setScreen(new BenchScreen());
    }

    boolean advance(Minecraft client) throws IOException {
        int count = COUNTS[group];
        if (dock.layout().entries().size() != count) return false;
        var metrics = dock.diagnostics();
        long now = System.nanoTime();
        int index = sample++ - WARMUP;
        if (index >= 0) {
            intervals[index] = now - previousFrame;
            if (count > 0) {
                KernelClientSmoke.require(metrics.visibleWindows() == count && metrics.solves() > previousSolves,
                        "Native dock benchmark missed a layout solve or window");
                KernelClientSmoke.require(dock.inspectCollisions().overlaps().isEmpty(),
                        "Native dock benchmark grid overlapped");
                solves[index] = metrics.layoutNanos();
                paints[index] = metrics.paintNanos();
            }
        }
        previousFrame = now;
        previousSolves = metrics.solves();
        if (index + 1 == SAMPLES) {
            record(count, "frame_interval", intervals);
            if (count > 0) {
                record(count, "layout_solve", solves);
                record(count, "paint_submit", paints);
            }
            if (++group == COUNTS.length) {
                Files.writeString(output.resolve("dock-native-profile.tsv"), rows);
                client.setScreen(parent);
                return true;
            }
            handles.clear();
            sample = 0;
            client.setScreen(new BenchScreen());
        } else if (count > 0) {
            handles.get(0).floatAt(8 + (sample & 1), 8);
        }
        return false;
    }

    private void record(int count, String metric, long[] times) {
        var sorted = times.clone();
        Arrays.sort(sorted);
        rows.append(String.format(Locale.ROOT, "%d\t%s\t%.3f\t%.3f\t%.3f%n", count, metric,
                Arrays.stream(sorted).average().orElseThrow() / 1_000_000,
                sorted[(int) Math.ceil(sorted.length * .95) - 1] / 1_000_000.0,
                sorted[sorted.length - 1] / 1_000_000.0));
    }

    private final class BenchScreen extends Screen {
        BenchScreen() { super(Component.translatable("sh_better_ui.dock_example.title")); }

        @Override protected void init() {
            if (!handles.isEmpty() || COUNTS[group] == 0) return;
            var scope = module.engine().require(UIApis.SCREENS).mount(module, this, UiId.parse("sh_better_ui:dock_bench"));
            for (int i = 0; i < COUNTS[group]; i++) {
                var id = new UiId("sh_better_ui", "dock_bench/" + i);
                var window = windows.createMiniWindow(module, scope, MiniWindowSpec.builder(id)
                        .size(64, 44).compactSize(20, 26).limits(64, 44, 64, 44).autoHide(false).build());
                window.floatAt(8 + i % 10 * 74, 8 + i / 10 * 56);
                handles.add(window);
            }
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xff243045);
        }
    }
}
