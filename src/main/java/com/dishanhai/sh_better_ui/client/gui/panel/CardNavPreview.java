// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.CardNavControl;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCardNav;
import com.dishanhai.sh_better_ui.kernel.api.controls.CardNavMotion;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/** Interactive CardNav sample: its links cycle the appearance without opening external URLs. */
final class CardNavPreview implements PreviewScene {
    private static final String KEY = "sh_better_ui.panel.developer.gallery.card_nav.";
    private static final String CODE = """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.CardNavControl;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiCardNav;
            import net.minecraft.network.chat.CommonComponents;
            import net.minecraft.network.chat.Component;
            import java.util.List;

            private UiCardNav nav;
            private void addCardNav(int x, int y, int width, int height) {
                var label = Component.translatable("sh_better_ui.panel.developer.gallery.sample.card_nav");
                var link = new CardNavControl.Link(Component.translatable("gui.done"), () -> {});
                nav = ShBetterUiApi.controls().cardNav(font, new Bounds(x, y, width, height),
                        label, null, List.of(new CardNavControl.Item(label, CommonComponents.EMPTY,
                        List.of(link))), link, CardNavControl.Options.DEFAULT,
                        ShBetterUiApi.settings().preferences(), open -> {});
                addRenderableWidget(nav);
            }
            // Before the host's normal pointer dispatch (including outside clicks):
            private void onHostPointerDown(double x, double y) { nav.pointerDown(x, y); }
            // Give focused nav.keyPressed(...) first refusal on Escape.
            // On removal or suspension: nav.cancelInteraction().
            // Controlled state: nav.setOpenController(() -> yourOpenState);
            // Update yourOpenState in the onOpenChange callback.
            """;

    private PreviewContext host;
    private UiCardNav widget;
    private boolean light, solid, hover, narrow, open;
    private CardNavMotion.Effect effect = CardNavMotion.Effect.RISE;
    private boolean mounted;
    private boolean introduced;

    static void registerInto(PreviewRegistry registry) {
        registry.register(new PreviewSpec("sh_better_ui:card_nav", PreviewSpec.Category.CONTROLS, 34,
                "sh_better_ui.panel.developer.gallery.sample.card_nav", 230, 0, CODE, CardNavPreview::new));
    }

    @Override
    public void layout(PreviewContext host) {
        suspend();
        this.host = host;
        var view = host.viewport();
        if (view.width() < 80 || view.height() < 30) return;
        mount();
    }

    private void mount() {
        var view = host.viewport();
        int width = Math.min(view.width(), narrow ? 220 : 400);
        var options = options();
        var items = List.of(
                new CardNavControl.Item(text("appearance"), text("appearance_description"),
                        List.of(link("theme", () -> light = !light), link("surface", () -> solid = !solid))),
                new CardNavControl.Item(text("motion"), text("motion_description"),
                        List.of(link("rise", () -> effect = CardNavMotion.Effect.RISE),
                                link("unfold", () -> effect = CardNavMotion.Effect.UNFOLD),
                                link("fade", () -> effect = CardNavMotion.Effect.FADE))),
                new CardNavControl.Item(text("interaction"), text("interaction_description"),
                        List.of(link("trigger", () -> hover = !hover), link("layout", () -> narrow = !narrow))));
        widget = ShBetterUiApi.controls().cardNav(host.font(),
                new Bounds(view.x() + (view.width() - width) / 2, view.y(), width, view.height()),
                text("name"), new CardNavControl.Link(text("logo"), () -> {}), items,
                new CardNavControl.Link(text("cta"), () -> widget.setOpen(true)), options, host.preferences(), value -> open = value);
        widget.setInputEnabled(() -> host.interactive() && mounted);
        host.add(widget);
        mounted = true;
        introduced = true;
    }

    private CardNavControl.Options options() {
        return new CardNavControl.Options(light ? CardNavControl.Theme.LIGHT : CardNavControl.Theme.DARK,
                solid ? CardNavControl.Surface.SOLID : CardNavControl.Surface.GLASS,
                hover ? CardNavControl.Trigger.HOVER : CardNavControl.Trigger.CLICK,
                new CardNavMotion.Settings(effect, .12, .2, 1), 88, 400, null, true, !introduced, open);
    }

    private CardNavControl.Link link(String key, Runnable change) {
        return new CardNavControl.Link(text(key), () -> {
            change.run();
            var view = host.viewport();
            int width = Math.min(view.width(), narrow ? 220 : 400);
            widget.setWidth(width);
            widget.setX(view.x() + (view.width() - width) / 2);
            widget.setOptions(options());
        });
    }

    private static Component text(String key) { return Component.translatable(KEY + key); }

    @Override
    public void suspend() {
        if (widget != null) { open = widget.isOpen(); widget.cancelInteraction(); }
        mounted = false;
        widget = null;
    }

    @Override
    public Supplier<? extends PreviewScene> saveForRemount() {
        boolean savedOpen = widget != null ? widget.isOpen() : open;
        boolean savedLight = light, savedSolid = solid, savedHover = hover, savedNarrow = narrow;
        var savedEffect = effect;
        return () -> {
            var scene = new CardNavPreview();
            scene.open = savedOpen;
            scene.light = savedLight; scene.solid = savedSolid;
            scene.hover = savedHover; scene.narrow = savedNarrow; scene.effect = savedEffect;
            return scene;
        };
    }
}
