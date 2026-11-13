// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.LayoutApi;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.TabScrollState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiUnderlinedTabs;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.api.page.UiBuiltinComponents;
import com.dishanhai.sh_better_ui.kernel.api.page.UiNode;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageTextReadability;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.LocalizedText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ParagraphBreak;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextMarkup;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.lwjgl.glfw.GLFW;

import static java.nio.charset.StandardCharsets.UTF_8;

/** A fixed set of native widgets laid out with Minecraft's grid and drawn through the themed UiScreen. */
final class NativePageView {
    private static final int INSET = 8;
    private static final int CONTENT_WIDTH = 384;
    private static final int STATUS_HEIGHT = 12;
    private static final int SCROLL_RAIL = 6;
    private static final UiId STACK_COMPONENT = UiBuiltinComponents.id("stack");
    private static final Set<UiId> COMPONENTS = Set.of(
            "row", "column", "stack", "grid", "scroll_view", "panel", "card", "modal", "spacer", "divider",
            "text", "rich_text", "icon", "image", "progress_bar", "badge", "tooltip", "button",
            "toggle", "checkbox", "slider", "text_field", "search_field", "dropdown", "tab_bar",
            "list_view", "virtual_list", "item_display", "ghost_slot")
            .stream().map(UiBuiltinComponents::id).collect(Collectors.toUnmodifiableSet());
    private final ClientPages.OpenPage page;
    private final LayoutApi layouts;
    private final UiPageTextReadability textOptions;
    private final Supplier<NativeTextReadability.Mount> mountText;
    private NativeTextReadability.Mount textMount;
    private final List<Binding> bindings = new ArrayList<>();
    private final List<LayoutBox> boxes = new ArrayList<>();
    private final Map<String, UiValue> drafts = new HashMap<>();
    private final Set<String> uncommitted = new HashSet<>();
    private final Map<String, UiValue> lastAuthority = new HashMap<>();
    private final Map<String, UiNode> searchFields = new HashMap<>();
    private final Map<String, TabScrollState> listScroll = new HashMap<>();
    private final Map<String, TabScrollState> regionScroll = new HashMap<>();
    private final Map<String, ScrollRegion> scrollRegions = new LinkedHashMap<>();
    private final TabScrollState scrolling = new TabScrollState();
    private LayoutElement rootLayout;
    private Bounds content;
    private Scrollbar scrollbar;
    private UiLocalModal options;
    private AbstractWidget lastFocus;
    private boolean localDirty;
    private PageService.Snapshot rendered;
    private boolean fits;

    NativePageView(ClientPages.OpenPage page, LayoutApi layouts, UiPageTextReadability textOptions,
                   Supplier<NativeTextReadability.Mount> mountText) {
        this.page = page;
        this.layouts = layouts;
        this.textOptions = textOptions;
        this.mountText = mountText;
    }

    static Set<UiId> supportedComponents() { return COMPONENTS; }

    Map<String, UiValue> localValues() { return Map.copyOf(drafts); }

    boolean hasUnsavedInput() { return !uncommitted.isEmpty(); }

    String focusedNodeId(UiScreen screen) {
        return bindings.stream().filter(binding -> binding.widget == screen.getFocused())
                .map(binding -> binding.node.id()).findFirst().orElse(null);
    }

    void restoreFocus(UiScreen screen, String nodeId) {
        if (nodeId == null) return;
        bindings.stream().filter(binding -> binding.node.id().equals(nodeId))
                .map(Binding::widget).filter(widget -> widget.active && widget.visible).findFirst()
                .ifPresent(screen::setFocused);
    }

    Optional<AbstractWidget> actionWidget(String nodeId) {
        return bindings.stream().filter(binding -> binding.node.id().equals(nodeId) && binding.node.action().isPresent())
                .map(Binding::widget).findFirst();
    }

    Optional<AbstractWidget> focusedAction() {
        return bindings.stream().filter(binding -> binding.widget == lastFocus && binding.node.action().isPresent())
                .map(Binding::widget).findFirst();
    }

    record WidgetBounds(String id, int x, int y, int width, int height, boolean visible, boolean enabled) {}

    record Inspection(List<WidgetBounds> widgets, Optional<String> focused, boolean scrollable, double scrollOffset) {}

