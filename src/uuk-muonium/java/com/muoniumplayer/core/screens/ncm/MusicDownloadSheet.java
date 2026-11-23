// Author: dishanhai
package com.muoniumplayer.core.screens.ncm;

import com.dishanhai.sh_better_ui.kernel.api.input.DirectoryPicker;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.kernel.api.music.MusicDownloads;
import com.dishanhai.sh_better_ui.kernel.api.music.MusicDownloads.*;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.muoniumplayer.compat.lwjgl2.Keyboard;
import com.muoniumplayer.core.ncm.music.CloudMusic;
import com.muoniumplayer.core.ncm.music.LocalMusicLibrary;
import com.muoniumplayer.core.ncm.music.MusicAudioCache;
import com.muoniumplayer.core.ncm.music.NeteaseMusicService;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.core.rendering.ui.container.Panel;
import com.muoniumplayer.core.rendering.ui.container.ScrollPanel;
import com.muoniumplayer.core.utils.other.BrowserUtils;
import com.muoniumplayer.party.ui.PartyIcons;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Download quality picker and the player's local-audio library. */
public final class MusicDownloadSheet extends NcmSheet {
    private enum Page { QUALITY, DOWNLOADS, LOCAL, TASKS, CACHE, CLEAR_CACHE, PROTECTED }
    private record Action(Supplier<String> label, Supplier<String> narration, Runnable run, Panel row) {}
    private static final double ROW_HEIGHT = 62;
    private final MusicDownloads downloads = NeteaseMusicService.songDownloads();
    private final NcmExpressiveLayout layout = new NcmExpressiveLayout();
    private final List<Action> actions = new ArrayList<>();
    private final OnlineTrack track;
    private Page page;
    private Path localDirectory;
    private LocalFile selected;
    private CompletableFuture<Options> optionsRequest;
    private CompletableFuture<List<LocalFile>> filesRequest;
    private CompletableFuture<Boolean> openRequest;
    private CompletableFuture<MusicAudioCache.Sweep> clearRequest;
    private List<LocalFile> files = List.of();
    private List<Task> tasks = List.of();
    private Options options;
    private ScrollPanel list;
    private String notice = "";
    private int focus = -1;
    private int filePage;
    private double localScroll;

    MusicDownloadSheet(Music music, Path folder) {
        track = music == null ? null : onlineTrack(music);
        page = track != null ? Page.QUALITY : folder == null ? Page.DOWNLOADS : Page.LOCAL;
        localDirectory = folder;
    }

    public static boolean supports(Music music) {
        if (music == null || !music.isQQ()) return false;
        String mid = music.getSourceMid();
        return mid != null && mid.matches("[A-Za-z0-9]{1,40}");
    }

    private static OnlineTrack onlineTrack(Music music) {
        return new OnlineTrack("qq", music.getSourceMid(), music.getSourceMid(), music.getName(),
                music.getArtistsName(), "", music.getDuration());
    }

    public static String text(String key, Object... arguments) {
        return Component.translatable("sh_better_ui.muonium.song_download." + key, arguments).getString();
    }

    @Override public void onInit() {
        if (page == Page.QUALITY) optionsRequest = NeteaseMusicService.downloadOptions(track);
        else scanDirectory();
        rebuild();
    }

    @Override public void renderWidget(double mouseX, double mouseY, int wheel) {
        if (!isDismissing()) poll();
        layout.render(this, mouseX, mouseY, wheel, (x, y, scroll) -> {
            super.renderWidget(x, y, 0);
            if (scroll != 0 && !isDismissing()) super.onDWheelReceived(x, y, scroll);
        });
    }

    @Override public void onMouseClickReceived(double x, double y, int button) {
        layout.dispatch(this, x, y, button, (mx, my, input) -> super.onMouseClickReceived(mx, my, input));
    }

