// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiResourcePickerSources;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiWorkbench;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
import com.dishanhai.sh_better_ui.kernel.api.controls.IngredientSlotsState;
import com.dishanhai.sh_better_ui.kernel.api.controls.ResourcePickerState;
import com.dishanhai.sh_better_ui.kernel.api.controls.ResourcePickerState.Entry;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.client.api.UiWorkbench.button;

/** GTSH resource-picker interaction, composed entirely from public SHBUI/UUK controls. */
public final class UiResourcePicker {
    private enum Source { ITEMS, INVENTORY, BOOKMARKS }
    private static Catalog cachedItems;
    private static long cachedItemsRevision;
    private static Language cachedLanguage;
    private static WeakReference<ClientPacketListener> cachedConnection = new WeakReference<>(null);
    private static boolean cachedInWorld;

    private record Module(String id, String name, List<Entry<ItemStack>> entries) {}

    private static final class Catalog {
        private final List<Entry<ItemStack>> entries;
        private final List<Module> modules;
        private SearchIndex<Entry<ItemStack>> searchIndex;

        Catalog(List<Entry<ItemStack>> entries) {
            this.entries = entries;
            var groups = new TreeMap<String, List<Entry<ItemStack>>>();
            for (var entry : entries) groups.computeIfAbsent(entry.namespace(), ignored -> new ArrayList<>()).add(entry);
            var modules = new ArrayList<Module>();
            modules.add(new Module("", "", entries));
            groups.forEach((id, values) -> modules.add(new Module(id,
                    values.stream().map(Entry::mod).filter(name -> !name.isBlank()).findFirst().orElse(id),
                    List.copyOf(values))));
            this.modules = List.copyOf(modules);
        }

        SearchIndex<Entry<ItemStack>> searchIndex() {
            if (searchIndex == null) {
                searchIndex = new SearchIndex<>(entries.stream().map(entry -> new SearchIndex.Entry<>(entry,
                        entry.name().isBlank() ? entry.id() : entry.name(), List.of(entry.id()), List.of())).toList());
            }
            return searchIndex;
        }
    }

    private static Catalog itemCatalog() {
        var connection = Minecraft.getInstance().getConnection();
        var language = Language.getInstance();
        long revision = UiResourcePickerSources.itemsRevision();
        if (cachedItems == null || revision != cachedItemsRevision || cachedLanguage != language
                || cachedInWorld != (connection != null) || cachedConnection.get() != connection) {
            cachedItems = new Catalog(UiResourcePickerSources.items());
            cachedItemsRevision = revision;
            cachedLanguage = language;
            cachedConnection = new WeakReference<>(connection);
            cachedInWorld = connection != null;
        }
        return cachedItems;
    }

    private final Screen parent;
    private final Component title;
    private final Supplier<? extends GuiEventListener> returnFocus;
    private final Consumer<ItemStack> onConfirm;
    private Consumer<List<ItemStack>> onSequence;
    private int sequenceLimit;
    private final List<Entry<ItemStack>> sequence = new ArrayList<>();
    private final Runnable manualEntry;
    private final boolean quantities;
    private final ResourcePickerState<ItemStack> state;
    private final List<Catalog> sources;
    private final Map<Entry<ItemStack>, String> tooltips = new IdentityHashMap<>();
    private ResourcePickerState.Layout layout;
    private ResourcePickerState.Sort sort = ResourcePickerState.Sort.DEFAULT;
    private Source source = Source.ITEMS;
    private UiScreen screen;
    private UiIngredientSlots grid, preview;
    private UiTextField search, amount;
    private UiButton confirm, clear, sourceButton, sortButton, groupButton;
    private final VerticalScroll gridScroll = new VerticalScroll(), moduleScroll = new VerticalScroll();
    private UiVerticalScrollBar gridBar, moduleBar;
    private final List<UiButton> modules = new ArrayList<>();
    private final List<UiButton> sourceButtons = new ArrayList<>();
    private final List<UiButton> suggestions = new ArrayList<>();
    private List<Entry<ItemStack>> matches = List.of();
    private List<SearchIndex.Suggestion<Entry<ItemStack>>> options = List.of();
    private Module module;
    private String query = "", amountText = "1";
    private Component resultCount, chosenLabel, chosenId;
    private boolean grouped, validAmount = true;
    private IngredientSlotsState.Grid fit;
    private int firstVisible, shownStart = -1;

