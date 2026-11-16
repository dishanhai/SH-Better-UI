// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.appearance;

import com.dishanhai.sh_better_ui.client.api.BackgroundSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.kernel.api.AppearanceService;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** UUK-owned theme editor shared by control panels and consumers. Never changes the live configuration. */
public final class ThemeCustomizationScreen extends Screen {
    private static final String[] COLORS = {"background", "surface", "border", "text", "accent"};
    private final AppearanceService appearance;
    private final Screen parent;
    private final ClientUiSettings settings;
    private final Consumer<ThemeSettings> apply;
    private final UiPreferences preferences;
    private final UiDotBackground background = new UiDotBackground();
    private final Set<Integer> invalidColors = new HashSet<>();
    private ThemeSettings draft;
    private ThemeSettings beforeReset;
    private String[] colors;
    private AppearanceService.ThemeSection section;
    private int page, pages, rows, left, panelWidth, footer;
    private UiButton done;

    public ThemeCustomizationScreen(AppearanceService appearance, Screen parent, ClientUiSettings settings,
                                    AppearanceService.ThemeSection section, Consumer<ThemeSettings> apply) {
        super(label("title"));
        this.appearance = appearance;
        this.parent = parent;
        this.settings = settings;
        this.section = section;
        this.apply = apply;
        preferences = UiPreferences.fromSettings(settings);
        draft = settings.theme().withMode(ThemeSettings.Mode.CUSTOM);
        readColors();
    }

    private void readColors() {
        colors = new String[]{draft.background(), draft.surface(), draft.border(), draft.text(), draft.accent()};
        invalidColors.clear();
    }

