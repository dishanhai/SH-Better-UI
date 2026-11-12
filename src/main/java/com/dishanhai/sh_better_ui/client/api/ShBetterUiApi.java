package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.gui.panel.BuiltinPreviews;
import com.dishanhai.sh_better_ui.client.theme.MinecraftThemes;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import net.minecraft.network.chat.Component;

/** 山海：Forge 与 NeoForge 共用的客户端 API 入口。分组可复用，组件状态归各自的宿主。 */
public final class ShBetterUiApi {
    private static final SettingsApi SETTINGS = new SettingsApi();
    private static final ControlsApi CONTROLS = new ControlsApi();
    private static final InteractionApi INTERACTIONS = new InteractionApi();
    private static final LinkApi LINKS = new LinkApi();
    private static final MarkdownApi MARKDOWN = new MarkdownApi();
    private static final SearchApi SEARCH = new SearchApi();
    private static final IngredientApi INGREDIENTS = new IngredientApi();
    private static final MaterialsApi MATERIALS = new MaterialsApi();
    private static final BlurApi BLUR = new BlurApi();
    private static final GraphicsApi GRAPHICS = new GraphicsApi();
    private static final LayoutApi LAYOUT = new LayoutApi();
    private static final MotionApi MOTION = new MotionApi();
    private static final AudioApi AUDIO = new AudioApi();
    private static final LyricApi LYRICS = new LyricApi();
    private static final MusicApi MUSIC = new MusicApi();
    private static final PlatformUiApi PLATFORM = new PlatformUiApi();
    private static final UiExtensionRegistry EXTENSIONS = new UiExtensionRegistry();
    private static final UiClientApi CLIENT = new UiClientApi();

    private ShBetterUiApi() {}

    public static UiClientApi client() {
        return CLIENT;
    }

    public static UiExtensionRegistry extensions() {
        return EXTENSIONS;
    }

    public static PreviewRegistry developerPreviews() {
        return DeveloperPreviewsHolder.REGISTRY;
    }

    /** Shared full-page effect registration, discovery and screen-owned renderers. */
    public static BackgroundRegistry backgrounds() { return BackgroundsHolder.REGISTRY; }

    public static UiApiContext context() {
        return CLIENT.context();
    }

    public static UiScreenBuilder screen(String titleKey) {
        return CLIENT.screen(titleKey);
    }

    public static UiScreenBuilder screen(Component title) {
        return CLIENT.screen(title);
    }

    public static SettingsApi settings() {
        return SETTINGS;
    }

    /** Dynamic themes and resource-pack definitions. Call on the physical client thread. */
    public static ThemeManager themes() { return MinecraftThemes.manager(); }

    public static ControlsApi controls() {
        return CONTROLS;
    }

    public static InteractionApi interactions() {
        return INTERACTIONS;
    }

    public static LinkApi links() {
        return LINKS;
    }

    public static MarkdownApi markdown() {
        return MARKDOWN;
    }

    public static SearchApi search() {
        return SEARCH;
    }

    public static IngredientApi ingredients() {
        return INGREDIENTS;
    }

    public static MaterialsApi materials() {
        return MATERIALS;
    }

    public static BlurApi blur() {
        return BLUR;
    }

    public static GraphicsApi graphics() {
        return GRAPHICS;
    }

    /** 创建页面持有的视觉布局，或使用 Minecraft 渲染和资源适配。 */
    public static LayoutApi layout() {
        return LAYOUT;
    }

    public static MotionApi motion() {
        return MOTION;
    }

    public static AudioApi audio() {
        return AUDIO;
    }

    public static LyricApi lyrics() {
        return LYRICS;
    }

    public static MusicApi music() {
        return MUSIC;
    }

    public static PlatformUiApi platform() {
        return PLATFORM;
    }

    private static final class DeveloperPreviewsHolder {
        private static final PreviewRegistry REGISTRY = createRegistry();

        private static PreviewRegistry createRegistry() {
            var registry = new PreviewRegistry();
            BuiltinPreviews.registerInto(registry);
            return registry;
        }
    }

    private static final class BackgroundsHolder {
        private static final BackgroundRegistry REGISTRY = createRegistry();

        private static BackgroundRegistry createRegistry() {
            var registry = new BackgroundRegistry();
            BuiltinBackgrounds.registerInto(registry);
            return registry;
        }
    }
}
