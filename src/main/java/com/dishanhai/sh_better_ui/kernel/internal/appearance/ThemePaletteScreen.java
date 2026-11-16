// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.appearance;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.function.Consumer;

/** Explicit nested transaction: gesture commits stay on this screen until Confirm. */
final class ThemePaletteScreen extends Screen {
    private final Screen parent;
    private final ClientUiSettings settings;
    private final UiPreferences preferences;
    private final ThemePaletteEdit edit;
    private final Consumer<String> apply;
    private final int index;
    private final UiDotBackground background = new UiDotBackground();
    private UiColorEditor editor;
    private UiTextField hex;
    private UiButton confirm;
    private int left, panelWidth, footer, previewTop;
    private boolean invalid, completed;
    private String input;

    ThemePaletteScreen(Screen parent, ClientUiSettings settings, int index, Component name, Consumer<String> apply) {
        super(label("title", name));
        this.parent = parent; this.settings = settings; this.index = index; this.apply = apply;
        preferences = UiPreferences.fromSettings(settings);
        edit = new ThemePaletteEdit(settings.theme(), index);
        input = edit.value();
    }

    @Override protected void init() {
        background.reset();
        cancelInteraction(); editor = null; hex = null; confirm = null;
        panelWidth = Math.min(440, Math.max(1, width - 16)); left = (width - panelWidth) / 2; footer = height - 26;
        if (width < 240 || height < 180) {
            button(left, footer, panelWidth, label("cancel"), this::onClose); return;
        }
        hex = addRenderableWidget(new UiTextField(font, new Bounds(left, 40, panelWidth, 20), label("hex"), 7, preferences));
        hex.setValue(input); hex.setHint(Component.translatable("sh_better_ui.custom_theme.inherit"));
        hex.setOnChange(value -> {
            input = value; invalid = !edit.text(value);
            hex.setError(invalid ? Component.translatable("sh_better_ui.custom_theme.invalid_color") : null);
            if (!invalid && editor != null) editor.setValue(resolve(edit.previewTheme(), index));
            confirm.active = !invalid;
        });
        if (invalid) hex.setError(Component.translatable("sh_better_ui.custom_theme.invalid_color"));
        previewTop = 70;
        if (height >= 290) {
            mountEditor(left, 70, panelWidth);
            previewTop = 70 + UiColorEditor.RGB_MIN_HEIGHT + 8;
        }
        int third = (panelWidth - 8) / 3;
        button(left, footer, third, label("inherit"), () -> {
            cancelInteraction(); edit.inherit(); input = ""; invalid = false;
            if (hex != null) hex.setValue("");
            if (editor != null) editor.setValue(resolve(edit.previewTheme(), index));
            if (hex != null) hex.setError(null);
            confirm.active = true;
        });
        button(left + third + 4, footer, third, label("cancel"), this::onClose);
        confirm = button(left + 2 * (third + 4), footer, panelWidth - 2 * (third + 4), label("confirm"), this::confirm);
        confirm.active = !invalid;
    }

    private void mountEditor(int x, int y, int width) {
        var inherited = ThemePaletteEdit.replace(settings.theme(), index, "");
        editor = addRenderableWidget(new UiColorEditor(new Bounds(x, y, width, UiColorEditor.RGB_MIN_HEIGHT), title,
                resolve(edit.previewTheme(), index), resolve(inherited, index), false, preferences,
                rgb -> {
                    if (editor != null && editor.hasPendingChange()) edit.preview(rgb);
                    else edit.cancelPreview();
                }, rgb -> {
                    edit.commit(rgb); input = edit.value(); invalid = false;
                    if (hex != null) { hex.setValue(input); hex.setError(null); }
                    if (confirm != null) confirm.active = true;
                }));
    }

