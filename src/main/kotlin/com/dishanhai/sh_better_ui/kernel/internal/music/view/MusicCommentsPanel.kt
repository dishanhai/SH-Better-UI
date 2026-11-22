// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view

import com.dishanhai.sh_better_ui.client.api.UiFrame
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.Entry
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.Order
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/** Shared comment reader for the standard and redesigned UUK workspaces. */
internal class MusicCommentsPanel(player: OnlineMusicPlayer) {
    data class Bookmark(val discussion: MusicCommentBrowser.State, val offset: Int)

    private data class Row(val line: MusicCommentBrowser.Line, val author: Component, val body: Component,
                           val metadata: Component, val top: Int, val bodyHeight: Int, val index: Int) {
        val actionY get() = if (line.entry == null) 1 else bodyHeight + 39
        val height get() = if (line.entry == null) 23 else bodyHeight + 40 + if (line.action != null) 19 else 0
    }
    private data class ReplyButton(val control: MusicTextControl, var line: MusicCommentBrowser.Line? = null)

    private lateinit var host: PreviewContext
    private lateinit var summary: Bounds
    private lateinit var list: Bounds
    private lateinit var orderBar: Bounds
    private lateinit var scroll: OnlineMusicScrollRegion
    private lateinit var previous: MusicTextControl
    private lateinit var next: MusicTextControl
    private lateinit var retry: MusicTextControl
    private val sorts = linkedMapOf<Order, MusicTextControl>()
    private val replyButtons = mutableListOf<ReplyButton>()
    private val avatars = MusicCommentAvatars { OnlineMusicViewPaint.Avatar(it) }
    private val presentation = MusicCommentPresentation()
    private var visible = false
    private val browser = MusicCommentBrowser(player)
    private val track get() = browser.state?.track
    private val orders get() = browser.orders
    private val order get() = browser.state?.order
    private val page get() = browser.state?.page ?: 1
    private val result get() = browser.state?.result
    private val failed get() = browser.state?.failed == true
    private var offset = 0
    private var rows = emptyList<Row>()
    private var renderedThread: MusicCommentBrowser.Thread? = null
    private var contentHeight = 0
    fun supports(track: OnlineTrack?): Boolean = browser.supports(track)

    fun mount(context: PreviewContext, area: Bounds) {
        mount(context, Bounds(area.x(), area.y(), area.width(), 28),
            Bounds(area.x(), area.y() + 30, area.width(), 22),
            Bounds(area.x(), area.y() + 54, area.width(), (area.height() - 54).coerceAtLeast(0)))
    }

    fun mount(context: PreviewContext, summary: Bounds, orderBar: Bounds, area: Bounds) {
        if (::scroll.isInitialized) scroll.cancelDrag()
        host = context
        this.summary = summary
        this.orderBar = orderBar
        list = Bounds(area.x(), area.y(), area.width(), (area.height() - 22).coerceAtLeast(0))
        scroll = host.add(OnlineMusicScrollRegion(list.x(), list.y(), list.width(), list.height(), label("title")) {
            offset = it
            narrate()
        })
        scroll.setStep(28)
        scroll.setOnBrowse { host.screen().setFocused(scroll) }
        sorts.clear()
        for (value in Order.entries) {
            val key = value.name.lowercase(java.util.Locale.ROOT)
            sorts[value] = button(orderBar, key) { select(value) }
        }
        val y = area.bottom() - 20
        previous = button(Bounds(area.x(), y, 54, 20), "previous") { load(page - 1) }
        next = button(Bounds(area.right() - 54, y, 54, 20), "next") { load(page + 1) }
        retry = button(Bounds(area.x() + (area.width() - 62) / 2, y, 62, 20), "retry") { load(page) }
        replyButtons.clear()
        repeat(21) {
            val control = button(Bounds(list.x(), list.y(), 80, 16), "view_replies") {
                replyButtons.getOrNull(it)?.line?.let(browser::activate)
            }
            control.setVisible(false)
            replyButtons.add(ReplyButton(control))
        }
        rebuildRows()
        refresh()
    }

    fun open(song: OnlineTrack) {
        clear()
        browser.open(song)
        visible = true
        refresh()
    }

    fun show(value: Boolean) {
        if (visible == value) return
        visible = value
        refresh()
    }

    fun bookmark(): Bookmark? = browser.bookmark()?.let { Bookmark(it, offset) }

    fun restore(state: Bookmark) {
        clear()
        browser.restore(state.discussion)
        offset = state.offset
        visible = true
        rebuildRows()
        refresh()
    }

    fun clear() {
        browser.clear()
        presentation.reset()
        avatars.close()
        offset = 0
        visible = false
        rebuildRows()
        refresh()
    }

    fun suspend() {
        browser.suspend()
        avatars.close()
        show(false)
    }

    private fun button(bounds: Bounds, key: String, action: () -> Unit): MusicTextControl =
        host.add(MusicTextControl(bounds, label(key), host.preferences(), action))

