package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.theme.ScopeOverride;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 山海：Java 与客户端脚本共用的原生界面构建器，构建时冻结回调。 */
public final class UiScreenBuilder {
    private final Component title;
    private final Supplier<UiApiContext> contexts;
    private final Consumer<UiScreen> onBuilt;
    private Screen parent;
    private boolean parentSpecified;
    private Supplier<? extends GuiEventListener> returnFocus;
    private boolean pausesGame;
    private boolean backButton = true;
    private BackgroundMode backgroundMode = BackgroundMode.AUTO;
    private Consumer<UiScreen> onInit = screen -> {};
    private UiScreen.Draw onRender;
    private UiScreen.Draw onForeground = (screen, frame) -> {};
    private Consumer<UiScreen> onTick = screen -> {};
    private Consumer<UiScreen> onRemoved = screen -> {};
    private BiConsumer<UiScreen, Throwable> onError;
    private Consumer<ScopeOverride.Builder> theme;

    public UiScreenBuilder(Component title) {
        this(title, ShBetterUiApi::context, screen -> {});
    }

    UiScreenBuilder(Component title, Supplier<UiApiContext> contexts, Consumer<UiScreen> onBuilt) {
        this.title = Objects.requireNonNull(title).copy();
        this.contexts = Objects.requireNonNull(contexts);
        this.onBuilt = Objects.requireNonNull(onBuilt);
    }

    /** 显式传入 null 时关闭界面返回游戏，未设置时由 open() 取得当前父页。 */
    public UiScreenBuilder parent(Screen parent) {
        this.parent = parent;
        parentSpecified = true;
        return this;
    }

    /** 父页重新打开后取得当前控件，可恢复到重建后的入口。 */
    public UiScreenBuilder returnFocus(Supplier<? extends GuiEventListener> returnFocus) {
        this.returnFocus = Objects.requireNonNull(returnFocus);
        return this;
    }

    public UiScreenBuilder pausesGame(boolean pausesGame) {
        this.pausesGame = pausesGame;
        return this;
    }

    /** 默认显示返回顶栏，关闭后由调用方提供返回控件，并可使用完整 viewport。 */
    public UiScreenBuilder backButton(boolean backButton) {
        this.backButton = backButton;
        return this;
    }

    /** Draws the full-screen dot matrix before the content viewport is clipped. */
    public UiScreenBuilder dotMatrixBackground() {
        backgroundMode = BackgroundMode.DOTS;
        return this;
    }

    /** Draws the user's themed backdrop before the content viewport is clipped. */
    public UiScreenBuilder themeBackground() {
        backgroundMode = BackgroundMode.THEME;
        return this;
    }

    /** Opts into global dynamic themes with a screen-local override draft. */
    public UiScreenBuilder dynamicTheme(Consumer<ScopeOverride.Builder> theme) {
        this.theme = Objects.requireNonNull(theme);
        return this;
    }

    /** Opts into global dynamic themes with no local overrides. */
    public UiScreenBuilder dynamicTheme() { return dynamicTheme(ignored -> {}); }

    /** 初次打开、返回本页和 GUI 尺寸变化时调用，在此注册和重新布局原生控件。 */
    public UiScreenBuilder onInit(Consumer<UiScreen> onInit) {
        this.onInit = Objects.requireNonNull(onInit);
        return this;
    }

    /** 在原生控件之前绘制。可由回调绘制背景，也可使用 themeBackground() 或 dotMatrixBackground()。 */
    public UiScreenBuilder onRender(UiScreen.Draw onRender) {
        this.onRender = Objects.requireNonNull(onRender);
        return this;
    }

    /** 在原生控件之后绘制，与 onRender 共用同一帧快照。 */
    public UiScreenBuilder onForeground(UiScreen.Draw onForeground) {
        this.onForeground = Objects.requireNonNull(onForeground);
        return this;
    }

    public UiScreenBuilder onTick(Consumer<UiScreen> onTick) {
        this.onTick = Objects.requireNonNull(onTick);
        return this;
    }

    /** 每次离开本页调用一次，随后释放该次打开的上下文资源。 */
    public UiScreenBuilder onRemoved(Consumer<UiScreen> onRemoved) {
        this.onRemoved = Objects.requireNonNull(onRemoved);
        return this;
    }

    /** 回调运行异常时先销毁本页并释放资源，再报告异常。未设置时异常继续交给调用方。 */
    public UiScreenBuilder onError(BiConsumer<UiScreen, Throwable> onError) {
        this.onError = Objects.requireNonNull(onError);
        return this;
    }

    /** 只创建界面，不读取 Minecraft。直接 setScreen 时请按需先设置 parent。 */
    public UiScreen build() {
        var screen = new UiScreen(new Definition(title.copy(), contexts, parent, parentSpecified, returnFocus, pausesGame,
                backButton, backgroundMode, onInit, onRender, onForeground, onTick, onRemoved, onError, theme));
        onBuilt.accept(screen);
        return screen;
    }

    /** 在客户端线程打开快照构建的界面，返回该界面实例。 */
    public UiScreen open() {
        var screen = build();
        screen.open();
        return screen;
    }

    enum BackgroundMode { AUTO, DOTS, THEME }

    record Definition(Component title, Supplier<UiApiContext> contexts, Screen parent, boolean parentSpecified,
                      Supplier<? extends GuiEventListener> returnFocus, boolean pausesGame, boolean backButton,
                      BackgroundMode backgroundMode,
                      Consumer<UiScreen> onInit, UiScreen.Draw onRender, UiScreen.Draw onForeground,
                      Consumer<UiScreen> onTick, Consumer<UiScreen> onRemoved,
                      BiConsumer<UiScreen, Throwable> onError, Consumer<ScopeOverride.Builder> theme) {}
}
