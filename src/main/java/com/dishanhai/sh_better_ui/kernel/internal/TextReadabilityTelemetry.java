// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Decision;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Diagnostic;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Protection;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.ProtectionArea;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Source;
import com.dishanhai.sh_better_ui.kernel.internal.TextReadabilityPainter.ActualCost;
import com.dishanhai.sh_better_ui.kernel.internal.TextReadabilityPainter.ActualDiagnostic;
import com.dishanhai.sh_better_ui.kernel.internal.TextReadabilityPainter.ActualInput;
import com.dishanhai.sh_better_ui.kernel.internal.TextReadabilityPainter.ActualSample;
import com.dishanhai.sh_better_ui.kernel.internal.TextReadabilityPainter.Scissor;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

/** Bounded, optional observation. Neither the draw nor polling waits for unfinished GPU work. */
final class TextReadabilityTelemetry implements AutoCloseable {
    private static final int DIAGNOSTIC_CAPACITY = 16;
    private static final int TIMER_CAPACITY = 128;
    private static final int VEC4_BYTES = 16;
    private static final int SAMPLE_OFFSET = 2 * VEC4_BYTES;
    // Bit order of text_readability_decide.fsh. Keep the wire format independent of enum order.
    private static final Diagnostic[] REASON_BITS = {
            Diagnostic.INSUFFICIENT_ACTUAL_SAMPLES, Diagnostic.UNKNOWN_BACKGROUND,
            Diagnostic.BACKGROUND_CONTRAST_INSUFFICIENT, Diagnostic.INDIVISIBLE_RUN,
            Diagnostic.HUE_LOCKED, Diagnostic.NO_ELIGIBLE_CANDIDATE, Diagnostic.WAITING_FOR_STABILITY,
            Diagnostic.UNSTABLE_TEXT_ID, Diagnostic.PROTECTION_LIMITED, Diagnostic.GROUP_OPACITY_CONTRAST_INSUFFICIENT
    };

    private final int pboBytes;
    private final Readback[] readbacks = new Readback[DIAGNOSTIC_CAPACITY];
    private final Timer[] timers = new Timer[TIMER_CAPACITY];
    private final boolean timingSupported;
    private boolean readbackFailed;

    TextReadabilityTelemetry(int maximumSamples) {
        pboBytes = SAMPLE_OFFSET + maximumSamples * VEC4_BYTES;
        var capabilities = GL.getCapabilities();
        timingSupported = capabilities.glQueryCounter != 0 && capabilities.glGetQueryObjectui64v != 0;
    }

