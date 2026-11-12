// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotBackground;
import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.visual.RichTextView;
import com.dishanhai.sh_better_ui.client.visual.VisualLayoutBinding;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.InlineNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.LocalizedText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ParagraphBreak;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.dishanhai.sh_better_ui.client.gui.panel.RichTextEditorProperties.Field;
import static com.dishanhai.sh_better_ui.client.gui.panel.RichTextEditorProperties.Flag;

/** Native preview and local authoring of a passive RichDocument. */
public final class RichTextEditorScreen extends Screen {
    private final UiDotBackground backdrop = new UiDotBackground();
    private static final String KEY = "sh_better_ui.rich_text_editor.";
    private static final int MARGIN = 8;
    private static final int GAP = 4;
    private static final int BODY_Y = 68;
    private static final int ROW = 21;
    private static final int CONTROL_HEIGHT = 19;
    private static final int MIN_TIMELINE_MS = 5000;
    private static final int INPUT_LIMIT = 256 * 1024;
    private static final UiId PULSE = UiId.parse("sh_better_ui:textures/gui/rich_text/pulse.gif");

    private enum Tab {
        NODES("nodes"), PROPERTIES("properties"), TIMELINE("timeline"), FILES("files");
        private final String key;
        Tab(String key) { this.key = key; }
    }

    private record Caption(Component text, int x, int y, int width) {}

    private static final class TextField extends MultiLineEditBox {
        private TextField(Font font, int x, int y, int width, Component name) {
            super(font, x, y, width, 35, name, name);
        }

        private boolean scroll(double delta) {
            double before = scrollAmount();
            setScrollAmount(before - delta * 9);
            return before != scrollAmount();
        }
    }

    private final ModuleContext module;
    private final Screen parent;
    private final GuiEventListener returnFocus;
    private final Map<String, AbstractWidget> widgets = new LinkedHashMap<>();
    private final List<Caption> captions = new ArrayList<>();
    private RichTextEditorDocument model;
    private RichTextEditorProperties draft;
    private Tab tab = Tab.PROPERTIES;
    private ScopeHandle scope;
    private AlignmentLayout.Node content;
    private VisualLayoutBinding binding;
    private RichTextView view;
    private RichLayoutSnapshot layout;
    private int listStart;
    private int listRows;
    private int scrollY;
    private boolean overlay;
    private boolean profile;
    private boolean playing;
    private int playhead;
    private int timelineMs = MIN_TIMELINE_MS;
    private long lastPlaybackTick;
    private long prepareNanos;
    private long drawNanos;
    private long layoutChanges;
    private boolean reloading;
    private String refreshFocus;
    private Component status;
    private boolean statusError;

    public RichTextEditorScreen(ModuleContext module, Screen parent) {
        super(label("title"));
        this.module = module;
        this.parent = parent;
        returnFocus = parent == null ? null : parent.getFocused();
    }

    @Override protected void init() {
        if (model == null) {
            model = new RichTextEditorDocument(preset(true));
            if (!Files.notExists(savedPath(), LinkOption.NOFOLLOW_LINKS)) reopen();
        }
        if (scope == null || !scope.isOpen()) {
            var engine = module.engine();
            scope = engine.require(UIApis.SCREENS).mount(module, this, id("screen"));
            var visual = ShBetterUiApi.layout().create(viewport());
            content = visual.node("rich_text_editor", AlignmentFrame.box(new Rect(0, 0, 0, 0)));
            view = RichTextView.attach(module, scope, content);
            binding = engine.require(UIApis.LAYOUT).bindVisual(module, scope, visual, time -> arrange());
        } else {
            view.invalidate();
        }
        rebuild(refreshFocus == null ? tab.key : refreshFocus);
    }

