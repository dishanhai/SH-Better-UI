// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import java.util.List;
import java.util.stream.IntStream;

/** Responsive player geometry, reusable without constructing a session or opening a device. */
public record OnlineMusicLayout(Bounds frame, Bounds navigation, Bounds header, Bounds content, Bounds transport,
                                boolean sidebar, boolean supported) {
    public static final int MIN_WIDTH = 208, MIN_HEIGHT = 240;
    public static OnlineMusicLayout of(Bounds frame) {
        var empty = new Bounds(frame.x(), frame.y(), 0, 0);
        if (frame.width() < MIN_WIDTH || frame.height() < MIN_HEIGHT)
            return new OnlineMusicLayout(frame, empty, empty, empty, empty, false, false);
        int x = frame.x() + 8, y = frame.y() + 8, width = frame.width() - 16;
        var transport = new Bounds(x, frame.bottom() - 76, width, 68);
        boolean sidebar = frame.width() >= 520 && frame.height() >= 264;
        var navigation = sidebar ? new Bounds(x, y, 96, transport.y() - y - 8) : new Bounds(x, y, width, 22);
        int mainX = sidebar ? navigation.right() + 12 : x;
        int mainY = sidebar ? y : navigation.bottom() + 6;
        var header = new Bounds(mainX, mainY, x + width - mainX, 26);
        var content = new Bounds(mainX, header.bottom() + 6, header.width(), transport.y() - header.bottom() - 14);
        return new OnlineMusicLayout(frame, navigation, header, content, transport, sidebar, true);
    }
    /** Results reserve a heading and a separate pager, never sharing a hit area with playback controls. */
    public List<Bounds> rows() {
        if (!supported) return List.of();
        int count = Math.min(20, Math.max(1, (content.height() - 44) / 27));
        return IntStream.range(0, count).mapToObj(i -> new Bounds(content.x(), content.y() + 20 + i * 27, content.width(), 24)).toList();
    }
    public Bounds pager() { return new Bounds(content.x(), content.bottom() - 20, content.width(), 20); }
}
