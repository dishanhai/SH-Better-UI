// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.api.music.control.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Page;

/** Opt-in vertical slice of the rebuilt workspace. The stock view remains the release default. */
public final class MusicFrontendWorkspace implements OnlineMusicView {
    public static final String PREVIEW_FLAG = "shbui.music.previewRedesign";
    private static final int INK = 0xfff5f5f7, MUTED = 0xffb4b4ba, ACCENT = 0xffff4866;
    private final OnlineMusicPlayer player;
    private final MusicFrontendTransport transport;
    private final MusicFrontendRoutes routes = new MusicFrontendRoutes();
    private final AppleMusicAccount apple = new AppleMusicAccount();
    private final List<MusicTextControl> nav = new ArrayList<>(), rows = new ArrayList<>();
    private PreviewContext host;
    private MusicFrontendLayout layout;
    private MusicFrontendMiniPlayer mini;
    private UiTextField search;
    private MusicTextControl back, utility, submit;
    private MusicLyricsView lyric;
    private CompletableFuture<List<OnlineTrack>> pending;
    private MusicFrontendRoutes.Request request;
    private List<OnlineTrack> matches = List.of();
    private String status = "";
    private MusicFrontendTransport.LyricContent lyricContent;
    private boolean closed;

    public MusicFrontendWorkspace(OnlineMusicPlayer player) {
        this.player = Objects.requireNonNull(player);
        transport = new MusicFrontendTransport(player);
    }
    @Override public OnlineMusicPlayer player() { return player; }

    @Override public void layout(PreviewContext ctx) {
        if (closed) return;
        host = ctx;
        if (mini != null) { mini.close(); mini = null; }
        layout = MusicFrontendLayout.compute(ctx.viewport(), MusicFrontendLayout.Route.valueOf(routes.current().page().name()), false);
        nav.clear(); rows.clear();
        if (!layout.supported()) return;
        back = control(layout.back(), Component.translatable("gui.back"), () -> { if (routes.back()) changed(); else ctx.screen().onClose(); });
        back.setGlyphOnly(MusicTransportControl.Glyph.BACK);
        utility = control(layout.utility(), label("accounts"), () -> navigate(Page.SETTINGS));
        utility.setGlyphOnly(MusicTransportControl.Glyph.USER);
        for (var place : MusicFrontendLayout.DESTINATIONS) {
            var page = Page.valueOf(place.name());
            var tab = control(layout.tab(place), heading(page), () -> {
                if (routes.select(page)) changed();
            });
            tab.setVariant(MusicTextControl.Variant.NAVIGATION);
            nav.add(tab);
        }

        var area = layout.body();
        int inset = 4, queryWidth = area.width() - 62;
        search = ctx.add(new UiTextField(ctx.font(), new Bounds(area.x() + inset, area.y(), queryWidth, 22),
                heading(Page.SEARCH), 100, ctx.preferences()));
        MusicInputAppearance.apply(search);
        search.setHint(label("search_hint"));
        search.setValue(routes.searchDraft());
        search.setOnChange(routes::searchDraft);
        search.setOnSubmit(ignored -> startSearch());
        submit = control(new Bounds(area.right() - 54, area.y(), 50, 22), heading(Page.SEARCH), this::startSearch);
        submit.setGlyphOnly(MusicTransportControl.Glyph.SEARCH);
        for (int i=0; i<Math.min(12, Math.max(0, (area.height()-29)/24)); i++) {
            final int index = i;
            var row = control(new Bounds(area.x()+inset, area.y()+28+i*24, area.width()-2*inset, 22),
                    Component.empty(), () -> selectRow(index));
            row.setVariant(MusicTextControl.Variant.ROW);
            rows.add(row);
        }
        lyric = ctx.add(new MusicLyricsView(area, heading(Page.LYRICS), ctx.preferences(), this::seekLyric));
        lyricContent = null;
        var controls = layout.transportControls();
        mini = new MusicFrontendMiniPlayer(ctx, new MusicFrontendMiniPlayer.Placement(layout.transport(), controls.track(),
                controls.previous(),controls.playPause(),controls.next(),controls.progress()),transport,
                () -> navigate(Page.NOW_PLAYING));
        refreshRows();
    }

