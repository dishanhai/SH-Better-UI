// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.UiWorkbench;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.screen.LayeredScreen;
import com.dishanhai.sh_better_ui.kernel.api.controls.IngredientSlotsState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Searchable and paginated registry fallback for versions without the JEI ingredient picker. */
public final class UiRegistryItemPicker extends LayeredScreen {
    private static final int COLUMNS = 6, ROWS = 6;
    private final Screen parent;
    private final Supplier<? extends GuiEventListener> returnFocus;
    private final Consumer<ItemStack> onSelect;
    private final List<Map.Entry<String, ItemStack>> all;
    private List<Map.Entry<String, ItemStack>> matches;
    private UiIngredientSlots grid;
    private UiButton previous, next;
    private int page;
    private int pageSize;
    private String query = "";

    public UiRegistryItemPicker(Screen parent, Component title, Supplier<? extends GuiEventListener> returnFocus,
                                Consumer<ItemStack> onSelect) {
        super(title);
        this.parent = Objects.requireNonNull(parent);
        this.returnFocus = Objects.requireNonNull(returnFocus);
        this.onSelect = Objects.requireNonNull(onSelect);
        all = List.copyOf(UiWorkbench.itemIcons().entrySet());
        matches = all;
    }

    @Override protected void init() {
        var prefs = UiPreferences.configured();
        int x = 8, availableWidth = Math.max(1, width - 16);
        var search = addRenderableWidget(new UiTextField(font, new Bounds(x, 22, availableWidth, 20),
                Component.translatable("sh_better_ui.search.label"), 256, prefs));
        search.setHint(Component.translatable("sh_better_ui.search.label"));
        search.setValue(query);
        search.setOnChange(this::search);
        int bottom = Math.max(44, height - 27);
        int gridHeight = Math.max(0, bottom - 47);
        var fit = IngredientSlotsState.fit(availableWidth, gridHeight, COLUMNS, ROWS, 18, 2);
        pageSize = fit.capacity();
        grid = null;
        if (pageSize > 0) {
            int gridWidth = fit.columns() * 20 - 2;
            grid = addRenderableWidget(new UiIngredientSlots(font, new Bounds((width - gridWidth) / 2, 46, gridWidth, fit.rows() * 20 - 2),
                    title, fit.columns(), java.util.Collections.nCopies(pageSize, ItemStack.EMPTY), prefs,
                    this::choose, ignored -> {}, (from, to) -> {}));
            grid.setAllowDrag(false);
        }
        int buttonWidth = Math.max(1, (availableWidth - 8) / 3);
        previous = addRenderableWidget(new UiButton(x, bottom, buttonWidth, 18,
                Component.translatable("sh_better_ui.pagination.previous"), button -> changePage(-1)));
        next = addRenderableWidget(new UiButton(x + buttonWidth + 4, bottom, buttonWidth, 18,
                Component.translatable("sh_better_ui.pagination.next"), button -> changePage(1)));
        addRenderableWidget(new UiButton(x + 2 * (buttonWidth + 4), bottom,
                Math.max(1, availableWidth - 2 * (buttonWidth + 4)), 18,
                Component.translatable("gui.back"), button -> onClose()));
        refresh();
        setInitialFocus(search);
    }

    private void search(String value) {
        query = value;
        String term = value.strip().toLowerCase(Locale.ROOT);
        matches = all.stream().filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).contains(term)
                || entry.getValue().getHoverName().getString().toLowerCase(Locale.ROOT).contains(term)).toList();
        page = 0;
        refresh();
    }

    private int pages() { return pageSize == 0 ? 1 : Math.max(1, (matches.size() + pageSize - 1) / pageSize); }
    private void changePage(int change) { page = Math.max(0, Math.min(pages() - 1, page + change)); refresh(); }
    private void refresh() {
        page = Math.min(page, pages() - 1);
        if (grid == null) { previous.active = false; next.active = false; return; }
        var stacks = new ArrayList<ItemStack>(pageSize);
        var labels = new ArrayList<Component>(pageSize);
        for (int i = 0; i < pageSize; i++) {
            int index = page * pageSize + i;
            if (index < matches.size()) {
                var entry = matches.get(index);
                stacks.add(entry.getValue());
                labels.add(entry.getValue().getHoverName().copy().append(" (" + entry.getKey() + ")"));
            } else {
                stacks.add(ItemStack.EMPTY);
                labels.add(Component.empty());
            }
        }
        grid.setItems(stacks);
        grid.setSlotNames(labels);
        previous.active = page > 0;
        next.active = page + 1 < pages();
    }

    private void choose(int index) {
        int selected = page * pageSize + index;
        if (selected >= matches.size()) return;
        var value = matches.get(selected).getValue().copy();
        onClose();
        onSelect.accept(value);
    }

    @Override public void onClose() {
        var client = Minecraft.getInstance();
        if (client.screen != this) return;
        client.setScreen(parent);
        var focus = returnFocus.get();
        if (client.screen == parent && focus != null && parent.children().contains(focus)) parent.setFocused(focus);
    }

    @Override protected void renderBackdrop(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        graphics.fill(0, 0, width, height, UiTheme.current().background() | 0xFF000000);
    }

    @Override protected void renderForeground(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        var label = Component.translatable("sh_better_ui.pagination.narration", title, page + 1, pages());
        graphics.text(font, label, 8, 8, UiTheme.current().text(), false);
        if (pageSize == 0) graphics.text(font, Component.translatable("sh_better_ui.picker.too_small"), 8, 47,
                UiTheme.current().text(), false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
