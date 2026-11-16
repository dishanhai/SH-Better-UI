package com.dishanhai.sh_better_ui.client.gui.scaled;

/**
 * Test-only double precision oracle for the shader's Schwarzschild null rays.
 * G=c=M=1; u=1/r, v=du/dphi. Derived from David Tong, General Relativity,
 * section 1.3.5, equations 1.53-1.55:
 * https://www.damtp.cam.ac.uk/user/tong/gr/grhtml/S1.html#S1.SS3.SSS5
 * This integrates test photons in a fixed, non-rotating metric, not plasma dynamics.
 */
public final class BlackHoleGeodesicReference {
    public static final double HORIZON = 2, PHOTON_SPHERE = 3, ISCO = 6;
    public static final double CRITICAL_IMPACT = 3 * Math.sqrt(3);
    public record State(double phi, double u, double v) {
        public double energy() { return v*v + u*u - 2*u*u*u; }
    }
    public enum Fate { CAPTURED, ESCAPED, UNRESOLVED }
    public record Trace(Fate fate, State end, double maxEnergyError, int steps) {}
    private BlackHoleGeodesicReference() {}

    /** Angle measured from the inward radial direction in a static observer's tetrad. */
    public static double impactFromLocalAngle(double radius, double angle) {
        if (!(radius > HORIZON) || !Double.isFinite(radius)
                || !(angle > 0 && angle <= Math.PI/2)) throw new IllegalArgumentException();
        return radius * Math.sin(angle) / Math.sqrt(1 - 2/radius);
    }
    public static State incoming(double radius, double impact) {
        if (!(radius > HORIZON) || !Double.isFinite(radius)
                || !(impact > 0) || !Double.isFinite(impact)) throw new IllegalArgumentException();
        double u = 1/radius, v2 = 1/(impact*impact) - u*u + 2*u*u*u;
        if (v2 < -1e-14) throw new IllegalArgumentException("Ray not accessible at camera radius");
        return new State(0, u, Math.sqrt(Math.max(0, v2)));
    }
    public static State rk4(State s, double h) {
        double a = s.v, b = acceleration(s.u);
        double c = s.v + h*b/2, d = acceleration(s.u + h*a/2);
        double e = s.v + h*d/2, f = acceleration(s.u + h*c/2);
        double g = s.v + h*f, j = acceleration(s.u + h*e);
        return new State(s.phi+h, s.u+h*(a+2*c+2*e+g)/6, s.v+h*(b+2*d+2*f+j)/6);
    }
    private static double acceleration(double u) { return -u+3*u*u; }

    /** Integrate to horizon or future infinity; interpolate only the final u=0 crossing. */
    public static Trace trace(double radius, double impact, double h, int budget) {
        if (!(h > 0 && h <= .1) || budget <= 0) throw new IllegalArgumentException();
        State s = incoming(radius, impact);
        double invariant = 1/(impact*impact), error = 0;
        for (int n = 1; n <= budget; n++) {
            State next = rk4(s, h);
            error = Math.max(error, Math.abs(next.energy()-invariant));
            if (next.u >= .5) return new Trace(Fate.CAPTURED, next, error, n);
            if (next.u <= 0) {
                double t = s.u/(s.u-next.u);
                State end = new State(s.phi+h*t, 0, s.v+t*(next.v-s.v));
                return new Trace(Fate.ESCAPED, end, error, n);
            }
            s = next;
        }
        return new Trace(Fate.UNRESOLVED, s, error, budget);
    }
    public static State atAngle(State initial, double phi, double step) {
        if (!(phi >= initial.phi) || !(step > 0) || !Double.isFinite(phi) || !Double.isFinite(step))
            throw new IllegalArgumentException();
        State s = initial;
        while (s.phi < phi) s = rk4(s, Math.min(step, phi-s.phi));
        return s;
    }
    /** Position on ray plane: x=(e_r*cos(phi)+e_t*sin(phi))/u. */
    public static double planeHeight(State s, double radialY, double tangentY) {
        return (radialY*Math.cos(s.phi)+tangentY*Math.sin(s.phi))/s.u;
    }
    /** CSV oracle: radius impact step count; usable by a GPU validation harness. */
    public static void main(String[] args) {
        double radius = args.length > 0 ? Double.parseDouble(args[0]) : 30;
        double impact = args.length > 1 ? Double.parseDouble(args[1]) : 6;
        double step = args.length > 2 ? Double.parseDouble(args[2]) : .02;
        int count = args.length > 3 ? Integer.parseInt(args[3]) : 100;
        State s = incoming(radius, impact);
        System.out.println("step,phi,u,v,energy");
        for (int i=0; i<=count; i++) {
            System.out.printf(java.util.Locale.ROOT,"%d,%.17g,%.17g,%.17g,%.17g%n",i,s.phi,s.u,s.v,s.energy());
            s=rk4(s,step);
        }
    }
}
