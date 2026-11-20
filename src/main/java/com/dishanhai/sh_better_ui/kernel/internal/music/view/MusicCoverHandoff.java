// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import java.util.List;
import java.util.function.Function;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.ArtworkPalette;

/** Bounded two-slot cover dissolve. A pending or superseded request never becomes paintable. */
final class MusicCoverHandoff<T> {
    static final long DURATION_MS = 180;

    interface Slot<T> {
        Result<T> poll(String key);
        boolean owns(T image);
        void clear();
    }

    record Result<T>(T image, boolean loading) {}
    record Frame<T>(T base, T incoming, float mix, ArtworkPalette background) {}

    private final List<Slot<T>> slots;
    private final Function<T,ArtworkPalette> palette;
    private String settledKey, transitioningKey, requestedKey;
    private T base, incoming;
    private int baseSlot = -1, incomingSlot = -1;
    private boolean transitioning;
    private long lastSample;
    private double fraction;
    private boolean reversing;

    MusicCoverHandoff(Slot<T> first, Slot<T> second, Function<T, ArtworkPalette> palette) {
        slots = List.of(first, second);
        this.palette = palette;
    }

    Frame<T> sample(String key, long now, boolean still) {
        if (!owned(baseSlot, base) || !owned(incomingSlot, incoming)) clear();
        if (transitioning) {
            if (now > lastSample) {
                double dt = ((double)now - lastSample) / DURATION_MS;
                fraction = Math.max(0d, Math.min(1d, fraction + (reversing ? -dt : dt)));
                lastSample = now;
            }
            if (key.equals(settledKey)) reversing = true;
            else if (key.equals(transitioningKey)) reversing = false;
            if (reversing && (still || fraction <= 0)) abandonIncoming();
            else if (!reversing && (still || fraction >= 1)) finish();
        }

        if (!transitioning) {
            int free = baseSlot == 0 ? 1 : 0;
            if (!key.equals(settledKey)) {
                Result<T> result = key.isBlank() ? new Result<>(null, false) : slots.get(free).poll(key);
                requestedKey = key;
                if (!result.loading()) {
                    transitioningKey = key;
                    incoming = result.image();
                    incomingSlot = incoming == null ? -1 : free;
                    if (incomingSlot == -1) slots.get(free).clear();
                    lastSample = now;
                    fraction = 0;
                    reversing = false;
                    transitioning = true;
                    if (still || base == null && incoming == null) finish();
                }
            } else if (requestedKey != null && !key.equals(requestedKey)) {
                // Returning to the displayed track cancels an unpainted request without reloading its cover.
                slots.get(free).clear();
                requestedKey = key;
            }
        }

        double t = transitioning ? Math.max(0d, Math.min(1d, fraction)) : 0d;
        return new Frame<>(base, incoming, (float)(t * t * (3 - 2 * t)), base == null ? null : palette.apply(base));
    }

    private boolean owned(int slot, T image) { return image == null || slots.get(slot).owns(image); }

    private void finish() {
        if (baseSlot >= 0) slots.get(baseSlot).clear();
        settledKey = transitioningKey;
        base = incoming;
        baseSlot = incomingSlot;
        incoming = null;
        incomingSlot = -1;
        transitioning = false;
    }

    private void abandonIncoming() {
        if (incomingSlot >= 0) slots.get(incomingSlot).clear();
        incoming = null;
        incomingSlot = -1;
        requestedKey = settledKey;
        transitioning = false;
    }

    private void clear() {
        slots.forEach(Slot::clear);
        base = incoming = null;
        baseSlot = incomingSlot = -1;
        settledKey = requestedKey = transitioningKey = null;
        transitioning = false;
    }
}