    private void rebuild(String focus) {
        refreshFocus = null;
        clearWidgets();
        setFocused(null);
        widgets.clear();
        captions.clear();

        button("undo", 24, 0, 4, width - 2 * MARGIN, () -> history(false));
        button("redo", 24, 1, 4, width - 2 * MARGIN, () -> history(true));
        button("overlay", 24, 2, 4, width - 2 * MARGIN, () -> {
            overlay = !overlay;
            updateActions();
        });
        button("close", 24, 3, 4, width - 2 * MARGIN, this::onClose);
        for (var value : Tab.values()) {
            var control = button(value.key, 46, value.ordinal(), Tab.values().length, width - 2 * MARGIN,
                    () -> switchTab(value));
            control.setMessage(marked(value.key, tab == value));
        }
        switch (tab) {
            case NODES -> buildNodes();
            case PROPERTIES -> buildProperties();
            case TIMELINE -> buildTimeline();
            case FILES -> buildFiles();
        }
        updateActions();
        var target = widgets.get(focus);
        if (target == null || !target.active) target = widgets.get(tab.key);
        if (target != null) setInitialFocus(target);
    }

    private void buildNodes() {
        int count = model.document().nodes().size();
        listRows = Math.max(1, (bodyBottom() - BODY_Y - 4 * ROW) / ROW);
        listStart = Math.min(listStart, Math.max(0, count - listRows));
        int y = BODY_Y;
        for (int slot = 0; slot < listRows && slot + listStart < count; slot++) {
            int index = slot + listStart;
            var node = model.document().nodes().get(index);
            String key = "node_" + slot;
            String name = label("node_row", index + 1, label(kind(node)).getString()).getString();
            var row = add(key, Button.builder(Component.literal(name).withStyle(
                            index == model.selection() ? ChatFormatting.UNDERLINE : ChatFormatting.RESET),
                    button -> select(index, key)).bounds(MARGIN, y, panelWidth(), CONTROL_HEIGHT).build());
            row.setTooltip(Tooltip.create(Component.literal(node.id().toString())));
            y += ROW;
        }
        if (count == 0) caption(label("empty"), MARGIN, y + 3, panelWidth());
        y = bodyBottom() - 4 * ROW;
        button("previous", y, 0, 2, panelWidth(), () -> scrollList(-listRows, "previous")).active = listStart > 0;
        button("next", y, 1, 2, panelWidth(), () -> scrollList(listRows, "next")).active = listStart + listRows < count;
        button("move_up", y + ROW, 0, 3, panelWidth(), () -> move(-1, "move_up")).active = model.selection() > 0;
        button("move_down", y + ROW, 1, 3, panelWidth(), () -> move(1, "move_down")).active =
                model.selection() >= 0 && model.selection() + 1 < count;
        button("delete", y + ROW, 2, 3, panelWidth(), this::delete).active = model.selected() != null;
        button("add_text", y + 2 * ROW, 0, 2, panelWidth(), () -> insert(new TextRun(newId(), label("sample_new").getString()), "add_text"));
        button("add_localized", y + 2 * ROW, 1, 2, panelWidth(),
                () -> insert(new LocalizedText(newId(), KEY + "sample_new", label("sample_new").getString(),
                        List.of(), RichStyle.EMPTY), "add_localized"));
        button("add_image", y + 3 * ROW, 0, 2, panelWidth(), () -> insert(sampleImage(newId()), "add_image"));
        button("add_paragraph", y + 3 * ROW, 1, 2, panelWidth(), () -> insert(new ParagraphBreak(newId(), 4), "add_paragraph"));
    }

