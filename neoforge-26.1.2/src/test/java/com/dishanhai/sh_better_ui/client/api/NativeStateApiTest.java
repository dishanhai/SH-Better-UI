// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiKeyframeAnimation;
import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Consumer-facing contracts, without booting a screen or relying on renderer compatibility. */
class NativeStateApiTest {
    @Test
    void richDocumentEditorKeepsCallerDataWithoutOpeningScreen() throws Exception {
        try (var fixture = NativeTestClient.install()) {
            var original = new com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument(List.of(
                    new com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun(
                            com.dishanhai.sh_better_ui.kernel.api.UiId.parse("test:body"), "Caller content")));
            var first = ShBetterUiApi.richDocuments().editor(null,
                    net.minecraft.network.chat.Component.literal("Editor"), original);
            var second = ShBetterUiApi.richDocuments().editor(null,
                    net.minecraft.network.chat.Component.literal("Other editor"), original);
            assertNotSame(first, second);
            assertEquals(original, first.document());
            assertEquals(original, second.document());
            assertNotNull(ShBetterUiApi.settings().richDocumentEditor(null));
        }
    }

    @Test
    void publicGroupsAreStableButMotionStateBelongsToEachView() {
        assertSame(ShBetterUiApi.motion(), ShBetterUiApi.motion());
        var stops = List.of(new UiKeyframeAnimation.Keyframe(0, 0),
                new UiKeyframeAnimation.Keyframe(100, 1));
        try (var first = ShBetterUiApi.motion().keyframes(stops);
             var second = ShBetterUiApi.motion().keyframes(stops)) {
            assertNotSame(first, second);
            first.restart(10);
            assertEquals(0.5f, first.sample(60), 0.0001f);
            assertEquals(0, second.sample(60));
            first.pause(60);
            assertEquals(0.5f, first.sample(160), 0.0001f);
            first.resume(160);
            assertEquals(1, first.sample(210));
        }
    }

    @Test
    void dragOwnershipReleaseAndCancelStayIndependent() {
        var api = ShBetterUiApi.interactions();
        assertSame(api, ShBetterUiApi.interactions());
        var drag = api.pointerDrag();
        var other = api.pointerDrag();
        assertTrue(drag.begin(0, 10, 20));
        assertFalse(other.active());
        assertNull(drag.move(1, 20, 40));
        assertEquals(5, drag.move(0, 15, 28).x());
        assertEquals(8, drag.offsetY());
        assertEquals(2, drag.release(0, 17, 30).x());
        assertFalse(drag.active());
        assertTrue(drag.begin(0, 17, 30));
        drag.move(0, 30, 50);
        drag.cancel();
        assertFalse(drag.active());
        assertEquals(0, drag.offsetX());
    }

    @Test
    void historyAndViewportRetainUndoAndPointerAnchor() {
        var api = ShBetterUiApi.interactions();
        var history = api.<String>history(2);
        history.remember("old");
        history.remember("middle");
        history.remember("new");
        assertEquals("new", history.restore().orElseThrow());
        assertEquals("middle", history.restore().orElseThrow());
        assertTrue(history.restore().isEmpty());
        var original = api.viewport(10, 20, 1);
        var zoomed = original.zoomAt(80, 90, 2, 0.5, 3);
        assertEquals(original.contentX(80), zoomed.contentX(80));
        assertEquals(original.contentY(90), zoomed.contentY(90));
        assertEquals(0, api.dragScrollOffset(5, 10, 100));
        assertEquals(100, api.dragScrollOffset(95, -10, 100));
    }

    @Test
    void countdownPausesWithoutSpendingSuspendedTime() {
        var countdown = ShBetterUiApi.interactions().countdown(100, 0);
        countdown.update(30, true);
        countdown.update(300, false);
        assertEquals(0.7f, countdown.remainingFraction(), 0.0001f);
        countdown.update(370, false);
        assertTrue(countdown.expired());
    }

    @Test
    void localSearchPreservesIdentifiersAliasesAndScope() {
        assertSame(ShBetterUiApi.search(), ShBetterUiApi.search());
        var apple = new SearchIndex.Entry<>(1, "Apple", List.of("food:apple"), List.of("fruit"));
        var pear = new SearchIndex.Entry<>(2, "Pear", List.of("food:pear"), List.of("fruit"));
        var index = ShBetterUiApi.search().index(List.of(apple, pear));
        assertEquals(List.of(apple), index.search("food:apple"));
        assertEquals(List.of(pear), index.search("fruit", value -> value == 2));
        assertTrue(index.search("unmatched").isEmpty());
    }
}
