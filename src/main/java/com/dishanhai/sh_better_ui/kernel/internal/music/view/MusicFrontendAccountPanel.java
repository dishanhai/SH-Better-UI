// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLogin;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendAccount.APPLE;
import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Page;
import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Route;

final class MusicFrontendAccountPanel {
    private final MusicFrontendAccount nativeAccount;
    private final AppleMusicAccount apple;
    private final BooleanSupplier webLinks;
    private final Consumer<Route> navigate;
    private final Map<String, MusicTextControl> accounts = new LinkedHashMap<>();
    private PreviewContext host;
    private Bounds body;
    private MusicTextControl primary, remember;
    private Route route;
    private String lastProvider = "";
    private OnlineMusicLogin.Snapshot qr;
    private MusicFrontendAccount.Primary displayedNativeAction;

    MusicFrontendAccountPanel(MusicFrontendAccount nativeAccount, AppleMusicAccount apple,
                              BooleanSupplier webLinks, Consumer<Route> navigate) {
        this.nativeAccount = nativeAccount;
        this.apple = apple;
        this.webLinks = webLinks;
        this.navigate = navigate;
    }

    void layout(PreviewContext host, Bounds body) {
        this.host = host;
        this.body = body;
        accounts.clear();
        displayedNativeAction = null;
        int offset = 0;
        for (String source : List.of("netease", "qq", APPLE)) {
            if (!source.equals(APPLE) && !nativeAccount.available(source)) continue;
            var item = control(new Bounds(body.x(), body.y() + offset, body.width(), 22), title(source),
                    () -> navigate.accept(Route.forProvider(Page.ACCOUNT, source)));
            item.setVariant(MusicTextControl.Variant.ROW);
            accounts.put(source, item);
            offset += 24;
        }
        primary = control(new Bounds(body.x(), body.y(), body.width(), 24), Component.empty(), this::primary);
        remember = control(new Bounds(body.x(), body.y()+28, body.width(), 24), label("login.remember_off"), () -> {
            if (isNativeDetail()) nativeAccount.toggleRemember();
            refresh();
        });
    }

    private MusicTextControl control(Bounds b, Component title, Runnable onPress) {
        return host.add(new MusicTextControl(b, title, host.preferences(), onPress));
    }

    void route(Route next, boolean focus) {
        if (!next.equals(route)) {
            if (route != null && route.page() == Page.ACCOUNT) lastProvider = route.provider();
            route = next;
            nativeAccount.activate(isNativeDetail() ? route.provider() : "");
        }
        if (primary == null) return;
        refresh();
        if (!focus) return;
        if (route.page() == Page.ACCOUNT && primary.visible && primary.active) host.screen().setFocused(primary);
        if (route.page() == Page.ACCOUNTS) {
            var target = accounts.get(lastProvider);
            if (target != null && target.visible) host.screen().setFocused(target);
        }
    }

    private boolean isAppleDetail() { return route != null && route.page() == Page.ACCOUNT && APPLE.equals(route.provider()); }
    private boolean isNativeDetail() { return route != null && route.page() == Page.ACCOUNT && !isAppleDetail(); }

    private void primary() {
        if (isAppleDetail()) {
            if (apple.running()) apple.close();
            else if (apple.primaryAvailable(webLinks.getAsBoolean())) apple.open();
        } else if (isNativeDetail()) nativeAccount.primary(displayedNativeAction);
        refresh();
    }

