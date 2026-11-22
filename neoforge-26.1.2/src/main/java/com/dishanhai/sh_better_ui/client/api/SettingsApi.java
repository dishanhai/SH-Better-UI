// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperGalleryScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.NativeAppearanceBackground;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/** Applied client preferences and the native control panel. */
public final class SettingsApi {
    SettingsApi() {}

    public ControlPanelSettings current() { return ControlPanelSettings.current(); }

    /** Applied custom palette and independent appearance options. */
    public ThemeSettings theme() { return current().theme(); }

    /** Persists the theme without overwriting unrelated control panel settings. */
    public boolean saveTheme(ThemeSettings theme) {
        return ControlPanelSettings.save(current().withTheme(theme));
    }

    /** Background IDs rendered by this platform, in selection order. */
    public List<String> backgroundIds() { return NativeAppearanceBackground.supportedIds(); }

    /** Includes compatibility aliases; unsupported saved options can remain dormant in the theme. */
    public boolean supportsBackground(String id) { return NativeAppearanceBackground.supported(id); }

    public UiPreferences preferences() { return UiPreferences.configured(); }

    public Screen feedbackScreen(@Nullable Screen parent) {
        return new com.dishanhai.sh_better_ui.client.gui.panel.NativeFeedbackScreen(parent);
    }

    public ControlPanelScreen controlPanel(@Nullable Screen parent) { return new ControlPanelScreen(parent); }

    public void openControlPanel() { ControlPanelScreen.open(Minecraft.getInstance()); }

    /** User-facing player route; does not enter the developer gallery. */
    public UiScreen onlineMusicScreen(@Nullable Screen parent) {
        return onlineMusicScreen(parent, returnFocus(parent));
    }

    public UiScreen onlineMusicScreen(@Nullable Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        return com.dishanhai.sh_better_ui.kernel.internal.music.view.OnlineMusicScreen.create(parent, returnFocus);
    }

    public boolean activitiesAvailable() {
        return com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.activitiesAvailable();
    }

    /** Opens controls for current real publications; never creates a demonstration activity. */
    public Screen activityScreen(@Nullable Screen parent) {
        return com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.activityScreen(parent);
    }

    public boolean musicActivityAvailable() { return com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.musicActivityAvailable(); }

    public boolean dockExampleAvailable() { return com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.dockExampleAvailable(); }
    public Screen dockExampleScreen(@Nullable Screen parent) {
        return com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.dockExampleScreen(parent);
    }

    public Screen musicSettingsScreen(@Nullable Screen parent) {
        return com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.musicSettingsScreen(parent);
    }

    public Screen richDocumentScreen(@Nullable Screen parent, net.minecraft.network.chat.Component title,
            com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument document) {
        return ShBetterUiApi.richDocuments().screen(parent, title, document);
    }

    public Screen richDocumentExample(@Nullable Screen parent) {
        var prefix = "sh_better_ui:rich_example/";
        var document = new com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument(java.util.List.of(
                new com.dishanhai.sh_better_ui.kernel.api.richtext.LocalizedText(
                        com.dishanhai.sh_better_ui.kernel.api.UiId.parse(prefix + "text"),
                        "sh_better_ui.native.rich_document.example_text", "Resource image and styled text: ", java.util.List.of(),
                        com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle.EMPTY),
                new com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode(
                        com.dishanhai.sh_better_ui.kernel.api.UiId.parse(prefix + "image"),
                        com.dishanhai.sh_better_ui.kernel.api.UiId.parse("sh_better_ui:textures/gui/rich/material-shop.png"),
                        64, 64, "Material icon"),
                new com.dishanhai.sh_better_ui.kernel.api.richtext.ParagraphBreak(
                        com.dishanhai.sh_better_ui.kernel.api.UiId.parse(prefix + "break"), 12),
                new com.dishanhai.sh_better_ui.kernel.api.richtext.LocalizedText(
                        com.dishanhai.sh_better_ui.kernel.api.UiId.parse(prefix + "media_label"),
                        "sh_better_ui.native.rich_document.media_example", "Local resource-pack animation: ", java.util.List.of(),
                        com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle.EMPTY),
                new com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode(
                        com.dishanhai.sh_better_ui.kernel.api.UiId.parse(prefix + "animation"),
                        com.dishanhai.sh_better_ui.kernel.api.UiId.parse("sh_better_ui:textures/gui/rich/native-motion.gif"),
                        128, 32, "Moving colored square")));
        return richDocumentScreen(parent, net.minecraft.network.chat.Component.translatable("sh_better_ui.native.rich_document.title"), document);
    }