    @Override public boolean onKeyTypedReceived(char character, int key) {
        if (isDismissing()) return true;
        if (key == Keyboard.KEY_ESCAPE) { backOrClose(); return true; }
        if (actions.isEmpty()) return true;
        if (key == Keyboard.KEY_TAB || key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
            boolean backwards = key == Keyboard.KEY_UP || key == Keyboard.KEY_TAB &&
                    (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
            focus = Math.floorMod(focus < 0 ? (backwards ? -1 : 0) : focus + (backwards ? -1 : 1), actions.size());
            Action action = actions.get(focus);
            MusicTypography.narrate(Component.literal(action.narration().get()));
            Panel row = action.row();
            if (row != null && list != null) {
                double top = row.getY() - list.getY();
                if (top < 0 || top + row.getHeight() > list.getHeight())
                    list.scrollToOffsetImmediately(list.actualScrollOffset + top);
            }
        } else if (focus >= 0 && (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_SPACE)) {
            actions.get(focus).run().run();
        }
        return true;
    }

    @Override public void dispose() {
        if (optionsRequest != null) optionsRequest.cancel(true);
        if (filesRequest != null) filesRequest.cancel(true);
        super.dispose();
    }

    private void backOrClose() {
        if (page == Page.PROTECTED) show(Page.LOCAL);
        else if (page == Page.CLEAR_CACHE) show(Page.CACHE);
        else dispose();
    }

    private void show(Page next) {
        if (page == next) return;
        boolean returningToLocal = page == Page.PROTECTED && next == Page.LOCAL;
        if (!returningToLocal) filePage = 0;
        page = next;
        notice = "";
        selected = null;
        focus = -1;
        tasks = downloads.tasks();
        if (!returningToLocal) scanDirectory();
        rebuild();
        if (next == Page.LOCAL && localScroll > 0) {
            list.targetScrollOffset = list.actualScrollOffset = localScroll;
            localScroll = 0;
        }
    }

    private void scanDirectory() {
        if (filesRequest != null) filesRequest.cancel(true);
        filesRequest = null;
        files = List.of();
        if (page == Page.DOWNLOADS) filesRequest = downloads.scan(downloads.directory());
        else if (page == Page.LOCAL && localDirectory != null) filesRequest = downloads.scan(localDirectory);
    }

    private void poll() {
        boolean rebuild = false;
        var snapshot = downloads.tasks();
        if (page == Page.DOWNLOADS && snapshot.stream().anyMatch(task ->
                task.state() == State.COMPLETE && tasks.stream().noneMatch(old -> old.id() == task.id() && old.state() == State.COMPLETE)))
            scanDirectory();
        if (page == Page.TASKS) rebuild = !sameTaskRows(tasks, snapshot);
        tasks = snapshot;
        if (optionsRequest != null && optionsRequest.isDone()) {
            try { options = optionsRequest.join(); }
            catch (RuntimeException failure) { notice = text("lookup_failed"); }
            optionsRequest = null;
            rebuild = true;
        }
        if (filesRequest != null && filesRequest.isDone()) {
            try { files = filesRequest.join(); }
            catch (RuntimeException failure) { notice = text("read_failed"); }
            filesRequest = null;
            rebuild = true;
        }
        if (openRequest != null && openRequest.isDone()) {
            try { notice = text(openRequest.join() ? "open_requested" : "open_failed"); }
            catch (RuntimeException failure) { notice = text("open_failed"); }
            openRequest = null;
        }
        if (clearRequest != null && clearRequest.isDone()) {
            try {
                var result = clearRequest.join();
                notice = text("cache_result", size(result.freedBytes), size(result.remainingBytes));
            } catch (RuntimeException failure) { notice = text("cache_failed"); }
            clearRequest = null;
            rebuild = true;
        }
        if (rebuild) rebuild();
    }

    private static boolean sameTaskRows(List<Task> left, List<Task> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++)
            if (left.get(i).id() != right.get(i).id() || left.get(i).state() != right.get(i).state()) return false;
        return true;
    }

