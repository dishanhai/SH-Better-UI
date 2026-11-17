// Author: dishanhai. Uses only the existing bounded media artwork, never captures audio or fetches URLs.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.music.MusicArtworkTexture;
import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import com.dishanhai.sh_better_ui.theme.palette.ThemePalette;
import com.dishanhai.sh_better_ui.theme.palette.ThemePaletteGenerator;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/** At most one decoding job and one immutable result per host, with stale-result rejection. */
final class MuoniumCoverPalette implements AutoCloseable {
    private ScheduledThreadPoolExecutor executor;
    private Future<?> pending;
    private String requested;
    private ThemePalette palette;
    private long generation;

    synchronized ThemePalette sample(MusicSnapshot.Artwork artwork) {
        String id = artwork == null ? null : artwork.id();
        if (Objects.equals(id, requested)) return palette;
        clear(); requested = id;
        if (artwork == null) return null;
        if (executor == null) {
            executor = new ScheduledThreadPoolExecutor(1, task -> {
                Thread thread = new Thread(task, "shb-fluid-cover"); thread.setDaemon(true); return thread;
            });
            executor.setRemoveOnCancelPolicy(true);
        }
        long ticket = generation;
        pending = executor.submit(() -> {
            ThemePalette result = read(artwork);
            synchronized (this) { if (ticket == generation) palette = result; }
        });
        return null;
    }

    private static ThemePalette read(MusicSnapshot.Artwork artwork) {
        try (var image = MusicArtworkTexture.readArtwork(artwork)) {
            if (image == null || Thread.currentThread().isInterrupted()) return null;
            int width = image.getWidth(), height = image.getHeight();
            // Decoder already limits dimensions to 240x240; sample at most 4096 pixels.
            int columns = Math.min(width, 64), rows = Math.min(height, 64);
            int[] pixels = new int[columns * rows];
            for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
                int abgr = image.getPixelRGBA(x * width / columns, y * height / rows);
                pixels[y * columns + x] = (abgr & 0xff00ff00) | ((abgr >>> 16) & 255) | ((abgr & 255) << 16);
            }
            var generator = new ThemePaletteGenerator();
            return generator.fromSeed(generator.extract(columns, rows, pixels), false);
        } catch (java.io.IOException | RuntimeException unavailable) { return null; }
    }

    private void clear() {
        generation++;
        if (pending != null) pending.cancel(true);
        pending = null; palette = null; requested = null;
    }
    @Override public synchronized void close() {
        clear();
        if (executor != null) executor.shutdownNow();
        executor = null;
    }
}