    private fun select(next: Order) {
        if (next == order || next !in orders) return
        browser.select(next)
        resetScroll()
    }

    private fun load(target: Int) {
        if (!visible || target !in 1..1000) return
        browser.load(target)
        resetScroll()
    }

    private fun resetScroll() {
        if (::scroll.isInitialized) scroll.cancelDrag()
        presentation.reset()
        offset = 0
        rebuildRows()
        refresh()
    }

    private fun rebuildRows() {
        if (!::list.isInitialized) return
        renderedThread = browser.thread
        contentHeight = 0
        rows = browser.lines().mapIndexed { index, line ->
            val entry = line.entry
            val name = entry?.author?.takeIf(String::isNotBlank)?.let { MusicTypography.text(it) } ?: label("unknown_author")
            val author = MusicTypography.bold(entry?.replyingTo?.let { label("reply_author", name, it) } ?: name)
            val body = MusicTypography.text(entry?.content.orEmpty())
            val width = (list.width() - textInset(line) - 16).coerceAtLeast(1)
            val lines = host.font().split(OnlineMusicViewPaint.wrapContent(body), width).size.coerceAtLeast(1)
            val row = Row(line, author, body, entry?.let(::metadata) ?: Component.empty(), contentHeight,
                lines * (MusicTypography.LINE_HEIGHT + 3), index)
            contentHeight += row.height
            row
        }
        offset = offset.coerceIn(0, (contentHeight - list.height()).coerceAtLeast(0))
    }

    private fun metadata(entry: Entry): Component {
        val parts = listOfNotNull(entry.date?.let { Component.literal(it.toString()) },
            entry.likes?.let { label("likes", it) })
        val text = Component.empty()
        parts.forEachIndexed { index, part ->
            if (index > 0) text.append(" · ")
            text.append(part)
        }
        return MusicTypography.text(text)
    }

    private fun textInset(line: MusicCommentBrowser.Line): Int = if (line.nested) 48 else 34

    private fun refresh() {
        if (!::scroll.isInitialized) return
        val shown = visible && track != null
        val interactive = shown && host.interactive() && Minecraft.getInstance().isWindowActive
        val tabs = orders.size
        val tabWidth = if (tabs > 1) (orderBar.width() - (tabs - 1) * 4) / tabs else 0
        for ((value, button) in sorts) {
            val index = orders.indexOf(value)
            val displayed = shown && tabs > 1 && index >= 0
            if (displayed) {
                button.setX(orderBar.x() + index * (tabWidth + 4))
                button.setWidth(tabWidth)
            }
            button.setVisible(displayed)
            button.setAvailable(button.visible && interactive)
            button.setSelected(value == order)
        }
        previous.setVisible(shown && page > 1)
        previous.setAvailable(previous.visible && interactive)
        next.setVisible(shown && page < 1000 && result?.hasMore == true)
        next.setAvailable(next.visible && interactive)
        retry.setVisible(shown && failed)
        retry.setAvailable(retry.visible && interactive)
        scroll.visible = shown && list.height() > 0
        if (!scroll.visible || !interactive) scroll.cancelDrag()
        scroll.configure(offset, contentHeight, list.height())
        // The list remains focusable for narration, including while loading and when empty.
        scroll.active = scroll.visible && interactive
        if (!interactive) replyButtons.forEach { it.control.setVisible(false) }
        narrate()
    }

    private fun narrate() {
        if (!scroll.visible) return
        val row = rows.firstOrNull { it.top + it.height > offset }
        scroll.message = if (row == null) label(listStatus())
            else row.line.status?.let { label(it) } ?: if (row.line.entry == null) row.line.actionLabel()
            else label("read", row.author, row.body, row.metadata)
    }

    private fun listStatus(): String = when {
        browser.loading || browser.state?.request == true -> "loading"
        failed -> "failed"
        else -> "empty"
    }

