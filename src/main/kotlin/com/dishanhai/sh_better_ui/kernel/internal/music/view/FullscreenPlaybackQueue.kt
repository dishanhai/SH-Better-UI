// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view

import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring
import com.muoniumplayer.core.management.FontManager
import com.muoniumplayer.core.ncm.music.AutomixSettings
import com.muoniumplayer.core.ncm.music.CloudMusic
import com.muoniumplayer.core.ncm.music.CloudMusic.PlayMode
import com.muoniumplayer.core.ncm.music.dto.Music
import com.muoniumplayer.core.rendering.FoliaLucideIcons
import com.muoniumplayer.core.rendering.MusicCapsuleCard
import com.muoniumplayer.core.rendering.PlayerQueueIcons
import com.muoniumplayer.core.rendering.ui.widgets.RoundedImageWidget
import com.muoniumplayer.core.screens.ncm.NCMScreen
import com.muoniumplayer.core.screens.ncm.NCMScreen.ColorType
import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box
import com.muoniumplayer.core.screens.ncm.effects.EffectsPaint
import com.muoniumplayer.core.screens.ncm.panels.PlayQueuePanel
import com.muoniumplayer.core.utils.Location
import com.muoniumplayer.party.ui.PartyIcons
import com.muoniumplayer.render.skija.SkijaGfx
import com.muoniumplayer.render.skija.SkijaEffects
import io.github.humbleui.skija.Canvas
import io.github.humbleui.types.Rect
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW.*
import java.util.function.Function
import java.util.function.Supplier
import kotlin.math.abs

/** The right column of the embedded fullscreen player, sharing its artwork and transport. */
internal class FullscreenPlaybackQueue(private val title: Function<Music, String>) : AutoCloseable {
    private enum class Target { ENTRY, SHUFFLE, REPEAT, AUTOPLAY, AUTOMIX, ENCYCLOPEDIA, LIST }

    private val targets = linkedMapOf<Target, Box>()
    private val queueScroll = ScrollRegionState().apply { setStep(42) }
    private val notesScroll = ScrollRegionState().apply { setStep(32) }
    private val encyclopedia = FullscreenSongEncyclopedia()
    private var showingNotes = false
    private val scroll get() = if (showingNotes) notesScroll else queueScroll
    private val reveal = InterruptibleAnimation(Spring.PANEL)
    private var paint: EffectsPaint? = null
    private val icon = PlayerQueueIcons.newPlayQueueButton(ENTRY_SIZE)
    private var coverLocation: Location? = null
    private val cover = RoundedImageWidget(Supplier { coverLocation }, 0.0, 0.0, 0.0, 0.0)
    private val requestedCovers = linkedSetOf<Location>()
    private var focus: Target? = null
    private var keyboardHints = false
    private var lastPointerX = Double.NaN
    private var lastPointerY = Double.NaN
    private var interactive = false
    private var handCursor = false
    private var rail: Box? = null
    private var currentTrack: Music? = null
    private var upcoming: CloudMusic.UpcomingPlayback? = null
    private var roomSongs: List<Music>? = null
    private var mode = PlayMode.Sequential

    var isOpen = false
        private set

    fun advance() {
        reveal.sample(nowMs(), UiPreferences.configured().reducedMotion())
    }

    fun lyricsOpacity(): Float = (1f - reveal.value() / .62f).coerceIn(0f, 1f)

    fun blocksLyricInput(): Boolean = isOpen || reveal.running()

