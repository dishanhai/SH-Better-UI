package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.panel.ThemeEffectPreview;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffectFields.Kind;

/** 山海：开发者面板和宿主页面共用的效果参数表单。 */
public final class ThemeEffectEditor extends Screen {
    private static final String KEYS = "sh_better_ui.theme_editor.";
    private static final int ROW = 22;
    private final Screen parent;
    private final ClientUiSettings settings;
    private final UiPreferences preferences;
    private final String group;
    private final JsonObject inherited;
    private final Consumer<JsonObject> apply;
    private final List<ThemeEffectFields.Field> fields;
    private final List<ThemeEffectPresets.Preset> presets;
    private final ThemeEffectPreview preview;
    private final UiDotBackground backdrop;
    private JsonObject local;
    private JsonObject values;
    private ClientUiSettings previewSettings;
    private Bounds canvas;
    private ThemeEffectFields.Field detail;
    private final Map<ThemeEffectFields.Field, Button> inheritButtons = new HashMap<>();
    private Button confirm;
    private Button presetButton;
    private int entry;
    private int page;
    private int pages = 1;
    private int left;
    private int right;
    private int footer;
    private boolean invalid;

    public ThemeEffectEditor(Screen parent, ClientUiSettings settings, String group, JsonObject inherited,
                             JsonObject local, Consumer<JsonObject> apply) {
        super(label("title", groupName(group)));
        this.parent = parent;
        this.settings = settings;
        this.preferences = UiPreferences.fromSettings(settings);
        this.group = group;
        backdrop = "dotMatrix".equals(group) ? new UiDotBackground() : null;
        preview = backdrop == null ? new ThemeEffectPreview() : null;
        this.inherited = inherited.deepCopy();
        this.local = local.deepCopy();
        this.apply = apply;
        fields = ThemeEffectFields.fields(group);
        presets = ThemeEffectPresets.forGroup(group);
        refresh();
    }

    @Override
    protected void init() {
        inheritButtons.clear();
        confirm = null;
        presetButton = null;
        invalid = false;
        int panelWidth = Math.max(1, Math.min(width - 12, 480));
        left = (width - panelWidth) / 2;
        right = left + panelWidth;
        footer = height - 26;
        int columnX = preview != null && ThemeEffectPreview.supports(group) && width >= 350
                ? left + panelWidth / 3 + 6 : left;
        int columnWidth = right - columnX;
        canvas = columnX > left ? new Bounds(left, 28, columnX - left - 6, Math.max(0, footer - 48)) : null;

        if (columnWidth < 138 || height < (detail == null ? 114 : 186)) {
            button(left, footer, panelWidth, label("cancel"), this::onClose);
            return;
        }
        if (detail == null) {
            showFields(columnX, columnWidth);
            int third = (panelWidth - 8) / 3;
            button(left, footer, third, label("reset"), () -> {
                local = new JsonObject();
                refresh();
                rebuild();
            });
            button(left + third + 4, footer, third, label("cancel"), this::onClose);
            confirm = button(left + 2 * (third + 4), footer, panelWidth - 2 * (third + 4), label("done"), () -> {
                try {
                    ThemeEffects.resolveEditor(settings, group, inherited, local);
                    apply.accept(local.deepCopy());
                    onClose();
                } catch (IllegalArgumentException invalidValues) {
                    setInvalid(true);
                }
            });
        } else {
            showColor(columnX, columnWidth);
            button(left, footer, panelWidth, label("done"), this::closeDetail);
        }
    }

