// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import com.dishanhai.sh_better_ui.theme.context.ContextKeys;
import com.dishanhai.sh_better_ui.theme.context.ContextSnapshot;
import com.dishanhai.sh_better_ui.theme.context.ThemeCondition;
import com.dishanhai.sh_better_ui.theme.context.ThemeRule;
import com.dishanhai.sh_better_ui.theme.transition.Easing;
import com.dishanhai.sh_better_ui.theme.transition.TransitionSpec;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Uses an isolated ThemeManager and the actual game dimension when following the environment. */
final class GalleryDynamicThemeSample extends GallerySampleScreen {
    private static final String THEME = "sh_better_ui.theme.dynamic.";
    private static final List<String> THEMES = List.of("", BuiltinThemes.DARK, BuiltinThemes.LIGHT_ID,
            BuiltinThemes.NETHER, BuiltinThemes.END, BuiltinThemes.LIQUID_GLASS, BuiltinThemes.HUD_GLASS);

    private record Draft(String id, boolean contrast, boolean motion) {}

    private ThemeManager manager;
    private Draft saved;
    private Draft draft;
    private String dimension;
    private Bounds canvas;
    private UiButton choice, contrast, motion, apply, cancel;
    private Component status = CommonComponents.EMPTY;

    GalleryDynamicThemeSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
        var values = GalleryPreviewSettings.read();
        String choice = values.getProperty("theme", "");
        saved = new Draft(THEMES.contains(choice) ? choice : "",
                Boolean.parseBoolean(values.getProperty("themeHighContrast")),
                Boolean.parseBoolean(values.getProperty("themeReducedMotion")));
        draft = saved;
    }

    @Override
    protected void initSample() {
        if (manager == null || manager.isClosed()) {
            manager = new ThemeManager(Util::getMillis, failure -> System.getLogger(getClass().getName())
                    .log(System.Logger.Level.WARNING, "Could not resolve the gallery theme.", failure));
            dimension = null;
            registerDimension("minecraft:the_nether", BuiltinThemes.NETHER);
            registerDimension("minecraft:the_end", BuiltinThemes.END);
        }
        int rowHeight = body.height() >= 82 ? 22 : 16;
        int firstY = body.bottom() - rowHeight * 2 - 3;
        canvas = new Bounds(body.x(), body.y(), body.width(), Math.max(0, firstY - body.y() - 4));
        Bounds row = new Bounds(body.x(), firstY, body.width(), rowHeight);
        choice = addButton(GalleryAppearanceSamples.column(row, 0, 3), name(draft.id), ignored -> {
            int next = (THEMES.indexOf(draft.id) + 1) % THEMES.size();
            change(new Draft(THEMES.get(next), draft.contrast, draft.motion));
        }, true);
        initialFocus = choice;
        contrast = addButton(GalleryAppearanceSamples.column(row, 1, 3), themeLabel("high_contrast"),
                ignored -> change(new Draft(draft.id, !draft.contrast, draft.motion)), false);
        motion = addButton(GalleryAppearanceSamples.column(row, 2, 3), themeLabel("reduced_motion"),
                ignored -> change(new Draft(draft.id, draft.contrast, !draft.motion)), false);
        Bounds edit = new Bounds(body.x(), firstY + rowHeight + 3, body.width(), rowHeight);
        apply = addButton(GalleryAppearanceSamples.column(edit, 0, 2), Component.translatable("sh_better_ui.panel.apply"),
                ignored -> apply(), true);
        cancel = addButton(GalleryAppearanceSamples.column(edit, 1, 2), CommonComponents.GUI_CANCEL, ignored -> {
            change(saved);
            status = GalleryAppearanceSamples.label("cancelled");
            triggerImmediateNarration(true);
        }, false);
        updateEnvironment();
        refresh();
    }

    private void registerDimension(String dimension, String themeId) {
        manager.registerRule(ThemeRule.builder(themeId).theme(themeId)
                .when(ThemeCondition.of(Set.of(ContextKeys.DIMENSION), context -> dimension.equals(context.get(ContextKeys.DIMENSION))))
                .transition(TransitionSpec.of(260, Easing.LINEAR)).build());
    }

    private void updateEnvironment() {
        String current = minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
        if (!current.equals(dimension)) {
            dimension = current;
            manager.environment(ContextSnapshot.of(Map.of(ContextKeys.DIMENSION, current)));
        }
    }

    private void change(Draft next) {
        draft = next;
        status = CommonComponents.EMPTY;
        refresh();
        triggerImmediateNarration(true);
    }

    private void refresh() {
        var next = ThemePreferences.DEFAULT.withReducedMotion(draft.motion || preferences.reducedMotion())
                .withHighContrast(draft.contrast || preferences.highContrast());
        if (!draft.id.isEmpty()) next = next.withFixedTheme(draft.id);
        manager.preferences(next);
        var preview = new UiPreferences(next.highContrast(), next.reducedMotion(), preferences.backgroundBlur(), preferences.feedback());
        choice.setMessage(name(draft.id));
        contrast.setSelected(next.highContrast());
        motion.setSelected(next.reducedMotion());
        contrast.active = !preferences.highContrast();
        motion.active = !preferences.reducedMotion();
        for (UiButton widget : List.of(choice, contrast, motion, apply, cancel)) widget.setPreferences(preview);
        apply.active = cancel.active = !saved.equals(draft);
    }

    private void apply() {
        boolean persisted = GalleryPreviewSettings.write(values -> {
            values.setProperty("theme", draft.id);
            values.setProperty("themeHighContrast", Boolean.toString(draft.contrast));
            values.setProperty("themeReducedMotion", Boolean.toString(draft.motion));
        });
        if (persisted) saved = draft;
        status = GalleryAppearanceSamples.label(persisted ? "saved" : "save_failed");
        refresh();
        triggerImmediateNarration(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!compact) updateEnvironment();
    }

    @Override
    public void removed() {
        super.removed();
        if (manager != null) manager.close();
    }

    private Component active() {
        if (manager == null || manager.isClosed()) return name(draft.id);
        return Component.translatable(THEME + "active", name(manager.snapshot().themeIds().getLast()));
    }

    @Override
    public Component getNarrationMessage() {
        return super.getNarrationMessage().copy().append(" ").append(active()).append(" ").append(status);
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractFooterStatus(graphics, status, mouseX, mouseY);
        if (canvas.height() <= 0) return;
        manager.frame();
        UiTheme preview = UiTheme.fromTokens(manager.snapshot());
        int edge = manager.preferences().highContrast() ? preview.text() : preview.border();
        graphics.fill(canvas.x(), canvas.y(), canvas.right(), canvas.bottom(), preview.background() | 0xFF000000);
        if (canvas.height() < 32) {
            PanelText.drawLine(graphics, font, active(), canvas.x() + 4, canvas.y() + 2,
                    canvas.width() - 8, preview.text(), mouseX, mouseY);
            return;
        }
        int height = Math.min(canvas.height() - 12, 72);
        int y = canvas.y() + (canvas.height() - height) / 2;
        int width = Math.min(226, canvas.width() - 14);
        int x = canvas.x() + (canvas.width() - width) / 2;
        RoundedCorners.draw(graphics, x, y, width, height, 9, 1, edge, preview.surface() | 0xFF000000);
        PanelText.drawLine(graphics, font, active(), x + 10, y + 5, width - 20, preview.text(), mouseX, mouseY);
        if (height >= 37) {
            graphics.fill(x + 10, y + height - 18, x + width - 10, y + height - 12, preview.accent());
        }
    }

    private static Component name(String id) {
        return themeLabel(id.isEmpty() ? "follow" : id.substring(id.indexOf(':') + 1));
    }

    private static Component themeLabel(String suffix) { return Component.translatable(THEME + suffix); }
}
