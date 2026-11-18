// Author: dishanhai. Native implementation inspired by MuoniumPlayer's MConfigHudScreen theme chips.
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.theme.*;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;

/** Registry-backed theme cards. Owns no global state or GPU resources; host owns widget mounting. */
public final class MpThemeBrowser implements AutoCloseable {
    public static final int CARD_WIDTH = 132, CARD_HEIGHT = 88, GAP = 8;
    private final ThemeManager source;
    private final Supplier<ThemeSettings> draft;
    private final Consumer<ThemeSettings> stage;
    private final Predicate<String> hidden;
    private final ThemeResolver resolver = new ThemeResolver();
    private final List<UiThemePreviewCard> mounted = new ArrayList<>();
    private ThemeRegistry registry;
    private Map<String, ThemeToken<?>> tokens;
    private List<String> ids = List.of();
    private Map<String, UiTheme> palettes = Map.of();
    private ThemeSettings previousDraft;
    private int page, pageSize = 1;
    private boolean closed;
    private Component error;

    public MpThemeBrowser(ThemeManager source, Supplier<ThemeSettings> draft,
                          Consumer<ThemeSettings> stage, Predicate<String> hidden) {
        this.source = Objects.requireNonNull(source); this.draft = Objects.requireNonNull(draft);
        this.stage = Objects.requireNonNull(stage); this.hidden = Objects.requireNonNull(hidden);
        refresh();
    }
    /** Call on the client thread before painting. True means the host should remount this page. */
    public boolean refresh() {
        if (closed) return false;
        boolean registryChanged = registry != source.registry() || tokens != source.tokenCatalog();
        var settings = draft.get();
        if (!registryChanged && settings.equals(previousDraft)) return false;
        var nextIds = new ArrayList<String>(); nextIds.add("");
        DisplayStyleSuites.IDS.stream().filter(id -> !hidden.test(id) && source.registry().find(id).isPresent()).forEach(nextIds::add);
        source.registry().themes().keySet().stream().filter(id -> !hidden.test(id) && !DisplayStyleSuites.matches(id)).sorted().forEach(nextIds::add);
        boolean listChanged = !nextIds.equals(ids);
        registry = source.registry(); tokens = source.tokenCatalog(); ids = List.copyOf(nextIds);
        var nextPalettes = new LinkedHashMap<String, UiTheme>();
        for (var id : ids) {
            if (id.isEmpty()) {
                try { nextPalettes.put(id, UiTheme.resolve(settings.withRegisteredTheme(""))); }
                catch (IllegalArgumentException full) { nextPalettes.put(id, UiTheme.resolve(ThemeSettings.DEFAULT)); }
            } else {
                var raw = UiTheme.fromTokens(resolver.resolve(registry, tokens, List.of(id), null));
                nextPalettes.put(id, overrides(raw, settings, id));
            }
        }
        palettes = Map.copyOf(nextPalettes); previousDraft = settings;
        page = Math.min(page, pageCount() - 1);
        for (var card : mounted) { card.active = ids.contains(card.themeId()); card.refreshLabel(); }
        return listChanged;
    }
    private static UiTheme overrides(UiTheme base, ThemeSettings settings, String id) {
        var saved = settings.paletteArchive().get(id);
        boolean current = settings.registeredThemeId().equals(id);
        String[] rgb = current ? new String[]{settings.background(),settings.surface(),settings.border(),settings.text(),settings.accent()}
                : new String[]{saved.background(),saved.surface(),saved.border(),saved.text(),saved.accent()};
        return new UiTheme(base.light(), rgb(base.background(),rgb[0]),rgb(base.surface(),rgb[1]),
                rgb(base.border(),rgb[2]),rgb(base.text(),rgb[3]),rgb(base.accent(),rgb[4]),base.overrides(),
                base.blurRadius(),base.backdrop(),base.secondarySurface(),base.elevatedSurface(),base.overlaySurface());
    }
    private static int rgb(int base, String override) { return override.isEmpty() ? base : base & 0xff000000 | Integer.parseInt(override.substring(1),16); }
    public List<AbstractWidget> layout(Bounds area, UiPreferences preferences) {
        retireCards(); refresh();
        if (closed || area.width() < CARD_WIDTH || area.height() < CARD_HEIGHT) return List.of();
        int columns = Math.min(3, Math.max(1, (area.width() + GAP) / (CARD_WIDTH + GAP)));
        int rows = Math.min(3, Math.max(1, (area.height() + GAP) / (CARD_HEIGHT + GAP)));
        pageSize = columns * rows; page = Math.min(page, pageCount() - 1);
        int cardWidth = (area.width() - GAP * (columns - 1)) / columns;
        for (int n = page * pageSize; n < Math.min(ids.size(), (page + 1) * pageSize); n++) {
            int local = n - page * pageSize;
            var bounds = new Bounds(area.x() + local % columns * (cardWidth + GAP),
                    area.y() + local / columns * (CARD_HEIGHT + GAP), cardWidth, CARD_HEIGHT);
            mounted.add(new UiThemePreviewCard(bounds, ids.get(n), this, preferences));
        }
        return List.copyOf(mounted);
    }
    public List<String> themeIds() { return ids; }
    public int pageCount() { return Math.max(1, (ids.size() + pageSize - 1) / pageSize); }
    public int pageIndex() { return page; }
    public boolean page(int delta) { int next = Math.max(0, Math.min(pageCount()-1, page+delta)); boolean changed = next != page; page=next; return changed; }
    public String selectedId() {
        var settings = draft.get();
        return settings.mode() == ThemeSettings.Mode.CUSTOM ? settings.registeredThemeId()
                : settings.mode().fixedThemeId() != null ? settings.mode().fixedThemeId()
                : settings.mode() == ThemeSettings.Mode.LIGHT ? BuiltinThemes.LIGHT_ID : BuiltinThemes.DARK;
    }
    public Component error() { return error; }
    UiTheme palette(String id) { return palettes.get(id); }
    Component name(String id) { return id.isEmpty() ? Component.translatable("sh_better_ui.custom_theme.registered_custom")
            : registry.find(id).map(t -> Component.translatable(t.nameKey())).orElse(Component.literal(id)); }
    boolean select(String id) {
        if (closed || !ids.contains(id) || !id.isEmpty() && (hidden.test(id) || source.registry().find(id).isEmpty())) return false;
        try { stage.accept(draft.get().withRegisteredTheme(id)); }
        catch (IllegalArgumentException full) { error = Component.translatable("sh_better_ui.custom_theme.palette_capacity"); return false; }
        error = null; refresh(); return true;
    }
    private void retireCards() { mounted.forEach(card -> { card.active=false; card.visible=false; }); mounted.clear(); }
    @Override public void close() { closed=true; retireCards(); palettes=Map.of(); }
}
