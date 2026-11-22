// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeletonMotion
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.Order
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack
import com.dishanhai.sh_better_ui.kernel.internal.music.ClientOnlineMusic
import com.dishanhai.sh_better_ui.kernel.internal.music.MusicCommentFeeds
import com.muoniumplayer.core.management.FontManager
import com.muoniumplayer.core.screens.ncm.NCMScreen
import com.muoniumplayer.core.screens.ncm.NCMScreen.ColorType
import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box
import com.muoniumplayer.core.screens.ncm.effects.EffectsPaint
import com.muoniumplayer.render.skija.SkijaGfx
import io.github.humbleui.skija.Canvas
import io.github.humbleui.skija.Image
import io.github.humbleui.skija.SamplingMode
import io.github.humbleui.types.RRect
import io.github.humbleui.types.Rect
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW.*
import java.util.Locale
import kotlin.math.roundToInt

/** Right-hand comment drawer inside the embedded player's own Skija rendering pass. */
class MusicCommentsDrawer : AutoCloseable {
    private enum class Target { ENTRY, CLOSE, DEFAULT, HOT, LATEST, LIST, REPLY, PREVIOUS, NEXT, RETRY }
    private data class Row(val line: MusicCommentBrowser.Line, val lines: List<String>, val top: Int) {
        val bodyEnd get() = 29 + lines.size * 15
        val height get() = if (line.entry == null) 24 else bodyEnd + 22 + if (line.action != null) 20 else 0
    }

    private var player: OnlineMusicPlayer? = null
    private var browser: MusicCommentBrowser? = null
    private var paint: EffectsPaint? = null
    private val scroll = ScrollRegionState().apply { setStep(36) }
    private val drawerEntrance = InterruptibleAnimation(Spring.PANEL)
    private val presentation = MusicCommentPresentation()
    private val targets = linkedMapOf<Target, Box>()
    private val replyBoxes = linkedMapOf<Box, MusicCommentBrowser.Line>()
    private var capturedReply: MusicCommentBrowser.Line? = null
    private val avatars = MusicCommentAvatars { bytes -> Image.makeDeferredFromEncodedBytes(bytes) }
    private var entry: Box? = null
    private var track: OnlineTrack? = null
    private var drawer: Box? = null
    private var list: Box? = null
    private var focused: Target? = null
    private var keyboardFocus = false
    private var captured: Target? = null
    private var capturedTrack: OnlineTrack? = null
    private var pointerOwned = false
    private var interactive = false
    private var handCursor = false
    private var rows = emptyList<Row>()
    private var cachedResult: OnlineMusicComments? = null
    private var cachedThread: MusicCommentBrowser.Thread? = null
    private var cachedWidth = 0

    fun beginFrame() { entry = null }
    fun isOpen(): Boolean = browser?.state != null && drawer != null
    fun ownsPointer(): Boolean = pointerOwned
    fun wantsHandCursor(): Boolean = handCursor

    fun position(track: OnlineTrack, x: Double, y: Double) {
        if (this.track != track && captured == Target.ENTRY) cancelPointer()
        browser?.let {
            val current = it.state?.track
            if (current != null && (current.source() != track.source() || current.id() != track.id())) {
                it.open(track)
                presentation.reset()
                scroll.moveTo(0)
            }
        }
        this.track = track
        entry = Box(x, y, 22.0, 20.0)
    }

