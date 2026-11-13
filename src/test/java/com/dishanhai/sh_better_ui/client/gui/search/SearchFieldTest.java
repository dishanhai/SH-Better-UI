package com.dishanhai.sh_better_ui.client.gui.search;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.Rect2i;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：覆盖两版原生 EditBox 上的回调和搜索交互。 */
class SearchFieldTest {
    @Test
    void modCompletionKeepsTheQueryPrefixAndCanBeUndone() {
        var field = metadataField();
        var changes = new ArrayList<String>();
        field.setResponder(changes::add);
        field.setValue("Iron  @mine");

        assertTrue(field.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals("Iron  @minecraft", field.getValue());
        assertEquals(field.getValue().length(), field.getCursorPosition());
        assertTrue(field.undoCompletion());
        assertEquals("Iron  @mine", field.getValue());
        assertEquals(List.of("Iron  @mine", "Iron  @minecraft", "Iron  @mine"), changes);
        assertTrue(field.dismissSuggestions());
        assertEquals("Iron  @mine", field.getValue());
    }

    @Test
    void tagCompletionUsesTheSameClickableDropdownAndUndo() {
        var field = metadataField();
        field.setValue("@minecraft #forge:ing");
        var area = field.suggestionArea(120).orElseThrow();

        assertTrue(field.mouseClickedSuggestion(area.getX() + 1, area.getY() + 1,
                GLFW.GLFW_MOUSE_BUTTON_LEFT, 120));
        assertEquals("@minecraft #forge:ingots/iron", field.getValue());
        assertTrue(field.undoCompletion());
        assertEquals("@minecraft #forge:ing", field.getValue());
        assertTrue(field.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertEquals("@minecraft #forge:ing", field.getValue());
    }

    @Test
    void facetCompletionHonorsScopeAndChecksTheWholeReplacementAgainstInputLimits() {
        var field = metadataField();
        field.setScope(value -> value == 1);
        field.setValue("@ot");
        assertFalse(field.dismissSuggestions());

        field.setValue("Iron @mi");
        field.setMaxLength(12);
        assertFalse(field.dismissSuggestions());
        field.setMaxLength(64);
        field.setFilter(text -> !text.equals("Iron @minecraft"));
        assertFalse(field.dismissSuggestions());
        field.setFilter(text -> true);
        assertTrue(field.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals("Iron @minecraft", field.getValue());
    }

    @Test
    void liquidGlassKeepsNativeInputPaddingAndCompletionUndo() {
        var field = fieldAt(20, UiStyle.LIQUID_GLASS);
        assertEquals(68, field.getInnerWidth());
        field.setValue("Ir");
        assertTrue(field.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0));
        assertTrue(field.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals("Iridium", field.getValue());
        assertTrue(field.undoCompletion());
        assertEquals("Ir", field.getValue());
    }

    @Test
    void searchIconReservesTextSpaceOnlyWhenBothDimensionsFit() {
        for (var style : UiStyle.values()) {
            var field = fieldAt(20, style);
            assertEquals(68, field.getInnerWidth());

            field.setWidth(64);
            assertEquals(32, field.getInnerWidth());
            field.setWidth(63);
            assertEquals(55, field.getInnerWidth());

            field.setBordered(false);
            assertEquals(35, field.getInnerWidth());
            field.setHeight(17);
            assertEquals(63, field.getInnerWidth());
        }
    }

    @Test
    void tabFillsSelectedCandidateOnceAndUndoRestoresQuery() {
        var field = fieldAt(20);
        var changes = new ArrayList<String>();
        field.setResponder(changes::add);
        field.setValue("Ir");
        assertTrue(field.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0));
        assertTrue(field.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals("Iridium", field.getValue());
        assertEquals(field.getValue().length(), field.getCursorPosition());
        assertFalse(field.dismissSuggestions());

        assertTrue(field.undoCompletion());
        assertEquals("Ir", field.getValue());
        assertFalse(field.undoCompletion());
        assertEquals(List.of("Ir", "Iridium", "Ir"), changes);
    }

    @Test
    void escapeAndOutsideClicksDismissWithoutChangingTextWhileEnterSubmitsQuery() {
        var field = fieldAt(20);
        var submissions = new ArrayList<String>();
        field.setOnSubmit(submissions::add);
        field.setValue("Ir");
        assertTrue(field.dismissSuggestions());
        assertFalse(field.dismissSuggestions());
        assertEquals("Ir", field.getValue());

        field.setValue("I");
        assertFalse(field.mouseClickedSuggestion(1, 1, GLFW.GLFW_MOUSE_BUTTON_LEFT, 120));
        assertFalse(field.dismissSuggestions());
        assertTrue(field.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(List.of("I"), submissions);
        assertEquals("I", field.getValue());
    }

    @Test
    void dropdownFlipsAndFillsOnlyRowsInsideItsVisibleBounds() {
        var field = fieldAt(96);
        field.setValue("Ir");
        assertTrue(field.mouseClickedSuggestion(10, 93, GLFW.GLFW_MOUSE_BUTTON_RIGHT, 120));
        assertEquals("Ir", field.getValue());
        assertTrue(field.mouseClickedSuggestion(10, 93, GLFW.GLFW_MOUSE_BUTTON_LEFT, 120));
        assertEquals("Iridium", field.getValue());

        field.setValue("Ir");
        assertFalse(field.mouseClickedSuggestion(10, 94, GLFW.GLFW_MOUSE_BUTTON_LEFT, 120));
        assertEquals("Ir", field.getValue());
        assertFalse(field.undoCompletion());
    }

    @Test
    void scrollingMovesSelectionWhenOnlyOneRowFits() {
        var field = fieldAt(18);
        field.setValue("Ir");
        assertFalse(field.mouseScrolledSuggestions(1, 40, -1, 54));
        assertTrue(field.mouseScrolledSuggestions(10, 40, -1, 54));
        assertTrue(field.mouseClickedSuggestion(10, 40, GLFW.GLFW_MOUSE_BUTTON_LEFT, 54));
        assertEquals("Iridium", field.getValue());
    }

    @Test
    void dropdownDoesNotAppearOrCaptureClicksWhenItsAnchorIsOffscreen() {
        var field = fieldAt(120);
        field.setValue("Ir");
        assertTrue(field.suggestionArea(120).isEmpty());
        assertFalse(field.mouseClickedSuggestion(10, 105, GLFW.GLFW_MOUSE_BUTTON_LEFT, 120));
        assertEquals("Ir", field.getValue());
    }

    @Test
    void hoverAndClickShareTheExclusiveBottomAndRightEdges() {
        var field = fieldAt(18);
        field.setValue("Ir");
        var area = field.suggestionArea(54).orElseThrow();
        int right = area.getX() + area.getWidth();
        int bottom = area.getY() + area.getHeight();
        assertTrue(field.isOverSuggestions(right - 1, bottom - 1, 54));
        assertFalse(field.isOverSuggestions(right, bottom - 1, 54));
        assertFalse(field.isOverSuggestions(right - 1, bottom, 54));
        assertTrue(field.mouseClickedSuggestion(right - 1, bottom - 1, GLFW.GLFW_MOUSE_BUTTON_LEFT, 54));
    }

    @Test
    void candidateTooltipStaysInsideTheFieldColumnAndAvoidsTheDropdown() {
        var positioner = SearchField.suggestionTooltipPositioner(new Rect2i(220, 20, 100, 50));
        var below = positioner.positionTooltip(420, 120, 310, 60, 92, 20);
        assertEquals(224, below.x());
        assertEquals(76, below.y());

        var nearBottom = SearchField.suggestionTooltipPositioner(new Rect2i(220, 60, 100, 50));
        var above = nearBottom.positionTooltip(420, 120, 310, 95, 92, 20);
        assertEquals(224, above.x());
        assertEquals(34, above.y());
    }

    @Test
    void candidateTooltipUsesTheLargerSideWhenNeitherSideFits() {
        var positioner = SearchField.suggestionTooltipPositioner(new Rect2i(220, 20, 100, 50));
        var position = positioner.positionTooltip(420, 120, 310, 60, 92, 60);
        assertEquals(56, position.y());
    }

    @Test
    void glassTooltipRaisesThePlateBeforePaintingOnBothPlatforms() throws Exception {
        var source = Path.of("src/main/java/com/dishanhai/sh_better_ui/client/glass/GlassTooltip.java");
        for (var path : List.of(source, Path.of("neoforge-1.21.1").resolve(source))) {
            String code = Files.readString(path);
            int depth = code.indexOf("g.pose().translate(0f, 0f, TOOLTIP_Z)");
            int smoke = code.indexOf("GlassSurface.smokePanel(g");
            int glass = code.indexOf("GlassTheme.GLASS.tooltipPanel(g");
            assertTrue(depth >= 0 && depth < smoke && depth < glass, path.toString());
        }
    }

    @Test
    void scopeFilterAndMaxLengthApplyBeforeTheNextSelection() {
        var field = fieldAt(20);
        field.setValue("Ir");
        assertTrue(field.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0));
        field.setScope(value -> value == 1);
        field.setMaxLength(4);
        field.setFilter(text -> !text.equals("Iron"));
        assertFalse(field.dismissSuggestions());
        field.setFilter(text -> true);
        assertTrue(field.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals("Iron", field.getValue());
    }

    private static SearchField<Integer> fieldAt(int y) {
        return fieldAt(y, UiStyle.DEFAULT);
    }

    private static SearchField<Integer> metadataField() {
        var field = fieldAt(20);
        field.setIndex(new SearchIndex<>(List.of(
                new SearchIndex.Entry<>(1, "Iron Ingot", List.of("minecraft:iron_ingot"), List.of(),
                        "Minecraft", List.of("forge:ingots/iron")),
                new SearchIndex.Entry<>(2, "Iron Plate", List.of("other:iron_plate"), List.of(),
                        "Other Mods", List.of("forge:plates/iron")))));
        return field;
    }

    private static SearchField<Integer> fieldAt(int y, UiStyle style) {
        Font font = new Font(id -> null, false) {
            @Override
            public String plainSubstrByWidth(String text, int width) {
                return plainSubstrByWidth(text, width, false);
            }

            @Override
            public String plainSubstrByWidth(String text, int width, boolean backwards) {
                int length = Math.clamp(width, 0, text.length());
                return backwards ? text.substring(text.length() - length) : text.substring(0, length);
            }
        };
        var index = new SearchIndex<>(List.of(new SearchIndex.Entry<>(1, "Iron", List.of(), List.of()),
                new SearchIndex.Entry<>(2, "Iridium", List.of(), List.of())));
        var field = new SearchField<>(font, 10, y, 100, 18, index, style, UiPreferences.DEFAULT);
        field.setFocused(true);
        return field;
    }
}
