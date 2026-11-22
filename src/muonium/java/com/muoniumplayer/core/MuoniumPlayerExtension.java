// Modified for SHBUI by dishanhai. See META-INF/third-party/muonium-player-2.6.0/SOURCE.
package com.muoniumplayer.core;

import lombok.Getter;
import com.muoniumplayer.api.OpenAPI;
import com.muoniumplayer.core.management.AbstractManager;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.module.impl.OpenNCMScreen;
import com.muoniumplayer.core.ncm.music.CloudMusic;
import com.muoniumplayer.core.reflection.Reflection;
import com.muoniumplayer.core.settings.HudConfig;
import com.muoniumplayer.core.screens.ncm.NCMTheme;
import com.muoniumplayer.core.screens.ncm.NCMPlayerConfig;
import com.muoniumplayer.core.ncm.music.GdStudioSourceSettings;
import com.muoniumplayer.core.ncm.music.NeteaseUnblockSettings;
import com.muoniumplayer.core.ncm.music.SwitchSourceSettings;
import com.muoniumplayer.core.utils.other.multithreading.MultiThreadingUtil;
import com.muoniumplayer.core.widget.impl.MusicInfoWidget;
import com.muoniumplayer.core.widget.impl.MusicLyricsWidget;

import java.util.Arrays;
import java.util.List;

/**
 */
public class MuoniumPlayerExtension {

    public static final String NAME = "MuoniumPlayer";
    public static final String AUTHOR = "MuoniumPlayer Contributors";
    public static final String VERSION = "2.6.0";

    @Getter
    private static final MuoniumPlayerExtension instance = new MuoniumPlayerExtension();

    @Getter
    private FontManager fontManager;

    /** 注册到旧版 OpenAPI 桥接上的音乐播放器功能。 */
    public OpenNCMScreen musicPlayerModule = new OpenNCMScreen();
    public MusicInfoWidget musicInfo = new MusicInfoWidget();
    public MusicLyricsWidget musicLyrics = new MusicLyricsWidget();

    public MuoniumPlayerExtension() {

    }

