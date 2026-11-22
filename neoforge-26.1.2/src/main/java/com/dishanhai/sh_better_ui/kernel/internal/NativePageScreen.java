// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.api.page.UiBuiltinComponents;
import com.dishanhai.sh_better_ui.kernel.api.page.UiNode;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Native widgets and a scrollable layout for the currently supported page nodes. */
final class NativePageScreen extends Screen {
    static final Map<UiId, UiVersion> COMPONENTS = Set.of("column", "row", "grid", "modal", "text", "button", "spacer", "toggle", "slider").stream()
            .map(UiBuiltinComponents::id).collect(Collectors.toUnmodifiableMap(id -> id, id -> UiBuiltinComponents.VERSION));
    private static final int INSET = 8;
    private static final int BODY_TOP = 24;
    private static final int FOOTER_HEIGHT = 28;
    private static final int SCROLLBAR_RESERVE = 28;
    private static final int CONTENT_WIDTH = 384;
    private static final int MODAL_WIDTH = CONTENT_WIDTH + 2 * INSET + SCROLLBAR_RESERVE;
    private static final int MODAL_HEIGHT = 300;

    private final Screen parent;
    private final boolean modal;
    private final UiNode root;
    private final Supplier<PageService.Snapshot> snapshot;
    private final Consumer<UiId> action;
    private final Runnable presented;
    private final Runnable close;
    private final Runnable removed;
    private final Map<String, AbstractWidget> buttons = new HashMap<>();
    private final Inputs inputs = new Inputs();
    private final List<Component> bodyNarration = new ArrayList<>();
    private AbstractContainerWidget scroll;
    private AbstractWidget back;
    private MultiLineTextWidget statusLabel;
    private Component status = Component.empty();
    private long revision = -1;
    private boolean actionFailed;

    NativePageScreen(UiPageDefinition definition, Supplier<PageService.Snapshot> snapshot,
                     Consumer<UiId> action, Runnable presented, Runnable close, Runnable removed) {
        super(Component.literal(definition.options().title()));
        this.parent = NativePort.screen(Minecraft.getInstance());
        this.modal = definition.type() == UiPageType.MODAL;
        this.root = definition.root();
        this.snapshot = snapshot;
        this.action = action;
        this.presented = presented;
        this.close = close;
        this.removed = removed;
    }

    @Override protected void init() {
        var area = presentationBounds();
        int backWidth = Math.max(1, Math.min(100, area.width() - 2 * INSET));
        back = addRenderableWidget(ShBetterUiApi.controls().button(
                new Bounds((width - backWidth) / 2, Math.max(area.y(), area.bottom() - FOOTER_HEIGHT), backWidth,
                        Math.min(20, area.height())),
                CommonComponents.GUI_BACK, button -> onClose()));
        int statusWidth = Math.max(1, Math.min(CONTENT_WIDTH, area.width() - 2 * INSET));
        statusLabel = addRenderableWidget(new MultiLineTextWidget((width - statusWidth) / 2, area.y() + 14, status, font)
                .setMaxWidth(statusWidth).setMaxRows(1).setCentered(true));
        refreshBody();
        if (modal && getFocused() == null) setInitialFocus(back);
    }

    @Override protected void repositionElements() {
        boolean backFocused = getFocused() == back;
        if (backFocused) clearFocus();
        removeWidget(back);
        removeWidget(statusLabel);
        init();
        if (backFocused) setInitialFocus(back);
    }

    @Override public void tick() {
        if (revision != snapshot.get().revision()) refreshBody();
    }

    private void refreshBody() {
        var previousFocus = scroll != null && getFocused() == scroll ? scroll.getFocused() : null;
        String focusId = buttons.entrySet().stream().filter(entry -> entry.getValue() == previousFocus)
                .map(Map.Entry::getKey).findFirst().orElse(null);
        double offset = scroll == null ? 0 : scroll.scrollAmount();
        if (scroll != null) {
            if (getFocused() == scroll) clearFocus();
            removeWidget(scroll);
            scroll = null;
        }
        buttons.values().forEach(widget -> { if (widget instanceof UiSlider slider) slider.cancelEdit(); });
        buttons.clear();
        bodyNarration.clear();
        var model = snapshot.get();
        inputs.synchronize(root, model.data());
        revision = model.revision();
        var area = presentationBounds();
        int bodyHeight = area.height() - BODY_TOP - FOOTER_HEIGHT;
        int bodyWidth = Math.min(CONTENT_WIDTH, area.width() - 2 * INSET - SCROLLBAR_RESERVE);
        if (bodyHeight <= 0 || bodyWidth <= 0) {
            statusLabel.setMessage(Component.translatable("sh_better_ui.pages.viewport_small"));
            return;
        }
        statusLabel.setMessage(status);
        var content = LinearLayout.vertical();
        var body = layout(root, model.data(), bodyWidth, true);
        if (body != null) content.addChild(body);
        var viewport = new ScrollableLayout(minecraft, content, bodyHeight);
        viewport.setMinWidth(bodyWidth);
        viewport.arrangeElements();
        viewport.setPosition((width - viewport.getWidth()) / 2, area.y() + BODY_TOP);
        viewport.visitWidgets(widget -> scroll = (AbstractContainerWidget) addRenderableWidget(widget));
        scroll.setScrollAmount(offset);
        var focused = focusId == null ? null : buttons.get(focusId);
        if (focused != null && focused.isActive()) {
            scroll.setFocused(focused);
            setFocused(scroll);
        }
    }

