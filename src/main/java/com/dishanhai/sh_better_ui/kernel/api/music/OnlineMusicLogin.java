// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.BitSet;

/** A cancellable QR login. Credentials are applied to the owning player, never exposed in snapshots. */
public interface OnlineMusicLogin extends AutoCloseable {
    enum State { CREATING, WAITING, SCANNED, CONFIRMED, EXPIRED, FAILED, CANCELLED }
    record Snapshot(State state, Image image) {}

    /** Immutable monochrome QR image, including its white quiet zone. No renderer or native texture required. */
    final class Image {
        private final int width, height;
        private final BitSet dark;
        public Image(int width, int height, BitSet dark) {
            if (width < 1 || height < 1 || width > 512 || height > 512 || dark == null || dark.length() > width * height)
                throw new IllegalArgumentException("Invalid QR image");
            this.width = width; this.height = height; this.dark = (BitSet) dark.clone();
        }
        public int width() { return width; }
        public int height() { return height; }
        public boolean dark(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height && dark.get(y * width + x); }
    }

    Snapshot snapshot();
    /** Cancels outstanding creation/polling and discards the QR image. Does not sign out a completed login. */
    @Override void close();
}
