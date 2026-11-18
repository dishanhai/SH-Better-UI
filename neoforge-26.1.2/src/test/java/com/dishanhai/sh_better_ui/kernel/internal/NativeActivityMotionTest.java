// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.live.*;
import com.dishanhai.sh_better_ui.kernel.internal.live.SurfaceTransition;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeActivityMotionTest {
    static final SurfacePlacement.Rect VIEW = new SurfacePlacement.Rect(0,0,400,200);
    static final ActivityService.Content A = ActivityService.Content.label(ActivityService.Text.literal("Song A"));
    static final ActivityService.Content B = ActivityService.Content.label(ActivityService.Text.literal("Song B"));
    static SurfaceTransition.Target<ActivityService.Content> target(double x, ActivityService.Content content, String key) {
        return new SurfaceTransition.Target<>(SurfacePresentation.COMPACT,new SurfaceGeometry(x,10,100,20,10,1),content,key);
    }
    static NativeActivityMotion fixed() {
        var m=new NativeActivityMotion();m.configure(new LiveSurfaceSettings.Motion(LiveSurfaceSettings.MotionMode.NONE,.2,1.1));
        m.step(target(0,A,"a"),VIEW,List.of(),0,false);return m;
    }
    @Test void interruptedSongChangesPreserveDrawnGeometryAndMix() {
        var m=fixed();m.configure(LiveSurfaceSettings.DEFAULT.motion());
        m.step(target(200,B,"b"),VIEW,List.of(),10,false);
        var before=m.step(target(200,B,"b"),VIEW,List.of(),60,false);
        var after=m.step(target(100,A,"a"),VIEW,List.of(),60,false);
        assertEquals(before.animated(),after.animated());assertTrue(after.running());
        assertEquals(1,after.contentMix().stream().mapToDouble(SurfaceTransition.Layer::weight).sum(),1e-6);
    }
    @Test void progressAndLyricsRefreshDoesNotRestartSettledGeometry() {
        var m=fixed();var refreshed=m.step(target(0,B,"a"),VIEW,List.of(),10,false);
        assertFalse(refreshed.running());assertSame(B,refreshed.contentMix().getFirst().content());
    }
    @Test void reducedMotionChangesGeometryWithoutSlidingAndSuspensionClearsIt() {
        var m=fixed();m.configure(LiveSurfaceSettings.DEFAULT.motion());
        var state=m.step(target(200,B,"b"),VIEW,List.of(),10,true);
        assertEquals(200,state.animated().x());m.suspend(20);
        var next=m.step(target(200,B,"b"),VIEW,List.of(),21,false);
        assertTrue(next.contentMix().stream().noneMatch(l->l.content()==A));
    }
    @Test void animatedPathNeverCrossesNewReservedBounds() {
        var m=fixed();m.configure(LiveSurfaceSettings.DEFAULT.motion());
        var obstacle=new SurfacePlacement.Rect(0,0,120,80);
        var state=m.step(target(200,B,"b"),VIEW,List.of(obstacle),10,false);
        assertEquals(200,state.animated().x());assertFalse(state.running());
    }
    @Test void compactCapsuleKeepsItsSmallHeightSeparateFromExpandedPresentation() {
        var compact = ClientLiveActivities.candidate(SurfacePresentation.COMPACT, SurfaceTokens.DARK,
                LiveSurfaceSettings.DEFAULT.layout(), 400, 200);
        var expanded = ClientLiveActivities.candidate(SurfacePresentation.EXPANDED, SurfaceTokens.DARK,
                LiveSurfaceSettings.DEFAULT.layout(), 400, 200);
        assertEquals(20, compact.geometry().height());
        assertTrue(expanded.geometry().height() >= 48);
        assertEquals(10, compact.geometry().radius());
    }
    @Test void alphaIsAppliedToEveryNativePaintColor() {
        var faded=ClientLiveActivities.fade(SurfaceTokens.DARK,0);
        assertEquals(0,faded.surface()>>>24);assertEquals(0,faded.title()>>>24);assertEquals(0,faded.accent()>>>24);
        assertEquals(SurfaceTokens.DARK.dimensions(),faded.dimensions());
    }
}
