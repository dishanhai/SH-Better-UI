// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.rendering.GlassLook;
import com.muoniumplayer.core.rendering.ui.container.Panel;
import com.muoniumplayer.core.screens.ncm.NcmExpressive;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;

import static com.dishanhai.sh_better_ui.kernel.internal.music.MusicChatWidgets.*;
import static com.muoniumplayer.core.screens.ncm.NCMScreen.ColorType.*;

/** A question in the transcript, scrolled and clipped together with ordinary messages. */
final class MusicChatChoices extends Panel {
    private static final double PADDING = 10;
    private static final double OPTION_STRIDE = BUTTON_HEIGHT + GAP;
    private final MusicChatActions.Question question;
    private final BitSet picked = new BitSet();
    private final Focus focus;
    private final String confirmLabel;
    private List<MusicChatMarkdown.Line> prompt = List.of();
    private double optionsTop;
    private double stride;

    MusicChatChoices(MusicChatActions.Question question, Focus focus, String confirmLabel,
                     Consumer<List<MusicChatActions.Option>> confirm, Runnable cancel) {
        this.question = question;
        this.focus = focus;
        this.confirmLabel = confirmLabel;
        for (int i = 0; i < question.options().size(); i++) {
            int index = i;
            String label = optionText(question.options().get(i));
            button(this, focus, () -> (picked.get(index) ? "✓  " : question.multiple() ? "□  " : "○  ") + label,
                    () -> true, () -> picked.get(index), () -> PADDING, () -> optionsTop + index * OPTION_STRIDE,
                    this::innerWidth, () -> toggle(index, label)).setTooltip(() -> label);
        }
        button(this, focus, MusicChatWidgets::cancelLabel, () -> true, () -> false, () -> PADDING,
                this::actionsTop, () -> Math.min(innerWidth() * .36, buttonWidth(cancelLabel())), cancel);
        button(this, focus, () -> this.confirmLabel, () -> !picked.isEmpty(), () -> true,
                () -> PADDING + innerWidth() - confirmWidth(), this::actionsTop, this::confirmWidth,
                () -> confirm.accept(picked.stream().mapToObj(question.options()::get).toList()));
    }

    MusicChatActions.Question question() { return question; }

    static String optionText(MusicChatActions.Option option) {
        return option.title().isEmpty() ? option.label() : text("choice_song", option.title(), option.artist());
    }

    void resize(double width) {
        prompt = MusicChatMarkdown.plain(question.prompt(), Math.max(1, width - 2 * PADDING));
        stride = FontManager.pf14.getFontHeight() + 4;
        optionsTop = 22 + (prompt.size() + 1) * stride + GAP;
        setBounds(0, 0, width, actionsTop() + BUTTON_HEIGHT + PADDING);
    }

    void release() { getChildren().forEach(focus::remove); }

    String transcriptText() {
        var lines = new ArrayList<String>();
        lines.add(question.prompt());
        for (var option : question.options()) lines.add("• " + optionText(option));
        return String.join("\n", lines);
    }

    private double innerWidth() { return Math.max(1, getWidth() - 2 * PADDING); }
    private double confirmWidth() { return Math.min(innerWidth() * .60, buttonWidth(confirmLabel)); }
    private double actionsTop() { return optionsTop + question.options().size() * OPTION_STRIDE + 2; }

    private void toggle(int index, String label) {
        boolean selecting = !picked.get(index);
        if (!question.multiple()) picked.clear();
        picked.set(index, selecting);
        MusicTypography.narrate(Component.literal(text(selecting ? "choice_selected" : "choice_deselected", label)));
    }

    @Override public void onRender(double mouseX, double mouseY) {
        roundedRect(getX(), getY(), getWidth(), getHeight(), 7, NcmExpressive.withAlpha(color(ELEMENT_BACKGROUND),
                getAlpha() * GlassLook.washAlpha(GlassLook.Surface.PLAYER, .8f)));
        double x = getX() + PADDING;
        int primary = NcmExpressive.withAlpha(color(PRIMARY_TEXT), getAlpha());
        int secondary = NcmExpressive.withAlpha(color(SECONDARY_TEXT), getAlpha());
        FontManager.pf14bold.drawString(text("assistant"), x, getY() + 7,
                NcmExpressive.withAlpha(color(ACCENT), getAlpha()));
        double y = getY() + 22;
        for (var line : prompt) {
            line.draw(x, y, primary, secondary);
            y += stride;
        }
        FontManager.pf14.drawString(FontManager.pf14.trim(text(question.multiple() ? "choice_multiple" : "choice_single"),
                innerWidth()), x, y, secondary);
    }
}