    private void rebuild() {
        double scroll = list == null ? 0 : list.targetScrollOffset;
        beginPage();
        actions.clear();
        Panel dialog = NcmExpressive.shell(this, 590, () -> page == Page.QUALITY || page == Page.PROTECTED ? 440 : 650,
                () -> presentation, this::backOrClose);
        NcmExpressive.header(dialog, PartyIcons.NOTE,
                () -> page == Page.QUALITY ? track.artist() : text("title"),
                () -> page == Page.QUALITY ? track.title() : page == Page.PROTECTED ? text("protected") : text("library"),
                this::backOrClose);
        boolean library = page == Page.DOWNLOADS || page == Page.LOCAL || page == Page.TASKS || page == Page.CACHE;
        if (library) buildTabs(dialog);
        Panel body = body(dialog, () -> NcmExpressive.headerHeight(dialog) + (library ? 50 : 6), () -> 34);
        list = feed(body);
        NcmExpressive.label(dialog, () -> notice, NcmExpressive.captionFont(), () -> PAD,
                () -> dialog.getHeight() - 15, () -> dialog.getWidth() - 2 * PAD,
                () -> NcmExpressivePalette.live().muted());
        switch (page) {
            case QUALITY -> buildQualities();
            case DOWNLOADS, LOCAL -> buildFiles();
            case TASKS -> buildTasks();
            case CACHE -> buildCache();
            case CLEAR_CACHE -> buildCacheConfirmation();
            case PROTECTED -> buildProtected();
        }
        if (focus >= actions.size()) focus = actions.size() - 1;
        // Preserve the offset across a task state update. ScrollPanel clamps it after layout.
        list.targetScrollOffset = list.actualScrollOffset = scroll;
    }

    private void buildTabs(Panel dialog) {
        List<Page> tabs = List.of(Page.DOWNLOADS, Page.LOCAL, Page.TASKS, Page.CACHE);
        for (int i = 0; i < tabs.size(); i++) {
            Page tab = tabs.get(i);
            int slot = i;
            var width = (java.util.function.DoubleSupplier) () -> (dialog.getWidth() - 2 * PAD) / tabs.size();
            Supplier<String> label = () -> text(tab.name().toLowerCase(Locale.ROOT));
            Action action = new Action(label, label, () -> {
                list.scrollToOffsetImmediately(0);
                show(tab);
            }, null);
            int index = actions.size();
            actions.add(action);
            NcmExpressive.action(dialog, "", () -> caption(action, index),
                    page == tab ? NcmExpressive.ActionStyle.PRIMARY : NcmExpressive.ActionStyle.TONAL,
                    () -> PAD + slot * width.getAsDouble(), () -> NcmExpressive.headerHeight(dialog) + 5,
                    () -> width.getAsDouble() - 5, () -> 36, action.run());
        }
    }

    private void buildQualities() {
        if (optionsRequest != null) { info(() -> text("checking")); return; }
        if (options == null) {
            row(() -> text("lookup_failed"), () -> "", "retry", () -> {
                notice = "";
                optionsRequest = NeteaseMusicService.downloadOptions(track);
                rebuild();
            });
            return;
        }
        Access access = options.access();
        if (access != Access.AVAILABLE) info(() -> accessText(access));
        for (Option option : options.qualities()) {
            String action = switch (access) {
                case AVAILABLE -> "save";
                case SIGN_IN -> "sign_in";
                case MEMBERSHIP, PURCHASE, UNAVAILABLE -> null;
            };
            row(() -> qualityText(option.quality()), () -> size(option.bytes()), action, () -> {
                if (access == Access.SIGN_IN) openAccount();
                else {
                    NeteaseMusicService.downloadSong(track, option.quality());
                    show(Page.TASKS);
                }
            });
        }
        if (access == Access.SIGN_IN && options.qualities().isEmpty())
            row(() -> accessText(access), () -> "", "sign_in", this::openAccount);
        if (usesOfficialPage(access))
            row(() -> text("official_song"), () -> "", "open",
                    () -> openQqSong(track));
    }

