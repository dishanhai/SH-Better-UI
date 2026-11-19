// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OnlineMusicFeedbackTest {
    @Test void newestEventReplacesPreviousWithoutAccumulatingNotifications() {
        var f=new OnlineMusicFeedback();f.show(Component.literal("requested"),100);
        f.show(Component.literal("failed"),200);
        assertEquals("failed",f.current(201).getString());
        assertEquals("",f.current(4700).getString());
    }
    @Test void clearPreventsStaleFeedbackAfterViewSuspension() {
        var f=new OnlineMusicFeedback();f.show(Component.literal("loading"),100);f.clear();
        assertEquals("",f.current(101).getString());
        f.show(Component.literal("new operation"),200);assertEquals("new operation",f.current(201).getString());
    }
    @Test void copiesCallerOwnedTextAndSaturatesExpiry() {
        var f=new OnlineMusicFeedback();var text=Component.literal("value");f.show(text,Long.MAX_VALUE-2);text.append("changed");
        assertEquals("value",f.current(Long.MAX_VALUE-1).getString());
        assertEquals("",f.current(Long.MAX_VALUE).getString());
    }
    @Test void reducedMotionDoesNotRemoveStaticFeedback() {
        var f=new OnlineMusicFeedback();var motion=new OnlineMusicViewMotion();
        f.show(Component.literal("cancelled"),100);motion.pulse(100);
        assertEquals(0,motion.sample(101,true,false).emphasis());assertEquals("cancelled",f.current(101).getString());
    }
}