    boolean enqueue(ActualInput input, int framebufferHeight, Scissor clip, int samples,
                    int decisionFramebuffer, int sampleFramebuffer) {
        if (readbackFailed) return false;
        var slot = freeReadback();
        if (slot == null) return false;
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldPack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        var pack = Pack.capture();
        try {
            if (slot.pbo == 0) {
                slot.pbo = GL15.glGenBuffers();
                if (slot.pbo == 0) throw new IllegalStateException("Could not allocate text diagnostic readback");
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot.pbo);
                GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, pboBytes, GL15.GL_STREAM_READ);
            } else {
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot.pbo);
            }
            Pack.DEFAULT.apply();
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, decisionFramebuffer);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT1);
            GL11.glReadPixels(0, 0, 1, 1, GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_INT, 0L);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT2);
            GL11.glReadPixels(0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, (long) VEC4_BYTES);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sampleFramebuffer);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadPixels(0, 0, samples, 1, GL11.GL_RGBA, GL11.GL_FLOAT, (long) SAMPLE_OFFSET);
            slot.fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            if (slot.fence == 0) throw new IllegalStateException("Could not fence text diagnostic readback");
            slot.stamp = new Stamp(input.diagnosticId(), input.frame(), input.threshold(), framebufferHeight, clip, samples);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            failReadback(failure);
            return false;
        } finally {
            pack.apply();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, oldPack);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
        }
    }

    void pollDiagnostics(Consumer<ActualDiagnostic> accept) {
        if (readbackFailed || !RenderSystem.isOnRenderThread()) return;
        for (var slot : readbacks) {
            if (slot == null || slot.stamp == null) continue;
            int status = GL32.glClientWaitSync(slot.fence, 0, 0);
            if (status == GL32.GL_TIMEOUT_EXPIRED) continue;
            ActualDiagnostic completed = null;
            try {
                if (status != GL32.GL_WAIT_FAILED) completed = map(slot);
            } catch (RuntimeException | LinkageError failure) {
                failReadback(failure);
                return;
            } finally {
                slot.complete();
            }
            if (completed != null) accept.accept(completed);
        }
    }

    private static ActualDiagnostic map(Readback slot) {
        int binding = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot.pbo);
            long bytes = SAMPLE_OFFSET + (long) slot.stamp.samples * VEC4_BYTES;
            var mapped = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY, bytes, null);
            if (mapped == null) throw new IllegalStateException("Could not map a completed text diagnostic");
            ActualDiagnostic result;
            boolean intact;
            try {
                result = decode(mapped.order(ByteOrder.nativeOrder()), slot.stamp);
            } finally {
                intact = GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            }
            return intact ? result : null;
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, binding);
        }
    }

    private static ActualDiagnostic decode(ByteBuffer bytes, Stamp stamp) {
        int fill = bytes.getInt(0), protectionArgb = bytes.getInt(4), pending = bytes.getInt(8), metadata = bytes.getInt(12);
        var source = switch (metadata & 3) {
            case 0 -> Source.ACTUAL;
            case 1 -> Source.DECLARED;
            case 2 -> Source.UNKNOWN;
            default -> throw new IllegalStateException("Invalid text readability source");
        };
        var protection = switch ((metadata >>> 2) & 3) {
            case 0 -> Protection.NONE;
            case 1 -> Protection.OUTLINE;
            case 2 -> Protection.BACKING;
            default -> throw new IllegalStateException("Invalid text readability protection");
        };
        var reasons = EnumSet.noneOf(Diagnostic.class);
        for (int bit = 0; bit < REASON_BITS.length; bit++) {
            if ((metadata & (1 << (4 + bit))) != 0) reasons.add(REASON_BITS[bit]);
        }
        var protections = protection == Protection.NONE ? List.<ProtectionArea>of()
                : List.of(new ProtectionArea(TextReadabilityReference.WHOLE_RUN, protection, protectionArgb));
        var decision = new Decision(fill, source, false, stamp.threshold, contrast(bytes, VEC4_BYTES),
                contrast(bytes, VEC4_BYTES + 4), contrast(bytes, VEC4_BYTES + 8),
                pending == 0 ? null : Integer.valueOf(pending), protections, reasons);
        var samples = new ArrayList<ActualSample>(stamp.samples);
        for (int i = 0; i < stamp.samples; i++) {
            int offset = SAMPLE_OFFSET + i * VEC4_BYTES;
            float luminance = bytes.getFloat(offset);
            if (luminance < 0) continue;
            int x = stamp.clip.left() + (int) bytes.getFloat(offset + 8);
            int y = stamp.framebufferHeight - stamp.clip.bottom() - (int) bytes.getFloat(offset + 12) - 1;
            samples.add(new ActualSample(x, y, luminance, bytes.getFloat(offset + 4)));
        }
        return new ActualDiagnostic(stamp.id, stamp.frame, stamp.samples, decision, List.copyOf(samples));
    }

    private static double contrast(ByteBuffer bytes, int offset) {
        float ratio = bytes.getFloat(offset);
        return ratio < 0 ? Double.NaN : ratio;
    }

    private Readback freeReadback() {
        for (int i = 0; i < readbacks.length; i++) {
            if (readbacks[i] == null) readbacks[i] = new Readback();
            if (readbacks[i].stamp == null) return readbacks[i];
        }
        return null;
    }

    private void failReadback(Throwable failure) {
        clearDiagnostics();
        readbackFailed = true;
        ShBetterUi.LOGGER.warn("Text readability diagnostics could not be read asynchronously", failure);
    }

    void clearDiagnostics() {
        for (int i = 0; i < readbacks.length; i++) {
            var slot = readbacks[i];
            if (slot == null) continue;
            slot.complete();
            if (slot.pbo != 0) GL15.glDeleteBuffers(slot.pbo);
            readbacks[i] = null;
        }
    }

    Timer beginCost(long frame) {
        if (!timingSupported) return null;
        for (int i = 0; i < timers.length; i++) {
            if (timers[i] == null) timers[i] = new Timer(GL15.glGenQueries(), GL15.glGenQueries());
            var timer = timers[i];
            if (timer.start == 0 || timer.end == 0) return null;
            if (!timer.pending) {
                timer.frame = frame;
                GL33.glQueryCounter(timer.start, GL33.GL_TIMESTAMP);
                return timer;
            }
        }
        return null;
    }

    void endCost(Timer timer) {
        if (timer == null) return;
        GL33.glQueryCounter(timer.end, GL33.GL_TIMESTAMP);
        timer.pending = true;
    }

    void pollCosts(Consumer<ActualCost> accept) {
        if (!timingSupported || !RenderSystem.isOnRenderThread()) return;
        long incomplete = Long.MAX_VALUE;
        for (var timer : timers) {
            if (timer != null && timer.pending && timer.frame < incomplete
                    && GL15.glGetQueryObjecti(timer.end, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
                incomplete = timer.frame;
            }
        }
        for (var timer : timers) {
            // Publish only completed source frames. A partial newest frame would hide the
            // previous frame's complete measurement in the service's aggregate snapshot.
            if (timer == null || !timer.pending || timer.frame >= incomplete) continue;
            long start = GL33.glGetQueryObjectui64(timer.start, GL15.GL_QUERY_RESULT);
            long end = GL33.glGetQueryObjectui64(timer.end, GL15.GL_QUERY_RESULT);
            timer.pending = false;
            if (end >= start) accept.accept(new ActualCost(timer.frame, end - start));
        }
    }

    long allocatedBytes() {
        long bytes = 0;
        for (var slot : readbacks) if (slot != null && slot.pbo != 0) bytes += pboBytes;
        return bytes;
    }

    @Override public void close() {
        clearDiagnostics();
        for (var timer : timers) {
            if (timer == null) continue;
            if (timer.start != 0) GL15.glDeleteQueries(timer.start);
            if (timer.end != 0) GL15.glDeleteQueries(timer.end);
        }
    }

    private record Stamp(long id, long frame, double threshold, int framebufferHeight, Scissor clip, int samples) {}

    private static final class Readback {
        int pbo;
        long fence;
        Stamp stamp;

        void complete() {
            if (fence != 0) GL32.glDeleteSync(fence);
            fence = 0;
            stamp = null;
        }
    }

    static final class Timer {
        final int start, end;
        long frame;
        boolean pending;

        Timer(int start, int end) { this.start = start; this.end = end; }
    }

    private record Pack(int alignment, int rowLength, int skipPixels, int skipRows, int swapBytes) {
        static final Pack DEFAULT = new Pack(1, 0, 0, 0, 0);

        static Pack capture() {
            return new Pack(GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT), GL11.glGetInteger(GL12.GL_PACK_ROW_LENGTH),
                    GL11.glGetInteger(GL12.GL_PACK_SKIP_PIXELS), GL11.glGetInteger(GL12.GL_PACK_SKIP_ROWS),
                    GL11.glGetInteger(GL11.GL_PACK_SWAP_BYTES));
        }

        void apply() {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, swapBytes);
        }
    }
}
