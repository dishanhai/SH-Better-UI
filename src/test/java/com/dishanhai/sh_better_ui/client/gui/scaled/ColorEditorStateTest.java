package com.dishanhai.sh_better_ui.client.gui.scaled;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
class ColorEditorStateTest {
    @Test void grayAndBlackRetainHueThroughCommitAndCancellation() {
        var s = new ColorEditorState(0xff00ff00, 0xffffffff, c -> {}, c -> {});
        s.preview(1, 0); s.commit(); assertEquals(1f / 3, s.channel(0), .0001f);
        s.preview(0, .7f); s.cancel(); assertEquals(1f / 3, s.channel(0), .0001f);
        s.preview(1, 1); assertEquals(0xff00ff00, s.color());
        s.preview(2, 0); s.preview(0, .6f); s.commit();
        s.preview(2, 1); assertEquals(.6f, s.channel(0));
    }
    @Test void commitIsSingleAndCancelRestoresPreviewWithoutCommit() {
        var previews = new ArrayList<Integer>(); var commits = new ArrayList<Integer>();
        var s = new ColorEditorState(0xffff0000, 0xff00ff00, previews::add, commits::add);
        s.preview(3, .5f); assertEquals(0x80ff0000, s.color()); assertTrue(commits.isEmpty());
        s.cancel(); assertEquals(0xffff0000, previews.getLast()); assertTrue(commits.isEmpty());
        s.preview(1, .5f); s.commit(); s.commit(); assertEquals(1, commits.size());
        assertEquals(s.color(), s.committedColor());
    }
    @Test void boundariesResetExternalValuesAndValidation() {
        var commits = new ArrayList<Integer>();
        var s = new ColorEditorState(0xffff0000, 0xff00ff00, c -> {}, commits::add);
        s.preview(3, -2); assertEquals(0, s.color() >>> 24);
        s.preview(3, 5); assertEquals(255, s.color() >>> 24);
        assertThrows(IllegalArgumentException.class, () -> s.preview(0, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> s.preview(4, .5f));
        s.reset(); s.reset(); assertEquals(1, commits.size()); assertEquals(0xff00ff00, s.color());
        s.setValue(0xff101010); assertFalse(s.editing()); assertEquals(1, commits.size());
        assertEquals(0xff101010, s.committedColor());
    }
    @Test void hueAndAlphaRampsHaveExactEndpoints() {
        var s = new ColorEditorState(0xff00ff00, 0, c -> {}, c -> {});
        assertEquals(0xffff0000, s.rampColor(0, 0)); assertEquals(0xffff0000, s.rampColor(0, 1));
        assertEquals(0x0000ff00, s.rampColor(3, 0)); assertEquals(0xff00ff00, s.rampColor(3, 1));
    }
}
