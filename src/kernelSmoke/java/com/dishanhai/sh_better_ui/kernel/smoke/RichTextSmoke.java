// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Anchor;
import com.dishanhai.sh_better_ui.client.gui.panel.RichTextExampleScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.visual.RichTextView;
import com.dishanhai.sh_better_ui.client.visual.RichTextView.Presentation;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Fragment;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Rect;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntPredicate;

import static com.dishanhai.sh_better_ui.kernel.smoke.KernelClientSmoke.require;

/** Native checks against the shipped Screen, its presented geometry and the rendered framebuffer. */
final class RichTextSmoke {
    private static final UiId INTRO = node("intro");
    private static final UiId DIAMOND = node("diamond");
    private static final UiId MISSING = node("missing");
    private static final UiId EDITION = node("edition");
    private static final UiId MISSING_TEXTURE = UiId.parse("sh_better_ui:textures/gui/rich_text/missing.png");
    private static final int PLACEHOLDER = 0xff3d2b47;
    private static final int PACK_COLOR = 0xff25d280;

    private final Path output;
    private final Screen parent;
    private final RichTextExampleScreen screen;
    private int narrowLines;
    private int wideLines;
    private long narrowFrame;
    private long oldResourceVersion;
    private long newResourceVersion;
    private Rect oldPlaceholder;
    private CompletableFuture<Void> reload;
    private int advancedStep;
    private long previousFrame;
    private Anchor formerButton;
    private int worldStep;
    private RichTextExampleScreen worldScreen;
    private RichTextPipelineSmoke pipeline;

    RichTextSmoke(Minecraft client, Path output) {
        this.output = output;
        parent = client.screen;
        ClientUiKernel.openRichTextExample(client);
        require(client.screen instanceof RichTextExampleScreen, "The shipped rich text example did not open");
        screen = (RichTextExampleScreen) client.screen;
    }

    boolean advance(Minecraft client, KernelClientSmoke.Platform platform) throws ReflectiveOperationException, IOException {
        if (worldStep != 0) return advanceWorld(client, platform);
        require(client.screen == screen, "The rich text example closed before verification finished");
        var view = view();
        var current = view.currentSnapshot().orElse(null);
        if (current == null) return false;
        if (advancedStep == 0 && !current.availableTextures().containsAll(java.util.Set.of(
                UiId.parse("minecraft:textures/item/diamond.png"),
                UiId.parse("minecraft:textures/gui/title/edition.png")))) return false;
        if (narrowLines == 0) {
            require(client.getWindow().getGuiScale() == 2, "The narrow capture requires GUI Scale 2");
            narrowLines = current.layout().lines().size();
            narrowFrame = current.frame().frameId();
            capture(client, view, current, PLACEHOLDER, "rich-text-scale2-before.png");
            client.options.guiScale().set(1);
            client.resizeDisplay();
            require(view.currentSnapshot().isEmpty(), "The previous presentation remained current after resize");
            return false;
        }
        require(client.getWindow().getGuiScale() == 1, "The wide capture requires GUI Scale 1");
        if (reload == null) {
            if (current.frame().frameId() <= narrowFrame) return false;
            wideLines = current.layout().lines().size();
            require(narrowLines > wideLines, "Viewport change did not reflow the document: "
                    + narrowLines + " lines at Scale 2, " + wideLines + " at Scale 1");
            oldResourceVersion = current.resourceVersion();
            oldPlaceholder = fragment(current, MISSING).bounds();
            capture(client, view, current, PLACEHOLDER, "rich-text-scale1-before.png");
            installPack(client);
            reload = client.reloadResourcePacks();
            return false;
        }
        if (!reload.isDone()) return false;
        reload.join();
        if (current.resourceVersion() <= oldResourceVersion) return false;
        if (advancedStep == 0) {
            if (!current.availableTextures().contains(MISSING_TEXTURE)) return false;
            newResourceVersion = current.resourceVersion();
            require(oldPlaceholder.equals(fragment(current, MISSING).bounds()), "Reload changed the image reservation");
            capture(client, view, current, PACK_COLOR, "rich-text-scale1-reloaded.png");
            activateButton("advanced");
            advancedStep = 1;
            return false;
        }
        if (!advanced(client, view, current)) return false;
        client.setScreen(parent);
        require(view.currentSnapshot().isEmpty(), "The presented rich text view survived Screen close");
        worldStep = 1;
        client.tell(() -> platform.join(client, parent, "rich-text-smoke-" + System.nanoTime()));
        return false;
    }