    private static final int ROW_STEP = 20, SCROLL_WIDTH = 5, SCROLL_GAP = 8;

    public static boolean open(Screen parent, Component title, Supplier<? extends GuiEventListener> returnFocus,
                               ItemStack initial, boolean quantities, Runnable manualEntry, Consumer<ItemStack> onConfirm) {
        if (parent == null || UiScreen.currentScreen() != parent) return false;
        new UiResourcePicker(parent, title, returnFocus, initial, quantities, manualEntry, onConfirm).open();
        return UiScreen.currentScreen() != parent;
    }

    public static boolean openSequence(Screen parent, Component title, Supplier<? extends GuiEventListener> returnFocus,
                                       int limit, Consumer<List<ItemStack>> onConfirm) {
        if (parent == null || UiScreen.currentScreen() != parent || limit < 1) return false;
        var picker = new UiResourcePicker(parent, title, returnFocus, ItemStack.EMPTY, false, null, ignored -> {});
        picker.sequenceLimit = limit;
        picker.onSequence = Objects.requireNonNull(onConfirm);
        picker.open();
        return UiScreen.currentScreen() != parent;
    }

    private UiResourcePicker(Screen parent, Component title, Supplier<? extends GuiEventListener> returnFocus,
                             ItemStack initial, boolean quantities, Runnable manualEntry, Consumer<ItemStack> onConfirm) {
        this.parent = parent; this.title = title.copy();
        this.returnFocus = Objects.requireNonNull(returnFocus);
        this.onConfirm = Objects.requireNonNull(onConfirm);
        this.manualEntry = manualEntry; this.quantities = quantities;
        state = new ResourcePickerState<>(initial.isEmpty() ? null : UiResourcePickerSources.entry("", initial, initial.getCount()));
        amountText = Integer.toString(quantities ? state.amount() : 1);
        sources = List.of(itemCatalog(), new Catalog(UiResourcePickerSources.inventory()),
                new Catalog(UiResourcePickerSources.bookmarks()));
        module = catalog().modules.get(0);
        state.source(module.entries());
    }

    private void open() {
        screen = UiWorkbench.builder(title, parent).onInit(this::init).onRender((owner, frame) -> {
            UiWorkbench.paintPopup(owner, frame, new Bounds(layout.x() - 6, layout.y() - 4,
                    layout.width() + 12, layout.height() + 8));
        }).onForeground(this::draw).build();
        screen.open();
    }

