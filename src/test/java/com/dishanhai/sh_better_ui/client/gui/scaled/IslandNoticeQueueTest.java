package com.dishanhai.sh_better_ui.client.gui.scaled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IslandNoticeQueueTest {
    private static IslandNoticeQueue.Notice notice(String id) { return new IslandNoticeQueue.Notice(id,id,"",-1,1000); }
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
}