    private LayoutElement layout(UiNode node, Map<String, UiValue> data, int maximumWidth, boolean parentEnabled) {
        if (!flag(node, "visible", data)) return null;
        int width = Math.min(maximumWidth, number(node, "width", maximumWidth));
        int height = number(node, "height", -1);
        if (width <= 0 || height == 0) return null;
        boolean enabled = parentEnabled && flag(node, "enabled", data);
        return switch (node.typeId().path()) {
            case "column", "modal" -> {
                int padding = Math.min(number(node, "padding", 0), (width - 1) / 2);
                var column = LinearLayout.vertical().spacing(number(node, "gap", 4));
                for (var child : node.children()) {
                    var element = layout(child, data, width - 2 * padding, enabled);
                    if (element != null) column.addChild(element);
                }
                var box = new FrameLayout(width, Math.max(0, height));
                box.addChild(column, settings -> settings.padding(padding).align(0, 0));
                yield box;
            }
            case "row", "grid" -> {
                int padding = Math.min(number(node, "padding", 0), (width - 1) / 2);
                int innerWidth = width - 2 * padding;
                var children = node.children().stream().filter(child -> flag(child, "visible", data)).toList();
                int requested = node.typeId().path().equals("row") ? Math.max(1, children.size()) : number(node, "columns", 2);
                int columns = Math.min(requested, innerWidth);
                int gap = boundedGap(innerWidth, columns, number(node, "gap", 4));
                var rows = LinearLayout.vertical().spacing(number(node, "gap", 4));
                for (int start = 0; start < children.size(); start += columns) {
                    var row = LinearLayout.horizontal().spacing(gap);
                    for (int cell = 0; cell < columns && start + cell < children.size(); cell++) {
                        int cellWidth = cellWidth(innerWidth, columns, gap, cell);
                        var element = layout(children.get(start + cell), data, cellWidth, enabled);
                        var box = new FrameLayout(cellWidth, 0);
                        if (element != null) box.addChild(element, settings -> settings.align(0, 0));
                        row.addChild(box);
                    }
                    rows.addChild(row);
                }
                var box = new FrameLayout(width, Math.max(0, height));
                box.addChild(rows, settings -> settings.padding(padding).align(0, 0));
                yield box;
            }
            case "toggle" -> {
                UiValue initial = inputs.value(node, "checked", data);
                var toggle = new UiToggleButton[1];
                toggle[0] = ShBetterUiApi.controls().toggle(new Bounds(0, 0, width, height < 0 ? 20 : height),
                        initial instanceof UiValue.Bool checked && checked.value(), UiPreferences.configured(), UiStyle.DEFAULT,
                        checked -> CommonComponents.optionStatus(text(node, "label", data), checked), checked -> {
                            if (!acceptsInput()) return;
                            inputs.edit(node, new UiValue.Bool(checked));
                            toggle[0].setValue(checked);
                            var narration = text(node, "narration", data);
                            if (!narration.getString().isEmpty()) {
                                toggle[0].button().setNarrationLabel(CommonComponents.optionStatus(narration, checked));
                            }
                            if (node.action().isPresent()) invoke(node);
                        });
                var button = toggle[0].button();
                button.active = enabled;
                var narration = text(node, "narration", data);
                if (!narration.getString().isEmpty()) button.setNarrationLabel(CommonComponents.optionStatus(narration, toggle[0].value()));
                tooltip(button, node, data);
                buttons.put(node.id(), button);
                yield button;
            }
            case "slider" -> {
                int sliderHeight = height < 0 ? 20 : height;
                if (width < 9 || sliderHeight < 16) yield new SpacerElement(width, sliderHeight);
                UiValue initial = inputs.value(node, "value", data);
                int percent = initial instanceof UiValue.Decimal decimal ? (int) Math.round(decimal.value() * 100) : 0;
                var label = text(node, "narration", data);
                if (label.getString().isEmpty()) label = text(node, "label", data);
                final Component sliderLabel = label;
                var edit = new SliderInput(inputs, node, data);
                var slider = ShBetterUiApi.controls().slider(new Bounds(0, 0, width, sliderHeight),
                        0, 100, 1, percent, UiPreferences.configured(),
                        value -> Component.translatable("sh_better_ui.pages.slider_value", sliderLabel, value),
                        value -> { if (acceptsInput()) edit.preview(value); });
                slider.setOnCommit(value -> {
                    if (!acceptsInput()) return;
                    edit.commit(value);
                    if (node.action().isPresent()) invoke(node);
                }, value -> edit.cancel());
                slider.active = enabled;
                tooltip(slider, node, data);
                buttons.put(node.id(), slider);
                yield slider;
            }
            case "spacer" -> new SpacerElement(width, height < 0 ? 6 : height);
            case "text" -> {
                var text = text(node, "text", data);
                var label = new MultiLineTextWidget(text, font).setMaxWidth(width);
                var narration = text(node, "narration", data);
                bodyNarration.add(narration.getString().isEmpty() ? text : narration);
                tooltip(label, node, data);
                var box = new FrameLayout(width, Math.max(0, height));
                box.addChild(label, settings -> settings.align(0, 0));
                yield box;
            }
            case "button" -> {
                var button = ShBetterUiApi.controls().button(new Bounds(0, 0, width, height < 0 ? 20 : height),
                        text(node, "label", data), ignored -> invoke(node));
                button.active = enabled && node.action().isPresent();
                var narration = text(node, "narration", data);
                if (!narration.getString().isEmpty()) button.setNarrationLabel(narration);
                tooltip(button, node, data);
                buttons.put(node.id(), button);
                yield button;
            }
            default -> throw new IllegalStateException("Unsupported page component: " + node.typeId());
        };
    }