    private MusicTextControl control(Bounds b, Component label, Runnable action) {
        return host.add(new MusicTextControl(b, label, host.preferences(), action));
    }
    private static Component label(String key) { return Component.translatable("sh_better_ui.online_music.redesign." + key); }
    private static Component appleLabel(String key) { return Component.translatable("sh_better_ui.online_music.apple_account." + key); }
    private static Component heading(Page page) { return label("page." + page.name().toLowerCase(java.util.Locale.ROOT)); }
    private void navigate(Page page) { if (routes.open(MusicFrontendRoutes.Route.of(page))) changed(); }
    private void changed() {
        if(pending!=null) pending.cancel(true);
        pending=null; request=null; status=""; lyricContent=null;
        if (mini!=null) mini.cancelInteraction();
        refreshRows();
    }
    private void startSearch() {
        if (routes.current().page()!=Page.SEARCH || routes.searchDraft().isBlank()) return;
        if (player.sources().isEmpty()) { status="empty"; return; }
        if (pending!=null) pending.cancel(true);
        request=routes.request();
        matches=List.of(); status="loading";
        // This first preview uses one existing source. Provider switching and paging are the next slice.
        try { pending=player.search(player.sources().get(0), routes.searchDraft().strip(),1); }
        catch (RuntimeException unavailable) { pending=null; status="failed"; }
        refreshRows();
    }

    private void selectRow(int index) {
        var page=routes.current().page();
        switch (page) {
            case DISCOVER -> { if (index==0 && routes.select(Page.SEARCH)) changed(); }
            case LIBRARY -> { if (index==0) navigate(Page.ACCOUNTS); }
            case SETTINGS -> { if (index==0) navigate(Page.ACCOUNTS); }
            case ACCOUNTS -> {
                if (index==0) {
                    if (apple.running()) apple.close();
                    else if (Minecraft.getInstance().options.chatLinks().get()) apple.open();
                    else status="browser_disabled";
                    refreshRows();
                }
            }
            case NOW_PLAYING -> { if(index==0) navigate(Page.LYRICS); else if(index==1) navigate(Page.QUEUE); }
            case QUEUE -> {
                var queue=player.queue();
                if(queue!=null && index<queue.snapshot().tracks().size()) {
                    try { queue.select(index); } catch (OnlineMusicQueue.TransitionInProgress busy) { status="busy"; }
                }
            }
            case SEARCH -> {
                if(index>=matches.size()) return;
                try {
                    var queue=player.queue();
                    if(queue==null) player.play(matches.get(index)); else queue.replace(matches,index);
                    status="";
                } catch (OnlineMusicQueue.TransitionInProgress busy) { status="busy"; }
            }
            default -> { }
        }
    }
    private void seekLyric(long songPosition) {
        var content = lyricContent;
        if (!closed && routes.current().page() == Page.LYRICS && content != null && lyric.contentKey() == content
                && Minecraft.getInstance().isWindowActive()) transport.seekLyric(content, songPosition);
    }

    private void refreshRows() {
        if (layout==null || !layout.supported() || search==null) return;
        var page=routes.current().page();
        search.visible=page==Page.SEARCH; submit.setVisible(page==Page.SEARCH);
        lyric.visible=page==Page.LYRICS;
        for(int i=0;i<nav.size();i++) nav.get(i).setSelected(MusicFrontendLayout.DESTINATIONS.get(i).name().equals(routes.rootPage().name()));
        var queue=page==Page.QUEUE?player.queue():null;
        List<OnlineTrack> shown=page==Page.SEARCH?matches:queue==null?List.of():queue.snapshot().tracks();
        for(int i=0;i<rows.size();i++) {
            var item=rows.get(i);
            var body=layout.body();
            item.setY(page==Page.NOW_PLAYING&&i<2?body.bottom()-50+i*24:body.y()+28+i*24);
            Component primary=null, secondary=Component.empty();
            if (i<shown.size()) {
                primary=MusicTypography.text(shown.get(i).title()); secondary=MusicTypography.text(shown.get(i).artist());
            } else if(i==0) {
                primary=switch(page) {
                    case DISCOVER -> heading(Page.SEARCH);
                    case LIBRARY, SETTINGS -> heading(Page.ACCOUNTS);
                    case NOW_PLAYING -> heading(Page.LYRICS);
                    case ACCOUNTS -> appleLabel(apple.running()?"close":"open");
                    default -> null;
                };
                if(page==Page.ACCOUNTS) secondary=appleLabel(apple.stateKey());
            } else if (i==1 && page==Page.NOW_PLAYING) primary=heading(Page.QUEUE);
            if(primary!=null&&!primary.equals(item.getMessage())) item.rebind(primary);
            item.setSecondary(secondary); item.setVisible(primary!=null);
        }
    }

