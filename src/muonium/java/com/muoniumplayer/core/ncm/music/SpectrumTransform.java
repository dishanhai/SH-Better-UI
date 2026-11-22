package com.muoniumplayer.core.ncm.music;

import java.util.Arrays;
import repackage.com.jsyn.util.FourierMath;

/** Worker-only real FFT, 48 logarithmic frequency bands, Hann window and a fixed silence floor. */
final class SpectrumTransform {
    static final int BANDS = 48;
    private final double[] window = new double[SpectrumCapture.SIZE];
    private final double[] real = new double[SpectrumCapture.SIZE];
    private final double[] imaginary = new double[SpectrumCapture.SIZE];
    private final double[] power = new double[SpectrumCapture.SIZE / 2];
    private final int[] starts = new int[BANDS], ends = new int[BANDS];
    private int mappedRate;

    SpectrumTransform() {
        for (int i=0; i<window.length; i++) window[i]=.5-.5*Math.cos(2*Math.PI*i/(window.length-1));
    }
    void analyze(float[] left, float[] right, int sampleRate, float[] result) {
        if (sampleRate < 8000 || left.length < window.length || right.length < window.length)
            throw new IllegalArgumentException("Invalid spectrum frame");
        if (sampleRate != mappedRate) map(sampleRate);
        double leftEnergy = windowChannel(left, real);
        double rightEnergy = windowChannel(right, imaginary);
        if (leftEnergy < 1e-12 && rightEnergy < 1e-12) {
            Arrays.fill(result, 0, BANDS, 0f);
            return;
        }
        if (leftEnergy < 1e-12) Arrays.fill(real, 0.0);
        if (rightEnergy < 1e-12) Arrays.fill(imaginary, 0.0);
        // Pack two real signals into one complex FFT. The conjugate bin pair cancels the
        // cross terms: (|Z[k]|² + |Z[N-k]|²) / 2 = |L[k]|² + |R[k]|².
        FourierMath.fft(window.length, real, imaginary);
        for (int bin = 1; bin < power.length; bin++) {
            int mirror = window.length - bin;
            double positive = real[bin] * real[bin] + imaginary[bin] * imaginary[bin];
            double negative = real[mirror] * real[mirror] + imaginary[mirror] * imaginary[mirror];
            power[bin] = (positive + negative) * .5;
        }
        for (int band=0; band<BANDS; band++) {
            double strongest=0.0;
            for (int bin=starts[band]; bin<ends[band]; bin++) strongest=Math.max(strongest,power[bin]);
            double amplitude=Math.sqrt(strongest * .5) * 2.0; // FourierMath already applies 2/N
            double db=20.0*Math.log10(Math.max(1e-9,amplitude));
            result[band]=(float)Math.max(0.0,Math.min(1.0,(db+72.0)/66.0));
        }
    }
    private double windowChannel(float[] channel, double[] destination) {
        double mean=0.0;
        for (int i=0; i<window.length; i++) mean+=Float.isFinite(channel[i]) ? channel[i] : 0f;
        mean/=window.length;
        double energy=0.0;
        for (int i=0; i<window.length; i++) {
            double sample=(Float.isFinite(channel[i]) ? channel[i] : 0f)-mean;
            energy+=sample*sample;
            destination[i] = sample * window[i];
        }
        return energy;
    }
    private void map(int rate) {
        mappedRate=rate;
        double top=Math.min(16_000.0,rate*.48);
        for (int i=0; i<BANDS; i++) {
            double low=40.0*Math.pow(top/40.0,i/(double)BANDS);
            double high=40.0*Math.pow(top/40.0,(i+1)/(double)BANDS);
            starts[i]=Math.min(power.length-1,Math.max(1,(int)Math.floor(low*window.length/rate)));
            ends[i]=Math.min(power.length,Math.max(starts[i]+1,(int)Math.ceil(high*window.length/rate)));
        }
    }
    static double centerHz(int band, int rate) {
        double top=Math.min(16_000.0,rate*.48);
        return 40.0*Math.pow(top/40.0,(band+.5)/BANDS);
    }
}