    private void showFields(int x, int w) {
        int rows = Math.max(1, (footer - 66) / ROW);
        int presetRows = presets.isEmpty() ? 0 : 1;
        int count = fields.size() + presetRows;
        pages = Math.max(1, (count + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int first = page * rows;
        for (int i = first; i < Math.min(first + rows, count); i++) {
            int y = 26 + (i - first) * ROW;
            if (i < presetRows) {
                presetButton = button(x, y, w, presetLabel(), this::cyclePreset);
                presetButton.setTooltip(Tooltip.create(label("preset_hint")));
                continue;
            }
            var field = fields.get(i - presetRows);
            addField(field, x, y, w - 50);
            var inherit = button(x + w - 46, y, 46, label("inherit"), () -> {
                if (change(field, null)) rebuild();
            });
            inherit.active = ThemeEffectFields.get(local, field.path()) != null;
            inheritButtons.put(field, inherit);
        }
        if (pages > 1) {
            var previous = button(x, footer - 38, 52, label("previous"), () -> page(-1));
            previous.active = page > 0;
            var next = button(x + w - 52, footer - 38, 52, label("next"), () -> page(1));
            next.active = page + 1 < pages;
        }
    }

    private int currentPreset() {
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).matches(values)) return i;
        }
        return -1;
    }

    private Component presetLabel() {
        int selected = currentPreset();
        return label("preset", selected < 0 ? label("preset.custom")
                : Component.translatable(presets.get(selected).titleKey()));
    }

    private void cyclePreset() {
        var next = presets.get((currentPreset() + 1) % presets.size());
        try {
            local = next.applyValidated(settings, group, inherited, local);
            refresh();
            boolean focused = getFocused() == presetButton;
            rebuild();
            if (focused && presetButton != null) setFocused(presetButton);
        } catch (IllegalArgumentException invalidValues) {
            setInvalid(true);
        }
    }

    private void addField(ThemeEffectFields.Field field, int x, int y, int w) {
        JsonElement value = ThemeEffectFields.get(values, field.path());
        Component name = fieldLabel(field);
        switch (field.kind()) {
            case TOGGLE -> addRenderableWidget(CycleButton.onOffBuilder(value.getAsBoolean()).create(x, y, w, 20, name,
                    (button, selected) -> change(field, new JsonPrimitive(selected))));
            case OPTION -> addRenderableWidget(CycleButton.<String>builder(ThemeEffectEditor::optionLabel)
                    .withValues(field.options()).withInitialValue(value.getAsString()).create(x, y, w, 20, name,
                            (button, selected) -> change(field, new JsonPrimitive(selected))));
            case NUMBER -> number(field, name, value, x, y, w);
            case TEXT -> {
                var input = new EditBox(font, x, y, w, 20, name);
                input.setMaxLength(256);
                input.setHint(name);
                input.setValue(value.getAsString());
                input.setTooltip(Tooltip.create(name));
                input.setResponder(text -> change(field, new JsonPrimitive(text)));
                addRenderableWidget(input);
            }
            default -> button(x, y, w, label("field_value", name,
                    value.isJsonArray() ? value.getAsJsonArray().size()
                            : value.getAsString().isEmpty() ? label("inherit") : colorCode(readColor(value), field.kind() == Kind.ARGB)),
                    () -> {
                        detail = field;
                        entry = 0;
                        rebuild();
                    }).setTooltip(Tooltip.create(name));
        }
    }

    private void number(ThemeEffectFields.Field field, Component name, JsonElement value, int x, int y, int w) {
        var range = ThemeEffectRanges.find(group, field);
        if (range != null) {
            slider(new Bounds(x, y, w, 20), name, range.min(), range.max(), value.getAsBigDecimal(), range.scale(),
                    next -> change(field, new JsonPrimitive(next)));
            return;
        }
        var input = new EditBox(font, x + 22, y, w - 44, 20, name);
        input.setMaxLength(48);
        input.setValue(value.getAsString());
        input.setTooltip(Tooltip.create(name));
        input.setResponder(text -> {
            try {
                change(field, new JsonPrimitive(new BigDecimal(text)));
            } catch (NumberFormatException invalidNumber) {
                setInvalid(true);
            }
        });
        addRenderableWidget(input);
        var step = field.type() == float.class || field.type() == double.class ? new BigDecimal("0.1") : BigDecimal.ONE;
        button(x, y, 20, Component.literal("−"), () ->
                input.setValue(ThemeEffectFields.get(values, field.path()).getAsBigDecimal().subtract(step).toPlainString()))
                .setTooltip(Tooltip.create(name));
        button(x + w - 20, y, 20, Component.literal("+"), () ->
                input.setValue(ThemeEffectFields.get(values, field.path()).getAsBigDecimal().add(step).toPlainString()))
                .setTooltip(Tooltip.create(name));
    }

    private void showColor(int x, int w) {
        JsonElement value = ThemeEffectFields.get(values, detail.path());
        JsonArray list = value.isJsonArray() ? value.getAsJsonArray() : null;
        boolean stops = detail.kind() == Kind.STOPS;
        int y = 26;
        if (list != null) {
            entry = Math.min(entry, Math.max(0, list.size() - 1));
            int part = (w - 6) / 4;
            button(x, y, part, label("previous"), () -> selectEntry(-1)).active = entry > 0;
            button(x + part + 2, y, part, label("next"), () -> selectEntry(1)).active = entry + 1 < list.size();
            button(x + 2 * (part + 2), y, part, label("add"), this::addColor).active = list.size() < 256;
            button(x + 3 * (part + 2), y, w - 3 * (part + 2), label("remove"), this::removeColor)
                    .active = stops ? list.size() > 0 : list.size() > 1;
            y += ROW;
            if (list.size() == 0) return;
            value = list.get(entry);
            if (stops) {
                if (entry > 0) {
                    float before = list.get(entry - 1).getAsJsonObject().get("position").getAsFloat();
                    float after = entry + 1 < list.size()
                            ? list.get(entry + 1).getAsJsonObject().get("position").getAsFloat() : 1;
                    int min = (int) Math.floor(before * 1000) + 1;
                    int max = (int) Math.ceil(after * 1000) - 1;
                    if (max > min) slider(new Bounds(x, y, w, 20), label("field.position"), min, max,
                            value.getAsJsonObject().get("position").getAsBigDecimal(), 3, this::setPosition);
                }
                y += ROW;
                value = value.getAsJsonObject().get("argb");
            }
        }
        int channels = detail.kind() == Kind.RGB ? 3 : 4;
        int color = readColor(value);
        for (int i = 0; i < channels; i++) {
            int shift = (2 - i + 4) % 4 * 8;
            String channel = List.of("red", "green", "blue", "alpha").get(i);
            addRenderableWidget(new UiSlider(new Bounds(x, y + i * ROW, w, 20), 0, 255, 1, color >>> shift & 255,
                    preferences, next -> label("field_value", label(channel), next),
                    next -> updateColor(shift, next)));
        }
    }

    private void slider(Bounds area, Component name, int min, int max, BigDecimal value, int decimals,
                        Consumer<BigDecimal> setter) {
        int initial = value.movePointRight(decimals).max(BigDecimal.valueOf(min)).min(BigDecimal.valueOf(max)).intValue();
        addRenderableWidget(new UiSlider(area, min, max, 1, initial, preferences,
                next -> label("field_value", name, BigDecimal.valueOf(next, decimals).stripTrailingZeros().toPlainString()),
                next -> setter.accept(BigDecimal.valueOf(next, decimals))));
    }

    private void setPosition(BigDecimal position) {
        var colors = current(detail).getAsJsonArray().deepCopy();
        colors.get(entry).getAsJsonObject().addProperty("position", position);
        change(detail, colors);
    }

    private void updateColor(int shift, int channel) {
        JsonElement source = current(detail);
        if (!source.isJsonArray()) {
            int color = setChannel(readColor(source), shift, channel);
            change(detail, detail.kind() == Kind.RGB ? new JsonPrimitive(colorCode(color, false)) : new JsonPrimitive(color));
            return;
        }
        var colors = source.getAsJsonArray().deepCopy();
        if (detail.kind() == Kind.STOPS) {
            var stop = colors.get(entry).getAsJsonObject();
            stop.addProperty("argb", setChannel(stop.get("argb").getAsInt(), shift, channel));
        } else {
            colors.set(entry, new JsonPrimitive(setChannel(colors.get(entry).getAsInt(), shift, channel)));
        }
        change(detail, colors);
    }

    private void addColor() {
        var colors = current(detail).getAsJsonArray().deepCopy();
        if (detail.kind() == Kind.STOPS) {
            float previous = colors.size() == 0 ? 0 : colors.get(colors.size() - 1).getAsJsonObject().get("position").getAsFloat();
            float position = colors.size() == 0 ? 0 : previous + (1 - previous) / 2;
            var stop = new JsonObject();
            stop.addProperty("position", position);
            stop.addProperty("argb", 0xFFFFFFFF);
            colors.add(stop);
        } else {
            colors.add(0xFFFFFFFF);
        }
        if (change(detail, colors)) {
            entry = colors.size() - 1;
            rebuild();
        }
    }

    private void removeColor() {
        var colors = current(detail).getAsJsonArray().deepCopy();
        colors.remove(entry);
        if (detail.kind() == Kind.STOPS && colors.size() > 0) colors.get(0).getAsJsonObject().addProperty("position", 0);
        if (change(detail, colors)) rebuild();
    }

    private static int setChannel(int color, int shift, int channel) {
        return color & ~(255 << shift) | channel << shift;
    }

    private static int readColor(JsonElement value) {
        if (value.getAsJsonPrimitive().isNumber()) return value.getAsInt();
        String hex = value.getAsString().replace("#", "");
        return hex.isEmpty() ? -1 : 0xFF000000 | Integer.parseInt(hex, 16);
    }

    private static String colorCode(int color, boolean alpha) {
        return String.format(Locale.ROOT, alpha ? "#%08X" : "#%06X", alpha ? color : color & 0xFFFFFF);
    }

    private JsonElement current(ThemeEffectFields.Field field) {
        return ThemeEffectFields.get(values, field.path());
    }

    private boolean change(ThemeEffectFields.Field field, JsonElement value) {
        try {
            local = ThemeEffectFields.edit(settings, group, inherited, local, field.path(), value);
            refresh();
            if (presetButton != null) presetButton.setMessage(presetLabel());
            var inherit = inheritButtons.get(field);
            if (inherit != null) inherit.active = ThemeEffectFields.get(local, field.path()) != null;
            setInvalid(false);
            return true;
        } catch (IllegalArgumentException e) {
            setInvalid(true);
            return false;
        }
    }

    private void refresh() {
        values = ThemeEffectFields.values(settings, group, inherited, local);
        previewSettings = ThemeEffects.previewSettings(settings, group, inherited, local);
    }

    private void setInvalid(boolean invalid) {
        this.invalid = invalid;
        if (confirm != null) confirm.active = !invalid;
    }

    private void selectEntry(int delta) {
        entry += delta;
        rebuild();
    }

    private void closeDetail() {
        detail = null;
        rebuild();
    }

    private void page(int delta) {
        int next = Math.max(0, Math.min(pages - 1, page + delta));
        if (next != page) {
            page = next;
            rebuild();
        }
    }

    private void rebuild() {
        setFocused(null);
        clearWidgets();
        init();
    }

    private Button button(int x, int y, int w, Component text, Runnable action) {
        var control = new UiButton(x, y, w, 20, text, button -> action.run(), preferences, false);
        control.setSampleBackground(false);
        return addRenderableWidget(control);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (detail == null && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
            page(key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && canvas != null && canvas.contains(x, y)) {
            if (ThemeEffectPreview.background(group) != null) {
                ThemeEffects.preview(previewSettings, () -> preview.click(group, canvas, x, y, PageTransition.nowMs()));
                return true;
            }
        }
        var previousPreset = presetButton;
        boolean handled = super.mouseClicked(x, y, button);
        if (!handled && button == 0 && backdrop != null) backdrop.pulse(width, height, x, y);
        if (previousPreset != null && previousPreset != presetButton && getFocused() == previousPreset) {
            setFocused(presetButton);
        }
        return handled;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ThemeEffects.preview(previewSettings, () -> renderForm(g, mouseX, mouseY, partialTick));
    }

    // 1.21 的 Screen.render 会再次绘制原版背景，预览已由 renderForm 绘制。
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    private void renderForm(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, preferences.highContrast() ? 0xFF121723 : 0xF0121723);
        if (backdrop != null) {
            UiTheme.preview(UiTheme.resolve(settings.theme()), () -> backdrop.render(this, g, width, height, preferences));
            g.fill(0, 0, width, 24, 0xE0121723);
            g.fill(left - 3, Math.max(24, footer - 40), right + 3, height, 0xE0121723);
        }
        Component heading = detail == null ? title : fieldLabel(detail);
        if (detail != null) {
            JsonElement color = current(detail);
            if (color.isJsonArray()) {
                var colors = color.getAsJsonArray();
                heading = label("field_value", heading, label("page", colors.size() == 0 ? 0 : entry + 1, colors.size()));
                color = entry < colors.size() ? colors.get(entry) : null;
                if (color != null && color.isJsonObject()) color = color.getAsJsonObject().get("argb");
            }
            if (color != null) {
                g.fill(left, 8, left + 12, 20, 0xFFFFFFFF);
                g.fill(left + 1, 9, left + 11, 19, 0xFF262626);
                g.fill(left + 1, 9, left + 11, 19, readColor(color));
            }
        }
        g.drawCenteredString(font, heading, width / 2, 10, 0xFFFFFF);
        if (canvas != null && canvas.height() > 0) {
            preview.draw(group, g, font, canvas, groupName(group), mouseX, mouseY, preferences);
        }
        if (detail == null && pages > 1) {
            int columnX = canvas == null ? left : canvas.right() + 6;
            g.drawCenteredString(font, label("page", page + 1, pages), (columnX + right) / 2, footer - 33, 0xFFFFFF);
        }
        if (invalid) g.drawCenteredString(font, label("invalid"), width / 2, footer - 13, 0xFFAAAA);
        else if (fields.isEmpty()) g.drawCenteredString(font, label("empty"), width / 2, height / 2, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() {
        if (backdrop != null) backdrop.reset();
        else preview.close();
        super.removed();
    }

    @Override
    public void onClose() {
        if (detail != null) closeDetail();
        else minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component groupName(String group) {
        var background = ThemeEffectPreview.background(group);
        return background == null ? Component.translatableWithFallback("sh_better_ui.panel.theme.group." + group, group)
                : Component.translatable(background.titleKey());
    }

    private static Component fieldLabel(ThemeEffectFields.Field field) {
        String name = field.name();
        String path = String.join(".", field.path());
        String key = KEYS + "field." + name;
        if (field.path().size() == 1) return Component.translatableWithFallback(key, name);
        String section = field.path().get(0);
        return label("field_value", Component.translatableWithFallback(KEYS + "field." + section, section),
                Component.translatableWithFallback(key, path.substring(section.length() + 1)));
    }

    private static Component optionLabel(String option) {
        return Component.translatableWithFallback(KEYS + "option." + option.toLowerCase(Locale.ROOT), option);
    }

    private static Component label(String id, Object... args) {
        return Component.translatable(KEYS + id, args);
    }
}
