// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Optional;

/** Style-preserving, bidi-aware bounded single-line text. Measurement and drawing share one font.
 * Literal/sibling traversal, pending nodes, emitted runs and characters are bounded. Minecraft
 * translation/custom contents may internally resolve argument trees before emitting a run; this
 * utility does not replace that resolver and is not a sandbox for arbitrary custom contents.
 */
public final class MusicTextLayout {
    public static final int MAX_CODE_POINTS=4096,MAX_RUNS=256;
    private MusicTextLayout(){}
    public record Bounded(FormattedText text,boolean truncated){}
    private record Node(Component component,Style inherited){}
    public static Bounded bound(Component source){return bound(source,Style.EMPTY);}
    private static Bounded bound(Component source,Style inherited){
        var pieces=new ArrayList<FormattedText>();var stack=new ArrayDeque<Node>();stack.push(new Node(source,inherited));
        int[] remaining={MAX_CODE_POINTS},runs={0};boolean[] stopped={false};boolean clipped=false;int nodes=0;
        while(!stack.isEmpty()&&!stopped[0]&&nodes<MAX_RUNS){
            var node=stack.pop();nodes++;
            var style=node.component().getStyle().applyTo(node.inherited());
            node.component().getContents().visit((partStyle,part)->{
                if(runs[0]++>=MAX_RUNS){stopped[0]=true;return Optional.of(Boolean.TRUE);}
                int count=part.codePointCount(0,Math.min(part.length(),remaining[0]*2+2));
                if(count>remaining[0]){
                    pieces.add(FormattedText.of(part.substring(0,part.offsetByCodePoints(0,remaining[0])),partStyle));
                    stopped[0]=true;return Optional.of(Boolean.TRUE);
                }
                pieces.add(FormattedText.of(part,partStyle));remaining[0]-=count;return Optional.empty();
            },style);
            var siblings=node.component().getSiblings();
            int take=Math.min(siblings.size(),Math.max(0,MAX_RUNS-nodes-stack.size()));
            if(take<siblings.size())clipped=true;
            for(int i=take-1;i>=0;i--)stack.push(new Node(siblings.get(i),style));
        }
        return new Bounded(FormattedText.composite(pieces),clipped||stopped[0]||!stack.isEmpty());
    }
    public static FormattedCharSequence ellipsize(Font font,Component source,int width){return ellipsize(font,source,width,null);}
    /** Applies a fallback font without deep-copying an unbounded Component before clipping it. Explicit child fonts survive. */
    public static FormattedCharSequence ellipsize(Font font,Component source,int width,ResourceLocation fallbackFont){
        if(width<=0)return FormattedCharSequence.EMPTY;
        var inherited=fallbackFont==null?Style.EMPTY:Style.EMPTY.withFont(fallbackFont);
        var bounded=bound(source,inherited);
        if(!bounded.truncated()&&font.width(bounded.text())<=width)return Language.getInstance().getVisualOrder(bounded.text());
        var ellipsis=Component.literal("…").withStyle(source.getStyle().applyTo(inherited));
        int marker=font.width(ellipsis);if(marker>width)return FormattedCharSequence.EMPTY;
        return Language.getInstance().getVisualOrder(FormattedText.composite(
                font.substrByWidth(bounded.text(),Math.max(0,width-marker)),ellipsis));
    }
}