    private void buildProperties() {
        var node = model.selected();
        if (node == null) {
            caption(label("empty"), MARGIN, BODY_Y, panelWidth());
            return;
        }
        if (draft == null || draft.source() != node) draft = new RichTextEditorProperties(node);
        if (node instanceof TextRun) {
            Component name = label(Field.TEXT.key());
            caption(name, MARGIN, BODY_Y, panelWidth());
            var field = add(Field.TEXT.key(), new TextField(font, MARGIN, BODY_Y + 10, panelWidth(), name));
            field.setCharacterLimit(INPUT_LIMIT);
            field.setValue(draft.get(Field.TEXT));
            field.setValueListener(value -> change(Field.TEXT, value));
            buildStyle(BODY_Y + 56);
        } else if (node instanceof LocalizedText) {
            field(Field.KEY, BODY_Y, 0, 1);
            field(Field.FALLBACK, BODY_Y + 28, 0, 1);
            buildStyle(BODY_Y + 56);
        } else if (node instanceof ImageNode) {
            field(Field.TEXTURE, BODY_Y, 0, 1);
            field(Field.ALT, BODY_Y + 30, 0, 1);
            field(Field.WIDTH, BODY_Y + 60, 0, 2);
            field(Field.HEIGHT, BODY_Y + 60, 1, 2);
        } else if (node instanceof ParagraphBreak) {
            field(Field.SPACING, BODY_Y, 0, 1);
        } else if (node instanceof RichExtensionNode extension) {
            caption(label("inert_extension", extension.type(), extension.version()), MARGIN, BODY_Y, panelWidth());
            field(Field.ALT, BODY_Y + 16, 0, 1);
        }
        int y = bodyBottom() - CONTROL_HEIGHT;
        button("apply", y, 0, 2, panelWidth(), () -> apply(true));
        button("reset", y, 1, 2, panelWidth(), () -> {
            draft = null;
            setStatus("reset_done", false);
            refresh(tab.key);
        });
    }

    private void buildStyle(int y) {
        field(Field.COLOR, y, 0, 2);
        field(Field.FONT, y, 1, 2);
        var flags = Flag.values();
        for (int index = 0; index < flags.length; index++) {
            Flag flag = flags[index];
            var control = button(flag.key(), y + 30 + index / 3 * ROW, index % 3, index < 3 ? 3 : 2,
                    panelWidth(), () -> {
                        draft.toggle(flag);
                        updateActions();
                    });
            control.setMessage(marked(flag.key(), draft.enabled(flag)));
        }
    }

    private void field(Field field, int y, int column, int columns) {
        int x = MARGIN + columnStart(panelWidth(), column, columns);
        int fieldWidth = columnWidth(panelWidth(), column, columns);
        Component name = label(field.key());
        caption(name, x, y, fieldWidth);
        var edit = add(field.key(), new EditBox(font, x, y + 10, fieldWidth, CONTROL_HEIGHT, name));
        edit.setTooltip(Tooltip.create(name));
        edit.setMaxLength(INPUT_LIMIT);
        edit.setValue(draft.get(field));
        edit.setResponder(value -> change(field, value));
    }

    private void change(Field field, String value) {
        draft.set(field, value);
        updateActions();
    }

    private void buildTimeline() {
        if (panelWidth() > 8) {
            add("seek", new UiSlider(new Bounds(MARGIN, BODY_Y, panelWidth(), CONTROL_HEIGHT), 0, timelineMs,
                    1, playhead, UiPreferences.configured(), value -> label("time", value), value -> {
                        playing = false;
                        playhead = value;
                        updateActions();
                    }));
        }
        button("play", BODY_Y + ROW, 0, 2, panelWidth(), this::togglePlayback);
        button("rewind", BODY_Y + ROW, 1, 2, panelWidth(), () -> {
            playing = false;
            playhead = 0;
            updateActions();
        });
        button("profile", BODY_Y + 2 * ROW, 0, 1, panelWidth(), () -> {
            profile = !profile;
            updateActions();
        });
    }

    private void buildFiles() {
        button("save", BODY_Y, 0, 2, panelWidth(), this::save);
        button("reopen", BODY_Y, 1, 2, panelWidth(), this::requestReopen);
        button("import_json", BODY_Y + ROW, 0, 2, panelWidth(), this::importJson);
        button("export_json", BODY_Y + ROW, 1, 2, panelWidth(), () -> export(false));
        button("export_java", BODY_Y + 2 * ROW, 0, 2, panelWidth(), () -> export(true));
        button("reload_assets", BODY_Y + 2 * ROW, 1, 2, panelWidth(), this::reloadAssets);
        caption(label("presets"), MARGIN, BODY_Y + 3 * ROW + 3, panelWidth());
        button("preset_text", BODY_Y + 3 * ROW + 16, 0, 2, panelWidth(), () -> loadPreset(false));
        button("preset_media", BODY_Y + 3 * ROW + 16, 1, 2, panelWidth(), () -> loadPreset(true));
        button("save_preset", BODY_Y + 4 * ROW + 16, 0, 2, panelWidth(), this::savePreset);
        button("load_preset", BODY_Y + 4 * ROW + 16, 1, 2, panelWidth(), this::loadPreset);
    }

