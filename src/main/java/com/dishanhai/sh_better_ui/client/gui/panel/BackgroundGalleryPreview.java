// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.BackgroundLayer;
import com.dishanhai.sh_better_ui.client.api.BackgroundRegistry;
import com.dishanhai.sh_better_ui.client.api.BackgroundSpec;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.*;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/** A real, independently owned renderer for every registered background, including addons. */
final class BackgroundGalleryPreview implements PreviewScene {
    private static final String KEYS = "sh_better_ui.panel.developer.gallery.";
    private final BackgroundRegistry registry;
    private final String id;
    private final BackgroundLayer layer;
    private PreviewContext host;
    private Bounds canvas;
    private UiMouseState previousMouse;

    BackgroundGalleryPreview(BackgroundRegistry registry, String id) {
        this.registry = registry;
        this.id = id;
        layer = registry.layer(GraphicIcon.TEMPLATE.texture());
    }

    static void registerInto(PreviewRegistry previews) {
        previews.registerSource(catalog(ShBetterUiApi.backgrounds()));
    }

    static Supplier<List<PreviewSpec>> catalog(BackgroundRegistry registry) {
        return new Supplier<>() {
            private List<BackgroundSpec> previous;
            private List<PreviewSpec> entries = List.of();
            private java.util.Map<BackgroundSpec, PreviewSpec> descriptors = new java.util.IdentityHashMap<>();
            private int nextOrder;
            @Override public List<PreviewSpec> get() {
                var current = registry.entries();
                if (previous != current) {
                    previous = current;
                    var next = new java.util.IdentityHashMap<BackgroundSpec, PreviewSpec>();
                    entries = current.stream().map(spec -> {
                        var descriptor = descriptors.get(spec);
                        if (descriptor == null) descriptor = new PreviewSpec(previewId(spec.id()),
                                PreviewSpec.Category.BACKGROUNDS, nextOrder++, spec.titleKey(),
                                146, 0, "", () -> new BackgroundGalleryPreview(registry, spec.id()));
                        next.put(spec, descriptor);
                        return descriptor;
                    }).toList();
                    descriptors = next;
                }
                return entries;
            }
        };
    }

    static String previewId(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "sh_better_ui" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        // Encode every character to keep legacy camelCase and namespaced paths collision-free.
        var encoded = new StringBuilder();
        for (char c : path.toCharArray()) {
            encoded.append(Integer.toHexString(c)).append('_');
        }
        return namespace + ":background/" + encoded;
    }

    @Override public void layout(PreviewContext host) {
        this.host = host;
        var area = host.viewport();
        canvas = new Bounds(area.x(), area.y(), area.width(), Math.max(0, area.height() - 28));
        int buttonWidth = Math.min(140, area.width());
        host.add(new UiButton(area.x() + (area.width() - buttonWidth) / 2, area.bottom() - 22,
                buttonWidth, 22, Component.translatable(KEYS + "background.restart"),
                ignored -> { if (host.interactive()) suspend(); }, host.preferences(), false));
    }

    @Override public void render(UiFrame frame) {
        if (canvas == null || canvas.width() <= 0 || canvas.height() <= 0) { suspend(); return; }
        var graphics = frame.graphics();
        if (id.equals(MuoniumFluidBackdrop.ID) && !frame.preferences().highContrast()) TransparencyPreview.draw(graphics, canvas);
        else DarkModeTheme.background(graphics, canvas.x(), canvas.y(), canvas.width(), canvas.height());
        var mouse = host.interactive() ? frame.mouse() : null;
        double x = mouse == null ? -1 : mouse.x(), y = mouse == null ? -1 : mouse.y();
        graphics.enableScissor(canvas.x(), canvas.y(), canvas.right(), canvas.bottom());
        try {
            layer.draw(id, graphics, host.font(), canvas, mouse, x, y, frame.nowMs(), frame.preferences());
            if (mouse != null && (mouse.pressedSince(previousMouse) & 1) != 0)
                layer.click(id, canvas, x, y, frame.nowMs());
            if (frame.preferences().highContrast()) {
                graphics.drawCenteredString(host.font(), Component.translatable(KEYS + "background.high_contrast"),
                        canvas.x() + canvas.width() / 2, canvas.y() + canvas.height() / 2, UiTheme.current().text());
            }
        } finally { graphics.disableScissor(); previousMouse = mouse; }
    }

    @Override public void suspend() { previousMouse = null; layer.close(); }
    @Override public void close() { suspend(); }
}
