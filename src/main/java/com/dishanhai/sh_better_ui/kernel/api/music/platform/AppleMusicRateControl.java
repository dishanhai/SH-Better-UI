// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.platform;

/** Browser observations are UI feedback, not proof of Apple entitlement. Never carries credentials. */
final class AppleMusicRateControl {
    record Command(long id, double rate) {}
    private static final long TIMEOUT_NANOS = 5_000_000_000L;
    private long observedAt, requestedAt, sequence;
    private boolean supported, observed;
    private double rate = 1;
    private Command pending;
    private String result = "NONE";
    synchronized boolean supported(long now) {
        expire(now);
        return observed && supported && now - observedAt >= 0 && now - observedAt < TIMEOUT_NANOS;
    }
    synchronized double rate() { return rate; }
    synchronized String result(long now) { expire(now); return result; }
    synchronized Command command(long now) { return supported(now) ? pending : null; }
    synchronized void request(double value, long now) {
        validate(value);
        if (!supported(now)) throw new UnsupportedOperationException("Apple browser playback rate unavailable");
        pending = new Command(++sequence, value); requestedAt = now; result = "PENDING";
    }
    synchronized void observe(boolean capability, double actual, long ack, String status, long now) {
        if (!Double.isFinite(actual) || actual < 0 || actual > 16 || ack < 0
                || !(status.equals("OBSERVED") || status.equals("APPLIED") || status.equals("REJECTED")))
            throw new IllegalArgumentException("Invalid rate observation");
        if (ack > sequence) throw new IllegalArgumentException("Unknown rate command");
        // A backgrounded tab must not apply an old game request when its heartbeat resumes.
        expire(now);
        supported = capability; observed = true; observedAt = now; rate = actual;
        if (pending != null && ack == pending.id() && !status.equals("OBSERVED")) {
            result = capability && status.equals("APPLIED") && Math.abs(actual - pending.rate()) < .001 ? "APPLIED" : "REJECTED";
            pending = null;
        }
        if (!capability && pending != null) { pending = null; result = "REJECTED"; }
    }
    private void expire(long now) {
        if (pending != null && (now - requestedAt >= TIMEOUT_NANOS || now - observedAt >= TIMEOUT_NANOS)) {
            pending = null;
            result = "UNAVAILABLE";
        }
    }
    synchronized void clear() { observed = supported = false; pending = null; result = "UNAVAILABLE"; }
    static void validate(double rate) {
        if (!Double.isFinite(rate) || rate < .5 || rate > 2) throw new IllegalArgumentException("Rate must be 0.5–2.0");
    }
}
