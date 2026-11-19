package com.dishanhai.sh_better_ui.client.gui.scaled;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationAdmission;
import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationNotices;
import static org.junit.jupiter.api.Assertions.*;
class IslandNoticeQueueTest {
    private static IslandNoticeQueue.Notice notice(String id) { return new IslandNoticeQueue.Notice(id,id,"",-1,1000); }
    @Test void nonEvictingAdmissionCanReplaceFullQueueEntry() {
        var q=new IslandNoticeQueue();for(int i=0;i<8;i++)assertTrue(q.tryOffer(notice("n"+i)));
        assertFalse(q.tryOffer(notice("overflow")));
        assertTrue(q.tryOffer(new IslandNoticeQueue.Notice("n0","changed","",-1,1000)));
        assertEquals("changed",q.sample(0).orElseThrow().title());
        assertTrue(q.tryOffer(notice("later")));
    }
    @Test void fifoAndActiveUpdateDoNotRestartDeadline() {
        var q = new IslandNoticeQueue(); q.offer(notice("a")); q.offer(notice("b"));
        assertEquals("a",q.sample(0).orElseThrow().id());
        q.offer(new IslandNoticeQueue.Notice("a","updated","",.5,1000));
        assertEquals("updated",q.sample(999).orElseThrow().title());
        assertEquals("b",q.sample(1000).orElseThrow().id());
        assertTrue(q.sample(2000).isEmpty());
    }
    @Test void boundedCoalescedQueueAndClockReset() {
        var q = new IslandNoticeQueue();
        for(int i=0;i<20;i++) q.offer(notice("n"+i));
        assertEquals("n12",q.sample(100).orElseThrow().id());
        q.offer(notice("n13")); q.dismiss();
        assertEquals("n13",q.sample(101).orElseThrow().id());
        assertTrue(q.sample(0).isEmpty());
    }
    @Test void rejectUnboundedHoldAndNormalizeProgress() {
        assertThrows(IllegalArgumentException.class,()->new IslandNoticeQueue.Notice("a","","",0,0));
        assertEquals(-1,new IslandNoticeQueue.Notice("a","","",Double.NaN,1000).progress());
        assertEquals(1,new IslandNoticeQueue.Notice("a","","",4,1000).progress());
    }
    private static IslandNoticeQueue.Notice priority(String id, IslandNoticeQueue.Priority priority, long expiry) {
        return new IslandNoticeQueue.Notice(id,id,"",-1,1000,priority,expiry);
    }
    @Test void highPriorityInterruptsAndResumesRemainingBudgetBeforePeers() {
        var q = new IslandNoticeQueue(); q.offer(notice("a")); q.sample(0); q.sample(400);
        q.offer(notice("peer")); q.offer(priority("urgent",IslandNoticeQueue.Priority.HIGH,0));
        assertEquals("urgent",q.sample(400).orElseThrow().id());
        assertEquals("a",q.sample(1400).orElseThrow().id());
        assertEquals("a",q.sample(1999).orElseThrow().id());
        assertEquals("peer",q.sample(2000).orElseThrow().id());
    }
    @Test void hiddenTimeDoesNotConsumeBudgetAndSameTimestampIsIdempotent() {
        var q = new IslandNoticeQueue(); q.offer(notice("a")); q.sample(0); q.sample(400);
        q.pause();
        assertEquals("a",q.sample(10000,false).orElseThrow().id());
        q.sample(20000,true); q.sample(20000,true);
        assertEquals("a",q.sample(20599).orElseThrow().id());
        assertTrue(q.sample(20600).isEmpty());
    }
    @Test void wallExpiryAppliesWhileHiddenAndToWaitingNotices() {
        var q = new IslandNoticeQueue();
        q.offer(priority("urgent",IslandNoticeQueue.Priority.HIGH,500));
        q.offer(priority("stale",IslandNoticeQueue.Priority.LOW,400)); q.offer(notice("fresh"));
        assertEquals("urgent",q.sample(0).orElseThrow().id());
        assertEquals("fresh",q.sample(500,false).orElseThrow().id());
        q.sample(10000,false);
        assertEquals("fresh",q.sample(20000,true).orElseThrow().id());
    }
    @Test void cancelAffectsOnlyItsChannelAndOverflowProtectsHighPriority() {
        var q = new IslandNoticeQueue(); q.offer(priority("urgent",IslandNoticeQueue.Priority.HIGH,0));
        for(int i=0;i<20;i++) q.offer(notice("n"+i));
        assertEquals("urgent",q.sample(0).orElseThrow().id());
        q.cancel("urgent"); q.cancel("n13");
        assertEquals("n14",q.sample(0).orElseThrow().id());
    }
    @Test void renderAcknowledgementChargesOnlyDrawnFramesAndCapsLongStalls() {
        var q = new IslandNoticeQueue(); var a = notice("a"); q.offer(a);
        q.peek(0); q.peek(50000); // Mere visibility queries do not consume presentation time.
        q.presented(a,50000);
        assertEquals(a,q.peek(60000).orElseThrow()); // Stall charges at most 250ms.
        q.peek(60000); q.presented(a,60000);
        q.peek(60250); q.presented(a,60250);
        q.peek(60500); q.presented(a,60500);
        assertTrue(q.peek(60750).isEmpty());
    }
    @Test void stalePresentationCannotChargeReplacementAndScenePausePreservesBudget() {
        var q = new IslandNoticeQueue(); var a = notice("a"); q.offer(a); q.peek(0);
        q.offer(new IslandNoticeQueue.Notice("a","new","",-1,1000));
        q.presented(a,0); q.peek(5000); // stale object, no acknowledgement
        var current = q.peek(5000).orElseThrow(); q.presented(current,5000); q.pause();
        assertEquals(current,q.peek(10000).orElseThrow());
    }
    @Test void instantPreemptsSamePriorityButDoesNotBounceOnRepeatedQueries() {
        var q=new IslandNoticeQueue();
        q.offer(priority("volume",IslandNoticeQueue.Priority.INSTANT,0)); q.sample(0);
        q.offer(priority("theme",IslandNoticeQueue.Priority.INSTANT,0));
        assertEquals("theme",q.sample(200).orElseThrow().id());
        assertEquals("theme",q.sample(200).orElseThrow().id());
        assertEquals("theme",q.sample(1199).orElseThrow().id());
        assertEquals("volume",q.sample(1200).orElseThrow().id());
    }
    @Test void resumedNoticeHasSevenHundredVisibleMillisecondsOfProtection() {
        var q=new IslandNoticeQueue(); q.offer(notice("normal")); q.sample(0);
        q.offer(priority("urgent",IslandNoticeQueue.Priority.HIGH,0)); q.sample(100);
        assertEquals("normal",q.sample(1100).orElseThrow().id());
        q.offer(priority("volume",IslandNoticeQueue.Priority.INSTANT,0));
        assertEquals("normal",q.sample(1799).orElseThrow().id());
        assertEquals("volume",q.sample(1800).orElseThrow().id());
    }
    @Test void highPriorityBypassesResumeProtectionButSameHighRemainsFifo() {
        var q=new IslandNoticeQueue(); q.offer(notice("normal")); q.sample(0);
        q.offer(priority("urgent",IslandNoticeQueue.Priority.HIGH,0)); q.sample(100); q.sample(1100);
        q.offer(priority("urgent2",IslandNoticeQueue.Priority.HIGH,0));
        assertEquals("urgent2",q.sample(1100).orElseThrow().id());
        q.offer(priority("urgent3",IslandNoticeQueue.Priority.HIGH,0));
        assertEquals("urgent2",q.sample(1100).orElseThrow().id());
        assertEquals("urgent3",q.sample(2100).orElseThrow().id());
    }
    @Test void interruptedInstantDoesNotBlockAnotherNewInstantBehindIt() {
        var q=new IslandNoticeQueue();
        q.offer(priority("a",IslandNoticeQueue.Priority.INSTANT,0)); q.sample(0);
        q.offer(priority("b",IslandNoticeQueue.Priority.INSTANT,0)); q.sample(100);
        q.offer(priority("c",IslandNoticeQueue.Priority.INSTANT,0));
        assertEquals("c",q.sample(200).orElseThrow().id());
    }
    @Test void sameTimestampPeekPreservesAcknowledgement() {
        var q=new IslandNoticeQueue(); var a=notice("a"); q.offer(a);
        for(long now=0;now<1000;now+=250) {
            assertEquals(a,q.peek(now).orElseThrow()); q.presented(a,now); q.peek(now); q.peek(now);
        }
        assertTrue(q.peek(1000).isEmpty());
    }
    @Test void cancelDismissAndSwitchNeverTransferAcknowledgement() {
        for(boolean cancel:new boolean[]{true,false}) {
            var q=new IslandNoticeQueue(); var a=notice("a"); var b=notice("b");
            q.offer(a); q.peek(0); q.presented(a,0);
            if(cancel) q.cancel("a"); else q.dismiss();
            q.offer(b); q.peek(0); q.presented(a,0); q.peek(250);
            for(long now=250;now<1250;now+=250) {
                assertEquals(b,q.peek(now).orElseThrow()); q.presented(b,now); q.peek(now+250);
            }
            assertTrue(q.peek(1250).isEmpty());
        }
        var q=new IslandNoticeQueue(); var a=notice("a");
        var high=priority("high",IslandNoticeQueue.Priority.HIGH,0);
        q.offer(a); q.peek(0); q.presented(a,0); q.offer(high); q.peek(0); q.presented(a,0);
        q.peek(250); q.presented(high,250);
        assertEquals(high,q.peek(500).orElseThrow());
        q.peek(10000); // no further acknowledgement: only 500ms consumed
        assertEquals(high,q.peek(20000).orElseThrow());
    }
    @Test void sameChannelCannotExtendOrRemoveDeadlineWhileWaitingOrActive() {
        for(boolean active:new boolean[]{true,false}) {
            var q=new IslandNoticeQueue(); q.offer(priority("a",IslandNoticeQueue.Priority.NORMAL,500));
            if(active) q.peek(0);
            q.offer(priority("a",IslandNoticeQueue.Priority.NORMAL,1000));
            q.offer(priority("a",IslandNoticeQueue.Priority.NORMAL,0));
            assertEquals(500,q.peek(100).orElseThrow().expiresAtMs());
            assertTrue(q.peek(500).isEmpty());
        }
        var q=new IslandNoticeQueue(); q.offer(notice("a")); q.peek(0);
        q.offer(priority("a",IslandNoticeQueue.Priority.NORMAL,1000));
        q.offer(priority("a",IslandNoticeQueue.Priority.NORMAL,500));
        assertTrue(q.peek(500).isEmpty());
    }
    @Test void continuousContentUpdatesConsumePreviouslyPresentedEntryBudget() {
        var q=new IslandNoticeQueue();
        for(long now=0;now<1000;now+=100) {
            var update=new IslandNoticeQueue.Notice("progress","update"+now,"",now/1000.0,1000);
            q.offer(update);
            assertEquals(update,q.peek(now).orElseThrow());
            q.presented(update,now);
            q.peek(now);
        }
        q.offer(new IslandNoticeQueue.Notice("progress","final","",1,1000));
        assertTrue(q.peek(1000).isEmpty());
    }
    @Test void updatedEntryKeepsExistingAckButRejectsLateOldReferenceAck() {
        var q=new IslandNoticeQueue(); var old=notice("a"); q.offer(old); q.peek(0); q.presented(old,0);
        var updated=new IslandNoticeQueue.Notice("a","new","",.5,1000); q.offer(updated);
        assertEquals(updated,q.peek(250).orElseThrow()); // first confirmed 250ms consumed
        q.presented(old,250); // cannot acknowledge the new frame
        q.peek(500); // no further time consumed
        for(long now=500;now<1250;now+=250) {
            assertEquals(updated,q.peek(now).orElseThrow()); q.presented(updated,now);
        }
        assertTrue(q.peek(1250).isEmpty());
    }
    @Test void fullQueueDefersWithoutEvictionThenRetriesOnlyUntilAdmitted() {
        var queue=new IslandNoticeQueue();for(int i=0;i<8;i++)queue.offer(notice("existing"+i));
        var admission=new MusicOperationAdmission();admission.update(List.of(new MusicOperationNotices.Update("result",1L,"succeeded",false,false,3500)),queue::cancel);
        var accepted=new AtomicInteger();java.util.function.Predicate<MusicOperationNotices.Update> offer=u->{boolean ok=queue.tryOffer(notice(u.id()));if(ok)accepted.incrementAndGet();return ok;};
        admission.flush(offer);admission.flush(offer);assertEquals(0,accepted.get());
        assertEquals("existing0",queue.sample(0).orElseThrow().id());
        admission.flush(offer);admission.flush(offer);assertEquals(1,accepted.get());
        for(int i=1;i<8;i++)assertEquals("existing"+i,queue.sample(i*1000).orElseThrow().id());
        assertEquals("result",queue.sample(8000).orElseThrow().id());assertTrue(queue.sample(9000).isEmpty());
    }

}
