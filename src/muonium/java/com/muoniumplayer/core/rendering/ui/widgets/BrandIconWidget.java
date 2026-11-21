// Modified for SHBUI by dishanhai. See META-INF/third-party/muonium-player-2.6.0/SOURCE.
package com.muoniumplayer.core.rendering.ui.widgets;

import com.muoniumplayer.core.ncm.music.MusicPlatform;
import com.muoniumplayer.core.rendering.BrandVectorIcons;

/**
 * 一枚矢量品牌标识（{@link BrandVectorIcons}）。
 *
 * <p>做成组件而不是在父组件的回调里直接画：它要跟着父组件的 {@code alpha} 一起淡入淡出
 * （账号头像位的品牌层整层是靠 {@code veil} 的 alpha 切换的），而组件树的 alpha 是自动相乘的。</p>
 *
 * <p>尺寸在构造时就定死。{@code LabelWidget} 那种「渲染末尾回填尺寸」的组件不能在这里用：
 * 同一帧里旁边的文字要靠 {@code getWidth()} 算位置，而那时它还是 0。</p>
 */
public class BrandIconWidget extends DrawableIconWidget<BrandIconWidget> {

    public BrandIconWidget(MusicPlatform platform, double size) {
        super((x, y, width, height, color) ->
                BrandVectorIcons.drawIn(platform, x, y, width, height, color), size, size);
        this.setClickable(false);
    }
}
