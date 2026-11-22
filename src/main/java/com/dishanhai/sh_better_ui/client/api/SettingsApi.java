package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.GlassBackdropSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;
import com.dishanhai.sh_better_ui.client.theme.DynamicThemePreview;
import com.google.gson.JsonObject;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.Supplier;

/** 山海：客户端设置快照、保存和内置面板入口。 */
public final class SettingsApi {
    SettingsApi() {}

    public UiPreferences preferences() {
        return UiPreferences.configured();
    }

    public UiPreferences preferences(ClientUiSettings settings) {
        return UiPreferences.fromSettings(settings);
    }

    public ClientUiSettings current() {
        return ClientUiSettings.current();
    }

    public GlassBackdropSettings backdrop() {
        return ThemeEffects.glass();
    }

    public boolean save(ClientUiSettings settings) {
        return ShBetterUi.saveClientUiSettings(settings);
    }

    public ThemeSettings theme() {
        return current().theme();
    }

    public boolean saveTheme(ThemeSettings theme) {
        ThemeEffects.validate(theme.effects());
        return save(current().withTheme(theme));
    }

    /** Background IDs rendered by this platform, in selection order. */
    public List<String> backgroundIds() {
        return ShBetterUiApi.backgrounds().entries().stream().map(BackgroundSpec::id).toList();
    }

    /** Includes compatibility aliases; unsupported saved options can remain dormant in the theme. */
    public boolean supportsBackground(String id) { return ShBetterUiApi.backgrounds().find(id).isPresent(); }

    public java.util.Set<String> themeGroups() { return ThemeEffects.groups(); }

    public JsonObject themeTemplate(String group) { return ThemeEffects.template(group); }

    public boolean saveBackdrop(GlassBackdropSettings settings) {
        return ShBetterUi.saveGlassBackdrop(settings);
    }

    /** 传入已注册的快捷键，面板使用库的客户端配置保存入口。 */
    public ControlPanelScreen controlPanel(Screen parent, KeyMapping keyMapping) {
        return new ControlPanelScreen(parent, keyMapping, ShBetterUi::saveClientUiSettings);
    }

    /** Creates the interactive dynamic theme example with its own screen lifetime. */
    public Screen dynamicThemePreview(Screen parent) {
        GuiEventListener focus = parent == null ? null : parent.getFocused();
        return DynamicThemePreview.screen(parent, () -> focus);
    }

    /** Whether the client has published its real online music service; independent of developer mode. */
    public boolean onlineMusicAvailable() {
        return com.dishanhai.sh_better_ui.kernel.internal.music.view.OnlineMusicScreen.available();
    }

    /** Normal user-facing music page. Creating the route does not open a session or make requests. */
    public UiScreen onlineMusicScreen(Screen parent) {
        GuiEventListener focus = parent == null ? null : parent.getFocused();
        return onlineMusicScreen(parent, () -> focus);
    }

    /** The focus supplier is evaluated after the parent is rebuilt on return. */
    public UiScreen onlineMusicScreen(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        return com.dishanhai.sh_better_ui.kernel.internal.music.view.OnlineMusicScreen.create(parent, returnFocus);
    }

    public DeveloperPanelScreen developerPanel(Screen parent) {
        return new DeveloperPanelScreen(parent);
    }

    public DeveloperPanelScreen developerPanel(Screen parent, UiPreferences preferences) {
        return new DeveloperPanelScreen(parent, preferences);
    }

    /** 返回已登记的控件与材质示例，顺序与图库一致。内置示例保留原有短 ID。 */
    public List<String> developerSampleIds() {
        return DeveloperPanelScreen.sampleIds();
    }

    public Screen developerSampleFullscreen(Screen parent, String sampleId) {
        return developerSampleFullscreen(parent, sampleId, preferences());
    }

    public Screen developerSampleFullscreen(Screen parent, String sampleId, UiPreferences preferences) {
        GuiEventListener focused = parent == null ? null : parent.getFocused();
        return developerSampleFullscreen(parent, sampleId, preferences, () -> focused);
    }

    /** 构建可交互的全屏示例。父页重建后，returnFocus 提供新的焦点入口。 */
    public Screen developerSampleFullscreen(Screen parent, String sampleId, UiPreferences preferences,
                                            Supplier<? extends GuiEventListener> returnFocus) {
        return DeveloperPanelScreen.fullscreenSample(parent, sampleId, preferences, returnFocus);
    }

    /** Resolves a catalog ID or alias and builds its registered native preview. Call on the client thread. */
    public Screen developerMaterialFullscreen(Screen parent, String materialId) {
        var focus = parent == null ? null : parent.getFocused();
        return developerMaterialFullscreen(parent, materialId, preferences(), () -> focus);
    }

    public Screen developerMaterialFullscreen(Screen parent, String materialId, UiPreferences preferences,
                                              Supplier<? extends GuiEventListener> returnFocus) {
        return DeveloperPanelScreen.fullscreenMaterial(parent, materialId, preferences, returnFocus);
    }
}
