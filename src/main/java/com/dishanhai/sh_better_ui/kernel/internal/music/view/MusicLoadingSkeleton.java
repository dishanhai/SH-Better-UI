// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Paint-only state for one list; observes existing futures without subscribing to or owning them. */
final class MusicLoadingSkeleton {
    private static final long DELAY_MS = 150;
    private static final long ENTER_MS = 110, EXIT_MS = 90;
    private final InterruptibleAnimation opacity = new InterruptibleAnimation(Spring.INDICATOR);
    private CompletableFuture<?> request;
    private long began;
    private boolean entered, exiting;

    float sample(CompletableFuture<?> pending, boolean populated, long nowMs, boolean reducedMotion) {
        if (pending != null && !pending.isDone()) {
            if (pending != request) {
                reset();
                request = pending;
                began = nowMs;
            }
            if (nowMs - began < DELAY_MS) return 0;
            if (!entered) {
                entered = true;
                opacity.blendTo(1f, nowMs, 0, ENTER_MS, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
            }
            return GuiMotion.clamp01(opacity.sample(nowMs, reducedMotion));
        }

        if (!populated || reducedMotion) {
            reset();
            return 0;
        }
        if (request != null) {
            request = null;
            if (entered) {
                opacity.blendTo(0f, nowMs, 0, EXIT_MS, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
                exiting = true;
            }
        }
        if (!exiting) return 0;
        float current = GuiMotion.clamp01(opacity.sample(nowMs));
        if (current == 0f) reset();
        return current;
    }

    void reset() {
        request = null;
        entered = exiting = false;
        opacity.snapTo(0f);
    }

    /** Aligned to actual row/control slots, with no widgets and no reserved space. */
    static List<UiSkeleton.Block> blocks(List<Bounds> rows, Bounds clip, boolean leadingIcon) {
        var blocks = new ArrayList<UiSkeleton.Block>();
        for (var row : rows) {
            int bottom = Math.min(row.bottom(), clip.bottom());
            int top = Math.max(row.y(), clip.y());
            int height = bottom - top;
            int usable = Math.min(row.right() - 10, clip.right()) - (row.x() + 10);
            if (height < 8 || usable < 8) continue;
            int x = row.x() + 10;
            if (leadingIcon && usable >= 48 && height >= 20) {
                blocks.add(new UiSkeleton.Block(new Bounds(x, top + (height - 14) / 2, 14, 14), 4));
                x += 26;
                usable -= 26;
            }
            boolean secondary = height >= 28;
            int y = top + (height - (secondary ? 15 : 5)) / 2;
            blocks.add(new UiSkeleton.Block(new Bounds(x, y, Math.max(5, usable * 77 / 100), 5)));
            if (secondary) blocks.add(new UiSkeleton.Block(new Bounds(x, y + 11, Math.max(5, usable * 48 / 100), 4)));
        }
        return List.copyOf(blocks);
    }
}
