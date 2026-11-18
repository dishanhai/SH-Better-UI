// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.kernel.api.richtext.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Predicate;

/** Passive structured-document reader. No network, file access, commands or embedded control dispatch.
 * The legacy renderer borrows static textures; animated rendering requires an explicitly owned media cache.
 */
public final class NativeRichDocumentView {
    private final Font font;
    private final RichLayoutSnapshot layout;
    private final Component narration;
    public NativeRichDocumentView(Font font, RichDocument source, int width) {
        this.font = Objects.requireNonNull(font);
        Objects.requireNonNull(source);
        if (source.nodes().size() > 8192) throw new IllegalArgumentException("Document has too many nodes");
        var nodes = new ArrayList<InlineNode>();
        long characters = 0;
        for (var node : source.nodes()) {
            InlineNode resolved = node;
            if (node instanceof LocalizedText text)
                resolved = new TextRun(text.id(), RichTextComponents.localized(text).getString(), text.style());
            else if (node instanceof RichObjectNode object)
                resolved = new TextRun(object.id(), object.altText());
            else if (node instanceof RichExtensionNode extension)
                resolved = new TextRun(extension.id(), extension.altText());
            if (resolved instanceof TextRun run) characters += run.text().length();
            else if (resolved instanceof ImageNode image) {
                characters += image.altText().length();
                if (image.intrinsicWidth() > 4096 || image.intrinsicHeight() > 4096)
                    throw new IllegalArgumentException("Image dimensions exceed document limits");
            } else if (resolved instanceof ParagraphBreak paragraph && paragraph.spacing() > 4096)
                throw new IllegalArgumentException("Paragraph spacing exceeds document limits");
            if (characters > 131072) throw new IllegalArgumentException("Document text exceeds limit");
            nodes.add(resolved);
        }
        var document = new RichDocument(nodes);
        narration = RichTextComponents.toComponent(document);
        layout = RichTextLayout.layout(document, Math.max(1, Math.min(4096, width)), new RichTextLayout.FontMetrics() {
            public int ascent() { return font.lineHeight; }
            public int descent() { return 3; }
            public int width(String text) { return font.width(text); }
            public int width(TextRun run, String text) { return font.width(component(run, text)); }
        });
    }
    public RichLayoutSnapshot layout() { return layout; }
    public Component narration() { return narration.copy(); }
    public int height() { return layout.height(); }
    private static Component component(TextRun run, String text) {
        return Component.literal(text).setStyle(RichTextComponents.nativeStyle(run.style()));
    }
    /** Only static PNG resource-pack assets, never URL or absolute filesystem locations. */
    public static boolean isStaticImage(ImageNode image) {
        return image.texture().path().startsWith("textures/") && image.texture().path().endsWith(".png");
    }
    /** Host owns scissor and resource lookup. Lookup is repeated for visible images, avoiding stale reload caches. */
    public void render(GuiGraphicsExtractor graphics, int x, int y, int clipTop, int clipBottom,
                       Predicate<Identifier> available) {
        render(graphics, x, y, clipTop, clipBottom, available, null, 0, true);
    }
    /** Resource-pack media only; the host closes the owner. Decoding and GPU uploads never run here. */
    public void render(GuiGraphicsExtractor graphics, int x, int y, int clipTop, int clipBottom,
                       Predicate<Identifier> available, NativeRichMedia media, long nowMs, boolean reducedMotion) {
        if (media != null) media.beginVisibleFrame();
        try {
            for (var line : layout.visibleLines(clipTop - y, clipBottom - y)) {
                for (var fragment : line.fragments()) {
                    var box = fragment.bounds();
                    int left = x + box.x(), top = y + box.y();
                    if (fragment.node() instanceof TextRun run) {
                        graphics.text(font, component(run, fragment.text()), left, top, 0xffe0e6ef, false);
                    } else if (fragment.node() instanceof ImageNode image) {
                        var texture = Identifier.parse(image.texture().toString());
                        NativeRichMedia.Region region = null;
                        if (media != null && isResourceMedia(image)) {
                            media.request(texture);
                            region = media.region(texture, nowMs, reducedMotion);
                        }
                        if (region != null) {
                            graphics.blit(region.texture(), left, top, left + box.width(), top + box.height(),
                                    region.u0(), region.u1(), region.v0(), region.v1());
                        } else if (media == null && isStaticImage(image) && available.test(texture)) {
                            graphics.blit(texture, left, top, left + box.width(), top + box.height(), 0, 1, 0, 1);
                        } else {
                            graphics.fill(left, top, left + box.width(), top + box.height(), 0xff303b49);
                            // Clip the alternative text to the reserved box, without changing document geometry.
                            graphics.enableScissor(left, Math.max(top, clipTop), left + box.width(), Math.min(top + box.height(), clipBottom));
                            graphics.text(font, font.plainSubstrByWidth(image.altText(), box.width()), left, top, 0xffc5cfda, false);
                            graphics.disableScissor();
                        }
                    }
                }
            }
        } finally { if (media != null) media.endVisibleFrame(); }
    }
    public static boolean isResourceMedia(ImageNode image) {
        String path=image.texture().path();
        return path.startsWith("textures/") && (path.endsWith(".png") || path.endsWith(".gif") || path.endsWith(".apng"));
    }
}
