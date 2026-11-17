// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.*;

/**
 * Trusted host composition of passive Markdown and explicit visual nodes into one RichTextView.
 * Text never registers renderers, fetches media or constructs controls. Tables use an accessible
 * labelled-row projection; use MarkdownView when a column-grid presentation is required.
 */
public final class RichMarkdownContent {
    public static final int MAX_NODES = 8192;
    private RichMarkdownContent() {}

    public static Builder builder(UiId prefix) { return new Builder(prefix); }

    public static final class Builder {
        private final UiId prefix;
        private final List<InlineNode> nodes = new ArrayList<>();
        private final Map<UiId, Style> styles = new LinkedHashMap<>();
        private final Set<UiId> ids = new HashSet<>();
        private int sourceLength;
        private long projectedLength;
        private int section;

        private Builder(UiId prefix) { this.prefix = Objects.requireNonNull(prefix); }

        /** Appends a bounded section atomically; IDs remain stable for the same composition order. */
        public Builder markdown(String source) {
            Objects.requireNonNull(source);
            if (source.length() > RichMarkdown.MAX_LENGTH - sourceLength)
                throw new IllegalArgumentException("Combined Markdown source is too large");
            var parsed = RichMarkdown.parse(source);
            // Bound expansion BEFORE creating labelled rows/components. Include separators and markers.
            long projected = source.length() + (long) parsed.blocks().size() * 32;
            for (var block : parsed.blocks()) if (block instanceof RichMarkdown.Table table) {
                long headers = table.header().stream().mapToLong(String::length).sum();
                projected += (headers + table.header().size() * 3L) * table.rows().size();
            }
            if (projected > 131072 - projectedLength)
                throw new IllegalArgumentException("Expanded Markdown content is too large");
            var temporary = new ArrayList<InlineNode>();
            var nativeStyles = new LinkedHashMap<UiId, Style>();
            var api = ShBetterUiApi.markdown();
            int row = 0;
            for (var block : parsed.blocks()) {
                var lines = new ArrayList<Component>();
                if (block instanceof RichMarkdown.Heading h)
                    lines.add(api.parseInline(h.text()).withStyle(Style.EMPTY.withBold(true)));
                else if (block instanceof RichMarkdown.Paragraph p) lines.add(api.parseInline(p.text()));
                else if (block instanceof RichMarkdown.Quote q)
                    lines.add(Component.literal("│ ").append(api.parseInline(q.text())));
                else if (block instanceof RichMarkdown.ListItem item) {
                    String marker = item.checked() == null ? item.marker() : item.checked() ? "[x]" : "[ ]";
                    lines.add(Component.literal(" ".repeat(Math.min(16, item.indent())) + marker + " ")
                            .append(api.parseInline(item.text())));
                } else if (block instanceof RichMarkdown.CodeBlock code)
                    lines.add(Component.literal(code.text()));
                else if (block instanceof RichMarkdown.ThematicBreak) lines.add(Component.literal("────────"));
                else if (block instanceof RichMarkdown.Table table) {
                    // Never lose an empty table's headers. No width-dependent column overlap.
                    if (table.rows().isEmpty()) for (var header : table.header()) lines.add(api.parseInline(header));
                    for (var cells : table.rows()) {
                        var record = Component.empty();
                        for (int col = 0; col < cells.size(); col++) {
                            if (col != 0) record.append("\n");
                            record.append(api.parseInline(table.header().get(col)).withStyle(Style.EMPTY.withBold(true)))
                                    .append(": ").append(api.parseInline(cells.get(col)));
                        }
                        lines.add(record);
                    }
                }
                for (var line : lines) {
                    var resolved = RichTextComponents.resolve(id("markdown_" + section + "/row_" + row++), line);
                    temporary.addAll(resolved.document().nodes());
                    resolved.nativeStyles().forEach((key, style) -> nativeStyles.put(key, UiRichText.literalStyle(style)));
                    temporary.add(new ParagraphBreak(id("markdown_" + section + "/break_" + row), 4));
                    if (temporary.size() > MAX_NODES) throw new IllegalArgumentException("Too many mixed content nodes");
                }
            }
            append(temporary, nativeStyles);
            sourceLength += source.length();
            projectedLength += projected;
            section++;
            return this;
        }

        /** Visual resources and controls are host-supplied typed nodes, never Markdown syntax. */
        public Builder node(InlineNode node) { append(List.of(node), Map.of()); return this; }
        public Builder document(RichDocument document) { append(document.nodes(), Map.of()); return this; }
        public Builder snapshot(RichTextComponents.Snapshot snapshot) {
            append(snapshot.document().nodes(), snapshot.nativeStyles()); return this;
        }
        public RichTextComponents.Snapshot build() {
            return new RichTextComponents.Snapshot(new RichDocument(nodes), styles);
        }
        private UiId id(String suffix) { return new UiId(prefix.namespace(), prefix.path() + "/" + suffix); }
        private void append(List<InlineNode> added, Map<UiId, Style> metadata) {
            if (added.size() > MAX_NODES - nodes.size()) throw new IllegalArgumentException("Too many mixed content nodes");
            var next = new HashSet<>(ids);
            var addedIds = new HashSet<UiId>();
            for (var node : added) {
                if (!next.add(node.id())) throw new IllegalArgumentException("Duplicate node: " + node.id());
                addedIds.add(node.id());
            }
            for (var id : metadata.keySet()) if (!addedIds.contains(id))
                throw new IllegalArgumentException("Orphan native style: " + id);
            nodes.addAll(added); styles.putAll(metadata); ids.clear(); ids.addAll(next);
        }
    }
}
