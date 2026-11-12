// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.*;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Render-thread ROI downsampling and asynchronous pixel observation. The caller flushes GUI geometry before request.
 * A request never waits for GPU completion; full slots and unsupported targets produce an explicit pending/unavailable sample.
 */
final class ClientBackgroundSampler implements AutoCloseable {
    private static final int SLOT_COUNT = 3;
    private static final int MAX_REGIONS = 128;
    private static final long MAX_SAMPLE_AGE_NANOS = 1_000_000_000L;
    private final int maxAxis;
    private final long minimumInterval;
    private final Slot[] slots = new Slot[SLOT_COUNT];
    private final LinkedHashMap<UiId, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private long frame, now, version, generation, nextTicket;
    private long scheduled, completed, discarded, cpuReadbackNanos, cacheHits;
    private int texture, framebuffer;
    private boolean failed, closed;

    ClientBackgroundSampler(int maxSamplesPerAxis, long minIntervalNanos) {
        if (maxSamplesPerAxis < 1 || maxSamplesPerAxis > 64 || minIntervalNanos < 0) {
            throw new IllegalArgumentException("Invalid bounded background sampling policy");
        }
        maxAxis = maxSamplesPerAxis; minimumInterval = minIntervalNanos;
    }

    void beginFrame(long frameId, long nowNanos, long stateVersion) {
        requireOpen();
        frame = frameId; now = nowNanos; version = stateVersion;
        if (!failed) poll();
    }

    BackgroundSample request(UiId id, PixelRegion roi, Stage stage) {
        return request(id, roi, stage, null);
    }

    BackgroundSample request(UiId id, PixelRegion roi, Stage stage, Runnable beforeCapture) {
        requireOpen(); Objects.requireNonNull(id); Objects.requireNonNull(roi); Objects.requireNonNull(stage);
        Entry entry = entries.get(id);
        if (entry == null || !entry.region.equals(roi) || entry.stage != stage || entry.version != version) {
            entry = new Entry(roi, stage, version, minimumInterval); entries.put(id, entry);
            if (entries.size() > MAX_REGIONS) entries.remove(entries.keySet().iterator().next());
        }
        boolean fresh = matches(entry.sample, roi, stage, version)
                && now - entry.sample.capturedNanos() >= 0 && now - entry.sample.capturedNanos() <= MAX_SAMPLE_AGE_NANOS;
        if (failed) return unavailable(roi, stage, Validity.UNAVAILABLE);
        if (entry.ticket == 0 && (entry.lastRequested == Long.MIN_VALUE || now - entry.lastRequested >= entry.interval)) {
            Slot free = freeSlot();
            if (free != null) {
                try {
                    if (beforeCapture != null) beforeCapture.run();
                    enqueue(free, id, entry);
                } catch (RuntimeException | LinkageError failure) {
                    failed = true;
                    System.getLogger("sh_better_ui.visual").log(System.Logger.Level.WARNING,
                            "Background GPU observation unavailable until resource reset", failure);
                    return unavailable(roi, stage, Validity.UNAVAILABLE);
                }
            }
        }
        if (entry.unavailable) return unavailable(roi, stage, Validity.UNAVAILABLE);
        if (fresh) cacheHits++;
        return fresh ? entry.sample : unavailable(roi, stage, entry.sample == null ? Validity.PENDING : Validity.STALE);
    }

    /** Cache-only lookup for nodes outside this frame's GPU budget; never enqueues or fabricates a sample. */
    BackgroundSample cached(UiId id, PixelRegion roi, Stage stage) {
        requireOpen();
        Entry entry = entries.get(id);
        if (entry == null || entry.unavailable || !matches(entry.sample, roi, stage, version)) return null;
        long age = now - entry.sample.capturedNanos();
        if (age < 0 || age > MAX_SAMPLE_AGE_NANOS) return null;
        cacheHits++; return entry.sample;
    }

    private BackgroundSample unavailable(PixelRegion roi, Stage stage, Validity validity) {
        return BackgroundSample.unavailable(roi, stage, validity, frame, now, version);
    }

    static boolean matches(BackgroundSample sample, PixelRegion roi, Stage stage, long version) {
        return sample != null && sample.validity() == Validity.VALID && sample.region().equals(roi)
                && sample.stage() == stage && sample.stateVersion() == version;
    }

    static boolean temporallyStable(BackgroundSample previous, BackgroundSample next) {
        if (previous == null || previous.sampleWidth() != next.sampleWidth() || previous.sampleHeight() != next.sampleHeight()) return false;
        int[] a = previous.pixels(), b = next.pixels();
        long difference = 0;
        for (int i = 0; i < a.length; i++) for (int shift = 0; shift <= 16; shift += 8) {
            difference += Math.abs((a[i] >>> shift & 255) - (b[i] >>> shift & 255));
        }
        return difference < a.length * 3L * 255 * .015;
    }