    private void init(UiScreen owner) {
        layout = ResourcePickerState.layout(owner.width, owner.height);
        modules.clear(); sourceButtons.clear(); suggestions.clear();
        var font = Minecraft.getInstance().font;
        var prefs = UiWorkbench.preferences(owner);
        int x = layout.x(), top = layout.y(), sx = layout.selectionX(), sw = layout.selectionWidth();
        search = owner.add(new UiTextField(font, new Bounds(layout.gridX(), top + 24, layout.gridWidth(), 20), text("search"), 256, prefs));
        search.setHint(text("search")); search.setValue(query);
        search.setRightClickClears(true);
        search.setTooltip(Tooltip.create(text("search_help")));
        search.setOnChange(value -> { query = value; resetGrid(); });
        int leftY = layout.contentY();
        if (layout.contentHeight() >= 130) {
            for (var value : Source.values()) {
                var b = button(owner, x, leftY, layout.sidebar(), 18, sourceLabel(value), () -> setSource(value));
                b.setLeadingIcon(sourceIcon(value));
                sourceButtons.add(b); leftY += 21;
            }
            sourceButton = null;
        } else {
            sourceButton = button(owner, x, leftY, layout.sidebar(), 18, text("source_cycle", sourceLabel(source)), () ->
                    setSource(Source.values()[(source.ordinal() + 1) % Source.values().length]));
            sourceButton.setLeadingIcon(sourceIcon(source));
            leftY += 21;
        }
        sortButton = button(owner, x, leftY, layout.sidebar(), 18, text("sort", sortLabel()), () -> {
            sort = ResourcePickerState.Sort.values()[(sort.ordinal() + 1) % ResourcePickerState.Sort.values().length];
            sortButton.setMessage(text("sort", sortLabel())); updateOrder();
        });
        sortButton.setLeadingIcon(GraphicIcon.SORT);
        leftY += 21;
        groupButton = button(owner, x, leftY, layout.sidebar(), 18, text("group"), () -> {
            grouped = !grouped; groupButton.setSelected(grouped); updateOrder();
        });
        groupButton.setLeadingIcon(GraphicIcon.MOD);
        groupButton.setSelected(grouped); leftY += 21;
        int moduleRows = Math.max(1, (layout.footerY() - leftY - 4) / 20);
        for (int i = 0; i < moduleRows; i++) {
            int index = i;
            modules.add(button(owner, x, leftY + i * ROW_STEP, layout.sidebar() - SCROLL_GAP, 18, Component.empty(), () -> {
                int at = (int) Math.round(moduleScroll.offset() / ROW_STEP) + index;
                if (at < catalog().modules.size()) setModule(catalog().modules.get(at));
            }));
        }
        int moduleTop = leftY;
        var moduleArea = new Bounds(x, moduleTop, layout.sidebar(), moduleRows * ROW_STEP - 2);
        moduleBar = owner.add(new UiVerticalScrollBar(new Bounds(moduleArea.right() - SCROLL_WIDTH,
                moduleArea.y(), SCROLL_WIDTH, moduleArea.height()), text("help"), moduleScroll, ROW_STEP, this::updateModules));
        var gridArea = new Bounds(layout.gridX(), layout.contentY(), layout.gridWidth(), layout.contentHeight());
        fit = IngredientSlotsState.fit(Math.max(0, gridArea.width() - SCROLL_GAP), gridArea.height(), 16, 18, 18, 2);
        grid = null;
        gridBar = null;
        if (fit.capacity() > 0) {
            int gridHeight = fit.rows() * ROW_STEP - 2;
            grid = owner.add(new UiIngredientSlots(font, new Bounds(gridArea.x(), gridArea.y(), fit.columns() * ROW_STEP - 2, gridHeight),
                    text("resources"), fit.columns(), Collections.nCopies(fit.capacity(), ItemStack.EMPTY), prefs,
                    index -> choose(index, false), index -> choose(index, true), (a,b) -> {}));
            grid.setAllowDrag(false);
            grid.setRounded(true);
            gridBar = owner.add(new UiVerticalScrollBar(new Bounds(gridArea.right() - SCROLL_WIDTH, gridArea.y(),
                    SCROLL_WIDTH, gridHeight), text("resources"), gridScroll, ROW_STEP, this::updateGrid));
        }
        owner.onScroll((mouseX, mouseY, horizontal, vertical) -> {
            if (moduleArea.contains(mouseX, mouseY)) return moduleBar.scrollByWheel(vertical);
            return gridBar != null && gridArea.contains(mouseX, mouseY) && gridBar.scrollByWheel(vertical);
        });
        int previewColumns = onSequence == null ? 1 : Math.min(3, sequenceLimit);
        int previewRows = onSequence == null ? 1 : (sequenceLimit + previewColumns - 1) / previewColumns;
        preview = owner.add(new UiIngredientSlots(font, new Bounds(sx, layout.contentY() + 2,
                onSequence == null ? 22 : previewColumns * 20 - 2, onSequence == null ? 22 : previewRows * 20 - 2), text("selected"),
                previewColumns, java.util.Collections.nCopies(onSequence == null ? 1 : sequenceLimit, ItemStack.EMPTY), prefs,
                ignored -> {}, this::removeSelection, this::moveSelection));
        preview.setAllowDrag(onSequence != null);
        preview.setDropTarget((from, to) -> from < sequence.size() ? Math.min(to, sequence.size() - 1) : -1);
        preview.setRounded(true);
        int ay = layout.contentY() + 39;
        amount = owner.add(new UiTextField(font, new Bounds(sx + 22, ay, Math.max(24, sw - 44), 18), text("quantity"), 10, prefs));
        amount.setValue(amountText);
        amount.setOnChange(value -> { amountText = value; validAmount = state.amount(value); updateControls(); });
        var minus = button(owner, sx, ay, 18, 18, Component.literal("−"), () -> step(-1)); minus.active = quantities;
        var plus = button(owner, sx + sw - 18, ay, 18, 18, Component.literal("+"), () -> step(1)); plus.active = quantities;
        if (onSequence != null) { amount.visible = false; minus.visible = false; plus.visible = false; }
        int clearY = onSequence == null ? ay + 23 : layout.contentY() + 6 + previewRows * 20;
        clear = button(owner, sx, clearY, sw, 18, text("clear"), this::clear);
        clear.setLeadingIcon(GraphicIcon.DELETE);
        int suggestionRows = Math.min(3, Math.max(0, (layout.contentHeight() - 126) / 20));
        for (int i = 0; i < suggestionRows; i++) {
            int row = i;
            var suggestion = button(owner, sx, layout.contentY() + 122 + i * 20, sw, 18, Component.empty(), () -> {
                if (row >= options.size()) return;
                query = options.get(row).completion();
                search.setValue(query);
                resetGrid();
                owner.setFocused(search);
            });
            suggestions.add(suggestion);
        }
        int footer = layout.footerY(), half = (sw - 4) / 2;
        button(owner, sx, footer, half, 20, text("cancel"), () -> finish(false)).setLeadingIcon(GraphicIcon.CANCEL);
        confirm = button(owner, sx + half + 4, footer, sw - half - 4, 20, text("confirm"), () -> finish(true));
        confirm.setLeadingIcon(GraphicIcon.SUCCESS);
        if (manualEntry != null) button(owner, x, footer, layout.sidebar(), 20, text("manual"), () -> {
            finish(false); manualEntry.run();
        }).setLeadingIcon(GraphicIcon.EDIT);
        for (var child : owner.children()) if (child instanceof UiButton button) button.setCornerRadius(4);
        updateSources(); resizeModules(); filter(); updateControls();
        owner.setFocused(search);
    }

