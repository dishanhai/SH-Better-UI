// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.blur.GuiReadbackMarker;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.PixelRegion;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.SamplePoint;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.SamplingStatus;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Bounded regional readback. Every result names the frame and rectangle captured before its own text. */
final class ClientReadabilitySamples {
    private static final int MAX_TRANSFERS = 32;
    private static final int MAX_REGION_PIXELS = 32_768;
    private static final long MAX_AGE_NANOS = 250_000_000L;
    private final KernelHost host;
    private final TextReadabilityBudget budget = TextReadabilityBudget.fromProperties();
    private final ArrayList<Buffer> buffers = new ArrayList<>();
    private int scheduled;
    private long epoch;
    private int runs;
    private int samples;
    private int pixels;
    private int budgetFallbacks;
    private int unavailableFallbacks;
    private int invalidRegions;
    private long peakBytes;
    private long frame;
    private long sourceFrame = -1;
    private GpuTexture target;
    private int width, height, scale;

    ClientReadabilitySamples(KernelHost host) { this.host = host; }

    void begin() {
        var client = Minecraft.getInstance();
        var window = client.getWindow();
        var next = NativePort.colorTarget(client);
        if (target != next || width != window.getWidth() || height != window.getHeight() || scale != window.getGuiScale()) {
            invalidate();
            target = next;
            width = window.getWidth();
            height = window.getHeight();
            scale = window.getGuiScale();
        }
        frame++;
        budget.reset();
        scheduled = runs = samples = pixels = budgetFallbacks = unavailableFallbacks = invalidRegions = 0;
        sourceFrame = -1;
    }

    long frame() { return frame; }
    int scale() { return Math.max(1, scale); }

    void invalidate() {
        epoch++;
        buffers.forEach(buffer -> buffer.buffer.close());
        buffers.clear();
    }

    long epoch() { return epoch; }
    int runs() { return runs; }
    int samples() { return samples; }
    int pixels() { return pixels; }
    int budgetFallbacks() { return budgetFallbacks; }
    int unavailableFallbacks() { return unavailableFallbacks; }
    int invalidRegions() { return invalidRegions; }
    long sourceFrame() { return sourceFrame; }
    long bytes() { return buffers.stream().mapToLong(buffer -> buffer.capacity).sum(); }
    long peakBytes() { return peakBytes; }

    PixelRegion region(Rect bounds) {
        int left = Math.clamp((long) Math.floor(bounds.x() * scale()), 0, width);
        int right = Math.clamp((long) Math.ceil(bounds.right() * scale()), 0, width);
        int top = Math.clamp((long) Math.floor(bounds.y() * scale()), 0, height);
        int bottom = Math.clamp((long) Math.ceil(bounds.bottom() * scale()), 0, height);
        return right > left && bottom > top ? new PixelRegion(left, top, right - left, bottom - top) : null;
    }

    Readback current(Ticket ticket, PixelRegion region) {
        var value = ticket.value;
        return value != null && value.epoch == epoch && value.region.equals(region)
                && frame - value.frame <= 10 && host.nowNanos() - value.nanos <= MAX_AGE_NANOS ? value : null;
    }

    SamplingStatus schedule(GuiGraphicsExtractor graphics, Ticket ticket, PixelRegion region, int priority,
                            List<SamplePoint> points, boolean panel) {
        if (region == null) {
            invalidRegions++;
            return SamplingStatus.INVALID_REGION;
        }
        if (!available()) {
            unavailableFallbacks++;
            return SamplingStatus.UNAVAILABLE;
        }
        long area = (long) region.width() * region.height();
        var grant = area <= MAX_REGION_PIXELS ? budget.offer(priority, (int) area) : null;
        long pending = buffers.stream().filter(buffer -> buffer.pending).count();
        if (grant == null || pending + scheduled >= MAX_TRANSFERS) {
            budgetFallbacks++;
            return SamplingStatus.BUDGET_EXHAUSTED;
        }
        if (!panel && points.isEmpty()) {
            invalidRegions++;
            return SamplingStatus.INVALID_REGION;
        }
        int count = panel ? 0 : Math.min(grant.samples(), points.size());
        var selected = panel ? List.<SamplePoint>of() : select(points, count);
        var stamp = new Capture(ticket, region, selected, panel, epoch, frame, host.nowNanos());
        scheduled++;
        ticket.pending = frame;
        budget.spend(grant, (int) area);
        GuiReadbackMarker.submit(graphics, () -> capture(stamp));
        return SamplingStatus.GPU;
    }

    private static List<SamplePoint> select(List<SamplePoint> points, int count) {
        var selected = new ArrayList<SamplePoint>(count);
        for (int i = 0; i < count; i++) selected.add(points.get(i * points.size() / count));
        return List.copyOf(selected);
    }

