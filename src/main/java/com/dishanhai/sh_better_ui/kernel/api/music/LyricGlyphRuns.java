// Author: dishanhai. Original renderer-neutral extraction of Minecraft's wrapped glyph runs.
package com.dishanhai.sh_better_ui.kernel.api.music;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

record LyricGlyphRuns(List<List<LyricGlyphRuns.Run>> rows, LyricSweepLayout geometry) {
    private static final String WORD_TAG="uuk-word:";

    record Glyph(int index, Style style, int codePoint, int word, float x, float advance) {}
    record Run(int word, float x, float wordOffset, List<Glyph> glyphs, FormattedCharSequence text) {}

    static String tag(int word) { return WORD_TAG+word; }

    static int wordIndex(String tag) {
        if(tag==null||!tag.startsWith(WORD_TAG))return -1;
        try{return Integer.parseInt(tag.substring(WORD_TAG.length()));}
        catch(NumberFormatException ignored){return -1;}
    }

    @FunctionalInterface
    interface Measure { float advance(int codePoint, Style style); }

    static LyricGlyphRuns measure(List<FormattedCharSequence> wrapped, int wordCount, Measure measure) {
        var widths=new float[wordCount];
        var glyphRows=new ArrayList<List<LyricSweepLayout.Glyph>>(wrapped.size());
        var rows=new ArrayList<List<Run>>(wrapped.size());
        for(var part:wrapped){
            var metrics=new ArrayList<LyricSweepLayout.Glyph>();
            var glyphs=new ArrayList<Glyph>();
            float[] x={0};
            part.accept((index,style,cp)->{
                float measured=measure.advance(cp,style);
                float advance=Float.isFinite(measured)?Math.max(0,measured):0;
                int word=wordIndex(style.getInsertion());
                if(word<0||word>=wordCount)word=-1;
                metrics.add(new LyricSweepLayout.Glyph(word,advance));
                glyphs.add(new Glyph(index,style,cp,word,x[0],advance));
                x[0]+=advance;
                return true;
            });
            var runs=new ArrayList<Run>();
            for(int from=0;from<glyphs.size();){
                var first=glyphs.get(from);
                int word=first.word(),to=from+1;
                while(to<glyphs.size()&&glyphs.get(to).word()==word)to++;
                var slice=List.copyOf(glyphs.subList(from,to));
                float wordOffset=word>=0?widths[word]:0;
                if(word>=0)for(var glyph:slice)widths[word]+=glyph.advance();
                FormattedCharSequence text=sink->{
                    for(var glyph:slice)if(!sink.accept(glyph.index(),glyph.style(),glyph.codePoint()))return false;
                    return true;
                };
                runs.add(new Run(word,first.x(),wordOffset,slice,text));
                from=to;
            }
            glyphRows.add(metrics);
            rows.add(List.copyOf(runs));
        }
        return new LyricGlyphRuns(List.copyOf(rows),LyricSweepLayout.measure(glyphRows,wordCount));
    }
}
