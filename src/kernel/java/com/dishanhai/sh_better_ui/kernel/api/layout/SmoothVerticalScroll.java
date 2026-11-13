// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;

import java.util.Objects;

/** Interruptible wheel and keyboard motion for a {@link VerticalScroll}. */
public final class SmoothVerticalScroll {
    private final VerticalScroll scroll;
    private final Spring spring;
    private double target;

    public SmoothVerticalScroll(VerticalScroll scroll) {
        this.scroll = Objects.requireNonNull(scroll, "scroll");
        target = scroll.offset();
        spring = new Spring(Spring.SCROLL, (float) target);
    }

    /** Retargets from the current trajectory, accumulating rapid successive inputs. */
    public boolean scrollBy(double delta, long nowMs, boolean reducedMotion) {
        return Double.isFinite(delta) && scrollTo(target + delta, nowMs, reducedMotion);
    }

    public boolean scrollTo(double value, long nowMs, boolean reducedMotion) {
        if (!Double.isFinite(value)) return false;
        double next = Math.max(0, Math.min(scroll.maxOffset(), value));
        boolean changed = next != target;
        target = next;
        if (reducedMotion) {
            scroll.scrollTo(target);
            spring.snap((float) target);
        } else {
            spring.setTarget((float) target, nowMs);
            scroll.scrollTo(spring.value());
        }
        return changed;
    }

    /** Samples once per rendered frame; returns whether the displayed offset changed. */
    public boolean advance(long nowMs, boolean reducedMotion) {
        target = Math.min(target, scroll.maxOffset());
        if (reducedMotion) {
            spring.snap((float) target);
        } else {
            if (spring.value() < 0 || spring.value() > scroll.maxOffset()) spring.snap((float) scroll.offset());
            if (spring.target() != (float) target) spring.setTarget((float) target, nowMs);
            spring.update(nowMs);
        }
        return scroll.scrollTo(spring.value());
    }

    /** Stops animation after a direct drag, cancellation, or external position change. */
    public void synchronize() {
        target = scroll.offset();
        spring.snap((float) target);
    }
}