    @Override protected void init() {
        panelWidth = Math.min(480, Math.max(1, width - 16));
        left = (width - panelWidth) / 2;
        footer = height - 26;
        done = null;
        if (width < 240 || height < 180) {
            button(left, footer, panelWidth, label("cancel"), this::returnToParent);
            return;
        }
        int tabWidth = (panelWidth - 8) / 3;
        for (var tab : AppearanceService.ThemeSection.values()) {
            var control = button(left + tab.ordinal() * (tabWidth + 4), 28, tabWidth,
                    label("section." + tab.name().toLowerCase(java.util.Locale.ROOT)), () -> {
                        section = tab;
                        page = 0;
                        rebuild();
                    });
            control.setSelected(section == tab);
        }
        boolean backgrounds = section == AppearanceService.ThemeSection.BACKGROUNDS;
        int listTop = backgrounds ? 88 : 64;
        rows = Math.max(1, (footer - listTop - 34) / 24);
        int count = switch (section) {
            case COLORS -> 6;
            case BACKGROUNDS -> appearance.backgrounds().entries().size();
            case EFFECTS -> effectGroups().size();
        };
        pages = Math.max(1, (count + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        if (backgrounds) configureBackground();
        for (int i = page * rows; i < Math.min(count, (page + 1) * rows); i++) {
            int y = listTop + (i - page * rows) * 24;
            switch (section) {
                case COLORS -> color(i, y);
                case BACKGROUNDS -> background(i, y);
                case EFFECTS -> effect(effectGroups().get(i), y);
            }
        }
        if (pages > 1) {
            button(left, footer - 30, 64, label("previous"), () -> page(-1)).active = page > 0;
            button(left + panelWidth - 64, footer - 30, 64, label("next"), () -> page(1)).active = page + 1 < pages;
        }
        int third = (panelWidth - 8) / 3;
        button(left, footer, third, label(beforeReset == null ? "reset" : "undo"), this::reset);
        button(left + third + 4, footer, third, label("cancel"), this::returnToParent);
        done = button(left + 2 * (third + 4), footer, panelWidth - 2 * (third + 4), label("done"), () -> {
            if (!invalidColors.isEmpty()) return;
            apply.accept(draft);
            returnToParent();
        });
        done.active = invalidColors.isEmpty();
    }

    private void color(int index, int y) {
        if (index == 0) {
            button(left, y, panelWidth, label("base", Component.translatable("sh_better_ui.panel.theme.mode."
                    + (draft.customBase() == ThemeSettings.Base.DARK ? "dark" : "light"))), () -> {
                draft = draft.withPalette(draft.customBase() == ThemeSettings.Base.DARK
                        ? ThemeSettings.Base.LIGHT : ThemeSettings.Base.DARK,
                        draft.background(), draft.surface(), draft.border(), draft.text(), draft.accent());
                beforeReset = null;
                rebuild();
            });
            return;
        }
        int color = index - 1;
        var input = new UiTextField(font, new Bounds(left + panelWidth / 2, y, panelWidth / 2 - 40, 20),
                label("color." + COLORS[color]), 7, preferences);
        input.setValue(colors[color]);
        input.setHint(label("inherit"));
        input.setOnChange(value -> {
            colors[color] = value;
            if (ThemeSettings.validColor(value)) invalidColors.remove(color);
            else invalidColors.add(color);
            input.setError(invalidColors.contains(color) ? label("invalid_color") : null);
            if (invalidColors.isEmpty()) {
                draft = draft.withPalette(draft.customBase(), colors[0], colors[1], colors[2], colors[3], colors[4]);
                beforeReset = null;
            }
            if (done != null) done.active = invalidColors.isEmpty();
        });
        if (invalidColors.contains(color)) input.setError(label("invalid_color"));
        addRenderableWidget(input);
        var reset = button(left + panelWidth - 36, y, 36, Component.translatable("sh_better_ui.theme_editor.inherit"), () -> {
            input.setValue("");
            colors[color] = "";
            invalidColors.remove(color);
            if (invalidColors.isEmpty()) draft = draft.withPalette(draft.customBase(), colors[0], colors[1], colors[2], colors[3], colors[4]);
            beforeReset = null;
            rebuild();
        });
        reset.setPreview(label("inherit"));
    }

    private void configureBackground() {
        var spec = appearance.backgrounds().find(draft.backgroundEffect()).orElse(null);
        var configure = button(left, 64, panelWidth, label("configure_background"), () -> {
            if (spec != null && spec.settingsGroup() != null) editGroup(spec.settingsGroup());
        });
        configure.active = spec != null && spec.settingsGroup() != null;
    }

    private void background(int index, int y) {
        BackgroundSpec spec = appearance.backgrounds().entries().get(index);
        var control = button(left, y, panelWidth, Component.translatable(spec.titleKey()), () -> {
            draft = draft.withBackgroundEffect(spec.id());
            beforeReset = null;
            rebuild();
        });
        control.setSelected(draft.backgroundEffect().equals(spec.id()));
    }

    private List<String> effectGroups() {
        return appearance.groups().stream().filter(group -> appearance.backgrounds().entries().stream()
                .noneMatch(spec -> group.equals(spec.settingsGroup()))).toList();
    }

    private void effect(String group, int y) {
        button(left, y, panelWidth,
                Component.translatableWithFallback("sh_better_ui.panel.theme.group." + group, group), () -> editGroup(group));
    }

    private void editGroup(String group) {
        minecraft.setScreen(appearance.edit(this, settings.withTheme(draft), group, new JsonObject(),
                draft.effects().group(group), values -> {
                    draft = draft.withEffects(draft.effects().withGroup(group, values));
                    beforeReset = null;
                }));
    }

    private void reset() {
        if (beforeReset == null) {
            beforeReset = draft;
            draft = ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.CUSTOM)
                    .withHudBackgroundEffect(draft.hudBackgroundEffect()).withHudBlurRadius(draft.hudBlurRadius());
        } else {
            draft = beforeReset;
            beforeReset = null;
        }
        readColors();
        rebuild();
    }

    private void page(int delta) {
        int next = Math.max(0, Math.min(pages - 1, page + delta));
        if (page != next) { page = next; rebuild(); }
    }

    private void rebuild() {
        int focus = children().indexOf(getFocused());
        setFocused(null);
        clearWidgets();
        init();
        if (focus >= 0 && !children().isEmpty()) setFocused(children().get(Math.min(focus, children().size() - 1)));
    }
    private UiButton button(int x, int y, int w, Component message, Runnable action) {
        var button = new UiButton(x, y, w, 20, message, ignored -> action.run(), preferences, false);
        button.setSampleBackground(false);
        return addRenderableWidget(button);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            page(key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ThemeEffects.preview(settings.withTheme(draft), () -> UiTheme.preview(UiTheme.resolve(draft), () -> {
            background.renderThemed(this, g, width, height, preferences, false);
            var palette = UiTheme.resolve(draft);
            g.fill(left - 6, 4, left + panelWidth + 6, height - 2, palette.surface());
            g.drawCenteredString(font, title, width / 2, 12, palette.text());
            if (width < 240 || height < 180) g.drawCenteredString(font, label("too_small"), width / 2, height / 2, palette.text());
            else {
                var hint = label(section == AppearanceService.ThemeSection.COLORS ? "color_hint"
                        : section == AppearanceService.ThemeSection.BACKGROUNDS ? "background_hint" : "effect_hint");
                g.drawString(font, font.plainSubstrByWidth(hint.getString(), panelWidth),
                        left, 52, palette.text(), false);
                if (section == AppearanceService.ThemeSection.COLORS) {
                    for (int i = page * rows; i < Math.min(6, (page + 1) * rows); i++) if (i > 0) {
                        int y = 64 + (i - page * rows) * 24;
                        g.drawString(font, label("color." + COLORS[i - 1]), left + 2, y + 6, palette.text(), false);
                    }
                }
                if (pages > 1) g.drawCenteredString(font, Component.translatable("sh_better_ui.theme_editor.page", page + 1, pages),
                        width / 2, footer - 24, palette.text());
            }
            super.render(g, mouseX, mouseY, partialTick);
        }));
    }

    @Override public void removed() { background.reset(); }
    private void returnToParent() { minecraft.setScreen(parent); }
    @Override public void onClose() {
        if (invalidColors.isEmpty() && draft.equals(settings.theme().withMode(ThemeSettings.Mode.CUSTOM))) {
            returnToParent();
            return;
        }
        minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(discard -> {
            if (discard) returnToParent();
            else minecraft.setScreen(this);
        }, Component.translatable("sh_better_ui.panel.discard.title"),
                Component.translatable("sh_better_ui.panel.discard.settings"),
                Component.translatable("sh_better_ui.panel.discard.action"),
                net.minecraft.network.chat.CommonComponents.GUI_CANCEL));
    }
    @Override public boolean isPauseScreen() { return false; }
    private static Component label(String key, Object... args) {
        return Component.translatable("sh_better_ui.custom_theme." + key, args);
    }
}
