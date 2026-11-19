package com.dishanhai.sh_better_ui.kernel.internal.music;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class MusicOperationNoticesTest {
    final MusicOperationNotices notices=new MusicOperationNotices();
    SessionMusicOperations.Snapshot sample(long id,SessionMusicOperations.State state,boolean background) {
        return new SessionMusicOperations.Snapshot(id,SessionMusicOperations.Kind.SEARCH,"private-source",state,background,SessionMusicOperations.Error.NONE);
    }
    @Test void detachedTerminalReplacesLoadingWithFreshBudgetOnlyOnce() {
        var start=notices.observe(List.of(sample(1,SessionMusicOperations.State.RUNNING,true)),true);
        assertEquals(8000,start.get(1).ttlMs());
        var end=notices.observe(List.of(sample(1,SessionMusicOperations.State.SUCCEEDED,true)),true);
        assertTrue(end.get(0).cancel());assertEquals(3500,end.get(1).ttlMs());
        assertEquals("operation_search_succeeded",end.get(1).key());
        assertTrue(notices.observe(List.of(sample(1,SessionMusicOperations.State.SUCCEEDED,true)),true).isEmpty());
    }
    @Test void disabledAndForegroundResultsNeverReplay() {
        notices.observe(List.of(sample(1,SessionMusicOperations.State.SUCCEEDED,true)),false);
        assertTrue(notices.observe(List.of(sample(1,SessionMusicOperations.State.SUCCEEDED,true)),true).isEmpty());
        assertTrue(notices.observe(List.of(sample(2,SessionMusicOperations.State.SUCCEEDED,false)),true).stream().allMatch(MusicOperationNotices.Update::cancel));
    }
    @Test void outOfOrderCompletionAndCancellationStayIndependent() {
        notices.observe(List.of(sample(1,SessionMusicOperations.State.RUNNING,true),sample(2,SessionMusicOperations.State.SUCCEEDED,true)),true);
        var updates=notices.observe(List.of(sample(1,SessionMusicOperations.State.FAILED,true),sample(2,SessionMusicOperations.State.SUCCEEDED,true)),true);
        assertEquals(2,updates.size());assertTrue(updates.get(1).error());assertEquals("music:operation/1",updates.get(1).id());
        assertTrue(notices.observe(List.of(sample(1,SessionMusicOperations.State.CANCELLED,true)),true).stream().allMatch(MusicOperationNotices.Update::cancel));
        assertTrue(notices.observe(List.of(),true).stream().allMatch(MusicOperationNotices.Update::cancel));
    }
}
