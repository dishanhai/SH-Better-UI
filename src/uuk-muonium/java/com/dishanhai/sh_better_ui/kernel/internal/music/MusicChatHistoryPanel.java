// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.rendering.GlassLook;
import com.muoniumplayer.core.rendering.ui.container.Panel;
import com.muoniumplayer.core.rendering.ui.container.ScrollPanel;
import com.muoniumplayer.core.screens.ncm.NcmExpressive;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.kernel.internal.music.MusicChatWidgets.*;
import static com.muoniumplayer.core.screens.ncm.NCMScreen.ColorType.*;

/** The conversation list lives beside the transcript, in the same player content surface. */
final class MusicChatHistoryPanel extends Panel {
    private static final int INSET = 7;
    private final Focus focus;
    private final ScrollPanel list;
    private final Supplier<String> active;
    private final Consumer<MusicChatHistory.Entry> select;
    private List<MusicChatHistory.Entry> entries = List.of();
    private boolean error;

    MusicChatHistoryPanel(Focus focus, Supplier<String> active, Consumer<MusicChatHistory.Entry> select, Runnable remove) {
        this.focus = focus;
        this.active = active;
        this.select = select;
        NcmExpressive.label(this, () -> text("history_title"), FontManager.pf14bold, () -> INSET, () -> 13,
                this::innerWidth, () -> color(PRIMARY_TEXT));
        list = scroll(this).setSpacing(4).setIndicatorInsideViewport();
        list.setBeforeRenderCallback(() -> list.setBounds(INSET, 25, innerWidth(), Math.max(1, getHeight() - 66)));
        NcmExpressive.label(this, () -> error ? text("history_error") : entries.isEmpty() ? text("history_empty") : "",
                FontManager.pf14, () -> INSET, () -> entries.isEmpty() ? 37 : getHeight() - 36,
                this::innerWidth, () -> color(SECONDARY_TEXT));
        button(this, focus, () -> text("history_delete"), () -> !isHidden()
                        && entries.stream().anyMatch(entry -> entry.id().equals(active.get())),
                () -> false, () -> INSET, () -> getHeight() - BUTTON_HEIGHT - INSET, this::innerWidth, remove);
    }

    void update(List<MusicChatHistory.Entry> entries, boolean error) {
        this.entries = entries;
        this.error = error;
        list.getChildren().forEach(focus::remove);
        list.getChildren().clear();
        for (var entry : entries) {
            Supplier<String> title = () -> entry.title().isBlank() ? text("history_untitled") : entry.title();
            scrollButton(list, focus, title, () -> !isHidden(), () -> entry.id().equals(active.get()),
                    list::getWidth, () -> select.accept(entry)).setTooltip(title).setBounds(1, BUTTON_HEIGHT);
        }
    }

    boolean scrollKey(int key) {
        return (list.isHovering() || list.getChildren().stream().anyMatch(focus::isFocused))
                && MusicChatWidgets.scrollKey(list, key);
    }

    boolean reveal(com.muoniumplayer.core.rendering.ui.AbstractWidget<?> widget) {
        if (widget.getParent() != list) return false;
        double above = widget.getY() - list.getY();
        double below = above + widget.getHeight() - list.getHeight();
        if (above < 0 || below > 0) list.scrollToOffsetImmediately(list.targetScrollOffset + (above < 0 ? above : below));
        return true;
    }

    private double innerWidth() { return Math.max(1, getWidth() - 2 * INSET); }

    @Override public void onRender(double mouseX, double mouseY) {
        roundedRect(getX(), getY(), getWidth(), getHeight(), 7, NcmExpressive.withAlpha(color(ELEMENT_BACKGROUND),
                getAlpha() * GlassLook.washAlpha(GlassLook.Surface.PLAYER, .8f)));
    }
}