    String result() {
        return "rich text native smoke passed\n"
                + "framebuffer glyphs, diamond, 8:1 logo, 2:1 reservation and hit geometry at GUI scales 2 and 1\n"
                + "wrap lines scale2=" + narrowLines + " scale1=" + wideLines + "\n"
                + "resource version " + oldResourceVersion + " -> " + newResourceVersion
                + ", placeholder replaced in the same bounds and Screen closed\n"
                + "styled/native runs, grapheme selection, Unicode edit+undo, button once, slider capture/cancel, "
                + "drag and drop, clipped scroll hit, native object pixels and Glass/Reveal advanced screenshots\n"
                + "world-backed fox preview pixels, Screen and world closure\n"
                + "PE5 resource-pack GIF changed pixels in place, corrupt GIF placeholder, scoped custom renderer\n"
                + "PE6 native handshake, 40 → 65 accepted, predicted 100 denied → 65, close/reopen and rejoin\n";
    }

    private boolean advanceWorld(Minecraft client, KernelClientSmoke.Platform platform)
            throws ReflectiveOperationException, IOException {
        if (worldStep == 1) {
            if (client.level == null || client.player == null || client.screen != null) return false;
            ClientUiKernel.openRichTextExample(client);
            require(client.screen instanceof RichTextExampleScreen, "The rich text example did not open in the world");
            worldScreen = (RichTextExampleScreen) client.screen;
            worldStep++;
            return false;
        }
        if (worldStep == 4 || worldStep == 7) {
            if (client.level == null || client.player == null || (client.screen != null
                    && !(client.screen instanceof com.dishanhai.sh_better_ui.client.gui.panel.RichTextPipelineScreen))) return false;
            if (!pipeline.advance(client, worldStep == 7)) return false;
            worldStep++;
            client.tell(() -> platform.leave(client, parent));
            return false;
        }
        if (worldStep == 5 || worldStep == 8) {
            if (client.level != null || client.screen != parent) return false;
            if (worldStep == 8) return true;
            worldStep = 7;
            client.tell(() -> platform.join(client, parent, "rich-text-reconnect-" + System.nanoTime()));
            return false;
        }
        var view = view(worldScreen);
        require(client.screen == worldScreen, "The world preview Screen changed before the capture");
        var current = view.currentSnapshot().orElse(null);
        if (current == null) return false;
        if (worldStep == 2) {
            activateButton(worldScreen, "advanced");
            worldStep++;
            return false;
        }
        if (current.layout().lines().size() < 120) return false;
        var box = fragment(current, node("entity")).bounds();
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            require(pixels(image, client, current, box, color -> color == 0xff6a52ad) == 0
                            && pixels(image, client, current, box, color -> (color & 255) > 145
                            && (color & 255) - (color >>> 16 & 255) > 70) > 2,
                    "The world-backed fox preview did not render inside its reserved box");
            image.writeToFile(output.resolve("rich-text-world.png").toFile());
        }
        worldStep++;
        pipeline = new RichTextPipelineSmoke(output);
        client.setScreen(null);
        require(!view.isActive() && view.currentSnapshot().isEmpty(), "Entity preview survived its Screen scope");
        return false;
    }

    private boolean advanced(Minecraft client, RichTextView view, Presentation current)
            throws ReflectiveOperationException, IOException {
        if (current.frame().frameId() <= previousFrame) return false;
        previousFrame = current.frame().frameId();
        if (advancedStep == 1) {
            if (current.layout().lines().size() < 120) return false;
            var press = fragment(current, node("press"));
            var slider = fragment(current, node("level"));
            var star = fragment(current, node("movable"));
            var frames = fragment(current, node("frames"));
            if (!(frames.node() instanceof RichObjectNode obj && obj.content() instanceof RichObjectNode.Frames sequence
                    && current.availableTextures().containsAll(sequence.textures()))) return false;
            require(current.layout().lines().stream().flatMap(line -> line.fragments().stream())
                    .anyMatch(part -> {
                        var point = center(current, part);
                        return view.componentStyleAt(point.x(), point.y())
                                .filter(style -> style.isItalic() && style.getHoverEvent() != null).isPresent();
                    }), "Styled Component metadata was lost in the presentation");

            var editable = fragment(current, node("editable"));
            var from = current.localToGui().map(new Anchor(editable.bounds().x() + 0.25,
                    editable.bounds().y() + 4));
            var to = center(current, editable);
            press(from);
            drag(from, to);
            release(to);
            require(!view.selectedText().isBlank(), "Visible text did not yield a native drag selection");
            var nextLine = current.layout().lines().stream().flatMap(line -> line.fragments().stream())
                    .filter(part -> part.node() instanceof TextRun && part.bounds().y() > editable.bounds().y()
                            && part.bounds().x() + part.bounds().width() + 2 < current.layout().width())
                    .filter(part -> view.isInViewport(center(current, part).x(), center(current, part).y()))
                    .findFirst().orElseThrow();
            var afterText = current.localToGui().map(new Anchor(nextLine.bounds().x() + nextLine.bounds().width() + 2,
                    nextLine.bounds().y() + nextLine.bounds().height() / 2.0));
            press(from);
            drag(from, afterText);
            release(afterText);
            require(view.selectedText().endsWith(nextLine.text()), "Dragging to the next line margin missed its end caret");
            require(screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0) && view.interaction().hasFocus(),
                    "Tab did not enter the visible inline controls from text focus");

            formerButton = center(current, press);
            press(formerButton);
            release(formerButton);
            screen.mouseReleased(formerButton.x(), formerButton.y(), 0);
            require(number("activations") == 1, "Pointer release produced " + number("activations") + " button activations");
            require(screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0), "Focused inline button did not accept Enter");
            screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            screen.keyReleased(GLFW.GLFW_KEY_ENTER, 0, 0);
            require(number("activations") == 2, "A held key repeated the button action");

            var knob = center(current, slider);
            var outside = current.localToGui().map(new Anchor(current.layout().width() + 80, slider.bounds().y() + 4));
            press(knob);
            drag(knob, outside);
            require(number("level") == 40, "Dragging committed a slider before release");
            require(screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), "Esc did not cancel the slider drag");
            release(outside);
            require(number("level") == 40, "Cancellation changed the committed slider");
            press(knob);
            drag(knob, outside);
            release(outside);
            require(number("level") == 100, "Captured release outside did not clamp the slider to its endpoint");

            var source = center(current, star);
            press(source);
            drag(source, formerButton);
            release(formerButton);
            require((Boolean) field("dropped") && number("activations") == 2, "Drop activated a nested target button");

            var editor = screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .findFirst().orElseThrow();
            screen.setFocused(editor);
            editor.setCursorPosition(editor.getValue().length());
            editor.setHighlightPos(editor.getValue().length());
            screen.charTyped('漢', 0);
            screen.charTyped('\ud83c', 0);
            screen.charTyped('\udf0a', 0);
            require(editor.getValue().endsWith("漢🌊"), "Native editor did not receive committed Unicode units");
            advancedStep++;
            return false;
        }
        if (advancedStep == 2) {
            var editable = fragment(current, node("editable"));
            require(editable.text().endsWith("漢🌊"), "The native editor did not update its stable inline run");
            activateButton("undo");
            screen.setFocused((GuiEventListener) field("inline"));
            require(screen.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0), "Page Down did not scroll the focused document");
            advancedStep++;
            return false;
        }
        if (advancedStep == 3) {
            require(view.hitTest(formerButton.x(), formerButton.y())
                    .filter(hit -> hit.node().id().equals(node("press"))).isEmpty(),
                    "A control above the clip retained its stale hit box");
            require(screen.keyPressed(GLFW.GLFW_KEY_PAGE_UP, 0, 0), "Page Up did not restore the focused document");
            activateButton("flat");
            advancedStep++;
            return false;
        }
        var editable = fragment(current, node("editable"));
        require(!editable.text().endsWith("漢🌊") && view.hitTest(formerButton.x(), formerButton.y())
                .filter(hit -> hit.node().id().equals(node("press"))).isPresent(), "Undo or scroll restore did not repaint");
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            require(pixels(image, client, current, editable.bounds(), RichTextSmoke::brightGlyph) > 3,
                    "The advanced viewport did not paint its editable text");
            require(pixels(image, client, current, fragment(current, node("item")).bounds(), RichTextSmoke::editionColor) > 3,
                    "The native item stack did not draw inside its inline box");
            require(pixels(image, client, current, fragment(current, node("block")).bounds(), color ->
                    (color >>> 8 & 255) - (color & 255) > 12) > 3, "The block inventory model was absent");
            require(pixels(image, client, current, fragment(current, node("fluid")).bounds(), color ->
                    (color >>> 16 & 255) - (color & 255) > 36) > 3, "The fluid atlas sprite was absent");
            String filename = advancedStep == 4 ? "rich-text-advanced.png" : "rich-text-advanced-reveal.png";
            image.writeToFile(output.resolve(filename).toFile());
        }
        if (advancedStep == 4) {
            activateButton("glass");
            advancedStep++;
            return false;
        }
        return true;
    }

    private void activateButton(String label) {
        activateButton(screen, label);
    }

    private static void activateButton(RichTextExampleScreen screen, String label) {
        var message = Component.translatable("sh_better_ui.rich_text_example." + label);
        var button = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(child -> child.getMessage().equals(message)).findFirst().orElseThrow();
        require(screen.mouseClicked(button.getX() + 2, button.getY() + 2, 0), "Button did not handle input: " + label);
    }

    private void press(Anchor at) {
        UiPointerCapture.beforePress(screen, 0);
        require(screen.mouseClicked(at.x(), at.y(), 0), "Native rich text press was not handled: " + at);
    }

    private void drag(Anchor from, Anchor to) {
        require(UiPointerCapture.drag(screen, 0, to.x(), to.y())
                || screen.mouseDragged(to.x(), to.y(), 0, to.x() - from.x(), to.y() - from.y()),
                "Native rich text drag was not handled");
    }

    private void release(Anchor at) {
        require(UiPointerCapture.release(screen, 0, at.x(), at.y())
                || screen.mouseReleased(at.x(), at.y(), 0), "Native rich text release was not handled");
    }

    private static Anchor center(Presentation current, Fragment fragment) {
        var box = fragment.bounds();
        return current.localToGui().map(new Anchor(box.x() + box.width() / 2.0, box.y() + box.height() / 2.0));
    }

    private Object field(String name) throws ReflectiveOperationException {
        var field = RichTextExampleScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    private int number(String name) throws ReflectiveOperationException { return (Integer) field(name); }

    private RichTextView view() throws ReflectiveOperationException {
        return view(screen);
    }

    private static RichTextView view(RichTextExampleScreen screen) throws ReflectiveOperationException {
        // Keep the demo's mounted view private in production.
        var field = RichTextExampleScreen.class.getDeclaredField("view");
        field.setAccessible(true);
        return (RichTextView) field.get(screen);
    }

    private void capture(Minecraft client, RichTextView view, Presentation current, int missingColor, String file)
            throws IOException {
        var first = current.layout().lines().get(0);
        var intro = fragment(current, INTRO);
        var diamond = fragment(current, DIAMOND);
        var missing = fragment(current, MISSING);
        var edition = fragment(current, EDITION);
        require(first.fragments().contains(intro) && first.fragments().contains(diamond)
                        && diamond.bounds().x() > intro.bounds().x(), "The glyph and image are not on the same line");
        require(diamond.bounds().width() == diamond.bounds().height()
                        && diamond.bounds().y() + diamond.bounds().height() == first.baseline(),
                "Diamond geometry did not respect intrinsic ratio and baseline");
        require(missing.bounds().width() == 2 * missing.bounds().height(), "Non-square image reservation lost aspect ratio");
        require(edition.bounds().width() == 8 * edition.bounds().height(), "The visible wide logo lost its aspect ratio");
        require(current.availableTextures().contains(MISSING_TEXTURE) == (missingColor == PACK_COLOR),
                "Texture readiness disagreed with the expected resource generation");
        var bounds = diamond.bounds();
        double centerY = bounds.y() + bounds.height() / 2.0;
        var inside = current.localToGui().map(new Anchor(bounds.x() + bounds.width() / 2.0, centerY));
        var right = current.localToGui().map(new Anchor(bounds.x() + bounds.width(), centerY));
        require(view.hitTest(inside.x(), inside.y()).filter(diamond::equals).isPresent(),
                "The presented view did not hit the visible diamond");
        require(view.hitTest(right.x(), right.y()).filter(diamond::equals).isEmpty(),
                "The diamond hit escaped its half-open right edge");
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            require(pixels(image, client, current, intro.bounds(), RichTextSmoke::brightGlyph) > 3,
                    "The framebuffer did not contain bright text inside the measured run");
            require(pixels(image, client, current, diamond.bounds(), RichTextSmoke::diamondColor) > 3,
                    "The framebuffer did not contain the diamond texture inside its measured bounds");
            require(pixels(image, client, current, edition.bounds(), RichTextSmoke::editionColor) > 3,
                    "The framebuffer did not contain the wide logo inside its measured bounds");
            require(pixels(image, client, current, missing.bounds(), color -> color == missingColor) > 3,
                    "The framebuffer did not contain the expected placeholder or resource-pack pixels");
            image.writeToFile(output.resolve(file).toFile());
        }
    }

    static int pixels(NativeImage image, Minecraft client, Presentation current, Rect bounds,
                              IntPredicate predicate) {
        int matches = 0;
        for (int y = bounds.y(); y < bounds.y() + bounds.height(); y++) {
            for (int x = bounds.x(); x < bounds.x() + bounds.width(); x++) {
                var gui = current.localToGui().map(new Anchor(x, y));
                if (predicate.test(KernelClientSmoke.pixel(image, client, (int) gui.x(), (int) gui.y()))) matches++;
            }
        }
        return matches;
    }

    private static boolean brightGlyph(int color) {
        return (color & 255) > 180 && (color >>> 8 & 255) > 180 && (color >>> 16 & 255) > 180;
    }

    private static boolean diamondColor(int color) {
        int red = color & 255;
        int green = color >>> 8 & 255;
        int blue = color >>> 16 & 255;
        return green >= 120 && blue >= 120 && green - red >= 35 && blue - red >= 25;
    }

    private static boolean editionColor(int color) {
        int red = color & 255;
        int green = color >>> 8 & 255;
        int blue = color >>> 16 & 255;
        return red >= 140 && green >= 125 && red - blue >= 10;
    }

    static Fragment fragment(Presentation current, UiId id) {
        return current.layout().lines().stream().flatMap(line -> line.fragments().stream())
                .filter(fragment -> fragment.node().id().equals(id)).findFirst().orElseThrow();
    }

    private void installPack(Minecraft client) throws IOException {
        var pack = output.resolve("resourcepacks/rich-text-smoke");
        var texture = pack.resolve("assets/sh_better_ui/textures/gui/rich_text/missing.png");
        Files.createDirectories(texture.getParent());
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        Files.writeString(pack.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":" + format + ",\"description\":\"Rich text smoke fixture\"}}");
        try (var image = new NativeImage(24, 12, false)) {
            image.fillRect(0, 0, 24, 12, PACK_COLOR);
            image.writeToFile(texture.toFile());
        }
        Files.write(texture.resolveSibling("absent.gif"), new byte[]{'G', 'I', 'F', '8', '9', 'a'});
        var repository = client.getResourcePackRepository();
        repository.reload();
        require(repository.addPack("file/rich-text-smoke"), "The rich text smoke resource pack was not selected");
    }

    private static UiId node(String path) { return new UiId("sh_better_ui", "rich_text_example/" + path); }
}