    /** Local authoring entry. The initial document is caller-owned and no paths are written. */
    public Screen richDocumentEditor(@Nullable Screen parent) {
        var document = new com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument(java.util.List.of(
                new com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun(
                        com.dishanhai.sh_better_ui.kernel.api.UiId.parse("sh_better_ui:editor/text"),
                        net.minecraft.network.chat.Component.translatable("sh_better_ui.native.rich_document.example_text").getString(),
                        com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle.EMPTY)));
        return ShBetterUiApi.richDocuments().editor(parent,
                net.minecraft.network.chat.Component.translatable("sh_better_ui.rich_text_editor.title"), document);
    }

    public Screen appearanceScreen(@Nullable Screen parent) {
        return new com.dishanhai.sh_better_ui.client.gui.panel.NativeAppearanceScreen(parent);
    }

    public Screen markdownScreen(@Nullable Screen parent) {
        return new com.dishanhai.sh_better_ui.client.gui.panel.MarkdownExampleScreen(parent);
    }

    /** Creates a document viewer for consumer-provided Markdown, without opening it. */
    public Screen markdownScreen(@Nullable Screen parent, String source) {
        return new com.dishanhai.sh_better_ui.client.gui.panel.MarkdownExampleScreen(parent, source);
    }

    public DeveloperPanelScreen developerPanel(@Nullable Screen parent) { return new DeveloperPanelScreen(parent); }

    public DeveloperPanelScreen developerPanel(@Nullable Screen parent, UiPreferences preferences) {
        return new DeveloperPanelScreen(parent, preferences);
    }

    public boolean animationExampleAvailable() { return com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.animationExampleAvailable(); }
    public Screen animationExampleScreen(@Nullable Screen parent) {
        return com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.animationExampleScreen(parent);
    }

    /** Built-in and registered extension preview IDs, in gallery order. */
    public List<String> developerSampleIds() { return DeveloperGalleryScreen.sampleIds(); }

    public Screen developerSampleFullscreen(@Nullable Screen parent, String sampleId) {
        return developerSampleFullscreen(parent, sampleId, preferences());
    }

    public Screen developerSampleFullscreen(@Nullable Screen parent, String sampleId, UiPreferences preferences) {
        return developerSampleFullscreen(parent, sampleId, preferences, returnFocus(parent));
    }

    public Screen developerSampleFullscreen(@Nullable Screen parent, String sampleId, UiPreferences preferences,
                                            Supplier<? extends GuiEventListener> returnFocus) {
        return DeveloperGalleryScreen.fullscreen(parent, sampleId, preferences, returnFocus);
    }

    public Screen developerMaterialFullscreen(@Nullable Screen parent, String materialId) {
        return developerMaterialFullscreen(parent, materialId, preferences(), returnFocus(parent));
    }

    public Screen developerMaterialFullscreen(@Nullable Screen parent, String materialId, UiPreferences preferences,
                                              Supplier<? extends GuiEventListener> returnFocus) {
        return DeveloperGalleryScreen.fullscreenMaterial(parent, materialId, preferences, returnFocus);
    }

    private static Supplier<? extends GuiEventListener> returnFocus(@Nullable Screen parent) {
        var focused = parent == null ? null : parent.getFocused();
        int index = parent == null ? -1 : parent.children().indexOf(focused);
        return () -> {
            if (parent == null) return null;
            if (focused != null && parent.children().contains(focused)) return focused;
            return index >= 0 && index < parent.children().size() ? parent.children().get(index) : null;
        };
    }
}