    private void setSource(Source value) {
        source = value;
        moduleScroll.scrollTo(0);
        updateSources();
        resizeModules();
        setModule(catalog().modules.get(0));
    }
    private Catalog catalog() { return sources.get(source.ordinal()); }
    private void setModule(Module value) {
        module = value;
        updateModules();
        updateOrder();
    }
    private void updateSources() {
        if (sourceButton != null) {
            sourceButton.setMessage(text("source_cycle", sourceLabel(source)));
            sourceButton.setLeadingIcon(sourceIcon(source));
        }
        for (int i = 0; i < sourceButtons.size(); i++) sourceButtons.get(i).setSelected(i == source.ordinal());
    }
    private void resizeModules() {
        moduleScroll.resize(modules.size() * ROW_STEP, catalog().modules.size() * ROW_STEP);
        moduleBar.active = moduleBar.visible = moduleScroll.maxOffset() > 0;
        updateModules();
    }
    private void updateModules() {
        var groups = catalog().modules;
        int first = (int) Math.round(moduleScroll.offset() / ROW_STEP);
        for (int i = 0; i < modules.size(); i++) {
            int at = first + i;
            var b = modules.get(i); b.visible = b.active = at < groups.size();
            if (!b.visible) continue;
            var group = groups.get(at);
            b.setMessage(text("module", at == 0 ? text("all_mods") : Component.literal(group.name()), group.entries().size()));
            b.setSelected(group == module);
        }
    }
    private void resetGrid() {
        gridScroll.scrollTo(0);
        filter();
    }
    private void updateOrder() {
        state.source(module.entries());
        state.source(state.search("", "", sort, grouped && module == catalog().modules.get(0)));
        resetGrid();
    }
    private void filter() {
        Map<String, Predicate<Entry<ItemStack>>> textMatchers = new HashMap<>();
        Function<String, Predicate<Entry<ItemStack>>> prepareText = word -> catalog().searchIndex().plainTextMatcher(word);
        matches = state.search(query, "", ResourcePickerState.Sort.DEFAULT, false,
                (entry, word) -> textMatchers.computeIfAbsent(word, prepareText).test(entry),
                entry -> tooltips.computeIfAbsent(entry, value -> value.tooltip().get()));
        updateSuggestions();
        resultCount = text("matches", matches.size(), catalog().entries.size());
        shownStart = -1;
        if (gridBar != null) {
            int totalRows = (matches.size() + fit.columns() - 1) / fit.columns();
            gridScroll.resize(fit.rows() * ROW_STEP, totalRows * ROW_STEP);
            gridBar.active = gridBar.visible = gridScroll.maxOffset() > 0;
            updateGrid();
        }
    }
    private void updateGrid() {
        firstVisible = (int) Math.round(gridScroll.offset() / ROW_STEP) * fit.columns();
        if (firstVisible == shownStart) return;
        shownStart = firstVisible;
        int capacity = fit.capacity();
        var entries = matches.subList(firstVisible, Math.min(firstVisible + capacity, matches.size()));
        var stacks = new ArrayList<ItemStack>(capacity);
        for (var entry : entries) stacks.add(entry.value());
        stacks.addAll(Collections.nCopies(capacity - stacks.size(), ItemStack.EMPTY));
        grid.setItems(stacks);
        var names = new Component[entries.size()];
        grid.setSlotNameProvider(index -> {
            if (index >= names.length) return Component.empty();
            if (names[index] == null) {
                var entry = entries.get(index);
                names[index] = entryLabel(entry).copy().append("\n").append(Component.literal(entry.id())
                        .withStyle(style -> style.withColor(UiWorkbench.secondaryTextColor() & 0xFFFFFF)));
            }
            return names[index];
        });
    }
    private void updateSuggestions() {
        options = query.isBlank() || matches.isEmpty() || suggestions.isEmpty() ? List.of()
                : catalog().searchIndex().suggestMatches(query, matches, suggestions.size());
        for (int i = 0; i < suggestions.size(); i++) {
            var button = suggestions.get(i);
            button.visible = button.active = i < options.size();
            if (button.visible) button.setMessage(Component.literal(options.get(i).label()));
        }
    }
    private void choose(int index, boolean toggle) {
        int at = firstVisible + index;
        if (at >= matches.size()) return;
        if (onSequence != null) {
            if (toggle) { if (!sequence.isEmpty()) sequence.remove(sequence.size() - 1); }
            else if (sequence.size() < sequenceLimit) sequence.add(matches.get(at));
            updateControls(); return;
        }
        state.choose(matches.get(at), toggle);
        amountText = Integer.toString(quantities ? state.amount() : 1);
        amount.setValue(amountText); validAmount = true;
        updateControls();
    }
    private void clear() { if (onSequence == null) state.clear(); else sequence.clear(); updateControls(); }
    private void removeSelection(int index) {
        if (onSequence == null) clear();
        else if (index < sequence.size()) {
            sequence.remove(index);
            updateControls();
        }
    }
    private void moveSelection(int from, int to) {
        sequence.add(to, sequence.remove(from));
        updateControls();
    }
    private void step(int direction) {
        state.stepAmount(direction, 1); amountText = Integer.toString(state.amount());
        amount.setValue(amountText); validAmount = true; updateControls();
    }
    private void updateControls() {
        var entry = state.selected();
        if (onSequence != null) {
            var stacks = new ArrayList<ItemStack>(); var labels = new ArrayList<Component>();
            for (int i = 0; i < sequenceLimit; i++) {
                var item = i < sequence.size() ? sequence.get(i) : null;
                stacks.add(item == null ? ItemStack.EMPTY : item.value().copy());
                labels.add(item == null ? text("empty") : entryLabel(item));
            }
            preview.setItems(stacks); preview.setSlotNames(labels);
            confirm.active = !sequence.isEmpty(); clear.active = !sequence.isEmpty();
            chosenLabel = text("sequence_count", sequence.size(), sequenceLimit);
            return;
        }
        var stack = entry == null ? ItemStack.EMPTY : entry.value().copy();
        if (!stack.isEmpty()) stack.setCount(quantities ? state.amount() : 1);
        preview.setItems(List.of(stack));
        chosenLabel = entry == null ? text("empty") : entryLabel(entry);
        chosenId = entry == null ? text("single") : Component.literal(entry.id());
        preview.setSlotNames(List.of(entry == null ? chosenLabel : chosenLabel.copy().append("\n")
                .append(Component.literal(entry.id()).withStyle(style -> style.withColor(UiWorkbench.secondaryTextColor() & 0xFFFFFF)))));
        confirm.active = entry != null && validAmount;
        clear.active = entry != null;
        amount.active = quantities && entry != null;
        amount.setError(validAmount ? null : text("invalid_quantity"));
    }
    private void finish(boolean confirmed) {
        if (UiScreen.currentScreen() != screen || state.finished() || confirmed && !validAmount) return;
        var selected = state.finish(confirmed && onSequence == null);
        screen.onClose();
        if (UiScreen.currentScreen() != parent) return;
        var focus = returnFocus.get();
        if (focus != null && parent.children().contains(focus)) parent.setFocused(focus);
        if (onSequence != null && confirmed && !sequence.isEmpty()) {
            onSequence.accept(sequence.stream().map(entry -> entry.value().copy()).toList());
            return;
        }
        selected.ifPresent(entry -> {
            var copy = entry.value().copy(); copy.setCount(quantities ? state.amount() : 1); onConfirm.accept(copy);
        });
    }
    private Component sourceLabel(Source value) { return text("source", text("source." + value.name().toLowerCase(Locale.ROOT)), sources.get(value.ordinal()).entries.size()); }
    private Component sortLabel() { return text("sort." + sort.name().toLowerCase(java.util.Locale.ROOT)); }
    private static GraphicIcon sourceIcon(Source source) {
        return switch (source) { case ITEMS -> GraphicIcon.ITEM; case INVENTORY -> GraphicIcon.INPUT; case BOOKMARKS -> GraphicIcon.BOOKMARK; };
    }
    private static Component entryLabel(Entry<ItemStack> entry) {
        return UiRichText.withItem(entry.id(), entry.value().getHoverName().copy().withStyle(style -> style.withBold(true)));
    }

