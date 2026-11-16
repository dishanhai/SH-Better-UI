// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.*;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.client.theme.MinecraftThemes;
import com.dishanhai.sh_better_ui.theme.ThemePreviewSession;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Live registered themes, with isolated native interactive controls rather than palette swatches. */
final class GlobalThemeGalleryPreview implements PreviewScene {
    private static final String KEY = "sh_better_ui.panel.developer.themes.";
    private final ThemePreviewSession session;
    private final List<UiButton> samples = new ArrayList<>();
    private PreviewContext host;
    private UiButton chooser;
    private boolean selected;
    private boolean toggleSelected = true;

    private GlobalThemeGalleryPreview(String theme) { session = new ThemePreviewSession(theme, candidate -> !MinecraftThemes.isConfiguredPalette(candidate.id())); }

    static void registerInto(PreviewRegistry registry) {
        registry.register(new PreviewSpec("sh_better_ui:global_themes", PreviewSpec.Category.THEMES, 0,
                KEY + "title", 140, 0, "", () -> new GlobalThemeGalleryPreview(null)));
    }

    @Override public void layout(PreviewContext host) {
        this.host = host;
        samples.forEach(UiButton::unbindTheme);
        samples.clear();
        sync();
        var area = host.viewport();
        int x = area.x() + 6, width = area.width() - 12;
        chooser = host.add(new UiButton(x + 28, area.y() + 6, Math.max(1, width - 56), 22,
                Component.empty(), ignored -> step(1), host.preferences(), false));
        host.add(new UiButton(x, area.y() + 6, 24, 22, Component.literal("‹"),
                ignored -> step(-1), host.preferences(), false)).setPreview(Component.translatable(KEY + "previous"));
        host.add(new UiButton(x + width - 24, area.y() + 6, 24, 22, Component.literal("›"),
                ignored -> step(1), host.preferences(), false)).setPreview(Component.translatable(KEY + "next"));
        int half = (width - 6) / 2, y = area.y() + 48;
        sample(x, y, half, "primary", true, () -> { selected = !selected; samples.get(0).setSelected(selected); });
        sample(x + half + 6, y, width - half - 6, "secondary", false,
                () -> { selected = !selected; samples.get(0).setSelected(selected); });
        var disabled = sample(x, y + 28, half, "disabled", false, () -> {});
        disabled.active = false;
        var toggle = sample(x + half + 6, y + 28, width - half - 6, "selected", false,
                () -> { toggleSelected = !toggleSelected; samples.get(3).setSelected(toggleSelected); });
        toggle.setSelected(toggleSelected);
        samples.get(0).setSelected(selected);
        refreshLabel();
    }

    private UiButton sample(int x, int y, int width, String label, boolean primary, Runnable action) {
        var button = new UiButton(x, y, width, 22, Component.translatable(KEY + label),
                ignored -> { if (host.interactive()) action.run(); }, host.preferences(), primary);
        button.bindTheme(session.scope());
        samples.add(host.add(button));
        return button;
    }

    private void sync() {
        var manager = MinecraftThemes.manager();
        var prefs = manager.preferences().withHighContrast(host.preferences().highContrast())
                .withReducedMotion(host.preferences().reducedMotion());
        if (session.sync(manager, prefs)) {
            samples.forEach(button -> button.bindTheme(session.scope()));
            refreshLabel();
        }
    }

    private void step(int direction) {
        if (!host.interactive()) return;
        sync();
        var themes = session.themes();
        int index = 0;
        for (int i = 0; i < themes.size(); i++) if (themes.get(i).id().equals(session.selected())) index = i;
        session.select(themes.get(Math.floorMod(index + direction, themes.size())).id());
        refreshLabel();
    }

    private void refreshLabel() {
        if (chooser == null) return;
        var themes = session.themes();
        for (int i = 0; i < themes.size(); i++) if (themes.get(i).id().equals(session.selected())) {
            var theme = themes.get(i);
            chooser.setMessage(Component.translatable(KEY + "selection", i + 1, themes.size(),
                    Component.translatable(theme.nameKey())));
            chooser.setPreview(Component.literal(theme.id()));
        }
    }

    @Override public void render(UiFrame frame) {
        sync();
        session.frame();
        var area = frame.viewport();
        UiTheme.preview(UiTheme.fromScope(session.scope()), () -> {
            if (UiTheme.current().liquidGlass() && !host.preferences().highContrast()) {
                // Real contrasting content behind the pane makes transmission observable, rather
                // than presenting a translucent material over an indistinguishable solid swatch.
                int top = area.y() + 34;
                TransparencyPreview.draw(frame.graphics(), new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(
                        area.x(), top, area.width(), Math.max(0, area.height() - 34)));
                LiquidGlassTheme.popover(frame.graphics(), area.x() + 2, top + 2,
                        Math.max(0, area.width() - 4), Math.max(0, area.height() - 38), host.preferences());
            } else {
                DarkModeTheme.background(frame.graphics(), area.x(), area.y() + 34, area.width(), area.height() - 34);
            }
            frame.graphics().drawString(host.font(), Component.translatable(KEY + "hint"),
                    area.x() + 6, area.bottom() - 18, DarkModeTheme.textColor(), false);
        });
    }

    @Override public Supplier<? extends PreviewScene> saveForRemount() {
        String theme = session.selected();
        boolean primarySelected = selected, secondarySelected = toggleSelected;
        return () -> {
            var preview = new GlobalThemeGalleryPreview(theme);
            preview.selected = primarySelected;
            preview.toggleSelected = secondarySelected;
            return preview;
        };
    }

    @Override public void suspend() { session.close(); }
    @Override public void close() { samples.forEach(UiButton::unbindTheme); samples.clear(); session.close(); }
}
