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
        boolean expanded = frame.width() >= 360 && frame.height() >= 340;
        int transportHeight = 64;
        var transport = new Bounds(x, frame.bottom() - transportHeight - 8, width, transportHeight);
        boolean sidebar = frame.width() >= 560 && frame.height() >= 264;
        var navigation = sidebar ? new Bounds(x, y, 112, transport.y() - y - 8) : new Bounds(x, y, width, 28);
        int mainX = sidebar ? navigation.right() + 12 : x;
        int mainY = sidebar ? y : navigation.bottom() + 6;
        var header = new Bounds(mainX, mainY, x + width - mainX, expanded ? 64 : 52);
        var content = new Bounds(mainX, header.bottom() + 6, header.width(), transport.y() - header.bottom() - 14);
        return new OnlineMusicLayout(frame, navigation, header, content, transport, sidebar, true);
    }
    /** Results reserve a heading and a separate pager, never sharing a hit area with playback controls. */
    public List<Bounds> rows() {
        if (!supported) return List.of();
        int stride = expanded() ? 38 : 32;
        int count = Math.min(20, Math.max(1, (content.height() - listHeadingHeight() - 24) / stride));
        return IntStream.range(0, count).mapToObj(i -> new Bounds(content.x(), content.y() + listHeadingHeight() + i * stride, content.width(), stride - 3)).toList();
    }
    /** Rich presentation is optional; minimum-size windows retain their compact controls. */
    public boolean expanded() { return supported && frame.width() >= 360 && frame.height() >= 340; }
    public Bounds searchBar() {
        return new Bounds(header.x(), header.bottom() - 24, header.width(), 22);
    }
    public int listHeadingHeight() { return content.height()<84?8:20; }
    public Bounds sourceBar() {
        int width=expanded()?Math.min(168,header.width()/2):header.width();
        return new Bounds(header.right()-width,header.y()+2,width,24);
    }
    public Bounds hero() { return new Bounds(header.x(),header.y(),Math.max(1,header.width()-sourceBar().width()-8),expanded()?34:0); }
    public Bounds cover() {
        return expanded() ? new Bounds(transport.x() + 8, transport.y() + 3, 32, 32)
                : new Bounds(transport.x(), transport.y(), 0, 0);
    }
    /** Exact native seek control area, shared by the view and constructor-contract tests. */
    public Bounds seekBar() {
        return new Bounds(transport.x() + 40, transport.y() + (MusicMiniPlayerLayout.stacked(transport.width()) ? 46 : 36), transport.width() - 80, 16);
    }
    public Bounds pager() { return new Bounds(content.x(), content.bottom() - 20, content.width(), 20); }
}
