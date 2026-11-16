// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPagination;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.controls.PaginationState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Gallery wiring only. Pagination behavior remains in the public control and kernel APIs. */
final class PaginationDemo {
    static final String ID = "pagination";
    static final int ORDER = 38, HEIGHT = 230;
    private static final String KEY = "sh_better_ui.pagination.";
    static final String CODE = """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPagination;
            import com.dishanhai.sh_better_ui.kernel.api.controls.PaginationState;
            import com.dishanhai.sh_better_ui.kernel.api.controls.PaginationRange;
            import net.minecraft.network.chat.Component;
            import java.util.List;

            private void addPagination(int x, int y) {
                var data = new PaginationState(250, 25, 2);
                var pagination = ShBetterUiApi.controls().pagination(font, new Bounds(x, y, 360, 22),
                    Component.translatable("sh_better_ui.pagination.title"), data,
                    ShBetterUiApi.settings().preferences(), page -> {
                        // Read data.fromIndex() / data.toIndex() and refresh host content.
                    });
                addRenderableWidget(pagination);
                // pagination.setMode(UiPagination.Mode.SIMPLE); // or ICONS_ONLY
                // pagination.setDirection(UiPagination.Direction.RTL);
                // pagination.setNumberFormatter(page -> localizedPageComponent(page));
                // pagination.setLabels(previousComponent, nextComponent);
                // pagination.setContent(List.of(UiPagination.Item.previous(null),
                //     UiPagination.Item.link(1), UiPagination.Item.link(2),
                //     UiPagination.Item.ellipsis(), UiPagination.Item.next(null)));
                // data.setTotalItems(count); data.setPageSize(50); // no user callback
                // PaginationRange.items(data.pageCount(), data.page(), 1, 1) is renderer independent.
                // On unmount: pagination.cancelInteraction().
            }
            """;
    private final PaginationState standard = new PaginationState(250, 25, 2);
    private final PaginationState simple = new PaginationState(5, 1, 2);
    private final PaginationState icons = new PaginationState(250, 25, 2);
    private final PaginationState rtl = new PaginationState(250, 25, 2);
    private final List<UiPagination> controls = new ArrayList<>();
    private final List<UiButton> summaries = new ArrayList<>();
    private int rowsIndex = 1;
    private int variantIndex;
    private boolean empty;

    void mount(Font font, Bounds v, UiPreferences preferences, Consumer<AbstractWidget> add, BooleanSupplier interactive) {
        suspend();
        int toolbarWidth = (v.width() - 4) / 2;
        if (toolbarWidth < 22 || v.height() < 22) return;
        add.accept(new UiButton(v.x(), v.y(), toolbarWidth, 22, text("rows", icons.pageSize()), button -> {
            int[] sizes = {10, 25, 50, 100}; rowsIndex = (rowsIndex + 1) % sizes.length;
            icons.setPageSize(sizes[rowsIndex]); button.setMessage(text("rows", icons.pageSize())); updateSummaries();
        }, preferences, false));
        add.accept(new UiButton(v.x() + toolbarWidth + 4, v.y(), toolbarWidth, 22, text("toggle_empty"), button -> {
            empty = !empty;
            standard.setTotalItems(empty ? 0 : 250); simple.setTotalItems(empty ? 0 : 5);
            icons.setTotalItems(empty ? 0 : 250); rtl.setTotalItems(empty ? 0 : 250); updateSummaries();
        }, preferences, false));
        boolean compact = v.height() < 214;
        String[] variants = {"standard", "simple", "icons", "rtl"};
        if (compact && v.height() >= 96) add.accept(new UiButton(v.x(), v.y() + 28, v.width(), 22,
                text("variant", text(variants[variantIndex])), button -> {
                    variantIndex = (variantIndex + 1) % variants.length;
                    button.setMessage(text("variant", text(variants[variantIndex])));
                    cancelInteraction();
                    for (int i = 0; i < controls.size(); i++) {
                        controls.get(i).visible = i == variantIndex;
                        summaries.get(i).visible = i == variantIndex;
                    }
                }, preferences, false));
        PaginationState[] states = {standard, simple, icons, rtl};
        for (int i = 0; i < states.length; i++) {
            int y = v.y() + (compact ? 56 : 30 + i * 46);
            if (y + 40 > v.bottom()) break;
            var state = states[i];
            String variant = switch (i) { case 0 -> "standard"; case 1 -> "simple"; case 2 -> "icons"; default -> "rtl"; };
            var summary = new UiButton(v.x(), y, v.width(), 16, summary(state, variant), ignored -> {}, preferences, false);
            summary.active = false; summary.visible = !compact || i == variantIndex;
            summaries.add(summary); add.accept(summary);
            var control = ShBetterUiApi.controls().pagination(font, new Bounds(v.x(), y + 18, v.width(), 22),
                    text(variant), state, preferences, page -> updateSummaries());
            if (i == 1) control.setMode(UiPagination.Mode.SIMPLE);
            if (i == 2) control.setMode(UiPagination.Mode.ICONS_ONLY);
            if (i == 3) {
                control.setDirection(UiPagination.Direction.RTL);
                control.setLabels(text("previous_rtl"), text("next_rtl"));
                control.setNumberFormatter(page -> Component.literal(arabicDigits(page)));
            }
            control.visible = !compact || i == variantIndex;
            control.setInputEnabled(interactive); controls.add(control); add.accept(control);
        }
    }
    private void updateSummaries() {
        PaginationState[] states = {standard, simple, icons, rtl};
        String[] names = {"standard", "simple", "icons", "rtl"};
        for (int i = 0; i < summaries.size(); i++) summaries.get(i).setMessage(summary(states[i], names[i]));
    }
    private static Component summary(PaginationState state, String variant) {
        return text("summary", text(variant), state.page(), state.pageCount());
    }
    private static String arabicDigits(long page) {
        var text = Long.toString(page); var result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) result.append((char) ('\u0660' + text.charAt(i) - '0'));
        return result.toString();
    }
    private static Component text(String key, Object... args) { return Component.translatable(KEY + key, args); }
    void cancelInteraction() { for (var control : controls) control.cancelInteraction(); }
    void suspend() { cancelInteraction(); controls.clear(); summaries.clear(); }
}
