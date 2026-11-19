package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.lyrics.LyricClient;
import com.dishanhai.sh_better_ui.client.lyrics.common.bridge.BridgePlaybackSource;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.FloatingLyricRenderer;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.LocalLyricFeed;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.LyricFeed;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LrcParser;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.TtmlParser;
import com.dishanhai.sh_better_ui.client.lyrics.render.LyricView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/**
 * 山海：歌词数据源、渲染器和客户端设置。
 * 实现改编自 <a href="https://github.com/beipuo/Link-Lyric">Link-Lyric（beipuo）</a>。
 */
public final class LyricApi {
    LyricApi() {}

    /** 使用宿主提供的歌词时间轴与播放进度。 */
    public LocalLyricFeed localFeed(String sourceId) {
        return new LocalLyricFeed(sourceId);
    }

    /** 将 LRC 和可选的译文 LRC 转为可向本地数据源发布的时间轴。未知曲长传 0。 */
    public LyricTimeline parseLrc(String lyrics, String translation, long trackDurationMs) {
        return LrcParser.parse(lyrics, translation, trackDurationMs);
    }

    /** 将媒体时间的 TTML 歌词转为可向本地数据源发布的时间轴。未知曲长传 0。 */
    public LyricTimeline parseTtml(String lyrics, long trackDurationMs) {
        return TtmlParser.parse(lyrics, trackDurationMs);
    }

    /** 创建独立的 Link-MC 会话。先在 Salt Player 中启用插件，再由调用方 start 和 close。 */
    public BridgePlaybackSource linkMc() {
        return BridgePlaybackSource.linkMc();
    }

    /** 当播放器缺少歌词时，从指定目录按当前曲目查找 TTML 或 LRC。 */
    public BridgePlaybackSource linkMc(Path lyricDirectory) {
        return BridgePlaybackSource.linkMc(lyricDirectory);
    }

    /** {@link #linkMc()} 的播放器名称入口。 */
    public BridgePlaybackSource saltPlayer() {
        return linkMc();
    }

    /** 每个绘制区域使用独立渲染器，避免换行与进度状态互相干扰。 */
    public FloatingLyricRenderer renderer(LyricFeed feed) {
        var renderer = new FloatingLyricRenderer(feed);
        renderer.setTextResolver(key -> Component.translatable(key).getString());
        return renderer;
    }

    /** 在当前 UI 区域内绘制，使用其裁切范围和无障碍偏好。 */
    public boolean draw(FloatingLyricRenderer renderer, UiFrame frame, Font font, Bounds area) {
        return LyricView.draw(renderer, frame.graphics(), font, area, frame.preferences());
    }

    /** Explicit scoped font; callers may pass their own resource-pack font instead of a default. */
    public boolean drawScoped(FloatingLyricRenderer renderer,UiFrame frame,Font font,Bounds area,
                              net.minecraft.resources.ResourceLocation fontId,int lineHeight) {
        return LyricView.drawScoped(renderer,frame.graphics(),font,area,frame.preferences(),null,fontId,lineHeight);
    }

    public Screen settings(Screen parent) {
        return LyricClient.settings(parent);
    }

    public Screen position(Screen parent) {
        return LyricClient.position(parent);
    }
}