    fun render(column: Box, entry: Box, panelLeft: Double, playing: Music?, room: List<Music>?,
               mouseX: Double, mouseY: Double,
               wheel: Int, alpha: Float, enabled: Boolean, pointerDown: Boolean) {
        targets.clear()
        handCursor = false
        rail = null
        interactive = enabled && alpha > .004f && column.width() >= 54 && column.height() >= 36
        if (!interactive) scroll.cancelDrag()
        val canvas = SkijaGfx.canvas() ?: return
        if (mouseX.isFinite() && mouseY.isFinite()) {
            if (lastPointerX != mouseX || lastPointerY != mouseY) keyboardHints = false
            lastPointerX = mouseX
            lastPointerY = mouseY
        }
        if (alpha <= .004f || column.width() < 54 || column.height() < 36) return
        val graphics = paint ?: EffectsPaint().also { paint = it }
        val preferences = UiPreferences.configured()
        val ink = color(ColorType.PRIMARY_TEXT, alpha)
        val accent = if (preferences.highContrast()) ink else color(ColorType.ACCENT, alpha)
        targets[Target.ENTRY] = entry
        val hover = interactive && entry.contains(mouseX, mouseY)
        val pressed = hover && pointerDown
        handCursor = hover
        if (interactive && keyboardHints && focus == Target.ENTRY) {
            graphics.line(canvas, entry.centerX() - 4, entry.bottom(), entry.centerX() + 4, entry.bottom(), 1.5, accent)
        }
        icon.setPosition(entry.x(), entry.y())
        icon.setAlpha(alpha)
        icon.setColor(when {
            pressed -> color(ColorType.PRIMARY_TEXT, alpha * .72f)
            isOpen || hover -> accent
            else -> ink
        })
        icon.renderWidget(Double.NaN, Double.NaN, 0)
        if (hover) {
            val text = label(if (isOpen) "back_to_lyrics" else "title").string
            val width = minOf(column.width() - 36, FontManager.pf12bold.getStringWidthD(text) + 14)
            val hint = Box(maxOf(panelLeft + 4, entry.centerX() - width * .5), entry.y() - 24, width, 20.0)
            graphics.round(canvas, hint, 5.0, color(ColorType.GENERIC_BACKGROUND, alpha))
            graphics.text(canvas, FontManager.pf12bold, text, hint.x() + 6, hint.centerY(), width - 12, ink)
        }
        val progress = reveal.value()
        val opacity = ((progress - .26f) / .74f).coerceIn(0f, 1f) * alpha
        if (opacity <= .004f) return
        val canUseColumn = interactive && isOpen && !reveal.running()
        val saved = canvas.save()
        try {
            canvas.clipRect(Rect.makeXYWH(column.x().toFloat(), column.y().toFloat(),
                column.width().toFloat(), column.height().toFloat()))
            canvas.translate(0f, 16f * (1f - progress))
            renderColumn(canvas, graphics, column, playing, room, mouseX, mouseY, wheel, opacity,
                preferences, canUseColumn, pointerDown)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun renderColumn(canvas: Canvas, graphics: EffectsPaint, column: Box, playing: Music?,
                             room: List<Music>?, mouseX: Double, mouseY: Double, wheel: Int, alpha: Float,
                             preferences: UiPreferences, canUseColumn: Boolean, pointerDown: Boolean) {
        val ink = color(ColorType.PRIMARY_TEXT, alpha)
        val secondary = if (preferences.highContrast()) ink else color(ColorType.SECONDARY_TEXT, alpha)
        val accent = if (preferences.highContrast()) ink else color(ColorType.ACCENT, alpha)
        val changed = isOpen && currentTrack?.stableKey != playing?.stableKey
        if (isOpen) {
            currentTrack = playing
            roomSongs = room
            upcoming = if (room == null) CloudMusic.upcomingPlayback(playing) else null
            mode = upcoming?.mode() ?: CloudMusic.playMode
        }
        val track = currentTrack
        if (changed) queueScroll.moveTo(0)
        if (showingNotes && track == null) toggleNotes()
        val next = roomSongs ?: upcoming?.next().orEmpty()
        val radio = upcoming?.radio().orEmpty()
        val left = column.x() + 7
        val width = column.width() - 14
        val top = column.y() + 6
        val hasModes = width >= 92 && column.height() >= 108
        val locked = CloudMusic.isFullscreenQueueModeLocked()
        val headerHeight = if (column.height() >= 115) 46.0 else 27.0
        val listTop = top + headerHeight + if (hasModes) 33.0 else 0.0
        val viewport = Box(left, listTop, width, (column.bottom() - listTop - 6).coerceAtLeast(0.0))
        val radioOnly = radio.isNotEmpty() && next.isEmpty()
        val nextHeight = if (radioOnly) 0 else sectionHeight(next.size)
        val radioTop = nextHeight + if (radioOnly) 0 else 8
        if (showingNotes && track != null) encyclopedia.update(track, (width - 9).toInt())
        val docHeight = when {
            showingNotes -> NOTES_HEADING + encyclopedia.lines.size * NOTE_LINE
            radio.isEmpty() -> nextHeight
            else -> radioTop + sectionHeight(radio.size)
        }
        scroll.configure(if (changed) 0 else scroll.offset(), docHeight, viewport.height().toInt())
        val now = nowMs()
        val reduce = preferences.reducedMotion() || preferences.highContrast()
        if (viewport.height() > 0) {
            if (canUseColumn && viewport.contains(mouseX, mouseY) && wheel != 0) {
                scroll.wheel(wheel.toDouble() / 120, now, reduce)
            }
            scroll.advance(now, reduce)
            if (scroll.maximum() > 0) {
                val track = Box(viewport.right() - 8, viewport.y(), 8.0, viewport.height())
                if (canUseColumn) rail = track
                handCursor = handCursor || canUseColumn && track.contains(mouseX, mouseY)
                if (scroll.dragging()) {
                    if (pointerDown) scroll.drag(mouseY - track.y(), track.height().toInt()) else scroll.endDrag()
                }
            } else scroll.endDrag()
        }

        if (track == null || headerHeight < 40) {
            graphics.text(canvas, FontManager.pf14bold, label("title").string, left, top + 10, width, ink)
        } else {
            val coverBounds = Box(left, top, 36.0, 36.0)
            drawCover(canvas, graphics, track, coverBounds, alpha)
            graphics.text(canvas, FontManager.pf18bold, title.apply(track), left + 45, top + 10, width - 45, ink)
            graphics.text(canvas, FontManager.pf12, track.artistsName, left + 45, top + 26, width - 45, secondary)
        }
        var hoveredControl: Pair<Target, Box>? = null
        if (hasModes) {
            val gap = 6.0
            val buttonWidth = (width - gap * (MODE_CONTROLS.size - 1)) / MODE_CONTROLS.size
            if (preferences.useBackdropBlur()) {
                SkijaEffects.backdropBlurRounded(left, top + headerHeight, width, 25.0, 8.0, 2f, alpha)
            }
            for ((index, target) in MODE_CONTROLS.withIndex()) {
                val box = Box(left + index * (buttonWidth + gap), top + headerHeight, buttonWidth, 25.0)
                val available = if (target == Target.ENCYCLOPEDIA) track != null
                    else !locked && (target != Target.AUTOPLAY || CloudMusic.canQueueAutoplay(track))
                val selected = when (target) {
                    Target.SHUFFLE -> mode.isShuffled
                    Target.REPEAT -> mode.repeatsOne() || mode.repeatsAll()
                    Target.AUTOPLAY -> CloudMusic.isQueueAutoplayEnabled()
                    Target.AUTOMIX -> AutomixSettings.isEnabled()
                    Target.ENCYCLOPEDIA -> showingNotes
                    else -> false
                }
                val hover = canUseColumn && box.contains(mouseX, mouseY)
                if (hover) hoveredControl = target to box
                if (canUseColumn && available) targets[target] = box
                handCursor = handCursor || hover && available
                modeButton(canvas, graphics, target, box, selected, hover && available,
                    hover && available && pointerDown, available, ink, accent, alpha)
                if (keyboardHints && canUseColumn && focus == target && available) {
                    graphics.line(canvas, box.centerX() - 4, box.y() + 1, box.centerX() + 4, box.y() + 1, 1.5, accent)
                }
            }
        }
        if (canUseColumn && viewport.height() > 0) targets[Target.LIST] = viewport
        if (canUseColumn && focus != null && focus !in targets) focus = Target.ENTRY
        if (viewport.height() <= 0) return
        val saved = canvas.save()
        try {
            canvas.clipRect(Rect.makeXYWH(viewport.x().toFloat(), viewport.y().toFloat(),
                viewport.width().toFloat(), viewport.height().toFloat()))
            val start = listTop - scroll.viewportOffset()
            if (showingNotes) {
                graphics.text(canvas, FontManager.pf18bold, FullscreenSongEncyclopedia.label("title").string,
                    left, start + 10, width - 9, ink)
                val first = ((viewport.y() - start - NOTES_HEADING) / NOTE_LINE).toInt().coerceAtLeast(0)
                val end = minOf(encyclopedia.lines.size, first + (viewport.height() / NOTE_LINE).toInt() + 2)
                for (i in first until end) {
                    graphics.text(canvas, FontManager.pf14, encyclopedia.lines[i], left,
                        start + NOTES_HEADING + i * NOTE_LINE + NOTE_LINE / 2, width - 9, secondary)
                }
            } else {
                val source = upcoming?.source()?.takeIf { it.isNotBlank() }
                if (!radioOnly) drawSection(canvas, graphics, viewport, start,
                    label(if (roomSongs != null) "room" else "up_next"), source?.let { label("from", it) },
                    next, ink, secondary, alpha)
                if (radio.isNotEmpty()) {
                    drawSection(canvas, graphics, viewport, start + radioTop,
                        label("radio"), label("radio_detail"), radio, ink, secondary, alpha)
                } else if (next.isEmpty()) {
                    val empty = when {
                        mode.repeatsOne() && !locked && track != null -> "repeat_current"
                        mode == PlayMode.Random && hasModes && track != null -> "shuffle_next_round"
                        else -> "empty"
                    }
                    graphics.text(canvas, FontManager.pf12, label(empty).string, left, start + 45, width - 7, secondary)
                }
            }
        } finally {
            canvas.restoreToCount(saved)
        }
        if (scroll.maximum() > 0) {
            val height = viewport.height().toInt()
            val thumb = Box(viewport.right() - 3, viewport.y() + scroll.thumbTop(height),
                3.0, scroll.thumbHeight(height).toDouble())
            graphics.round(canvas, thumb, 1.5, secondary)
        }
        if (keyboardHints && canUseColumn && focus == Target.LIST && viewport.height() >= 15) {
            graphics.line(canvas, left - 4, listTop + 5, left - 4, listTop + 15, 1.5, accent)
        }
        val focusedControl = focus?.takeIf { keyboardHints && canUseColumn && it in MODE_CONTROLS }
        val hintControl = if (keyboardHints) focusedControl?.let { target -> targets[target]?.let { target to it } }
            else hoveredControl
        hintControl?.let { (target, box) ->
            val text = if (locked && target != Target.ENCYCLOPEDIA) label("mode_locked") else modeLabel(target)
            val font = FontManager.pf12bold
            val caption = text.string
            val bounds = font.getGlyphBounds(caption)
            val inkWidth = bounds?.let { it.right - minOf(0f, it.left) } ?: 0f
            val hintWidth = minOf(width, maxOf(font.getStringWidthD(caption), inkWidth.toDouble()) + 18)
            val lines = if (hintWidth < width || graphics.fit(font, caption, width - 14) == caption)
                arrayOf(caption) else graphics.lines(font, caption, width - 16)
            val step = maxOf(12.0, font.getFontHeight() + 2)
            val hintHeight = maxOf(20.0, lines.size * step + 6)
            val hintX = (box.centerX() - hintWidth / 2).coerceIn(left, left + width - hintWidth)
            val hint = Box(hintX, maxOf(column.y(), box.y() - hintHeight - 3), hintWidth, hintHeight)
            graphics.round(canvas, hint, 5.0, color(ColorType.GENERIC_BACKGROUND, alpha))
            lines.forEachIndexed { i, line ->
                graphics.text(canvas, font, line, hint.x() + 7,
                    hint.centerY() + (i - (lines.size - 1) * .5) * step, hint.width() - 14, ink)
            }
        }
    }

    private fun drawSection(canvas: Canvas, graphics: EffectsPaint, viewport: Box, y: Double,
                            heading: Component, detail: Component?, songs: List<Music>,
                            ink: Int, secondary: Int, alpha: Float) {
        val width = viewport.width() - 8
        if (y + HEADING > viewport.y() && y < viewport.bottom()) {
            graphics.text(canvas, FontManager.pf18bold, heading.string, viewport.x(), y + 11, width, ink)
            if (detail != null) graphics.text(canvas, FontManager.pf12, detail.string,
                viewport.x(), y + 27, width, secondary)
        }
        val start = y + HEADING
        val first = ((viewport.y() - start) / ROW).toInt().coerceAtLeast(0)
        val end = (((viewport.bottom() - start) / ROW).toInt() + 1).coerceIn(0, songs.size)
        for (i in first until end) {
            val song = songs[i]
            val rowY = start + ROW * i
            val duration = if (width >= 220 && song.duration > 0) MusicCapsuleCard.formatTime(song.duration) else null
            val timeWidth = if (duration == null) 0.0 else FontManager.pf12.getStringWidthD(duration) + 3
            val textWidth = width - 36 - if (duration == null) 0.0 else timeWidth + 10
            drawCover(canvas, graphics, song, Box(viewport.x(), rowY + 3, 28.0, 28.0), alpha)
            graphics.text(canvas, FontManager.pf14bold, title.apply(song), viewport.x() + 36,
                rowY + 10, textWidth, ink)
            graphics.text(canvas, FontManager.pf12, song.artistsName, viewport.x() + 36,
                rowY + 24, textWidth, secondary)
            if (duration != null) graphics.text(canvas, FontManager.pf12, duration,
                viewport.x() + width - timeWidth, rowY + ROW * .5, timeWidth, secondary)
        }
    }

    private fun drawCover(canvas: Canvas, graphics: EffectsPaint, track: Music, box: Box, alpha: Float) {
        val location = track.smallCoverLocation
        if (location !in requestedCovers && PlayQueuePanel.loadQueueCover(track)) {
            requestedCovers.add(location)
            if (requestedCovers.size > 128) requestedCovers.remove(requestedCovers.first())
        }
        graphics.round(canvas, box, 5.0, color(ColorType.ELEMENT_BACKGROUND, alpha * .7f))
        coverLocation = PlayQueuePanel.queueCoverLocation(track)
        cover.setBounds(box.x(), box.y(), box.width(), box.height())
        cover.setRadius(5.0)
        cover.setLinearFilter(true)
        cover.setAlpha(alpha)
        cover.onRender(0.0, 0.0)
    }

    private fun modeButton(canvas: Canvas, graphics: EffectsPaint, target: Target, box: Box,
                           selected: Boolean, hover: Boolean, pressed: Boolean, available: Boolean,
                           ink: Int, accent: Int, alpha: Float) {
        val radius = 8.0
        if (hover) {
            graphics.round(canvas, box, radius, color(ColorType.PRIMARY_TEXT, alpha * if (pressed) .12f else .055f))
        }
        if (selected && available) {
            graphics.round(canvas, Box(box.centerX() - 3.0, box.bottom() - 3.0, 6.0, 1.5), .75, accent)
        }
        val iconInk = when {
            !available -> color(ColorType.SECONDARY_TEXT, alpha * .65f)
            selected -> accent
            else -> ink
        }
        when (target) {
            Target.SHUFFLE, Target.REPEAT -> {
                val glyph = when {
                    target == Target.SHUFFLE -> PartyIcons.SHUFFLE
                    mode.repeatsOne() -> PartyIcons.REPEAT_ONE
                    else -> PartyIcons.REPEAT
                }
                graphics.buttonLabel(canvas, FontManager.partyIcon32, glyph, null, box, iconInk)
            }
            Target.AUTOPLAY -> FoliaLucideIcons.draw(FoliaLucideIcons.INFINITY,
                box.centerX(), box.centerY(), 16.0, iconInk)
            Target.AUTOMIX -> FoliaLucideIcons.draw(FoliaLucideIcons.AUDIO_LINES,
                box.centerX(), box.centerY(), 16.0, iconInk)
            Target.ENCYCLOPEDIA -> FoliaLucideIcons.draw(FoliaLucideIcons.INFO,
                box.centerX(), box.centerY(), minOf(16.0, box.width()), iconInk)
            else -> Unit
        }
    }

    fun blocksLyrics(x: Double, y: Double): Boolean = blocksLyricInput() || targets[Target.ENTRY]?.contains(x, y) == true
    fun wantsHandCursor(): Boolean = handCursor

    fun pressScrollbar(x: Double, y: Double): Boolean {
        val track = rail ?: return false
        if (!interactive || !isOpen || reveal.running() || !track.contains(x, y)
            || !scroll.beginDrag(y - track.y(), track.height().toInt())) return false
        keyboardHints = false
        focus = Target.LIST
        return true
    }

    fun releaseScrollbar(): Boolean {
        if (!scroll.dragging()) return false
        scroll.endDrag()
        return true
    }

    fun click(x: Double, y: Double, button: Int): Boolean {
        if (!interactive) return false
        val target = targets.entries.firstOrNull { (target, box) ->
            box.contains(x, y) && (target == Target.ENTRY || isOpen && !reveal.running())
        }?.key ?: return false
        if (button == 0) {
            keyboardHints = false
            focus = target
            activate(target)
        }
        return true
    }

    fun focusEntry(): Boolean {
        if (!interactive || Target.ENTRY !in targets) return false
        keyboardHints = true
        focus = Target.ENTRY
        announce()
        return true
    }

    fun clearFocus() { focus = null }
    fun entryFocused(): Boolean = focus == Target.ENTRY

    fun key(key: Int, backwards: Boolean): Boolean {
        if (isOpen && key == GLFW_KEY_ESCAPE) {
            if (scroll.dragging()) {
                scroll.cancelDrag()
                return true
            }
            if (showingNotes) {
                toggleNotes()
                keyboardHints = true
                focus = Target.ENCYCLOPEDIA
                announce()
                return true
            }
            setOpen(false)
            focusEntry()
            return true
        }
        if (!interactive) return false
        if (isOpen && key == GLFW_KEY_TAB) {
            val order = targets.keys.toList()
            if (order.isEmpty()) return false
            val index = order.indexOf(focus)
            keyboardHints = true
            focus = order[Math.floorMod(index + if (backwards) -1 else 1, order.size)]
            announce()
            return true
        }
        val target = focus ?: return false
        if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER || key == GLFW_KEY_SPACE) {
            keyboardHints = true
            activate(target)
            return true
        }
        if (!isOpen || target != Target.LIST) return false
        val offset = scroll.offset().toLong()
        val next = when (key) {
            GLFW_KEY_UP -> offset - ROW
            GLFW_KEY_DOWN -> offset + ROW
            GLFW_KEY_PAGE_UP -> offset - scroll.page()
            GLFW_KEY_PAGE_DOWN -> offset + scroll.page()
            GLFW_KEY_HOME -> 0L
            GLFW_KEY_END -> scroll.maximum().toLong()
            else -> return false
        }
        keyboardHints = true
        if (scroll.moveTo(next)) announce()
        return true
    }

    private fun activate(target: Target) {
        when (target) {
            Target.ENTRY -> {
                setOpen(!isOpen)
            }
            Target.SHUFFLE, Target.REPEAT -> CloudMusic.toggleFullscreenQueueMode(target == Target.SHUFFLE)
            Target.AUTOPLAY -> CloudMusic.toggleQueueAutoplay()
            Target.AUTOMIX -> if (!CloudMusic.isFullscreenQueueModeLocked()) {
                AutomixSettings.setEnabled(!AutomixSettings.isEnabled())
            }
            Target.LIST -> Unit
            Target.ENCYCLOPEDIA -> if (currentTrack != null) toggleNotes()
        }
        mode = CloudMusic.playMode
        announce()
    }

    private fun toggleNotes() {
        scroll.cancelDrag()
        showingNotes = !showingNotes
        rail = null
        notesScroll.moveTo(0)
        encyclopedia.clear()
    }

    private fun setOpen(open: Boolean) {
        val now = nowMs()
        val from = reveal.sample(now)
        val to = if (open) 1f else 0f
        isOpen = open
        if (!open && showingNotes) toggleNotes()
        scroll.cancelDrag()
        if (open && from == 0f) scroll.moveTo(0)
        if (UiPreferences.configured().reducedMotion()) {
            reveal.snapTo(to)
        } else {
            val duration = if (open) 280 else 220
            reveal.blendTo(to, now, 0, (duration * abs(to - from)).toLong().coerceAtLeast(80),
                InterruptibleAnimation.BlendCurve.SMOOTHSTEP)
        }
        targets.keys.removeIf { it != Target.ENTRY }
        rail = null
    }

    private fun announce() {
        val message = when (val target = focus) {
            Target.ENTRY -> label(if (isOpen) "back_to_lyrics" else "title")
            Target.SHUFFLE, Target.REPEAT, Target.AUTOPLAY, Target.AUTOMIX, Target.ENCYCLOPEDIA -> modeLabel(target)
            Target.LIST -> {
                if (showingNotes) {
                    val index = ((scroll.offset() - NOTES_HEADING) / NOTE_LINE).coerceAtLeast(0)
                    OnlineMusicViewPaint.narrate(Component.literal(encyclopedia.lines.drop(index)
                        .take((scroll.page() / NOTE_LINE).coerceAtLeast(1)).joinToString(" ")))
                    return
                }
                val next = roomSongs ?: upcoming?.next().orEmpty()
                val radio = upcoming?.radio().orEmpty()
                val offset = scroll.offset()
                val radioTop = if (next.isEmpty()) 0 else sectionHeight(next.size) + 8
                val song = if (offset >= radioTop && radio.isNotEmpty()) {
                    radio.getOrNull(((offset - radioTop - HEADING) / ROW).coerceAtLeast(0))
                } else next.getOrNull(((offset - HEADING) / ROW).coerceAtLeast(0)) ?: radio.firstOrNull()
                if (song != null) label("read", title.apply(song), song.artistsName) else label("empty")
            }
            null -> return
        }
        OnlineMusicViewPaint.narrate(message)
    }

    private fun modeLabel(target: Target): Component {
        if (target == Target.ENCYCLOPEDIA) return FullscreenSongEncyclopedia.label(if (showingNotes) "back" else "title")
        return label(when (target) {
            Target.SHUFFLE -> if (mode.isShuffled) "shuffle_on" else "shuffle_off"
            Target.REPEAT -> when {
                mode.repeatsOne() -> "repeat_one"
                mode.repeatsAll() -> "repeat_all"
                else -> "repeat_off"
            }
            Target.AUTOPLAY -> when {
                !CloudMusic.canQueueAutoplay(currentTrack) -> "autoplay_unavailable"
                !CloudMusic.isQueueAutoplayEnabled() -> "autoplay_off"
                mode.repeatsOne() || mode.repeatsAll() -> "autoplay_repeat"
                else -> "autoplay_on"
            }
            Target.AUTOMIX -> if (AutomixSettings.isEnabled()) "automix_on" else "automix_off"
            else -> "title"
        })
    }

    private fun label(key: String, vararg args: Any): Component =
        Component.translatable("sh_better_ui.muonium.fullscreen_queue." + key, *args)

    private fun color(kind: ColorType, opacity: Float): Int =
        (NCMScreen.getColor(kind) and 0xffffff) or ((255 * opacity.coerceIn(0f, 1f)).toInt() shl 24)

    override fun close() {
        encyclopedia.clear()
        reveal.close()
        paint?.close()
        paint = null
        requestedCovers.clear()
    }

    companion object {
        const val ENTRY_SIZE = 26.0
        const val ENTRY_GAP = 4.0
        private val MODE_CONTROLS = listOf(Target.SHUFFLE, Target.REPEAT, Target.AUTOPLAY, Target.AUTOMIX, Target.ENCYCLOPEDIA)
        private const val NOTES_HEADING = 26
        private const val NOTE_LINE = 18
        private const val HEADING = 36
        private const val ROW = 36
        private fun nowMs(): Long = System.nanoTime() / 1_000_000L
        private fun sectionHeight(size: Int): Int = HEADING + maxOf(1, size) * ROW
    }
}
