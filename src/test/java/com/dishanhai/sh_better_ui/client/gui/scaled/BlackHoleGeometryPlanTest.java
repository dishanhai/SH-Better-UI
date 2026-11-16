package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackHoleGeometryPlanTest {
    private final BlackHoleBackdropProfile p = BlackHoleBackdropProfile.DEFAULT;
    private BlackHoleBackdropProfile copy(float size,float tilt,float inclination,float outer,
                                          BlackHoleBackdropProfile.Quality quality) {
        return new BlackHoleBackdropProfile(p.speed(),size,tilt,p.diskWidth(),p.lensing(),p.brightness(),
                p.glow(),p.opacity(),p.color(),p.backgroundColor(),inclination,p.outline(),quality,outer);
    }
    @Test void materialAndMotionChangesDoNotInvalidateGeometry() {
        var changed=new BlackHoleBackdropProfile(2,p.size(),p.tilt(),.35f,0,2,3,.2f,
                0xFF332211,0x00112233,p.inclination(),1,p.quality(),p.diskOuterRadius());
        assertEquals(BlackHoleGeometryPlan.key(p,1200,600,2),BlackHoleGeometryPlan.key(changed,1200,600,2));
        // Phase and owner are deliberately absent from the cache key/API.
        assertEquals(9,BlackHoleGeometryPlan.Key.class.getRecordComponents().length);
    }
    @Test void everyPhysicalGeometryParameterInvalidatesCache() {
        var baseline=BlackHoleGeometryPlan.key(p,1200,600,2);
        for (var changed : new BlackHoleBackdropProfile[]{
                copy(1.2f,p.tilt(),p.inclination(),p.diskOuterRadius(),p.quality()),
                copy(p.size(),.3f,p.inclination(),p.diskOuterRadius(),p.quality()),
                copy(p.size(),p.tilt(),.4f,p.diskOuterRadius(),p.quality()),
                copy(p.size(),p.tilt(),p.inclination(),20,p.quality()),
                copy(p.size(),p.tilt(),p.inclination(),p.diskOuterRadius(),BlackHoleBackdropProfile.Quality.LOW)})
            assertNotEquals(baseline,BlackHoleGeometryPlan.key(changed,1200,600,2));
        assertNotEquals(baseline,BlackHoleGeometryPlan.key(p,1200,600,1.99f));
        assertNotEquals(baseline,BlackHoleGeometryPlan.key(p,100,50,2));
    }
    @Test void pixelDimensionsRespectAspectCapAndDoNotUpsampleSmallPreviews() {
        for (var quality:BlackHoleBackdropProfile.Quality.values()) {
            var profile=copy(p.size(),p.tilt(),p.inclination(),p.diskOuterRadius(),quality);
            var large=BlackHoleGeometryPlan.key(profile,3840,2160,3840f/2160);
            assertEquals(quality.resolution(),large.width());
            assertEquals(Math.round(quality.resolution()*2160d/3840),large.height());
            var small=BlackHoleGeometryPlan.key(profile,160,90,160f/90);
            assertEquals(160,small.width()); assertEquals(90,small.height());
            var skinny=BlackHoleGeometryPlan.key(profile,1,Integer.MAX_VALUE,1f/Integer.MAX_VALUE);
            assertEquals(1,skinny.width()); assertEquals(quality.resolution(),skinny.height());
        }
    }
    @Test void balancedPreservesFullHdDetailAndHighPreservesTwoKDetail() {
        var balanced = BlackHoleGeometryPlan.key(p, 1920, 1080, 16f / 9);
        assertEquals(1536, balanced.width());
        assertEquals(864, balanced.height());
        var high = copy(p.size(), p.tilt(), p.inclination(), p.diskOuterRadius(), BlackHoleBackdropProfile.Quality.HIGH);
        assertEquals(2048, BlackHoleGeometryPlan.key(high, 2048, 1280, 1.6f).width());
        // Higher resolution still consumes no more than the existing shared frame budget.
        assertEquals(8, new BlackHoleGeometryPlan.Budget(16_384).takeRows(1, 2048, 1280));
    }
    @Test void guiScalingUsesPhysicalDimensionsUntilResolutionCap() {
        var logical=BlackHoleGeometryPlan.key(p,128,64,2);
        var physical=BlackHoleGeometryPlan.key(p,256,128,2);
        assertEquals(logical.width()*2,physical.width());
        assertEquals(logical.height()*2,physical.height());
    }
    @Test void progressiveRowsCoverEveryPixelWithoutExceedingFrameBudget() {
        for (int width:new int[]{1,127,384,768,1024,1536,2048}) {
            int remaining=1024, total=0;
            while (remaining>0) {
                int rows=BlackHoleGeometryPlan.rowsPerFrame(width,remaining,16_384);
                assertTrue(rows>0); assertTrue(rows*width<=16_384);
                remaining-=rows;total+=rows;
            }
            assertEquals(1024,total);
        }
        assertEquals(16,BlackHoleGeometryPlan.rowsPerFrame(1024,100,16_384));
        assertEquals(3,BlackHoleGeometryPlan.rowsPerFrame(1024,3,16_384));
        assertEquals(0,BlackHoleGeometryPlan.rowsPerFrame(1024,100,0));
        assertEquals(0,BlackHoleGeometryPlan.rowsPerFrame(1024,100,1023));
        assertEquals(0,BlackHoleGeometryPlan.rowsPerFrame(1024,0,16_384));
    }
    @Test void multipleOwnersAndRepeatedDrawsShareOneFrameBudget() {
        var budget=new BlackHoleGeometryPlan.Budget(16_384);
        assertEquals(10,budget.takeRows(100,1024,10));
        assertEquals(8,budget.takeRows(100,768,100));
        assertEquals(0,budget.takeRows(100,1,1));
        assertEquals(16,budget.takeRows(101,1024,100));
        assertEquals(0,budget.takeRows(101,1024,100));
        // Different widths and awkward remainders cannot overspend.
        long frame=102;
        int total=0;
        for (int width:new int[]{127,384,1,1024}) total+=width*budget.takeRows(frame,width,23);
        assertTrue(total<=16_384);
    }
    @Test void budgetHandlesSameMillisecondConservativelyAndResetExplicitly() {
        var budget=new BlackHoleGeometryPlan.Budget(16_384);
        assertEquals(16,budget.takeRows(Long.MAX_VALUE,1024,100));
        assertEquals(0,budget.takeRows(Long.MAX_VALUE,1024,100));
        assertEquals(16,budget.takeRows(Long.MIN_VALUE,1024,100));
        budget.clear();
        assertEquals(16,budget.takeRows(Long.MIN_VALUE,1024,100));
    }
    @Test void budgetCanFinishSmallBuildAndUseRemainderWithoutReset() {
        var budget=new BlackHoleGeometryPlan.Budget(16_384);
        assertEquals(2,budget.takeRows(1,1024,2));
        assertEquals(14,budget.takeRows(1,1024,200));
        assertEquals(0,budget.takeRows(1,1024,200));
        assertEquals(16,budget.takeRows(2,1024,200));
    }
    @Test void invalidDimensionsAndBudgetAreRejected() {
        assertThrows(IllegalArgumentException.class,()->BlackHoleGeometryPlan.key(p,0,1,1));
        assertThrows(IllegalArgumentException.class,()->BlackHoleGeometryPlan.key(p,1,1,Float.NaN));
        assertThrows(IllegalArgumentException.class,()->BlackHoleGeometryPlan.rowsPerFrame(0,1,1));
        assertThrows(IllegalArgumentException.class,()->BlackHoleGeometryPlan.rowsPerFrame(1,-1,1));
    }
}