    static int[] sampleSize(PixelRegion roi, int maxAxis) {
        if (maxAxis < 1 || maxAxis > 64) throw new IllegalArgumentException("Invalid sample axis budget");
        double scale = Math.min(1, maxAxis / (double) Math.max(roi.width(), roi.height()));
        return new int[]{Math.min(maxAxis, Math.max(1, (int) Math.ceil(roi.width() * scale))),
                Math.min(maxAxis, Math.max(1, (int) Math.ceil(roi.height() * scale)))};
    }

    private Slot freeSlot() {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) slots[i] = new Slot();
            if (slots[i].stamp == null) return slots[i];
        }
        return null;
    }

    private void enqueue(Slot slot, UiId id, Entry entry) {
        if (!GL.getCapabilities().OpenGL32) throw new IllegalStateException("Background observation requires OpenGL 3.2");
        var roi = entry.region;
        int[] size = sampleSize(roi, maxAxis);
        try (var stack = MemoryStack.stackPush()) {
            var viewport = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int vx = viewport.get(0), vy = viewport.get(1), vw = viewport.get(2), vh = viewport.get(3);
            // An offset viewport alone does not reveal the framebuffer's full upper-left coordinate origin.
            // Decline it instead of claiming pixels from an ambiguous coordinate conversion.
            if (vx != 0 || vy != 0 || roi.x() < 0 || roi.y() < 0 || (long) roi.x() + roi.width() > vw
                    || (long) roi.y() + roi.height() > vh) {
                entry.lastRequested = now; entry.unavailable = true;
                return;
            }
            int source = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int attachment = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0);
            int samples = GL11.glGetInteger(GL32.GL_SAMPLES);
            if (attachment == GL11.GL_NONE || samples > 1 && (size[0] != roi.width() || size[1] != roi.height())) {
                throw new IllegalStateException("The current framebuffer cannot be downsampled without an MSAA resolve");
            }
            int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int oldDraw = source;
            int oldPack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            int rowLength = GL11.glGetInteger(GL12.GL_PACK_ROW_LENGTH);
            int skipPixels = GL11.glGetInteger(GL12.GL_PACK_SKIP_PIXELS);
            int skipRows = GL11.glGetInteger(GL12.GL_PACK_SKIP_ROWS);
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            try {
                ensureTarget();
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source);
                int sourceBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
                try {
                    GL11.glReadBuffer(attachment);
                    if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                        throw new IllegalStateException("Background read framebuffer is incomplete");
                    }
                    if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
                    int bottom = vy + vh - roi.y() - roi.height();
                    GL30.glBlitFramebuffer(roi.x(), bottom, roi.x() + roi.width(), bottom + roi.height(),
                            0, 0, size[0], size[1], GL11.GL_COLOR_BUFFER_BIT,
                            samples > 1 ? GL11.GL_NEAREST : GL11.GL_LINEAR);
                } finally {
                    GL11.glReadBuffer(sourceBuffer);
                }
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                if (slot.pbo == 0) {
                    slot.pbo = GL15.glGenBuffers();
                    if (slot.pbo == 0) throw new IllegalStateException("Could not allocate an asynchronous background buffer");
                    GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot.pbo);
                    GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, (long) maxAxis * maxAxis * 4, GL15.GL_STREAM_READ);
                } else GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot.pbo);
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
                GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, 0);
                // A PBO offset is supplied, never client memory; only the bounded reduced ROI is read.
                GL11.glReadPixels(0, 0, size[0], size[1], GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
                slot.fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
                if (slot.fence == 0) throw new IllegalStateException("Could not fence background readback");
                long ticket = ++nextTicket;
                slot.stamp = new Stamp(id, roi, entry.stage, size[0], size[1], frame, now, version, generation, ticket);
                entry.ticket = ticket; entry.lastRequested = now; entry.unavailable = false; scheduled++;
            } finally {
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
                GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, rowLength);
                GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, skipPixels);
                GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, skipRows);
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, oldPack);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
                if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    private void poll() {
        for (Slot slot : slots) {
            if (slot == null || slot.stamp == null) continue;
            int status = GL32.glClientWaitSync(slot.fence, 0, 0);
            if (status == GL32.GL_TIMEOUT_EXPIRED) continue;
            var stamp = slot.stamp;
            Entry entry = entries.get(stamp.id);
            boolean current = status != GL32.GL_WAIT_FAILED && stamp.generation == generation && stamp.version == version
                    && entry != null && entry.ticket == stamp.ticket && entry.region.equals(stamp.region) && entry.stage == stamp.stage;
            try {
                if (current) {
                    long start = System.nanoTime();
                    int binding = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
                    try {
                        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot.pbo);
                        ByteBuffer bytes = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY,
                                (long) stamp.width * stamp.height * 4, null);
                        if (bytes == null) throw new IllegalStateException("Completed background buffer could not be mapped");
                        int[] colors = new int[stamp.width * stamp.height];
                        boolean intact;
                        try {
                            for (int y = 0; y < stamp.height; y++) for (int x = 0; x < stamp.width; x++) {
                                int offset = ((stamp.height - 1 - y) * stamp.width + x) * 4;
                                colors[y * stamp.width + x] = 0xff000000 | (bytes.get(offset) & 255) << 16
                                        | (bytes.get(offset + 1) & 255) << 8 | bytes.get(offset + 2) & 255;
                            }
                        } finally { intact = GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER); }
                        if (intact) {
                            var sample = BackgroundSample.pixels(stamp.region, stamp.width, stamp.height, colors,
                                    stamp.stage, Source.GPU_DOWNSAMPLED_PIXELS, stamp.frame, stamp.time, stamp.version);
                            if (temporallyStable(entry.sample, sample)) {
                                entry.stableSamples = Math.min(6, entry.stableSamples + 1);
                            } else entry.stableSamples = 0;
                            long multiplier = 1L << Math.min(3, entry.stableSamples / 2);
                            long expanded = minimumInterval > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : minimumInterval * multiplier;
                            entry.interval = Math.max(minimumInterval, Math.min(500_000_000L, expanded));
                            entry.sample = sample;
                            completed++;
                        } else discarded++;
                    } finally {
                        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, binding);
                        cpuReadbackNanos += Math.max(0, System.nanoTime() - start);
                    }
                } else discarded++;
            } catch (RuntimeException | LinkageError failure) {
                failed = true;
                System.getLogger("sh_better_ui.visual").log(System.Logger.Level.WARNING,
                        "Asynchronous background readback failed", failure);
            } finally {
                if (entry != null && entry.ticket == stamp.ticket) entry.ticket = 0;
                GL32.glDeleteSync(slot.fence); slot.fence = 0; slot.stamp = null;
            }
        }
    }

    private void ensureTarget() {
        if (framebuffer != 0) return;
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            texture = GL11.glGenTextures(); framebuffer = GL30.glGenFramebuffers();
            if (texture == 0 || framebuffer == 0) throw new IllegalStateException("Could not allocate background reduction target");
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, maxAxis, maxAxis, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Incomplete background reduction framebuffer");
            }
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack);
        }
    }

    void forget(UiId id) { requireOpen(); entries.remove(id); }

    /** Called for resource reload, window resize and world/session teardown by the owning Kernel bridge. */
    void reset() {
        if (closed) throw new IllegalStateException("Background sampler is closed");
        // Pure Kernel lifecycle tests and pre-render shutdown have no native resources to release.
        boolean allocated = framebuffer != 0 || texture != 0;
        for (var slot : slots) if (slot != null && (slot.pbo != 0 || slot.fence != 0)) allocated = true;
        if (allocated) RenderSystem.assertOnRenderThread();
        generation++; entries.clear(); failed = false;
        for (int i = 0; i < slots.length; i++) {
            Slot slot = slots[i]; if (slot == null) continue;
            if (slot.fence != 0) GL32.glDeleteSync(slot.fence);
            if (slot.pbo != 0) GL15.glDeleteBuffers(slot.pbo);
            slots[i] = null;
        }
        if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
        if (texture != 0) GL11.glDeleteTextures(texture);
        framebuffer = 0; texture = 0;
    }

    Metrics metrics() {
        int busy = 0; for (var slot : slots) if (slot != null && slot.stamp != null) busy++;
        return new Metrics(scheduled, completed, discarded, cpuReadbackNanos, busy,
                (long) maxAxis * maxAxis * 4 * (1 + SLOT_COUNT), !failed, -1, cacheHits);
    }
    record Metrics(long scheduled, long completed, long discarded, long cpuReadbackNanos, int pendingSlots,
                   long maximumAllocatedBytes, boolean available, long gpuCostNanos, long cacheHits) {}

    private void requireOpen() {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("Background sampler is closed");
    }
    @Override public void close() { if (!closed) { reset(); closed = true; } }
    private static final class Entry {
        final PixelRegion region; final Stage stage; final long version;
        long ticket, lastRequested = Long.MIN_VALUE, interval; int stableSamples; boolean unavailable; BackgroundSample sample;
        Entry(PixelRegion region, Stage stage, long version, long interval) {
            this.region = region; this.stage = stage; this.version = version; this.interval = interval;
        }
    }
    private static final class Slot { int pbo; long fence; Stamp stamp; }
    private record Stamp(UiId id, PixelRegion region, Stage stage, int width, int height,
                         long frame, long time, long version, long generation, long ticket) {}
}
