// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import net.minecraft.network.chat.Component;
import java.util.Objects;

/** A passive bounded bar. The trusted host supplies the source ID, never the document payload. */
public final class BoundedMetricRenderer {
    private BoundedMetricRenderer() {}

    public static void render(RichTextExtensions.Context context, UiId sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        var metric = context.data(sourceId).flatMap(BoundedMetricSource::decode);
        var box = context.bounds();
        var canvas = context.graphics();
        if (box.width() <= 0 || box.height() <= 0) return;
        canvas.fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), 0xff263746);
        String label;
        if (metric.isPresent()) {
            double ratio = metric.get().ratio();
            int fill = (int) Math.round(Math.max(0, box.width() - 2) * ratio);
            if (fill > 0 && box.height() > 2) {
                canvas.fill(box.x() + 1, box.y() + 1, box.x() + 1 + fill,
                        box.y() + box.height() - 1, 0xff247c67);
            }
            label = Math.round(ratio * 100) + "%";
        } else {
            // A missing metric is not a fabricated zero. Keep a distinct warning stripe even
            // when the fragment is too small for its text; the host should also provide alt text.
            canvas.fill(box.x(), box.y(), box.x() + Math.min(2, box.width()),
                    box.y() + box.height(), 0xffefad64);
            label = "数据不可用 / N/A";
        }
        if (box.width() > 8 && box.height() >= context.font().lineHeight + 2) {
            String visible = context.font().plainSubstrByWidth(label, box.width() - 8);
            if (!visible.isEmpty()) canvas.drawText(Component.literal(visible), box.x() + 4,
                    box.y() + (box.height() - context.font().lineHeight) / 2, 0xfff2f7fa, false);
        }
    }
}
