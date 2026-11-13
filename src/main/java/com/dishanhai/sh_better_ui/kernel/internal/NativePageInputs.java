// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.TabScrollState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiUnderlinedTabs.Tab;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.UiNode;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class NativePageInputs {
    private static final UiValue.ItemReference EMPTY_ITEM = new UiValue.ItemReference(UiId.parse("minecraft:air"), 0);

    record Control(AbstractWidget widget, Consumer<Map<String, UiValue>> update) {}

    private NativePageInputs() {}

    static Control build(NativePageView view, UiScreen screen, UiNode node, List<UiNode> ancestors, int width, int height) {
        return switch (node.typeId().path()) {
            case "toggle", "checkbox" -> toggle(view, screen, node, ancestors, width, height);
            case "slider" -> slider(view, screen, node, ancestors, width, height);
            case "text_field", "search_field" -> field(view, screen, node, ancestors, width, height);
            case "dropdown", "ghost_slot" -> picker(view, screen, node, ancestors, width, height);
            case "tab_bar" -> tabs(view, screen, node, ancestors, width, height);
            case "list_view", "virtual_list" -> list(view, node, width, height);
            default -> throw new IllegalArgumentException("Unknown page input component");
        };
    }

    private static Control toggle(NativePageView view, UiScreen screen, UiNode node, List<UiNode> ancestors,
                                  int width, int height) {
        Component[] label = {Component.empty()};
        var toggle = screen.context().controls().toggle(new Bounds(0, 0, width, height), false,
                screen.context().preferences(), UiStyle.DEFAULT,
                checked -> Component.translatable("sh_better_ui.pages.toggle_value", label[0],
                        Component.translatable(checked ? "options.on" : "options.off")),
                checked -> view.commit(node, ancestors, new UiValue.Bool(checked)));
        toggle.setModeIndicatorsVisible(!node.typeId().path().equals("checkbox"));
        toggle.setIcons(GraphicIcon.SUCCESS, GraphicIcon.CLOSE);
        toggle.button().setSampleBackground(false);
        return new Control(toggle.button(), data -> {
            label[0] = NativePageView.text(NativePageView.value(node, "label", data));
            toggle.setValue(view.input(node, "checked", data) instanceof UiValue.Bool value && value.value());
        });
    }

    private static Control slider(NativePageView view, UiScreen screen, UiNode node, List<UiNode> ancestors,
                                  int width, int height) {
        if (width < 9 || height < 16) {
            return unavailable(width, height);
        }
        Component[] label = {Component.empty()};
        var slider = screen.context().controls().slider(new Bounds(0, 0, width, height), 0, 100, 1, 0,
                screen.context().preferences(), percent -> Component.translatable("sh_better_ui.pages.slider_value",
                        label[0], percent),
                percent -> view.edit(node, ancestors, new UiValue.Decimal(percent / 100.0)));
        slider.setOnCommit(percent -> view.commit(node, ancestors, new UiValue.Decimal(percent / 100.0)),
                percent -> view.cancelEdit(node, new UiValue.Decimal(percent / 100.0)));
        return new Control(slider, data -> {
            label[0] = NativePageView.text(NativePageView.value(node, "label", data));
            UiValue value = view.input(node, "value", data);
            slider.setValue(value instanceof UiValue.Decimal decimal ? (int) Math.round(100 * decimal.value()) : 0);
            slider.refreshLabel();
        });
    }

    private static Control field(NativePageView view, UiScreen screen, UiNode node, List<UiNode> ancestors,
                                 int width, int height) {
        boolean search = node.typeId().path().equals("search_field");
        if (width < 16 || height < 12) return unavailable(width, height);
        var minecraft = Minecraft.getInstance();
        Component title = NativePageView.text(node.properties().get("label"));
        if (title.getString().isEmpty()) {
            title = Component.translatable(search ? "sh_better_ui.search.label" : "sh_better_ui.pages.text_field");
        }
        var hint = NativePageView.text(node.properties().get("placeholder"));
        if (search) {
            var finder = screen.context().search().<String>field(minecraft.font, new Bounds(0, 0, width, height),
                    screen.context().search().index(List.of()), UiStyle.DEFAULT, screen.context().preferences());
            finder.setMessage(title);
            finder.setSampleBackground(false);
            finder.setHint(hint.getString().isEmpty() ? title : hint);
            finder.setValue(NativePageView.text(view.input(node, "value", Map.of())).getString());
            finder.setResponder(value -> applyText(value, input -> view.edit(node, ancestors, input)));
            if (node.action().isPresent()) {
                finder.setOnSubmit(value -> applyText(value, input -> view.commit(node, ancestors, input)));
            }
            return new Control(finder, ignored -> {});
        }
        var editor = new EditBox(minecraft.font, 0, 0, width, height, title) {
            @Override
            public boolean keyPressed(int key, int scanCode, int modifiers) {
                if (active && isFocused() && node.action().isPresent()
                        && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
                    applyText(getValue(), input -> view.commit(node, ancestors, input));
                    return true;
                }
                return super.keyPressed(key, scanCode, modifiers);
            }
        };
        editor.setMaxLength(256);
        editor.setHint(hint.getString().isEmpty() ? title : hint);
        editor.setValue(NativePageView.text(view.input(node, "value", Map.of())).getString());
        editor.setResponder(value -> applyText(value, input -> view.edit(node, ancestors, input)));
        return new Control(editor, ignored -> {});
    }

    private static Control picker(NativePageView view, UiScreen screen, UiNode node, List<UiNode> ancestors,
                                  int width, int height) {
        boolean ghost = node.typeId().path().equals("ghost_slot");
        List<UiValue> candidates = new ArrayList<>();
        if (ghost) {
            candidates.add(EMPTY_ITEM);
            candidates.addAll(sequence(node.properties().get("choices")).stream()
                    .filter(item -> !item.equals(EMPTY_ITEM)).distinct().toList());
        } else {
            int count = sequence(node.properties().get("options")).size();
            for (int index = 0; index < count; index++) candidates.add(new UiValue.Int(index));
        }
        List<Component> labels = ghost ? candidates.stream().map(NativePageInputs::itemLabel).toList()
                : sequence(node.properties().get("options")).stream().map(NativePageView::text).toList();
        UiValue fallback = ghost ? EMPTY_ITEM : new UiValue.Int(0);
        String key = ghost ? "item" : "selected";
        UiValue[] current = {fallback};
        UiButton[] source = new UiButton[1];
        source[0] = screen.context().controls().button(new Bounds(0, 0, width, height), Component.empty(),
                button -> {
                    if (view.acceptsInput(node, ancestors)) {
                        NativePageOptions.open(screen, view.options(), source[0], labels, candidates.indexOf(current[0]),
                                view.textLabel(node, ancestors, source[0]),
                                index -> view.commit(node, ancestors, candidates.get(index)));
                    }
                }, screen.context().preferences(), false, UiStyle.DEFAULT);
        source[0].setSampleBackground(false);
        source[0].setLeadingIcon(ghost ? GraphicIcon.FILTER : GraphicIcon.EXPAND);
        return new Control(source[0], data -> {
            var value = view.input(node, key, data);
            current[0] = value == null ? fallback : value;
            int selected = candidates.indexOf(current[0]);
            Component label = selected >= 0 ? labels.get(selected)
                    : ghost ? itemLabel(current[0]) : Component.translatable("sh_better_ui.pages.select_value");
            source[0].setMessage(label);
            source[0].active &= !labels.isEmpty();
        });
    }

    private static Control tabs(NativePageView view, UiScreen screen, UiNode node, List<UiNode> ancestors,
                                int width, int height) {
        var tabs = sequence(node.properties().get("options")).stream()
                .map(option -> new Tab(NativePageView.text(option), Component.empty())).toList();
        if (tabs.isEmpty()) return unavailable(width, height);
        var bar = screen.context().controls().underlinedTabs(new Bounds(0, 0, width, height), tabs, 0,
                index -> view.commit(node, ancestors, new UiValue.Int(index)));
        return new Control(bar, data -> {
            int index = NativePageView.number(view.input(node, "selected", data), 0);
            bar.selection().select(Math.max(0, Math.min(tabs.size() - 1, index)));
        });
    }

    private static Control list(NativePageView view, UiNode node, int width, int height) {
        int rowHeight = NativePageView.number(node.properties().get("rowHeight"), 20);
        String source = NativePageView.text(node.properties().get("search")).getString();
        var list = new NativePageList(width, height, rowHeight, view.listScroll(node.id()));
        return new Control(list, data -> list.update(sequence(NativePageView.value(node, "items", data)).stream()
                .map(item -> NativePageView.text(item).getString()).toList(), view.query(source, data)));
    }

    private static Control unavailable(int width, int height) {
        var spacer = new NativePageView.PassiveWidget(width, height, "text");
        spacer.setMessage(Component.translatable("sh_better_ui.pages.viewport_small"));
        return new Control(spacer, ignored -> spacer.active = false);
    }

    private static void applyText(String value, Consumer<UiValue.Text> consumer) {
        UiValue.Text input;
        try {
            input = new UiValue.Text(value);
        } catch (IllegalArgumentException incompleteUnicodeInput) {
            return;
        }
        consumer.accept(input);
    }

    private static List<UiValue> sequence(UiValue value) {
        return value instanceof UiValue.Sequence entries ? entries.value() : List.of();
    }

    private static Component itemLabel(UiValue value) {
        var stack = NativePageView.item(value);
        return stack.isEmpty() ? Component.translatable("sh_better_ui.pages.ghost_empty") : stack.getHoverName();
    }
}