    fun render(frame: Box, bottom: Double, immersive: Boolean, x: Double, y: Double, held: Boolean, enabled: Boolean) {
        val button = entry
        handCursor = false
        val canvas = SkijaGfx.canvas() ?: return
        if (button == null) {
            interactive = false
            targets.clear()
            drawer = null
            cancelPointer()
            if (enabled && browser?.state != null) dismiss()
            return
        }
        val compact = frame.width() < 390
        val width = if (compact) frame.width() - 20 else (frame.width() * .44).coerceIn(172.0, 310.0)
        val top = frame.y() + 10
        val end = if (immersive && !compact) frame.bottom() - 10 else bottom - 6
        val bounds = Box(frame.right() - width - 10, top, width, end - top)
        if (bounds.width() < 138 || bounds.height() < 108) {
            interactive = false
            targets.clear()
            dismiss()
            return
        }
        interactive = enabled
        if (!enabled) cancelPointer()
        val graphics = paint ?: EffectsPaint().also { paint = it }
        val preferences = UiPreferences.configured()
        val highContrast = preferences.highContrast()
        val ink = color(ColorType.PRIMARY_TEXT)
        val muted = if (highContrast) ink else color(ColorType.SECONDARY_TEXT)
        val accent = if (highContrast) ink else color(ColorType.ACCENT)
        targets.clear()
        replyBoxes.clear()
        targets[Target.ENTRY] = button
        val controller = browser
        val previous = controller?.state
        if (enabled) controller?.poll()
        val state = controller?.state
        if (state != null) {
            val now = now()
            if (drawer == null) {
                drawerEntrance.snapTo(0f)
                drawerEntrance.blendTo(1f, now, 0, 180, InterruptibleAnimation.BlendCurve.SMOOTHSTEP)
            }
            val progress = drawerEntrance.sample(now, preferences.reducedMotion())
            val placed = Box(bounds.x() + 8 * (1 - progress), bounds.y(), bounds.width(), bounds.height())
            drawer = placed
            val layer = if (progress < 1f) canvas.saveLayerAlpha(EffectsPaint.rect(placed),
                (progress * 255).roundToInt()) else null
            try {
                drawDrawer(canvas, graphics, placed, state, controller, x, y, held, ink, muted, accent, preferences, now)
            } finally {
                if (layer != null) canvas.restoreToCount(layer)
            }
            if (state !== previous && focused == Target.LIST && (state.result != null || state.failed)) announce(Target.LIST)
        } else {
            drawer = null
            list = null
            presentation.reset()
        }
        val selected = state != null
        actionChrome(canvas, graphics, Target.ENTRY, button, x, y, selected, accent)
        bubble(canvas, graphics, button, if (selected) accent else ink)
        handCursor = interactive && (button.contains(x, y) ||
            replyAt(x, y) != null || targets.any { (item, box) -> item != Target.LIST && box.contains(x, y) })
        if (interactive && button.contains(x, y)) {
            val title = label(if (selected) "close" else "title").string
            val width = FontManager.pf12.getStringWidthD(title) + 14
            val hint = Box(button.x(), button.y() - 20, width, 18.0)
            graphics.round(canvas, hint, 5.0, color(ColorType.GENERIC_BACKGROUND))
            graphics.text(canvas, FontManager.pf12, title, hint.x() + 7, hint.centerY(), width - 10, ink)
        }
    }

