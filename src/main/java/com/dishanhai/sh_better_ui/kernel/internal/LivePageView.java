// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.*;
import com.dishanhai.sh_better_ui.kernel.api.live.NotificationService;
import com.dishanhai.sh_better_ui.kernel.api.page.UiNode;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValueType;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Adapts a passive declaration to the kernel's existing live activity and notification surfaces. */
final class LivePageView {
    private LivePageView() {}

    static void validate(UiPageDefinition page) {
        var root = page.root();
        var expected = page.type() == UiPageType.HUD ? "hud_panel" : "notification";
        var title = page.options().title();
        if (!root.typeId().path().equals(expected) || !root.properties().isEmpty() || !root.bindings().isEmpty()
                || !page.actions().isEmpty() || title.isBlank() || title.length() > 512) {
            throw new IllegalArgumentException("Unsupported live page definition");
        }
        var children = root.children();
        if (children.size() > 2 || children.stream().map(UiNode::typeId).distinct().count() != children.size()) {
            throw new IllegalArgumentException("A live page supports one detail and one progress value");
        }
        for (var child : children) {
            String property = switch (child.typeId().path()) {
                case "text" -> "text";
                case "progress_bar" -> "value";
                default -> throw new IllegalArgumentException("Unsupported live page component");
            };
            if (!Set.of(property).containsAll(child.properties().keySet())
                    || !Set.of(property).containsAll(child.bindings().keySet())) {
                throw new IllegalArgumentException("Unsupported live page property");
            }
            if (child.properties().get(property) instanceof UiValue.Text text && text.value().length() > 512) {
                throw new IllegalArgumentException("Live page detail exceeds 512 characters");
            }
            var field = child.bindings().get(property);
            if (field != null) {
                var schema = page.dataSchema().fields().get(field);
                if (schema.type() == UiValueType.STRING && schema.maxSize() > 512) {
                    throw new IllegalArgumentException("Live page detail schema exceeds 512 characters");
                }
            }
        }
    }

    static Mount mount(LiveSurfaceRuntime runtime, ModuleContext module, ScopeHandle scope, ClientPages.OpenPage page) {
        var id = new UiId(module.scope().owner().namespace(), "live_pages/" + page.id);
        var initial = content(page.definition, page.snapshot().data());
        Consumer<Snapshot> presentation = snapshot -> {
            if (snapshot.lifecycle().terminal()) page.close();
        };
        if (page.definition.type() == UiPageType.HUD) {
            var handle = runtime.publish(module, scope,
                    new ActivityService.Request(id, initial, Priority.LOW, Duration.ZERO, true, null));
            scope.track(handle.observe(presentation));
            return new Mount(fields -> handle.update(content(page.definition, fields)),
                    () -> runtime.hasPainted(handle.snapshot()));
        }
        var handle = runtime.publish(module, scope,
                new NotificationService.Request(id, initial, Priority.NORMAL, Duration.ZERO, null,
                        NotificationService.Merge.KEEP, null));
        scope.track(handle.observe(presentation));
        return new Mount(fields -> handle.update(content(page.definition, fields)),
                () -> runtime.hasPainted(handle.snapshot()));
    }

    record Mount(Consumer<Map<String, UiValue>> update, BooleanSupplier painted) {}

    private static Content content(UiPageDefinition page, Map<String, UiValue> data) {
        String detail = "";
        double progress = -1;
        for (var child : page.root().children()) {
            if (child.typeId().path().equals("text")) {
                detail = switchValue(field(child, "text", data));
            } else if (field(child, "value", data) instanceof UiValue.Decimal decimal) {
                progress = decimal.value();
            }
        }
        return new Content(Text.literal(page.options().title()), Text.literal(detail), null, progress,
                false, List.of(), List.of());
    }

    private static UiValue field(UiNode node, String property, Map<String, UiValue> data) {
        String binding = node.bindings().get(property);
        return binding == null ? node.properties().get(property) : data.getOrDefault(binding, node.properties().get(property));
    }

    private static String switchValue(UiValue value) {
        if (value instanceof UiValue.Text text) return text.value();
        if (value instanceof UiValue.Int number) return Integer.toString(number.value());
        if (value instanceof UiValue.LongInt number) return Long.toString(number.value());
        if (value instanceof UiValue.Decimal number) return Double.toString(number.value());
        return "";
    }
}
