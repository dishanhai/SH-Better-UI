// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicQueue;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicInputAppearance;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Page;
import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Route;

/** Provider library, canonical link discovery and playlist detail for the redesigned workspace only. */
final class MusicFrontendLibraryPanel {
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_PITCH = 24;
    private final MusicFrontendLibrary model;
    private final MusicFrontendRoutes routes;
    private final Consumer<Route> navigate;
    private final Consumer<String> browse;
    private final List<MusicTextControl> rows = new ArrayList<>();
    private List<Entry> entries = List.of();
    private MusicTextControl retry, browseButton, open;
    private OnlineMusicScrollRegion scroll;
    private UiTextField link;
    private PreviewContext host;
    private Bounds body, list;
    private int capacity;
    private Route shownRoute;
    private MusicFrontendLibrary.View shownView;
    private String draft = "", message = "";
    private int first;

    private record Entry(Component title, Component subtitle, Runnable action) {}

    MusicFrontendLibraryPanel(OnlineMusicPlayer player, MusicFrontendRoutes routes,
                               Consumer<Route> navigate, Consumer<String> browse) {
        model = new MusicFrontendLibrary(player); this.routes = routes;
        this.navigate = navigate; this.browse = browse;
    }

    static boolean controls(Page page) {
        return page == Page.LIBRARY || page == Page.DISCOVERY || page == Page.PLAYLIST;
    }

    static int visibleRows(Bounds body) { return Math.min(12,Math.max(0,(body.height()-42)/ROW_PITCH)); }
    static Bounds rowBounds(Bounds body,int slot) {
        return new Bounds(body.x()+4,body.y()+17+slot*ROW_PITCH,body.width()-8,ROW_HEIGHT);
    }
    static Bounds listBounds(Bounds body) {
        return new Bounds(body.x()+4, body.y()+17, body.width()-8, visibleRows(body)*ROW_PITCH);
    }

    void mount(PreviewContext host, Bounds bounds) {
        this.host = host; body = bounds; rows.clear();
        list = listBounds(body);
        capacity = visibleRows(body);
        scroll = host.add(new OnlineMusicScrollRegion(list.x(), list.y(), list.width(), list.height(),
                stock("view.scroll_library"), offset -> {
                    rows.forEach(MusicTextControl::cancelInteraction);
                    first = offset;
                    routes.remember("", first);
                    refresh();
                }));
        scroll.setOnBrowse(() -> host.screen().setFocused(scroll));
        scroll.onEnd(model::canExtend, () -> { model.extend(); refresh(); });
        int spill = MusicScrollRows.spill(host, capacity);
        if (spill > 0) scroll.pixelRows(ROW_PITCH, shift -> positionRows());
        for(int i=0;i<capacity+spill;i++) {
            final int slot=i;
            var row=MusicScrollRows.add(host, new MusicTextControl(rowBounds(body,i),
                    Component.empty(),host.preferences(),()->choose(slot)), () -> list);
            row.setVariant(MusicTextControl.Variant.ROW);
            rows.add(row);
        }
        retry = add(new Bounds(body.right()-64, body.bottom()-22, 60, 20), stock("library.retry"),
                () -> { model.retry(); refresh(); });
        link=host.add(new UiTextField(host.font(),new Bounds(body.x()+4,body.y()+2,body.width()-60,22),
                stock("discovery.paste"),2048,host.preferences()));
        MusicInputAppearance.apply(link);
        link.setHint(stock("discovery.paste")); link.setValue(draft);
        link.setOnChange(value->{draft=value;message="";});
        link.setOnSubmit(ignored->openLink());
        open=add(new Bounds(body.right()-52,body.y()+2,48,22),stock("discovery.open"),this::openLink);
        browseButton=add(new Bounds(body.x()+4,body.y()+28,body.width()-8,22),
                stock("discovery.official"),this::openBrowser);
        browseButton.setPreview(stock("discovery.hint"));
        routeChanged();
    }

    private MusicTextControl add(Bounds bounds, Component title, Runnable action) {
        return host.add(new MusicTextControl(bounds,title,host.preferences(),action));
    }

