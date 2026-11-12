// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotBackground;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.indicator.*;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Native SDK example. Use the /shbetterdock command from the client. */
final class DockExampleScreen extends Screen {
    private static final int ACCENT = 0xff80caff;
    private final ModuleContext module;
    private final Screen parent;
    private final UiDotBackground backdrop = new UiDotBackground();
    private ScopeHandle scope;
    private MiniWindowHandle monitor;
    private MiniWindowHandle auxiliary;
    private int ticks, notices = 3;
    private float level = .6f;
    private boolean inspecting;
    private final float[] spectrum = new float[8];

    DockExampleScreen(ModuleContext module, Screen parent) {
        super(label("title"));
        this.module = module;
        this.parent = parent instanceof ChatScreen ? null : parent;
    }

    @Override protected void init() {
        if (scope == null || !scope.isOpen()) {
            scope = module.engine().require(UIApis.SCREENS).mount(module, this, UiId.parse("sh_better_ui:dock_example"));
        }
        if (monitor != null && monitor.isOpen()) return;
        monitor = module.engine().require(UIApis.WINDOWS).createMiniWindow(module, scope,
                MiniWindowSpec.builder("sh_better_ui:monitor").title(getTitle()).persistent(true)
                        .shortcut(GLFW.GLFW_KEY_F8).outsideClickCloses(true)
                        .glass(GlassMaterial.preset(GlassVariant.REGULAR))
                        .compactIndicator(() -> new LayeredIndicator(List.of(
                                new LayeredIndicator.Layer(new StatusIndicator(8, () -> new StatusIndicator.State(
                                        StatusIndicator.Shape.READY, ACCENT)), 10, 5),
                                new LayeredIndicator.Layer(new NotificationIndicator(20, 12, () -> notices,
                                        value -> label("count", value), 0xffffffff), 4, 18),
                                new LayeredIndicator.Layer(new SpectrumIndicator(20, 23, () -> spectrum, ACCENT), 4, 33),
                                new LayeredIndicator.Layer(new ProgressIndicator(20, 4, () -> level, ACCENT,
                                        0xff304355), 4, 65))))
                        .content(MonitorContent::new).build());
    }

