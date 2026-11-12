// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTimeline;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassQuality;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalog;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialEntry;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialParameter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** Preview-local drafts and clock. Only the kernel's active native pass draws the optical surfaces. */
final class LiquidGlassPreview implements PreviewScene {
    static final String ID = "sh_better_ui:liquid_glass";
    private static final String FIXTURE = "material_catalog/fixtures/";
    private static final ResourceLocation PANORAMA = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "textures/gui/title/background/panorama_0.png");
    private static final int ROW = 20;
    private static final int GAP = 3;
    private static final List<Integer> SPEEDS = List.of(25, 50, 100, 200);
    private static final Component PLAY = text("play");
    private static final Component PAUSE = text("pause");
    private static final Component SOLID = text("fallback");
    private static final Component CAPTION = text("view.caption");
    private static final Component BACKDROP_TEXT = text("tuning.scene");

    private enum Section { OPTICS, PLAYBACK, PARAMETERS, COMPARE, PRESETS }
    private enum ViewMode {
        SNELL, OPTICS_COMPARE, CALIBRATION;

        Component label() { return text("view." + name().toLowerCase(Locale.ROOT)); }
    }

    private final Saved saved;
    private MaterialCatalog catalog;
    private MaterialEntry material;
    private MaterialPreviewDraft draft;
    private LiquidGlassFixture fixture;
    private ResourceLocation texture;
    private UiTimeline timeline;
    private long resourceVersion = Long.MIN_VALUE;
    private boolean textureAvailable;
    private boolean panoramaAvailable;

    private Section section = Section.OPTICS;
    private ViewMode viewMode = ViewMode.SNELL;
    private boolean editingB;
    private boolean comparing;
    private boolean wipe;
    private int split = 50;
    private int field;
    private int speed = 100;
    private long realAnchor;
    private double virtualAnchor;
    private Component status;

    private Font font;
    private Bounds canvas;
    private int statusY;
    private UiPreferences preferences;
    private UiButton sectionButton;
    private UiButton compareButton;
    private UiButton targetButton;
    private UiButton viewButton;
    private UiButton play;
    private UiButton step;
    private UiButton difference;
    private UiButton undo;
    private UiSlider seek;
    private UiSlider divider;
    private final List<List<AbstractWidget>> controls = List.of(new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    private final List<Field> tuning = new ArrayList<>();
    private final List<Field> fields = new ArrayList<>();
    private Consumer<RenderService.Context> pending;

    LiquidGlassPreview() { this(null); }

    private LiquidGlassPreview(Saved saved) { this.saved = saved; }

    @Override
    public void layout(PreviewContext host) {
        pending = null;
        canvas = null;
        font = host.font();
        preferences = host.preferences();
        if (draft == null && !initialize()) return;
        if (!active()) return;
        var area = host.viewport();
        if (area.width() < 184 || area.height() < 112) return;

        controls.forEach(List::clear);
        tuning.clear();
        fields.clear();
        int left = area.x() + GAP;
        int width = area.width() - 2 * GAP;
        int navY = area.bottom() - 3 * (ROW + GAP);
        statusY = navY - font.lineHeight - GAP;
        canvas = new Bounds(left, area.y() + GAP, width, statusY - area.y() - 2 * GAP);

        sectionButton = button(host, cell(left, navY, width, 0, 4), sectionLabel(), () -> {
            section = Section.values()[(section.ordinal() + 1) % Section.values().length];
            syncControls();
        });
        sectionButton.setPreview(text("section_hint"));
        compareButton = button(host, cell(left, navY, width, 1, 4), text("compare"), () -> {
            comparing = !comparing;
            syncControls();
        });
        targetButton = button(host, cell(left, navY, width, 2, 4), text("target", draftName(editingB)), () -> {
            editingB = !editingB;
            syncControls();
        });
        viewButton = button(host, cell(left, navY, width, 3, 4), viewMode.label(), () -> {
            viewMode = ViewMode.values()[(viewMode.ordinal() + 1) % ViewMode.values().length];
            syncControls();
        });
        viewButton.setPreview(text("view.hint"));
        int row = navY + ROW + GAP;
        optics(host, left, row, width);
        playback(host, left, row, width);
        parameters(host, left, row, width);
        comparison(host, left, row, width);
        presets(host, left, row, width);
        syncControls();
    }

    private boolean initialize() {
        var service = NativeMaterialRoute.catalogService().orElse(null);
        var entry = service == null ? null : service.find(ID).orElse(null);
        if (entry == null) return false;
        try (var stream = LiquidGlassPreview.class.getResourceAsStream(
                "/assets/sh_better_ui/" + FIXTURE + "liquid-glass.json")) {
            if (stream == null) throw new IOException("Missing Liquid Glass fixture");
            fixture = Objects.requireNonNull(LiquidGlassFixture.read(new InputStreamReader(stream, StandardCharsets.UTF_8)));
            texture = Objects.requireNonNull(ResourceLocation.tryParse(ShBetterUi.MOD_ID + ":" + FIXTURE + fixture.texture()));
        } catch (IOException | RuntimeException failure) {
            ShBetterUi.LOGGER.warn("Could not load the Liquid Glass preview fixture", failure);
            return false;
        }

        catalog = service;
        material = entry;
        draft = new MaterialPreviewDraft(entry.defaultValues(), entry::validateParameters);
        timeline = new UiTimeline(fixture.fps(), fixture.frameCount());
        realAnchor = PageTransition.nowMs();
        if (saved != null) {
            try {
                draft.restore(saved.draft());
            } catch (IllegalArgumentException invalid) {
                status = text("restore_failed");
            }
            timeline.seek(saved.frame());
            section = saved.section();
            viewMode = saved.viewMode();
            field = saved.field();
            editingB = saved.editingB();
            comparing = saved.comparing();
            wipe = saved.wipe();
            split = saved.split();
            speed = saved.speed();
        } else if (!preferences.reducedMotion()) {
            timeline.play(0);
        }
        return true;
    }

    private boolean active() {
        return draft != null && NativeMaterialRoute.catalogService().orElse(null) == catalog
                && catalog.find(ID).orElse(null) == material;
    }

    private boolean hasTexture() {
        var resources = UnifiedUI.get().require(UIApis.RESOURCES);
        long version = resources.version();
        if (version != resourceVersion) {
            var manager = Minecraft.getInstance().getResourceManager();
            textureAvailable = manager.getResource(texture).isPresent();
            panoramaAvailable = manager.getResource(PANORAMA).isPresent();
            resourceVersion = version;
        }
        return textureAvailable || (viewMode != ViewMode.CALIBRATION && panoramaAvailable);
    }

    private void playback(PreviewContext host, int x, int y, int width) {
        var list = controls.get(Section.PLAYBACK.ordinal());
        play = button(host, cell(x, y, width, 0, 4), PLAY, () -> {
            long now = virtualTime(PageTransition.nowMs());
            if (timeline.sample(now).state() == UiTimeline.State.PLAYING) timeline.pause(now);
            else if (!preferences.reducedMotion()) timeline.play(now);
            syncPlayback(now);
        });
        play.setPreview(text(preferences.reducedMotion() ? "reduced_motion" : "play_hint"));
        list.add(play);
        step = button(host, cell(x, y, width, 1, 4), text("step"), () -> {
            long now = virtualTime(PageTransition.nowMs());
            timeline.seek(timeline.sample(now).index() + 1);
            syncPlayback(now);
        });
        list.add(step);
        list.add(button(host, cell(x, y, width, 2, 4), text("time_reset"), () -> {
            timeline.reset();
            syncPlayback(virtualTime(PageTransition.nowMs()));
        }));
        list.add(cycle(host, cell(x, y, width, 3, 4), text("speed"), SPEEDS, speed,
                value -> text("percent", value), value -> {
                    long now = PageTransition.nowMs();
                    virtualAnchor = virtualTime(now);
                    realAnchor = now;
                    speed = value;
                }));
        seek = host.add(new UiSlider(new Bounds(x, y + ROW + GAP, width, ROW), 0, fixture.frameCount() - 1, 1,
                timeline.sample(virtualTime(PageTransition.nowMs())).index(), preferences,
                index -> text("frame", index, fixture.frameCount() - 1), timeline::seek));
        list.add(seek);
    }

    private long virtualTime(long now) {
        return (long) (virtualAnchor + Math.max(0, now - realAnchor) * (speed / 100d));
    }

    private void optics(PreviewContext host, int x, int y, int width) {
        tuning.add(slider(host, new Bounds(x, y, width, ROW), "transmission", material.parameters().get("transmission"),
                true, (value, places) -> text("tuning.tint", value)));
        int lower = y + ROW + GAP;
        int columns = width >= 255 ? 3 : 2;
        tuning.add(slider(host, cell(x, lower, width, 0, columns), "scattering", material.parameters().get("scattering"),
                false, (value, places) -> text("tuning.blur", value)));
        tuning.add(slider(host, cell(x, lower, width, 1, columns), "refraction", material.parameters().get("refraction"),
                false, (value, places) -> text("tuning.warp", decimal(value, places))));
        if (columns == 3) {
            tuning.add(slider(host, cell(x, lower, width, 2, columns), "blurRadius", material.parameters().get("blurRadius"),
                    false, (value, places) -> text("tuning.radius", decimal(value, places))));
        }
        var list = controls.get(Section.OPTICS.ordinal());
        for (var control : tuning) list.add(control.widget());
    }

    private void parameters(PreviewContext host, int x, int y, int width) {
        var list = controls.get(Section.PARAMETERS.ordinal());
        var entries = material.parameters().entrySet().stream().filter(entry -> entry.getValue().editable()).toList();
        if (entries.isEmpty()) return;
        field = Math.min(field, entries.size() - 1);
        var ids = entries.stream().map(Map.Entry::getKey).toList();
        list.add(cycle(host, new Bounds(x, y, width, ROW), text("parameter"), ids, ids.get(field),
                LiquidGlassPreview::parameterName, key -> {
                    field = ids.indexOf(key);
                    syncControls();
                }));
        var bounds = new Bounds(x, y + ROW + GAP, width, ROW);
        for (var entry : entries) fields.add(field(host, bounds, entry.getKey(), entry.getValue()));
    }

    private Field field(PreviewContext host, Bounds bounds, String id, MaterialParameter parameter) {
        if (parameter.type().equals("enum")) {
            var widget = cycle(host, bounds, parameterName(id), parameter.choices(), (String) draft.values(editingB).get(id),
                    option -> text("choice." + option), next -> edit(id, next));
            return new Field(id, widget, next -> widget.setValue((String) next));
        }
        return slider(host, bounds, id, parameter, false, (value, places) -> text(
                parameter.unit().equals("gui") ? "value_gui" : "value", parameterName(id), decimal(value, places)));
    }

    private Field slider(PreviewContext host, Bounds bounds, String id, MaterialParameter parameter, boolean inverted,
                         BiFunction<Integer, Integer, Component> label) {
        var min = BigDecimal.valueOf(parameter.minimum()).stripTrailingZeros();
        var max = BigDecimal.valueOf(parameter.maximum()).stripTrailingZeros();
        var stepSize = BigDecimal.valueOf(parameter.step()).stripTrailingZeros();
        int places = Math.max(0, Math.max(min.scale(), Math.max(max.scale(), stepSize.scale())));
        int lower = sliderValue(min, places), upper = sliderValue(max, places);
        IntUnaryOperator position = inverted ? value -> lower + upper - value : value -> value;
        int initial = position.applyAsInt(sliderValue((Number) draft.values(editingB).get(id), places));
        var widget = host.add(new UiSlider(bounds, lower, upper, sliderValue(stepSize, places), initial, preferences,
                next -> label.apply(next, places), next -> edit(id, BigDecimal.valueOf(position.applyAsInt(next), places).doubleValue())));
        return new Field(id, widget, next -> widget.setValue(position.applyAsInt(sliderValue((Number) next, places))));
    }

    private static String decimal(int value, int places) {
        return BigDecimal.valueOf(value, places).stripTrailingZeros().toPlainString();
    }

    private static int sliderValue(Number value, int places) {
        return BigDecimal.valueOf(value.doubleValue()).movePointRight(places).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private void edit(String id, Object value) {
        var next = new LinkedHashMap<>(draft.values(editingB));
        next.put(id, value);
        try {
            draft.replace(editingB, next);
            status = null;
        } catch (IllegalArgumentException invalid) {
            showStatus(text("invalid_value", parameterName(id)));
        }
        syncControls();
    }

    private void comparison(PreviewContext host, int x, int y, int width) {
        var list = controls.get(Section.COMPARE.ordinal());
        list.add(cycle(host, cell(x, y, width, 0, 3), text("layout"), List.of(false, true), wipe,
                value -> text(value ? "wipe" : "side_by_side"), value -> {
                    wipe = value;
                    if (viewMode != ViewMode.OPTICS_COMPARE) comparing = true;
                    syncControls();
                }));
        list.add(button(host, cell(x, y, width, 1, 3), text("copy_other"), () -> {
            draft.copyToOther(editingB);
            showStatus(text("copied_other", draftName(editingB), draftName(!editingB)));
            syncControls();
        }));
        list.add(button(host, cell(x, y, width, 2, 3), text("swap"), () -> {
            draft.swap();
            syncControls();
        }));
        divider = host.add(new UiSlider(cell(x, y + ROW + GAP, width, 0, 2), 0, 100, 1, split, preferences,
                value -> text("divider", value), value -> split = value));
        list.add(divider);
        difference = button(host, cell(x, y + ROW + GAP, width, 1, 2), text("difference", 0),
                () -> Minecraft.getInstance().getNarrator().say(differences()));
        list.add(difference);
    }

    private Component differences() {
        var out = Component.empty();
        var a = draft.values(false);
        var b = draft.values(true);
        for (String id : material.parameters().keySet()) {
            if (Objects.equals(a.get(id), b.get(id))) continue;
            if (!out.getSiblings().isEmpty()) out.append("\n");
            out.append(text("difference_row", parameterName(id), printable(a.get(id)), printable(b.get(id))));
        }
        return out.getSiblings().isEmpty() ? text("difference", 0) : out;
    }

    private static Component printable(Object value) {
        if (value instanceof String choice) return text("choice." + choice);
        return Component.literal(String.valueOf(value));
    }

    private void presets(PreviewContext host, int x, int y, int width) {
        var list = controls.get(Section.PRESETS.ordinal());
        list.add(button(host, cell(x, y, width, 0, 2), text("defaults"), () -> {
            draft.reset(editingB);
            showStatus(text("defaults_applied", draftName(editingB)));
            syncControls();
        }));
        undo = button(host, cell(x, y, width, 1, 2), text("undo"), () -> {
            draft.undo();
            showStatus(text("undone"));
            syncControls();
        });
        list.add(undo);
        list.add(button(host, cell(x, y + ROW + GAP, width, 0, 2), text("import"), this::importPreset));
        list.add(button(host, cell(x, y + ROW + GAP, width, 1, 2), text("export"), () -> {
            try {
                String json = catalog.exportPreset(ID, draft.values(editingB));
                Minecraft.getInstance().keyboardHandler.setClipboard(json);
                showStatus(text("exported", draftName(editingB)));
            } catch (IllegalArgumentException invalid) {
                showStatus(text("invalid_preset", detail(invalid)));
            }
        }));
    }

    private void importPreset() {
        try {
            var preset = catalog.importPreset(Minecraft.getInstance().keyboardHandler.getClipboard());
            if (!ID.equals(preset.materialId())) {
                showStatus(text("wrong_preset", preset.materialId()));
                return;
            }
            draft.replace(editingB, preset.parameters());
            showStatus(text("imported", draftName(editingB)));
            syncControls();
        } catch (IllegalArgumentException invalid) {
            showStatus(text("invalid_preset", detail(invalid)));
        }
    }

    private static String detail(IllegalArgumentException invalid) {
        String message = Objects.toString(invalid.getMessage(), "");
        return message.substring(0, Math.min(message.length(), 120));
    }

    private void showStatus(Component message) {
        status = message;
        Minecraft.getInstance().getNarrator().say(message);
    }

    private void syncControls() {
        if (section == Section.OPTICS && viewMode == ViewMode.CALIBRATION) section = Section.PLAYBACK;
        sectionButton.setMessage(sectionLabel());
        for (var pane : Section.values()) {
            boolean visible = section == pane;
            for (var widget : controls.get(pane.ordinal())) widget.visible = widget.active = visible;
        }
        var values = draft.values(editingB);
        for (var control : tuning) control.set().accept(values.get(control.id()));
        for (int i = 0; i < fields.size(); i++) {
            var control = fields.get(i);
            control.widget().visible = control.widget().active = section == Section.PARAMETERS && field == i;
            control.set().accept(values.get(control.id()));
        }
        compareButton.setSelected(comparing);
        compareButton.setMessage(text(comparing ? "compare_on" : "compare_off"));
        compareButton.active = viewMode != ViewMode.OPTICS_COMPARE;
        viewButton.setMessage(viewMode.label());
        viewButton.setSelected(viewMode == ViewMode.OPTICS_COMPARE);
        targetButton.setMessage(text("target", draftName(editingB)));
        undo.active &= draft.canUndo();
        divider.active &= isComparing() && wipe;
        long count = material.parameters().keySet().stream()
                .filter(id -> !Objects.equals(draft.values(false).get(id), draft.values(true).get(id))).count();
        difference.setMessage(text("difference", count));
        difference.setPreview(differences());
        syncPlayback(virtualTime(PageTransition.nowMs()));
    }

    private UiTimeline.Frame syncPlayback(long now) {
        var frame = timeline.sample(now);
        seek.setValue(frame.index());
        play.active = section == Section.PLAYBACK && !preferences.reducedMotion();
        Component label = frame.state() == UiTimeline.State.PLAYING ? PAUSE : PLAY;
        if (play.getMessage() != label) play.setMessage(label);
        step.active = section == Section.PLAYBACK && frame.index() < fixture.frameCount() - 1;
        return frame;
    }

    @Override
    public void render(UiFrame frame) {
        pending = null;
        var area = frame.viewport();
        if (!active() || !hasTexture()) {
            GalleryPreviewLayout.drawCentered(frame.graphics(), font, text("unavailable"), area.x(), area.y(), area.width());
            return;
        }
        if (canvas == null) {
            GalleryPreviewLayout.drawCentered(frame.graphics(), font, text("compact"), area.x(), area.y(), area.width());
            return;
        }

        long now = virtualTime(frame.nowMs());
        if (preferences.reducedMotion()) timeline.pause(now);
        var sample = fixture.sample(syncPlayback(now).seconds() * 1000);
        if (isComparing() && !wipe) {
            int half = (canvas.width() - GAP) / 2;
            var a = paintBackdrop(frame.graphics(), new Bounds(canvas.x(), canvas.y(), half, canvas.height()), sample);
            var b = paintBackdrop(frame.graphics(), new Bounds(canvas.right() - half, canvas.y(), half, canvas.height()), sample);
            if (a != null && b != null) {
                pending = compare(draw(a, a.area(), sample, false), draw(b, b.area(), sample, true), null);
            }
        } else {
            var view = paintBackdrop(frame.graphics(), canvas, sample);
            if (view != null) pending = isComparing() ? wipe(view, sample) : draw(view, view.area(), sample, editingB);
        }
        var message = status == null && !preferences.useBackdropBlur() ? SOLID : status;
        if (message != null) {
            var lines = font.split(message, canvas.width());
            if (!lines.isEmpty()) frame.graphics().drawString(font, lines.get(0), canvas.x(), statusY,
                    LiquidGlassTheme.textColor(), false);
        }
    }

    private boolean isComparing() { return comparing || viewMode == ViewMode.OPTICS_COMPARE; }

    private View paintBackdrop(GuiGraphics graphics, Bounds slot, LiquidGlassFixture.Sample sample) {
        float scale = Math.min(slot.width() / (float) fixture.width(), slot.height() / (float) fixture.height());
        if (!(scale > 0)) return null;
        int width = Math.round(fixture.width() * scale);
        int height = Math.round(fixture.height() * scale);
        int x = slot.x() + (slot.width() - width) / 2;
        int y = slot.y() + (slot.height() - height) / 2;
        if (viewMode != ViewMode.CALIBRATION && panoramaAvailable) {
            // Sample the vanilla panorama in normalized proportions so resource-pack sizes can vary.
            float pan = 24f + 80f * sample.objectX() / fixture.width();
            graphics.blit(PANORAMA, x, y, width, height, pan, 240f, 896, 538, 1024, 1024);
            int textWidth = font.width(BACKDROP_TEXT);
            if (textWidth + 8 <= width) {
                int bevelY = y + Math.round(fixture.surface().y() * scale);
                // Leave the lower strokes in the bevel's sample range so the two optics can be compared on text.
                graphics.drawString(font, BACKDROP_TEXT, x + (width - textWidth) / 2,
                        bevelY - font.lineHeight / 4, 0xfff5f7ff, false);
            }
        } else {
            graphics.blit(texture, x, y, width, height, 0f, 0f, fixture.width(), fixture.height(),
                    fixture.width(), fixture.height());
            var object = fixture.movingObject();
            graphics.fill(x + Math.round(sample.objectX() * scale), y + Math.round(sample.objectY() * scale),
                    x + Math.round((sample.objectX() + object.width()) * scale),
                    y + Math.round((sample.objectY() + object.height()) * scale), object.argb());
        }
        return new View(new Bounds(x, y, width, height), scale);
    }

    private Consumer<RenderService.Context> wipe(View view, LiquidGlassFixture.Sample sample) {
        var area = view.area();
        int left = Math.round(area.width() * split / 100f);
        int middle = area.x() + left;
        var first = draw(view, new Bounds(area.x(), area.y(), left, area.height()), sample, false);
        var second = draw(view, new Bounds(middle, area.y(), area.width() - left, area.height()), sample, true);
        Bounds line = left > 0 && left < area.width() ? new Bounds(middle, area.y(), 1, area.height()) : null;
        return compare(first, second, line);
    }

    private static Consumer<RenderService.Context> compare(OpticalDraw a, OpticalDraw b, Bounds divider) {
        return pass -> {
            // A solid fallback must never paint over pixels before the first optical capture.
            if (a.isFallback() && !b.isFallback()) {
                b.accept(pass);
                a.accept(pass);
            } else {
                a.accept(pass);
                b.accept(pass);
            }
            if (divider != null) pass.fill(divider.x(), divider.y(), divider.right(), divider.bottom(), 0xffffffff);
        };
    }

    private OpticalDraw draw(View view, Bounds clip, LiquidGlassFixture.Sample sample, boolean second) {
        boolean comparingOptics = viewMode == ViewMode.OPTICS_COMPARE;
        boolean calibration = viewMode == ViewMode.CALIBRATION;
        var values = draft.values(comparingOptics ? editingB : second);
        var optics = new GlassOptics(number(values, "transmission"), number(values, "refraction"), number(values, "lensing"),
                number(values, "scattering"), number(values, "edgeHighlight"), number(values, "thickness"), number(values, "blurRadius"));
        var quality = preferences.useBackdropBlur() ? GlassQuality.valueOf((String) values.get("quality")) : GlassQuality.FALLBACK;
        var glass = new GlassMaterial(GlassVariant.REGULAR, optics, quality,
                GlassMaterial.Foreground.valueOf((String) values.get("foreground")));
        var profile = calibration || (comparingOptics && !second) ? GlassDraw.Profile.LEGACY : GlassDraw.Profile.SNELL;
        var region = fixture.surface();
        Caption caption = null;
        GlassDraw.ForegroundBounds foreground = null;
        if (!calibration) {
            float scale = view.scale();
            region = new GlassRegion(view.area().x() + region.x() * scale, view.area().y() + region.y() * scale,
                    region.width() * scale, region.height() * scale, region.radius() * scale, region.shape());
            int width = font.width(CAPTION);
            if (region.width() >= width + 14 && region.height() >= font.lineHeight + 10) {
                int x = Math.round(region.x() + (region.width() - width) / 2);
                int y = Math.round(region.y() + (region.height() - font.lineHeight) / 2);
                caption = new Caption(x, y);
                foreground = new GlassDraw.ForegroundBounds(x - 3, y - 2, width + 6, font.lineHeight + 4);
            }
        }
        var draw = new GlassDraw(region, glass, sample.lightX(), sample.lightY(), sample.lightStrength(), profile, foreground);
        Component marker = comparingOptics ? text(second ? "view.new_profile" : "view.old_profile")
                : comparing ? draftName(second) : null;
        return new OpticalDraw(calibration ? view : null, clip, draw, caption, marker, marker == null ? 0 : font.width(marker));
    }

    Consumer<RenderService.Context> takeDraw() {
        var draw = pending;
        pending = null;
        return draw;
    }

    @Override
    public void suspend() {
        pending = null;
        if (timeline != null) timeline.pause(virtualTime(PageTransition.nowMs()));
        tuning.clear();
        fields.clear();
        controls.forEach(List::clear);
    }

    @Override
    public Supplier<? extends PreviewScene> saveForRemount() {
        if (draft == null || !active()) return null;
        var snapshot = new Saved(draft.snapshot(), timeline.sample(virtualTime(PageTransition.nowMs())).index(),
                section, viewMode, field, editingB, comparing, wipe, split, speed);
        return () -> new LiquidGlassPreview(snapshot);
    }

    private UiButton button(PreviewContext host, Bounds area, Component title, Runnable action) {
        return GalleryPreviewLayout.addButton(host, area, title, null, () -> { if (active()) action.run(); });
    }

    private <T> CycleButton<T> cycle(PreviewContext host, Bounds area, Component title, List<T> choices, T value,
                                     Function<T, Component> display, Consumer<T> changed) {
        return host.add(CycleButton.<T>builder(display).withValues(choices).withInitialValue(value)
                .create(area.x(), area.y(), area.width(), area.height(), title,
                        (button, selected) -> { if (active()) changed.accept(selected); }));
    }

    private static Bounds cell(int x, int y, int width, int column, int columns) {
        return cell(x, y, width, column, columns, ROW);
    }

    private static Bounds cell(int x, int y, int width, int column, int columns, int height) {
        int shared = width - (columns - 1) * GAP;
        int left = shared * column / columns;
        int right = shared * (column + 1) / columns;
        return new Bounds(x + left + column * GAP, y, right - left, height);
    }

    private static float number(Map<String, Object> values, String id) { return ((Number) values.get(id)).floatValue(); }

    private Component sectionLabel() { return text("section." + section.name().toLowerCase(Locale.ROOT)); }

    private static Component parameterName(String id) { return text("parameter." + id); }

    private static Component draftName(boolean second) { return text(second ? "draft_b" : "draft_a"); }

    private static Component text(String key, Object... args) { return label("gallery.material." + key, args); }

    private record Field(String id, AbstractWidget widget, Consumer<Object> set) {}

    private record View(Bounds area, float scale) {}

    private record Caption(int x, int y) {}

    private record OpticalDraw(View calibration, Bounds clip, GlassDraw glass, Caption caption, Component marker, int markerWidth)
            implements Consumer<RenderService.Context> {
        boolean isFallback() { return glass.material().quality() == GlassQuality.FALLBACK; }

        @Override public void accept(RenderService.Context pass) {
            if (clip.width() == 0) return;
            pass.clip(clip.x(), clip.y(), clip.right(), clip.bottom(), () -> {
                if (calibration == null) {
                    pass.glass(List.of(glass));
                } else {
                    pass.transform(calibration.area().x(), calibration.area().y(), calibration.scale(),
                            () -> pass.glass(List.of(glass)));
                }
                if (caption != null) pass.text(CAPTION, caption.x(), caption.y(), glass.material().foreground().argb());
                if (marker != null && clip.width() >= markerWidth + 10 && clip.height() >= 14) {
                    pass.fill(clip.x() + 2, clip.y() + 2, clip.x() + markerWidth + 8, clip.y() + 14, 0xff20252c);
                    pass.text(marker, clip.x() + 5, clip.y() + 4, 0xffffffff);
                }
            });
        }
    }

    private record Saved(MaterialPreviewDraft.Snapshot draft, int frame, Section section, ViewMode viewMode, int field,
                         boolean editingB, boolean comparing, boolean wipe, int split, int speed) {}
}
