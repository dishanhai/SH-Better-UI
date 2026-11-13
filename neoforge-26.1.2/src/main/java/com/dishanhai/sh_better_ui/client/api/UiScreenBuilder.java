// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/** Creates an opt-in native Screen for a module-owned UUK tree. */
public final class UiScreenBuilder {
    private final Component title;
    private Screen parent;
    private boolean parentSpecified;
    private boolean pausesGame;
    private Consumer<UiScreen> init = screen -> {};
    private Consumer<UiScreen> tick = screen -> {};
    private Consumer<UiScreen> removed = screen -> {};
    private UiScreen.Draw content = (screen, frame) -> {};
    private UiScreen.Draw foreground = (screen, frame) -> {};

    public UiScreenBuilder(Component title) { this.title = Objects.requireNonNull(title).copy(); }
    public UiScreenBuilder parent(Screen parent) { this.parent = parent; parentSpecified = true; return this; }
    public UiScreenBuilder pausesGame(boolean value) { pausesGame = value; return this; }
    public UiScreenBuilder onInit(Consumer<UiScreen> action) { init = Objects.requireNonNull(action); return this; }
    public UiScreenBuilder onTick(Consumer<UiScreen> action) { tick = Objects.requireNonNull(action); return this; }
    public UiScreenBuilder onRemoved(Consumer<UiScreen> action) { removed = Objects.requireNonNull(action); return this; }
    public UiScreenBuilder onRender(UiScreen.Draw draw) { content = Objects.requireNonNull(draw); return this; }
    public UiScreenBuilder onForeground(UiScreen.Draw draw) { foreground = Objects.requireNonNull(draw); return this; }

    public UiScreen build() {
        return new UiScreen(title.copy(), parent, parentSpecified, pausesGame, init, tick, removed, content, foreground);
    }

    public UiScreen open() {
        var screen = build();
        screen.open();
        return screen;
    }
}