    @Override public void tick() {
        if (monitor != null && !monitor.isOpen() && scope != null && scope.isOpen()) init();
        ticks++;
        for (int i = 0; i < spectrum.length; i++) spectrum[i] = (float) (.5 + .4 * Math.sin(ticks * .2 + i * 1.3));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        backdrop.render(this, graphics, width, height, UiPreferences.configured());
        var available = scope == null || !scope.isOpen() ? null
                : module.engine().require(UIApis.LAYOUT).dockContentArea(module, scope).orElse(null);
        int center = available == null ? width / 2 : (int) (available.x() + available.width() / 2);
        int lineWidth = available == null ? width : (int) available.width();
        graphics.drawCenteredString(font, getTitle(), center, 17, 0xffffffff);
        int lineY = 34;
        for (var line : font.split(label("help"), Math.max(1, lineWidth - 20))) {
            graphics.drawCenteredString(font, line, center, lineY, 0xffd3e1ee);
            lineY += font.lineHeight + 2;
        }
        if (inspecting && monitor != null && monitor.isOpen()) {
            monitor.snapshot().ifPresent(view -> {
                var placement = view.geometry().placement();
                var metrics = module.engine().require(UIApis.DOCK).diagnostics();
                graphics.drawString(font, label("inspect.state", view.state().name(), Math.round(view.progress() * 100),
                        view.layoutVersion()), 10, height - 41, 0xffffffff, false);
                graphics.drawString(font, label("inspect.input", view.hovered(), view.focused(), view.pointerCaptured(),
                        view.targetExpanded()), 10, height - 29, 0xffffffff, false);
                graphics.drawString(font, label("inspect.layout", placement.edge().name(), placement.mode().name(),
                        view.geometry().zIndex(), metrics.layoutNanos() / 1000, metrics.paintNanos() / 1000),
                        10, height - 17, 0xffffffff, false);
            });
        }
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_F9) {
            inspecting = !inspecting;
            return true;
        }
        if (monitor != null && monitor.isOpen()) {
            switch (key) {
                case GLFW.GLFW_KEY_F6 -> { monitor.resetPlacement(); return true; }
                case GLFW.GLFW_KEY_F7 -> {
                    var current = monitor.snapshot().map(s -> s.geometry().placement().edge()).orElse(DockEdge.LEFT);
                    var next = DockEdge.values()[(current.ordinal() + 1) % DockEdge.values().length];
                    monitor.dock(next);
                    if (auxiliary != null && auxiliary.isOpen()) auxiliary.dock(next);
                    return true;
                }
                case GLFW.GLFW_KEY_F10 -> { toggleAuxiliary(); return true; }
                default -> {}
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void toggleAuxiliary() {
        if (auxiliary != null && auxiliary.isOpen()) {
            auxiliary.close();
            return;
        }
        var anchor = monitor.snapshot().map(s -> s.geometry().placement().edge()).orElse(DockEdge.LEFT);
        auxiliary = module.engine().require(UIApis.WINDOWS).createMiniWindow(module, scope,
                MiniWindowSpec.builder("sh_better_ui:monitor_notifications").title(label("auxiliary"))
                        .anchor(anchor).size(172, 96).compactSize(28, 32).priority(-1)
                        .expansionLayout(ExpansionLayoutMode.PUSH)
                        .compactIndicator(() -> new NotificationIndicator(20, 20, () -> notices,
                                value -> label("count", value), 0xffffffff))
                        .content(() -> (draw, view) -> draw.text(label("notices", notices), (int) view.bounds().x() + 6,
                                (int) view.bounds().y() + 8, view.foregroundArgb())).build());
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private final class MonitorContent implements WindowContent {
        private final StringBuilder note = new StringBuilder();
        private int row;
        private boolean editing;

        @Override public void render(RenderService.Context draw, View view) {
            var bounds = view.bounds();
            int x = (int) bounds.x() + 8, y = (int) bounds.y(), w = Math.max(0, (int) bounds.width() - 16);
            int foreground = view.foregroundArgb();
            draw.text(label("level", Math.round(level * 100)), x, y + 5, foreground);
            draw.fill(x, y + 18, x + w, y + 26, 0xff354253);
            draw.fill(x, y + 18, x + Math.round(w * level), y + 26, ACCENT);
            draw.text(label("notices", notices), x, y + 34, foreground);
            draw.fill(x, y + 48, x + w, y + 49, editing ? ACCENT : 0xff66768a);
            draw.text(note.isEmpty() ? label("note") : Component.literal(note.toString()), x, y + 54, foreground);
            draw.text(label("scroll", row + 1, 12), x, y + 75, foreground);
        }

        @Override public Response input(NativeInput value, View view) {
            if (value instanceof NativeInput.Pointer pointer) {
                double localY = pointer.y() - view.bounds().y();
                if (pointer.action() == NativeInput.PointerAction.SCROLL) {
                    row = Math.max(0, Math.min(11, row + (pointer.deltaY() > 0 ? -1 : 1)));
                    return Response.HANDLED;
                }
                if (pointer.action() == NativeInput.PointerAction.DRAG || pointer.action() == NativeInput.PointerAction.RELEASE) {
                    slide(pointer.x(), view);
                    return Response.HANDLED;
                }
                if (pointer.action() != NativeInput.PointerAction.PRESS || pointer.button() != 0) return Response.PASS;
                if (localY >= 14 && localY < 30) { slide(pointer.x(), view); return Response.CAPTURE; }
                if (localY >= 30 && localY < 48) { notices = Math.max(0, notices - 1); return Response.HANDLED; }
                if (localY >= 48 && localY < 69) { editing = true; return Response.FOCUS; }
                return Response.PASS;
            }
            if (value instanceof NativeInput.Character character && editing && note.length() < 80
                    && !Character.isISOControl(character.codeUnit())) {
                note.append(character.codeUnit());
                return Response.HANDLED;
            }
            if (value instanceof NativeInput.Key key && key.action() != NativeInput.KeyAction.RELEASE) {
                if (editing && (key.key() == GLFW.GLFW_KEY_ESCAPE || key.key() == GLFW.GLFW_KEY_ENTER)) {
                    editing = false;
                    return Response.HANDLED;
                }
                if (editing && key.key() == GLFW.GLFW_KEY_BACKSPACE) {
                    if (!note.isEmpty()) note.delete(note.offsetByCodePoints(note.length(), -1), note.length());
                    return Response.HANDLED;
                }
                if (!editing && key.key() == GLFW.GLFW_KEY_ENTER) {
                    editing = true;
                    return Response.HANDLED;
                }
                if (!editing && (key.key() == GLFW.GLFW_KEY_LEFT || key.key() == GLFW.GLFW_KEY_RIGHT)) {
                    level = Math.max(0, Math.min(1, level + (key.key() == GLFW.GLFW_KEY_LEFT ? -.05f : .05f)));
                    return Response.HANDLED;
                }
            }
            return Response.PASS;
        }

        private void slide(double x, View view) {
            level = (float) Math.max(0, Math.min(1, (x - view.bounds().x() - 8) / Math.max(1, view.bounds().width() - 16)));
        }

        @Override public void focusChanged(boolean focused) { if (!focused) editing = false; }
        @Override public Component narration() { return label("note"); }
    }

    private static Component label(String key, Object... arguments) {
        return Component.translatable("sh_better_ui.dock_example." + key, arguments);
    }
}
