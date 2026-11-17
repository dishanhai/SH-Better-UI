// Author: dishanhai. Original palette extraction from caller-owned, already decoded artwork.
package com.dishanhai.sh_better_ui.kernel.api.music.presentation;

import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import java.util.Objects;

/** Four low-frequency artwork colors. No image loading, URLs, audio or hidden global state. */
public record ArtworkPalette(int topLeft, int topRight, int bottomLeft, int bottomRight) {
    public static final int MAX_SAMPLES = 4096;
    public ArtworkPalette {
        topLeft |= 0xff000000; topRight |= 0xff000000;
        bottomLeft |= 0xff000000; bottomRight |= 0xff000000;
    }
    public static ArtworkPalette solid(int argb) { return new ArtworkPalette(argb, argb, argb, argb); }
    public static ArtworkPalette fallback(int themeColor) {
        int top = tone(themeColor, .14), bottom = tone(themeColor, .065);
        return new ArtworkPalette(top, top, bottom, bottom);
    }
    /** Input is a caller-sampled ARGB grid, at most 4096 pixels, not an unbounded source image. */
    public static ArtworkPalette fromArgb(int width, int height, int[] pixels) {
        Objects.requireNonNull(pixels);
        if (width <= 0 || height <= 0 || (long)width * height != pixels.length || pixels.length > MAX_SAMPLES)
            throw new IllegalArgumentException("Expected a bounded artwork sample grid");
        double[][] sums = new double[5][4];
        for (int y=0; y<height; y++) for (int x=0; x<width; x++) {
            int argb=pixels[y*width+x]; double weight=(argb>>>24)/255d;
            if (weight == 0) continue;
            int cell=(y*2>=height?2:0)+(x*2>=width?1:0);
            for(int bucket:new int[]{cell,4}) {
                sums[bucket][0]+=((argb>>>16)&255)*weight;
                sums[bucket][1]+=((argb>>>8)&255)*weight;
                sums[bucket][2]+=(argb&255)*weight; sums[bucket][3]+=weight;
            }
        }
        if(sums[4][3]==0) return fallback(0xff29282b);
        int global=average(sums[4],0xff29282b);
        int[] colors=new int[4];
        for(int i=0;i<4;i++) {
            int local=average(sums[i],global);
            // Broad color fields, not four rectangular image quadrants. Preserve neutral artwork.
            colors[i]=tone(mix(global,local,.45),i<2?.14:.065);
        }
        return new ArtworkPalette(colors[0],colors[1],colors[2],colors[3]);
    }
    private static int average(double[] sums,int fallback) {
        return sums[3]==0?fallback:0xff000000 | ((int)Math.round(sums[0]/sums[3])<<16)
                | ((int)Math.round(sums[1]/sums[3])<<8) | (int)Math.round(sums[2]/sums[3]);
    }
    private static int tone(int rgb,double maximumLuminance) {
        double r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;
        double max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
        if(max>0 && (max-min)/max>.38) {
            double factor=.38*max/(max-min);
            r=max-(max-r)*factor;g=max-(max-g)*factor;b=max-(max-b)*factor;
        }
        double luminance=.2126*ColorValue.linear((int)r)+.7152*ColorValue.linear((int)g)+.0722*ColorValue.linear((int)b);
        // Scaling sRGB conservatively also lowers linear luminance below the cap.
        double scale=luminance>maximumLuminance?Math.sqrt(maximumLuminance/luminance):1;
        int result=0xff000000|((int)Math.round(r*scale)<<16)|((int)Math.round(g*scale)<<8)|(int)Math.round(b*scale);
        while(luminance(result)>maximumLuminance) {
            result=0xff000000|((Math.max(0,((result>>>16)&255)-1))<<16)
                    |((Math.max(0,((result>>>8)&255)-1))<<8)|Math.max(0,(result&255)-1);
        }
        return result;
    }
    public static double luminance(int rgb) {
        return .2126*ColorValue.linear((rgb>>>16)&255)+.7152*ColorValue.linear((rgb>>>8)&255)+.0722*ColorValue.linear(rgb&255);
    }
    public ArtworkPalette interpolate(ArtworkPalette other,double progress) {
        return new ArtworkPalette(mix(topLeft,other.topLeft,progress),mix(topRight,other.topRight,progress),
                mix(bottomLeft,other.bottomLeft,progress),mix(bottomRight,other.bottomRight,progress));
    }
    public int color(double x,double y) {
        return mix(mix(topLeft,topRight,x),mix(bottomLeft,bottomRight,x),y);
    }
    private static int mix(int from,int to,double progress) {
        return ColorValue.interpolateOklab(ColorValue.argb(from),ColorValue.argb(to),Math.max(0,Math.min(1,progress))).argb();
    }
}
