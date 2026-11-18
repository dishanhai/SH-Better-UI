// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import com.dishanhai.sh_better_ui.theme.DisplayStyleSuites;
import com.dishanhai.sh_better_ui.theme.ThemeResolver;
import com.dishanhai.sh_better_ui.theme.ThemeSnapshot;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.dishanhai.sh_better_ui.theme.token.TokenRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.dishanhai.sh_better_ui.theme.token.ThemeTokens.*;

/** Stateless suite fallback for unbound native buttons. No managers, scopes or render resources. */
final class DisplaySuiteButtonPaint {
    private static final Map<String,ThemeSnapshot> SUITES = snapshots();
    private DisplaySuiteButtonPaint() {}
    private static Map<String,ThemeSnapshot> snapshots() {
        var tokens=new TokenRegistry();ThemeTokens.all().forEach(tokens::register);
        var registry=BuiltinThemes.registry();var resolver=new ThemeResolver();
        var result=new LinkedHashMap<String,ThemeSnapshot>();
        for(String id:DisplayStyleSuites.IDS)result.put(id,resolver.resolve(registry,tokens.snapshot(),List.of(id),null));
        return Map.copyOf(result);
    }

    /** Null leaves legacy/custom materials unchanged; local preview scopes take precedence. */
    static ThemeButtonStyle.Paint sample(ThemeSettings settings,UiTheme palette,UiStyle style,
                                         boolean preview,boolean primary,boolean selected,boolean enabled,
                                         boolean highContrast,float hover,float press) {
        if(preview || style!=UiStyle.DEFAULT || settings.mode()!=ThemeSettings.Mode.CUSTOM)return null;
        var tokens=SUITES.get(settings.registeredThemeId());if(tokens==null)return null;
        int background=palette.background()|0xff000000,surface=palette.surface()|0xff000000;
        int accent=palette.accent()|0xff000000,text=palette.text()|0xff000000;
        int border=palette.border()|0xff000000,focus=tokens.resolve(BORDER_FOCUSED).argb();
        if(!settings.accent().isEmpty())focus=accent;
        if(highContrast){
            background=surface=palette.light()?0xffffffff:0xff000000;
            text=border=palette.light()?0xff000000:0xffffffff;
            accent=focus=palette.light()?0xff123974:0xffb6daff;
        }
        int fill=primary?accent:surface;
        if(selected)fill=ContrastPolicy.composite(overlay(tokens.resolve(SELECTED).argb(),accent,1),fill);
        if(enabled){
            fill=ContrastPolicy.composite(overlay(tokens.resolve(HOVER).argb(),accent,hover),fill);
            fill=ContrastPolicy.composite(overlay(tokens.resolve(PRESSED).argb(),accent,press),fill);
        }else fill=ContrastPolicy.composite(overlay(tokens.resolve(DISABLED).argb(),background,1),fill);
        if(primary)text=tokens.resolve(ON_PRIMARY).argb();
        if(!enabled && !highContrast && settings.text().isEmpty())text=tokens.resolve(TEXT_DISABLED).argb();
        text=ContrastPolicy.readable(text,fill,background,highContrast?7:4.5);
        focus=ContrastPolicy.readable(focus,fill,background,3);
        return new ThemeButtonStyle.Paint(fill,text,selected?focus:border,focus,accent,background,
                tokens.resolve(RADIUS_SMALL),highContrast?2:Math.round(tokens.resolve(BORDER_WIDTH)),false,false);
    }
    private static int overlay(int token,int color,float amount){
        int alpha=Math.round((token>>>24)*Math.max(0f,Math.min(1f,amount)));
        return alpha<<24 | color&0xffffff;
    }
}