    private void updateActions() {
        if (model == null) return;
        boolean pending = draft != null && draft.dirty();
        active("apply", pending);
        active("reset", pending);
        active("undo", pending || model.canUndo());
        active("redo", !pending && model.canRedo());
        active("reload_assets", !reloading);
        message("overlay", marked("overlay", overlay));
        message("profile", marked("profile", profile));
        message("play", label(playing ? "pause" : "play"));
        if (widgets.get("seek") instanceof UiSlider slider) slider.setValue(playhead);
        if (draft != null) {
            for (Flag flag : Flag.values()) message(flag.key(), marked(flag.key(), draft.enabled(flag)));
        }
    }

    private void active(String key, boolean value) {
        var widget = widgets.get(key);
        if (widget != null) widget.active = value;
    }

    private void message(String key, Component value) {
        var widget = widgets.get(key);
        if (widget != null && !widget.getMessage().equals(value)) widget.setMessage(value);
    }

    private void switchTab(Tab next) {
        if (tab == next || !apply(false)) return;
        tab = next;
        if (tab == Tab.NODES) showSelectedRow();
        refresh(next.key);
    }

    private boolean apply(boolean explicit) {
        if (draft == null || !draft.dirty()) return true;
        if (!edit(() -> model.change(draft.node()))) return false;
        draft = null;
        if (explicit) {
            setStatus("applied", false);
            refresh(tab.key);
        }
        return true;
    }

    private boolean edit(Runnable action) {
        try {
            var before = model.document();
            action.run();
            if (before != model.document() && view != null) view.invalidate();
            return true;
        } catch (IllegalArgumentException invalid) {
            LogUtils.getLogger().debug("Rejected local rich text editor input", invalid);
            setStatus("invalid", true);
            return false;
        }
    }

    private void select(int index, String focus) {
        model.select(index);
        draft = null;
        refresh(focus);
    }

    private void insert(InlineNode node, String focus) {
        if (!edit(() -> model.insert(node))) return;
        draft = null;
        showSelectedRow();
        refresh(focus);
    }

    private void move(int step, String focus) {
        if (!edit(() -> model.move(step))) return;
        draft = null;
        showSelectedRow();
        refresh(focus);
    }

    private void delete() {
        var node = model.selected();
        if (node == null) return;
        confirm("delete_title", label("delete_details", node.id()), "delete", () -> {
            if (!edit(model::delete)) return;
            draft = null;
            showSelectedRow();
            refresh(tab.key);
        });
    }

    private void history(boolean redo) {
        if (draft != null && draft.dirty()) {
            if (redo) return;
        } else if (!edit(redo ? model::redo : model::undo)) {
            return;
        }
        draft = null;
        showSelectedRow();
        refresh(redo ? "redo" : "undo");
    }

    private void showSelectedRow() {
        int selection = Math.max(0, model.selection());
        listStart = Math.min(selection, Math.max(listStart, selection - Math.max(0, listRows - 1)));
    }

    private void scrollList(int delta, String focus) {
        int next = Math.max(0, Math.min(Math.max(0, model.document().nodes().size() - listRows), listStart + delta));
        if (next != listStart) {
            listStart = next;
            refresh(focus);
        }
    }

    private void togglePlayback() {
        if (UiPreferences.configured().reducedMotion()) {
            setStatus("reduced_motion", false);
            return;
        }
        playing = !playing;
        lastPlaybackTick = UiFrameScheduler.nowMs();
        updateActions();
    }

