// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import java.util.Objects;

/** Bounded editor value; rejected updates leave the last accepted text intact. */
public final class CodeEditorValue {
    private final int maximumLength;
    private String value = "";
    public CodeEditorValue(int maximumLength) {
        if (maximumLength < 1) throw new IllegalArgumentException("Invalid maximum length");
        this.maximumLength = maximumLength;
    }
    public String get() { return value; }
    public boolean replace(String candidate) {
        Objects.requireNonNull(candidate);
        if (candidate.length() > maximumLength) return false;
        value = candidate;
        return true;
    }
    public boolean replaceRange(int start, int end, String insert) {
        Objects.requireNonNull(insert);
        Objects.checkFromToIndex(start, end, value.length());
        if ((long) value.length() - (end - start) + insert.length() > maximumLength) return false;
        value = value.substring(0, start) + insert + value.substring(end);
        return true;
    }
}
