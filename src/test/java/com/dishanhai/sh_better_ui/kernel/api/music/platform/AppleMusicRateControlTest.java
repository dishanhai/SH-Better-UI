// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.platform;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AppleMusicRateControlTest {
    @Test void capabilityExpiresAndInvalidRatesNeverQueue() {
        var rate = new AppleMusicRateControl();
        assertFalse(rate.supported(0));
        assertThrows(UnsupportedOperationException.class, () -> rate.request(1.5,0));
        rate.observe(true,1,0,"OBSERVED",100);
        assertTrue(rate.supported(101));
        for (double invalid : new double[]{Double.NaN,Double.POSITIVE_INFINITY,.49,2.01})
            assertThrows(IllegalArgumentException.class, () -> rate.request(invalid,101));
        assertFalse(rate.supported(5_000_000_100L));
        assertNull(rate.command(5_000_000_100L));
    }
    @Test void onlyCurrentMatchingAcknowledgementCompletesRequest() {
        var rate = new AppleMusicRateControl();
        rate.observe(true,1,0,"OBSERVED",1);
        rate.request(1.25,2); long old = rate.command(2).id();
        rate.request(1.5,3); long latest = rate.command(3).id();
        rate.observe(true,1.25,old,"APPLIED",4);
        assertEquals("PENDING",rate.result(5));
        assertEquals(latest,rate.command(4).id());
        rate.observe(true,1.5,latest,"APPLIED",5);
        assertEquals("APPLIED",rate.result(5)); assertEquals(1.5,rate.rate());
        assertNull(rate.command(5));
    }
    @Test void staleRequestCannotReappearWhenBrowserReturns() {
        var rate = new AppleMusicRateControl();
        rate.observe(true, 1, 0, "OBSERVED", 1);
        rate.request(1.5, 2);
        long later = 5_000_000_010L;
        rate.observe(true, 1, 0, "OBSERVED", later);
        assertTrue(rate.supported(later));
        assertNull(rate.command(later));
        assertEquals("UNAVAILABLE", rate.result(later));
    }
    @Test void refusedClampedAndLostCapabilityAreNotSuccess() {
        var rate = new AppleMusicRateControl(); rate.observe(true,1,0,"OBSERVED",1);
        rate.request(2,2); rate.observe(true,1,rate.command(2).id(),"APPLIED",3);
        assertEquals("REJECTED",rate.result(5));
        rate.request(1.5,4); rate.observe(false,1,0,"OBSERVED",5);
        assertEquals("REJECTED",rate.result(5)); assertNull(rate.command(5));
        rate.clear(); assertFalse(rate.supported(6));
        assertThrows(IllegalArgumentException.class, () -> rate.observe(true,1,99,"APPLIED",7));
    }
}
