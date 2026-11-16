// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiResourcePickerSources;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiWorkbench;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import com.dishanhai.sh_better_ui.kernel.api.controls.IngredientSlotsState;
import com.dishanhai.sh_better_ui.kernel.api.controls.ResourcePickerState;
import com.dishanhai.sh_better_ui.kernel.api.controls.ResourcePickerState.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.client.api.UiWorkbench.button;

/** GTSH resource-picker interaction, composed entirely from public SHBUI/UUK controls. */
public final class UiResourcePicker {
    private enum Source { ITEMS, INVENTORY, BOOKMARKS }
    private final Screen parent;
    private final Component title;
    private final Supplier<? extends GuiEventListener> returnFocus;
    private final Consumer<ItemStack> onConfirm;
    private final Runnable manualEntry;
    private final boolean quantities;
    private final ResourcePickerState<ItemStack> state;
    private final List<List<Entry<ItemStack>>> sources;
    private ResourcePickerState.Layout layout;
    private ResourcePickerState.Sort sort = ResourcePickerState.Sort.DEFAULT;
    private Source source = Source.ITEMS;
    private UiScreen screen;
    private UiIngredientSlots grid, preview;
    private UiTextField search, amount;
    private UiButton previous, next, confirm, clear, sourceButton, sortButton, groupButton;
    private final List<UiButton> modules = new ArrayList<>();
    private final List<UiButton> sourceButtons = new ArrayList<>();
    private List<Entry<ItemStack>> matches = List.of();
    private List<String> moduleIds = List.of();
    private String query = "", module = "", amountText = "1";
    private boolean grouped, validAmount = true;
    private int page, pageSize, modulePage;

    public static boolean open(Screen parent, Component title, Supplier<? extends GuiEventListener> returnFocus,
                               ItemStack initial, boolean quantities, Runnable manualEntry, Consumer<ItemStack> onConfirm) {
        var client = Minecraft.getInstance();
        if (parent == null || client.screen != parent) return false;
        new UiResourcePicker(parent, title, returnFocus, initial, quantities, manualEntry, onConfirm).open();
        return client.screen != parent;
    }

    private UiResourcePicker(Screen parent, Component title, Supplier<? extends GuiEventListener> returnFocus,
                             ItemStack initial, boolean quantities, Runnable manualEntry, Consumer<ItemStack> onConfirm) {
        this.parent = parent; this.title = title.copy();
        this.returnFocus = Objects.requireNonNull(returnFocus);
        this.onConfirm = Objects.requireNonNull(onConfirm);
        this.manualEntry = manualEntry; this.quantities = quantities;
        state = new ResourcePickerState<>(initial.isEmpty() ? null : UiResourcePickerSources.entry("", initial, initial.getCount()));
        amountText = Integer.toString(quantities ? state.amount() : 1);
        sources = List.of(UiResourcePickerSources.items(), UiResourcePickerSources.inventory(), UiResourcePickerSources.bookmarks());
        state.source(sources.get(0));
    }

    private void open() {
        screen = UiWorkbench.builder(title, parent).onInit(this::init).onForeground(this::draw).build();
        screen.open();
    }

