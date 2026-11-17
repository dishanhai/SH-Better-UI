// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Point;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Rect;
import org.junit.jupiter.api.Test;
import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RichTextGeometryHitTest {
    @Test
    void presentedHitUsesTheClippedMeshUnionAndTheRegistrationLifetime() {
        var visible = new Bounds<Local>(5, 5, 25, 15);
        var triangle = FilledGeometry.polygon(List.of(
                new Point<Local>(0, 0), new Point<Local>(10, 0), new Point<Local>(10, 10)), 32);
        var decoration = FilledGeometry.rectangle(new Bounds<Local>(18, 5, 22, 15));
        var meshes = new ArrayList<>(List.of(triangle.clip(visible, 32), decoration));
        var active = new AtomicBoolean(true);
        var presented = new RichTextExtensions.Hit(true, meshes, active::get);

        meshes.clear();
        assertTrue(presented.contains(9, 6));
        assertTrue(presented.contains(20, 8));
        assertFalse(presented.contains(6, 9));
        assertFalse(presented.contains(12, 8));
        assertFalse(presented.contains(9, 3));
        assertFalse(presented.contains(Double.NaN, 9));
        assertFalse(presented.contains(1_000_001, 8));

        active.set(false);
        assertFalse(presented.contains(9, 6));
        assertFalse(presented.contains(20, 8));
    }

    @Test
    void emptyPreciseHitAndFailedOrSkippedDrawsDoNotExpandToTheFragment() {
        assertFalse(new RichTextExtensions.Hit(true, List.of(), () -> true).contains(1, 1));
        assertFalse(RichTextExtensions.PaintResult.CLIPPED.hit().contains(1, 1));
        assertFalse(RichTextExtensions.PaintResult.FAILED.hit().contains(1, 1));
        assertTrue(RichTextExtensions.PaintResult.FAILED.placeholder());
        assertTrue(RichTextExtensions.PaintResult.UNAVAILABLE.hit().contains(1, 1));
    }

    @Test
    void readabilityGroupsOnlyContiguousFragmentsWithAnExactNativeAdvance() {
        var first = new Rect(2, 4, 9, 9);
        var next = new Rect(11, 4, 5, 9);
        assertTrue(RichTextView.canJoin(first, next, 9f));
        assertFalse(RichTextView.canJoin(first, next, 8.75f));
        assertFalse(RichTextView.canJoin(first, new Rect(12, 4, 5, 9), 9f));
        assertFalse(RichTextView.canJoin(first, new Rect(11, 13, 5, 9), 9f));
        assertFalse(RichTextView.canJoin(first, new Rect(11, 4, 0, 9), 9f));
    }
    @Test
    void literalMarkdownCanBatchWithoutRemovingItsTrustBoundary() {
        var style = UiRichText.literalStyle(Style.EMPTY.withBold(true).withColor(0xffccaa));
        assertTrue(RichTextView.canJoinStyle(Style.EMPTY));
        assertTrue(RichTextView.canJoinStyle(style));
        assertTrue(UiRichText.isLiteral(style));
        String text = "<rich:item=minecraft:diamond> §c literal";
        var source = Component.literal(text).setStyle(style);
        var prepared = UiRichText.prepare(source);
        assertEquals(text, prepared.getString());
        prepared.visit((nativeStyle, value) -> {
            if (!value.isEmpty()) assertTrue(UiRichText.isLiteral(nativeStyle));
            return java.util.Optional.empty();
        }, Style.EMPTY);
    }

    @Test
    void batchingStillRejectsInteractiveMetadataAndSpecialRendering() {
        var literal = UiRichText.literalStyle(Style.EMPTY);
        assertFalse(RichTextView.canJoinStyle(Style.EMPTY.withInsertion("user insertion")));
        assertFalse(RichTextView.canJoinStyle(literal.withClickEvent(
                new ClickEvent(ClickEvent.Action.OPEN_URL, "https://example.com"))));
        assertFalse(RichTextView.canJoinStyle(literal.withFont(ResourceLocation.tryParse("test:font"))));
        assertFalse(RichTextView.canJoinStyle(literal.withUnderlined(true)));
        assertFalse(RichTextView.canJoinStyle(literal.withStrikethrough(true)));
        assertFalse(RichTextView.canJoinStyle(literal.withObfuscated(true)));
    }

    @Test
    void adjacentLiteralSequencesCannotReconstructAnExecutableInlineTag() {
        var literal = UiRichText.literalStyle(Style.EMPTY);
        var first = FormattedCharSequence.forward("<rich:item=", literal);
        var second = FormattedCharSequence.forward("minecraft:diamond>", literal);
        FormattedCharSequence joined = sink -> first.accept(sink) && second.accept(sink);
        var result = new StringBuilder();
        UiRichText.prepare(joined).accept((index, style, codePoint) -> {
            assertTrue(UiRichText.isLiteral(style));
            result.appendCodePoint(codePoint);
            return true;
        });
        assertEquals("<rich:item=minecraft:diamond>", result.toString());
    }

}
