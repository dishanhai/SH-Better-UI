// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeThemeDraftTest {
    @Test void undoIsBoundedAndNoOpStagesDoNotConsumeHistory() {
        var draft=new NativeThemeDraft(ClientUiSettings.DEFAULT);
        draft.stage(draft.current().theme());
        assertFalse(draft.canUndo());
        for(int i=0;i<20;i++) draft.stage(draft.current().theme().withRegisteredTheme("example:theme_"+i));
        for(int i=0;i<16;i++) assertTrue(draft.undo());
        assertFalse(draft.undo());
        draft.cancel();assertFalse(draft.dirty());assertFalse(draft.canUndo());
    }
    @Test void failedSavePreservesUndoButSuccessfulSaveClearsIt() {
        var draft=new NativeThemeDraft(ClientUiSettings.DEFAULT);
        draft.stage(draft.current().theme().withRegisteredTheme("sh_better_ui:light"));
        assertFalse(draft.apply(value->false));assertTrue(draft.canUndo());
        assertTrue(draft.apply(value->true));assertFalse(draft.canUndo());
    }

    @Test void selectionRemainsIsolatedAndCancelRestoresBaseline() {
        var initial=ClientUiSettings.DEFAULT;
        var draft=new NativeThemeDraft(initial);
        draft.stage(initial.theme().withRegisteredTheme("sh_better_ui:dark"));
        assertTrue(draft.dirty());
        assertEquals(initial.withTheme(draft.current().theme()),draft.current());
        draft.cancel();assertEquals(initial,draft.current());assertFalse(draft.dirty());
    }
    @Test void failedApplyKeepsDraftAndSuccessfulApplyBecomesCancelBaseline() {
        var draft=new NativeThemeDraft(ClientUiSettings.DEFAULT);
        draft.stage(draft.current().theme().withRegisteredTheme("sh_better_ui:dark"));
        var desired=draft.current();
        assertFalse(draft.apply(value->false));assertTrue(draft.dirty());assertEquals(desired,draft.current());
        var saved=new AtomicReference<ClientUiSettings>();
        assertTrue(draft.apply(value->{saved.set(value);return true;}));assertEquals(desired,saved.get());assertFalse(draft.dirty());
        draft.stage(draft.current().theme().withRegisteredTheme("sh_better_ui:light"));
        draft.cancel();assertEquals(desired,draft.current());
    }
    @Test void unchangedApplyDoesNotWriteAndExceptionsRetainDraft() {
        var draft=new NativeThemeDraft(ClientUiSettings.DEFAULT);
        assertTrue(draft.apply(value->{fail("unchanged draft must not write");return false;}));
        draft.stage(draft.current().theme().withRegisteredTheme("sh_better_ui:light"));
        assertThrows(IllegalStateException.class,()->draft.apply(value->{throw new IllegalStateException("disk unavailable");}));
        assertTrue(draft.dirty());
    }
}