    private void buildFiles() {
        if (page == Page.LOCAL) row(() -> text("pick_folder"), () -> localDirectory == null ? "" : localDirectory.toString(),
                "choose", this::pickFolder);
        if (filesRequest != null) { info(() -> text("reading")); return; }
        if (files.isEmpty()) { info(() -> text("empty_files")); return; }
        int pages = Math.max(1, (files.size() + 47) / 48);
        filePage = Math.min(filePage, pages - 1);
        if (pages > 1) {
            if (filePage > 0) row(() -> text("file_page", filePage + 1, pages), () -> "", "prev_page", () -> filePage(-1));
            if (filePage + 1 < pages) row(() -> text("file_page", filePage + 1, pages), () -> "", "next_page", () -> filePage(1));
        }
        for (LocalFile file : files.subList(filePage * 48, Math.min(files.size(), (filePage + 1) * 48))) {
            boolean protectedAudio = file.format() == LocalFormat.QQ_PROTECTED;
            row(() -> file.path().getFileName().toString(),
                    () -> protectedAudio ? text("protected_size", size(file.bytes())) : size(file.bytes()),
                    protectedAudio ? "inspect" : "play", () -> {
                        if (protectedAudio) {
                            localScroll = list.targetScrollOffset;
                            page = Page.PROTECTED;
                            selected = file;
                            focus = -1;
                            notice = "";
                            list.scrollToOffsetImmediately(0);
                            rebuild();
                        } else playLocal(file.path());
                    });
        }
    }

    private void filePage(int step) {
        filePage += step;
        list.scrollToOffsetImmediately(0);
        rebuild();
    }

    private void buildCache() {
        row(() -> text("cache_active"), () -> text("cache_size", size(MusicAudioCache.cachedTotalBytes())),
                clearRequest == null ? "clear_cache" : null, () -> show(Page.CLEAR_CACHE));
        if (clearRequest != null) info(() -> text("cache_clearing"));
    }

    private void buildCacheConfirmation() {
        info(() -> text("cache_confirm"));
        info(() -> text("cache_in_use"));
        row(() -> text("clear_cache"), () -> text("cache_size", size(MusicAudioCache.cachedTotalBytes())),
                "clear", () -> {
                    clearRequest = MusicAudioCache.clearIdleAsync();
                    show(Page.CACHE);
                });
        row(() -> text("cache"), () -> "", "back", this::backOrClose);
    }

    private void buildTasks() {
        if (tasks.isEmpty()) info(() -> text("empty_tasks"));
        for (Task task : tasks) {
            String action = switch (task.state()) {
                case QUEUED, DOWNLOADING -> "cancel";
                case COMPLETE -> "play";
                case FAILED -> switch (task.access()) {
                    case SIGN_IN -> "sign_in";
                    case MEMBERSHIP, PURCHASE, UNAVAILABLE -> "open_official";
                    case AVAILABLE -> "retry";
                };
                case CANCELLED -> "retry";
            };
            row(() -> task.track().title() + " · " + qualityText(task.quality()),
                    () -> taskStatus(task.id()), action, () -> {
                        switch (task.state()) {
                            case QUEUED, DOWNLOADING -> downloads.cancel(task.id());
                            case COMPLETE -> playLocal(task.file());
                            case CANCELLED, FAILED -> {
                                if (task.access() == Access.SIGN_IN) openAccount();
                                else if (usesOfficialPage(task.access()))
                                    openQqSong(task.track());
                                else NeteaseMusicService.downloadSong(task.track(), task.quality());
                            }
                        }
                    });
        }
    }

    private String taskStatus(long id) {
        for (Task task : tasks) if (task.id() == id) return switch (task.state()) {
            case DOWNLOADING -> text("progress", size(task.received()), size(task.total()));
            case COMPLETE -> text("complete", size(task.total()));
            case FAILED -> task.access() != Access.AVAILABLE ? accessText(task.access()) : text("failed");
            case QUEUED -> text("queued");
            case CANCELLED -> text("cancelled");
        };
        return "";
    }

