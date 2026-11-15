// 作者：山海／dishanhai
package com.dishanhai.sh_better_ui.automation.client;

import com.dishanhai.sh_better_ui.automation.AutomationPage;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 在原页面上编辑，保留服务端可见性租约；取消不提交，应用只更新同一服务端草稿。 */
final class AutomationEditor implements UiLocalModal.Content {
    private final UiLocalModal.Session session;
    private final PageService.Snapshot snapshot;
    private final Map<String, UiValue> draft = new LinkedHashMap<>();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<Node> nodes = new ArrayList<>();
    private final Bounds bounds;
    private AbstractWidget focused;
    private AbstractWidget pressed;
    private Component status = Component.empty();
    private final boolean scriptMode;

    static void open(UiScreen screen, UiButton source, boolean script) {
        ClientUiKernel.activeServerPage().ifPresent(snapshot -> {
            var modal = new UiLocalModal(screen, screen.context().preferences());
            modal.open(source, screen.viewport(), (start, viewport) -> new Bounds(viewport.x() + 4,
                    viewport.y() + 4, Math.max(1, viewport.width() - 8), Math.max(1, viewport.height() - 8)),
                    UiExpansionController.Config.DEFAULT,
                    session -> new AutomationEditor(session, snapshot, script), () -> {});
        });
    }

