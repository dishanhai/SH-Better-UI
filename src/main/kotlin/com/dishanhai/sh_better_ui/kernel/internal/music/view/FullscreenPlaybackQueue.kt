// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring
import com.dishanhai.sh_better_ui.client.gui.scaled.SpringTable
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeletonMotion
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
    private enum class Target { ENTRY, SHUFFLE, REPEAT, AUTOPLAY, AUTOMIX, ENCYCLOPEDIA, REVIEW, LIST }

    private val targets = linkedMapOf<Target, Box>()
    private val queueScroll = ScrollRegionState().apply { setStep(42) }
    private val notesScroll = ScrollRegionState().apply { setStep(32) }
    private val encyclopedia = FullscreenSongEncyclopedia()
    private var showingNotes = false
    private val scroll get() = if (showingNotes) notesScroll else queueScroll
    private val reveal = InterruptibleAnimation(Spring.PANEL)
    private val contentReveal = InterruptibleAnimation(Spring.PANEL, 1f)
    private val hoverMotion = SpringTable<Target>(Spring.HOVER)
    private var pressedAction: Target? = null
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
    private var queueLoading = false

    var isOpen = false
        private set

    fun advance() {
        reveal.sample(nowMs(), UiPreferences.configured().reducedMotion())
    }

    fun lyricsOpacity(): Float = (1f - reveal.value() / .62f).coerceIn(0f, 1f)

    fun blocksLyricInput(): Boolean = isOpen || reveal.running()

    fun render(column: Box, entry: Box, panelLeft: Double, playing: Music?, preparing: Boolean, room: List<Music>?,
               mouseX: Double, mouseY: Double,
               wheel: Int, alpha: Float, enabled: Boolean, pointerDown: Boolean) {
        targets.clear()
        handCursor = false
        val previousRail = rail
        rail = null
        interactive = enabled && alpha > .004f && column.width() >= 54 && column.height() >= 36
        if (!interactive) {
            scroll.cancelDrag()
            cancelAction()
        } else if (!pointerDown) cancelAction()
        val canvas = SkijaGfx.canvas() ?: run {
            scroll.cancelDrag()
            cancelAction()
            return
        }
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
        val pressed = hover && pressedAction == Target.ENTRY
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
            val left = panelLeft + 4
            val width = minOf(column.right() - left, FontManager.pf12bold.getStringWidthD(text) + 14)
            val x = (entry.centerX() - width * .5).coerceAtMost(column.right() - width).coerceAtLeast(left)
            val hint = Box(x, entry.y() - 24, width, 20.0)
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
            renderColumn(canvas, graphics, column, playing, preparing, room, mouseX, mouseY, wheel, opacity,
                preferences, canUseColumn, pointerDown, previousRail)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun renderColumn(canvas: Canvas, graphics: EffectsPaint, column: Box, playing: Music?, preparing: Boolean,
                             room: List<Music>?, mouseX: Double, mouseY: Double, wheel: Int, alpha: Float,
                             preferences: UiPreferences, canUseColumn: Boolean, pointerDown: Boolean,
                             previousRail: Box?) {
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
        if (changed) {
            queueScroll.cancelDrag()
            queueScroll.moveTo(0)
            if (showingNotes) {
                notesScroll.cancelDrag()
                notesScroll.moveTo(0)
                if (pressedAction == Target.REVIEW) cancelAction()
                restartContent()
            }
        }
        if (showingNotes && track == null) toggleNotes()
        val next = roomSongs ?: upcoming?.next().orEmpty()
        val radio = upcoming?.radio().orEmpty()
        val waitingForQueue = !showingNotes && preparing && roomSongs == null && next.isEmpty() && radio.isEmpty()
        if (queueLoading && !waitingForQueue && !showingNotes) restartContent()
        queueLoading = waitingForQueue
        val locked = CloudMusic.isFullscreenQueueModeLocked()
        val notesTitle = if (showingNotes) FullscreenSongEncyclopedia.label("title").string else ""
        val reviewWidth = if (showingNotes) {
            val cancelReview = FullscreenSongEncyclopedia.label("review_cancel").string
            (FontManager.pf12bold.getStringWidthD(cancelReview) + 20).coerceAtLeast(54.0)
        } else 0.0
        val layout = FullscreenQueueLayout.of(column, MODE_CONTROLS.size, ROW, NOTE_LINE, track != null,
            showingNotes, FontManager.pf18bold.getStringWidthD(notesTitle), reviewWidth)
        val left = layout.left()
        val top = layout.top()
        val width = layout.width()
        val modes = layout.modes()
        val reviewBox = layout.review()
        val viewport = layout.viewport()
        val listTop = viewport.y()
        val radioOnly = radio.isNotEmpty() && next.isEmpty()
        val nextHeight = if (radioOnly) 0 else sectionHeight(next.size)
        val radioTop = nextHeight + if (radioOnly) 0 else 8
        val reviewAvailable = reviewBox != null && track != null && canUseColumn && !preparing
        if (showingNotes && track != null) {
            encyclopedia.update(track, title.apply(track), (width - 9).toInt(), reviewAvailable)
        }
        val docHeight = when {
            showingNotes -> encyclopedia.lines.size * NOTE_LINE
            queueLoading -> HEADING + 3 * ROW
            radio.isEmpty() -> nextHeight
            else -> radioTop + sectionHeight(radio.size)
        }
        val viewportHeight = viewport.height().toInt()
        val scrollbar = if (viewportHeight > 0 && docHeight > viewportHeight) {
            Box(viewport.right() - 8, viewport.y(), 8.0, viewport.height())
        } else null
        if (scroll.dragging() && (!canUseColumn || !pointerDown || scrollbar == null || previousRail != scrollbar
                || docHeight - viewportHeight != scroll.maximum())) {
            scroll.cancelDrag()
        }
        scroll.configure(if (changed) 0 else scroll.offset(), docHeight, viewportHeight)
        val now = nowMs()
        val reduce = preferences.reducedMotion() || preferences.highContrast()
        val contentProgress = contentReveal.sample(now, reduce).coerceIn(0f, 1f)
        val contentAlpha = alpha * (.25f + .75f * contentProgress)
        val contentInk = color(ColorType.PRIMARY_TEXT, contentAlpha)
        val contentSecondary = if (preferences.highContrast()) contentInk else color(ColorType.SECONDARY_TEXT, contentAlpha)
        if (viewportHeight > 0) {
            if (canUseColumn && !scroll.dragging() && viewport.contains(mouseX, mouseY) && wheel != 0) {
                scroll.wheel(wheel.toDouble() / 120, now, reduce)
            }
            scroll.advance(now, reduce)
            if (scrollbar != null && canUseColumn) {
                rail = scrollbar
                handCursor = handCursor || scroll.dragging() || scrollbar.contains(mouseX, mouseY)
                if (scroll.dragging()) scroll.drag(mouseY - scrollbar.y(), viewportHeight)
            }
        }

        if (track != null && layout.headerHeight() >= 46) {
            val coverBounds = Box(left, top, 36.0, 36.0)
            drawCover(canvas, graphics, track, coverBounds, alpha)
            graphics.text(canvas, FontManager.pf18bold, title.apply(track), left + 45, top + 10, width - 45, ink)
            graphics.text(canvas, FontManager.pf12, track.artistsName, left + 45, top + 26, width - 45, secondary)
        } else if (layout.headerHeight() > 0) {
            graphics.text(canvas, FontManager.pf14bold, label("title").string, left, top + 10, width, ink)
        }
        var hoveredControl: Pair<Target, Box>? = null
        if (modes != null) {
            val gap = layout.modeGap()
            val buttonWidth = (width - gap * (MODE_CONTROLS.size - 1)) / MODE_CONTROLS.size
            if (preferences.useBackdropBlur()) {
                SkijaEffects.backdropBlurRounded(modes.x(), modes.y(), modes.width(), modes.height(), 8.0, 2f, alpha)
            }
            for ((index, target) in MODE_CONTROLS.withIndex()) {
                val box = Box(left + index * (buttonWidth + gap), modes.y(), buttonWidth, modes.height())
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
                val hoverAmount = hoverAmount(target, hover && available, now, reduce)
                modeButton(canvas, graphics, target, box, selected, hoverAmount,
                    hover && available && pressedAction == target, available, ink, accent, alpha)
                if (keyboardHints && canUseColumn && focus == target && available) {
                    graphics.line(canvas, box.centerX() - 4, box.y() + 1, box.centerX() + 4, box.y() + 1, 1.5, accent)
                }
            }
        }
        if (reviewBox != null) {
            if (layout.notesTitleWidth() > 0) {
                val titleY = if (reviewBox.x() == left) reviewBox.y() - 11 else reviewBox.centerY()
                graphics.text(canvas, FontManager.pf18bold, notesTitle, left, titleY, layout.notesTitleWidth(), ink)
            }
            val hover = reviewAvailable && reviewBox.contains(mouseX, mouseY)
            if (reviewAvailable) targets[Target.REVIEW] = reviewBox
            if (hover) {
                hoveredControl = Target.REVIEW to reviewBox
                handCursor = true
            }
            val amount = hoverAmount(Target.REVIEW, hover, now, reduce)
            val focused = keyboardHints && reviewAvailable && focus == Target.REVIEW
            val pressed = hover && pressedAction == Target.REVIEW
            graphics.round(canvas, reviewBox, 7.0, color(ColorType.PRIMARY_TEXT,
                alpha * (if (pressed) .20f else .09f + .08f * amount)))
            graphics.buttonLabel(canvas, FontManager.pf12bold, encyclopedia.reviewButton().string, null,
                reviewBox, if (focused) accent else if (reviewAvailable) ink else secondary)
            if (focused) graphics.line(canvas, reviewBox.x() + 6, reviewBox.bottom() - 2,
                reviewBox.right() - 6, reviewBox.bottom() - 2, 1.0, accent)
        }
        if (canUseColumn && viewportHeight > 0) targets[Target.LIST] = viewport
        if (canUseColumn && pressedAction != null && pressedAction !in targets) cancelAction()
        if (canUseColumn && focus != null && focus !in targets) focus = Target.ENTRY
        if (viewport.height() <= 0) return
        val saved = canvas.save()
        try {
            canvas.clipRect(Rect.makeXYWH(viewport.x().toFloat(), viewport.y().toFloat(),
                viewport.width().toFloat(), viewport.height().toFloat()))
            val start = listTop - scroll.viewportOffset() + (if (showingNotes) 7 else -7) * (1f - contentProgress)
            if (showingNotes) {
                val first = ((viewport.y() - start) / NOTE_LINE).toInt().coerceAtLeast(0)
                val end = minOf(encyclopedia.lines.size, first + (viewport.height() / NOTE_LINE).toInt() + 2)
                val clip = Bounds(left.toInt(), viewport.y().toInt(), (width - 9).toInt(), viewport.height().toInt())
                for (i in first until end) {
                    val y = start + i * NOTE_LINE + NOTE_LINE / 2
                    val fraction = encyclopedia.skeletonWidths[i]
                    if (fraction == null) {
                        graphics.text(canvas, FontManager.pf14, encyclopedia.lines[i], left, y, width - 9, contentSecondary)
                    } else {
                        val area = Bounds(left.toInt(), (y - 3).toInt(), ((width - 9) * fraction).toInt(), 6)
                        val fill = UiSkeletonMotion.fill(clip, area, now, reduce,
                            color(ColorType.PRIMARY_TEXT, contentAlpha * if (preferences.highContrast()) .28f else .09f),
                            color(ColorType.PRIMARY_TEXT, contentAlpha * .19f), 1f)
                        graphics.round(canvas, Box(left, y - 3, area.width().toDouble(), 6.0), 3.0, fill)
                    }
                }
            } else if (queueLoading) {
                graphics.text(canvas, FontManager.pf14, label("loading").string, left, start + 11, width - 9, contentSecondary)
                val clip = Bounds(left.toInt(), viewport.y().toInt(), (width - 9).toInt(), viewport.height().toInt())
                val rows = List(3) { index -> Bounds(clip.x(), (start + HEADING + index * ROW).toInt(), clip.width(), ROW) }
                val base = color(ColorType.PRIMARY_TEXT, contentAlpha * if (preferences.highContrast()) .28f else .09f)
                val peak = color(ColorType.PRIMARY_TEXT, contentAlpha * .19f)
                for (block in MusicLoadingSkeleton.blocks(rows, clip, true)) {
                    val area = block.bounds()
                    val fill = UiSkeletonMotion.fill(clip, area, now, reduce, base, peak, 1f)
                    graphics.round(canvas, Box(area.x().toDouble(), area.y().toDouble(), area.width().toDouble(),
                        area.height().toDouble()), block.cornerRadius().toDouble(), fill)
                }
            } else {
                val source = upcoming?.source()?.takeIf { it.isNotBlank() }
                if (!radioOnly) drawSection(canvas, graphics, viewport, start,
                    label(if (roomSongs != null) "room" else "up_next"), source?.let { label("from", it) },
                    next, contentInk, contentSecondary, contentAlpha)
                if (radio.isNotEmpty()) {
                    drawSection(canvas, graphics, viewport, start + radioTop,
                        label("radio"), label("radio_detail"), radio, contentInk, contentSecondary, contentAlpha)
                } else if (next.isEmpty()) {
                    val empty = when {
                        mode.repeatsOne() && !locked && track != null -> "repeat_current"
                        mode == PlayMode.Random && track != null -> "shuffle_next_round"
                        else -> "empty"
                    }
                    graphics.text(canvas, FontManager.pf12, label(empty).string, left, start + 45, width - 7, contentSecondary)
                }
            }
        } finally {
            canvas.restoreToCount(saved)
        }
        if (scrollbar != null) {
            val height = viewport.height().toInt()
            val engaged = scroll.dragging() || canUseColumn && scrollbar.contains(mouseX, mouseY)
            val thumbWidth = if (engaged) 6.0 else 3.0
            if (engaged) graphics.round(canvas, scrollbar, 4.0, color(ColorType.PRIMARY_TEXT, alpha * .12f))
            val thumb = Box(viewport.right() - thumbWidth - 1, viewport.y() + scroll.thumbTop(height),
                thumbWidth, scroll.thumbHeight(height).toDouble())
            graphics.round(canvas, thumb, thumbWidth / 2, if (engaged) accent else secondary)
        }
        if (keyboardHints && canUseColumn && focus == Target.LIST && viewport.height() >= 15) {
            graphics.line(canvas, left - 4, listTop + 5, left - 4, listTop + 15, 1.5, accent)
        }
        val focusedControl = focus?.takeIf { keyboardHints && canUseColumn && (it in MODE_CONTROLS || it == Target.REVIEW) }
        val hintControl = if (keyboardHints) focusedControl?.let { target -> targets[target]?.let { target to it } }
            else hoveredControl
        hintControl?.let { (target, box) ->
            val text = when {
                target == Target.REVIEW -> encyclopedia.reviewHint()
                locked && target != Target.ENCYCLOPEDIA -> label("mode_locked")
                else -> modeLabel(target)
            }
            val font = FontManager.pf12bold
            val caption = text.string
            if (caption.isBlank()) return@let
            val bounds = font.getGlyphBounds(caption)
            val inkWidth = bounds?.let { it.right - minOf(0f, it.left) } ?: 0f
            val hintWidth = minOf(width, maxOf(font.getStringWidthD(caption), inkWidth.toDouble()) + 18)
            val lines = if (hintWidth < width || graphics.fit(font, caption, width - 14) == caption)
                arrayOf(caption) else graphics.lines(font, caption, width - 16)
            val step = maxOf(12.0, font.getFontHeight() + 2)
            val hintHeight = maxOf(20.0, lines.size * step + 6)
            val hintX = (box.centerX() - hintWidth / 2).coerceAtMost(left + width - hintWidth).coerceAtLeast(left)
            val above = box.y() - hintHeight - 3
            val below = box.bottom() + 3
            val hintY = when {
                above >= column.y() -> above
                below + hintHeight <= column.bottom() -> below
                else -> maxOf(column.y(), column.bottom() - hintHeight)
            }
            val hint = Box(hintX, hintY, hintWidth, hintHeight)
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
                           selected: Boolean, hover: Float, pressed: Boolean, available: Boolean,
                           ink: Int, accent: Int, alpha: Float) {
        val radius = 8.0
        if (pressed || hover > .001f) {
            graphics.round(canvas, box, radius, color(ColorType.PRIMARY_TEXT, alpha * if (pressed) .14f else .075f * hover))
        }
        if (selected && available) {
            graphics.round(canvas, Box(box.centerX() - 3.0, box.bottom() - 3.0, 6.0, 1.5), .75, accent)
        }
        val iconInk = when {
            !available -> color(ColorType.SECONDARY_TEXT, alpha * .65f)
            selected -> accent
            else -> ink
        }
        val iconSize = if (box.width() >= 20) 16.0 else 12.0
        when (target) {
            Target.SHUFFLE, Target.REPEAT -> {
                val glyph = when {
                    target == Target.SHUFFLE -> PartyIcons.SHUFFLE
                    mode.repeatsOne() -> PartyIcons.REPEAT_ONE
                    else -> PartyIcons.REPEAT
                }
                val font = if (iconSize == 16.0) FontManager.partyIcon32 else FontManager.partyIcon24
                font.getGlyphBounds(glyph)?.let { bounds ->
                    font.drawGlyphsOn(canvas, glyph, box.centerX() - (bounds.left + bounds.right) * .5,
                        box.centerY() - (bounds.top + bounds.bottom) * .5, iconInk, iconInk ushr 24, 0f, null)
                }
            }
            Target.AUTOPLAY -> FoliaLucideIcons.draw(FoliaLucideIcons.INFINITY,
                box.centerX(), box.centerY(), iconSize, iconInk)
            Target.AUTOMIX -> FoliaLucideIcons.draw(FoliaLucideIcons.AUDIO_LINES,
                box.centerX(), box.centerY(), iconSize, iconInk)
            Target.ENCYCLOPEDIA -> FoliaLucideIcons.draw(FoliaLucideIcons.INFO,
                box.centerX(), box.centerY(), iconSize, iconInk)
            else -> Unit
        }
    }

    private fun hoverAmount(target: Target, hovered: Boolean, now: Long, reduce: Boolean): Float {
        val destination = if (hovered) 1f else 0f
        if (reduce) {
            hoverMotion.get(target, now).snap(destination)
            return destination
        }
        return hoverMotion.follow(target, destination, now).coerceIn(0f, 1f)
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

    fun pressAction(x: Double, y: Double): Boolean {
        val target = actionAt(x, y) ?: return false
        pressedAction = target
        keyboardHints = false
        focus = target
        return true
    }

    fun hasActionPress(): Boolean = pressedAction != null

    fun cancelAction() { pressedAction = null }

    fun releaseAction(x: Double, y: Double) {
        val target = pressedAction
        cancelAction()
        if (target != null && target == actionAt(x, y)) activate(target)
    }

    private fun actionAt(x: Double, y: Double): Target? {
        if (!interactive) return null
        return targets.entries.firstOrNull { (target, box) ->
            target != Target.LIST && box.contains(x, y) && (target == Target.ENTRY || isOpen && !reveal.running())
        }?.key
    }

    fun click(x: Double, y: Double, button: Int): Boolean {
        if (!interactive) return false
        val target = targets.entries.firstOrNull { (target, box) ->
            box.contains(x, y) && (target == Target.ENTRY || isOpen && !reveal.running())
        }?.key ?: return false
        if (button == 0) {
            keyboardHints = false
            focus = target
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
        if (key == GLFW_KEY_ESCAPE && hasActionPress()) {
            cancelAction()
            return true
        }
        if (isOpen && key == GLFW_KEY_ESCAPE) {
            if (scroll.dragging()) {
                scroll.cancelDrag()
                return true
            }
            if (showingNotes) {
                toggleNotes()
                keyboardHints = true
                focus = if (Target.ENCYCLOPEDIA in targets) Target.ENCYCLOPEDIA else Target.ENTRY
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
            focus = if (index < 0) {
                if (backwards) order.last() else order.first()
            } else order[Math.floorMod(index + if (backwards) -1 else 1, order.size)]
            announce()
            return true
        }
        val target = focus
        if (target != null && target in targets && (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER || key == GLFW_KEY_SPACE)) {
            keyboardHints = true
            activate(target)
            return true
        }
        if (!isOpen || Target.LIST !in targets) return false
        val offset = scroll.offset().toLong()
        val step = if (showingNotes) NOTE_LINE else ROW
        val next = when (key) {
            GLFW_KEY_UP -> if (target == Target.LIST) offset - step else return false
            GLFW_KEY_DOWN -> if (target == Target.LIST) offset + step else return false
            GLFW_KEY_PAGE_UP -> offset - scroll.page()
            GLFW_KEY_PAGE_DOWN -> offset + scroll.page()
            GLFW_KEY_HOME -> 0L
            GLFW_KEY_END -> scroll.maximum().toLong()
            else -> return false
        }
        if (scroll.dragging()) return true
        val focusChanged = target != Target.LIST
        focus = Target.LIST
        keyboardHints = true
        if (scroll.moveTo(next) || focusChanged) announce()
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
            Target.REVIEW -> if (showingNotes && currentSongMatches() && encyclopedia.activateReview()) {
                notesScroll.moveTo(0)
            }
        }
        mode = CloudMusic.playMode
        announce()
    }

    private fun toggleNotes() {
        scroll.cancelDrag()
        cancelAction()
        showingNotes = !showingNotes
        rail = null
        notesScroll.moveTo(0)
        encyclopedia.clear()
        restartContent()
        targets.keys.removeIf { it == Target.LIST || it == Target.REVIEW }
    }

    private fun restartContent() {
        contentReveal.snapTo(0f)
        contentReveal.blendTo(1f, nowMs(), 0, 190, InterruptibleAnimation.BlendCurve.SMOOTHSTEP)
    }

    private fun currentSongMatches(): Boolean {
        val presentation = CloudMusic.playbackPresentation()
        return !presentation.preparing && currentTrack?.stableKey == presentation.displayTrack?.stableKey
    }

    private fun setOpen(open: Boolean) {
        val now = nowMs()
        val from = reveal.sample(now)
        val to = if (open) 1f else 0f
        cancelAction()
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
            Target.REVIEW -> encyclopedia.reviewButton().copy().append(" ").append(encyclopedia.reviewHint())
            Target.LIST -> {
                if (showingNotes) {
                    val index = (scroll.offset() / NOTE_LINE).coerceAtLeast(0)
                    OnlineMusicViewPaint.narrate(Component.literal(encyclopedia.lines.drop(index)
                        .take((scroll.page() / NOTE_LINE).coerceAtLeast(1)).filter { it.isNotBlank() }.joinToString(" ")))
                    return
                }
                if (queueLoading) {
                    OnlineMusicViewPaint.narrate(label("loading"))
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
        cancelAction()
        scroll.cancelDrag()
        encyclopedia.clear()
        reveal.close()
        contentReveal.close()
        hoverMotion.clear()
        paint?.close()
        paint = null
        requestedCovers.clear()
    }

    companion object {
        const val ENTRY_SIZE = 26.0
        const val ENTRY_GAP = 4.0
        private val MODE_CONTROLS = listOf(Target.SHUFFLE, Target.REPEAT, Target.AUTOPLAY, Target.AUTOMIX, Target.ENCYCLOPEDIA)
        private const val NOTE_LINE = 18
        private const val HEADING = 36
        private const val ROW = 36
        private fun nowMs(): Long = System.nanoTime() / 1_000_000L
        private fun sectionHeight(size: Int): Int = HEADING + maxOf(1, size) * ROW
    }
}