    Map<String, UiValue> localValues() {
        synchronizeInputs(snapshot.get());
        return inputs.snapshot();
    }

    void synchronizeInputs(PageService.Snapshot model) {
        if (model.revision() != revision) {
            buttons.values().forEach(widget -> { if (widget instanceof UiSlider slider) slider.cancelEdit(); });
        }
        inputs.synchronize(root, model.data());
    }

    private boolean acceptsInput() {
        var model = snapshot.get();
        return model.state() == PageService.State.ACTIVE && model.revision() == revision
                && NativePort.screen(Minecraft.getInstance()) == this;
    }

    private void invoke(UiNode node) {
        if (!acceptsInput()) return;
        actionFailed = false;
        node.action().ifPresent(action);
        if (!actionFailed && snapshot.get().state() == PageService.State.ACTIVE) {
            setStatus(Component.translatable("sh_better_ui.pages.action.succeeded"));
        }
    }

    void actionFailed() {
        actionFailed = true;
        setStatus(Component.translatable("sh_better_ui.pages.action.local_failed"));
    }

    private void setStatus(Component message) {
        status = message;
        statusLabel.setMessage(message);
    }

    @Override public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(getTitle(),
                CommonComponents.joinForNarration(bodyNarration.toArray(Component[]::new)),
                statusLabel == null ? status : statusLabel.getMessage());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var area = presentationBounds();
        if (modal) {
            graphics.fill(0, 0, width, height, 0x90000000);
            if (area.width() > 2 && area.height() > 2) {
                var theme = UiTheme.current();
                var preferences = UiPreferences.configured();
                RoundedCorners.draw(graphics, area.x(), area.y(), area.width(), area.height(), 9, 1,
                        preferences.highContrast() ? theme.text() : theme.border(), theme.surface() | 0xff000000);
            }
        }
        var title = modal ? UiRichText.fit(font, getTitle(), Math.max(0, area.width() - 2 * INSET)) : getTitle();
        graphics.centeredText(font, title, width / 2, area.y() + 4, modal ? UiTheme.current().text() : 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (scroll != null) presented.run();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { close.run(); }
    @Override public void removed() {
        buttons.values().forEach(widget -> { if (widget instanceof UiSlider slider) slider.cancelEdit(); });
        removed.run();
    }

    void restoreParent() {
        var client = Minecraft.getInstance();
        if (NativePort.screen(client) == this) NativePort.show(client, parent);
    }

    private Bounds presentationBounds() {
        return modal ? modalBounds(width, height) : new Bounds(0, 0, width, height);
    }

    static Bounds modalBounds(int width, int height) {
        int modalWidth = Math.min(MODAL_WIDTH, width - (width >= 80 ? 2 * INSET : 0));
        int modalHeight = Math.min(MODAL_HEIGHT, height - (height >= 80 ? 2 * INSET : 0));
        return new Bounds((width - modalWidth) / 2, (height - modalHeight) / 2, modalWidth, modalHeight);
    }

    static int boundedGap(int width, int columns, int requested) {
        return columns <= 1 ? 0 : Math.min(requested, (width - columns) / (columns - 1));
    }

    static int cellWidth(int width, int columns, int gap, int index) {
        int available = width - (columns - 1) * gap;
        return available / columns + (index < available % columns ? 1 : 0);
    }

    /** Preserve exact model values and draft absence across the integer widget's cancellation. */
    static final class SliderInput {
        private final Inputs inputs;
        private final UiNode node;
        private final Map<String, UiValue> data;
        private boolean editing;
        private UiValue beforeDraft;
        private UiValue beforeValue;

        SliderInput(Inputs inputs, UiNode node, Map<String, UiValue> data) {
            this.inputs = inputs;
            this.node = node;
            this.data = data;
        }

        void preview(int percent) {
            if (!editing) {
                beforeValue = inputs.value(node, "value", data);
                beforeDraft = inputs.drafts.get(node.id());
                editing = true;
            }
            // Returning to the widget's starting tick is not a change to a higher-precision value.
            if (beforeValue instanceof UiValue.Decimal value && Math.round(value.value() * 100) == percent) restore();
            else inputs.edit(node, new UiValue.Decimal(percent / 100.0));
        }

        void commit(int percent) {
            preview(percent);
            editing = false;
        }

        void cancel() {
            if (!editing) return;
            restore();
            editing = false;
        }

        private void restore() {
            if (beforeDraft == null) inputs.drafts.remove(node.id());
            else inputs.drafts.put(node.id(), beforeDraft);
        }
    }

    /** Drafts are instance-local; an authoritative bound-field change supersedes its draft. */
    static final class Inputs {
        private final Map<String, UiValue> drafts = new HashMap<>();
        private final Map<String, UiValue> authority = new HashMap<>();

        void synchronize(UiNode node, Map<String, UiValue> data) {
            switch (node.typeId().path()) {
                case "toggle" -> value(node, "checked", data);
                case "slider" -> value(node, "value", data);
                default -> { }
            }
            node.children().forEach(child -> synchronize(child, data));
        }

        void edit(UiNode node, UiValue value) { drafts.put(node.id(), value); }

        UiValue value(UiNode node, String property, Map<String, UiValue> data) {
            var current = NativePageScreen.value(node, property, data);
            if (node.bindings().containsKey(property) && current != null) {
                var previous = authority.put(node.id(), current);
                if (previous != null && !previous.equals(current)) drafts.remove(node.id());
            }
            return drafts.getOrDefault(node.id(), current);
        }

        Map<String, UiValue> snapshot() { return Map.copyOf(drafts); }
    }

    private static void tooltip(AbstractWidget widget, UiNode node, Map<String, UiValue> data) {
        var description = text(node, "tooltip", data);
        if (!description.getString().isEmpty()) widget.setTooltip(Tooltip.create(description));
    }

    private static UiValue value(UiNode node, String key, Map<String, UiValue> data) {
        var literal = node.properties().get(key);
        var field = node.bindings().get(key);
        return field == null ? literal : data.getOrDefault(field, literal);
    }

    private static boolean flag(UiNode node, String key, Map<String, UiValue> data) {
        return !(value(node, key, data) instanceof UiValue.Bool flag) || flag.value();
    }

    private static int number(UiNode node, String key, int fallback) {
        return node.properties().get(key) instanceof UiValue.Int number ? number.value() : fallback;
    }

    private static Component text(UiNode node, String key, Map<String, UiValue> data) {
        return switch (value(node, key, data)) {
            case UiValue.Text text -> Component.literal(text.value());
            case UiValue.Int number -> Component.literal(Integer.toString(number.value()));
            case UiValue.LongInt number -> Component.literal(Long.toString(number.value()));
            case UiValue.Decimal number -> Component.literal(Double.toString(number.value()));
            case null, default -> Component.empty();
        };
    }
}
