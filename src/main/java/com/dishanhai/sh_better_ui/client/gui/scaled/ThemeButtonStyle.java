// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.theme.ThemeBinding;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import com.dishanhai.sh_better_ui.theme.style.StyleInvalidation;
import com.dishanhai.sh_better_ui.theme.style.StyleState;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.dishanhai.sh_better_ui.theme.token.ThemeTokens.*;

/** Maps the theme cascade to the existing button renderers. */
final class ThemeButtonStyle implements AutoCloseable {
    private static final List<Set<StyleState>> STATES = states();
    private final ThemeScope scope;
    private final ThemeScope.InvalidationOwner owner;
    private final ThemeScope.BoundValue<Tones> tones;
    private final UiStyle style;

    ThemeButtonStyle(ThemeScope scope, UiStyle style, ThemeBinding<ColorValue> background, ThemeBinding<ColorValue> text) {
        this.scope = scope;
        this.style = style;
        // Minecraft repaints widgets each frame. PAINT updates the cached properties without relayout.
        owner = scope.invalidationOwner(ignored -> {});
        try {
            Set<ThemeToken<?>> keys = new LinkedHashSet<>(List.of(BACKGROUND, BORDER, BORDER_FOCUSED, ACCENT,
                    TEXT_DISABLED, HOVER, PRESSED, SELECTED, DISABLED, RADIUS_SMALL, RADIUS_LARGE,
                    LIQUID_GLASS, BORDER_WIDTH, OPACITY));
            keys.addAll(background.dependencies());
            keys.addAll(text.dependencies());
            var binding = ThemeBinding.derive(keys, (values, state) -> {
                boolean glass = style == UiStyle.LIQUID_GLASS
                        || style == UiStyle.DEFAULT && values.resolve(LIQUID_GLASS);
                return new Tones(
                        background.read(values, state).argb(), text.read(values, state).argb(),
                        values.resolve(BACKGROUND).argb() | 0xff000000, values.resolve(BORDER).argb(),
                        values.resolve(BORDER_FOCUSED).argb(), values.resolve(ACCENT).argb(),
                        values.resolve(TEXT_DISABLED).argb(), values.resolve(HOVER).argb(), values.resolve(PRESSED).argb(),
                        values.resolve(SELECTED).argb(), values.resolve(DISABLED).argb(),
                        values.resolve(glass ? RADIUS_LARGE : RADIUS_SMALL), Math.round(values.resolve(BORDER_WIDTH)),
                        values.resolve(OPACITY), glass);
            });
            tones = scope.bind("sh_better_ui:button", Set.of(), binding, owner, StyleInvalidation.PAINT);
        } catch (RuntimeException failure) {
            owner.close();
            throw failure;
        }
    }

    boolean active() { return !tones.isClosed(); }

    UiPreferences preferences(UiPreferences base) {
        var next = scope.preferences();
        boolean contrast = next.highContrast() || base.highContrast();
        boolean reduced = next.reducedMotion() || base.reducedMotion();
        boolean blur = base.backgroundBlur() && !next.lowPerformance()
                && (style != UiStyle.DEFAULT || !scope.resolve(LIQUID_GLASS) || scope.resolve(BACKDROP));
        if (contrast == base.highContrast() && reduced == base.reducedMotion() && blur == base.backgroundBlur()) {
            return base;
        }
        return new UiPreferences(contrast, reduced, blur, base.feedback());
    }

    Paint sample(boolean hovered, boolean pressed, boolean focused, boolean enabled, boolean highContrast,
                 boolean selected, float hover, float press) {
        int mask = (hovered ? 1 : 0) | (pressed ? 2 : 0) | (focused ? 4 : 0)
                | (selected ? 8 : 0) | (!enabled ? 16 : 0);
        tones.state(STATES.get(mask));
        var tokens = tones.value();
        boolean solid = (style == UiStyle.DEFAULT && !tokens.glass) || highContrast;
        int tint = opacity(tokens.fill, tokens.opacity);
        int fill = solid ? ContrastPolicy.composite(tint, tokens.background) : tint | 0xff000000;
        if (selected) fill = ContrastPolicy.composite(tokens.selected, fill);
        if (enabled) {
            fill = ContrastPolicy.composite(opacity(tokens.hover, hover), fill);
            fill = ContrastPolicy.composite(opacity(tokens.pressed, press), fill);
        } else {
            fill = ContrastPolicy.composite(tokens.disabled, fill);
        }
        if (!solid) fill = opacity((tint & 0xff000000) | (fill & 0xffffff),
                tokens.glass ? 0.78f : 0.88f);
        int text = enabled ? tokens.text : tokens.disabledText;
        if (enabled || highContrast) {
            text = ContrastPolicy.readable(text, fill, tokens.background, highContrast ? 7 : 4.5);
        }
        int border = selected ? tokens.focus : GuiMotion.lerpColor(tokens.border, tokens.accent,
                highContrast ? 0 : Math.max(hover, press));
        return new Paint(fill, text, border, tokens.focus, tokens.accent, tokens.background,
                tokens.radius, tokens.borderWidth, tokens.glass);
    }

    private static int opacity(int color, float amount) {
        return color & 0xffffff | Math.round((color >>> 24) * GuiMotion.clamp01(amount)) << 24;
    }

    private static List<Set<StyleState>> states() {
        var list = new ArrayList<Set<StyleState>>(32);
        for (int i = 0; i < 32; i++) {
            var state = EnumSet.noneOf(StyleState.class);
            if ((i & 1) != 0) state.add(StyleState.HOVERED);
            if ((i & 2) != 0) state.add(StyleState.PRESSED);
            if ((i & 4) != 0) state.add(StyleState.FOCUSED);
            if ((i & 8) != 0) state.add(StyleState.SELECTED);
            if ((i & 16) != 0) state.add(StyleState.DISABLED);
            list.add(Set.copyOf(state));
        }
        return List.copyOf(list);
    }

    @Override
    public void close() { owner.close(); }

    record Paint(int fill, int text, int border, int focus, int accent, int background,
                 float radius, int borderWidth, boolean glass) {}

    private record Tones(int fill, int text, int background, int border, int focus, int accent, int disabledText,
                         int hover, int pressed, int selected, int disabled, float radius, int borderWidth,
                         float opacity, boolean glass) {}
}