    void routeChanged() {
        var route=routes.current();
        if (!Objects.equals(shownRoute,route)) {
            if(shownRoute!=null&&!shownRoute.provider().equals(route.provider())) {
                draft="";
                if(link!=null)link.setValue("");
            }
            if (scroll != null) scroll.cancelInteraction();
            shownRoute=route; first=routes.memory().scroll(); message="";
        }
        model.open(key(route));
        shownView=null;
        refresh();
    }

    private static MusicFrontendLibrary.Key key(Route route) {
        if (route.page()==Page.PLAYLIST) return MusicFrontendLibrary.Key.detail(route.provider(),route.id());
        if (route.page()!=Page.DISCOVERY || route.provider().isBlank()) return null;
        return switch (route.id()) {
            case "playlists" -> MusicFrontendLibrary.Key.collection(route.provider(),MusicFrontendLibrary.Kind.PLAYLISTS);
            case "liked" -> MusicFrontendLibrary.Key.collection(route.provider(),MusicFrontendLibrary.Kind.LIKED);
            default -> null;
        };
    }

    String draft() { return draft; }
    MusicTextControl restoreDraft(String value) {
        draft=value;
        if(link!=null)link.setValue(value);
        refresh();
        return browseButton != null && browseButton.visible ? browseButton : null;
    }

    private static boolean official(Route route) { return route.page()==Page.DISCOVERY&&route.id().equals("official"); }
    private void openLink() {
        var route=routes.current();
        if(!official(route))return;
        var id=NativePlaylistDiscovery.playlistId(route.provider(),draft);
        if(id==null){message="discovery.invalid";return;}
        open(new Route(Page.PLAYLIST,route.provider(),id));
    }
    private void openBrowser() {
        var route=routes.current();
        if(official(route)) {
            var url=NativePlaylistDiscovery.discoveryUrl(route.provider());
            if(url!=null)browse.accept(url);
        }
    }
    void browserFailed() { message="library.browser_unavailable"; }

    private void open(Route route) {
        routes.remember("",first);
        navigate.accept(route);
    }

    private void choose(int slot) {
        if(!Objects.equals(shownRoute,routes.current())||slot<0||first+slot>=entries.size()||!rows.get(slot).visible)return;
        entries.get(first+slot).action().run();
    }

    private List<Entry> makeEntries(Route route, MusicFrontendLibrary.View view) {
        if (route.page()==Page.LIBRARY) return model.providers().stream().map(source->
                new Entry(stock("source."+source),Component.empty(),()->open(Route.forProvider(Page.DISCOVERY,source)))).toList();
        if (route.page()==Page.DISCOVERY && route.id().isEmpty()) return List.of(
                group(route,"playlists",stock("library.playlists")), group(route,"liked",stock("library.liked")),
                group(route,"official",stock("discovery.tab")));
        if (view.key()==null) return List.of();
        if (view.status()==MusicFrontendLibrary.Status.SIGN_IN || view.status()==MusicFrontendLibrary.Status.AUTHORIZATION)
            return List.of(new Entry(stock("library.sign_in"),Component.empty(),
                    ()->open(Route.forProvider(Page.ACCOUNT,view.key().source()))));
        if (view.status()==MusicFrontendLibrary.Status.FAILED && !model.extensionFailed() || view.status()==MusicFrontendLibrary.Status.CHANGED)
            return List.of(new Entry(stock("library.retry"),Component.empty(),model::retry));
        if (!view.playlists().isEmpty()) return view.playlists().stream().map(item->new Entry(MusicTypography.text(item.name()),
                stock("library.track_count",item.trackCount()),()->{
                    if(model.current(view))open(new Route(Page.PLAYLIST,item.source(),item.id()));
                })).toList();
        var result=new ArrayList<Entry>(view.tracks().size());
        for(int i=0;i<view.tracks().size();i++){
            int index=i; var track=view.tracks().get(i);
            result.add(new Entry(MusicTypography.text(track.title()),MusicTypography.text(track.artist()),()->{
                try { if(!model.play(view,index)) message="discovery.play_failed"; }
                catch(OnlineMusicQueue.TransitionInProgress busy){message="library.transition";}
                catch(RuntimeException unavailable){message="discovery.play_failed";}
            }));
        }
        return List.copyOf(result);
    }

    private Entry group(Route route,String kind,Component title) {
        return new Entry(title,Component.empty(),()->open(new Route(Page.DISCOVERY,route.provider(),kind)));
    }