    private AutomationEditor(UiLocalModal.Session session, PageService.Snapshot snapshot, boolean script) {
        this.session = session;
        this.snapshot = snapshot;
        this.scriptMode = script;
        bounds = session.target();
        AutomationPage.editSchema().fields().keySet().forEach(key -> draft.put(key, snapshot.data().get(key)));
        draft.put("editor", new UiValue.Int(script ? 2 : 1));
        int x = bounds.x() + 10, width = Math.max(40, bounds.width() - 20), y = bounds.y() + 30;
        if (bounds.height() < (script ? 150 : 200) || bounds.width() < 260) {
            status = Component.translatable("sh_better_ui.pages.viewport_small");
            widgets.add(button(x, bounds.bottom() - 30, width, "gui.cancel", session::cancel));
            focus(widgets.getFirst());
            return;
        }
        if (script) {
            var editor = new MultiLineEditBox(Minecraft.getInstance().font, x, y, width,
                    Math.max(40, bounds.height() - 94), Component.translatable("sh_better_ui.automation.script"),
                    Component.translatable("sh_better_ui.automation.script"));
            editor.setCharacterLimit(4096);
            editor.setValue(text("script"));
            editor.setValueListener(value -> draft.put("script", new UiValue.Text(value)));
            widgets.add(editor);
        } else {
            var condition = new Node(x + 2, y, Math.min(150, width / 2 - 8), "condition");
            var action = new Node(x + width / 2 + 4, y + 22, Math.min(150, width / 2 - 8), "action");
            nodes.add(condition);
            nodes.add(action);
            widgets.addAll(nodes);
            int fieldY = y + 66;
            String[] fields = {"ruleId", "item", "lower", "targetAmount", "target"};
            String[] labels = {"rule_id", "item", "lower", "target_amount", "target"};
            int fieldWidth = (width - 6) / 2;
            for (int i = 0; i < fields.length; i++) {
                String field = fields[i];
                var edit = new EditBox(Minecraft.getInstance().font, x + (i % 2) * (fieldWidth + 6),
                        fieldY + (i / 2) * 26, fieldWidth, 20, tr(labels[i]));
                edit.setMaxLength(256);
                edit.setHint(tr(labels[i]));
                edit.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr(labels[i])));
                edit.setValue(text(field));
                edit.setResponder(value -> draft.put(field, new UiValue.Text(value)));
                widgets.add(edit);
            }
            widgets.add(new UiButton(x + fieldWidth + 6, fieldY + 52, fieldWidth, 20,
                    tr(mode() == 0 ? "mode_page" : "mode_online"),
                    button -> { draft.put("mode", new UiValue.Int(1 - mode()));
                        button.setMessage(tr(mode() == 0 ? "mode_page" : "mode_online")); }));
        }
        widgets.add(button(x, bounds.bottom() - 28, width / 2 - 3, "sh_better_ui.automation.apply", this::apply));
        widgets.add(button(x + width / 2 + 3, bounds.bottom() - 28, width / 2 - 3, "gui.cancel", session::cancel));
        focus(widgets.getFirst());
    }

    private int mode() { return draft.get("mode") instanceof UiValue.Int value ? value.value() : 0; }
    private String text(String field) { return draft.get(field) instanceof UiValue.Text value ? value.value() : ""; }
    private static Component tr(String key) { return Component.translatable("sh_better_ui.automation." + key); }
    private UiButton button(int x, int y, int width, String key, Runnable callback) {
        return new UiButton(x, y, width, 20, Component.translatable(key), ignored -> callback.run());
    }

    private void apply() {
        var current = ClientUiKernel.activeServerPage();
        if (current.isEmpty() || !current.orElseThrow().instanceId().equals(snapshot.instanceId())
                || AutomationPage.editSchema().fields().keySet().stream().anyMatch(field ->
                !java.util.Objects.equals(current.orElseThrow().data().get(field), snapshot.data().get(field)))) {
            status = tr("stale");
            return;
        }
        if (ClientUiKernel.submitServerPageAction(snapshot.instanceId(), AutomationPage.action("apply"), Map.copyOf(draft))) {
            session.confirm();
        } else status = tr("stale");
    }

    @Override public void render(GuiGraphics graphics, UiLocalModal.Frame frame, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, status.getString().isEmpty() ? tr(scriptMode ? "script_editor" : "node_hint") : status,
                bounds.x() + 10, bounds.y() + 10,
                DarkModeTheme.textColor(), false);
        if (nodes.size() == 2) {
            var from = nodes.getFirst(); var to = nodes.getLast();
            int x1 = from.getX() + from.getWidth(), y1 = from.getY() + from.getHeight() / 2;
            int x2 = to.getX(), y2 = to.getY() + to.getHeight() / 2;
            graphics.fill(Math.min(x1, x2), y1, Math.max(x1, x2) + 1, y1 + 2, DarkModeTheme.textColor());
            graphics.fill(x2, Math.min(y1, y2), x2 + 2, Math.max(y1, y2) + 1, DarkModeTheme.textColor());
        }
        for (var widget : widgets) widget.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean mousePressed(double x, double y, int button, long now) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            var widget = widgets.get(i);
            if (widget.mouseClicked(x, y, button)) { focus(widget); pressed = widget; return true; }
        }
        return false;
    }
    @Override public void mouseDragged(double x, double y, int button, long now) {
        if (pressed != null) pressed.mouseDragged(x, y, button, 0, 0);
    }
    @Override public void mouseReleased(double x, double y, int button, long now) {
        if (pressed != null) pressed.mouseReleased(x, y, button);
        pressed = null;
    }
    @Override public void mouseScrolled(double x, double y, double horizontal, double vertical) {
        for (var widget : widgets) if (widget.isMouseOver(x, y)) widget.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers, long now) {
        if (key == GLFW.GLFW_KEY_TAB) {
            int direction = (modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1;
            focus(widgets.get(Math.floorMod(widgets.indexOf(focused) + direction, widgets.size())));
            return true;
        }
        return focused != null && focused.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers, long now) {
        return focused != null && focused.charTyped(character, modifiers);
    }
    private void focus(AbstractWidget next) {
        if (focused != null) focused.setFocused(false);
        focused = next;
        focused.setFocused(true);
        Minecraft.getInstance().getNarrator().sayNow(focused.getMessage());
    }

    private final class Node extends AbstractWidget {
        private final UiButton appearance;
        private double offsetX, offsetY;
        Node(int x, int y, int width, String key) {
            super(x, y, width, 22, tr(key));
            appearance = new UiButton(x, y, width, 22, tr(key), ignored -> {});
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            appearance.setX(getX()); appearance.setY(getY()); appearance.setFocused(isFocused());
            appearance.render(graphics, mouseX, mouseY, partialTick);
        }
        @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (!super.mouseClicked(x, y, button)) return false;
            offsetX = x - getX(); offsetY = y - getY();
            return true;
        }
        @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            move((int) (x - offsetX), (int) (y - offsetY)); return true;
        }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                move(getX() + (key == GLFW.GLFW_KEY_LEFT ? -4 : key == GLFW.GLFW_KEY_RIGHT ? 4 : 0),
                        getY() + (key == GLFW.GLFW_KEY_UP ? -4 : key == GLFW.GLFW_KEY_DOWN ? 4 : 0));
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
        private void move(int x, int y) {
            setX(Math.clamp(x, bounds.x() + 10, bounds.right() - getWidth() - 10));
            setY(Math.clamp(y, bounds.y() + 30, bounds.y() + 72));
        }
    }
}
