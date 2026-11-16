// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.api.effect.Dither;
import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** CPU-only, latest-request-wins preparation. A bounded shared worker never runs tasks on the caller. */
final class DitherImagePipeline implements AutoCloseable {
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), task -> {
                var thread = new Thread(task, "shb-dither-image");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    static { WORKERS.allowCoreThreadTimeOut(true); }

    /** Called during resource loading, not on first background selection. Never waits or reads images. */
    static void warmUp() {
        try {
            WORKERS.execute(() -> {
                var first = new Request(1, 1, Dither.Options.DEMO, 0);
                // Prime record invokedynamic machinery away from the interactive draw callback.
                first.equals(new Request(1, 1, Dither.Options.DEMO, 0));
                first.hashCode();
            });
        } catch (RejectedExecutionException busy) {
            // Existing work already started the dispatcher; prewarming is optional.
        }
    }

    @FunctionalInterface interface Source { InputStream open() throws IOException; }
    record Request(int width, int height, Dither.Options options, double time) {
        Request {
            if (width < 1 || height < 1 || width > 2048 || height > 2048 || !Double.isFinite(time))
                throw new IllegalArgumentException("Invalid dither request");
            Objects.requireNonNull(options);
        }
    }
    /** Pixels are packed ABGR, owned exclusively by this result after publication; consumers only read. */
    record Result(Request request, int[] pixels, Exception failure) {}
    private final Executor executor;
    private final Source source;
    private Request desired, completed;
    private Result ready;
    private boolean running, closed;
    private long generation;
    // Only the worker accesses these CPU caches; there is at most one worker per pipeline.
    private BufferedImage image;
    private int[] fitted;
    private int fittedWidth, fittedHeight;
    private Dither.Fit fittedMode;

    DitherImagePipeline(Source source) { this(source, WORKERS); }
    DitherImagePipeline(Source source, Executor executor) {
        this.source = Objects.requireNonNull(source);
        this.executor = Objects.requireNonNull(executor);
    }

    synchronized void request(Request request) {
        if (closed) return;
        if (!request.equals(desired)) { desired = request; ready = null; completed = null; }
        schedule();
    }
    synchronized boolean pending() { return running || ready != null || !Objects.equals(desired, completed); }
    synchronized Result poll() { var result = ready; ready = null; return result; }

    private void schedule() {
        if (running || closed || desired == null || desired.equals(completed)) return;
        long ticket = generation;
        running = true;
        try { executor.execute(() -> prepare(ticket)); }
        catch (RejectedExecutionException busy) { running = false; } // Next draw retries; never CallerRuns.
    }

    private void prepare(long ticket) {
        Request request;
        synchronized (this) {
            if (closed || ticket != generation) { running = false; clearCpu(); return; }
            request = desired; // Coalesce changes made while this task was queued.
        }
        Result result;
        try {
            if (image == null) {
                try (var stream = source.open()) { image = ImageIO.read(stream); }
                if (image == null) throw new IOException("Unsupported dither image");
            }
            if (cancelled(ticket)) return;
            if (fitted == null || fittedWidth != request.width || fittedHeight != request.height
                    || fittedMode != request.options.fit()) {
                fitted = fit(image, request.width, request.height, request.options.fit());
                fittedWidth = request.width; fittedHeight = request.height; fittedMode = request.options.fit();
            }
            if (cancelled(ticket)) return;
            int[] pixels = Dither.apply(fitted, request.width, request.height, request.options, request.time);
            for (int i = 0; i < pixels.length; i++) {
                int argb = pixels[i];
                pixels[i] = argb & 0xff00ff00 | (argb >> 16 & 255) | (argb & 255) << 16;
            }
            result = new Result(request, pixels, null);
        } catch (IOException | RuntimeException error) { result = new Result(request, null, error); }
        synchronized (this) {
            running = false;
            if (closed || ticket != generation) { clearCpu(); return; }
            completed = request;
            if (request.equals(desired)) ready = result;
            schedule();
        }
    }

    private synchronized boolean cancelled(long ticket) {
        if (!closed && ticket == generation) return false;
        running = false;
        clearCpu();
        return true;
    }
    @Override public synchronized void close() {
        closed = true;
        generation++;
        desired = completed = null;
        ready = null;
        // A running worker owns its caches until it observes cancellation. No cross-thread mutation.
        if (!running) clearCpu();
    }
    private void clearCpu() { image = null; fitted = null; }

    static int[] fit(BufferedImage image, int width, int height, Dither.Fit fit) {
        var target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = switch (fit) {
                case COVER -> Math.max(width / (double) image.getWidth(), height / (double) image.getHeight());
                case CONTAIN -> Math.min(width / (double) image.getWidth(), height / (double) image.getHeight());
                default -> 1;
            };
            int dw = fit == Dither.Fit.FILL ? width : (int) Math.ceil(image.getWidth() * scale);
            int dh = fit == Dither.Fit.FILL ? height : (int) Math.ceil(image.getHeight() * scale);
            int dx = fit == Dither.Fit.FILL ? 0 : (int) Math.floor((width - dw) / 2d);
            int dy = fit == Dither.Fit.FILL ? 0 : (int) Math.floor((height - dh) / 2d);
            g.drawImage(image, dx, dy, dw, dh, null);
        } finally { g.dispose(); }
        // TYPE_INT_ARGB is already packed: avoid getRGB's additional full-image allocation/copy.
        return ((java.awt.image.DataBufferInt) target.getRaster().getDataBuffer()).getData();
    }
}
