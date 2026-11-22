// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import com.dishanhai.sh_better_ui.kernel.internal.PlayerMenuMotion;
import com.dishanhai.sh_better_ui.kernel.internal.PlayerMenuStyle;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
import java.util.Objects;

/** Quiet reusable music text action, navigation item or list row. No vanilla button/slider skin. */
public final class MusicTextControl extends AbstractWidget {
    public enum Variant { ACTION, NAVIGATION, ROW, STACKED_NAVIGATION, LINK, UNDERLINED_TAB, ARTWORK_CARD, MENU }
    private int textInset;

    /** Reserves space for artwork drawn by the owner, within the same hit area. */
    public void setTextInset(int inset) { textInset = Math.max(0, inset); }
    private Variant variant=Variant.ACTION;
    private PlayerMenuMotion.Row menuMotion;
    private UiPreferences preferences;
    private final Runnable action,feedback;
    private Component secondary=Component.empty();
    private MusicTransportControl.Glyph leading,trailing;
    private MusicControlRenderer.Icon outline;
    private boolean selected,pointerPressed,iconOnly,secondaryPresent;
    private int pressedKey=-1;
    private Integer mainInk,secondaryInk;

    public MusicTextControl(Bounds b,Component label,UiPreferences preferences,Runnable action){this(b,label,preferences,action,null);}
    /** Optional feedback callback; null retains native activation sound, independent of custom rendering. */
    public MusicTextControl(Bounds b,Component label,UiPreferences preferences,Runnable action,Runnable feedback){
        super(b.x(),b.y(),b.width(),b.height(),Objects.requireNonNull(label));
        if(b.width()<12||b.height()<16)throw new IllegalArgumentException("Text control requires a 12 x 16 hit area");
        this.preferences=Objects.requireNonNull(preferences);this.action=Objects.requireNonNull(action);this.feedback=feedback;
    }
    public void setVariant(Variant value) {
        variant = Objects.requireNonNull(value);
        if (variant == Variant.MENU && menuMotion == null) menuMotion = new PlayerMenuMotion.Row();
    }
    public void setSelected(boolean value){selected=value;}
    public void setSecondary(Component value){secondary=Objects.requireNonNull(value);secondaryPresent=!value.equals(Component.empty());}
    public void setPreview(Component value){setTooltip(Tooltip.create(MusicTypography.text(Objects.requireNonNull(value))));}
    /** Compatibility mapping only: glyphs are rendered by UUK vectors, never the legacy bitmap texture. */
    public void setIcon(GraphicIcon icon){outline=outlineFor(icon);leading=map(icon);iconOnly=outline!=null||leading!=null;}
    public void setLeadingIcon(GraphicIcon icon){outline=outlineFor(icon);leading=map(icon);iconOnly=false;}
    public void setGlyphOnly(MusicTransportControl.Glyph glyph){outline=null;leading=glyph;iconOnly=glyph!=null;}
    public void setLeadingIcon(MusicControlRenderer.Icon icon){outline=icon;leading=null;iconOnly=false;}
    public void setGlyphOnly(MusicControlRenderer.Icon icon){outline=icon;leading=null;iconOnly=icon!=null;}
    /** Unknown legacy assets retain the existing text fallback; no texture is rendered. */
    public static MusicControlRenderer.Icon outlineFor(GraphicIcon icon){
        if(icon==GraphicIcon.SEARCH||icon==GraphicIcon.WEB_SEARCH)return MusicControlRenderer.Icon.SEARCH;
        if(icon==GraphicIcon.BACK)return MusicControlRenderer.Icon.BACK;
        if(icon==GraphicIcon.FAVORITE)return MusicControlRenderer.Icon.FAVORITE;
        if(icon==GraphicIcon.USER)return MusicControlRenderer.Icon.USER;
        if(icon==GraphicIcon.CLOSE)return MusicControlRenderer.Icon.CLOSE;
        if(icon==GraphicIcon.ADD||icon==GraphicIcon.NEW)return MusicControlRenderer.Icon.ADD;
        if(icon==GraphicIcon.REMOVE)return MusicControlRenderer.Icon.REMOVE;
        if(icon==GraphicIcon.QUEUE)return MusicControlRenderer.Icon.PLAYLIST;
        return null;
    }
    private static MusicTransportControl.Glyph map(GraphicIcon icon){
        if(icon==GraphicIcon.SEARCH)return MusicTransportControl.Glyph.SEARCH;
        if(icon==GraphicIcon.CLOSE)return MusicTransportControl.Glyph.CANCEL;
        if(icon==GraphicIcon.FAVORITE)return MusicTransportControl.Glyph.FAVORITE;
        if(icon==GraphicIcon.REMOVE)return MusicTransportControl.Glyph.REMOVE;
        if(icon==GraphicIcon.USER)return MusicTransportControl.Glyph.USER;
        if(icon==GraphicIcon.RUN||icon==GraphicIcon.RESUME)return MusicTransportControl.Glyph.PLAY;
        if(icon==GraphicIcon.PAUSE)return MusicTransportControl.Glyph.PAUSE;
        if(icon==GraphicIcon.PREVIOUS)return MusicTransportControl.Glyph.PREVIOUS;
        if(icon==GraphicIcon.NEXT)return MusicTransportControl.Glyph.NEXT;
        if(icon==GraphicIcon.BACK)return MusicTransportControl.Glyph.BACK;
        if(icon==GraphicIcon.MUSIC)return MusicTransportControl.Glyph.LYRICS;
        if(icon==GraphicIcon.QUEUE)return MusicTransportControl.Glyph.QUEUE;
        return null;
    }
    public void setLeadingGlyph(MusicTransportControl.Glyph value){outline=null;leading=value;iconOnly=false;}
    public void setTrailingGlyph(MusicTransportControl.Glyph value){trailing=value;}
    public void setInk(int foreground,int secondary){mainInk=foreground;secondaryInk=secondary;}
    public void useThemeInk(){mainInk=null;secondaryInk=null;}
    public void setPreferences(UiPreferences value){preferences=Objects.requireNonNull(value);}
    /** Owners call when a row's identity changes while retaining its widget slot. */
    public void rebind(Component label){cancelInteraction();setMessage(Objects.requireNonNull(label));}
    /** Prefer this over AbstractWidget's public field: hiding and showing between frames must not resurrect capture. */
    public void setVisible(boolean value){visible=value;if(!value)cancelInteraction();}
    public void setAvailable(boolean value){active=value;if(!value||!visible)cancelInteraction();}
    public void cancelInteraction(){pointerPressed=false;pressedKey=-1;}
    public boolean isArmed(){return pointerPressed||pressedKey!=-1;}
    @Override public void setFocused(boolean value){super.setFocused(value);if(!value)cancelInteraction();}
    @Override public void setX(int value){if(value!=getX())cancelInteraction();super.setX(value);}
    @Override public void setY(int value){if(value!=getY())cancelInteraction();super.setY(value);}
    @Override public void setWidth(int value){if(value!=getWidth())cancelInteraction();super.setWidth(value);}
    @Override public void setHeight(int value){if(value!=getHeight())cancelInteraction();super.setHeight(value);}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0||!active||!visible||!isMouseOver(x,y))return false;
        pointerPressed=true;pressedKey=-1;setFocused(true);return true;
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        if(button!=0||!pointerPressed)return false;
        pointerPressed=false;if(active&&visible&&isMouseOver(x,y))activate();return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==256&&isArmed()){cancelInteraction();return true;}
        if(pointerPressed||!active||!visible||!isFocused()||(key!=32&&key!=257&&key!=335))return false;
        if(pressedKey==-1)pressedKey=key;return true;
    }
    @Override public boolean keyReleased(int key,int scan,int mods){
        if(key!=pressedKey)return false;pressedKey=-1;
        if(active&&visible&&isFocused())activate();return true;
    }
    public void onPress(){if(active&&visible)action.run();}
    private void activate(){
        if(feedback==null)playDownSound(Minecraft.getInstance().getSoundManager());else feedback.run();onPress();
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        if(!active)cancelInteraction();
        var theme=UiTheme.current();var font=Minecraft.getInstance().font;
        boolean menu = variant == Variant.MENU;
        boolean contrast = preferences.highContrast();
        int ink = menu ? PlayerMenuStyle.text(1, contrast) : mainInk == null ? theme.text() : mainInk;
        int outlineInk=ink;
        int muted=secondaryInk==null?MpPageChrome.muted(theme,preferences):secondaryInk;
        if(variant==Variant.LINK&&!active)ink=muted;
        else if(!active){ink=alpha(ink,.4f);muted=alpha(muted,.4f);}
        if (variant == Variant.UNDERLINED_TAB) {
            var title = MusicTextLayout.ellipsize(font, getMessage(), Math.max(0, width - 6), MusicTypography.font());
            int titleWidth = font.width(title);
            int x = getX() + (width - titleWidth) / 2;
            if (isFocused() || isArmed())
                RoundedCorners.draw(g, getX(), getY(), width, height, 5, isFocused() ? 1 : 0,
                        ink, alpha(ink, isArmed() ? .16f : 0));
            g.drawString(font, title, x, getY() + (height - MusicTypography.LINE_HEIGHT) / 2 - 2,
                    selected || isHovered() || isFocused() ? ink : muted, false);
            if (selected && titleWidth > 0) {
                int marker = Math.min(22, titleWidth);
                int left = getX() + (width - marker) / 2;
                g.fill(left, getY() + height - 3, left + marker, getY() + height - 1,
                        contrast ? ink : theme.accent());
            }
            return;
        }
        float surface=selected?.15f:pointerPressed||pressedKey!=-1?.12f:isHovered()?.07f:0;
        if (menu) {
            int accent = PlayerMenuStyle.themedAccent(theme.accent(), contrast);
            boolean hover = active && isHovered();
            float fill = menuMotion.sample(hover, isArmed(), System.nanoTime() / 1_000_000L,
                    contrast || preferences.reducedMotion());
            RoundedCorners.draw(g, getX(), getY(), width, height, PlayerMenuStyle.ROW_RADIUS,
                    isFocused() ? (contrast ? 1.5f : 1f) : 0f, accent,
                    PlayerMenuStyle.rowColor(fill));
            if (active && selected) ink = accent;
            if (hover || isFocused() || active && selected) outlineInk = accent;
        } else if(surface>0||isFocused()||contrast&&selected)
            RoundedCorners.draw(g,getX(),getY(),width,height,variant==Variant.ACTION?5:7,
                    isFocused()||contrast&&selected?1:0,ink,alpha(ink,surface));
        if (variant == Variant.ARTWORK_CARD) {
            int captionWidth = Math.max(0, width - 4);
            int top = getY() + width + 4;
            var title = MusicTextLayout.ellipsize(font, getMessage(), captionWidth, MusicTypography.font());
            g.drawString(font, title, getX() + 2, top, ink, false);
            if (secondaryPresent) {
                var details = MusicTextLayout.ellipsize(font, secondary, captionWidth, MusicTypography.font());
                g.drawString(font, details, getX() + 2, top + MusicTypography.LINE_HEIGHT + 2, muted, false);
            }
            return;
        }
        if(iconOnly&&outline!=null){
            MusicControlRenderer.icon(g,new Bounds(getX(),getY(),width,height),outline,isArmed(),active,outlineInk,preferences);
            return;
        }
        if(iconOnly&&leading!=null){
            MusicControlRenderer.transport(g,new Bounds(getX(),getY(),width,height),leading,false,false,false,true,ink,preferences);
            return;
        }
        if(variant==Variant.STACKED_NAVIGATION && outline!=null && height>=24){
            var primary=MusicTextLayout.ellipsize(font,getMessage(),Math.max(0,width-8),MusicTypography.font());
            MusicControlRenderer.icon(g,new Bounds(getX(),getY()+1,width,height-MusicTypography.LINE_HEIGHT-2),
                    outline,isArmed(),active,outlineInk,preferences);
            g.drawString(font,primary,getX()+(width-font.width(primary))/2,
                    getY()+height-MusicTypography.LINE_HEIGHT-1,ink,false);
            return;
        }
        int padding = menu ? PlayerMenuStyle.ROW_INSET : variant == Variant.LINK ? 4 : variant == Variant.ACTION ? 6 : 10;
        int left=getX()+padding+textInset,right=getX()+width-padding;
        if(outline!=null&&right-left>=48){
            MusicControlRenderer.icon(g,new Bounds(left,getY()+(height-22)/2,22,22),outline,isArmed(),active,outlineInk,preferences);left+=26;
        }else if(leading!=null&&right-left>=48){glyph(g,leading,left,ink);left+=26;}
        if(trailing!=null&&right-left>=48){glyph(g,trailing,right-22,ink);right-=26;}
        boolean twoLines=secondaryVisible();
        int line=MusicTypography.LINE_HEIGHT;
        int top=getY()+(height-(twoLines?line*2+2:line))/2;
        var caption=variant==Variant.LINK&&active&&(isHovered()||isFocused())
                ?getMessage().copy().withStyle(net.minecraft.ChatFormatting.UNDERLINE):getMessage();
        var primary=MusicTextLayout.ellipsize(font,caption,Math.max(0,right-left),MusicTypography.font());
        int x=variant==Variant.ACTION?left+Math.max(0,(right-left-font.width(primary))/2):left;
        g.drawString(font,primary,x,top,ink,false);
        if(twoLines)g.drawString(font,MusicTextLayout.ellipsize(font,secondary,Math.max(0,right-left),MusicTypography.font()),left,top+line+2,muted,false);
    }
    public boolean secondaryVisible(){return variant==Variant.ROW&&height>=28&&secondaryPresent&&!iconOnly;}
    private void glyph(GuiGraphics g,MusicTransportControl.Glyph icon,int x,int ink){
        MusicControlRenderer.transport(g,new Bounds(x,getY()+(height-22)/2,22,22),icon,false,false,false,true,ink,preferences);
    }
    private static int alpha(int argb,float factor){return (argb&0xffffff)|(Math.round((argb>>>24)*factor)<<24);}
    @Override protected void updateWidgetNarration(NarrationElementOutput out){
        out.add(NarratedElementType.TITLE,getMessage().copy().append(!secondaryPresent?Component.empty():Component.literal(". ").append(secondary)));
        if(selected)out.add(NarratedElementType.POSITION,Component.translatable("narrator.select",getMessage()));
        if(active)out.add(NarratedElementType.USAGE,Component.translatable(isFocused()?"narration.button.usage.focused":"narration.button.usage.hovered"));
    }
}
