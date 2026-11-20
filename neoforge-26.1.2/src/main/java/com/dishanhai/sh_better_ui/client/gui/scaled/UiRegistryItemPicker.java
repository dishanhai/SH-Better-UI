// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.UiWorkbench;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.screen.LayeredScreen;
import com.dishanhai.sh_better_ui.kernel.internal.NativePort;
import com.dishanhai.sh_better_ui.kernel.api.controls.IngredientSlotsState;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
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
    private final VerticalScroll scroll = new VerticalScroll();
    private UiVerticalScrollBar scrollbar;
    private IngredientSlotsState.Grid fit;
    private int first;
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
        fit = IngredientSlotsState.fit(Math.max(0, availableWidth - UiScrollingRows.TRACK_SPACE), gridHeight, COLUMNS, ROWS, 18, 2);
        grid = null;
        scrollbar = null;
        if (fit.capacity() > 0) {
            int gridWidth = fit.columns() * 20 - 2;
            int left = (width - gridWidth - UiScrollingRows.TRACK_SPACE) / 2;
            int height = fit.rows() * 20 - 2;
            grid = addRenderableWidget(new UiIngredientSlots(font, new Bounds(left, 46, gridWidth, height),
                    title, fit.columns(), java.util.Collections.nCopies(fit.capacity(), ItemStack.EMPTY), prefs,
                    this::choose, ignored -> {}, (from, to) -> {}));
            grid.setAllowDrag(false);
            grid.setRounded(true);
            scrollbar = addRenderableWidget(new UiVerticalScrollBar(new Bounds(left + gridWidth + 3, 46, 5, height),
                    title, scroll, 20, this::refresh));
        }
        addRenderableWidget(new UiButton(x, bottom, availableWidth, 18,
                Component.translatable("gui.back"), button -> onClose()));
        for (var child : children()) if (child instanceof UiButton button) button.setCornerRadius(4);
        resizeScroll();
        setInitialFocus(search);
    }

    private void search(String value) {
        query = value;
        String term = value.strip().toLowerCase(Locale.ROOT);
        matches = term.isEmpty() ? all : all.stream().filter(entry -> entry.getKey().contains(term)
                || entry.getValue().getHoverName().getString().toLowerCase(Locale.ROOT).contains(term)).toList();
        scroll.scrollTo(0);
        resizeScroll();
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return scrollbar != null && (grid.isMouseOver(x, y) || scrollbar.isMouseOver(x, y)) && scrollbar.scrollByWheel(vertical)
                || super.mouseScrolled(x, y, horizontal, vertical);
    }

    private void resizeScroll() {
        if (scrollbar == null) return;
        int totalRows = (matches.size() + fit.columns() - 1) / fit.columns();
        scroll.resize(fit.rows() * 20, totalRows * 20);
        scrollbar.active = scrollbar.visible = scroll.maxOffset() > 0;
        refresh();
    }

    private void refresh() {
        first = (int) Math.round(scroll.offset() / 20) * fit.columns();
        var stacks = new ArrayList<ItemStack>(fit.capacity());
        var labels = new ArrayList<Component>(fit.capacity());
        for (int i = 0; i < fit.capacity(); i++) {
            int index = first + i;
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
    }

    private void choose(int index) {
        int selected = first + index;
        if (selected >= matches.size()) return;
        var value = matches.get(selected).getValue().copy();
        onClose();
        onSelect.accept(value);
    }

    @Override public void onClose() {
        var client = Minecraft.getInstance();
        if (NativePort.screen(client) != this) return;
        NativePort.show(client, parent);
        var focus = returnFocus.get();
        if (NativePort.screen(client) == parent && focus != null && parent.children().contains(focus)) parent.setFocused(focus);
    }

    @Override protected void renderBackdrop(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        graphics.fill(0, 0, width, height, UiTheme.current().background() | 0xFF000000);
    }

    @Override protected void renderForeground(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        graphics.text(font, title, 8, 8, UiTheme.current().text(), false);
        if (grid == null) graphics.text(font, Component.translatable("sh_better_ui.picker.too_small"), 8, 47,
                UiTheme.current().text(), false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