    void refresh() {
        if (route == null || primary == null) return;
        accounts.forEach((source, item) -> {
            item.setVisible(route.page() == Page.ACCOUNTS && item.getY() + item.getHeight() <= body.bottom());
            if (item.visible) item.setSecondary(source.equals(APPLE) ? apple("browser_only")
                    : label(nativeAccount.authenticated(source) ? "view.signed_in" : "view.guest"));
        });
        boolean browser = isAppleDetail(), detail = isNativeDetail();
        var display = detail ? nativeAccount.display() : null;
        qr = display == null ? null : display.snapshot();
        var nextAction = display == null ? null : display.primary();
        if (displayedNativeAction != null && !displayedNativeAction.matches(nextAction)) primary.cancelInteraction();
        displayedNativeAction = nextAction;
        int width = browser ? body.width() : nativeActionWidth();
        primary.setWidth(width);
        primary.setVisible((browser || detail) && body.height() >= 24);
        primary.setAvailable(browser ? apple.primaryAvailable(webLinks.getAsBoolean()) : nextAction != null);
        var caption = browser ? apple(apple.running() ? "close" : "open") : label(
                nextAction == null ? "login.refresh" : switch (nextAction.action()) {
                    case START -> "login.refresh";
                    case CANCEL -> "cancel";
                    case SIGN_OUT -> "login.sign_out";
                });
        if (!caption.equals(primary.getMessage())) primary.rebind(caption);
        remember.setWidth(width);
        remember.setVisible(detail && nativeAccount.available(route.provider()) && body.height() >= 52);
        if (remember.visible) {
            boolean on = nativeAccount.remembers();
            var save = label(on ? "login.remember_on" : "login.remember_off");
            if (!save.equals(remember.getMessage())) remember.rebind(save);
            remember.setSelected(on);
            remember.setPreview(label("login.remember_hint"));
        }
    }

    Component heading() { return route != null && route.page() == Page.ACCOUNT ? title(route.provider()) : null; }

    private int nativeActionWidth() {
        if (qr == null || qr.image() == null) return body.width();
        int card = Math.min(body.width() / 2, body.height());
        return body.width() - card - 8;
    }

    void render(UiFrame ui, int muted) {
        if (primary == null || route == null || route.page() != Page.ACCOUNT) return;
        refresh();
        if (isAppleDetail()) {
            var state = apple(webLinks.getAsBoolean() || apple.running() ? apple.stateKey() : "links_disabled");
            var hint = apple(apple.running() ? "close_hint" : "instructions");
            primary.setPreview(state.copy().append("\n").append(hint));
            paragraph(ui, state.copy().append("\n").append(hint), body.width(), 30, muted);
            return;
        }
        boolean available = nativeAccount.available(route.provider());
        var status = label(nativeAccount.status(qr));
        var hint = available ? label("login.scan." + route.provider()) : Component.empty();
        int width = nativeActionWidth();
        int side = body.width() - width - 8;
        if (qr != null && qr.image() != null && !paintQr(ui, qr.image(), side)) status = label("login.enlarge");
        primary.setPreview(status.copy().append("\n").append(hint));
        paragraph(ui, status.copy().append("\n").append(hint), width, 58, muted);
    }

    private void paragraph(UiFrame ui, Component text, int width, int y, int muted) {
        if (body.height() > y) OnlineMusicViewPaint.paragraph(ui, host.font(), text,
                new Bounds(body.x(), body.y()+y, width, body.height()-y), muted);
    }

    private boolean paintQr(UiFrame ui, OnlineMusicLogin.Image image, int side) {
        int scale = Math.min(side/image.width(), body.height()/image.height());
        if (scale < 1) return false;
        int left = body.right()-side + (side-image.width()*scale)/2;
        int top = body.y()+(body.height()-image.height()*scale)/2;
        ui.graphics().fill(left, top, left+image.width()*scale, top+image.height()*scale, 0xffffffff);
        for (int y = 0; y < image.height(); y++) for (int start = 0; start < image.width();) {
            if (!image.dark(start, y)) { start++; continue; }
            int end = start + 1;
            while (end < image.width() && image.dark(end, y)) end++;
            ui.graphics().fill(left+start*scale, top+y*scale, left+end*scale, top+(y+1)*scale, 0xff000000);
            start = end;
        }
        return true;
    }

    void suspend() {
        nativeAccount.reset();
        apple.close();
        route = null;
        qr = null;
        displayedNativeAction = null;
    }

    private static Component title(String provider) {
        return switch (provider) {
            case APPLE -> MusicTypography.text("Apple Music");
            case "netease", "qq" -> label("source." + provider);
            default -> label("view.accounts");
        };
    }
    private static Component label(String key) { return MusicTypography.text(Component.translatable("sh_better_ui.online_music." + key)); }
    private static Component apple(String key) { return label("apple_account." + key); }
}