    private boolean available() {
        return target != null && !target.isClosed() && target.getWidth(0) == width && target.getHeight(0) == height
                && NativePort.isRgba(target) && (target.usage() & GpuTexture.USAGE_COPY_SRC) != 0;
    }

    private void capture(Capture capture) {
        if (capture.epoch != epoch || !available() || target != NativePort.colorTarget(Minecraft.getInstance())) {
            if (capture.epoch == epoch) unavailableFallbacks++;
            return;
        }
        var device = RenderSystem.tryGetDevice();
        if (device == null) return;
        var region = capture.region;
        int area = region.width() * region.height();
        Buffer staging = null;
        try {
            int bytes = area * 4;
            staging = buffers.stream().filter(buffer -> !buffer.pending && buffer.capacity >= bytes)
                    .min(java.util.Comparator.comparingInt(buffer -> buffer.capacity)).orElse(null);
            if (staging == null) {
                if (buffers.size() == MAX_TRANSFERS) {
                    var free = buffers.stream().filter(buffer -> !buffer.pending).findFirst().orElse(null);
                    if (free == null) return;
                    buffers.remove(free);
                    free.buffer.close();
                }
                int capacity = Math.max(256, Integer.highestOneBit(bytes - 1) * 2);
                staging = new Buffer(device.createBuffer(() -> "UUK text background",
                        GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, capacity), capacity);
                buffers.add(staging);
                peakBytes = Math.max(peakBytes, bytes());
            }
            var active = staging;
            active.pending = true;
            var encoder = device.createCommandEncoder();
            encoder.copyTextureToBuffer(target, active.buffer, 0,
                    () -> {
                        try {
                            if (capture.epoch == epoch && !active.buffer.isClosed()) {
                                NativePort.readPixels(encoder, active.buffer, data -> publish(capture, data));
                            }
                        } catch (RuntimeException failure) {
                            host.report("Read UUK text background", failure);
                        } finally {
                            active.pending = false;
                        }
                    }, 0, region.x(), height - region.y() - region.height(), region.width(), region.height());
            sourceFrame = capture.frame;
            runs++;
            pixels += area;
            samples += capture.panel ? Math.min(16, region.width()) * Math.min(16, region.height()) : capture.points.size();
        } catch (RuntimeException failure) {
            if (staging != null) {
                buffers.remove(staging);
                staging.buffer.close();
            }
            unavailableFallbacks++;
            host.report("Copy UUK text background", failure);
        }
    }

    private static void publish(Capture capture, ByteBuffer data) {
        var ticket = capture.ticket;
        if (ticket.value != null && ticket.value.frame >= capture.frame) return;
        var region = capture.region;
        int columns = capture.panel ? Math.min(16, region.width()) : capture.points.size();
        int rows = capture.panel ? Math.min(16, region.height()) : 1;
        var pixels = new int[columns * rows];
        if (capture.panel) {
            for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
                int px = (2 * x + 1) * region.width() / (2 * columns);
                int py = (2 * y + 1) * region.height() / (2 * rows);
                pixels[y * columns + x] = pixel(data, region, px, py);
            }
        } else {
            for (int i = 0; i < pixels.length; i++) {
                var point = capture.points.get(i);
                pixels[i] = pixel(data, region, point.x() - region.x(), point.y() - region.y());
            }
        }
        ticket.value = new Readback(region, capture.points, pixels, columns, rows, capture.epoch, capture.frame, capture.nanos);
    }

    private static int pixel(ByteBuffer data, PixelRegion region, int x, int y) {
        int offset = ((region.height() - 1 - y) * region.width() + x) * 4;
        int r = Byte.toUnsignedInt(data.get(offset));
        int g = Byte.toUnsignedInt(data.get(offset + 1));
        int b = Byte.toUnsignedInt(data.get(offset + 2));
        int a = Byte.toUnsignedInt(data.get(offset + 3));
        return a << 24 | r << 16 | g << 8 | b;
    }

    static final class Ticket {
        long pending = -1;
        Readback value;
    }

    record Readback(PixelRegion region, List<SamplePoint> points, int[] pixels,
                    int columns, int rows, long epoch, long frame, long nanos) {}

    private record Capture(Ticket ticket, PixelRegion region, List<SamplePoint> points, boolean panel,
                           long epoch, long frame, long nanos) {}

    private static final class Buffer {
        final GpuBuffer buffer;
        final int capacity;
        boolean pending;

        Buffer(GpuBuffer buffer, int capacity) {
            this.buffer = buffer;
            this.capacity = capacity;
        }
    }
}
