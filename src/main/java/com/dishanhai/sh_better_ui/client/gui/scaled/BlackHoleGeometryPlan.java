package com.dishanhai.sh_better_ui.client.gui.scaled;

/** CPU-only cache identity and progressive work sizing. Does not own GL objects. */
final class BlackHoleGeometryPlan {
    static final int PIXELS_PER_FRAME = 16_384;
    record Key(float size, float tilt, float inclination, float diskOuterRadius,
               int steps, float stepSize, int width, int height, float aspect) {}

    private BlackHoleGeometryPlan() {}

    /** Inputs are physical framebuffer pixels, not GUI-scaled logical coordinates. */
    static Key key(BlackHoleBackdropProfile profile, int physicalWidth, int physicalHeight, float aspect) {
        java.util.Objects.requireNonNull(profile);
        if (physicalWidth <= 0 || physicalHeight <= 0 || !Float.isFinite(aspect) || aspect <= 0)
            throw new IllegalArgumentException("Invalid physical extent/aspect");
        int cap = profile.quality().resolution();
        double scale = Math.min(1d, (double) cap / Math.max(physicalWidth, physicalHeight));
        int width = Math.max(1, (int) Math.round(physicalWidth * scale));
        int height = Math.max(1, (int) Math.round(physicalHeight * scale));
        return new Key(profile.size(), profile.tilt(), profile.inclination(), profile.diskOuterRadius(),
                profile.quality().steps(), profile.quality().stepSize(), width, height, aspect);
    }

    /** Never exceeds the supplied budget; caller must share budget across draws in a frame. */
    static int rowsPerFrame(int width, int remaining, int pixelBudget) {
        if (width <= 0 || remaining < 0 || pixelBudget < 0) throw new IllegalArgumentException();
        // Zero rows is intentional if a caller already spent this frame's shared budget.
        return Math.min(remaining, pixelBudget / width);
    }
    /** One renderer-global instance, shared by every owner/draw in the same host frame. */
    static final class Budget {
        private final int limit;
        private boolean initialized;
        private long frame;
        private int spent;
        Budget(int pixelBudget) {
            if (pixelBudget <= 0) throw new IllegalArgumentException();
            limit = pixelBudget;
        }
        int takeRows(long frameStamp, int width, int remainingRows) {
            if (width <= 0 || remainingRows < 0) throw new IllegalArgumentException();
            if (!initialized || frameStamp != frame) {
                initialized = true;
                frame = frameStamp;
                spent = 0;
            }
            int rows = rowsPerFrame(width, remainingRows, limit-spent);
            spent += rows*width; // bounded above by limit, including int overflow safety
            return rows;
        }
        void clear() { initialized = false; spent = 0; }
    }

}
