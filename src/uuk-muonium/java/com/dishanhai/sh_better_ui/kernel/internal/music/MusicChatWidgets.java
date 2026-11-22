// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.muoniumplayer.compat.lwjgl2.Keyboard;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.rendering.FontelloIcons;
import com.muoniumplayer.core.rendering.FoliaLucideIcons;
import com.muoniumplayer.core.rendering.GlassLook;
import com.muoniumplayer.core.rendering.ui.AbstractWidget;
import com.muoniumplayer.core.rendering.ui.container.Panel;
import com.muoniumplayer.core.rendering.ui.container.ScrollPanel;
import com.muoniumplayer.core.rendering.ui.widgets.RoundedButtonWidget;
import com.muoniumplayer.core.rendering.ui.widgets.TextFieldWidget;
import com.muoniumplayer.core.screens.ncm.NCMScreen;
import com.muoniumplayer.core.screens.ncm.NcmExpressive;
import com.muoniumplayer.core.screens.ncm.NcmExpressivePalette;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import static com.muoniumplayer.core.screens.ncm.NCMScreen.ColorType.*;

final class MusicChatWidgets {
    static final int INSET = 12;
    static final int BUTTON_HEIGHT = 22;
    static final int GAP = 5;

    private MusicChatWidgets() {}

    static String text(String key, Object... args) {
        return Component.translatable("sh_better_ui.music_chat." + key, args).getString();
    }

    static String cancelLabel() { return Component.translatable("gui.cancel").getString(); }
    static int color(NCMScreen.ColorType role) { return NCMScreen.getColor(role); }

    static Panel column(Panel page, double top, double bottom, double maxWidth) {
        Panel panel = new Panel();
        page.addChild(panel);
        panel.setBeforeRenderCallback(() -> {
            double width = Math.max(1, Math.min(maxWidth, page.getWidth() - INSET * 2));
            panel.setBounds((page.getWidth() - width) / 2, top, width,
                    Math.max(1, page.getHeight() - top - bottom));
        });
        return panel;
    }

    static ScrollPanel scroll(Panel parent) {
        ScrollPanel scroll = new ScrollPanel();
        parent.addChild(scroll);
        return scroll.setAlignment(ScrollPanel.Alignment.VERTICAL).setScrollStrength(28).setIndicatorInsideViewport();
    }

    static double buttonWidth(String title) {
        return Math.max(42, FontManager.pf14bold.getStringWidthD(title) + 18);
    }

    static void activityLabel(Panel parent, Supplier<String> message, BooleanSupplier busy, DoubleSupplier centerY) {
        double indicatorWidth = 25;
        var indicator = new Panel() {
            @Override public void onRender(double mouseX, double mouseY) {
                if (!busy.getAsBoolean()) return;
                var preferences = UiPreferences.configured();
                boolean animate = !preferences.reducedMotion() && !preferences.highContrast();
                double phase = (System.nanoTime() / 1_000_000L % 1_300) / 1_300.0;
                int accent = color(ACCENT);
                for (int dot = 0; dot < 3; dot++) {
                    double pulse = (1 + Math.cos(Math.PI * 2 * (phase - dot * .18))) * .5;
                    double opacity = animate ? .42 + .58 * pulse * pulse : 1;
                    roundedRect(getX() + dot * 7, getY() + 2, 4, 4, 2,
                            NcmExpressive.withAlpha(accent, opacity * getAlpha()));
                }
            }
        };
        parent.addChild(indicator);
        indicator.setClickable(false).setBeforeRenderCallback(() ->
                indicator.setBounds(0, centerY.getAsDouble() - 4, indicatorWidth, 8));
        DoubleSupplier left = () -> busy.getAsBoolean() ? indicatorWidth : 0;
        NcmExpressive.label(parent, message, FontManager.pf14, left, centerY,
                () -> parent.getWidth() - left.getAsDouble(), () -> color(SECONDARY_TEXT));
    }

    static void header(Panel parent, Focus focus, String title, Runnable back) {
        NcmExpressive.label(parent, () -> title, FontManager.pf24bold, () -> INSET + 6, () -> 20,
                () -> parent.getWidth() - 68, () -> color(PRIMARY_TEXT));
        button(parent, focus, () -> FontelloIcons.BACK, () -> Component.translatable("gui.back").getString(),
                () -> true, () -> false, () -> parent.getWidth() - INSET - 28, () -> 8, () -> 28, back, true);
    }

