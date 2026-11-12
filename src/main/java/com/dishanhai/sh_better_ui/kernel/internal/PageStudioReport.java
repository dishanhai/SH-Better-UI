// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.api.page.UiNode;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionState;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolTrace;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An on-demand inspection copy containing declarations and measurements only. */
record PageStudioReport(List<Page> pages, ClientPageNetwork.Diagnostics network, Component server) {
    static final boolean CAPTURE = Boolean.getBoolean("shb.pages.trace");
    private static final String PREFIX = "sh_better_ui.pages.studio.";

    PageStudioReport {
        pages = List.copyOf(pages);
    }

    record Page(UiId id, UiPageType type, UiVersion schema, boolean remote, String state, long revision,
                boolean synchronizedData, List<Row> tree, List<String> degradations, NativePageView.Inspection layout,
                UiActionState action, Bounds viewport, long renderSamples, long maxRenderNanos) {
        static Page registered(UiPageDefinition definition) {
            return create(definition, false, "REGISTERED", 0, true, List.of(), null,
                    UiActionState.IDLE, null, 0, 0);
        }

        static Page mounted(UiPageDefinition definition, PageService.State state, long revision, boolean synchronizedData,
                            boolean remote, NativePageView.Inspection layout, UiActionState action,
                            List<String> degradations, Bounds viewport,
                            long samples, long maxNanos) {
            return create(definition, remote, state.name(), revision, synchronizedData, degradations, layout,
                    action, viewport, samples, maxNanos);
        }

        private static Page create(UiPageDefinition definition, boolean remote, String state, long revision,
                                   boolean synchronizedData, List<String> degradations, NativePageView.Inspection layout,
                                   UiActionState action, Bounds viewport, long samples, long maxNanos) {
            var tree = new ArrayList<Row>();
            append(tree, definition.root(), definition, 0);
            return new Page(definition.id(), definition.type(), definition.schemaVersion(), remote, state,
                    revision, synchronizedData, List.copyOf(tree), List.copyOf(degradations), layout,
                    action, viewport, samples, maxNanos);
        }
    }

    record Row(int depth, String id, UiId type, List<String> properties, List<String> bindings, String action) {}

    private static void append(List<Row> rows, UiNode node, UiPageDefinition definition, int depth) {
        var properties = node.properties().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + " [" + entry.getValue().type() + "]").toList();
        var bindings = node.bindings().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + " ← " + entry.getValue() + " [" +
                        definition.dataSchema().fields().get(entry.getValue()).type() + "]").toList();
        String action = node.action().map(id -> id + " [" + definition.actions().get(id).policy() + "]").orElse("—");
        rows.add(new Row(depth, node.id(), node.typeId(), properties, bindings, action));
        for (var child : node.children()) append(rows, child, definition, depth + 1);
    }

    static Component label(String key, Object... values) { return Component.translatable(PREFIX + key, values); }

    List<Component> tree(Page page) {
        var lines = new ArrayList<Component>();
        if (page == null) return List.of(label("empty"));
        lines.add(label("page", page.id().toString(), page.type().name(), page.schema().toString()));
        lines.add(label("state", label(page.remote() ? "remote" : "local"), page.state(), page.revision(),
                yesNo(page.synchronizedData())));
        lines.add(label("degradation_count", page.degradations().size()));
        for (String degradation : page.degradations()) lines.add(Component.literal(degradation));
        for (var row : page.tree()) {
            lines.add(label("node", row.depth(), row.id(), row.type().toString()));
            if (!row.properties().isEmpty()) lines.add(label("properties", String.join(", ", row.properties())));
            if (!row.bindings().isEmpty()) lines.add(label("bindings", String.join(", ", row.bindings())));
            if (!row.action().equals("—")) lines.add(label("action", row.action()));
        }
        return lines;
    }

    List<Component> layout(Page page) {
        if (page == null || page.layout() == null || page.viewport() == null) return List.of(label("no_layout"));
        var lines = new ArrayList<Component>();
        lines.add(label("viewport", page.viewport().width(), page.viewport().height()));
        lines.add(label("focus", page.layout().focused().map(id -> focusPath(page.tree(), id)).orElse("—")));
        lines.add(label("scroll", yesNo(page.layout().scrollable()), Math.round(page.layout().scrollOffset())));
        lines.add(label("frames", page.renderSamples(), page.maxRenderNanos() / 1_000));
        var preferences = UiPreferences.configured();
        lines.add(label("preferences", yesNo(preferences.highContrast()), yesNo(preferences.reducedMotion())));
        lines.add(label("action", page.action().name()));
        for (var box : page.layout().widgets()) {
            lines.add(label("bounds", box.id(), box.x(), box.y(), box.width(), box.height(),
                    yesNo(box.visible()), yesNo(box.enabled())));
        }
        return lines;
    }

    private static String focusPath(List<Row> tree, String focusedId) {
        var path = new ArrayList<String>();
        for (var row : tree) {
            path.subList(row.depth(), path.size()).clear();
            path.add(row.id());
            if (row.id().equals(focusedId)) return String.join(" / ", path);
        }
        return focusedId;
    }

    List<Component> protocol() {
        var lines = new ArrayList<Component>();
        lines.add(label("connection", server, yesNo(network.negotiated()),
                network.sessionId().map(UUID::toString).orElse("—")));
        lines.add(label("permissions", network.permissions().toString(), yesNo(network.priorityModal()),
                yesNo(network.interactionAllowed())));
        lines.add(label("surfaces", network.mounted(), network.queued()));
        network.peer().ifPresent(peer -> {
            lines.add(label("peer", peer.protocolVersions().toString(), peer.schemaVersions().toString(),
                    peer.pageTypes().toString(), peer.maxMessageBytes()));
            peer.capabilities().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(capability ->
                    lines.add(label("capability", capability.getKey().toString(), capability.getValue().toString())));
        });
        if (!CAPTURE) lines.add(label("trace_disabled"));
        Map<UUID, Long> requests = new LinkedHashMap<>();
        for (UiProtocolTrace entry : network.trace()) {
            lines.add(label("trace", entry.sequence(), entry.direction().name(), entry.type().name(),
                    entry.messageBytes(), entry.error().map(Enum::name).orElse("—")));
            entry.requestId().ifPresent(id -> {
                if (entry.type() == UiProtocolMessage.Type.ACTION_REQUEST) requests.put(id, entry.timestampNanos());
                if (entry.type() == UiProtocolMessage.Type.ACTION_RESULT) {
                    var start = requests.remove(id);
                    if (start != null) lines.add(label("latency", (entry.timestampNanos() - start) / 1_000_000));
                }
            });
        }
        return lines;
    }

    private static Component yesNo(boolean value) { return label(value ? "yes" : "no"); }
}