    @Override public void render(UiFrame ui) {
        if(closed||layout==null)return;
        var frame=layout.frame();
        ui.graphics().fill(frame.x(),frame.y(),frame.right(),frame.bottom(),0xff171719);
        if(!layout.supported()) { OnlineMusicViewPaint.line(ui,host.font(),label("small"),frame.x()+4,frame.y()+4,frame.width()-8,INK); return; }
        if(pending!=null&&pending.isDone()) {
            var done=pending; pending=null;
            if(routes.complete(request)) {
                try { matches=List.copyOf(done.join()); status=matches.isEmpty()?"empty":""; }
                catch(RuntimeException unavailable) { status="failed"; }
            }
            request=null;
        }
        refreshRows();
        var page=routes.current().page();
        var header=layout.header();
        var panel=layout.navigation();
        if(layout.mode()==MusicFrontendLayout.Mode.DESKTOP) {
            RoundedCorners.draw(ui.graphics(),panel.x(),panel.y(),panel.width(),panel.height(),9,0,0,0xff212124);
        }
        for(var tab:nav) tab.setInk(ui.preferences().highContrast()?INK:ACCENT,MUTED);
        OnlineMusicViewPaint.scaledLine(ui,host.font(),heading(page),header.x()+4,header.y()+3,header.width()-8,INK,1.5f);
        var body=layout.body();
        if(!status.isEmpty()) OnlineMusicViewPaint.line(ui,host.font(),label(status),body.x()+4,
                page==Page.SEARCH?body.y()+25:body.y()+4,body.width()-8,MUTED);
        if(page==Page.NOW_PLAYING) {
            var song=player.snapshot().track();
            int size=Math.min(176,Math.min(body.width()/3,Math.max(0,body.height()-54)));
            if(size>=12) {
                var art=new Bounds(body.x()+4,body.y()+2,size,size);
                mini.paintCover(ui,art,song);
                int text=art.right()+10, width=body.right()-text-4;
                OnlineMusicViewPaint.scaledLine(ui,host.font(),song==null?label("nothing_playing"):MusicTypography.text(song.title()),
                        text,body.y()+4,width,INK,1.3f);
                if(song!=null) OnlineMusicViewPaint.line(ui,host.font(),MusicTypography.text(song.artist()),
                        text,body.y()+22,width,MUTED);
            }
        }
        if(page==Page.ACCOUNTS) {
            OnlineMusicViewPaint.paragraph(ui,host.font(),appleLabel("instructions"),
                    new Bounds(body.x()+4,body.y()+54,body.width()-8,Math.max(0,body.height()-54)),MUTED);
        }
        if(page==Page.LYRICS) {
            var lyrics = transport.lyrics(Minecraft.getInstance().isWindowActive());
            if (!Objects.equals(lyricContent, lyrics.content())) {
                lyricContent = lyrics.content();
                lyric.setLines(lyricContent, OnlineLyricPresentation.lines(lyricContent.timeline()));
            }
            lyric.playback(lyrics.songPositionMs(), lyrics.seekable(), lyrics.preview());
        }
        mini.render(ui);
    }
    boolean permitsScreenClose() { if (!routes.back()) return true; changed(); return false; }
    @Override public void suspend() { if(pending!=null)pending.cancel(true);pending=null;routes.invalidateRequests();apple.close();if(mini!=null)mini.cancelInteraction(); }
    @Override public void close() { if(!closed){suspend();closed=true;if(mini!=null)mini.close(); player.close();} }
}
