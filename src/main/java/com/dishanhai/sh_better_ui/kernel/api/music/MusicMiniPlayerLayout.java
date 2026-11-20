// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import java.util.List;

/** Mini-player hit geometry. Compact screens reflow instead of hiding transport actions. */
public record MusicMiniPlayerLayout(Bounds metadata, Bounds previous, Bounds play, Bounds next,
                                   Bounds stop, Bounds lyrics, Bounds queue) {
    public static boolean stacked(int width) { return width < 320; }

    /** The enclosing player guarantees at least 192 x 64 logical pixels. */
    public static MusicMiniPlayerLayout of(Bounds transport, boolean artwork) {
        boolean stacked = stacked(transport.width());
        int actionWidth = 176;
        int left = stacked ? transport.x() + (transport.width() - actionWidth) / 2 : transport.right() - actionWidth - 6;
        int y = transport.y() + (stacked ? 18 : 5);
        int playHeight = stacked ? 28 : 34;
        int art = artwork ? 48 : 0;
        var metadata = stacked
                ? new Bounds(transport.x() + 4, transport.y(), transport.width() - 8, 16)
                : new Bounds(transport.x() + art, transport.y() + 3, left - transport.x() - art - 4, 32);
        return new MusicMiniPlayerLayout(metadata,
                new Bounds(left, y, 28, 28),
                new Bounds(left + 28, stacked ? y : y - 3, 36, playHeight),
                new Bounds(left + 64, y, 28, 28),
                new Bounds(left + 148, y, 28, 28),
                new Bounds(left + 92, y, 28, 28),
                new Bounds(left + 120, y, 28, 28));
    }

    /** The legacy stop slot is now used as the menu trigger by the default player. */
    public Bounds more() { return stop; }

    public List<Bounds> actions() { return List.of(previous, play, next, stop, lyrics, queue); }
}
