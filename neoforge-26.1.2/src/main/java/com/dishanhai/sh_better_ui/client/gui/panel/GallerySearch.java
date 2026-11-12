// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Search-index completion with native 26.x text input and focusable results. */
final class GallerySearch {
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_PITCH = ROW_HEIGHT + 5;
    private static final int RESULT_COUNT = 2;
    private static final int MAX_QUERY_LENGTH = 64;
    private static final int MODIFIERS = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL
            | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;
    private final List<UiButton> results = new ArrayList<>(RESULT_COUNT);
    private List<SearchIndex.Suggestion<String>> suggestions = List.of();
    private SearchIndex<String> index;
    private EditBox input;
    private String query = "";
    private @Nullable String undoQuery;
    private int selection;
    private boolean dismissed;
    private boolean focusRequested;

    AbstractWidget addWidgets(Bounds area, Font font, UiPreferences preferences, Consumer<AbstractWidget> add) {
        index = new SearchIndex<>(List.of(entry("apple"), entry("carrot")));
        input = new EditBox(font, area.x(), area.y(), area.width(), ROW_HEIGHT,
                Component.translatable("sh_better_ui.panel.developer.gallery.sample.search")) {
            @Override
            public void updateWidgetNarration(NarrationElementOutput output) {
                super.updateWidgetNarration(output);
                if (suggestions.isEmpty()) return;
                output.add(NarratedElementType.POSITION, Component.translatable("narrator.position.object_list",
                        selection + 1, suggestions.size()));
                output.add(NarratedElementType.HINT, label(suggestions.get(selection)));
            }
        };
        input.setHint(Component.translatable("sh_better_ui.panel.developer.gallery.search_hint"));
        input.setMaxLength(MAX_QUERY_LENGTH);
        input.setValue(query);
        input.setResponder(this::queryChanged);
        add.accept(input);

        results.clear();
        focusRequested = false;
        int slots = Math.clamp((area.height() - ROW_HEIGHT) / ROW_PITCH, 1, RESULT_COUNT);
        for (int slot = 0; slot < slots; slot++) {
            int row = slot;
            var bounds = new Bounds(area.x(), area.y() + ROW_PITCH * (slot + 1), area.width(), ROW_HEIGHT);
            var button = ShBetterUiApi.controls().button(bounds, CommonComponents.EMPTY, pressed -> {
                if (accept(suggestionIndex(row))) focusRequested = true;
            }, preferences, false, UiStyle.DEFAULT);
            results.add(button);
            add.accept(button);
        }
        refreshSuggestions();
        return input;
    }

    private static SearchIndex.Entry<String> entry(String item) {
        String id = "minecraft:" + item;
        return new SearchIndex.Entry<>(id, Component.translatable("item.minecraft." + item).getString(),
                List.of(id), List.of());
    }

    private void queryChanged(String value) {
        if (value.equals(query)) return;
        query = value;
        selection = 0;
        dismissed = false;
        undoQuery = null;
        refreshSuggestions();
    }

    private void refreshSuggestions() {
        suggestions = dismissed ? List.of() : index.suggest(query, RESULT_COUNT).stream()
                .filter(suggestion -> suggestion.completion().length() <= MAX_QUERY_LENGTH).toList();
        selection = Math.min(selection, Math.max(0, suggestions.size() - 1));
        updateResults();
    }

    private void updateResults() {
        for (int row = 0; row < results.size(); row++) {
            var button = results.get(row);
            int candidate = suggestionIndex(row);
            button.visible = candidate < suggestions.size();
            button.active = button.visible;
            if (button.visible) {
                button.setMessage(label(suggestions.get(candidate)));
                button.setSelected(candidate == selection);
            }
        }
    }

    private int suggestionIndex(int row) {
        return results.size() == 1 ? selection : row;
    }

    private static Component label(SearchIndex.Suggestion<?> suggestion) {
        String name = suggestion.label();
        String completion = suggestion.completion();
        if (!name.equals(suggestion.entry().name()) || completion.equals(name)) return Component.literal(name);
        return Component.translatable("sh_better_ui.search.suggestion.id", completion, name);
    }

    private boolean accept(int candidate) {
        if (candidate >= suggestions.size()) return false;
        String previous = query;
        input.setValue(suggestions.get(candidate).completion());
        if (!query.equals(previous)) undoQuery = previous;
        dismissed = true;
        refreshSuggestions();
        return true;
    }

    boolean keyPressed(KeyEvent event, @Nullable GuiEventListener focused, Consumer<AbstractWidget> focus) {
        int row = results.indexOf(focused);
        if (focused != input && row < 0) return false;
        if (event.isEscape() && !suggestions.isEmpty()) {
            dismissed = true;
            refreshSuggestions();
            if (row >= 0) focus.accept(input);
            return true;
        }
        if (focused == input && event.key() == GLFW.GLFW_KEY_Z && event.hasControlDownWithQuirk()
                && !event.hasShiftDown() && !event.hasAltDown() && undoQuery != null) {
            input.setValue(undoQuery);
            return true;
        }
        if ((event.modifiers() & MODIFIERS) != 0 || suggestions.isEmpty()) return false;
        if (focused == input && (event.isUp() || event.isDown())) {
            selection = Math.clamp(selection + (event.isUp() ? -1 : 1), 0, suggestions.size() - 1);
            updateResults();
            return true;
        }
        if (focused == input && event.isConfirmation()) return accept(selection);
        if (row >= 0 && event.isSelection() && accept(suggestionIndex(row))) {
            focus.accept(input);
            return true;
        }
        return false;
    }

    @Nullable AbstractWidget takeFocusRequest() {
        if (!focusRequested) return null;
        focusRequested = false;
        return input;
    }
}
