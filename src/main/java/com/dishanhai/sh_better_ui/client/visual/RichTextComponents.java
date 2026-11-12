// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.LocalizedText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ParagraphBreak;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Client-owned adapter between Minecraft component runs and the passive rich document model. */
public final class RichTextComponents {
    private RichTextComponents() {}

    /**
     * A resolved document and its composed native styles. The immutable map includes native hover, click and insertion
     * metadata for a trusted host to interpret on the client thread. The adapter never dispatches actions.
     */
    public record Snapshot(RichDocument document, Map<UiId, Style> nativeStyles) {
        public Snapshot {
            Objects.requireNonNull(document, "document");
            nativeStyles = Map.copyOf(nativeStyles);
        }
    }

    /**
     * Visit Minecraft's logical styled runs in the current language. Keep the original Component in the host and
     * resolve again on language or source changes. IDs are stable for an unchanged run sequence.
     */
    public static Snapshot resolve(UiId prefix, Component source) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(source, "source");
        var document = RichText.document(prefix);
        var styles = new LinkedHashMap<UiId, Style>();
        source.visit((nativeStyle, text) -> {
            if (!text.isEmpty()) {
                var id = new UiId(prefix.namespace(), prefix.path() + "/node_" + styles.size());
                document.text(id, text, style(nativeStyle));
                styles.put(id, nativeStyle);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return new Snapshot(document.build(), styles);
    }

    /** Construct a native component so current-language translation and its fallback use Minecraft formatting. */
    public static MutableComponent localized(LocalizedText text) {
        Objects.requireNonNull(text, "text");
        return Component.translatableWithFallback(text.key(), text.fallback(), text.arguments().toArray())
                .setStyle(nativeStyle(text.style()));
    }

    /**
     * Export a fresh native text projection. Paragraph spacing becomes a newline and non-text nodes use their alt text.
     * Native interaction metadata is kept in Snapshot, outside this passive document projection.
     */
    public static MutableComponent toComponent(RichDocument document) {
        return toComponent(document, Map.of());
    }

    /** Restore native styles from a trusted snapshot. The receiving Screen controls any resulting actions. */
    public static MutableComponent toComponent(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return toComponent(snapshot.document(), snapshot.nativeStyles());
    }

    private static MutableComponent toComponent(RichDocument document, Map<UiId, Style> nativeStyles) {
        Objects.requireNonNull(document, "document");
        var result = Component.empty();
        for (var node : document.nodes()) {
            if (node instanceof TextRun run) {
                var style = nativeStyles.get(run.id());
                result.append(Component.literal(run.text()).setStyle(style != null ? style : nativeStyle(run.style())));
            } else if (node instanceof LocalizedText text) {
                result.append(localized(text));
            } else if (node instanceof ParagraphBreak) {
                result.append("\n");
            } else if (node instanceof ImageNode image) {
                result.append(image.altText());
            } else if (node instanceof RichObjectNode object) {
                result.append(object.altText());
            } else if (node instanceof RichExtensionNode extension) {
                result.append(extension.altText());
            } else {
                throw new IllegalArgumentException("Node kind has no native Component projection");
            }
        }
        return result;
    }

    /** Extract only the visual fields of a composed native style. */
    public static RichStyle style(Style style) {
        Objects.requireNonNull(style, "style");
        var color = style.getColor();
        var font = style.getFont();
        return new RichStyle(color == null ? null : color.getValue(), style.isBold(), style.isItalic(),
                style.isUnderlined(), style.isStrikethrough(), style.isObfuscated(),
                font.equals(Style.DEFAULT_FONT) ? null : new UiId(font.getNamespace(), font.getPath()));
    }

    /** A null font uses Minecraft's default font and its resource-pack glyph providers. */
    public static Style nativeStyle(RichStyle style) {
        Objects.requireNonNull(style, "style");
        if (style.equals(RichStyle.EMPTY)) return Style.EMPTY;
        var result = Style.EMPTY.withBold(style.bold()).withItalic(style.italic()).withUnderlined(style.underlined())
                .withStrikethrough(style.strikethrough()).withObfuscated(style.obfuscated());
        if (style.color() != null) result = result.withColor(style.color());
        if (style.font() != null) result = result.withFont(ResourceLocation.tryParse(style.font().toString()));
        return result;
    }
}