    private fun drawDrawer(canvas: Canvas, paint: EffectsPaint, box: Box, state: MusicCommentBrowser.State,
                           controller: MusicCommentBrowser, x: Double, y: Double, held: Boolean,
                           ink: Int, muted: Int, accent: Int, preferences: UiPreferences, now: Long) {
        val fill = color(ColorType.GENERIC_BACKGROUND)
        paint.round(canvas, box, 9.0, fill)
        if (preferences.highContrast()) paint.outline(canvas, box, 9.0, 1.0, ink)
        val content = box.inset(10.0)
        val compressed = box.height() < 160
        val close = Box(content.right() - 22, content.y(), 22.0, 20.0)
        targets[Target.CLOSE] = close
        actionChrome(canvas, paint, Target.CLOSE, close, x, y, false, accent)
        paint.icon(canvas, EffectsPaint.Icon.CLOSE, close.inset(5.0), ink)
        val heading = state.result?.total?.let { label("count", it) } ?: label("title")
        paint.text(canvas, FontManager.pf16bold, heading.string, content.x(), content.y() + 10, content.width() - 28, ink)
        val track = state.track
        val singer = if (track.artist().isEmpty()) "" else " · ${track.artist()}"
        paint.text(canvas, FontManager.pf12, track.title() + singer, content.x(), content.y() + if (compressed) 23 else 29,
            content.width(), muted)

        val tabY = content.y() + if (compressed) 32 else 42
        val orders = controller.orders
        if (orders.size > 1) {
            val width = (content.width() - 4 * (orders.size - 1)) / orders.size
            orders.forEachIndexed { index, order ->
                val target = Target.valueOf(order.name)
                val tab = Box(content.x() + (width + 4) * index, tabY, width, 20.0)
                chip(canvas, paint, target, tab, x, y, order == state.order, ink, accent)
            }
        }
        val footerY = content.bottom() - 20
        val top = if (orders.size > 1) tabY + 24 else tabY
        val viewport = Box(content.x(), top, content.width(), (footerY - top - 4).coerceAtLeast(1.0))
        list = viewport
        targets[Target.LIST] = viewport
        val textWidth = (viewport.width() - 13).toInt()
        if (cachedResult !== state.result || cachedThread !== controller.thread || cachedWidth != textWidth) {
            rebuild(state.result, controller.thread, textWidth)
        }
        presentation.update(state.result, now)
        val contentHeight = rows.lastOrNull()?.let { it.top + it.height } ?: 0
        val viewHeight = viewport.height().toInt()
        scroll.configure(scroll.offset(), contentHeight, viewHeight)
        val rail = rail(viewport)
        if (scroll.dragging() && held && interactive) scroll.drag(y - rail.y(), viewHeight)
        scroll.advance(now, preferences.reducedMotion() || preferences.highContrast())

        canvas.save()
        try {
            canvas.clipRect(Rect.makeXYWH(viewport.x().toFloat(), viewport.y().toFloat(),
                viewport.width().toFloat(), viewport.height().toFloat()))
            if (rows.isEmpty()) {
                val status = when {
                    controller.loading || state.request -> "loading"
                    state.failed -> "failed"
                    else -> "empty"
                }
                if (status != "loading" || !drawSkeleton(canvas, paint, viewport, ink, preferences, now)) {
                    paint.text(canvas, FontManager.pf14, label(status).string, viewport.x(), viewport.y() + 10,
                        textWidth.toDouble(), ink)
                }
            } else {
                for ((index, row) in rows.withIndex()) {
                    val top = viewport.y() + row.top - scroll.viewportOffset()
                    if (top >= viewport.bottom()) break
                    val rowY = top + presentation.offset(index, now, preferences.reducedMotion())
                    if (rowY + row.height <= viewport.y()) continue
                    val nested = row.line.nested
                    val left = viewport.x() + if (nested) 18 else 4
                    val textX = left + if (nested) 24 else 31
                    val right = viewport.x() + textWidth
                    val item = row.line.entry
                    if (nested) paint.rect(canvas, Box(left - 6, rowY, 1.0, row.height.toDouble()), wash(accent, 0x58))
                    if (item != null) {
                        val size = if (nested) 18.0 else 24.0
                        val author = item.author.ifBlank { label("unknown_author").string }
                        avatar(canvas, paint, Box(left, rowY + 6, size, size), item, author, muted, accent)
                        val width = right - textX
                        val authorLine = item.replyingTo?.let { label("reply_author", author, it).string } ?: author
                        paint.text(canvas, FontManager.pf14bold, authorLine, textX, rowY + 14, width, muted)
                        row.lines.forEachIndexed { index, line ->
                            paint.text(canvas, FontManager.pf14, line, textX, rowY + 34 + index * 15, width, ink)
                        }
                        paint.text(canvas, FontManager.pf12, metadata(item), textX, rowY + row.bodyEnd + 7, width, muted)
                        if (!nested) paint.rect(canvas, Box(textX, rowY + row.height - 2, width, 1.0), wash(muted, 0x36))
                    }
                    val actionY = if (item == null) rowY + 2 else rowY + row.bodyEnd + 16
                    row.line.status?.let {
                        paint.text(canvas, FontManager.pf12, label(it).string, textX, actionY + 9, right - textX, muted)
                    }
                    if (row.line.action != null) {
                        val title = row.line.actionLabel().string
                        val actionWidth = (FontManager.pf12bold.getStringWidthD(title) + 12).coerceAtMost(right - textX)
                        val action = Box(textX, actionY, actionWidth, 18.0)
                        if (action.y() >= viewport.y() && action.bottom() <= viewport.bottom()) {
                            replyBoxes[action] = row.line
                            targets.putIfAbsent(Target.REPLY, action)
                        }
                        if (action.contains(x, y) && viewport.contains(x, y) || capturedReply == row.line) {
                            paint.round(canvas, action, 4.0, wash(accent, 0x38))
                        }
                        if (targets[Target.REPLY] == action) focusMark(canvas, paint, Target.REPLY, action, accent)
                        paint.text(canvas, FontManager.pf12bold, title, textX + 5, action.centerY(), actionWidth - 8, accent)
                    }
                }
            }
        } finally {
            canvas.restore()
        }
        if (interactive && keyboardFocus && focused == Target.LIST && viewport.height() >= 12) {
            val left = viewport.x() - 5
            paint.line(canvas, left, viewport.y() + 4, left, viewport.y() + 14, 1.5, accent)
        }
        if (scroll.maximum() > 0) {
            paint.round(canvas, rail, 2.0, wash(muted, 0x36))
            paint.round(canvas, Box(rail.x(), rail.y() + scroll.thumbTop(viewHeight), rail.width(),
                scroll.thumbHeight(viewHeight).toDouble()), 2.0, if (scroll.dragging()) accent else muted)
        }
        if (state.page > 1) chip(canvas, paint, Target.PREVIOUS, Box(content.x(), footerY, 50.0, 20.0), x, y, false, ink, accent)
        if (state.page < 1000 && state.result?.hasMore == true) {
            chip(canvas, paint, Target.NEXT, Box(content.right() - 50, footerY, 50.0, 20.0), x, y, false, ink, accent)
        }
        val center = Box(content.centerX() - 24, footerY, 48.0, 20.0)
        if (state.failed) chip(canvas, paint, Target.RETRY, center, x, y, false, ink, accent)
        else if (state.page > 1 || state.result?.hasMore == true) {
            paint.text(canvas, FontManager.pf12, label("page", state.page).string, center.x() + 4, center.centerY(), 44.0, muted)
        }
    }

