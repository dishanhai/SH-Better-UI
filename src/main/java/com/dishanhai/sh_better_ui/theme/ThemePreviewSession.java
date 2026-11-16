// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import java.util.List;
import java.util.Map;

/** A gallery-owned theme runtime. Selection and transitions never touch the live manager. */
public final class ThemePreviewSession implements AutoCloseable {
    private final java.util.function.Predicate<Theme> visible;
    private ThemeRegistry sourceRegistry;
    private Map<String, ThemeToken<?>> sourceTokens;
    private ThemeManager preview;
    private String selected;
    private List<Theme> themes = List.of();

    public ThemePreviewSession(String selected) { this(selected, theme -> true); }
    /** Visibility is supplied by the host; pure theme code has no client/runtime dependency. */
    public ThemePreviewSession(String selected, java.util.function.Predicate<Theme> visible) {
        this.selected = selected; this.visible = java.util.Objects.requireNonNull(visible);
    }

    /** Copies whole definitions atomically, preserving inheritance, styles and extension tokens. */
    public boolean sync(ThemeManager source, ThemePreferences preferences) {
        boolean rebuilt = preview == null || sourceRegistry != source.registry() || sourceTokens != source.tokenCatalog();
        if (rebuilt) {
            var replacement = new ThemeManager();
            try {
                source.tokenCatalog().values().forEach(replacement::registerToken);
                replacement.replaceResourceThemes(source.registry().themes().values().stream()
                        .filter(theme -> !BuiltinThemes.IDS.contains(theme.id())).toList(), List.of());
            } catch (RuntimeException failure) {
                replacement.close();
                throw failure;
            }
            close();
            preview = replacement;
            sourceRegistry = source.registry();
            sourceTokens = source.tokenCatalog();
            themes = sourceRegistry.themes().values().stream()
                    .filter(visible).sorted(java.util.Comparator.comparing(Theme::id)).toList();
            if (themes.isEmpty()) throw new IllegalStateException("No visible preview themes");
            if (selected == null || themes.stream().noneMatch(theme -> theme.id().equals(selected))) {
                selected = themes.stream().anyMatch(theme -> theme.id().equals(BuiltinThemes.DARK))
                        ? BuiltinThemes.DARK : themes.get(0).id();
            }
        }
        preview.preferences(preferences.withFixedTheme(selected));
        return rebuilt;
    }

    public List<Theme> themes() {
        return themes;
    }

    public String selected() { return selected; }

    public void select(String id) {
        if (themes.stream().noneMatch(theme -> theme.id().equals(id))) throw new IllegalArgumentException("Unknown preview theme: " + id);
        selected = id;
        preview.transitionTo(id);
    }

    public ThemeScope scope() { return preview.global(); }
    public void frame() { preview.frame(); }

    @Override public void close() {
        if (preview != null) preview.close();
        preview = null;
    }
}
