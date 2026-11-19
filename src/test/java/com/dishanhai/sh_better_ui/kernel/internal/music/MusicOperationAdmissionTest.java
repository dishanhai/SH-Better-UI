// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class MusicOperationAdmissionTest {
    private MusicOperationNotices.Update update(String id){return new MusicOperationNotices.Update(id,1L,"succeeded",false,false,3500);}
    @Test void refusalRetriesOnlyUntilAdmitted() {
        var admission=new MusicOperationAdmission();admission.update(List.of(update("result")),id->{});
        admission.flush(u->false);admission.flush(u->false);assertEquals(1,admission.pendingCount());
        var accepted=new AtomicInteger();admission.flush(u->{accepted.incrementAndGet();return true;});
        admission.flush(u->{accepted.incrementAndGet();return true;});
        assertEquals(0,admission.pendingCount());assertEquals(1,accepted.get());
    }
    @Test void cancellationAndDisableRemoveDeferredItemsWithoutReplay() {
        var admission=new MusicOperationAdmission();admission.update(List.of(update("result")),id->{});
        admission.flush(u->false);admission.update(List.of(new MusicOperationNotices.Update("result",1L,"",true,false,0)),id->{});
        admission.flush(u->{fail("cancelled result replayed");return true;});
        admission.update(List.of(update("result")),id->{});admission.clear(id->{});
        admission.flush(u->{fail("disabled result replayed");return true;});assertEquals(0,admission.pendingCount());
    }
    @Test void terminalTransitionReplacesPendingRunningState() {
        var admission=new MusicOperationAdmission();admission.update(List.of(new MusicOperationNotices.Update("job",1L,"running",false,false,8000)),id->{});
        admission.flush(u->false);admission.update(List.of(new MusicOperationNotices.Update("job",1L,"",true,false,0),update("job")),id->{});
        admission.flush(u->{assertEquals("succeeded",u.key());return true;});assertEquals(0,admission.pendingCount());
        var cancelled=new ArrayList<String>();admission.clear(cancelled::add);assertEquals(List.of("job"),cancelled);
    }
}
