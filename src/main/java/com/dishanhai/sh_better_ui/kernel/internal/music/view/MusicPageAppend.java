// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import java.util.ArrayList;
import java.util.List;

final class MusicPageAppend {
    private MusicPageAppend() {}
    static <T> List<T> append(List<T> before, List<T> page) {
        if (page.isEmpty()) return before;
        var merged = new ArrayList<T>(before.size() + page.size());
        merged.addAll(before);
        merged.addAll(page);
        return List.copyOf(merged);
    }
}