    Inspection inspect() {
        if (rendered == null) return new Inspection(List.of(), Optional.empty(), false, 0);
        var data = rendered.data();
        var geometry = boxes.stream().map(box -> {
            var element = box.element;
            var widget = element instanceof AbstractWidget nativeWidget ? nativeWidget : null;
            boolean visible = intersects(element, clip(box.parents)) && (widget == null
                    ? inheritedFlag(box.node, box.parents, "visible", data) : widget.visible);
            boolean enabled = visible && (widget == null
                    ? inheritedFlag(box.node, box.parents, "enabled", data) : widget.active);
            return new WidgetBounds(box.node.id(), element.getX(), element.getY(), element.getWidth(),
                    element.getHeight(), visible, enabled);
        }).toList();
        var focused = bindings.stream().filter(binding -> binding.widget == lastFocus)
                .map(binding -> binding.node.id()).findFirst();
        return new Inspection(geometry, focused, scrolling.maxOffset() > 0, scrolling.offset());
    }

    void init(UiScreen screen) {
        var viewport = screen.viewport();
        var root = page.definition.root();
        var restore = inspect().focused();
        UiLocalModal.interrupt(screen);
        if (options == null) options = new UiLocalModal(screen, screen.context().preferences());
        if (!textOptions.equals(UiPageTextReadability.DEFAULT)
                && (textMount == null || !textMount.scope().isOpen())) {
            var mounted = mountText.get();
            try {
                mounted.service().configure(mounted.module(), mounted.scope(), textOptions.page().policy());
                screen.context().manage(mounted.scope());
            } catch (RuntimeException | Error failure) {
                mounted.scope().close();
                throw failure;
            }
            textMount = mounted;
        }
        for (var binding : bindings) {
            if (binding.widget instanceof NativePageList list) list.retireTextRows();
        }
        bindings.clear();
        boxes.clear();
        searchFields.clear();
        scrollRegions.clear();
        rendered = null;
        lastFocus = null;
        int inset = Math.min(INSET, Math.min(viewport.width(), viewport.height()) / 2);
        boolean modal = page.definition.type() == UiPageType.MODAL;
        int available = Math.max(0, Math.min(modal ? 280 : CONTENT_WIDTH,
                viewport.width() - 2 * inset - SCROLL_RAIL));
        int viewportHeight = Math.max(0, viewport.height() - 2 * inset - STATUS_HEIGHT);
        if (root.typeId().path().equals("scroll_view")) {
            viewportHeight = Math.min(viewportHeight, number(root.properties().get("height"), viewportHeight));
        }
        var layout = element(screen, root, List.of(), available);
        if (layout instanceof Layout rootContainer) rootContainer.arrangeElements();
        int spareHeight = modal ? Math.max(0, viewportHeight - layout.getHeight()) : 0;
        viewportHeight -= spareHeight;
        rootLayout = layout;
        fits = available > 0 && viewportHeight > 0 && layout.getWidth() <= available;
        content = new Bounds(viewport.x(), viewport.y() + inset + spareHeight / 2, viewport.width(), viewportHeight);
        var alignment = layouts.create(viewport);
        var rootGeometry = alignment.node(root.id(), AlignmentFrame.box(new Rect(
                viewport.x() + Math.max(0, (viewport.width() - SCROLL_RAIL - layout.getWidth()) / 2),
                content.y(), layout.getWidth(), layout.getHeight())));
        alignment.resolve();
        var bounds = rootGeometry.current(layouts.pixels(Minecraft.getInstance().getWindow())).layoutBounds();
        layout.setX((int) Math.round(bounds.x()));
        scrolling.resize(viewportHeight, layout.getHeight());
        scrollbar = screen.add(new Scrollbar(viewport.right() - SCROLL_RAIL, content.y(), viewportHeight,
                scrolling, this::place));
        scrollbar.visible = scrollbar.active = fits && scrolling.maxOffset() > 0;
        screen.widgetViewport(content, (x, y, horizontal, vertical) -> scrollAt(x, y, -vertical * 20));
        place();
        refresh(screen);
        restoreFocus(screen, restore.orElse(null));
    }

