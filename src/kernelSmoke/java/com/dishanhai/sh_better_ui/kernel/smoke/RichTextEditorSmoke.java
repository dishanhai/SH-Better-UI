// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Anchor;
import com.dishanhai.sh_better_ui.client.gui.panel.RichTextEditorScreen;
import com.dishanhai.sh_better_ui.client.visual.RichTextView;
import com.dishanhai.sh_better_ui.client.visual.RichTextView.Presentation;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocumentCodec;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionInput;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Fragment;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.dishanhai.sh_better_ui.kernel.smoke.KernelClientSmoke.require;

/** Exercises the shipped editor in an isolated integrated world and verifies its rendered geometry and files. */
final class RichTextEditorSmoke {
    private static final String COMMAND = "shbetterricheditor";
    private static final String PREFIX = "sh_better_ui.rich_text_editor.";
    private static final UiId PULSE = UiId.parse("sh_better_ui:textures/gui/rich_text/pulse.gif");
    private static final RichExtensionInput PASSIVE = new RichExtensionInput(Map.of());
    private static final String EDITED = "Native draft 漢 🌊";
    private static final int GREEN = 0xffaef45a;
    private static final int BLUE = 0xffffd05d;

    private final Screen parent;
    private final Path output;
    private final Path folder;
    private final boolean enabled = ClientUiKernel.richTextEditorEnabled();
    private Stage stage = Stage.JOIN;
    private RichTextEditorScreen editor;
    private RichTextView view;
    private UiId title;
    private int originalNodes;
    private int overlayBaseline;
    private MultiLineEditBox beforeSaveField;
    private RichDocument saved;
    private long resourceVersion;

    private enum Stage {
        JOIN, OPEN, NARROW, EDIT, SAVED_PROPERTIES, UNDO, REDO, OVERLAY, TIMELINE_START, TIMELINE_SEEK, TIMELINE_VERIFY,
        PROFILE, SELECT, ADD, SAVE, FILES, PRESET, DIRTY, REOPEN, RELOAD, LEAVE
    }

    RichTextEditorSmoke(Minecraft client, KernelClientSmoke.Platform platform, Path output) {
        parent = client.screen;
        this.output = output;
        folder = output.resolve("sh_better_ui/rich_text_editor");
        client.tell(() -> platform.join(client, parent, "rich-editor-smoke-" + System.nanoTime()));
    }