    private void samplePlayback(long now, UiPreferences preferences) {
        if (!playing) return;
        if (preferences.reducedMotion()) {
            playing = false;
            return;
        }
        playhead = (int) Math.floorMod(playhead + Math.max(0, now - lastPlaybackTick), (long) timelineMs);
        lastPlaybackTick = now;
    }

    private Path directory() { return minecraft.gameDirectory.toPath().resolve("sh_better_ui").resolve("rich_text_editor"); }
    private Path savedPath() { return directory().resolve("document.json"); }
    private Path presetPath() { return directory().resolve("preset.json"); }

    private void save() {
        boolean pending = draft != null && draft.dirty();
        if (!apply(false)) return;
        if (pending) refresh(tab.key);
        try {
            RichTextEditorDocument.write(savedPath(), RichTextEditorDocument.encode(model.document()));
            model.markSaved();
            setStatus("saved", false);
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            ioError("save_error", failure);
        }
    }

    private void requestReopen() {
        if (dirty()) confirm("discard_title", label("reopen_details"), "reopen", this::reopen);
        else reopen();
    }

    private void reopen() {
        try {
            model.reopen(RichTextEditorDocument.load(savedPath()));
            resetDocumentView();
            setStatus("reopened", false);
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            ioError("reopen_error", failure);
        }
    }

    private void importJson() {
        try {
            var imported = RichTextEditorDocument.decode(minecraft.keyboardHandler.getClipboard());
            if (!edit(() -> model.replace(imported))) return;
            resetDocumentView();
            setStatus("imported", false);
        } catch (IllegalArgumentException failure) {
            ioError("import_error", failure);
        }
    }

    private void loadPreset(boolean media) {
        if (!edit(() -> model.replace(preset(media)))) return;
        resetDocumentView();
        setStatus("preset_loaded", false);
    }

    private void savePreset() {
        try {
            RichTextEditorDocument.write(presetPath(), RichTextEditorDocument.encode(model.document()));
            setStatus("preset_saved", false);
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            ioError("preset_save_error", failure);
        }
    }

    private void loadPreset() {
        try {
            var preset = RichTextEditorDocument.load(presetPath());
            if (!edit(() -> model.replace(preset))) return;
            resetDocumentView();
            setStatus("preset_loaded", false);
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            ioError("preset_load_error", failure);
        }
    }

    private void resetDocumentView() {
        draft = null;
        listStart = 0;
        scrollY = 0;
        if (view != null) view.invalidate();
        refresh(tab.key);
    }

    private void export(boolean java) {
        if (!apply(false)) return;
        String name = java ? "ExportedRichText.java" : "export.json";
        try {
            String output = java ? RichTextEditorDocument.javaSource(model.document())
                    : RichTextEditorDocument.encode(model.document());
            RichTextEditorDocument.write(directory().resolve(name), output);
            status = label("exported", name);
            statusError = false;
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            ioError("export_error", failure);
        }
    }

    private void reloadAssets() {
        if (reloading) return;
        reloading = true;
        setStatus("reloading", false);
        updateActions();
        minecraft.reloadResourcePacks().whenComplete((unused, failure) -> minecraft.execute(() -> {
            reloading = false;
            if (failure == null) setStatus("reloaded", false);
            else ioError("reload_error", failure);
        }));
    }

    private void ioError(String key, Throwable failure) {
        LogUtils.getLogger().warn("Rich text editor operation {} failed", key, failure);
        setStatus(key, true);
    }

    private boolean dirty() { return model != null && (model.dirty() || draft != null && draft.dirty()); }

    @Override public void onClose() {
        if (dirty()) confirm("discard_title", label("discard_details"), "discard", this::leave);
        else leave();
    }

    private void leave() {
        minecraft.setScreen(parent);
        if (parent != null && returnFocus != null && parent.children().contains(returnFocus)) parent.setFocused(returnFocus);
    }