    private boolean acceptDraft() {
        if (invalid || completed) return false;
        if (editor != null) editor.commitInteraction();
        completed = true;
        apply.accept(edit.value());
        return true;
    }
    private void confirm() { if (acceptDraft()) minecraft.setScreen(parent); }
    private void cancelInteraction() {
        if (editor != null) editor.cancelInteraction();
        edit.cancelPreview(); setDragging(false);
    }
    static int resolve(ThemeSettings theme, int index) {
        var palette = UiTheme.resolve(theme);
        return switch (index) {
            case 0 -> palette.background(); case 1 -> palette.surface(); case 2 -> palette.border();
            case 3 -> palette.text(); case 4 -> palette.accent(); default -> throw new IllegalArgumentException("Unknown palette field");
        };
    }
    private UiButton button(int x, int y, int width, Component title, Runnable action) {
        var button = new UiButton(x, y, width, 20, title, ignored -> action.run(), preferences, false);
        button.setSampleBackground(false); return addRenderableWidget(button);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (editor != null && confirm != null && confirm.active && confirm.isMouseOver(x, y) && button == 0) editor.commitInteraction();
        else if (editor != null && !editor.isMouseOver(x, y)) cancelInteraction();
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (editor != null && editor.releasePointer(x, y, button)) { setDragging(false); return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (editor != null && editor.isEditing()) { cancelInteraction(); return true; }
            onClose(); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var theme = edit.previewTheme(); var palette = UiTheme.resolve(theme);
        ThemeEffects.preview(settings.withTheme(theme), () -> UiTheme.preview(palette, () -> {
            // Repaint the actual backdrop each frame; alpha-filling last frame's framebuffer
            // accumulates old widgets/tooltips when the menu has no world renderer behind it.
            background.renderThemed(this, g, width, height, preferences, false);
            g.drawCenteredString(font, title, width / 2, 14, palette.text());
            if (width < 240 || height < 180) g.drawCenteredString(font,
                    Component.translatable("sh_better_ui.custom_theme.too_small"), width / 2, height / 2, palette.text());
            else {
                int bottom = footer - 10;
                // The sample has a known opaque reference canvas, not an invented world
                // pixel. Keep the theme layers translucent and compose their real alpha.
                // The rest of the page still previews its transparent background normally.
                g.fill(left, previewTop, left + panelWidth, bottom, sampleCanvas(palette));
                g.fill(left, previewTop, left + panelWidth, bottom, palette.background());
                // Paint the border only at its edges; an opaque full-size underlay would
                // make translucent registered surfaces look like the border color.
                g.fill(left, previewTop, left + panelWidth, previewTop + 1, palette.border());
                g.fill(left, bottom - 1, left + panelWidth, bottom, palette.border());
                g.fill(left, previewTop + 1, left + 1, bottom - 1, palette.border());
                g.fill(left + panelWidth - 1, previewTop + 1, left + panelWidth, bottom - 1, palette.border());
                g.fill(left + 1, previewTop + 1, left + panelWidth - 1, bottom - 1, palette.surface());
                g.drawString(font, label("sample_text"), left + 10, previewTop + 10, palette.text(), false);
                int actionY = previewTop + 28;
                g.fill(left + 10, actionY, left + panelWidth - 10, Math.min(bottom - 6, actionY + 20), palette.accent());
                int actionText = sampleActionText(palette);
                g.drawString(font, label("sample_action"), left + 16, actionY + 5, actionText, false);
            }
            super.render(g, mouseX, mouseY, partialTick);
        }));
    }
    static int sampleCanvas(UiTheme palette) { return palette.light() ? 0xffe8eef5 : 0xff101722; }
    static int sampleBackdrop(UiTheme palette) {
        int background = com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy.composite(
                palette.background(), sampleCanvas(palette));
        return com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy.composite(palette.surface(), background);
    }
    static int sampleActionText(UiTheme palette) {
        return com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy.readable(
                palette.text(), palette.accent(), sampleBackdrop(palette), 4.5);
    }

    @Override public void removed() { cancelInteraction(); if (editor != null) editor.close(); background.reset(); super.removed(); }
    @Override public void onClose() { completed = true; cancelInteraction(); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    private static Component label(String key, Object... args) { return Component.translatable("sh_better_ui.palette_editor." + key, args); }
}
