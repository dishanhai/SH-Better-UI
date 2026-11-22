// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton
import com.dishanhai.sh_better_ui.kernel.api.layout.ListEntrance
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments

/** Presentation shared by the Skija drawer and the native comment page. */
internal class MusicCommentPresentation {
    private val entrance = ListEntrance()
    private var result: OnlineMusicComments? = null

    fun update(result: OnlineMusicComments?, now: Long) {
        if (this.result === result) return
        this.result = result
        if (!result?.entries.isNullOrEmpty()) entrance.start(now)
    }

    fun reset() { result = null }

    fun offset(row: Int, now: Long, reducedMotion: Boolean): Double =
        if (result?.entries.isNullOrEmpty()) 0.0 else entrance.offset(row, now, reducedMotion)

    companion object {
        fun skeleton(viewport: Bounds): List<UiSkeleton.Block> {
            val textX = viewport.x() + 35
            val width = viewport.right() - textX - 12
            if (width < 24 || viewport.height() < 30) return emptyList()
            return buildList {
                for ((index, y) in (viewport.y()..viewport.bottom() - 30 step 68).withIndex()) {
                    add(UiSkeleton.Block(Bounds(viewport.x() + 4, y + 6, 24, 24), 12f))
                    add(UiSkeleton.Block(Bounds(textX, y + 10, width * (30 + index % 3 * 4) / 100, 7)))
                    if (y + 37 <= viewport.bottom())
                        add(UiSkeleton.Block(Bounds(textX, y + 30, width * (92 - index % 3 * 8) / 100, 7)))
                    if (y + 52 <= viewport.bottom())
                        add(UiSkeleton.Block(Bounds(textX, y + 45, width * (56 + index % 3 * 9) / 100, 7)))
                    if (y + 63 <= viewport.bottom())
                        add(UiSkeleton.Block(Bounds(textX, y + 59, width / 4, 4), 2f))
                }
            }
        }
    }
}