    private fun drawSkeleton(canvas: Canvas, paint: EffectsPaint, viewport: Box, ink: Int,
                             preferences: UiPreferences, now: Long): Boolean {
        val bounds = Bounds(viewport.x().roundToInt(), viewport.y().roundToInt(),
            viewport.width().toInt(), viewport.height().toInt())
        val blocks = MusicCommentPresentation.skeleton(bounds)
        val base = wash(ink, if (preferences.highContrast()) 0x78 else 0x21)
        val peak = wash(ink, if (preferences.highContrast()) 0xa8 else 0x43)
        for (block in blocks) {
            val area = block.bounds()
            val fill = UiSkeletonMotion.fill(bounds, area, now, preferences.reducedMotion(), base, peak, 1f)
            paint.round(canvas, Box(area.x().toDouble(), area.y().toDouble(), area.width().toDouble(),
                area.height().toDouble()), block.cornerRadius().toDouble(), fill)
        }
        return blocks.isNotEmpty()
    }

    private fun avatar(canvas: Canvas, paint: EffectsPaint, box: Box, entry: OnlineMusicComments.Entry,
                       author: String, ink: Int, accent: Int) {
        val image = avatars.get(entry.avatar)
        val rect = EffectsPaint.rect(box)
        if (image == null) {
            paint.round(canvas, box, box.width() / 2, wash(accent, 0x40))
            val initial = author.substring(0, author.offsetByCodePoints(0, 1))
            val width = FontManager.pf12bold.getStringWidthD(initial)
            paint.text(canvas, FontManager.pf12bold, initial, box.centerX() - width / 2, box.centerY(), width + 1, ink)
            return
        }
        val dim = minOf(image.width, image.height).toFloat()
        val source = Rect.makeXYWH((image.width - dim) / 2, (image.height - dim) / 2, dim, dim)
        val saved = canvas.save()
        try {
            canvas.clipRRect(RRect.makeXYWH(rect.left, rect.top, rect.width, rect.height, rect.width / 2), true)
            canvas.drawImageRect(image, source, rect, SamplingMode.LINEAR, null, true)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    fun wheel(x: Double, y: Double, delta: Int): Boolean {
        if (!interactive || delta == 0 || drawer?.contains(x, y) != true) return false
        keyboardFocus = false
        val preferences = UiPreferences.configured()
        if (!scroll.dragging()) scroll.wheel(delta.toDouble() / 120.0, now(), preferences.reducedMotion() || preferences.highContrast())
        return true
    }

    fun press(x: Double, y: Double, button: Int): Boolean {
        if (!interactive || button != 0) return false
        keyboardFocus = false
        val target = hit(x, y)
        if (target == null && drawer?.contains(x, y) != true) {
            focused = null
            return false
        }
        pointerOwned = true
        captured = target
        capturedReply = replyAt(x, y)
        capturedTrack = track
        if (target != null) focused = target
        val viewport = list
        if (target == Target.LIST && viewport != null && rail(viewport).contains(x, y) && scroll.maximum() > 0) {
            scroll.beginDrag(y - viewport.y(), viewport.height().toInt())
        }
        return true
    }

    fun release(x: Double, y: Double, button: Int): Boolean {
        if (button != 0 || !pointerOwned) return false
        pointerOwned = false
        val target = captured
        captured = null
        if (scroll.dragging()) {
            scroll.endDrag()
            announce(Target.LIST)
        } else if (interactive && target != null && hit(x, y) == target
            && (target != Target.ENTRY || capturedTrack == track)) {
            if (target == Target.REPLY) {
                val hit = replyAt(x, y)
                if (hit != null && hit == capturedReply) browser?.activate(hit)
            } else activate(target)
        }
        capturedTrack = null
        capturedReply = null
        return true
    }

    fun key(key: Int, backwards: Boolean): Boolean {
        if (!interactive) return false
        val opened = browser?.state != null && drawer != null
        if (key == GLFW_KEY_ESCAPE && opened) {
            keyboardFocus = true
            if (scroll.dragging()) cancelPointer() else dismiss()
            return true
        }
        if (key == GLFW_KEY_TAB) {
            if (!opened && focused == Target.ENTRY) { focused = null; return false }
            val available = targets.keys.toList()
            if (available.isEmpty()) return false
            val index = available.indexOf(focused)
            val target = if (index == -1 && backwards) available.last()
                else available[Math.floorMod(index + if (backwards) -1 else 1, available.size)]
            focused = target
            keyboardFocus = true
            announce(target)
            return true
        }
        val focus = focused?.takeIf { it in targets } ?: return false
        if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER || key == GLFW_KEY_SPACE && focus != Target.LIST) {
            keyboardFocus = true
            activate(focus)
            return true
        }
        if (!opened || focus != Target.LIST) return false
        val offset = scroll.offset().toLong()
        val next = when (key) {
            GLFW_KEY_UP -> offset - 24
            GLFW_KEY_DOWN -> offset + 24
            GLFW_KEY_PAGE_UP -> offset - scroll.page()
            GLFW_KEY_PAGE_DOWN -> offset + scroll.page()
            GLFW_KEY_HOME -> 0L
            GLFW_KEY_END -> scroll.maximum().toLong()
            else -> return false
        }
        keyboardFocus = true
        if (scroll.moveTo(next)) announce(Target.LIST)
        return true
    }

    private fun activate(target: Target) {
        when (target) {
            Target.ENTRY -> {
                if (browser?.state != null) dismiss()
                else track?.let {
                    val session = player ?: ClientOnlineMusic.openCommentReader().also { player = it }
                    val controller = browser ?: MusicCommentBrowser(session).also { browser = it }
                    controller.open(it)
                    focused = Target.LIST
                    OnlineMusicViewPaint.narrate(label("loading"))
                }
            }
            Target.CLOSE -> dismiss()
            Target.LIST -> announce(target)
            Target.REPLY -> replyBoxes.values.firstOrNull()?.let { browser?.activate(it) }
            Target.HOT, Target.LATEST, Target.DEFAULT -> {
                val order = Order.valueOf(target.name)
                if (browser?.state?.order != order) {
                    browser?.select(order)
                    presentation.reset()
                }
                scroll.moveTo(0)
            }
            Target.PREVIOUS, Target.NEXT, Target.RETRY -> browser?.state?.let {
                if (target == Target.NEXT && it.result?.hasMore != true || target == Target.RETRY && !it.failed) return
                browser?.load(it.page + when (target) { Target.PREVIOUS -> -1; Target.NEXT -> 1; else -> 0 })
                presentation.reset()
                scroll.moveTo(0)
                focused = Target.LIST
            }
        }
    }

    fun dismiss() {
        browser?.clear()
        presentation.reset()
        focused = if (entry == null) null else Target.ENTRY
        drawer = null
        list = null
        targets.keys.retainAll(setOf(Target.ENTRY))
        cancelPointer()
        replyBoxes.clear()
        avatars.close()
        rebuild(null, null, 0)
    }

    private fun cancelPointer() {
        captured = null
        capturedReply = null
        capturedTrack = null
        pointerOwned = false
        scroll.cancelDrag()
        scroll.freezeMotion()
    }

    override fun close() {
        dismiss()
        player?.close()
        player = null
        browser = null
        paint?.close()
        paint = null
        track = null
        entry = null
        targets.clear()
        focused = null
        interactive = false
    }

    private fun rebuild(result: OnlineMusicComments?, thread: MusicCommentBrowser.Thread?, width: Int) {
        cachedResult = result
        cachedThread = thread
        cachedWidth = width
        var y = 0
        rows = browser?.lines().orEmpty().map { line ->
            val wrapped = line.entry?.content?.split('\n').orEmpty().flatMap { paragraph ->
                if (paragraph.isEmpty()) listOf("") else buildList {
                    var text = paragraph
                    while (text.isNotEmpty()) {
                        val fit = FontManager.pf14.trim(text, (width - if (line.nested) 42 else 35).coerceAtLeast(1).toDouble())
                        val length = fit.length.coerceIn(0, text.length)
                        val end = if (length > 0) length else text.offsetByCodePoints(0, 1)
                        val safe = if (end < text.length && text[end - 1].isHighSurrogate()) end + 1 else end
                        add(text.take(safe))
                        text = text.substring(safe).trimStart()
                    }
                }
            }
            Row(line, wrapped, y).also { y += it.height }
        }
    }

    private fun announce(target: Target) {
        val message = if (target == Target.LIST) {
            rows.firstOrNull { it.top + it.height > scroll.offset() }?.line?.entry?.let {
                label("read", it.author.ifBlank { label("unknown_author").string }, it.content, metadata(it))
            } ?: label(if (browser?.loading == true) "loading" else if (browser?.state?.failed == true) "failed" else "empty")
        } else if (target == Target.REPLY) replyBoxes.values.firstOrNull()?.actionLabel() ?: label("view_replies")
        else targetLabel(target)
        OnlineMusicViewPaint.narrate(message)
    }

    private fun chip(canvas: Canvas, paint: EffectsPaint, target: Target, box: Box, x: Double, y: Double,
                     selected: Boolean, ink: Int, accent: Int) {
        targets[target] = box
        actionChrome(canvas, paint, target, box, x, y, selected, accent)
        val name = targetLabel(target).string
        val width = FontManager.pf12bold.getStringWidthD(name).coerceAtMost(box.width() - 6)
        paint.text(canvas, FontManager.pf12bold, name, box.centerX() - width / 2, box.centerY(), width + 2,
            if (selected) accent else ink)
    }

    private fun actionChrome(canvas: Canvas, paint: EffectsPaint, target: Target, box: Box, x: Double,
                             y: Double, selected: Boolean, accent: Int) {
        val hovered = interactive && box.contains(x, y)
        if (selected || hovered || captured == target) {
            paint.round(canvas, box, 5.0, wash(accent, if (captured == target) 0x56 else 0x28))
        }
        focusMark(canvas, paint, target, box, accent)
    }

    private fun focusMark(canvas: Canvas, paint: EffectsPaint, target: Target, box: Box, accent: Int) {
        if (interactive && keyboardFocus && focused == target) {
            val y = box.bottom() - 2
            paint.line(canvas, box.centerX() - 4, y, box.centerX() + 4, y, 1.5, accent)
        }
    }

    private fun bubble(canvas: Canvas, paint: EffectsPaint, box: Box, color: Int) {
        val x = box.centerX() - 6.5
        val y = box.centerY() - 5.5
        paint.outline(canvas, Box(x, y, 13.0, 10.0), 3.0, 1.2, color)
        paint.line(canvas, x + 3, y + 10, x + 2, y + 12, 1.2, color)
        paint.line(canvas, x + 3.5, y + 4, x + 9.5, y + 4, 1.0, color)
        paint.line(canvas, x + 3.5, y + 6.5, x + 7.5, y + 6.5, 1.0, color)
    }

    private fun replyAt(x: Double, y: Double): MusicCommentBrowser.Line? =
        replyBoxes.entries.firstOrNull { it.key.contains(x, y) }?.value
    private fun hit(x: Double, y: Double): Target? = if (replyAt(x, y) != null) Target.REPLY
        else targets.entries.lastOrNull { it.key != Target.REPLY && it.value.contains(x, y) }?.key
    private fun rail(view: Box) = Box(view.right() - 5, view.y(), 5.0, view.height())
    private fun color(key: ColorType): Int = NCMScreen.getColor(key) or 0xff000000.toInt()
    private fun wash(color: Int, opacity: Int): Int = (color and 0xffffff) or (opacity shl 24)
    private fun now(): Long = System.nanoTime() / 1_000_000L
    private fun targetLabel(target: Target) = label(if (target == Target.ENTRY) "title" else target.name.lowercase(Locale.ROOT))
    private fun label(key: String, vararg args: Any): Component = MusicCommentsPanel.label(key, *args)
    private fun metadata(entry: OnlineMusicComments.Entry): String = listOfNotNull(entry.date?.toString(),
        entry.likes?.let { label("likes", it).string }).joinToString(" · ")

    companion object {
        private val qqMid = Regex("[A-Za-z0-9]{1,40}")

        @JvmStatic
        fun reference(source: String, id: String?, mid: String?, kugouId: String?, title: String, artist: String): OnlineTrack? {
            val identity = when (source) {
                "netease" -> id?.takeIf(MusicCommentFeeds::numericId)
                "qq" -> id?.takeIf(MusicCommentFeeds::numericId) ?: mid?.takeIf(qqMid::matches)
                "kugou" -> kugouId?.takeIf(MusicCommentFeeds::numericId)
                else -> null
            } ?: return null
            return OnlineTrack(source, identity, identity, title, artist, "", 0)
        }
    }
}