    boolean advance(Minecraft client, KernelClientSmoke.Platform platform) throws ReflectiveOperationException, IOException {
        if (stage == Stage.LEAVE) return client.level == null && client.screen == parent;
        if (stage == Stage.JOIN) {
            if (client.level == null || client.player == null || client.screen != null) return false;
            require(platform.clientCommand(COMMAND) == enabled, "Unexpected editor command registration");
            if (!enabled) {
                ClientUiKernel.openRichTextEditor(client);
                require(client.screen == null && Files.notExists(folder.resolve("document.json")),
                        "Disabled editor opened or touched the draft");
                leave(client, platform);
            } else {
                stage = Stage.OPEN;
            }
            return false;
        }
        if (stage == Stage.OPEN || stage == Stage.REOPEN) {
            if (client.screen == null) return false;
            require(client.screen instanceof RichTextEditorScreen, "Client command did not open the shipped editor");
            editor = (RichTextEditorScreen) client.screen;
            view = readMountedView();
            if (stage == Stage.OPEN) stage = Stage.NARROW;
        }
        var current = view.currentSnapshot().orElse(null);
        if (current == null || (stage != Stage.PRESET && !current.availableTextures().contains(PULSE))) return false;
        require(client.screen == editor, "The editor closed before verification finished at " + stage);
        switch (stage) {
            case NARROW -> {
                require(client.getWindow().getGuiScale() == 2, "The narrow capture requires Scale 2");
                title = current.layout().lines().stream().flatMap(line -> line.fragments().stream())
                        .filter(part -> part.node() instanceof TextRun).findFirst().orElseThrow().node().id();
                assertHit(current, image(current));
                capture(client, "rich-text-editor-narrow.png");
                client.options.guiScale().set(1);
                client.resizeDisplay();
                stage = Stage.EDIT;
            }
            case EDIT -> {
                require(client.getWindow().getGuiScale() == 1, "Editor did not resize to Scale 1");
                require(!text(current, title).contains(EDITED), "The isolated draft was not fresh");
                beforeSaveField = textField();
                editor.setFocused(beforeSaveField);
                beforeSaveField.setValue("Saved properties 漢");
                var save = RichTextEditorScreen.class.getDeclaredMethod("save");
                save.setAccessible(true);
                save.invoke(editor);
                stage = Stage.SAVED_PROPERTIES;
            }
            case SAVED_PROPERTIES -> {
                require(text(current, title).equals("Saved properties 漢"), "Property save did not commit the draft");
                var field = textField();
                require(field != beforeSaveField, "Property save retained a responder bound to the consumed draft");
                field.setValue(EDITED);
                press(editor, "apply");
                stage = Stage.UNDO;
            }
            case UNDO -> {
                require(text(current, title).equals(EDITED), "Editing did not update the native preview at the same node ID");
                press(editor, "undo");
                stage = Stage.REDO;
            }
            case REDO -> {
                require(!text(current, title).contains(EDITED), "Undo did not restore the original preview");
                press(editor, "redo");
                overlayBaseline = baselinePixel(client, current);
                press(editor, "overlay");
                stage = Stage.OVERLAY;
            }
            case OVERLAY -> {
                require(text(current, title).equals(EDITED), "Redo did not restore the edited text");
                assertHit(current, image(current));
                require(baselinePixel(client, current) != overlayBaseline, "Overlay did not draw its layout baseline");
                capture(client, "rich-text-editor-overlay.png");
                press(editor, "timeline");
                stage = Stage.TIMELINE_START;
            }
            case TIMELINE_START -> {
                seek(0.0);
                stage = Stage.TIMELINE_SEEK;
            }
            case TIMELINE_SEEK -> {
                require(colorAt(client, current, image(current)) == GREEN, "Start of timeline did not render the first GIF frame");
                seek(0.09);
                stage = Stage.TIMELINE_VERIFY;
            }
            case TIMELINE_VERIFY -> {
                require(colorAt(client, current, image(current)) == BLUE, "Seeking did not render the second GIF frame");
                press(editor, "profile");
                stage = Stage.PROFILE;
            }
            case PROFILE -> {
                capture(client, "rich-text-editor-timeline.png");
                originalNodes = (int) current.layout().lines().stream().flatMap(line -> line.fragments().stream())
                        .map(part -> part.node().id()).distinct().count();
                var box = image(current).bounds();
                var point = current.localToGui().map(new Anchor(box.x() + box.width() / 2.0,
                        box.y() + box.height() / 2.0));
                click(editor, point.x(), point.y());
                stage = Stage.SELECT;
            }
            case SELECT -> {
                require(editor.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                                .anyMatch(field -> field.getValue().equals(PULSE.toString())),
                        "Selecting the visible image did not open its properties");
                press(editor, "nodes");
                stage = Stage.ADD;
            }
            case ADD -> {
                press(editor, "add_text");
                stage = Stage.SAVE;
            }
            case SAVE -> {
                long nodes = current.layout().lines().stream().flatMap(line -> line.fragments().stream())
                        .map(part -> part.node().id()).distinct().count();
                require(nodes == originalNodes + 1, "Insert did not add a visible node");
                press(editor, "files");
                stage = Stage.FILES;
            }
            case FILES -> {
                press(editor, "save");
                press(editor, "export_json");
                press(editor, "export_java");
                press(editor, "save_preset");
                press(editor, "preset_text");
                stage = Stage.PRESET;
            }
            case PRESET -> {
                require(!text(current, title).equals(EDITED) && !current.availableTextures().contains(PULSE),
                        "Text preset did not replace the working preview");
                press(editor, "load_preset");
                stage = Stage.DIRTY;
            }
            case DIRTY -> {
                if (!Files.isRegularFile(folder.resolve("ExportedRichText.java"))) return false;
                saved = read("document.json");
                require(saved.equals(read("export.json")), "JSON export differs from the saved document");
                require(saved.equals(read("preset.json")) && text(current, title).equals(EDITED),
                        "The local preset did not restore the draft");
                require(saved.nodes().get(0) instanceof TextRun text && text.id().equals(title) && text.text().equals(EDITED),
                        "Saved document lost Unicode or node identity");
                press(editor, "nodes");
                press(editor, "add_text");
                editor.onClose();
                require(client.screen instanceof ConfirmScreen, "Closing an unsaved draft did not require a decision");
                client.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(client.screen == editor, "Cancel did not return to the editor");
                editor.onClose();
                require(client.screen instanceof ConfirmScreen, "Cancel discarded the draft");
                press(client.screen, "discard");
                require(client.screen == null && !readMountedView().isActive(), "Discard did not close the editor scope");
                require(saved.equals(read("document.json")), "Discard overwrote the saved file");
                require(platform.clientCommand(COMMAND), "Client command could not reopen the editor");
                stage = Stage.REOPEN;
            }
            case REOPEN -> {
                require(text(current, title).equals(EDITED), "Reopening did not load the saved draft");
                capture(client, "rich-text-editor-reopened.png");
                resourceVersion = current.resourceVersion();
                press(editor, "files");
                press(editor, "reload_assets");
                stage = Stage.RELOAD;
            }
            case RELOAD -> {
                if (current.resourceVersion() <= resourceVersion) return false;
                require(text(current, title).equals(EDITED), "Resource reload lost the draft");
                assertHit(current, image(current));
                editor.onClose();
                require(client.screen == null && !view.isActive() && view.currentSnapshot().isEmpty(),
                        "Clean close did not release the presented view");
                leave(client, platform);
            }
            default -> throw new AssertionError("Unexpected editor smoke stage " + stage);
        }
        return false;
    }

