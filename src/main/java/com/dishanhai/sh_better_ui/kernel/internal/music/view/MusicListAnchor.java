package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import java.util.List;
import java.util.function.Function;

/** A visible row occurrence and sub-row displacement, not the obsolete list length. */
record MusicListAnchor(String id, int occurrence, double position, long accountEpoch) {
    static <T> MusicListAnchor capture(List<T> rows, Function<T, String> key, double offset, long epoch) {
        int index = (int) offset;
        if (index < 0 || index >= rows.size()) return new MusicListAnchor(null, 0, offset, epoch);
        String id = key.apply(rows.get(index));
        int occurrence = 0;
        for (int i = 0; i < index; i++) if (id.equals(key.apply(rows.get(i)))) occurrence++;
        return new MusicListAnchor(id, occurrence, offset, epoch);
    }

    <T> double resolve(List<T> rows, Function<T, String> key, int capacity, long epoch) {
        if (accountEpoch != epoch || capacity <= 0) return 0;
        double result = position;
        if (id != null) {
            int remaining = occurrence;
            for (int i = 0; i < rows.size(); i++) {
                if (id.equals(key.apply(rows.get(i))) && remaining-- == 0) {
                    result = i + position - Math.floor(position);
                    break;
                }
            }
        }
        return Math.max(0, Math.min(Math.max(0, rows.size() - capacity), result));
    }
}