    private LayoutElement element(UiScreen screen, UiNode node, List<UiNode> parents, int available) {
        String type = node.typeId().path();
        if (type.equals("search_field")) searchFields.put(node.id(), node);
        int width = Math.min(available, number(node.properties().get("width"), available));
        LayoutElement element = switch (type) {
            case "row", "column", "grid", "panel", "card", "modal" ->
                    container(screen, node, parents, width, type);
            case "scroll_view" -> parents.isEmpty() ? container(screen, node, parents, width, type)
                    : nestedScroll(screen, node, parents, width);
            case "stack" -> stack(screen, node, parents, width);
            case "spacer" -> new SpacerElement(width, number(node.properties().get("height"), 6));
            default -> {
                var control = leaf(screen, node, parents, type, width);
                var widget = add(screen, control.widget(), parents);
                var label = textLabel(node, parents, widget);
                if (label != null) {
                    if (widget instanceof UiButton button) button.bindTextReadability(label);
                    if (widget instanceof PassiveWidget passive) passive.readableLabel = label;
                    if (widget instanceof NativePageList list) list.bindTextReadability(label);
                    if (widget instanceof UiSlider slider) slider.bindTextReadability(label);
                    if (widget instanceof UiUnderlinedTabs tabs) tabs.bindTextReadability(label);
                    if (widget instanceof NativeTextReadability.EditBoxBinding editor) {
                        editor.shbui$bindReadability(label.inline());
                    }
                }
                bindings.add(new Binding(node, parents, widget, control.update()));
                yield widget;
            }
        };
        boxes.add(new LayoutBox(node, parents, element));
        return element;
    }

    private ScrollRegion nestedScroll(UiScreen screen, UiNode node, List<UiNode> parents, int width) {
        int height = Math.max(0, Math.min(screen.viewport().height(), number(node.properties().get("height"), 100)));
        TabScrollState state = regionScroll.computeIfAbsent(node.id(), ignored -> new TabScrollState());
        var region = new ScrollRegion(node, parents, width, height, state);
        scrollRegions.put(node.id(), region);
        region.body = container(screen, node, parents, Math.max(0, width - SCROLL_RAIL), "column");
        region.bar = add(screen, new Scrollbar(0, 0, height, state, region::place), parents);
        region.arrangeElements();
        return region;
    }

    private <T extends AbstractWidget> T add(UiScreen screen, T widget, List<UiNode> ancestors) {
        for (var ancestor : ancestors) {
            if (ancestor.typeId().equals(STACK_COMPONENT)) {
                return screen.addClipped(widget, () -> clip(ancestors), ancestor.id());
            }
        }
        return ancestors.stream().anyMatch(node -> scrollRegions.containsKey(node.id()))
                ? screen.addClipped(widget, () -> clip(ancestors)) : screen.add(widget);
    }

    private FrameLayout stack(UiScreen screen, UiNode node, List<UiNode> parents, int width) {
        var layout = new FrameLayout().setMinHeight(number(node.properties().get("height"), 0));
        var path = childrenOf(node, parents);
        int padding = number(node.properties().get("padding"), 0);
        for (UiNode child : node.children()) {
            layout.addChild(element(screen, child, path, Math.max(0, width - 2 * padding)),
                    layout.newChildLayoutSettings().padding(padding));
        }
        return layout;
    }

    private GridLayout container(UiScreen screen, UiNode node, List<UiNode> parents, int width, String type) {
        var ancestors = childrenOf(node, parents);
        int gap = number(node.properties().get("gap"), 4);
        int padding = number(node.properties().get("padding"), 0);
        int columns = switch (type) {
            case "row" -> Math.max(1, node.children().size());
            case "grid" -> Math.min(Math.max(1, node.children().size()), number(node.properties().get("columns"), 2));
            default -> 1;
        };
        var grid = new GridLayout().spacing(gap);
        if (node.children().isEmpty()) return grid;
        int columnWidth = Math.max(0, (width - (columns - 1) * gap) / columns - 2 * padding);
        for (int index = 0; index < node.children().size(); index++) {
            var child = element(screen, node.children().get(index), ancestors, columnWidth);
            grid.addChild(child, index / columns, index % columns, grid.newCellSettings().padding(padding));
        }
        return grid;
    }

    private static List<UiNode> childrenOf(UiNode node, List<UiNode> parents) {
        var path = new ArrayList<>(parents);
        path.add(node);
        return List.copyOf(path);
    }

