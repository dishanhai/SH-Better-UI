// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.kernel.api.UIEngine;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperGalleryScreen;

/** Entry point for the native GUI features available on NeoForge 26. */
public final class ShBetterUiApi {
    private static final MusicApi MUSIC = new MusicApi();
    private static final MotionApi MOTION = new MotionApi();
    private static final InteractionApi INTERACTIONS = new InteractionApi();
    private static final SearchApi SEARCH = new SearchApi();
    private static final ControlsApi CONTROLS = new ControlsApi();
    private static final SettingsApi SETTINGS = new SettingsApi();
    private static final BlurApi BLUR = new BlurApi();
    private static final UiClientApi CLIENT = new UiClientApi();

    private static final RichDocumentsApi RICH_DOCUMENTS = new RichDocumentsApi();
    private static final MarkdownApi MARKDOWN = new MarkdownApi();
    private static final LinkApi LINKS = new LinkApi();

    public static RichDocumentsApi richDocuments() { return RICH_DOCUMENTS; }
    public static MarkdownApi markdown() { return MARKDOWN; }
    public static LinkApi links() { return LINKS; }

    private ShBetterUiApi() {}

    /** Canonical cross-platform service entry. Available after client kernel startup. */
    public static UIEngine unified() { return UnifiedUI.get(); }

    public static ControlsApi controls() {
        return CONTROLS;
    }

    public static MusicApi music() { return MUSIC; }

    public static MotionApi motion() { return MOTION; }

    public static InteractionApi interactions() { return INTERACTIONS; }

    public static SearchApi search() { return SEARCH; }

    public static SettingsApi settings() { return SETTINGS; }

    public static BlurApi blur() { return BLUR; }

    public static UiClientApi client() { return CLIENT; }

    public static UiScreenBuilder screen(String titleKey) {
        return screen(net.minecraft.network.chat.Component.translatable(titleKey));
    }

    public static UiScreenBuilder screen(net.minecraft.network.chat.Component title) { return CLIENT.screen(title); }

    public static PreviewRegistry developerPreviews() { return Previews.REGISTRY; }

    private static final class Previews {
        private static final PreviewRegistry REGISTRY = create();

        private static PreviewRegistry create() {
            var registry = new PreviewRegistry();
            DeveloperGalleryScreen.registerBuiltins(registry);
            return registry;
        }
    }
}