    private void confirm(String title, Component details, String action, Runnable confirmed) {
        String focus = widgets.entrySet().stream().filter(entry -> entry.getValue() == getFocused())
                .map(Map.Entry::getKey).findFirst().orElse(tab.key);
        UiConfirmation.open(this, new UiConfirmation.Prompt(label(title), details, label(action), label("cancel")),
                UiPreferences.configured(), null, () -> widgets.get(focus), confirmed);
    }

    @Override public void removed() {
        playing = false;
        if (scope != null) scope.close();
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }

    private void arrange() {
        var clip = preview();
        long before = System.nanoTime();
        var next = view.prepare(model.document(), font, clip.width());
        prepareNanos = System.nanoTime() - before;
        if (layout != next) layoutChanges++;
        layout = next;
        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, layout.height() - clip.height())));
        content.update(AlignmentFrame.box(new Rect(clip.x(), clip.y(), clip.width(), clip.height()))
                .withClip(new Rect(0, 0, clip.width(), clip.height())));
    }

    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        flushWidgets();
        var preferences = UiPreferences.configured();
        long now = UiFrameScheduler.nowMs();
        samplePlayback(now, preferences);
        backdrop.render(this, graphics, width, height, preferences);
        drawCaption(graphics, title, MARGIN, 10, width - 2 * MARGIN, DarkModeTheme.textColor());
        var clip = preview();
        drawCaption(graphics, label("preview"), clip.x(), BODY_Y, clip.width(), DarkModeTheme.textColor());
        drawCaption(graphics, label(dirty() ? "unsaved" : "clean"), clip.x(),
                Math.max(BODY_Y, bodyBottom() + 2), clip.width(), DarkModeTheme.secondaryTextColor());
        if (clip.width() > 0 && clip.height() > 0) {
            graphics.fill(clip.x() - 2, clip.y() - 2, clip.x() + clip.width() + 2, clip.y() + clip.height() + 2,
                    DarkModeTheme.borderColor());
            graphics.fill(clip.x() - 1, clip.y() - 1, clip.x() + clip.width() + 1, clip.y() + clip.height() + 1,
                    DarkModeTheme.surfaceColor());
        }
        if (minecraft.getOverlay() != null) {
            if (view != null) view.invalidate();
        } else if (binding != null && binding.isActive() && clip.width() > 0 && clip.height() > 0) {
            var frame = new UiFrame(graphics, viewport(), UiMouseState.read(), preferences, now, delta);
            binding.renderScreen(frame, pass -> {
                long before = System.nanoTime();
                view.drawAtMediaTime(pass, DarkModeTheme.textColor(), preferences, scrollY, mouseX, mouseY, playhead);
                drawNanos = System.nanoTime() - before;
            });
            view.currentSnapshot().ifPresent(snapshot -> drawOverlay(graphics, snapshot, clip, mouseX, mouseY));
            int duration = Math.max(MIN_TIMELINE_MS, (int) view.mediaCycleMs());
            if (duration != timelineMs) {
                timelineMs = duration;
                playhead = Math.min(playhead, timelineMs);
                if (tab == Tab.TIMELINE) refresh("seek");
            }
        }
        if (tab == Tab.TIMELINE && profile) renderProfile(graphics);
        for (var caption : captions) {
            drawCaption(graphics, caption.text(), caption.x(), caption.y(), caption.width(), DarkModeTheme.secondaryTextColor());
        }
        if (status != null) drawCaption(graphics, status, MARGIN, height - 12, width - 2 * MARGIN,
                statusError ? 0xffff9696 : DarkModeTheme.secondaryTextColor());
        updateActions();
        super.render(graphics, mouseX, mouseY, delta);
        if (overlay && view != null && view.isActive()) view.hitTest(mouseX, mouseY).ifPresent(fragment ->
                graphics.renderTooltip(font, label("hit_details", fragment.node().id(),
                        fragment.bounds().width(), fragment.bounds().height()), mouseX, mouseY));
    }

    private void drawOverlay(GuiGraphics graphics, RichTextView.Presentation snapshot, Bounds clip, int mouseX, int mouseY) {
        var selected = model.selected();
        var hovered = overlay ? view.hitTest(mouseX, mouseY).orElse(null) : null;
        var transform = snapshot.localToGui();
        graphics.enableScissor(clip.x(), clip.y(), clip.x() + clip.width(), clip.y() + clip.height());
        try {
            for (var line : snapshot.layout().visibleLines(scrollY, scrollY + clip.height())) {
                if (overlay) {
                    var baseline = transform.map(new Rect(0, line.baseline(), clip.width(), 1));
                    fill(graphics, baseline, 0x907ec9ff);
                }
                for (var fragment : line.fragments()) {
                    boolean selection = selected != null && selected.id().equals(fragment.node().id());
                    if (!selection && !overlay) continue;
                    var box = fragment.bounds();
                    if (box.width() == 0 || box.height() == 0) continue;
                    var rectangle = transform.map(new Rect(box.x(), box.y(), box.width(), box.height()));
                    int x = (int) rectangle.x(), y = (int) rectangle.y();
                    int right = x + (int) rectangle.width(), bottom = y + (int) rectangle.height();
                    int color = selection ? DarkModeTheme.accentColor() : fragment == hovered ? 0xffffd180 : 0x90ffffff;
                    graphics.fill(x, y, right, y + 1, color);
                    graphics.fill(x, bottom - 1, right, bottom, color);
                    graphics.fill(x, y, x + 1, bottom, color);
                    graphics.fill(right - 1, y, right, bottom, color);
                }
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private static void fill(GuiGraphics graphics, Rect box, int color) {
        graphics.fill((int) box.x(), (int) box.y(), (int) box.right(), (int) box.bottom(), color);
    }

    private void renderProfile(GuiGraphics graphics) {
        if (layout == null) return;
        int y = BODY_Y + 3 * ROW + 3;
        int color = DarkModeTheme.secondaryTextColor();
        drawCaption(graphics, label("prepare", prepareNanos / 1000), MARGIN, y, panelWidth(), color);
        drawCaption(graphics, label("draw", drawNanos / 1000), MARGIN, y + 11, panelWidth(), color);
        drawCaption(graphics, label("layout_count", layoutChanges), MARGIN, y + 22, panelWidth(), color);
        drawCaption(graphics, label("line_count", layout.lines().size()), MARGIN, y + 33, panelWidth(), color);
        view.currentSnapshot().ifPresent(snapshot -> drawCaption(graphics,
                label("resources", snapshot.availableTextures().size(), snapshot.resourceVersion()), MARGIN, y + 44,
                panelWidth(), color));
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && view != null && view.isActive()) {
            var hit = view.hitTest(x, y);
            if (hit.isPresent()) {
                if (apply(false)) {
                    model.select(hit.get().node().id());
                    draft = null;
                    tab = Tab.PROPERTIES;
                    refresh(tab.key);
                }
                flushWidgets();
                return true;
            }
        }
        boolean handled = super.mouseClicked(x, y, button);
        flushWidgets();
        return handled;
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (hasControlDown()) {
            if (key == GLFW.GLFW_KEY_S) {
                save();
                flushWidgets();
                return true;
            }
            if (key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_Y) {
                history(key == GLFW.GLFW_KEY_Y || hasShiftDown());
                flushWidgets();
                return true;
            }
        }
        if (!(getFocused() instanceof EditBox) && !(getFocused() instanceof MultiLineEditBox)
                && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
            scrollPreview((key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1) * Math.max(1, preview().height() - 10));
            return true;
        }
        boolean handled = super.keyPressed(key, scanCode, modifiers);
        flushWidgets();
        return handled;
    }

    public boolean mouseScrolled(double x, double y, double delta) {
        if (delta == 0) return false;
        var clip = preview();
        if (x >= clip.x() && x < clip.x() + clip.width() && y >= clip.y() && y < clip.y() + clip.height()) {
            scrollPreview(delta > 0 ? -24 : 24);
            return true;
        }
        if (tab == Tab.NODES && x >= MARGIN && x < MARGIN + panelWidth() && y >= BODY_Y && y < BODY_Y + listRows * ROW) {
            scrollList(delta > 0 ? -1 : 1, tab.key);
            flushWidgets();
            return true;
        }
        if (widgets.get(Field.TEXT.key()) instanceof TextField field
                && x >= field.getX() && x < field.getX() + field.getWidth() && y >= field.getY()
                && y < field.getY() + field.getHeight()) {
            return field.scroll(delta);
        }
        return false;
    }

    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) { return mouseScrolled(x, y, deltaY); }

    private void scrollPreview(int delta) {
        if (layout != null) {
            scrollY = Math.max(0, Math.min(Math.max(0, layout.height() - preview().height()), scrollY + delta));
        }
    }

    private void refresh(String focus) { refreshFocus = focus; }

    private void flushWidgets() {
        if (refreshFocus != null && minecraft.screen == this) rebuild(refreshFocus);
    }

    private void setStatus(String key, boolean error) {
        status = label(key);
        statusError = error;
    }

    private void caption(Component text, int x, int y, int width) { captions.add(new Caption(text, x, y, width)); }

    private void drawCaption(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        if (width <= 0 || y < 0 || y + font.lineHeight > height) return;
        graphics.enableScissor(x, y, x + width, y + font.lineHeight + 1);
        try {
            graphics.drawString(font, text, x, y, color, false);
        } finally {
            graphics.disableScissor();
        }
    }

    private Button button(String key, int y, int column, int columns, int rowWidth, Runnable action) {
        var name = label(key);
        var widget = add(key, Button.builder(name, button -> action.run())
                .bounds(MARGIN + columnStart(rowWidth, column, columns), y, columnWidth(rowWidth, column, columns),
                        CONTROL_HEIGHT).build());
        widget.setTooltip(Tooltip.create(name));
        return widget;
    }

    private <T extends AbstractWidget> T add(String key, T widget) {
        widgets.put(key, widget);
        return addRenderableWidget(widget);
    }

    private Bounds viewport() { return new Bounds(0, 0, width, height); }
    private int bodyBottom() { return height - 27; }
    private int panelWidth() { return Math.max(1, Math.min(248, (width - 3 * MARGIN) / 2)); }

    private Bounds preview() {
        int x = MARGIN * 2 + panelWidth() + 2;
        int y = BODY_Y + 15;
        return new Bounds(x, y, Math.max(0, width - MARGIN - x - 2), Math.max(0, bodyBottom() - y));
    }

    private static int columnStart(int width, int column, int count) { return (width + GAP) * column / count; }
    private static int columnWidth(int width, int column, int count) {
        return Math.max(1, columnStart(width, column + 1, count) - columnStart(width, column, count) - GAP);
    }

    private static RichDocument preset(boolean media) {
        var text = new TextRun(id("sample_text"), label("sample_text").getString());
        return media ? new RichDocument(List.of(text, new ParagraphBreak(id("sample_break"), 4),
                sampleImage(id("sample_image")))) : new RichDocument(List.of(text));
    }

    private static ImageNode sampleImage(UiId nodeId) { return new ImageNode(nodeId, PULSE, 24, 24, label("sample_alt").getString()); }
    private static UiId newId() { return id("node/" + UUID.randomUUID()); }
    private static UiId id(String path) { return new UiId("sh_better_ui", "rich_text_editor/" + path); }

    private static String kind(InlineNode node) {
        if (node instanceof TextRun) return "kind.text";
        if (node instanceof LocalizedText) return "kind.localized";
        if (node instanceof ParagraphBreak) return "kind.paragraph";
        if (node instanceof ImageNode) return "kind.image";
        if (node instanceof RichExtensionNode) return "kind.extension";
        return "kind.unknown";
    }

    private static Component marked(String key, boolean selected) {
        return selected ? label(key).copy().withStyle(ChatFormatting.UNDERLINE) : label(key);
    }

    private static Component label(String key, Object... args) { return Component.translatable(KEY + key, args); }
}