    static RoundedButtonWidget button(Panel parent, Focus focus, Supplier<String> label, BooleanSupplier enabled,
                       BooleanSupplier primary, DoubleSupplier x, DoubleSupplier y, DoubleSupplier width, Runnable click) {
        return button(parent, focus, label, label, enabled, primary, x, y, width, click, false);
    }

    static RoundedButtonWidget scrollButton(ScrollPanel parent, Focus focus, Supplier<String> label, BooleanSupplier enabled,
                                             BooleanSupplier primary, DoubleSupplier width, Runnable click) {
        return button(parent, focus, label, label, enabled, primary, () -> 0, null, width, click, false);
    }

    static void channelRow(ScrollPanel parent, Focus focus, String caption, BooleanSupplier enabled,
                           BooleanSupplier selected, Runnable click) {
        Supplier<String> label = () -> caption;
        var row = scrollButton(parent, focus, label, enabled, selected, () -> Math.max(1, parent.getWidth() - 8), click);
        var title = row.getChildren().get(0);
        title.setBeforeRenderCallback(() -> title.setPosition(6, (row.getHeight() - FontManager.pf14bold.getFontHeight()) / 2));
        row.setTooltip(label);
        row.setBounds(1, BUTTON_HEIGHT);
    }

    static void historyButton(Panel parent, Focus focus, BooleanSupplier open, Runnable toggle) {
        Supplier<String> name = () -> text(open.getAsBoolean() ? "history_close" : "history_title");
        var button = button(parent, focus, () -> "", name, () -> true, open, () -> INSET + 6,
                () -> 44, () -> BUTTON_HEIGHT, toggle, true);
        button.setTooltip(name);
        var icon = new Panel() {
            @Override public void onRender(double mouseX, double mouseY) {
                int ink = open.getAsBoolean() || focus.isFocused(button)
                        ? NcmExpressivePalette.live().onPrimary() : color(PRIMARY_TEXT);
                FoliaLucideIcons.draw(FoliaLucideIcons.CLASSIC_UI, button.getX() + button.getWidth() / 2,
                        button.getY() + button.getHeight() / 2, 13, NcmExpressive.withAlpha(ink, button.getAlpha()));
            }
        };
        button.addChild(icon);
        icon.setClickable(false).setBeforeRenderCallback(() -> icon.setBounds(button.getWidth(), button.getHeight()));
    }

    private static RoundedButtonWidget button(AbstractWidget<?> parent, Focus focus, Supplier<String> label, Supplier<String> spoken,
                               BooleanSupplier enabled, BooleanSupplier primary, DoubleSupplier x,
                               DoubleSupplier y, DoubleSupplier width, Runnable click, boolean icon) {
        var button = new RoundedButtonWidget(() -> icon ? label.get()
                : FontManager.pf14bold.trim(label.get(), Math.max(1, width.getAsDouble() - 10)),
                icon ? FontManager.fontello18 : FontManager.pf14bold);
        parent.addChild(button);
        button.setRadius(6).setGlassPlate(GlassLook.Surface.PLAYER);
        button.setBeforeRenderCallback(() -> {
            boolean active = enabled.getAsBoolean();
            boolean focused = focus.isFocused(button);
            boolean highlight = focused || button.isHovering();
            boolean accent = active && (focused || primary.getAsBoolean());
            button.setBounds(x.getAsDouble(), y == null ? button.getRelativeY() : y.getAsDouble(),
                    Math.max(1, width.getAsDouble()), BUTTON_HEIGHT);
            button.setClickable(active);
            button.setFillAlpha(accent ? 1f : GlassLook.washAlpha(GlassLook.Surface.PLAYER, .95f));
            button.setColor(accent ? color(highlight ? ACCENT_HOVER : ACCENT)
                    : highlight && active ? color(ELEMENT_HOVER) : color(ELEMENT_BACKGROUND));
            button.setTextColor(accent ? NcmExpressivePalette.live().onPrimary()
                    : color(active ? PRIMARY_TEXT : SECONDARY_TEXT));
        });
        button.setOnClickCallback((mouseX, mouseY, key) -> {
            if (key != 0 || !enabled.getAsBoolean()) return false;
            focus.clear();
            click.run();
            return true;
        });
        focus.controls.add(new Control(button, null, spoken, enabled, click));
        return button;
    }

