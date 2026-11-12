// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.visual.RichTextComponents;
import com.dishanhai.sh_better_ui.client.visual.RichTextView;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocumentCodec;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.*;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextEdit;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextMarkup;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextSelection;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextSelection.Position;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.DoublePredicate;
import java.util.function.IntPredicate;

/** Native example content and one ordinary Screen input/focus entry for the rich text viewport. */
final class RichTextWorkbench {
    static final UiId EDITABLE = id("editable");
    static final UiId PRESS = id("press");
    static final UiId LEVEL = id("level");
    static final UiId MOVABLE = id("movable");
    private static final RichStyle ACCENT = new RichStyle(0x88ddff, true, false, false, false, false, null);

    private RichTextWorkbench() {}

    static RichTextComponents.Snapshot document(String editable) {
        var builder = RichText.document(id("workbench"))
                .localized(key("guide"), "Drag to select text. Ctrl+C copies.", List.of(), ACCENT)
                .paragraph(4);
        // The example runs the same passive markup and codec paths that library consumers can use.
        var marked = RichTextMarkup.parse(id("markup"),
                "[b][color=#ffce86]" + RichTextMarkup.escape(RichTextExampleScreen.label("markup").getString())
                        + "[/color][/b]");
        RichDocumentCodec.decode(RichDocumentCodec.encode(marked)).nodes().forEach(builder::add);
        var nativeText = RichTextComponents.resolve(id("component"), Component.literal(" ")
                .append(RichTextExampleScreen.label("component").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC)
                        .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                RichTextExampleScreen.label("component_alt"))))));
        nativeText.document().nodes().forEach(builder::add);
        builder.paragraph(3)
                .add(object("frames", new Frames(List.of(asset("diamond"), asset("emerald")), 360), 1, 1, 16))
                .text("  ")
                .add(object("item", new Item(UiId.parse("minecraft:golden_apple"), 1), 1, 1, 16))
                .text("  ")
                .add(object("block", new Block(UiId.parse("minecraft:grass_block")), 1, 1, 16))
                .text("  ")
                .add(object("fluid", new Fluid(UiId.parse("minecraft:water")), 1, 1, 16))
                .text("  ")
                .add(object("entity", new Entity(UiId.parse("minecraft:fox")), 1, 1, 16))
                .text("  ")
                .add(object("movable", new Icon(Symbol.STAR), 1, 1, 16))
                .paragraph(4)
                .add(object("press", new Button(RichTextExampleScreen.label("press").getString()), 6, 1, 16))
                .text("  ")
                .add(object("level", new Slider(RichTextExampleScreen.label("level").getString(), 0, 100, 40, 5), 8, 1, 16))
                .paragraph(4)
                .add(new TextRun(EDITABLE, editable, ACCENT))
                .paragraph(4);
        for (int i = 1; i <= 120; i++) {
            builder.localized(key("row"), "Scrollable row %s", List.of(Integer.toString(i)), RichStyle.EMPTY).paragraph(2);
        }
        return new RichTextComponents.Snapshot(builder.build(), nativeText.nativeStyles());
    }

    static RichTextComponents.Snapshot edit(RichTextComponents.Snapshot before, String oldText, String committed) {
        var range = new RichTextSelection(new Position(EDITABLE, 0), new Position(EDITABLE, oldText.length()));
        var doc = RichTextEdit.replace(before.document(), range, committed);
        return new RichTextComponents.Snapshot(doc, before.nativeStyles());
    }

    private static RichObjectNode object(String name, Content content, int w, int h, int size) {
        return new RichObjectNode(id(name), content, w, h, size, RichTextExampleScreen.label(name + "_alt").getString());
    }

    private static UiId asset(String name) { return UiId.parse("minecraft:textures/item/" + name + ".png"); }

    private static UiId id(String name) { return new UiId("sh_better_ui", "rich_text_example/" + name); }

    private static String key(String name) { return "sh_better_ui.rich_text_example." + name; }

    static final class Viewport extends AbstractWidget {
        private final Screen owner;
        private final RichTextView view;
        private final DoublePredicate scroll;
        private final IntPredicate scrollPage;
        private Position anchor;
        private RichLayoutSnapshot selectionLayout;

        Viewport(Screen owner, RichTextView view, DoublePredicate scroll, IntPredicate scrollPage) {
            super(0, 0, 0, 0, RichTextExampleScreen.label("title"));
            this.owner = owner;
            this.view = view;
            this.scroll = scroll;
            this.scrollPage = scrollPage;
        }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {}

        @Override public boolean isMouseOver(double x, double y) { return visible && view.isInViewport(x, y); }

        @Override public boolean mouseClicked(double x, double y, int button) {
            if (!isMouseOver(x, y)) return false;
            anchor = null;
            view.clearSelection();
            if (view.interaction().press(x, y, button)) {
                view.interaction().capture(owner, button);
                return true;
            }
            view.interaction().clearFocus();
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
            anchor = view.caretAt(x, y).orElse(null);
            selectionLayout = view.currentSnapshot().map(RichTextView.Presentation::layout).orElse(null);
            return anchor != null;
        }

        @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            if (view.interaction().drag(x, y, button)) return true;
            if (anchor == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
            extendSelection(x, y);
            return true;
        }

        @Override public boolean mouseReleased(double x, double y, int button) {
            if (view.interaction().release(x, y, button)) return true;
            if (anchor == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
            extendSelection(x, y);
            anchor = null;
            return true;
        }

        private void extendSelection(double x, double y) {
            if (view.currentSnapshot().filter(frame -> frame.layout() == selectionLayout).isPresent()) {
                view.selectionCaretAt(x, y).ifPresent(end -> view.select(new RichTextSelection(anchor, end)));
            }
        }

        public boolean mouseScrolled(double x, double y, double dy) {
            return isMouseOver(x, y) && scroll.test(dy);
        }

        public boolean mouseScrolled(double x, double y, double dx, double dy) {
            return mouseScrolled(x, y, dy);
        }

        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == GLFW.GLFW_KEY_C && Screen.hasControlDown()) {
                String selected = view.selectedText();
                if (selected.isEmpty()) return false;
                Minecraft.getInstance().keyboardHandler.setClipboard(selected);
                return true;
            }
            if (key == GLFW.GLFW_KEY_PAGE_UP) return scrollPage.test(-1);
            if (key == GLFW.GLFW_KEY_PAGE_DOWN) return scrollPage.test(1);
            if (key == GLFW.GLFW_KEY_TAB && !view.interaction().hasFocus()) {
                return view.interaction().focusFirst((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            }
            return view.interaction().keyPressed(key, modifiers);
        }

        @Override public boolean keyReleased(int key, int scan, int modifiers) {
            return view.interaction().keyReleased(key);
        }

        @Override public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (focused) {
                if (anchor == null && !view.interaction().hasFocus()) view.interaction().focusFirst(Screen.hasShiftDown());
            } else {
                anchor = null;
                view.interaction().clearFocus();
            }
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            if (!view.interaction().narrate(output)) output.add(NarratedElementType.TITLE, getMessage());
        }
    }
}