    private RichTextView readMountedView() throws ReflectiveOperationException {
        var field = RichTextEditorScreen.class.getDeclaredField("view");
        field.setAccessible(true);
        return (RichTextView) field.get(editor);
    }

    private RichDocument read(String file) throws IOException {
        return RichDocumentCodec.decode(Files.readString(folder.resolve(file)), PASSIVE);
    }

    private MultiLineEditBox textField() {
        return editor.children().stream().filter(MultiLineEditBox.class::isInstance)
                .map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
    }

    private void seek(double fraction) {
        var slider = editor.children().stream().filter(AbstractSliderButton.class::isInstance)
                .map(AbstractSliderButton.class::cast).findFirst().orElseThrow();
        double x = slider.getX() + 4 + fraction * (slider.getWidth() - 8);
        click(editor, x, slider.getY() + slider.getHeight() / 2.0);
    }

    private void assertHit(Presentation current, Fragment part) {
        var box = part.bounds();
        double y = box.y() + box.height() / 2.0;
        var center = current.localToGui().map(new Anchor(box.x() + box.width() / 2.0, y));
        var outside = current.localToGui().map(new Anchor(box.x() + box.width(), y));
        require(view.hitTest(center.x(), center.y()).filter(part::equals).isPresent(), "Preview hit missed its image box");
        require(view.hitTest(outside.x(), outside.y()).filter(part::equals).isEmpty(), "Preview hit escaped its image box");
    }

    private static int colorAt(Minecraft client, Presentation current, Fragment part) {
        var box = part.bounds();
        var center = current.localToGui().map(new Anchor(box.x() + box.width() / 2.0, box.y() + box.height() / 2.0));
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            return KernelClientSmoke.pixel(image, client, (int) center.x(), (int) center.y());
        }
    }

    private static int baselinePixel(Minecraft client, Presentation current) {
        var point = current.localToGui().map(new Anchor(current.layout().width() - 8,
                current.layout().lines().get(0).baseline()));
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            return KernelClientSmoke.pixel(image, client, (int) point.x(), (int) point.y());
        }
    }

    private static Fragment image(Presentation current) {
        return current.layout().lines().stream().flatMap(line -> line.fragments().stream())
                .filter(part -> part.node() instanceof ImageNode image && image.texture().equals(PULSE))
                .findFirst().orElseThrow();
    }

    private static String text(Presentation current, UiId id) {
        return current.layout().lines().stream().flatMap(line -> line.fragments().stream())
                .map(Fragment::node).filter(node -> node.id().equals(id))
                .map(node -> ((TextRun) node).text()).findFirst().orElseThrow();
    }

    private void capture(Minecraft client, String file) throws IOException {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(output.resolve(file).toFile());
        }
    }

    private static void press(Screen screen, String key) {
        var widget = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(candidate -> candidate.visible && candidate.active
                        && candidate.getMessage().getContents() instanceof TranslatableContents label
                        && (label.getKey().equals(PREFIX + key) || label.getKey().startsWith(PREFIX + key + "_")))
                .findFirst().orElseThrow(() -> new AssertionError("Missing active editor button " + key + " on " + screen));
        click(screen, widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0);
    }

    private static void click(Screen screen, double x, double y) {
        require(screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT), "Editor did not accept a widget click");
        screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private void leave(Minecraft client, KernelClientSmoke.Platform platform) {
        stage = Stage.LEAVE;
        client.tell(() -> platform.leave(client, parent));
    }

    String stage() { return stage.name(); }

    String result() {
        if (!enabled) return "rich text editor command and direct entry disabled by feature flag\n";
        return "rich text editor integrated-world smoke passed\n"
                + "client command, GUI Scale 2 → 1, Unicode edit with stable ID, undo/redo, preview selection and insertion\n"
                + "native preview overlay, same-snapshot hit bounds and two actual timeline GIF frames\n"
                + "save, local preset round trip, JSON and Java export, dirty-close cancel/discard, reopen and resource reload\n";
    }
}