    private NativePageInputs.Control leaf(UiScreen screen, UiNode node, List<UiNode> parents, String type, int width) {
        int preferred = switch (type) {
            case "item_display", "button", "dropdown", "ghost_slot" -> 22;
            case "icon" -> 16;
            case "image", "rich_text" -> 48;
            case "list_view", "virtual_list" -> 100;
            case "progress_bar" -> 8;
            case "divider" -> 1;
            default -> 23;
        };
        int height = number(node.properties().get("height"), preferred);
        if (UiBuiltinComponents.inputType(node.typeId()).isPresent() || type.equals("list_view")
                || type.equals("virtual_list")) {
            return NativePageInputs.build(this, screen, node, parents, width, height);
        }
        if (type.equals("button")) {
            var button = screen.context().controls().button(new Bounds(0, 0, width, height), Component.empty(), b -> {
                if (acceptsInput(node, parents)) page.act(node);
            }, screen.context().preferences(), false, com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle.DEFAULT);
            button.setSampleBackground(false);
            return new NativePageInputs.Control(button, data -> button.setMessage(text(value(node, "label", data))));
        }
        var passive = new PassiveWidget(width, height, type);
        return new NativePageInputs.Control(passive, data -> passive.update(node, data, screen.context().preferences()));
    }

    NativeTextReadability.Label textLabel(UiNode node, List<UiNode> parents, AbstractWidget widget) {
        if (textMount == null) return null;
        var options = textOptions.page();
        for (var ancestor : parents) {
            options = options.overlay(textOptions.nodes().getOrDefault(ancestor.id(), UiPageTextReadability.Options.INHERIT));
        }
        options = options.overlay(textOptions.nodes().getOrDefault(node.id(), UiPageTextReadability.Options.INHERIT));
        String stableId = HexFormat.of().formatHex(node.id().getBytes(UTF_8));
        return new NativeTextReadability.Label(textMount, new UiId("sh_better_ui", "page_node/" + stableId),
                NativeTextReadability.Options.fromPage(options), () -> intersect(clip(parents),
                new Bounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight())));
    }

    void render(UiScreen screen, UiFrame frame) {
        if (page.definition.type() == UiPageType.MODAL) {
            var graphics = frame.graphics();
            graphics.fill(0, 0, frame.viewport().right(), frame.viewport().bottom(), 0x99000000);
            if (fits) graphics.fill(rootLayout.getX() - 6, content.y() - 6,
                    rootLayout.getX() + rootLayout.getWidth() + 6, content.bottom() + 6, DarkModeTheme.surfaceColor());
        }
        if (!Minecraft.getInstance().isWindowActive() && screen.isDragging()) {
            if (screen.getFocused() instanceof UiSlider slider) slider.cancelEdit();
            screen.setDragging(false);
        }
        refresh(screen);
        revealFocus(screen);
        var font = Minecraft.getInstance().font;
        if (!fits) {
            frame.graphics().drawWordWrap(font, Component.translatable("sh_better_ui.pages.viewport_small"),
                    frame.viewport().x() + INSET, frame.viewport().y() + INSET,
                    Math.max(1, frame.viewport().width() - 2 * INSET), DarkModeTheme.textColor());
        } else {
            var status = switch (page.actionState()) {
                case IDLE -> null;
                case PENDING -> "sh_better_ui.pages.action.pending";
                case SUCCEEDED -> "sh_better_ui.pages.action.succeeded";
                case FAILED -> "sh_better_ui.pages.action.failed";
                case TIMED_OUT -> "sh_better_ui.pages.action.timed_out";
                case CANCELLED -> "sh_better_ui.pages.action.cancelled";
            };
            if (!page.snapshot().synchronizedData()) status = "sh_better_ui.pages.syncing";
            if (status != null) frame.graphics().drawCenteredString(font, Component.translatable(status),
                    frame.viewport().x() + frame.viewport().width() / 2,
                    frame.viewport().bottom() - STATUS_HEIGHT, DarkModeTheme.textColor());
        }
    }

    void presented(UiScreen screen, UiFrame frame) {
        if (fits && frame.viewport().height() > 0) page.presented();
    }

    private void refresh(UiScreen screen) {
        var snapshot = page.snapshot();
        if (rendered == snapshot && !localDirty) return;
        rendered = snapshot;
        localDirty = false;
        for (var binding : bindings) {
            var node = binding.node;
            var widget = binding.widget;
            var data = snapshot.data();
            widget.visible = fits && widget.getWidth() > 0 && widget.getHeight() > 0
                    && inheritedFlag(node, binding.parents, "visible", data);
            widget.active = widget.visible && (node.action().isEmpty()
                    || snapshot.synchronizedData() && page.actionAvailable())
                    && inheritedFlag(node, binding.parents, "enabled", data);
            if (node.typeId().path().equals("button") && node.action().isEmpty()) widget.active = false;
            binding.update.accept(data);
            if ((!widget.active || !widget.visible) && screen.getFocused() == widget) screen.setFocused(null);
            var tooltip = text(value(node, "tooltip", data));
            if (!tooltip.getString().isEmpty()) widget.setTooltip(Tooltip.create(tooltip));
            else if (widget instanceof PassiveWidget passive) passive.setDefaultTooltip();
            else if (node.bindings().containsKey("tooltip")) widget.setTooltip(null);
        }
        for (var region : scrollRegions.values()) {
            region.bar.visible = fits && region.bar.getHeight() > 0 && region.scroll.maxOffset() > 0
                    && inheritedFlag(region.node, region.parents, "visible", snapshot.data());
            region.bar.active = region.bar.visible && inheritedFlag(region.node, region.parents, "enabled", snapshot.data());
        }
    }

    private Bounds clip(List<UiNode> ancestors) {
        Bounds clipped = content;
        for (var node : ancestors) {
            var region = scrollRegions.get(node.id());
            if (region != null) clipped = intersect(clipped, region.viewport());
        }
        return clipped;
    }

    private static Bounds intersect(Bounds first, Bounds second) {
        int x = Math.max(first.x(), second.x()), y = Math.max(first.y(), second.y());
        return new Bounds(x, y, Math.max(0, Math.min(first.right(), second.right()) - x),
                Math.max(0, Math.min(first.bottom(), second.bottom()) - y));
    }

    private static boolean intersects(LayoutElement element, Bounds bounds) {
        return bounds != null && bounds.width() > 0 && bounds.height() > 0
                && element.getX() < bounds.right() && element.getX() + element.getWidth() > bounds.x()
                && element.getY() < bounds.bottom() && element.getY() + element.getHeight() > bounds.y();
    }

    private boolean scrollBy(double delta) {
        if (!fits || !scrolling.scrollBy(delta)) return false;
        place();
        return true;
    }

    private boolean scrollAt(double x, double y, double delta) {
        var regions = new ArrayList<>(scrollRegions.values());
        for (int index = regions.size() - 1; index >= 0; index--) {
            var region = regions.get(index);
            if (region.bar.active && intersect(clip(region.parents), region.bounds()).contains(x, y)
                    && region.scroll.scrollBy(delta)) {
                region.place();
                return true;
            }
        }
        return scrollBy(delta);
    }

    private void place() {
        rootLayout.setY(content.y() - (int) Math.round(scrolling.offset()));
    }

    private void revealFocus(UiScreen screen) {
        var focused = screen.getFocused() instanceof AbstractWidget widget ? widget : null;
        if (focused == lastFocus) return;
        lastFocus = focused;
        var binding = bindings.stream().filter(candidate -> candidate.widget == focused).findFirst().orElse(null);
        if (binding != null) {
            for (int index = binding.parents.size() - 1; index >= 0; index--) {
                var region = scrollRegions.get(binding.parents.get(index).id());
                if (region == null || region.scroll.maxOffset() <= 0) continue;
                int top = Math.max(0, focused.getY() - region.body.getY());
                region.scroll.reveal(top, top + focused.getHeight(), region.height);
                region.place();
            }
        }
        boolean pageControl = binding != null || scrollRegions.values().stream().anyMatch(region -> region.bar == focused);
        if (!pageControl || scrolling.maxOffset() <= 0) return;
        int top = Math.max(0, focused.getY() - rootLayout.getY());
        scrolling.reveal(top, top + focused.getHeight(), content.height());
        place();
    }

    boolean acceptsInput(UiNode node, List<UiNode> ancestors) {
        var current = page.snapshot();
        return fits && rendered != null && rendered.revision() == current.revision()
                && current.state() == PageService.State.ACTIVE && Minecraft.getInstance().isWindowActive()
                && bindings.stream().anyMatch(binding -> binding.node == node && intersects(binding.widget, clip(ancestors)))
                && inheritedFlag(node, ancestors, "visible", current.data())
                && inheritedFlag(node, ancestors, "enabled", current.data())
                && (node.action().isEmpty() || current.synchronizedData() && page.actionAvailable());
    }

    void edit(UiNode node, List<UiNode> ancestors, UiValue value) {
        if (!acceptsInput(node, ancestors)) return;
        var previous = drafts.put(node.id(), value);
        localDirty |= !value.equals(previous);
        if (node.action().isPresent() && (value instanceof UiValue.Text || value instanceof UiValue.Decimal)) {
            uncommitted.add(node.id());
        }
    }

    void commit(UiNode node, List<UiNode> ancestors, UiValue value) {
        if (!acceptsInput(node, ancestors)) return;
        edit(node, ancestors, value);
        uncommitted.remove(node.id());
        if (node.action().isEmpty()) return;
        if (node.properties().get("payloadField") instanceof UiValue.Text field) {
            page.act(node, Map.of(field.value(), value));
        } else {
            page.act(node);
        }
    }

    void cancelEdit(UiNode node, UiValue previous) {
        drafts.put(node.id(), previous);
        uncommitted.remove(node.id());
        localDirty = true;
    }

    UiValue input(UiNode node, String property, Map<String, UiValue> data) {
        var authority = value(node, property, data);
        if (node.bindings().containsKey(property) && authority != null) {
            var previous = lastAuthority.put(node.id(), authority);
            if (previous != null && !previous.equals(authority)) {
                drafts.remove(node.id());
                uncommitted.remove(node.id());
            }
        }
        return drafts.getOrDefault(node.id(), authority);
    }

    String query(String id, Map<String, UiValue> data) {
        var search = searchFields.get(id);
        return search == null ? "" : text(input(search, "value", data)).getString();
    }

    TabScrollState listScroll(String nodeId) {
        return listScroll.computeIfAbsent(nodeId, ignored -> new TabScrollState());
    }

    UiLocalModal options() { return options; }

    static UiValue value(UiNode node, String key, Map<String, UiValue> data) {
        var binding = node.bindings().get(key);
        if (binding == null) return node.properties().get(key);
        return data.getOrDefault(binding, node.properties().get(key));
    }

    private static boolean flag(UiNode node, String key, Map<String, UiValue> data) {
        return !(value(node, key, data) instanceof UiValue.Bool flag) || flag.value();
    }

    private static boolean inheritedFlag(UiNode node, List<UiNode> parents, String key, Map<String, UiValue> data) {
        return flag(node, key, data) && parents.stream().allMatch(parent -> flag(parent, key, data));
    }

    static int number(UiValue value, int fallback) {
        return value instanceof UiValue.Int number ? number.value() : fallback;
    }

    static Component text(UiValue value) {
        if (value instanceof UiValue.Text text) return Component.literal(text.value());
        if (value instanceof UiValue.Int number) return Component.literal(Integer.toString(number.value()));
        if (value instanceof UiValue.LongInt number) return Component.literal(Long.toString(number.value()));
        if (value instanceof UiValue.Decimal number) return Component.literal(Double.toString(number.value()));
        return Component.empty();
    }

    static ItemStack item(UiValue value) {
        if (!(value instanceof UiValue.ItemReference item)) return ItemStack.EMPTY;
        var id = ResourceLocation.tryParse(item.itemId().toString());
        if (id == null) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(id), item.count());
    }

    private record LayoutBox(UiNode node, List<UiNode> parents, LayoutElement element) {}

    private record Binding(UiNode node, List<UiNode> parents, AbstractWidget widget,
                           Consumer<Map<String, UiValue>> update) {}

    static final class PassiveWidget extends AbstractWidget {
        private final String type;
        private NativeTextReadability.Label readableLabel;
        private double progress;
        private ItemStack item = ItemStack.EMPTY;
        private Component narration = Component.empty();
        private ResourceLocation texture;

        PassiveWidget(int width, int height, String type) {
            super(0, 0, width, height, Component.empty());
            this.type = type;
        }

        @Override public boolean mouseClicked(double x, double y, int button) { return false; }

        void update(UiNode node, Map<String, UiValue> data, UiPreferences preferences) {
            var message = text(value(node, "text", data));
            if (type.equals("rich_text")) message = rich(message.getString(), preferences.highContrast());
            if (!getMessage().equals(message)) setMessage(message);
            progress = value(node, "value", data) instanceof UiValue.Decimal decimal ? decimal.value() : 0;
            item = item(value(node, "item", data));
            narration = text(value(node, "narration", data));
            if (type.equals("icon") || type.equals("image")) {
                texture = texture(value(node, type, data));
            }
        }

        void setDefaultTooltip() {
            Component tooltip = type.equals("tooltip") ? getMessage()
                    : !item.isEmpty() ? item.getHoverName() : Component.empty();
            setTooltip(tooltip.getString().isEmpty() ? null : Tooltip.create(tooltip));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            graphics.enableScissor(x, y, x + w, y + h);
            try {
                var font = Minecraft.getInstance().font;
                switch (type) {
                    case "divider" -> graphics.fill(x, y, x + w, y + 1, DarkModeTheme.textColor());
                    case "progress_bar" -> {
                        graphics.fill(x, y, x + w, y + h, DarkModeTheme.surfaceColor());
                        graphics.fill(x, y, x + (int) (progress * w), y + h, DarkModeTheme.textColor());
                    }
                    case "item_display" -> {
                        graphics.renderItem(item, x, y);
                        if (!item.isEmpty()) graphics.renderItemDecorations(font, item, x, y);
                    }
                    case "icon", "image" -> {
                        if (texture != null) {
                            int drawWidth = type.equals("icon") ? Math.min(w, h) : w;
                            graphics.blit(texture, x, y, 0f, 0f, drawWidth, h, drawWidth, h);
                        } else if (!narration.getString().isEmpty()) {
                            drawLabel(graphics, font, narration, x, y, Math.max(1, w));
                        }
                    }
                    case "badge" -> {
                        graphics.fill(x, y, x + w, y + h, DarkModeTheme.surfaceColor());
                        drawLabel(graphics, font, getMessage(), x + 3, y + 3, Math.max(1, w - 6));
                    }
                    default -> drawLabel(graphics, font, getMessage(), x, y, Math.max(1, w));
                }
            } finally {
                graphics.disableScissor();
            }
        }

        private void drawLabel(GuiGraphics graphics, Font font, Component message,
                               int x, int y, int width) {
            int color = DarkModeTheme.textColor();
            if (readableLabel == null) {
                graphics.drawWordWrap(font, message, x, y, width, color);
                return;
            }
            int number = 0;
            for (var line : font.split(message, width)) {
                if (!readableLabel.draw(graphics, font, line, x, y, color, false, number++, isHovered, isFocused())) {
                    graphics.drawString(font, line, x, y, color, false);
                }
                y += font.lineHeight;
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                    narration.getString().isEmpty() ? getMessage() : narration);
        }
    }

    private static ResourceLocation texture(UiValue value) {
        String reference = value instanceof UiValue.Text text ? text.value()
                : value instanceof UiValue.Choice choice ? "sh_better_ui:" + choice.value() : "";
        var resource = reference.isEmpty() ? null : ResourceLocation.tryParse(reference);
        if (resource == null) return null;
        if (resource.getNamespace().equals("sh_better_ui")
                && resource.getPath().indexOf('/') < 0 && resource.getPath().indexOf('.') < 0) {
            resource = ResourceLocation.tryParse("sh_better_ui:textures/gui/display/" + resource.getPath() + ".png");
        }
        return resource != null && Minecraft.getInstance().getResourceManager().getResource(resource).isPresent()
                ? resource : null;
    }

    private static Component rich(String source, boolean highContrast) {
        try {
            var document = RichTextMarkup.parse(UiBuiltinComponents.id("page_text"), source);
            var result = Component.empty();
            for (var node : document.nodes()) {
                if (node instanceof TextRun run) {
                    result.append(Component.literal(run.text()).withStyle(style(run.style(), highContrast)));
                } else if (node instanceof ParagraphBreak) {
                    result.append(Component.literal("\n\n"));
                } else if (node instanceof LocalizedText localized) {
                    result.append(Component.translatableWithFallback(localized.key(), localized.fallback(),
                            localized.arguments().toArray()).withStyle(style(localized.style(), highContrast)));
                } else if (node instanceof ImageNode image) {
                    result.append(Component.literal(image.altText()));
                }
            }
            return result;
        } catch (IllegalArgumentException invalidMarkup) {
            return Component.literal(source);
        }
    }

    private static Style style(RichStyle style, boolean highContrast) {
        var nativeStyle = Style.EMPTY.withBold(style.bold()).withItalic(style.italic())
                .withUnderlined(style.underlined()).withStrikethrough(style.strikethrough());
        return !highContrast && style.color() != null ? nativeStyle.withColor(style.color()) : nativeStyle;
    }

    private static final class Scrollbar extends AbstractWidget {
        private final TabScrollState state;
        private final Runnable changed;

        Scrollbar(int x, int y, int height, TabScrollState state, Runnable changed) {
            super(x, y, SCROLL_RAIL, height, Component.translatable("sh_better_ui.pages.scroll"));
            this.state = state;
            this.changed = changed;
        }

        @Override public void onClick(double x, double y) { dragTo(y); }

        @Override
        protected void onDrag(double x, double y, double dx, double dy) {
            if (active && visible) dragTo(y);
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (!isFocused() || !active) return false;
            boolean moved = switch (key) {
                case GLFW.GLFW_KEY_UP -> state.scrollBy(-20);
                case GLFW.GLFW_KEY_DOWN -> state.scrollBy(20);
                case GLFW.GLFW_KEY_PAGE_UP -> state.scrollBy(-getHeight());
                case GLFW.GLFW_KEY_PAGE_DOWN -> state.scrollBy(getHeight());
                case GLFW.GLFW_KEY_HOME -> state.scrollTo(0);
                case GLFW.GLFW_KEY_END -> state.scrollTo(state.maxOffset());
                default -> false;
            };
            if (moved) changed.run();
            return moved;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int travel = getHeight() - thumb();
            int top = getY() + (int) Math.round(travel * state.offset() / state.maxOffset());
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), DarkModeTheme.surfaceColor());
            graphics.fill(getX() + 1, top, getX() + getWidth() - 1, top + thumb(), DarkModeTheme.textColor());
            if (isFocused()) graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), DarkModeTheme.textColor());
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        private int thumb() {
            return Math.min(getHeight(), Math.max(8, (int) (getHeight() * (double) getHeight()
                    / Math.max(1, getHeight() + state.maxOffset()))));
        }

        private void dragTo(double y) {
            int thumb = thumb();
            if (state.scrollTo(state.maxOffset() * (y - getY() - thumb / 2.0)
                    / Math.max(1, getHeight() - thumb))) changed.run();
        }
    }

    private static final class ScrollRegion implements Layout {
        private final UiNode node;
        private final List<UiNode> parents;
        private final int width;
        private final int height;
        private final TabScrollState scroll;
        private int x;
        private int y;
        private GridLayout body;
        private Scrollbar bar;

        private ScrollRegion(UiNode node, List<UiNode> parents, int width, int height, TabScrollState scroll) {
            this.node = node;
            this.parents = parents;
            this.width = width;
            this.height = height;
            this.scroll = scroll;
        }

        Bounds viewport() { return new Bounds(x, y, Math.max(0, width - SCROLL_RAIL), height); }
        Bounds bounds() { return new Bounds(x, y, width, height); }

        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
        @Override public void setX(int x) { this.x = x; place(); }
        @Override public void setY(int y) { this.y = y; place(); }

        @Override
        public void arrangeElements() {
            body.arrangeElements();
            scroll.resize(height, body.getHeight());
            place();
        }

        @Override
        public void visitChildren(Consumer<LayoutElement> visitor) {
            visitor.accept(body);
            visitor.accept(bar);
        }

        void place() {
            body.setX(x);
            body.setY(y - (int) Math.round(scroll.offset()));
            bar.setX(x + Math.max(0, width - SCROLL_RAIL));
            bar.setY(y);
        }
    }
}