    public void init(OpenAPI api) {
        com.muoniumplayer.core.rendering.ui.widgets.ThemedTextureIconWidget.setDefaultTextureLoader(
                com.muoniumplayer.core.screens.ncm.PlayerIconAssets::requestLoad);
        com.dishanhai.sh_better_ui.kernel.api.controls.WidgetTheme.setColors(role ->
                com.muoniumplayer.core.screens.ncm.NCMScreen.getColor(
                        com.muoniumplayer.core.screens.ncm.NCMScreen.ColorType.valueOf(role.name())));
        com.dishanhai.sh_better_ui.kernel.api.controls.WidgetTheme.setShowBounds(
                () -> com.muoniumplayer.core.settings.ClientSettings.SHOW_WIDGET_BOUNDARY);
        com.dishanhai.sh_better_ui.kernel.api.controls.WidgetTouch.Host.set(new com.dishanhai.sh_better_ui.kernel.api.controls.WidgetTouch() {
            private com.muoniumplayer.core.input.TouchGestures gestures() {
                return com.muoniumplayer.core.input.TouchGestures.get();
            }

            @Override public boolean enabled() { return com.muoniumplayer.core.settings.TouchSettings.ENABLED; }
            @Override public boolean verticalDrag() { return gestures().isVerticalDrag(); }
            @Override public Object claimedBy() { return gestures().claimedBy(); }
            @Override public double dy() { return gestures().dy(); }
            @Override public boolean pressed() { return gestures().isPressed(); }
            @Override public double velocityY() { return gestures().velocityY(); }
            @Override public double startX() { return gestures().startX(); }
            @Override public double startY() { return gestures().startY(); }
            @Override public boolean claim(Object owner) { return gestures().claim(owner); }
            @Override public double flingMaxDistance() { return com.muoniumplayer.core.settings.TouchSettings.FLING_MAX_DISTANCE; }
            @Override public double flingDeceleration() { return com.muoniumplayer.core.settings.TouchSettings.FLING_DECEL; }
        });
        com.dishanhai.sh_better_ui.kernel.api.text.FontServices.Host.set(new com.dishanhai.sh_better_ui.kernel.api.text.FontServices() {
            @Override
            public long pinnedBytes(String fileName) {
                return com.muoniumplayer.core.management.FontAssetDownload.pinnedBytes(fileName);
            }

            @Override
            public java.io.File cachedFile(String fileName) {
                return com.muoniumplayer.core.management.FontAssetDownload.cachedFile(fileName);
            }

            @Override
            public void fontChanged() {
                com.muoniumplayer.core.lyrics3d.LyricGlyphAtlas.onFontChanged();
            }
        });
        api.registerEvent(MuoniumPlayerEventHandler.getInstance());
        com.muoniumplayer.core.download.DownloadCatalog.beginSession();

        // 在异步账号引导可能创建 AudioPlayer 之前，先恢复所有播放器偏好。
        // 这包括 mod 自有的持久化音量。
        HudConfig.load();
        com.muoniumplayer.core.screens.hud.HudInitialSelection.initialize();
        // Folia 背景的独立配置（config/muonium/folia_lyrics_background.json）。
        com.muoniumplayer.core.screens.ncm.foliabg.FoliaBackgroundConfig.load();
        this.musicInfo.volume.setValue((double) HudConfig.playerVolume);
        this.musicInfo.animatedCover.setValue(HudConfig.animatedCoverEnabled);
        NCMPlayerConfig.load();
        GdStudioSourceSettings.load();
        NeteaseUnblockSettings.load();
        SwitchSourceSettings.load();
        CloudMusic.quality = NCMPlayerConfig.getAudioQuality();
        MultiThreadingUtil.runAsync(CloudMusic::initNCM);
        Reflection.init(api);

        this.fontManager = new FontManager();

        List<AbstractManager> managers = Arrays.asList(this.fontManager);

        for (AbstractManager manager : managers) {
//            logger.debug("calling init() on {}...", manager.getName());
            manager.init();
        }

        api.registerFeature(this.musicPlayerModule);
        api.registerFeature(this.musicInfo);
        api.registerFeature(this.musicInfo.widget);
        api.registerFeature(this.musicLyrics);
        api.registerFeature(this.musicLyrics.widget);

        // 歌曲信息 HUD / 歌词 HUD：显隐随存档走（HudConfig.musicInfoEnabled/musicLyricsEnabled，
        // 编辑器与快捷键切换时落盘）。此前每次启动都硬编码 setEnabled(true)，用户关掉的
        // HUD 组件重启全部回来——这正是「HUD 组件关闭不记忆」的根因。
        this.musicInfo.setEnabled(HudConfig.musicInfoEnabled);
        this.musicLyrics.setEnabled(HudConfig.musicLyricsEnabled);
        NCMTheme.load();
        this.musicLyrics.loadHudEditorSettings();
        com.muoniumplayer.core.download.DownloadCatalog.markStartupReady();
    }

    public void unload() {

    }

    /**
     * 是否正在主（客户端）线程上。
     *
     * <p><b>1.20.1 移植关键点</b>：1.8.9 的这个判断是比线程名 {@code "Client thread"}。
     * 1.20.1 把同一条线程改名成 {@code "Render thread"}，照抄名字会让主线程上的调用被判成
     * "不在主线程"，于是 {@code MultiThreadingUtil#runOnMainThreadBlocking} 把任务塞进队列
     * 后在同一条线程上等它执行 —— 自我死锁。实测表现为打开播放器界面（NCMScreen 首帧
     * 加载 ThemedTextureIconWidget 的纹理）后整个客户端卡死，窗口停在最后一帧。
     *
     * <p>改成问 MC 自己：{@code BlockableEventLoop#isSameThread()}（Yarn 里叫
     * {@code ThreadExecutor#isOnThread()}）比的是线程对象本身，
     * 等价于 1.8.9 的 {@code IThreadListener#isCallingFromMinecraftThread()}，不依赖线程名。
     */
    public static boolean isCallingFromMainThread() {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        // 极早期（Minecraft 尚未构造完）退化成按名字判断，避免 NPE。
        return client != null ? client.isSameThread() : "Render thread".equals(Thread.currentThread().getName());
    }

}