    private void buildProtected() {
        if (selected == null) return;
        row(() -> selected.path().getFileName().toString(), () -> text("protected_detail"), "open_associated",
                () -> openLocal(selected.path()));
        row(() -> text("qq_client"), () -> "", "open", () -> openUrl("https://y.qq.com/download/index.html"));
        row(() -> text("local"), () -> "", "back", this::backOrClose);
    }

    private void pickFolder() {
        Path initial = localDirectory == null ? Path.of(System.getProperty("user.home"), "Music") : localDirectory;
        DirectoryPicker.choose(Component.translatable("sh_better_ui.muonium.pick_music_folder"), initial.toFile())
                .ifPresent(folder -> {
                    localDirectory = folder.toPath();
                    filePage = 0;
                    notice = "";
                    scanDirectory();
                    rebuild();
                });
    }

    private void openLocal(Path path) {
        if (openRequest != null) return;
        notice = text("opening");
        openRequest = downloads.openLocal(path);
    }

    private void openUrl(String url) {
        if (!BrowserUtils.open(url)) notice = text("browser_failed");
    }

    private void openQqSong(OnlineTrack song) {
        openUrl("https://y.qq.com/n/ryqq/songDetail/" + song.id());
    }

    private void openAccount() {
        NCMScreen.getInstance().openDownloadAccount();
    }

    private void playLocal(Path file) {
        if (file == null || !java.nio.file.Files.isRegularFile(file)) {
            notice = text("read_failed");
            return;
        }
        CloudMusic.play(List.of(LocalMusicLibrary.fromLocalFile(file.toFile())), 0);
        dispose();
    }

    private void info(Supplier<String> message) {
        var row = strip(list, 42);
        NcmExpressive.label(row, message, NcmExpressive.bodyFont(), () -> PAD, () -> 21,
                () -> row.getWidth() - 2 * PAD, () -> NcmExpressivePalette.live().muted());
    }

    private void row(Supplier<String> title, Supplier<String> detail, String key, Runnable run) {
        Panel row = strip(list, ROW_HEIGHT);
        NcmExpressive.surface(row, PAD, 3, () -> row.getWidth() - 2 * PAD, () -> ROW_HEIGHT - 6,
                12, () -> NcmExpressivePalette.live().container());
        var buttonWidth = (java.util.function.DoubleSupplier) () -> key == null ? 0
                : Math.min(175, (row.getWidth() - 2 * PAD) * .42);
        NcmExpressive.label(row, title, NcmExpressive.labelFont(), () -> PAD + 12, () -> 20,
                () -> Math.max(1, row.getWidth() - 2 * PAD - buttonWidth.getAsDouble() - 24), () -> NcmExpressivePalette.live().text());
        NcmExpressive.label(row, detail, NcmExpressive.captionFont(), () -> PAD + 12, () -> 42,
                () -> Math.max(1, row.getWidth() - 2 * PAD - buttonWidth.getAsDouble() - 24), () -> NcmExpressivePalette.live().muted());
        if (key == null) return;
        Action action = new Action(() -> text(key), () -> title.get() + " · " + detail.get() + " · " + text(key),
                run, row);
        int index = actions.size();
        actions.add(action);
        var button = NcmExpressive.action(row, "", () -> caption(action, index), NcmExpressive.ActionStyle.TONAL,
                () -> row.getWidth() - PAD - buttonWidth.getAsDouble(), () -> 12,
                () -> buttonWidth.getAsDouble() - 12, () -> 38, run);
        button.setTooltip(action.narration());
    }

    private String caption(Action action, int index) {
        return (index == focus ? "› " : "") + action.label().get();
    }

    private static String accessText(Access access) {
        return text("access." + access.name().toLowerCase(Locale.ROOT));
    }

    private static boolean usesOfficialPage(Access access) {
        return access == Access.MEMBERSHIP || access == Access.PURCHASE || access == Access.UNAVAILABLE;
    }

    private static String qualityText(Quality quality) {
        return text("quality." + quality.name().toLowerCase(Locale.ROOT));
    }

    private static String size(long bytes) {
        return String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0);
    }
}