    private void draw(UiScreen owner, UiFrame frame) {
        int sx = layout.selectionX(), sy = layout.contentY(), sw = layout.selectionWidth();
        line(frame, title.copy().withStyle(style -> style.withBold(true)), layout.x(), layout.y() + 4, layout.width(), UiWorkbench.textColor());
        line(frame, text("help"), layout.x(), layout.y() + 25, layout.sidebar(), UiWorkbench.secondaryTextColor());
        line(frame, text("selected").copy().withStyle(style -> style.withBold(true)), sx, layout.y() + 25, sw, UiWorkbench.accentColor());
        if (onSequence != null) {
            line(frame, chosenLabel, sx + Math.min(3, sequenceLimit) * 20 + 4, sy + 4,
                    sw - Math.min(3, sequenceLimit) * 20 - 4, UiWorkbench.textColor());
        } else {
            line(frame, chosenLabel, sx + 26, sy + 4, sw - 26, UiWorkbench.textColor());
            line(frame, chosenId, sx, sy + 27, sw, UiWorkbench.secondaryTextColor());
        }
        if (layout.contentHeight() > 110) {
            line(frame, resultCount, sx, sy + 90, sw, UiWorkbench.secondaryTextColor());
            line(frame, text(onSequence != null ? "sequence_help" : quantities ? "quantity_help" : "single"), sx, sy + 105, sw, UiWorkbench.secondaryTextColor());
        }
        if (matches.isEmpty()) line(frame, text(source == Source.BOOKMARKS && !UiResourcePickerSources.bookmarksAvailable() ? "no_jei" : "no_matches"),
                layout.gridX(), sy + 6, layout.gridWidth(), UiWorkbench.secondaryTextColor());
    }
    private static void line(UiFrame frame, Component value, int x, int y, int width, int color) {
        if (width < 1) return;
        var font = Minecraft.getInstance().font;
        frame.drawScaledText(font, UiRichText.fit(font, value, width), x, y, color, 1);
    }
    private static Component text(String key, Object... args) { return Component.translatable("sh_better_ui.resource_picker." + key, args); }
}
