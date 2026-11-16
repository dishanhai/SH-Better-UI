package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.dishanhai.sh_better_ui.client.gui.scaled.BlackHoleGeodesicReference.*;

class BlackHoleGeodesicReferenceTest {
    @Test void criticalImpactSeparatesCaptureAndEscapeForFiniteCamera() {
        for (double camera : new double[]{30, 1000}) {
            assertEquals(Fate.CAPTURED, trace(camera, CRITICAL_IMPACT*.999, .002, 20000).fate());
            assertEquals(Fate.ESCAPED, trace(camera, CRITICAL_IMPACT*1.001, .002, 20000).fate());
        }
    }
    @Test void nullEnergyIsConservedOnBothSidesOfPhotonSphereBarrier() {
        for (double b : new double[]{3, 5.19, 5.2, 6, 10, 20}) {
            var result = trace(30,b,.002,20000);
            assertNotEquals(Fate.UNRESOLVED,result.fate());
            assertTrue(result.maxEnergyError()<1e-11, "b="+b+" error="+result.maxEnergyError());
        }
    }
    @Test void photonSphereIsAnUnstableCircularNullOrbitNotTheDiskInnerEdge() {
        var s = new State(0,1/PHOTON_SPHERE,0);
        var end = atAngle(s,2*Math.PI,.005);
        assertEquals(s.u(),end.u(),1e-12);
        assertEquals(1/(CRITICAL_IMPACT*CRITICAL_IMPACT),s.energy(),1e-15);
        assertEquals(2,HORIZON); assertEquals(6,ISCO);
    }
    @Test void rk4ConvergesAtFourthOrderAgainstFineReference() {
        var initial = incoming(30,6);
        var fine = atAngle(initial,2,.0005);
        double coarse = Math.abs(atAngle(initial,2,.08).u()-fine.u());
        double medium = Math.abs(atAngle(initial,2,.04).u()-fine.u());
        double small = Math.abs(atAngle(initial,2,.02).u()-fine.u());
        assertTrue(coarse/medium>12 && coarse/medium<20);
        assertTrue(medium/small>12 && medium/small<20);
    }
    @Test void weakFieldDeflectionApproachesFourMassOverImpact() {
        // Finite camera correction is b/R at leading order; R=1e10 makes it negligible.
        for (double b : new double[]{1000,2000,4000}) {
            var ray = trace(1e10,b,.001,10000);
            assertEquals(Fate.ESCAPED,ray.fate());
            double bend = ray.end().phi()-Math.PI + b/1e10;
            assertEquals(4/b,bend,(4/b)*.004);
        }
    }
    @Test void localCameraTetradAgreesWithNullInvariant() {
        double r=30, alpha=.2, b=impactFromLocalAngle(r,alpha);
        var s=incoming(r,b);
        assertEquals(1/(b*b),s.energy(),1e-15);
        assertEquals(Math.sqrt(1-2/r)*Math.cos(alpha)/(r*Math.sin(alpha)),s.v(),1e-15);
    }
    @Test void rayPlaneDiskCrossingIsNotScreenSpaceEllipse() {
        // Plane basis has y components sin(i), -cos(i). y=0 at phi=i+n*pi.
        double inclination=.4;
        var crossing=atAngle(incoming(30,6),inclination,.0005);
        assertEquals(0,planeHeight(crossing,Math.sin(inclination),-Math.cos(inclination)),1e-12);
        assertTrue(1/crossing.u()>ISCO);
        assertTrue(planeHeight(atAngle(incoming(30,6),inclination-.01,.0005),Math.sin(inclination),-Math.cos(inclination))>0);
        assertTrue(planeHeight(atAngle(incoming(30,6),inclination+.01,.0005),Math.sin(inclination),-Math.cos(inclination))<0);
    }
    @Test void rendererQualityBudgetsResolveOrdinaryRaysButNotAllCriticalRays() {
        double[] steps = {.04, .025, .016};
        int[] budgets = {192, 320, 512};
        double[] impacts = {.1, 3, 5, 5.15, 5.3, 6, 8, 12};
        for (int q=0; q<steps.length; q++) {
            for (double b : impacts) {
                var bounded = trace(30,b,steps[q],budgets[q]);
                var fine = trace(30,b,.0005,50000);
                assertEquals(fine.fate(),bounded.fate(),"quality="+q+" b="+b);
                assertTrue(bounded.maxEnergyError()/(1/(b*b))<1e-6);
                if (bounded.fate()==Fate.ESCAPED)
                    assertEquals(fine.end().phi(),bounded.end().phi(),3e-6);
            }
            // Finite shader budgets intentionally omit arbitrarily high-order photon rings.
            assertEquals(Fate.UNRESOLVED,trace(30,CRITICAL_IMPACT-.001,steps[q],budgets[q]).fate());
            assertEquals(Fate.UNRESOLVED,trace(30,CRITICAL_IMPACT+.001,steps[q],budgets[q]).fate());
        }
    }
    @Test void impossibleFiniteCameraRayAndInvalidBudgetsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->incoming(2,6));
        assertThrows(IllegalArgumentException.class,()->incoming(30,100));
        assertThrows(IllegalArgumentException.class,()->trace(30,6,0,100));
        assertThrows(IllegalArgumentException.class,()->trace(30,6,.01,0));
    }
}