    fun render(ui: UiFrame, ink: Int, muted: Int) {
        val song = track ?: return
        if (!visible || !::scroll.isInitialized) return
        val previousResult = result
        browser.poll()
        if (previousResult !== result || renderedThread !== browser.thread) rebuildRows()
        val now = System.nanoTime() / 1_000_000L
        val preferences = ui.preferences()
        presentation.update(result, now)
        refresh()
        scroll.tick()
        for (button in sorts.values + listOf(previous, next, retry)) button.setInk(ink, muted)
        updateReplyButtons(ink, muted, now, preferences.reducedMotion())
        val title = MusicTypography.bold(song.title())
        val source = Component.translatable("sh_better_ui.online_music.source.${song.source()}")
        val subtitle = source.copy()
        result?.total?.let { subtitle.append(" · ").append(label("count", it)) }
        if (song.artist().isNotBlank()) subtitle.append(" · ").append(MusicTypography.text(song.artist()))
        line(ui, title, summary.x() + 4, summary.y() + 1, summary.width() - 8, ink)
        line(ui, subtitle, summary.x() + 4, summary.y() + 14, summary.width() - 8, muted)

        if (list.height() > 0) {
            val status = listStatus()
            val blocks = if (rows.isEmpty() && status == "loading") MusicCommentPresentation.skeleton(list) else emptyList()
            if (blocks.isNotEmpty()) {
                OnlineMusicViewPaint.skeleton(ui, list, blocks, now)
            } else {
                OnlineMusicViewPaint.clip(ui, list)
                try {
                    if (rows.isEmpty()) line(ui, label(status), list.x() + 6, list.y() + 4, list.width() - 12, ink)
                    else drawRows(ui, ink, muted, now, preferences.reducedMotion())
                } finally {
                    OnlineMusicViewPaint.unclip(ui)
                }
            }
        }
        if (!failed && (page > 1 || result?.hasMore == true)) {
            line(ui, label("page", page), list.x() + 58, list.bottom() + 7, list.width() - 116, muted)
        }
    }

    private fun rowY(row: Row, now: Long, reducedMotion: Boolean): Int =
        list.y() + row.top - offset + presentation.offset(row.index, now, reducedMotion).roundToInt()

    private fun updateReplyButtons(ink: Int, muted: Int, now: Long, reducedMotion: Boolean) {
        val interactive = host.interactive() && Minecraft.getInstance().isWindowActive
        val actions = rows.filter { it.line.action != null }
        replyButtons.forEachIndexed { index, button ->
            val row = actions.getOrNull(index)
            val y = if (row == null) -1 else rowY(row, now, reducedMotion) + row.actionY
            if (row == null || y < list.y() || y + 16 > list.bottom()) {
                if (button.control.isFocused) host.screen().setFocused(scroll)
                button.control.setVisible(false)
                return@forEachIndexed
            }
            val name = row.line.actionLabel()
            if (button.line != row.line) {
                if (button.control.isFocused && button.line?.actionEntry?.id != row.line.actionEntry?.id)
                    host.screen().setFocused(scroll)
                button.control.rebind(name)
                button.line = row.line
            }
            val x = list.x() + textInset(row.line)
            button.control.setX(x)
            button.control.setY(y)
            button.control.setWidth((host.font().width(name) + 16).coerceAtMost((list.right() - x - 16).coerceAtLeast(1)))
            button.control.setInk(ink, muted)
            button.control.setVisible(true)
            button.control.setAvailable(interactive)
        }
    }

    private fun drawRows(ui: UiFrame, ink: Int, muted: Int, now: Long, reducedMotion: Boolean) {
        for (row in rows) {
            val y = rowY(row, now, reducedMotion)
            if (y >= list.bottom()) break
            if (y + row.height <= list.y()) continue
            val x = list.x() + textInset(row.line)
            val width = (list.right() - x - 16).coerceAtLeast(1)
            if (row.line.nested) OnlineMusicViewPaint.rule(ui, Bounds(list.x() + 20, y, 1, row.height),
                (muted and 0xffffff) or 0x62000000)
            val entry = row.line.entry
            if (entry == null) {
                row.line.status?.let { line(ui, label(it), x, y + 7, width, muted) }
                continue
            }
            val size = if (row.line.nested) 17 else 22
            val portrait = Bounds(x - size - 6, y + 5, size, size)
            val image = avatars.get(entry.avatar)
            if (image != null) image.draw(ui, portrait) else {
                OnlineMusicViewPaint.panel(ui, portrait, (muted and 0xffffff) or 0x32000000, false)
                val author = row.author.string
                if (author.isNotEmpty()) {
                    val first = Component.literal(author.substring(0, author.offsetByCodePoints(0, 1)))
                    line(ui, first, portrait.x() + (size - host.font().width(first)) / 2, portrait.y() + (size - 9) / 2, size, muted)
                }
            }
            line(ui, row.author, x, y + 8, width, muted)
            OnlineMusicViewPaint.paragraph(ui, host.font(), row.body, Bounds(x, y + 25, width, row.bodyHeight), ink)
            line(ui, row.metadata, x, y + 27 + row.bodyHeight, width, muted)
            if (!row.line.nested) OnlineMusicViewPaint.rule(ui, Bounds(x, y + row.height - 2, width, 1),
                (muted and 0xffffff) or 0x43000000)
        }
        if (scroll.isFocused) OnlineMusicViewPaint.panel(ui, Bounds(list.x(), list.y(), 1, list.height()), muted, false)
    }

    private fun line(ui: UiFrame, text: Component, x: Int, y: Int, width: Int, color: Int) {
        OnlineMusicViewPaint.line(ui, host.font(), text, x, y, width.coerceAtLeast(1), color)
    }

    companion object {
        @JvmStatic
        fun label(key: String, vararg args: Any): Component =
            Component.translatable("sh_better_ui.online_music.comments.$key", *args)
    }
}
