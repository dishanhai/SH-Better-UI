// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.glass.PixelRect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Transform;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.View;
import com.dishanhai.sh_better_ui.client.gui.layout.PixelAlignment;

import static com.dishanhai.sh_better_ui.client.visual.VisualEffectResult.*;

/** Screen GUI geometry and the visible part projected into top-left framebuffer pixels. */
public record VisualProjection(Rect area, Rect visible, PixelRect pixels, VisualEffectResult result) {
    public enum BoundsMode { RENDER, VISUAL }

    static boolean supportsFramebuffer(Transform transform) {
        return transform.xy() == 0 && transform.yx() == 0 && transform.xx() > 0 && transform.yy() > 0;
    }

    /** Physical target size and the unrounded extent of the native GUI projection. */
    public record Target(int width, int height, double guiWidth, double guiHeight) {
        public Target {
            if (width <= 0 || height <= 0 || !Double.isFinite(guiWidth) || guiWidth <= 0
                    || !Double.isFinite(guiHeight) || guiHeight <= 0) {
                throw new IllegalArgumentException("Invalid framebuffer projection");
            }
        }

        public PixelAlignment grid() { return new PixelAlignment(width / guiWidth, height / guiHeight); }

        public Rect viewport() { return new Rect(0, 0, guiWidth, guiHeight); }

        public VisualProjection project(View view, BoundsMode mode, Rect viewportClip) {
            Rect area = switch (mode) {
                case RENDER -> view.renderBounds();
                case VISUAL -> view.visualBounds();
            };
            Rect visible = area.intersect(viewportClip).intersect(viewport());
            if (view.clip() != null) visible = visible.intersect(view.clip());
            var result = area.empty() ? EMPTY_REGION : visible.empty() ? FULLY_CLIPPED
                    : supportsFramebuffer(view.transform()) ? DRAWN : UNSUPPORTED_TRANSFORM;
            return new VisualProjection(area, visible, result == DRAWN ? pixels(visible) : PixelRect.EMPTY, result);
        }

        public PixelRect pixels(Rect area) {
            return edges(area.intersect(viewport()));
        }

        // Preserve the original extent for a spatial reveal, including the part outside the target.
        PixelRect edges(Rect area) {
            if (area.empty()) return PixelRect.EMPTY;
            int left = floor(area.x() * width / guiWidth);
            int top = floor(area.y() * height / guiHeight);
            int right = ceil(area.right() * width / guiWidth);
            int bottom = ceil(area.bottom() * height / guiHeight);
            if ((long) right - left > Integer.MAX_VALUE || (long) bottom - top > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Framebuffer effect area exceeds the integer pixel range");
            }
            return right > left && bottom > top ? new PixelRect(left, top, right - left, bottom - top) : PixelRect.EMPTY;
        }

        Rect guiRect(PixelRect pixels) {
            return new Rect(pixels.getX() * guiWidth / width, pixels.getY() * guiHeight / height,
                    pixels.getW() * guiWidth / width, pixels.getH() * guiHeight / height);
        }

        PixelRect clamp(PixelRect area) {
            int left = Math.max(0, area.getX()), top = Math.max(0, area.getY());
            long right = Math.min(width, (long) area.getX() + area.getW());
            long bottom = Math.min(height, (long) area.getY() + area.getH());
            return right > left && bottom > top
                    ? new PixelRect(left, top, (int) right - left, (int) bottom - top) : PixelRect.EMPTY;
        }

        private static int floor(double n) { return (int) Math.floor(n); }
        private static int ceil(double n) { return (int) Math.ceil(n); }
    }
}