    private void refresh() {
        if(host==null||link==null)return;
        var route=routes.current();
        boolean visible=controls(route.page());
        boolean input=visible&&official(route);
        link.visible=input; open.setVisible(input); browseButton.setVisible(input);
        var view=model.view();
        if(shownView!=view) {
            entries=visible?makeEntries(route,view):List.of();
            shownView=view;
            for(var row:rows)row.cancelInteraction();
        }
        // Keep the saved position while the reopened route is loading.
        if (!entries.isEmpty() && !rows.isEmpty()) first = Math.min(first, Math.max(0, entries.size() - capacity));
        scroll.visible = visible && !input;
        scroll.configure(first, entries.size(), capacity);
        retry.setVisible(scroll.visible && model.extensionFailed());
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);int at=first+i;
            boolean show=visible&&!input&&at<entries.size();
            row.setVisible(show);
            row.setWidth(body.width()-8-(scroll.active ? 8 : 0));
            if(show){
                var item=entries.get(at);
                if(!item.title().equals(row.getMessage()))row.rebind(item.title());
                row.setSecondary(item.subtitle());
            }
        }
        positionRows();
    }

    private void positionRows() {
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            int y = list.y() + i * ROW_PITCH - scroll.rowShift();
            row.setY(y);
            row.setVisible(scroll.visible && first+i<entries.size() && MusicScrollRows.intersects(y, ROW_HEIGHT, list));
        }
    }

    Component heading() {
        var route=routes.current();
        var view=model.view();
        if (view.detail()!=null&&!view.detail().name().isBlank()) return MusicTypography.text(view.detail().name());
        if (route.page()==Page.LIBRARY)return stock("view.library");
        if (route.page()==Page.PLAYLIST)return stock("library.playlists");
        return switch(route.id()){
            case "liked" -> stock("library.liked");
            case "playlists" -> stock("library.playlists");
            case "official" -> stock("discovery.tab");
            default -> stock("source."+route.provider());
        };
    }

    void render(UiFrame ui,int muted) {
        if (!controls(routes.current().page())||body==null)return;
        model.poll();
        if(model.view()!=shownView)refresh();
        scroll.tick();
        var route=routes.current();var view=model.view();
        Component status=Component.empty();
        if(!message.isEmpty())status=message.startsWith("library.")?label(message):stock(message);
        else if(official(route))status=stock("discovery.hint");
        else if(view.key()==null&&entries.isEmpty())status=stock("view.unavailable");
        else if(view.key()!=null)status=switch(view.status()){
            case IDLE->Component.empty();
            case READY->view.key().kind()==MusicFrontendLibrary.Kind.LIKED
                    ?stock("library.track_count",view.tracks().size()):view.key().kind()==MusicFrontendLibrary.Kind.DETAIL
                    ?stock("library.track_count",view.tracks().size()):Component.empty();
            case LOADING->stock(view.key().kind()==MusicFrontendLibrary.Kind.DETAIL?"discovery.loading":"library.loading");
            case EMPTY->stock("library.empty");
            case SIGN_IN,AUTHORIZATION->label("library.sign_in");
            case CHANGED->stock("discovery.session_changed");
            case FAILED->stock(view.key().kind()==MusicFrontendLibrary.Kind.DETAIL?"discovery.failed":"library.failed");
            case UNSUPPORTED->stock("discovery.unsupported");
        };
        if(official(route)||view.status()==MusicFrontendLibrary.Status.FAILED&&view.key()!=null&&view.key().kind()==MusicFrontendLibrary.Kind.DETAIL){
            int top=body.y()+(official(route)?53:0);
            if(official(route))OnlineMusicViewPaint.paragraph(ui,host.font(),status,new Bounds(body.x()+4,top,body.width()-8,Math.max(0,body.bottom()-top)),muted);
            else OnlineMusicViewPaint.line(ui,host.font(),status,body.x()+4,top,body.width()-8,muted);
        }else OnlineMusicViewPaint.line(ui,host.font(),status,body.x()+4,body.y(),body.width()-8,muted);
    }

    void close() { if (scroll != null) scroll.cancelInteraction(); model.clear(); }

    private static Component stock(String key,Object... args){
        return Component.translatable("sh_better_ui.online_music."+key,args);
    }

    private static Component label(String key,Object... args){
        return Component.translatable("sh_better_ui.online_music.redesign."+key,args);
    }
}
