// Author: dishanhai. Original glyph-advance mask geometry; no web implementation code copied.
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.ArrayList;
import java.util.List;

/** Immutable measured word fragments. A wrapped word consumes one continuous width budget. */
final class LyricSweepLayout {
    record Glyph(int word,float advance) {}
    record Span(int word,float x,float width,float wordOffset) {}
    private final List<List<Span>> rows;
    private final float[] widths;
    private LyricSweepLayout(List<List<Span>> rows,float[] widths){this.rows=List.copyOf(rows);this.widths=widths;}
    static LyricSweepLayout measure(List<List<Glyph>> glyphRows,int wordCount){
        float[] widths=new float[wordCount];var rows=new ArrayList<List<Span>>();
        for(var glyphs:glyphRows){
            var spans=new ArrayList<Span>();float x=0;
            for(var glyph:glyphs){
                float advance=Float.isFinite(glyph.advance())?Math.max(0,glyph.advance()):0;
                int word=glyph.word();
                if(word>=0&&word<wordCount){
                    int last=spans.size()-1;
                    if(last>=0&&spans.get(last).word()==word){
                        var previous=spans.get(last);
                        spans.set(last,new Span(word,previous.x(),previous.width()+advance,previous.wordOffset()));
                    }else spans.add(new Span(word,x,advance,widths[word]));
                    widths[word]+=advance;
                }
                x+=advance;
            }
            rows.add(List.copyOf(spans));
        }
        return new LyricSweepLayout(rows,widths);
    }
    List<Span> row(int index){return rows.get(index);}
    float revealed(Span span,float fraction){
        float safe=Float.isFinite(fraction)?Math.max(0,Math.min(1,fraction)):0;
        return Math.max(0,Math.min(span.width(),widths[span.word()]*safe-span.wordOffset()));
    }
    static boolean leftToRight(String text){
        char[] chars=text.toCharArray();return !java.text.Bidi.requiresBidi(chars,0,chars.length);
    }
}