    static TextFieldWidget field(Panel parent, Focus focus, String label, int limit, boolean secret,
                                 Supplier<String> hint, String initial, BooleanSupplier enabled,
                                 DoubleSupplier x, DoubleSupplier y, DoubleSupplier width,
                                 Consumer<String> change, Runnable submit) {
        var field = new TextFieldWidget(FontManager.pf14);
        var frame = NcmExpressive.surface(parent, x, y, width, () -> BUTTON_HEIGHT, 6,
                () -> color(field.isFocused() ? ELEMENT_HOVER : INPUT_BACKGROUND));
        frame.addChild(field);
        field.getTextField().setMaxStringLength(limit);
        field.getTextField().isPassword = secret;
        field.setText(initial).drawUnderline(false).setTextChangedCallback(change::accept);
        field.setBeforeRenderCallback(() -> {
            boolean active = enabled.getAsBoolean();
            field.setBounds(7, 1, Math.max(1, frame.getWidth() - 14), BUTTON_HEIGHT - 2);
            field.setEnabled(active).setClickable(active);
            field.setColor(color(PRIMARY_TEXT));
            field.setDisabledTextColor(color(active ? PRIMARY_TEXT : SECONDARY_TEXT));
            field.setPlaceholder(hint.get());
        });
        focus.controls.add(new Control(frame, field, () -> label, enabled, submit));
        return field;
    }

    static boolean scrollKey(ScrollPanel scroll, int key) {
        double step = switch (key) {
            case Keyboard.KEY_PRIOR -> -scroll.getHeight() * .8;
            case Keyboard.KEY_NEXT -> scroll.getHeight() * .8;
            default -> 0;
        };
        if (step == 0) return false;
        scroll.scrollToOffsetImmediately(scroll.targetScrollOffset + step);
        return true;
    }

    private record Control(AbstractWidget<?> widget, TextFieldWidget field, Supplier<String> name,
                           BooleanSupplier enabled, Runnable activate) {}

    static final class Focus {
        private final List<Control> controls = new ArrayList<>();
        private int index = -1;

        boolean isFocused(AbstractWidget<?> widget) {
            return index >= 0 && controls.get(index).widget() == widget && controls.get(index).enabled().getAsBoolean();
        }

        void clear() {
            index = -1;
            for (Control control : controls) if (control.field() != null) control.field().setFocused(false);
        }

        void remove(AbstractWidget<?> widget) {
            for (int i = 0; i < controls.size(); i++) {
                if (controls.get(i).widget() != widget) continue;
                controls.remove(i);
                if (index == i) index = -1;
                else if (index > i) index--;
                return;
            }
        }

        boolean key(int key, Consumer<AbstractWidget<?>> reveal) {
            if (key == Keyboard.KEY_TAB) {
                int start = index;
                for (int i = 0; i < controls.size(); i++) {
                    TextFieldWidget field = controls.get(i).field();
                    if (field != null && field.isFocused()) start = i;
                }
                boolean backwards = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                if (start < 0 && backwards) start = 0;
                for (int offset = 1; offset <= controls.size(); offset++) {
                    int candidate = Math.floorMod(start + (backwards ? -offset : offset), controls.size());
                    Control control = controls.get(candidate);
                    if (!control.enabled().getAsBoolean()) continue;
                    clear();
                    index = candidate;
                    if (control.field() != null) control.field().setFocused(true);
                    reveal.accept(control.widget());
                    MusicTypography.narrate(Component.literal(control.name().get()));
                    break;
                }
                return true;
            }
            if (key != Keyboard.KEY_RETURN && key != Keyboard.KEY_NUMPADENTER) return false;
            for (Control control : controls) {
                if (control.field() != null && control.field().isFocused()) {
                    if (control.activate() != null && control.enabled().getAsBoolean()) control.activate().run();
                    return true;
                }
            }
            if (index < 0) return false;
            Control control = controls.get(index);
            if (control.field() == null && control.enabled().getAsBoolean()) {
                control.activate().run();
                return true;
            }
            return false;
        }
    }
}