    private void init(UiScreen owner) {
        layout = ResourcePickerState.layout(owner.width, owner.height);
        modules.clear(); sourceButtons.clear();
        var font = Minecraft.getInstance().font;
        var prefs = UiWorkbench.preferences(owner);
        int x = layout.x(), top = layout.y(), sx = layout.selectionX(), sw = layout.selectionWidth();
        search = owner.add(new UiTextField(font, new Bounds(layout.gridX(), top + 24, layout.gridWidth(), 20), text("search"), 256, prefs));
        search.setHint(text("search")); search.setValue(query);
        search.setTooltip(Tooltip.create(text("search_help")));
        search.setOnChange(value -> { query = value; page = 0; filter(); });
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
            sortButton.setMessage(text("sort", sortLabel())); page = 0; filter();
        });
        sortButton.setLeadingIcon(GraphicIcon.SORT);
        leftY += 21;
        groupButton = button(owner, x, leftY, layout.sidebar(), 18, text("group"), () -> {
            grouped = !grouped; groupButton.setSelected(grouped); page = 0; filter();
        });
        groupButton.setLeadingIcon(GraphicIcon.MOD);
        groupButton.setSelected(grouped); leftY += 21;
        int moduleRows = Math.max(1, (layout.footerY() - leftY - 4) / 20);
        for (int i = 0; i < moduleRows; i++) {
            int index = i;
            modules.add(button(owner, x, leftY + i * 20, layout.sidebar(), 18, Component.empty(), () -> {
                int at = modulePage * modules.size() + index;
                if (at < moduleIds.size()) { module = moduleIds.get(at); page = 0; filter(); }
            }));
        }
        var fit = IngredientSlotsState.fit(layout.gridWidth(), layout.contentHeight(), 16, 18, 18, 2);
        pageSize = fit.capacity();
        grid = null;
        if (pageSize > 0) {
            grid = owner.add(new UiIngredientSlots(font, new Bounds(layout.gridX(), layout.contentY(), fit.columns() * 20 - 2, fit.rows() * 20 - 2),
                    text("resources"), fit.columns(), java.util.Collections.nCopies(pageSize, ItemStack.EMPTY), prefs,
                    index -> choose(index, false), index -> choose(index, true), (a,b) -> {}));
            grid.setAllowDrag(false);
        }
        preview = owner.add(new UiIngredientSlots(font, new Bounds(sx, layout.contentY() + 2, 22, 22), text("selected"),
                1, List.of(ItemStack.EMPTY), prefs, ignored -> {}, ignored -> clear(), (a,b) -> {}));
        preview.setAllowDrag(false);
        int ay = layout.contentY() + 39;
        amount = owner.add(new UiTextField(font, new Bounds(sx + 22, ay, Math.max(24, sw - 44), 18), text("quantity"), 10, prefs));
        amount.setValue(amountText);
        amount.setOnChange(value -> { amountText = value; validAmount = state.amount(value); updateControls(); });
        button(owner, sx, ay, 18, 18, Component.literal("−"), () -> step(-1)).active = quantities;
        button(owner, sx + sw - 18, ay, 18, 18, Component.literal("+"), () -> step(1)).active = quantities;
        clear = button(owner, sx, ay + 23, sw, 18, text("clear"), this::clear);
        clear.setLeadingIcon(GraphicIcon.DELETE);
        int footer = layout.footerY(), half = (sw - 4) / 2;
        button(owner, sx, footer, half, 20, text("cancel"), () -> finish(false)).setLeadingIcon(GraphicIcon.CANCEL);
        confirm = button(owner, sx + half + 4, footer, sw - half - 4, 20, text("confirm"), () -> finish(true));
        confirm.setLeadingIcon(GraphicIcon.SUCCESS);
        int navWidth = Math.max(18, (layout.gridWidth() - 4) / 2);
        previous = button(owner, layout.gridX(), footer, navWidth, 20, text("previous"), () -> changePage(-1));
        next = button(owner, layout.gridX() + navWidth + 4, footer, Math.max(18, layout.gridWidth() - navWidth - 4), 20,
                text("next"), () -> changePage(1));
        previous.setLeadingIcon(GraphicIcon.PREVIOUS); next.setTrailingIcon(GraphicIcon.NEXT);
        int moreWidth = manualEntry == null ? layout.sidebar() : 18;
        button(owner, x, footer, moreWidth, 20, text("modules"), () -> { modulePage++; updateModules(); }).setPreview(text("more_modules"));
        if (manualEntry != null) button(owner, x + 22, footer, Math.max(18, layout.sidebar() - 22), 20, text("manual"), () -> {
            finish(false); manualEntry.run();
        }).setLeadingIcon(GraphicIcon.EDIT);
        rebuildModules(); filter();
        owner.setFocused(search);
    }

    private void setSource(Source value) {
        source = value; state.source(sources.get(value.ordinal()));
        module = ""; page = 0; modulePage = 0;
        rebuildModules(); filter();
    }
    private void rebuildModules() {
        var ids = new ArrayList<String>(); ids.add("");
        state.source().stream().map(Entry::namespace).distinct().sorted().forEach(ids::add);
        moduleIds = List.copyOf(ids);
        updateModules();
    }
    private void updateModules() {
        int pages = Math.max(1, (moduleIds.size() + modules.size() - 1) / modules.size());
        modulePage = Math.floorMod(modulePage, pages);
        for (int i = 0; i < modules.size(); i++) {
            int at = modulePage * modules.size() + i;
            var b = modules.get(i); b.visible = b.active = at < moduleIds.size();
            if (!b.visible) continue;
            String id = moduleIds.get(at);
            long count = state.source().stream().filter(entry -> id.isEmpty() || entry.namespace().equals(id)).count();
            String name = state.source().stream().filter(entry -> entry.namespace().equals(id)).map(Entry::mod)
                    .filter(value -> !value.isBlank()).findFirst().orElse(id);
            b.setMessage(text("module", id.isEmpty() ? text("all_mods") : Component.literal(name), count));
            b.setSelected(id.equals(module));
        }
    }
    private void filter() {
        matches = state.search(query, module, sort, grouped);
        page = Math.max(0, Math.min(page, pages() - 1));
        var stacks = new ArrayList<ItemStack>(); var names = new ArrayList<Component>();
        for (int i = 0; i < pageSize; i++) {
            int at = page * pageSize + i;
            var entry = at < matches.size() ? matches.get(at) : null;
            stacks.add(entry == null ? ItemStack.EMPTY : entry.value());
            names.add(entry == null ? Component.empty() : entryLabel(entry).copy().append("\n")
                    .append(Component.literal(entry.id()).withStyle(style -> style.withColor(UiWorkbench.secondaryTextColor() & 0xFFFFFF))));
        }
        if (grid != null) { grid.setItems(stacks); grid.setSlotNames(names); }
        updateModules(); updateControls();
    }
    private int pages() { return pageSize == 0 ? 1 : Math.max(1, (matches.size() + pageSize - 1) / pageSize); }
    private void changePage(int delta) { page = Math.max(0, Math.min(pages() - 1, page + delta)); filter(); }
    private void choose(int index, boolean toggle) {
        int at = page * pageSize + index;
        if (at >= matches.size()) return;
        state.choose(matches.get(at), toggle);
        amountText = Integer.toString(quantities ? state.amount() : 1);
        amount.setValue(amountText); validAmount = true;
        updateControls();
    }
    private void clear() { state.clear(); updateControls(); }
    private void step(int direction) {
        state.stepAmount(direction, 1); amountText = Integer.toString(state.amount());
        amount.setValue(amountText); validAmount = true; updateControls();
    }
    private void updateControls() {
        var entry = state.selected();
        var stack = entry == null ? ItemStack.EMPTY : entry.value().copy();
        if (!stack.isEmpty()) stack.setCount(quantities ? state.amount() : 1);
        preview.setItems(List.of(stack));
        preview.setSlotNames(List.of(entry == null ? text("empty") : entryLabel(entry).copy().append("\n")
                .append(Component.literal(entry.id()).withStyle(style -> style.withColor(UiWorkbench.secondaryTextColor() & 0xFFFFFF)))));
        confirm.active = entry != null && validAmount;
        clear.active = entry != null;
        amount.active = quantities && entry != null;
        amount.setError(validAmount ? null : text("invalid_quantity"));
        previous.active = page > 0; next.active = page + 1 < pages();
        if (sourceButton != null) { sourceButton.setMessage(text("source_cycle", sourceLabel(source))); sourceButton.setLeadingIcon(sourceIcon(source)); }
        for (int i = 0; i < sourceButtons.size(); i++) sourceButtons.get(i).setSelected(i == source.ordinal());
    }
    private void finish(boolean confirmed) {
        if (Minecraft.getInstance().screen != screen || state.finished() || confirmed && !validAmount) return;
        var selected = state.finish(confirmed);
        screen.onClose();
        if (Minecraft.getInstance().screen != parent) return;
        var focus = returnFocus.get();
        if (focus != null && parent.children().contains(focus)) parent.setFocused(focus);
        selected.ifPresent(entry -> {
            var copy = entry.value().copy(); copy.setCount(quantities ? state.amount() : 1); onConfirm.accept(copy);
        });
    }
    private Component sourceLabel(Source value) { return text("source", text("source." + value.name().toLowerCase(java.util.Locale.ROOT)), sources.get(value.ordinal()).size()); }
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
        var entry = state.selected();
        line(frame, entry == null ? text("empty") : entryLabel(entry), sx + 26, sy + 4, sw - 26, UiWorkbench.textColor());
        line(frame, entry == null ? text("single") : Component.literal(entry.id()), sx, sy + 27, sw, UiWorkbench.secondaryTextColor());
        if (layout.contentHeight() > 110) {
            line(frame, text("matches", matches.size(), state.source().size(), page + 1, pages()), sx, sy + 90, sw, UiWorkbench.secondaryTextColor());
            line(frame, text(quantities ? "quantity_help" : "single"), sx, sy + 105, sw, UiWorkbench.secondaryTextColor());
        }
        if (matches.isEmpty()) line(frame, text(source == Source.BOOKMARKS && !UiResourcePickerSources.bookmarksAvailable() ? "no_jei" : "no_matches"),
                layout.gridX(), sy + 6, layout.gridWidth(), UiWorkbench.secondaryTextColor());
        previous.setPreview(text("page", page + 1, pages())); next.setPreview(text("page", page + 1, pages()));
    }
    private static void line(UiFrame frame, Component value, int x, int y, int width, int color) {
        if (width < 1) return;
        var font = Minecraft.getInstance().font;
        frame.drawScaledText(font, UiRichText.fit(font, value, width), x, y, color, 1);
    }
    private static Component text(String key, Object... args) { return Component.translatable("sh_better_ui.resource_picker." + key, args); }
}
