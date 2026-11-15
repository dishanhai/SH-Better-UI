// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.api.page.UiBuiltinComponents;
import com.dishanhai.sh_better_ui.kernel.api.page.UiNode;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
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
    static final Map<UiId, UiVersion> COMPONENTS = Set.of("column", "text", "button", "spacer").stream()
            .map(UiBuiltinComponents::id).collect(Collectors.toUnmodifiableMap(id -> id, id -> UiBuiltinComponents.VERSION));
    private static final int INSET = 8;
    private static final int BODY_TOP = 24;
    private static final int FOOTER_HEIGHT = 28;
    private static final int SCROLLBAR_RESERVE = 28;
    private static final int CONTENT_WIDTH = 384;

    private final Screen parent;
    private final UiNode root;
    private final Supplier<PageService.Snapshot> snapshot;
    private final Consumer<UiId> action;
    private final Runnable presented;
    private final Runnable close;
    private final Runnable removed;
    private final Map<String, UiButton> buttons = new HashMap<>();
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
        this.root = definition.root();
        this.snapshot = snapshot;
        this.action = action;
        this.presented = presented;
        this.close = close;
        this.removed = removed;
    }

    @Override protected void init() {
        int backWidth = Math.max(1, Math.min(100, width - 2 * INSET));
        back = addRenderableWidget(ShBetterUiApi.controls().button(
                new Bounds((width - backWidth) / 2, Math.max(0, height - FOOTER_HEIGHT), backWidth, Math.min(20, height)),
                CommonComponents.GUI_BACK, button -> onClose()));
        int statusWidth = Math.max(1, Math.min(CONTENT_WIDTH, width - 2 * INSET));
        statusLabel = addRenderableWidget(new MultiLineTextWidget((width - statusWidth) / 2, 14, status, font)
                .setMaxWidth(statusWidth).setMaxRows(1).setCentered(true));
        refreshBody();
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
        buttons.clear();
        bodyNarration.clear();
        var model = snapshot.get();
        revision = model.revision();
        int bodyHeight = height - BODY_TOP - FOOTER_HEIGHT;
        int bodyWidth = Math.min(CONTENT_WIDTH, width - 2 * INSET - SCROLLBAR_RESERVE);
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
        viewport.setPosition((width - viewport.getWidth()) / 2, BODY_TOP);
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
            case "column" -> {
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

    private void invoke(UiNode node) {
        var model = snapshot.get();
        if (model.state() != PageService.State.ACTIVE || model.revision() != revision) return;
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
        graphics.centeredText(font, getTitle(), width / 2, 4, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (scroll != null) presented.run();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { close.run(); }
    @Override public void removed() { removed.run(); }

    void restoreParent() {
        var client = Minecraft.getInstance();
        if (NativePort.screen(client) == this) NativePort.show(client, parent);
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
