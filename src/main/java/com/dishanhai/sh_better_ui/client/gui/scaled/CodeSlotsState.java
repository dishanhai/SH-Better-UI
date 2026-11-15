// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/** Digits and transition timing shared by the native widget implementations. */
final class CodeSlotsState {
    static final long SETTLE_MS = 300;
    static final long CASCADE_MS = 20;
    private final char[] digits;
    private final char[] outgoing;
    private final long[] transition;
    private int filled;
    private int selected;
    private UiCodeSlots.Status status = UiCodeSlots.Status.IDLE;
    private long statusSince;
    private long rejectUntil;

    Consumer<String> onChange = code -> {};
    Consumer<String> onComplete = code -> {};

    CodeSlotsState(int length) {
        if (length < 1) throw new IllegalArgumentException("length must be positive");
        digits = new char[length];
        outgoing = new char[length];
        transition = new long[length];
        Arrays.fill(transition, -SETTLE_MS);
    }

    int length() { return digits.length; }
    int filled() { return filled; }
    int selected() { return selected; }
    char digit(int slot) { return digits[slot]; }
    char displayed(int slot) { return digits[slot] != 0 ? digits[slot] : outgoing[slot]; }
    UiCodeSlots.Status status() { return status; }
    long statusAge(long now) { return now - statusSince; }
    long transitionAge(int slot, long now) { return now - transition[slot]; }
    boolean accepting() { return rejectUntil == 0 && status != UiCodeSlots.Status.SUCCESS; }

    String value() {
        var code = new StringBuilder(filled);
        for (char digit : digits) if (digit != 0) code.append(digit);
        return code.toString();
    }

    boolean select(int slot) {
        if (!accepting()) return false;
        selected = UiMath.clamp(slot, 0, length() - 1);
        return true;
    }

    boolean insert(String raw, long now) {
        if (!accepting()) return false;
        int start = selected;
        int slot = start;
        boolean wasFull = filled == length();
        String old = value();
        for (int i = 0; i < raw.length() && slot < length(); i++) {
            char digit = raw.charAt(i);
            if (digit < '0' || digit > '9') continue;
            if (digits[slot] == 0) filled++;
            digits[slot] = digit;
            outgoing[slot] = 0;
            transition[slot] = now + (slot - start) * CASCADE_MS;
            slot++;
        }
        if (slot == start) return false;
        status = UiCodeSlots.Status.IDLE;
        selected = Math.min(slot, length() - 1);
        String code = value();
        boolean completed = !wasFull && filled == length();
        if (!old.equals(code)) onChange.accept(code);
        if (completed && filled == length() && code.equals(value()) && accepting()) onComplete.accept(code);
        return true;
    }

    boolean delete(boolean backspace, long now) {
        if (!accepting()) return false;
        if (backspace && digits[selected] == 0 && selected > 0) selected--;
        if (digits[selected] != 0) {
            status = UiCodeSlots.Status.IDLE;
            outgoing[selected] = digits[selected];
            digits[selected] = 0;
            transition[selected] = now;
            filled--;
            onChange.accept(value());
        }
        return true;
    }

    /** An authoritative update cancels any previous drain and does not submit the code. */
    void setValue(String value, long now) {
        Objects.requireNonNull(value, "value");
        int next = 0;
        int changed = 0;
        filled = 0;
        status = UiCodeSlots.Status.IDLE;
        rejectUntil = 0;
        for (int slot = 0; slot < length(); slot++) {
            char digit = 0;
            while (next < value.length()) {
                char candidate = value.charAt(next++);
                if (candidate >= '0' && candidate <= '9') {
                    digit = candidate;
                    break;
                }
            }
            if (digit != digits[slot] || outgoing[slot] != 0) {
                outgoing[slot] = digit == 0 ? digits[slot] : 0;
                digits[slot] = digit;
                transition[slot] = now + changed++ * CASCADE_MS;
            }
            if (digit != 0) filled++;
        }
        selected = Math.min(filled, length() - 1);
    }

    void setStatus(UiCodeSlots.Status next, long now, boolean reducedMotion) {
        if (status == Objects.requireNonNull(next, "status")) return;
        if (rejectUntil != 0) clearRejected();
        status = next;
        statusSince = now;
        if (next != UiCodeSlots.Status.ERROR || filled == 0) return;
        int place = 0;
        for (int slot = length() - 1; slot >= 0; slot--) {
            if (digits[slot] == 0) continue;
            outgoing[slot] = digits[slot];
            transition[slot] = now + place++ * CASCADE_MS;
        }
        rejectUntil = now + (reducedMotion ? SETTLE_MS : (place - 1) * CASCADE_MS + SETTLE_MS);
    }

    void advance(long now) {
        if (rejectUntil != 0 && now >= rejectUntil) clearRejected();
    }

    private void clearRejected() {
        Arrays.fill(digits, (char) 0);
        Arrays.fill(outgoing, (char) 0);
        rejectUntil = 0;
        filled = 0;
        selected = 0;
        onChange.accept("");
    }
}
