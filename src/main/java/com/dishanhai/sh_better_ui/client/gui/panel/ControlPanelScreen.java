package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiMath;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.audio.ClientMusicPulse;
import com.dishanhai.sh_better_ui.client.audio.SystemAudioCapture;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.FluidMorph;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiRenderUtil;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.MpTypography;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotBackground;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiFrameHandoff;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCapsuleReveal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScreenReturn;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiNavigationRail;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;
import com.dishanhai.sh_better_ui.client.theme.MinecraftThemes;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScrollableSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiRubberSegment;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.DynamicThemeSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.google.gson.JsonObject;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.config.ConfigPage;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import net.minecraft.client.gui.components.EditBox;
import com.dishanhai.sh_better_ui.config.GlassBackdropSettings;
import com.dishanhai.sh_better_ui.config.ControlFeedbackSettings;
import com.dishanhai.sh_better_ui.config.TabScrollSettings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.IntConsumer;

/** 山海：在同一画面编辑 SHBUI 客户端配置和快捷键。 */
public final class ControlPanelScreen extends Screen implements UiScreenReturn.Target {
    private static final int MAX_WIDTH = 800;
    private static final int PADDING = 14;
    private static final int GAP = 8;
    private static final int BUTTON_HEIGHT = 22;
    private static final int TOOLBAR_TOP = 8;
    private static final int STACKED_SEARCH_TOP = 28;
    private static final int NAVIGATION_GAP = 6;
    private static final int FOOTER_HEIGHT = 46;
    private static final int GLASS_CARD_TOP = 6;
    private static final int GLASS_SIDE_HEIGHT = 116;
    private static final int MAX_CONTENT_HEIGHT = 190;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final float BODY_RADIUS = 5f;
    private static final List<ThemeSettings.Mode> THEMES = List.of(ThemeSettings.Mode.DARK, ThemeSettings.Mode.LIGHT,
            ThemeSettings.Mode.CUSTOM, ThemeSettings.Mode.LIQUID_GLASS, ThemeSettings.Mode.HUD_GLASS);
    private static final List<Section> SECTIONS = List.of(Section.values());
    private static final List<UiSectionBar.Section> TABS = SECTIONS.stream().map(section -> section.tab).toList();
    private static final List<Area> AREAS = List.of(Area.values());
    private static final List<UiSectionBar.Section> MAIN_TABS = AREAS.stream().map(area -> area.tab).toList();

    static {
        GaussBlur.allowScreen(screen -> screen instanceof ControlPanelScreen);
    }

    private final Screen parent;
    private final KeyMapping keyMapping;
    private final Predicate<ClientUiSettings> save;
    private final Consumer<Screen> openAutomation = ShBetterUiApi.platform().controlPanelAutomation().orElse(null);
    private final Map<AbstractWidget, Integer> rows = new LinkedHashMap<>();
    private final List<ToggleOption> options = new ArrayList<>();
    private final List<SliderOption> optionSliders = new ArrayList<>();
    private final List<ModOverviewGroup> overviewGroups = new ArrayList<>();
    private final int[] sectionScroll = new int[SECTIONS.size()];
    private final int[] areaScroll = new int[AREAS.size()];
    private final UiDotBackground background = new UiDotBackground();
    private final UiCapsuleReveal entrance = ShBetterUiApi.motion().capsuleReveal();
    private final UiScreenReturn returning;
    private final UiFrameHandoff pageHandoff = new UiFrameHandoff();
    private final InterruptibleAnimation pageMorph = new InterruptibleAnimation(Spring.FLUID_PANEL, 1f);
    private final InterruptibleAnimation pageOrigin = new InterruptibleAnimation(Spring.INDICATOR);
    private final Draft defaults;
    private Draft saved;
    private Draft draft;
    private Draft beforeReset;
    private UiPreferences preferences;
    private UiOverlayHost overlays;
    private UiScrollableSectionBar sections;
    private UiScrollableSectionBar navigation;
    private UiNavigationRail sidebar;
    private final com.dishanhai.sh_better_ui.client.gui.scaled.UiLayeredMotion layers = ShBetterUiApi.motion().layeredMotion(380, 250);
    private MpThemeBrowser themeBrowser;
    private int retainedThemePage;
    private UiButton musicOpen;
    private UiButton themePrevious, themeNext;
    private EditBox search;
    private UiButton automation;
    private String searchQuery = "";
    private boolean searchDirty;
    private UiButton historyBack, historyForward;
    private int sidebarWidth;
    private boolean classicSearchOpen;
    private boolean layoutRebuild;
    private UiButton layoutChoice;
    private com.dishanhai.sh_better_ui.config.ControlPanelLayout panelLayout(){return draft.settings().controlPanelLayout();}
    private int headerOffset(){return panelLayout().headerOffset(classicSearchOpen||!searchQuery.isBlank());}
    private final ControlCenterNavigation history = new ControlCenterNavigation(
            new ControlCenterNavigation.Location("CONFIGURATION", "GLASS", "", 0));
    private int bodyX() { return left + PADDING + sidebarWidth; }
    private int toolbarWidth() { return panelWidth - 2 * PADDING; }
    private int bodyWidth() { return toolbarWidth() - sidebarWidth; }
    private int automationWidth() { return openAutomation == null ? 0 : toolbarWidth() < 280 ? 24 : 102; }
    private int automationSpace() { return openAutomation == null ? 0 : automationWidth() + GAP; }
    private boolean stacksSearch() { return toolbarWidth() - automationSpace() < 360 && headerOffset() == 0; }
    private int navigationOffset() {
        return (stacksSearch() ? STACKED_SEARCH_TOP : TOOLBAR_TOP) + BUTTON_HEIGHT + NAVIGATION_GAP;
    }
    private int sectionY() {
        return top + navigationOffset() + (sidebarWidth == 0 ? BUTTON_HEIGHT + NAVIGATION_GAP : 0);
    }
    private UiToggleButton enabled;
    private UiButton glassParameters;
    private com.dishanhai.sh_better_ui.client.gui.scaled.UiSettingCard glassCard;
    private UiToggleButton sampling;
    private UiToggleButton contrast;
    private UiToggleButton motion;
    private UiToggleButton musicToggle;
    private SystemAudioCapture.Status displayedAudioStatus;
    private UiToggleButton widgetBlur;
    private UiButton shortcut;
    private UiButton clearShortcut;
    private UiButton back;
    private UiButton developer;
    private UiButton reset;
    private UiButton apply;
    private Component status;
    private boolean restoreDeveloperFocus;
    private boolean entered;
    private boolean capturingShortcut;
    private InputConstants.Key modifierKey;
    private Section selectedSection = Section.GLASS;
    private Section requestedSection;
    private Area selectedArea = Area.CONFIGURATION;
    private Area requestedArea;
    private long nextOverviewRefresh;
    private int scroll;
    private int scrollGrab = -1;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int bodyTop;
    private int bodyBottom;
    private int contentWidth;
    private int contentHeight;
    private int glassWidth;
    private Bounds visiblePage;
    private boolean sidePreview;
    private boolean fits;
    private List<String> themeGroups = List.copyOf(ThemeEffects.groups());
    private final ThemeEffectPreview themeEffectPreview = new ThemeEffectPreview();
    private int themeGroupIndex;
    private UiButton themeGroup;
    private UiButton themeConfigure;
    private UiButton themeCustomize;
    private UiButton themeBackgroundSelect;
    private boolean restoreThemeConfigureFocus;
    private com.dishanhai.sh_better_ui.kernel.api.AppearanceService.ThemeSection returnThemeSection;
    private EditBox dynamicThemeId;
    private UiRubberSegment themeMode;
    private UiButton hudBackground;
    private UiSlider hudBlur;
    private UiTheme previewTheme;
    private int layoutWidth;
    private int layoutHeight;

    public ControlPanelScreen(Screen parent, KeyMapping keyMapping, Predicate<ClientUiSettings> save) {
        super(label("title"));
        this.parent = parent;
        this.keyMapping = keyMapping;
        this.save = save;
        defaults = new Draft(ClientUiSettings.DEFAULT,
                new Shortcut(keyMapping.getDefaultKey(), keyMapping.getDefaultKeyModifier().name()));
        saved = new Draft(ClientUiSettings.current(), new Shortcut(keyMapping.getKey(), keyMapping.getKeyModifier().name()));
        draft = saved;
        updateThemePreview(draft.settings().theme());
        preferences = UiPreferences.fromSettings(draft.settings());
        returning = ShBetterUiApi.motion().screenCrossfade(this, parent, preferences);
    }

    @Override
    protected void init() {
        var previousThemeGroup = themeGroupId();
        themeGroups = List.copyOf(ThemeEffects.groups());
        themeGroupIndex = Math.max(0, themeGroups.indexOf(previousThemeGroup));
        if (sections != null) sections.cancelDrag();
        if (navigation != null) navigation.cancelDrag();
        sections = null;
        navigation = null;
        if (sidebar != null) sidebar.close();
        sidebar = null;
        search = null;
        automation = null;
        historyBack = historyForward = null;
        if (width != layoutWidth || height != layoutHeight) {
            pageMorph.snapTo(1f);
            pageHandoff.clear();
            layoutWidth = width;
            layoutHeight = height;
        }
        back = null;
        clearPage();
        fits = width >= 240 && height >= 200;
        if (!fits) {
            entrance.finish();
            returning.clearHandoff();
            if (width >= 32 && height >= 34) {
                int backWidth = Math.min(width - 8, 96);
                addRenderableWidget(button((width - backWidth) / 2, height - 26, backWidth,
                        Component.translatable("gui.back"), GraphicIcon.BACK, false, this::onClose));
            }
            return;
        }

        panelWidth = Math.min(MAX_WIDTH, width - 12);
        sidebarWidth = panelLayout().sidebarWidth(panelWidth, height);
        sidePreview = bodyWidth() >= 388;
        int navigationHeight = (sidebarWidth == 0 ? 2 : 1) * (BUTTON_HEIGHT + NAVIGATION_GAP);
        panelHeight = Math.min(height - 12, navigationOffset() + navigationHeight + MAX_CONTENT_HEIGHT + FOOTER_HEIGHT);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        if (!entered) {
            entered = true;
            entrance.startAt(width * 0.5f, top + 5f);
        }
        layers.setShown(true, PageTransition.nowMs(), preferences.reducedMotion());
        bodyBottom = top + panelHeight - FOOTER_HEIGHT;
        int x = bodyX();
        int innerWidth = bodyWidth();
        boolean searchVisible = headerOffset() == 0;
        boolean stacked = stacksSearch();
        int toolbarRight = left + panelWidth - PADDING;
        int searchWidth = !searchVisible || stacked ? toolbarWidth()
                : Math.min(320, toolbarWidth() - 180 - automationSpace());
        int searchX = toolbarRight - searchWidth;
        int searchY = top + (stacked ? STACKED_SEARCH_TOP : TOOLBAR_TOP);
        if (openAutomation != null) {
            int right = searchVisible && !stacked ? searchX - GAP : toolbarRight;
            int buttonWidth = automationWidth();
            var caption = MpTypography.text(Component.translatable("sh_better_ui.automation.open"));
            automation = addRenderableWidget(ShBetterUiApi.controls().button(
                    new Bounds(right - buttonWidth, top + TOOLBAR_TOP + 1, buttonWidth, BUTTON_HEIGHT - 4),
                    caption, ignored -> openAutomation.accept(this), preferences, false, UiStyle.LIQUID_GLASS));
            automation.setSampleBackground(false);
            automation.setPreview(caption);
            if (buttonWidth == 24) automation.setIcon(GraphicIcon.CODE);
        }
        historyBack = addRenderableWidget(button(searchX, searchY, 24, label("history.back"), GraphicIcon.BACK,
                false, () -> restoreHistory(-1)));
        historyForward = addRenderableWidget(button(searchX + 28, searchY, 24, label("history.forward"), GraphicIcon.FORWARD,
                false, () -> restoreHistory(1)));
        historyBack.setIcon(GraphicIcon.BACK);
        historyBack.setPreview(label("history.back"));
        historyForward.setIcon(GraphicIcon.FORWARD);
        historyForward.setPreview(label("history.forward"));
        search = addRenderableWidget(new EditBox(font, searchX + 60, searchY, Math.max(1, searchWidth - 60),
                BUTTON_HEIGHT, label("search")));
        search.setMaxLength(128);
        search.setHint(label("search"));
        search.setValue(searchQuery);
        search.setResponder(value -> { searchQuery = value; searchDirty = true; });
        search.visible = search.active = searchVisible;
        historyBack.visible = historyForward.visible = searchVisible;

        int navigationY = top + navigationOffset();
        int mainTabWidth = Math.max(80, (innerWidth - 4 * (AREAS.size() - 1)) / AREAS.size());
        navigation = addRenderableWidget(ShBetterUiApi.controls().scrollableSectionBar(
                new Bounds(x, navigationY, innerWidth, BUTTON_HEIGHT),
                preferences, MAIN_TABS, selectedArea.ordinal(), index -> requestedArea = AREAS.get(index),
                draft.settings().tabs(), mainTabWidth).withJellyRadio());
        navigation.visible = navigation.active = sidebarWidth == 0;
        if (sidebarWidth > 0) {
            sidebar = addRenderableWidget(ShBetterUiApi.controls().navigationRail(
                    font, new Bounds(left + PADDING, navigationY, sidebarWidth - 2 * GAP,
                    Math.min(bodyBottom - navigationY, AREAS.size() * 28)),
                    label("navigation"), AREAS.stream().map(area -> new UiNavigationRail.Item(area.tab.label(), area.tab.icon())).toList(),
                    selectedArea.ordinal(), UiNavigationRail.Orientation.VERTICAL,
                    preferences, index -> requestedArea = AREAS.get(index)));
        }
        sections = addRenderableWidget(ShBetterUiApi.controls().scrollableSectionBar(
                new Bounds(x, sectionY(), innerWidth, BUTTON_HEIGHT),
                preferences, TABS, selectedSection.ordinal(), index -> requestedSection = SECTIONS.get(index),
                draft.settings().tabs(), UiScrollableSectionBar.DEFAULT_TAB_WIDTH).withJellyRadio());
        addFooter(left + PADDING, toolbarWidth());
        buildPage();
        if (restoreThemeConfigureFocus && (themeConfigure != null || themeCustomize != null) && minecraft != null && minecraft.screen == this) {
            setFocused(returnThemeSection == null ? themeConfigure : switch (returnThemeSection) {
                case COLORS -> themeCustomize;
                case BACKGROUNDS -> themeBackgroundSelect;
                case EFFECTS -> themeGroup;
            });
            restoreThemeConfigureFocus = false;
            returnThemeSection = null;
        }
        if (restoreDeveloperFocus && developer != null && minecraft != null && minecraft.screen == this) {
            setFocused(developer);
            restoreDeveloperFocus = false;
        }
    }

    private void clearPage() {
        if (themeMode != null) themeMode.cancelDrag();
        rows.keySet().forEach(this::removeWidget);
        rows.clear();
        options.clear();
        optionSliders.clear();
        overviewGroups.clear();
        developer = null;
        enabled = null;
        glassParameters = null;
        if (glassCard != null) glassCard.close();
        glassCard = null;
        sampling = null;
        layoutChoice = null;
        contrast = null;
        motion = null;
        musicToggle = null;
        themeMode = null;
        themeGroup = null;
        themeConfigure = null;
        themeCustomize = null;
        themeBackgroundSelect = null;
        hudBackground = null;
        hudBlur = null;
        dynamicThemeId = null;
        displayedAudioStatus = null;
        widgetBlur = null;
        shortcut = null;
        clearShortcut = null;
        capturingShortcut = false;
        modifierKey = null;
        scrollGrab = -1;
        visiblePage = null;
    }

    private void buildPage() {
        int x = bodyX();
        boolean configuration = selectedArea == Area.CONFIGURATION;
        int retainedScroll = configuration ? sectionScroll[selectedSection.ordinal()] : areaScroll[selectedArea.ordinal()];
        sections.visible = sections.active = configuration && searchQuery.isBlank();
        bodyTop = sectionY() + (sections.visible ? BUTTON_HEIGHT + NAVIGATION_GAP : 0);
        contentHeight = switch (selectedArea) {
            case CONFIGURATION -> switch (selectedSection) {
                case GLASS -> 0;
                case DISPLAY -> 250;
                case FEEDBACK, TABS, DYNAMIC_THEME -> 120;
                case SHORTCUT -> 42;
            };
            case DEVELOPER -> 120;
            case THEMES -> 230;
            case MUSIC -> 114;
            case MUONIUM -> 132;
            case OVERVIEW -> 0;
            case LINKS -> 16 + (FeedbackLink.values().length + 1) * (BUTTON_HEIGHT + GAP);
        };
        // Native-page launch rows compute their wrapped footer height after layout.
        // Reserve their scroll gutter up front so later overflow never covers a button.
        contentWidth = bodyWidth()
                - (selectedArea != Area.OVERVIEW && selectedArea != Area.MUONIUM
                        && maxScroll() == 0 ? 0 : SCROLLBAR_WIDTH + GAP);
        glassWidth = sidePreview ? contentWidth * 5 / 8 : contentWidth;
        if (!searchQuery.isBlank()) {
            var matches = searchEntries();
            contentHeight = 24 + matches.size() * 30;
            contentWidth = bodyWidth() - (maxScroll() > 0 ? SCROLLBAR_WIDTH + GAP : 0);
            for (int i = 0; i < matches.size(); i++) {
                var result = matches.get(i);
                row(button(x, 24 + i * 30, contentWidth, Component.literal(result.title()), GraphicIcon.NEXT, false,
                        () -> navigateTo(Area.valueOf(result.area()), Section.valueOf(result.section()))));
            }
        } else if (selectedArea == Area.THEMES) addThemeBrowser(x);
        else if (selectedArea == Area.MUONIUM) {
            row(button(x, 28, contentWidth, Component.translatable("sh_better_ui.favorites.title"), GraphicIcon.NEXT, false,
                    () -> NativeFavoritesScreen.open(this)));
            row(button(x, 60, contentWidth, Component.translatable("sh_better_ui.native_mods.title"), GraphicIcon.NEXT, false,
                    () -> minecraft.setScreen(new NativeModsScreen(this))));
            row(button(x, 92, contentWidth, Component.translatable("sh_better_ui.native_themes.title"), GraphicIcon.NEXT, false,
                    () -> minecraft.setScreen(new NativeThemesScreen(this, draft.settings(), this::stageNativeSettings, true))));
            row(button(x, 124, contentWidth, Component.translatable("sh_better_ui.native_preferences.title"), GraphicIcon.NEXT, false,
                    () -> minecraft.setScreen(new NativePreferencesScreen(this, draft.settings(),
                            changes -> stageNativeSettings(changes.apply(draft.settings())), true))));
            row(button(x, 156, contentWidth, label("muonium.player"), GraphicIcon.MUSIC, false,
                    com.muoniumplayer.core.screens.onboarding.OnboardingManager::openPlayerEntry));
            var open = row(button(x, 188, contentWidth, label("muonium.open"), GraphicIcon.NEXT, false,
                    () -> com.muoniumplayer.MuoniumPlayerClient.getInstance().openHudEditor(minecraft)));
            open.setPreview(label("muonium.description").copy().append(" ").append(label("muonium.independent")));
            playerAppearanceOption(x, 220, "host_theme",
                    com.muoniumplayer.core.settings.PlayerAppearance::useShbuiTheme,
                    com.muoniumplayer.core.settings.PlayerAppearance::setShbuiTheme);
            playerAppearanceOption(x, 252, "host_controls",
                    com.muoniumplayer.core.settings.PlayerAppearance::useShbuiControls,
                    com.muoniumplayer.core.settings.PlayerAppearance::setShbuiControls);
            contentHeight = 294 + font.split(label("muonium.independent"), contentWidth).size() * font.lineHeight;
        } else if (selectedArea == Area.MUSIC) {
            musicOpen = row(button(x, 28, contentWidth, label("music.open"), GraphicIcon.NEXT, false,
                    () -> minecraft.setScreen(ShBetterUiApi.settings().onlineMusicScreen(this, () -> musicOpen))));
            musicOpen.active = ShBetterUiApi.settings().onlineMusicAvailable();
            var overlay = row(button(x, 60, contentWidth, Component.translatable("sh_better_ui.music_overlay.title"), GraphicIcon.NEXT, false,
                    () -> com.dishanhai.sh_better_ui.kernel.internal.NativeMusicOverlay.open(this)));
            overlay.active = com.dishanhai.sh_better_ui.kernel.internal.NativeMusicOverlay.available();
        } else if (selectedArea == Area.DEVELOPER) addDeveloperControls(x);
        else if (selectedArea == Area.OVERVIEW) refreshOverview(PageTransition.nowMs());
        else if (selectedArea == Area.LINKS) addFeedbackLinks(x);
        else switch (selectedSection) {
            case GLASS -> addGlassControls(x);
            case DISPLAY -> addDisplayControls(x);
            case DYNAMIC_THEME -> addDynamicThemeControls(x);
            case FEEDBACK -> addFeedbackControls(x);
            case TABS -> addTabControls(x);
            case SHORTCUT -> addShortcutControls(x);
        }
        scrollTo(searchQuery.isBlank() ? retainedScroll : 0);
        refreshActions();
    }

    private boolean stageNativeSettings(ClientUiSettings value) {
        edit(new Draft(value, draft.shortcut()));
        status = Component.translatable("sh_better_ui.native_pages.staged");
        return true;
    }

    private void addThemeBrowser(int x) {
        if (themeBrowser == null) {
            themeBrowser = new MpThemeBrowser(MinecraftThemes.manager(),
                    this::effectiveBrowserTheme, theme -> edit(new Draft(draft.settings().withTheme(theme), draft.shortcut())),
                    MinecraftThemes::isConfiguredPalette);
            themeBrowser.page(retainedThemePage);
        }
        themeBrowser.layout(new Bounds(x, 24, contentWidth, 184), preferences).forEach(this::row);
        themePrevious = row(button(x, 216, 28, label("history.back"), GraphicIcon.BACK, false, () -> pageThemes(-1)));
        themePrevious.setIcon(GraphicIcon.BACK);
        themePrevious.setPreview(label("history.back"));
        themePrevious.active = themeBrowser.pageIndex() > 0;
        themeNext = row(button(x + 32, 216, 28, label("history.forward"), GraphicIcon.FORWARD, false, () -> pageThemes(1)));
        themeNext.setIcon(GraphicIcon.FORWARD);
        themeNext.setPreview(label("history.forward"));
        themeNext.active = themeBrowser.pageIndex() + 1 < themeBrowser.pageCount();
        themeCustomize = row(button(x + 68, 216, contentWidth - 68, label("theme.customize"), GraphicIcon.SETTINGS, false,
                () -> customizeTheme(com.dishanhai.sh_better_ui.kernel.api.AppearanceService.ThemeSection.COLORS)));
        contentHeight = 242;
    }

    static ThemeSettings effectiveBrowserTheme(ClientUiSettings settings, Predicate<String> selectable) {
        var theme = settings.theme();
        String dynamic = settings.dynamicTheme().fixedTheme();
        if (theme.mode().fixedThemeId() == null
                && (theme.mode() != ThemeSettings.Mode.CUSTOM || theme.registeredThemeId().isEmpty())
                && !dynamic.isEmpty() && selectable.test(dynamic)) return theme.withRegisteredTheme(dynamic);
        return theme;
    }

    private ThemeSettings effectiveBrowserTheme() {
        try {
            return effectiveBrowserTheme(draft.settings(), id -> !MinecraftThemes.isConfiguredPalette(id)
                    && MinecraftThemes.manager().registry().find(id).isPresent());
        } catch (IllegalArgumentException fullArchive) {
            status = Component.translatable("sh_better_ui.custom_theme.palette_capacity");
            return draft.settings().theme();
        }
    }

    private void pageThemes(int direction) {
        if (themeBrowser.page(direction)) remountThemePage(direction);
    }

    private void remountThemePage(int direction) {
        var previousFocus = getFocused();
        String id = previousFocus instanceof UiThemePreviewCard card ? card.themeId() : null;
        boolean previousButton = previousFocus == themePrevious;
        boolean nextButton = previousFocus == themeNext;
        boolean customize = previousFocus == themeCustomize;
        boolean owned = rows.containsKey(previousFocus);
        clearPage();
        buildPage();
        if (!owned) return; // Search/sidebar/footer focus survives a registry refresh untouched.
        GuiEventListener target = null;
        if (id != null) target = rows.keySet().stream().filter(UiThemePreviewCard.class::isInstance)
                .map(UiThemePreviewCard.class::cast).filter(card -> card.themeId().equals(id)).findFirst().orElse(null);
        if (customize) target = themeCustomize;
        else if ((previousButton || direction < 0) && themePrevious.active) target = themePrevious;
        else if ((nextButton || direction > 0) && themeNext.active) target = themeNext;
        if (target == null) target = rows.keySet().stream().filter(UiThemePreviewCard.class::isInstance)
                .filter(widget -> widget.active && widget.visible).findFirst().orElse(themePrevious);
        setFocused(target);
    }

    private void addGlassControls(int x) {
        enabled = toggle(x, 16, glassWidth, "enabled", value -> label("enabled", onOff(value)),
                value -> editBackdrop(draft.settings().backdrop().withEnabled(value)));
        enabled.setIcons(GraphicIcon.ENABLE, GraphicIcon.DISABLE);
        // Replace the separately mounted parameter row with a real settings card and an independent arrow.
        rows.remove(enabled.button());
        removeWidget(enabled.button());
        glassCard = ShBetterUiApi.controls().settingCard(font, new Bounds(x, GLASS_CARD_TOP, glassWidth, 0),
                MpTypography.bold(label("section.glass")), label("glass_parameters.tooltip"), enabled.button(),
                label("glass_parameters"), preferences, this::openGlassParameters);
        contentHeight = glassContentHeight();
        // Wrapping can make this page scroll even at the same GUI height. Keep its cards clear of the thumb.
        if (maxScroll() > 0) {
            contentWidth -= SCROLLBAR_WIDTH + GAP;
            glassWidth = sidePreview ? contentWidth * 5 / 8 : contentWidth;
            glassCard.setBounds(new Bounds(x, GLASS_CARD_TOP, glassWidth, 0));
            contentHeight = glassContentHeight();
        }
        glassCard.widgets().forEach(this::row);
        glassParameters = glassCard.detailsButton();
        glassParameters.setMessage(label("glass_parameters"));
    }

    private int glassStackPreviewTop() { return GLASS_CARD_TOP + glassCard.height() + 23; }
    private int glassContentHeight() {
        return sidePreview ? Math.max(GLASS_SIDE_HEIGHT, GLASS_CARD_TOP + glassCard.height() + GAP)
                : glassStackPreviewTop() + 28 + GAP;
    }

    private void openGlassParameters() {
        var fields = Arrays.stream(Scale.values()).map(Scale::option).toList();
        var page = new ConfigPage<>(label("glass_parameters"), label("glass_parameters.tooltip"), fields, List.of());
        minecraft.setScreen(page.screen(this, draft.settings().backdrop(), value -> {
            // The parent widgets are rebuilt when the child returns.
            draft = new Draft(draft.settings().withBackdrop(value), draft.shortcut());
            beforeReset = null;
            status = null;
            return true;
        }, () -> glassParameters));
    }

    private void addDisplayControls(int x) {
        int toggleWidth = (contentWidth - GAP) / 2;
        contrast = toggle(x, 16, toggleWidth, "contrast", value -> label("contrast", onOff(value)), value -> {
            var settings = draft.settings();
            editDisplay(value, settings.reducedMotion(), settings.widgetBackdropBlur());
        });
        motion = toggle(x + toggleWidth + GAP, 16, contentWidth - toggleWidth - GAP, "motion",
                value -> label("motion", onOff(value)), value -> {
                    var settings = draft.settings();
                    editDisplay(settings.highContrast(), value, settings.widgetBackdropBlur());
                });
        musicToggle = toggle(x, 42, contentWidth, "background_music",
                value -> label("background_music", onOff(value)), this::setBackgroundMusic);
        musicToggle.button().setLeadingIcon(GraphicIcon.MUSIC);
        var modes = THEMES.stream().map(mode -> new UiSectionBar.Section(themeLabel(mode), UiTheme.icon(mode))).toList();
        themeMode = row(ShBetterUiApi.controls().rubberSegment(new Bounds(x, 68, contentWidth, BUTTON_HEIGHT),
                label("theme.mode"), modes, THEMES.indexOf(draft.settings().theme().mode()), preferences, index -> {
                    var settings = draft.settings();
                    edit(new Draft(settings.withTheme(settings.theme().withMode(THEMES.get(index))), draft.shortcut()));
                }));
        addThemeEditor(x);
        hudBackground = row(button(x, 94, (contentWidth - GAP) / 2, hudBackgroundLabel(), GraphicIcon.CUSTOM_THEME,
                false, this::nextHudBackground));
        hudBackground.setPreview(label("theme.hud_background.tooltip"));
        hudBlur = optionSlider(x, 120, "theme.hud_blur", 0, ThemeSettings.MAX_HUD_BLUR_RADIUS, 1,
                () -> draft.settings().theme().hudBlurRadius(), value -> {
                    var settings = draft.settings();
                    edit(new Draft(settings.withTheme(settings.theme().withHudBlurRadius(value)), draft.shortcut()));
                });
        layoutChoice=row(button(x,146,contentWidth,panelLayoutLabel(),GraphicIcon.SETTINGS,false,this::switchPanelLayout));
        layoutChoice.setPreview(label("layout.tooltip"));
    }

    private void switchPanelLayout() {
        var next=panelLayout()==com.dishanhai.sh_better_ui.config.ControlPanelLayout.CLASSIC
                ?com.dishanhai.sh_better_ui.config.ControlPanelLayout.SIDEBAR
                :com.dishanhai.sh_better_ui.config.ControlPanelLayout.CLASSIC;
        edit(new Draft(draft.settings().withControlPanelLayout(next),draft.shortcut()));
        layoutRebuild=true;
    }
    private Component panelLayoutLabel(){return label("layout",label("layout."+panelLayout().name().toLowerCase(java.util.Locale.ROOT)));}

    private Component hudBackgroundLabel() {
        var effect = draft.settings().theme().hudBackgroundEffect();
        var name = effect.isEmpty() ? label("theme.hud_background.off")
                : ShBetterUiApi.backgrounds().find(effect).<Component>map(spec -> Component.translatable(spec.titleKey()))
                        .orElseGet(() -> effect.equals(ThemeSettings.DEFAULT_BACKGROUND_EFFECT)
                                ? label("theme.group.dotMatrix") : label("theme.hud_background.unavailable", effect));
        return label("theme.hud_background", name);
    }

    private void nextHudBackground() {
        var choices = new ArrayList<String>();
        choices.add("");
        choices.add(ThemeSettings.DEFAULT_BACKGROUND_EFFECT);
        for (var spec : ShBetterUiApi.backgrounds().entries()) {
            if (!spec.id().equals(ThemeSettings.DEFAULT_BACKGROUND_EFFECT)) choices.add(spec.id());
        }
        var settings = draft.settings();
        var theme = settings.theme();
        int next = (choices.indexOf(theme.hudBackgroundEffect()) + 1) % choices.size();
        edit(new Draft(settings.withTheme(theme.withHudBackgroundEffect(choices.get(next))), draft.shortcut()));
    }

    private void addThemeEditor(int x) {
        int half = (contentWidth - GAP) / 2;
        themeGroup = row(button(x, 94, half, themeGroupName(), GraphicIcon.CUSTOM_THEME, false,
                () -> customizeTheme(com.dishanhai.sh_better_ui.kernel.api.AppearanceService.ThemeSection.EFFECTS)));
        themeGroup.setPreview(label("theme.group.tooltip"));
        themeConfigure = row(button(x + half + GAP, 94, contentWidth - half - GAP,
                themeConfigureLabel(), GraphicIcon.CUSTOM_THEME, false, this::configureThemeGroup));
        themeCustomize = row(button(x, 120, half, label("theme.customize"), GraphicIcon.CUSTOM_THEME, false,
                () -> customizeTheme(com.dishanhai.sh_better_ui.kernel.api.AppearanceService.ThemeSection.COLORS)));
        themeBackgroundSelect = row(button(x + half + GAP, 120, contentWidth - half - GAP,
                label("theme.background.select"), GraphicIcon.CUSTOM_THEME, false,
                () -> customizeTheme(com.dishanhai.sh_better_ui.kernel.api.AppearanceService.ThemeSection.BACKGROUNDS)));
        syncThemeEditor();
    }

    private void customizeTheme(com.dishanhai.sh_better_ui.kernel.api.AppearanceService.ThemeSection section) {
        restoreThemeConfigureFocus = true;
        returnThemeSection = section;
        minecraft.setScreen(UnifiedUI.get().require(UIApis.APPEARANCE).editTheme(this, draft.settings(), section, theme -> {
            var spec = ShBetterUiApi.backgrounds().find(theme.backgroundEffect()).orElse(null);
            if (spec != null && themeGroups.contains(spec.settingsGroup())) themeGroupIndex = themeGroups.indexOf(spec.settingsGroup());
            edit(new Draft(draft.settings().withTheme(theme), draft.shortcut()));
            status = label("theme.staged");
        }));
    }

    private void addDynamicThemeControls(int x) {
        dynamicThemeId = row(new EditBox(font, x, 16, contentWidth, BUTTON_HEIGHT, label("dynamic_theme.id")));
        dynamicThemeId.setMaxLength(256);
        dynamicThemeId.setHint(label("dynamic_theme.follow_hint"));
        dynamicThemeId.setValue(draft.settings().dynamicTheme().fixedTheme());
        dynamicThemeId.setTooltip(Tooltip.create(label("dynamic_theme.id.tooltip")));
        int half = (contentWidth - GAP) / 2;
        row(button(x, 42, half, label("dynamic_theme.stage"), GraphicIcon.SUCCESS, false,
                () -> stageDynamicTheme(dynamicThemeId.getValue().strip()))).setPreview(label("dynamic_theme.stage.tooltip"));
        row(button(x + half + GAP, 42, contentWidth - half - GAP, label("dynamic_theme.next"), GraphicIcon.CUSTOM_THEME,
                false, this::nextDynamicTheme)).setPreview(label("dynamic_theme.next.tooltip"));
        optionSlider(x, 68, "dynamic_theme.speed", 0, DynamicThemeSettings.MAX_ANIMATION_PERCENT, 1,
                () -> draft.settings().dynamicTheme().animationPercent(), value -> {
                    var current = draft.settings().dynamicTheme();
                    editDynamicTheme(new DynamicThemeSettings(current.fixedTheme(), value, current.lowPerformance()));
                });
        option(x, 94, "dynamic_theme.low_performance", () -> draft.settings().dynamicTheme().lowPerformance(), value -> {
            var current = draft.settings().dynamicTheme();
            editDynamicTheme(new DynamicThemeSettings(current.fixedTheme(), current.animationPercent(), value));
        });
    }

    private void nextDynamicTheme() {
        var choices = dynamicThemeChoices(ShBetterUiApi.themes().registry().themes().keySet(),
                MinecraftThemes::isConfiguredPalette);
        int next = choices.indexOf(draft.settings().dynamicTheme().fixedTheme()) + 1;
        stageDynamicTheme(next < choices.size() ? choices.get(next) : "");
    }

    static List<String> dynamicThemeChoices(java.util.Collection<String> ids, Predicate<String> internal) {
        return ids.stream().filter(id -> !internal.test(id)).sorted().toList();
    }

    static boolean canStageDynamicTheme(String id, Predicate<String> internal) {
        return DynamicThemeSettings.validFixedTheme(id) && (id.isEmpty() || !internal.test(id));
    }

    private void stageDynamicTheme(String id) {
        if (!canStageDynamicTheme(id, MinecraftThemes::isConfiguredPalette)) {
            status = label("dynamic_theme.invalid");
            return;
        }
        var current = draft.settings().dynamicTheme();
        editDynamicTheme(new DynamicThemeSettings(id, current.animationPercent(), current.lowPerformance()));
        dynamicThemeId.setValue(id);
        status = id.isEmpty() ? label("dynamic_theme.follow")
                : label(ShBetterUiApi.themes().registry().find(id).isPresent()
                        ? "dynamic_theme.staged" : "dynamic_theme.unavailable", id);
    }

    private void editDynamicTheme(DynamicThemeSettings settings) {
        edit(new Draft(draft.settings().withDynamicTheme(settings), draft.shortcut()));
    }

    private String themeGroupId() { return themeGroups.get(themeGroupIndex); }

    private Component themeGroupName() {
        var background = ThemeEffectPreview.background(themeGroupId());
        return background == null ? label("theme.group." + themeGroupId()) : Component.translatable(background.titleKey());
    }

    private void syncThemeEditor() {
        var background = ThemeEffectPreview.background(themeGroupId());
        themeGroup.setMessage(themeGroupName());
        var theme = draft.settings().theme();
        String registered = theme.mode() == ThemeSettings.Mode.CUSTOM ? theme.registeredThemeId() : "";
        var selectedName = registered.isEmpty() ? null : ShBetterUiApi.themes().registry().find(registered)
                .map(value -> Component.translatable(value.nameKey())).orElseGet(() -> Component.literal(registered));
        themeCustomize.setMessage(selectedName == null ? label("theme.customize")
                : label("theme.customize").copy().append(" · ").append(selectedName));
        themeCustomize.setPreview(selectedName == null ? label("theme.customize")
                : selectedName.copy().append("\n").append(Component.literal(registered)));
        boolean hud = theme.mode() == ThemeSettings.Mode.HUD_GLASS;
        themeConfigure.setMessage(hud ? label("theme.hud_background.configure") : themeConfigureLabel());
        themeConfigure.setPreview(hud || background != null ? label("theme.background.tooltip") : null);
        themeConfigure.active = hud ? ThemeEffects.hudBackgroundGroup(theme) != null
                : theme.mode() == ThemeSettings.Mode.CUSTOM;
    }

    private Component themeConfigureLabel() {
        return Component.translatable("sh_better_ui.theme_editor.title", themeGroupName());
    }

    private void configureThemeGroup() {
        var settings = draft.settings();
        boolean hud = settings.theme().mode() == ThemeSettings.Mode.HUD_GLASS;
        String group = hud ? ThemeEffects.hudBackgroundGroup(settings.theme()) : themeGroupId();
        if (group == null) return;
        var background = hud ? null : ThemeEffectPreview.background(group);
        var form = UnifiedUI.get().require(UIApis.APPEARANCE).edit(this, settings, group, new JsonObject(),
                settings.theme().effects().group(group), values -> {
                    var theme = draft.settings().theme();
                    var next = theme.withEffects(theme.effects().withGroup(group, values));
                    if (background != null) next = next.withBackgroundEffect(background.id());
                    if (!next.equals(theme)) {
                        edit(new Draft(draft.settings().withTheme(next), draft.shortcut()));
                        status = label("theme.staged");
                    }
                });
        restoreThemeConfigureFocus = true;
        minecraft.setScreen(form);
    }

    private static Component themeLabel(ThemeSettings.Mode mode) {
        return label(switch (mode) {
            case DARK -> "theme.mode.dark";
            case LIGHT -> "theme.mode.light";
            case CUSTOM -> "theme.mode.custom";
            case LIQUID_GLASS -> "theme.mode.liquid_glass";
            case HUD_GLASS -> "theme.mode.hud_glass";
        });
    }

    private boolean musicMotionAllowed() {
        return !preferences.reducedMotion() && !preferences.highContrast();
    }

    private void setBackgroundMusic(boolean enabled) {
        if (!enabled || musicMotionAllowed()) ClientMusicPulse.global().setEnabled(enabled);
        refreshMusicToggle();
    }

    private void refreshMusicToggle() {
        if (musicToggle == null) return;
        var pulse = ClientMusicPulse.global();
        boolean allowed = musicMotionAllowed();
        boolean enabled = pulse.enabled();
        boolean active = allowed || enabled;
        var audioStatus = allowed ? pulse.status() : null;
        if (audioStatus == displayedAudioStatus && musicToggle.button().active == active
                && musicToggle.value() == enabled) return;
        displayedAudioStatus = audioStatus;
        musicToggle.button().active = active;
        musicToggle.setValue(enabled);
        musicToggle.button().setPreview(allowed ? Component.translatable(audioStatus.translationKey())
                .append(CommonComponents.NEW_LINE).append(label("background_music.tooltip"))
                : label("background_music.suspended"));
    }

    private void addDeveloperControls(int x) {
        sampling = toggle(x, 16, contentWidth, "sampling",
                value -> label("sampling", label(value ? "sampling.dense" : "sampling.standard")),
                value -> editBackdrop(draft.settings().backdrop().withDenseSampling(value)));
        widgetBlur = toggle(x, 42, contentWidth, "widget_blur",
                value -> label("widget_blur", onOff(value)), value -> {
                    var settings = draft.settings();
                    editDisplay(settings.highContrast(), settings.reducedMotion(), value);
                });
        developer = row(button(x, 94, contentWidth, label("developer.title"), GraphicIcon.CODE, false, () -> {
            restoreDeveloperFocus = true;
            minecraft.setScreen(new DeveloperPanelScreen(this, preferences, () -> developer));
        }));
        developer.setPreview(label("developer_config.tools.tooltip"));
    }

    private void addFeedbackLinks(int x) {
        row(button(x, 16, contentWidth, NativeAboutScreen.text("title"), GraphicIcon.INFO, false,
                () -> minecraft.setScreen(new NativeAboutScreen(this))));
        int y = 16 + BUTTON_HEIGHT + GAP;
        for (var link : FeedbackLink.values()) {
            var message = label(link.key);
            var entry = row(button(x, y, contentWidth, message, link.icon, false, () -> {
                if (!ShBetterUiApi.links().open(this, link.url, () -> rows.keySet().stream()
                        .filter(widget -> widget.getMessage().equals(message)).findFirst().orElse(null))) {
                    ShBetterUiApi.interactions().toast(UiToast.Kind.ERROR,
                            label("links.unavailable"), label("links.unavailable.details"));
                }
            }));
            entry.setPreview(label(link.key + ".tooltip"));
            y += BUTTON_HEIGHT + GAP;
        }
    }

    private void refreshOverview(long now) {
        var groups = ModOverviewData.capture(minecraft);
        int y = 4;
        for (int index = 0; index < groups.size(); index++) {
            ModOverviewGroup widget;
            if (index < overviewGroups.size()) {
                widget = overviewGroups.get(index);
                widget.update(groups.get(index));
            } else {
                widget = new ModOverviewGroup(bodyX(), y, contentWidth, groups.get(index), font, preferences);
                overviewGroups.add(row(widget));
            }
            rows.put(widget, y);
            y += widget.getHeight() + GAP;
        }
        contentHeight = y;
        nextOverviewRefresh = now + 1_000L;
        scrollTo(scroll);
    }

    private void addShortcutControls(int x) {
        int clearWidth = 52;
        shortcut = row(button(x, 16, contentWidth - clearWidth - GAP, CommonComponents.EMPTY,
                null, false, () -> {
                    capturingShortcut = true;
                    refreshActions();
                }));
        shortcut.setPreview(label("shortcut.tooltip"));
        clearShortcut = row(button(x + contentWidth - clearWidth, 16, clearWidth,
                label("shortcut.clear"), null, false, () -> finishShortcut(new Shortcut(InputConstants.UNKNOWN, "NONE"))));
        clearShortcut.setPreview(label("shortcut.clear.tooltip"));
    }

    private void addFeedbackControls(int x) {
        option(x, 16, "hover_animation", () -> draft.settings().feedback().hoverAnimation(), value -> {
            var current = draft.settings().feedback();
            editFeedback(new ControlFeedbackSettings(value, current.pressAnimation(), current.selectionPulse(),
                    current.tooltipDelayMs()));
        });
        option(x, 42, "press_animation", () -> draft.settings().feedback().pressAnimation(), value -> {
            var current = draft.settings().feedback();
            editFeedback(new ControlFeedbackSettings(current.hoverAnimation(), value, current.selectionPulse(),
                    current.tooltipDelayMs()));
        });
        option(x, 68, "selection_pulse", () -> draft.settings().feedback().selectionPulse(), value -> {
            var current = draft.settings().feedback();
            editFeedback(new ControlFeedbackSettings(current.hoverAnimation(), current.pressAnimation(), value,
                    current.tooltipDelayMs()));
        });
        optionSlider(x, 94, "tooltip_delay", 0, ControlFeedbackSettings.MAX_TOOLTIP_DELAY_MS, 50,
                () -> draft.settings().feedback().tooltipDelayMs(), value -> {
                    var current = draft.settings().feedback();
                    editFeedback(new ControlFeedbackSettings(current.hoverAnimation(), current.pressAnimation(),
                            current.selectionPulse(), value));
                });
    }

    private void addTabControls(int x) {
        option(x, 16, "tab_wheel", () -> draft.settings().tabs().wheelEnabled(), value -> {
            var current = draft.settings().tabs();
            editTabs(new TabScrollSettings(value, current.dragEnabled(), current.autoReveal(), current.scrollStep()));
        });
        option(x, 42, "tab_drag", () -> draft.settings().tabs().dragEnabled(), value -> {
            var current = draft.settings().tabs();
            editTabs(new TabScrollSettings(current.wheelEnabled(), value, current.autoReveal(), current.scrollStep()));
        });
        option(x, 68, "tab_auto_reveal", () -> draft.settings().tabs().autoReveal(), value -> {
            var current = draft.settings().tabs();
            editTabs(new TabScrollSettings(current.wheelEnabled(), current.dragEnabled(), value, current.scrollStep()));
        });
        optionSlider(x, 94, "tab_scroll_step", TabScrollSettings.MIN_SCROLL_STEP, TabScrollSettings.MAX_SCROLL_STEP, 1,
                () -> draft.settings().tabs().scrollStep(), value -> {
                    var current = draft.settings().tabs();
                    editTabs(new TabScrollSettings(current.wheelEnabled(), current.dragEnabled(), current.autoReveal(), value));
                });
    }

    private void option(int x, int y, String key, BooleanSupplier read, Consumer<Boolean> write) {
        var control = toggle(x, y, contentWidth, key, value -> label(key, onOff(value)), write);
        options.add(new ToggleOption(control, read));
    }

    private void playerAppearanceOption(int x, int y, String key, BooleanSupplier read, Consumer<Boolean> write) {
        String translation = "sh_better_ui.muonium.center." + key;
        var control = toggle(x, y, contentWidth, key,
                value -> Component.translatable(translation).append(": ").append(onOff(value)), value -> {
                    write.accept(value);
                    refreshActions();
                });
        control.button().setPreview(Component.translatable(translation + ".desc"));
        control.setValue(read.getAsBoolean());
        options.add(new ToggleOption(control, read));
    }

    private UiSlider optionSlider(int x, int y, String key, int min, int max, int step, IntSupplier read, IntConsumer write) {
        var slider = new UiSlider(new Bounds(x, y, contentWidth, BUTTON_HEIGHT), min, max, step, read.getAsInt(),
                preferences, value -> label(key, value), write);
        slider.setTooltip(Tooltip.create(label(key + ".tooltip")));
        optionSliders.add(new SliderOption(row(slider), read));
        return slider;
    }

    private void addFooter(int x, int width) {
        int y = bodyBottom + 16;
        int available = (width - 2 * GAP) / 3;
        int backWidth = Math.min(88, available);
        int resetWidth = Math.min(128, available);
        int applyWidth = Math.min(108, available);
        int right = x + width;
        back = addRenderableWidget(button(x, y, backWidth, Component.translatable("gui.back"), GraphicIcon.BACK,
                false, this::onClose));
        reset = addRenderableWidget(button(right - applyWidth - GAP - resetWidth, y, resetWidth, label("reset"), GraphicIcon.RESET,
                false, this::resetDraft));
        apply = addRenderableWidget(button(right - applyWidth, y, applyWidth,
                label("apply"), GraphicIcon.SUCCESS, true, this::apply));
        back.setPreview(CommonComponents.GUI_BACK);
        apply.setPreview(label("apply.tooltip"));
        back.setTabOrderGroup(2);
        reset.setTabOrderGroup(2);
        apply.setTabOrderGroup(2);
    }

    private UiButton button(int x, int y, int width, Component text, GraphicIcon icon, boolean primary, Runnable action) {
        var widget = new UiButton(x, y, width, BUTTON_HEIGHT, text, button -> action.run(),
                preferences, primary, UiStyle.LIQUID_GLASS);
        widget.setSampleBackground(false);
        widget.setLeadingIcon(icon);
        return widget;
    }

    private <T extends AbstractWidget> T row(T widget) {
        widget.setTabOrderGroup(1);
        rows.put(widget, widget.getY());
        return addWidget(widget);
    }

    private UiToggleButton toggle(int x, int y, int width, String key, Function<Boolean, Component> text,
                                  Consumer<Boolean> onChange) {
        var toggle = new UiToggleButton(new Bounds(x, y, width, BUTTON_HEIGHT), false, preferences,
                UiStyle.LIQUID_GLASS, text, onChange);
        toggle.setModeIndicatorsVisible(true);
        toggle.button().setSampleBackground(false);
        toggle.button().setPreview(label(key + ".tooltip"));
        row(toggle.button());
        return toggle;
    }

    private void editBackdrop(GlassBackdropSettings backdrop) {
        edit(new Draft(draft.settings().withBackdrop(backdrop), draft.shortcut()));
    }

    private void editDisplay(boolean highContrast, boolean reducedMotion, boolean widgetBackdropBlur) {
        edit(new Draft(draft.settings().withDisplay(highContrast, reducedMotion, widgetBackdropBlur),
                draft.shortcut()));
    }

    private void editFeedback(ControlFeedbackSettings feedback) {
        edit(new Draft(draft.settings().withFeedback(feedback), draft.shortcut()));
    }

    private void editTabs(TabScrollSettings tabs) {
        edit(new Draft(draft.settings().withTabs(tabs), draft.shortcut()));
    }

    private void edit(Draft next) {
        if (!next.equals(draft)) {
            draft = next;
            beforeReset = null;
            status = null;
        }
        refreshActions();
    }

    private void resetDraft() {
        var oldLayout=panelLayout();
        if (beforeReset == null) {
            beforeReset = draft;
            draft = defaults;
        } else {
            draft = beforeReset;
            beforeReset = null;
        }
        layoutRebuild=oldLayout!=panelLayout();
        status = null;
        syncThemeEditor();
        if (dynamicThemeId != null) dynamicThemeId.setValue(draft.settings().dynamicTheme().fixedTheme());
        optionSliders.forEach(option -> option.control().setValue(option.read().getAsInt()));
        refreshActions();
    }

    private void updateThemePreview(ThemeSettings settings) {
        previewTheme = UiTheme.resolve(settings);
    }

    private void refreshActions() {
        if(layoutChoice!=null)layoutChoice.setMessage(panelLayoutLabel());
        var settings = draft.settings();
        updateThemePreview(settings.theme());
        var nextPreferences = UiPreferences.fromSettings(settings);
        sections.setSettings(settings.tabs());
        navigation.setSettings(settings.tabs());
        if (historyBack != null) historyBack.active = history.canBack();
        if (historyForward != null) historyForward.active = history.canForward();
        if (sidebar != null) sidebar.setPreferences(nextPreferences);
        if (!preferences.equals(nextPreferences)) {
            preferences = nextPreferences;
            for (GuiEventListener listener : children()) {
                if (listener instanceof UiButton button) button.setPreferences(preferences);
                else if (listener instanceof UiRubberSegment segment) segment.setPreferences(preferences);
                else if (listener instanceof UiSlider slider) slider.setPreferences(preferences);
                else if (listener instanceof UiScrollableSectionBar bar) bar.setPreferences(preferences);
                else if (listener instanceof ModOverviewGroup group) group.setPreferences(preferences);
            }
        }
        if (searchQuery.isBlank() && selectedArea == Area.DEVELOPER) {
            sampling.setValue(settings.backdrop().denseSampling());
            widgetBlur.setValue(settings.widgetBackdropBlur() && !settings.highContrast());
            widgetBlur.button().active = !settings.highContrast();
            if (settings.highContrast()) widgetBlur.button().setMessage(label("widget_blur.suspended"));
        } else if (searchQuery.isBlank() && selectedArea == Area.MUONIUM) {
            options.forEach(option -> option.control().setValue(option.read().getAsBoolean()));
        } else if (searchQuery.isBlank() && selectedArea == Area.CONFIGURATION) switch (selectedSection) {
            case GLASS -> {
                var backdrop = settings.backdrop();
                enabled.setValue(backdrop.enabled());
            }
            case DISPLAY -> {
                contrast.setValue(settings.highContrast());
                motion.setValue(settings.reducedMotion());
                refreshMusicToggle();
                var mode = settings.theme().mode();
                themeMode.select(THEMES.indexOf(mode));
                boolean custom = mode == ThemeSettings.Mode.CUSTOM;
                boolean hud = mode == ThemeSettings.Mode.HUD_GLASS;
                themeCustomize.visible = themeCustomize.active = custom;
                themeBackgroundSelect.visible = themeBackgroundSelect.active = custom;
                themeGroup.active = custom;
                themeGroup.visible = !hud;
                hudBackground.visible = hudBackground.active = hud;
                hudBackground.setMessage(hudBackgroundLabel());
                hudBlur.visible = hudBlur.active = hud;
                syncThemeEditor();
            }
            case SHORTCUT -> {
                shortcut.setMessage(capturingShortcut ? label("shortcut.capture") : label("shortcut.binding",
                        draft.shortcut().displayName(keyMapping)));
                shortcut.setSelected(capturingShortcut);
                clearShortcut.active = !capturingShortcut && !draft.shortcut().key().equals(InputConstants.UNKNOWN);
            }
            case FEEDBACK, TABS, DYNAMIC_THEME -> options.forEach(option ->
                    option.control().setValue(option.read().getAsBoolean()));
        }
        boolean undo = beforeReset != null;
        reset.setMessage(label(undo ? "reset.undo" : "reset"));
        reset.setLeadingIcon(undo ? GraphicIcon.BACK : GraphicIcon.RESET);
        reset.setPreview(label(undo ? "reset.undo.tooltip" : "reset.tooltip"));
        reset.active = !capturingShortcut && (undo || !draft.equals(defaults));
        apply.active = !capturingShortcut && !draft.equals(saved);
    }

    private static Component onOff(boolean selected) {
        return Component.translatable(selected ? "options.on" : "options.off");
    }

    private void apply() {
        if (!draft.settings().equals(saved.settings()) && !save.test(draft.settings())) {
            status = label("save_failed");
            return;
        }
        if (!draft.shortcut().equals(saved.shortcut())) {
            draft.shortcut().assign(keyMapping);
            minecraft.options.save();
        }
        saved = draft;
        beforeReset = null;
        status = label("saved");
        refreshActions();
    }

    private void finishShortcut(Shortcut binding) {
        capturingShortcut = false;
        modifierKey = null;
        edit(new Draft(draft.settings(), binding));
    }

    private static String modifierName(boolean control, boolean shift, boolean alt) {
        if (control) return "CONTROL";
        if (shift) return "SHIFT";
        return alt ? "ALT" : "NONE";
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (entrance.closing()) {
            reopenFromKey(key, scanCode, modifiers);
            return true;
        }
        if (entrance.active() && !capturingShortcut && key != GLFW.GLFW_KEY_ESCAPE
                && matchesPanelKey(key, scanCode, modifiers)) return true;
        if (!entrance.allowsActivation(key, getFocused())) return true;
        if (!capturingShortcut && fits && (modifiers & GLFW.GLFW_MOD_ALT) != 0
                && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            restoreHistory(key == GLFW.GLFW_KEY_LEFT ? -1 : 1); return true;
        }
        if (!capturingShortcut && search != null && search.isFocused() && !searchQuery.isBlank()
                && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            var results = searchEntries();
            if (!results.isEmpty()) navigateTo(Area.valueOf(results.get(0).area()), Section.valueOf(results.get(0).section()));
            return true;
        }
        if (!capturingShortcut && search != null && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_F) {
            if(headerOffset()!=0){classicSearchOpen=true;rebuildPanelLayout();}
            setFocused(search); return true;
        }
        if (!capturingShortcut && panelLayout()==com.dishanhai.sh_better_ui.config.ControlPanelLayout.CLASSIC
                && classicSearchOpen && key==GLFW.GLFW_KEY_ESCAPE) {
            searchQuery="";classicSearchOpen=false;searchDirty=false;rebuildPanelLayout();setFocused(navigation);return true;
        }
        if (!capturingShortcut && search != null && !searchQuery.isEmpty() && key == GLFW.GLFW_KEY_ESCAPE) {
            search.setValue(""); applyRequestedSection(); return true;
        }
        if (capturingShortcut) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                finishShortcut(draft.shortcut());
            } else {
                var input = InputConstants.getKey(key, scanCode);
                switch (key) {
                    case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL, GLFW.GLFW_KEY_LEFT_SHIFT,
                         GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> modifierKey = input;
                    default -> finishShortcut(new Shortcut(input, modifierName((modifiers & GLFW.GLFW_MOD_CONTROL) != 0,
                            (modifiers & GLFW.GLFW_MOD_SHIFT) != 0, (modifiers & GLFW.GLFW_MOD_ALT) != 0)));
                }
            }
            return true;
        }
        if (pageHandoff.active() && rows.containsKey(getFocused()) && (key == GLFW.GLFW_KEY_ENTER
                || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE && themeMode != null && themeMode.cancelDrag()) {
            setDragging(false);
            return true;
        }
        if (fits && key == GLFW.GLFW_KEY_ESCAPE && (navigation.keyPressed(key, scanCode, modifiers)
                || sections.visible && sections.keyPressed(key, scanCode, modifiers))) {
            setDragging(false);
            return true;
        }
        if (fits && maxScroll() > 0 && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
            scrollTo(scroll + (key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1) * (bodyBottom - bodyTop - GAP));
            return true;
        }
        if (key != GLFW.GLFW_KEY_ESCAPE) revealFocus(getFocused());
        boolean handled = super.keyPressed(key, scanCode, modifiers);
        applyRequestedSection();
        return handled;
    }

    /** 模态确认也在收起时，允许平台把明确的重开意图交给主控面板。 */
    public boolean reopenFromKey(int key, int scanCode, int modifiers) {
        return entrance.closing() && matchesPanelKey(key, scanCode, modifiers) && entrance.reopen();
    }

    private boolean matchesPanelKey(int key, int scanCode, int modifiers) {
        return saved.shortcut().matches(InputConstants.getKey(key, scanCode),
                modifierName((modifiers & GLFW.GLFW_MOD_CONTROL) != 0, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0,
                        (modifiers & GLFW.GLFW_MOD_ALT) != 0));
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (capturingShortcut && modifierKey != null && modifierKey.equals(InputConstants.getKey(key, scanCode))) {
            finishShortcut(new Shortcut(modifierKey, "NONE"));
            return true;
        }
        return super.keyReleased(key, scanCode, modifiers);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (bodyBottom - bodyTop));
    }

    private void scrollTo(int offset) {
        scroll = UiMath.clamp(offset, 0, maxScroll());
        if (searchQuery.isBlank()) {
            if (selectedArea == Area.CONFIGURATION) sectionScroll[selectedSection.ordinal()] = scroll;
            else areaScroll[selectedArea.ordinal()] = scroll;
        }
        rows.forEach((widget, y) -> widget.setY(bodyTop + y - scroll));
        if (glassCard != null) glassCard.setBounds(new Bounds(bodyX(), bodyTop + GLASS_CARD_TOP - scroll, glassWidth, 0));
    }

    private ControlCenterNavigation.Location location() {
        return new ControlCenterNavigation.Location(selectedArea.name(), selectedSection.name(), searchQuery, scroll);
    }

    private void navigateTo(Area area, Section section) {
        history.replace(location());
        history.visit(new ControlCenterNavigation.Location(area.name(), section.name(), "",
                area == Area.CONFIGURATION ? sectionScroll[section.ordinal()] : areaScroll[area.ordinal()]));
        restoreLocation(history.current());
    }

    private void restoreHistory(int direction) {
        if (direction < 0 ? !history.canBack() : !history.canForward()) return;
        history.replace(location());
        var destination = direction < 0 ? history.back() : history.forward();
        restoreLocation(destination);
    }

    private void restoreLocation(ControlCenterNavigation.Location location) {
        boolean continuing = capturePage();
        int previousHeader=headerOffset();
        selectedArea = Area.valueOf(location.area());
        selectedSection = Section.valueOf(location.section());
        searchQuery = location.query();
        if(!searchQuery.isBlank())classicSearchOpen=true;
        if(previousHeader!=headerOffset())rebuildPanelLayout();
        searchDirty = false;
        setDragging(false);
        clearPage();
        navigation.select(selectedArea.ordinal());
        sections.select(selectedSection.ordinal());
        if (sidebar != null) sidebar.state().select(selectedArea.ordinal());
        search.setValue(searchQuery);
        searchDirty = false;
        buildPage();
        scrollTo(location.scroll());
        startPageMorph(historyBack, PageTransition.nowMs(), continuing);
        GuiEventListener target = searchQuery.isBlank() ? (sidebar == null ? navigation : sidebar) : search;
        if (searchQuery.isBlank() && selectedArea == Area.CONFIGURATION && getFocused() == sections) target = sections;
        // A mouse-selected partially visible tab must not receive another focus-enter
        // event: the tab bar intentionally skips reveal when autoReveal is disabled.
        if (getFocused() != target) setFocused(target);
    }

    private List<ControlCenterNavigation.Entry> searchEntries() {
        var entries = new ArrayList<ControlCenterNavigation.Entry>();
        for (Section section : SECTIONS) entries.add(new ControlCenterNavigation.Entry(
                Area.CONFIGURATION.name(), section.name(), section.tab.label().getString(),
                label("search_keywords." + section.name().toLowerCase(java.util.Locale.ROOT)).getString()));
        for (Area area : AREAS) if (area != Area.CONFIGURATION) entries.add(new ControlCenterNavigation.Entry(
                area.name(), Section.GLASS.name(), area.tab.label().getString(), area.tab.label().getString()));
        return ControlCenterNavigation.search(entries, searchQuery);
    }

    /** Rebuilds structural controls; each caller restores its explicit focus target below.
     * Avoid Screen.rebuildWidgets' loader-specific automatic focus reset, which can steal
     * search/layout focus and also assumes an attached Minecraft instance in test hosts. */
    private void rebuildPanelLayout() { clearWidgets(); init(); }

    private void applyRequestedSection() {
        if(layoutRebuild){layoutRebuild=false;rebuildPanelLayout();if(layoutChoice!=null)setFocused(layoutChoice);return;}
        if (searchDirty) {
            searchDirty = false;
            clearPage();
            buildPage();
        }
        Area area = requestedArea;
        Section section = requestedSection;
        requestedArea = null;
        requestedSection = null;
        if (area != null && (area != selectedArea || !searchQuery.isBlank())) navigateTo(area, selectedSection);
        else if (section != null && section != selectedSection) {
            navigateTo(Area.CONFIGURATION, section);
            // Native release belongs to the section bar. Do not transfer its keyboard
            // continuation to the main navigation just because both use the same journal.
            if (getFocused() != sections) setFocused(sections);
        }
    }

    private boolean capturePage() {
        boolean continuing = pageHandoff.active();
        if (fits && minecraft != null && minecraft.screen == this) {
            pageHandoff.capture(new Bounds(bodyX() - 3, bodyTop, bodyWidth() + 6,
                    bodyBottom - bodyTop), preferences);
        }
        return continuing;
    }

    private void startPageMorph(UiButton tab, long now, boolean continuing) {
        float center = tab.getX() + tab.getWidth() * 0.5f;
        pageMorph.sample(now, preferences);
        if (preferences.reducedMotion()) {
            pageOrigin.snapTo(center);
        } else if (!pageMorph.running() && !continuing) {
            pageOrigin.snapTo(center);
            pageMorph.snapTo(0f);
            pageMorph.setOpen(true, now);
        } else {
            pageOrigin.animateTo(center, now);
        }
    }

    @Override
    public void setFocused(GuiEventListener listener) {
        super.setFocused(listener);
        revealFocus(listener);
    }

    private void revealFocus(GuiEventListener listener) {
        if (!fits || !(listener instanceof AbstractWidget widget)) return;
        Integer offset = rows.get(widget);
        if (offset == null) return;
        if (widget.getHeight() > bodyBottom - bodyTop - 6) {
            // 长信息组不能完整塞进视口；仍有可见内容时保留阅读位置。
            if (widget.getY() >= bodyBottom || widget.getY() + widget.getHeight() <= bodyTop) scrollTo(offset - 3);
            return;
        }
        if (widget.getY() < bodyTop + 3) scrollTo(offset - 3);
        else if (widget.getY() + widget.getHeight() > bodyBottom - 3) {
            scrollTo(offset + widget.getHeight() - (bodyBottom - bodyTop) + 3);
        }
    }

    private boolean inBody(double x, double y) {
        return fits && x >= bodyX() - 3 && x < left + panelWidth - PADDING + 3
                && y >= bodyTop && y < bodyBottom;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (entrance.closing()) {
            reopenFromMouse(button);
            return true;
        }
        if (fits && !entrance.allowsPointer(x, y)) return false;
        background.pulse(width, height, x, y);
        if (capturingShortcut) {
            finishShortcut(new Shortcut(InputConstants.Type.MOUSE.getOrCreate(button),
                    modifierName(hasControlDown(), hasShiftDown(), hasAltDown())));
            return true;
        }
        boolean inBody = inBody(x, y);
        if (button == 0 && inBody && !pageHandoff.active() && visiblePage != null && visiblePage.contains(x, y)
                && maxScroll() > 0 && x >= scrollbarX() - 3) {
            int thumbTop = thumbTop();
            scrollGrab = y >= thumbTop && y < thumbTop + thumbHeight() ? (int) y - thumbTop : thumbHeight() / 2;
            dragScrollbar(y);
            return true;
        }
        GuiEventListener clicked = null;
        for (GuiEventListener child : children()) {
            if (rows.containsKey(child) && (pageHandoff.active() || visiblePage == null
                    || !visiblePage.contains(x, y))) continue;
            if (child.mouseClicked(x, y, button)) {
                clicked = child;
                break;
            }
        }
        if (clicked == null) return false;
        setFocused(clicked);
        if (button == 0) setDragging(true);
        applyRequestedSection();
        return true;
    }

    /** 鼠标绑定沿用已保存的按键和修饰键。 */
    public boolean reopenFromMouse(int button) {
        if (!entrance.closing()) return false;
        Shortcut binding = saved.shortcut();
        var input = InputConstants.Type.MOUSE.getOrCreate(button);
        return (binding.matches(input, "NONE") || binding.key().equals(input)
                && binding.matches(input, modifierName(hasControlDown(), hasShiftDown(), hasAltDown())))
                && entrance.reopen();
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && scrollGrab >= 0) {
            dragScrollbar(y);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && scrollGrab >= 0) {
            scrollGrab = -1;
            return true;
        }
        if (button == 0 && isDragging() && getFocused() instanceof UiScrollableSectionBar bar) {
            setDragging(false);
            boolean handled = bar.mouseReleased(x, y, button);
            applyRequestedSection();
            return handled;
        }
        if (button == 0 && isDragging() && getFocused() instanceof UiRubberSegment segment) {
            setDragging(false);
            return segment.mouseReleased(x, y, button);
        }
        if (button == 0 && isDragging() && getFocused() instanceof UiSlider slider) {
            setDragging(false);
            return slider.mouseReleased(x, y, button);
        }
        boolean handled = super.mouseReleased(x, y, button);
        applyRequestedSection();
        return handled;
    }

    // Forge 与 NeoForge 分别使用单轴和双轴滚轮签名。
    public boolean mouseScrolled(double x, double y, double delta) {
        if (scrollTabs(x, y, 0, delta)) return true;
        return scrollBody(x, y, delta);
    }

    private boolean scrollBody(double x, double y, double delta) {
        if (!entrance.allowsPointer(x, y) || pageHandoff.active() || !inBody(x, y)
                || maxScroll() <= 0 || delta == 0 || isDragging() || scrollGrab >= 0) {
            return false;
        }
        scrollTo(scroll - (int) Math.round(delta * 26));
        return true;
    }

    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        if (scrollTabs(x, y, deltaX, deltaY)) return true;
        return scrollBody(x, y, deltaY);
    }

    private boolean scrollTabs(double x, double y, double deltaX, double deltaY) {
        return fits && entrance.allowsPointer(x, y) && !capturingShortcut
                && (navigation.mouseScrolled(x, y, deltaX, deltaY)
                    || sections.visible && sections.mouseScrolled(x, y, deltaX, deltaY));
    }

    private int scrollbarX() {
        return bodyX() + contentWidth + GAP;
    }

    private int thumbHeight() {
        int visible = bodyBottom - bodyTop;
        return Math.max(12, visible * visible / contentHeight);
    }

    private int thumbTravel() {
        return bodyBottom - bodyTop - thumbHeight();
    }

    private int thumbTop() {
        return bodyTop + Math.round((float) scroll * thumbTravel() / maxScroll());
    }

    private void dragScrollbar(double y) {
        if (thumbTravel() > 0) scrollTo((int) Math.round((y - bodyTop - scrollGrab) * maxScroll() / thumbTravel()));
    }

    @Override
    public void onClose() {
        if (entrance.closing()) return;
        if (draft.equals(saved)) {
            closeToParent();
            return;
        }
        if (overlays != null && overlays.isModalOpen()) return;
        overlays = new UiOverlayHost(this, preferences);
        var prompt = new UiConfirmation.Prompt(label("discard.title"), label("discard.details"),
                label("discard.action"), CommonComponents.GUI_CANCEL);
        if (back == null) overlays.showDialog(prompt, this::closeToParent);
        else overlays.showDialogFrom(back, prompt, this::closeToParent);
    }

    private void closeToParent() {
        setDragging(false);
        scrollGrab = -1;
        if (sections != null) sections.cancelDrag();
        if (navigation != null) navigation.cancelDrag();
        if (themeMode != null) themeMode.cancelDrag();
        entrance.close(preferences, () -> minecraft.setScreen(parent));
        if (!fits) entrance.finish();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (themeBrowser != null) { retainedThemePage = themeBrowser.pageIndex(); themeBrowser.close(); themeBrowser = null; }
        if (themeMode != null) themeMode.cancelDrag();
        themeEffectPreview.close();
        pageHandoff.clear();
        returning.clearHandoff();
        background.reset();
        super.removed();
    }

    @Override
    public void prepareReturnLayer() {
        restoreDeveloperFocus = true;
        if (fits && developer != null) setFocused(developer);
    }

    // NeoForge 由 Screen.render 调用，Forge 1.20.1 使用不同签名。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var palette = MinecraftThemes.previewPalette(draft.settings(), previewTheme);
        ThemeEffects.preview(draft.settings(),
                () -> UiTheme.preview(palette, () -> renderWithTheme(graphics, mouseX, mouseY, partialTick)));
    }

    private void renderWithTheme(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!fits) {
            renderPanelBackdrop(graphics, true);
            var lines = font.split(Component.translatable("sh_better_ui.picker.too_small"), Math.max(1, width - 8));
            int y = 6;
            for (var line : lines) {
                if (y + font.lineHeight > height - 27) break;
                graphics.drawCenteredString(font, line, width / 2, y, LiquidGlassTheme.textColor());
                y += font.lineHeight;
            }
        } else {
            var panel = new Bounds(left, top, panelWidth, panelHeight);
            if (entrance.render(graphics, panel, preferences,
                    progress -> returning.renderCrossfade(parent, graphics, partialTick, preferences, progress,
                            () -> renderPanelBackdrop(graphics, false)),
                    () -> renderContent(graphics, -1, -1, partialTick))) return;
            returning.clearHandoff();
            renderPanelBackdrop(graphics, true);
            LiquidGlassTheme.popover(graphics, left, top, panelWidth, panelHeight, preferences);
            renderContent(graphics, mouseX, mouseY, partialTick);
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPanelBackdrop(GuiGraphics graphics, boolean allowPointer) {
        refreshMusicToggle();
        if (!fits && UiTheme.current().hudGlass()) {
            DarkModeTheme.background(graphics, 0, 0, width, height);
            return;
        }
        background.renderThemed(this, graphics, width, height, preferences, allowPointer);
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        applyRequestedSection();
        int x = bodyX();
        long now = PageTransition.nowMs();
        if (searchQuery.isBlank() && selectedArea == Area.OVERVIEW && now >= nextOverviewRefresh) refreshOverview(now);
        layers.setShown(!entrance.closing(), now, preferences.reducedMotion());
        layers.tick(now, 1d, preferences.reducedMotion());
        if (themeBrowser != null && selectedArea == Area.THEMES && searchQuery.isBlank() && themeBrowser.refresh()) {
            remountThemePage(0);
        }
        if (themeBrowser != null && selectedArea == Area.THEMES && themeBrowser.error() != null) status = themeBrowser.error();
        int chromeX = left + PADDING;
        int chromeRight = left + panelWidth - PADDING;
        int titleRight = search.visible && !stacksSearch() ? historyBack.getX() - GAP : chromeRight;
        if (automation != null) titleRight = automation.getX() - GAP;
        var heading = UiRichText.fit(font, MpTypography.bold(title), titleRight - chromeX);
        graphics.drawString(font, heading, chromeX, top + 14, (LiquidGlassTheme.textColor() & 0xffffff)
                | (Math.max(1, (int)(255 * layers.layer(0, .45))) << 24), false);
        int divider = preferences.highContrast() ? LiquidGlassTheme.textColor() : DarkModeTheme.borderColor();
        if (sidebar != null) {
            graphics.fill(x - GAP - 1, top + navigationOffset(), x - GAP, bodyBottom, divider);
        }
        graphics.fill(chromeX, bodyBottom, chromeRight, bodyBottom + 1, divider);
        renderBody(graphics, x, mouseX, mouseY, partialTick);
        pageHandoff.render(graphics, preferences);
        Component message = status != null ? status : !draft.equals(saved) ? label("unsaved") : null;
        if (message != null) {
            int color = preferences.highContrast() || status != null
                    ? LiquidGlassTheme.textColor() : LiquidGlassTheme.mutedTextColor();
            graphics.drawString(font, UiRichText.fit(font, message, chromeRight - chromeX),
                    chromeX, bodyBottom + 4, color, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderBody(GuiGraphics graphics, int x, int mouseX, int mouseY, float partialTick) {
        long now = PageTransition.nowMs();
        float progress = GuiMotion.clamp01(pageMorph.sample(now, preferences));
        var viewport = new Bounds(x - 3, bodyTop, bodyWidth() + 6, bodyBottom - bodyTop);
        int surface = preferences.highContrast() ? DarkModeTheme.backgroundColor() : DarkModeTheme.surfaceColor();
        var panel = new GuiRect(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        float center = pageOrigin.sample(now, preferences);
        var capsule = FluidMorph.seed(center, bodyTop + 5f);
        var frame = FluidMorph.between(capsule, panel, BODY_RADIUS, progress);
        var rect = frame.rect();
        int edge = preferences.highContrast() ? LiquidGlassTheme.textColor()
                : GuiMotion.lerpColor(0xA0000000 | (LiquidGlassTheme.accentColor() & 0xFFFFFF), DarkModeTheme.borderColor(), progress);
        if (UiTheme.current().hudGlass() && !preferences.highContrast()) {
            surface = (surface & 0xFFFFFF) | 0x18000000;
            edge = (edge & 0xFFFFFF) | 0x40000000;
        }

        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        try {
            if (progress < 1f) {
                RoundedCorners.draw(graphics, viewport.x(), viewport.y(), viewport.width(), viewport.height(),
                        BODY_RADIUS, 0f, 0, surface);
                float opacity = GuiRenderUtil.easeOut((progress - 0.54f) / 0.44f) * layers.layer(.12, 1);
                renderSkeleton(graphics, viewport, x, now, (1f - opacity) * 0.42f);
            }
            RoundedCorners.draw(graphics, rect.getX(), rect.getY(), rect.getW(), rect.getH(),
                    frame.radius(), 1f, edge, surface);
            renderMorphContents(graphics, viewport, frame, x, mouseX, mouseY, partialTick, now, progress, surface);
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderMorphContents(GuiGraphics graphics, Bounds viewport, FluidMorph frame, int x, int mouseX,
                                     int mouseY, float partialTick, long now, float progress, int surface) {
        var rect = frame.rect();
        float inset = frame.radius() * 0.4f * (1f - progress);
        int left = Math.max(viewport.x(), Math.round(rect.getX() + inset));
        int top = Math.max(viewport.y(), Math.round(rect.getY() + inset));
        int right = Math.min(viewport.right(), Math.round(rect.getX() + rect.getW() - inset));
        int bottom = Math.min(viewport.bottom(), Math.round(rect.getY() + rect.getH() - inset));
        var clip = new Bounds(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        float opacity = GuiRenderUtil.easeOut((progress - 0.54f) / 0.44f) * layers.layer(.12, 1);
        visiblePage = opacity >= 0.9f ? clip : null;
        if (clip.width() == 0 || clip.height() == 0) return;
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        try {
            if (opacity > 0f) {
                boolean hover = !pageHandoff.active() && visiblePage != null && visiblePage.contains(mouseX, mouseY);
                renderPage(graphics, x, hover ? mouseX : -1, hover ? mouseY : -1, partialTick);
                if (maxScroll() > 0) {
                    int trackX = scrollbarX();
                    int thumbY = thumbTop();
                    graphics.fill(trackX + 1, bodyTop, trackX + SCROLLBAR_WIDTH - 1, bodyBottom, 0x544A5565);
                    graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight(),
                            LiquidGlassTheme.mutedTextColor());
                }
                if (opacity < 1f) {
                    graphics.fill(clip.x(), clip.y(), clip.right(), clip.bottom(),
                            GuiMotion.lerpColor(surface, surface & 0xFFFFFF, opacity));
                }
            }
            if (opacity < 1f) renderSkeleton(graphics, clip, x, now, 1f - opacity);
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderSkeleton(GuiGraphics graphics, Bounds viewport, int x, long now, float opacity) {
        var blocks = new ArrayList<UiSkeleton.Block>();
        blocks.add(new UiSkeleton.Block(new Bounds(x, bodyTop - scroll + 5, 48, 5)));
        for (AbstractWidget widget : rows.keySet()) {
            if (!widget.visible) continue;
            blocks.add(new UiSkeleton.Block(new Bounds(widget.getX() + 2, widget.getY() + 3,
                    widget.getWidth() - 4, widget.getHeight() - 6), 8f));
        }
        UiSkeleton.draw(graphics, viewport, blocks, now, preferences, opacity);
    }

    private void renderPage(GuiGraphics graphics, int x, int mouseX, int mouseY, float partialTick) {
        int origin = bodyTop - scroll;
        boolean searching = !searchQuery.isBlank();
        Component pageTitle = searching ? label("search.results", searchEntries().size())
                : selectedArea == Area.CONFIGURATION ? selectedSection.tab.label() : selectedArea.tab.label();
        if (searching || (selectedArea != Area.OVERVIEW && glassCard == null)) {
            graphics.drawString(font, UiRichText.fit(font, MpTypography.bold(pageTitle), contentWidth),
                    x, origin + 4, LiquidGlassTheme.textColor(), false);
        }
        if (glassCard != null) {
            glassCard.render(graphics, mouseX, mouseY, partialTick);
            renderPreview(graphics, x, origin);
        }
        if (searchQuery.isBlank() && selectedArea == Area.CONFIGURATION && selectedSection == Section.DISPLAY
                && draft.settings().theme().mode() == ThemeSettings.Mode.CUSTOM && ThemeEffectPreview.supports(themeGroupId())
                && origin + 248 > bodyTop && origin + 172 < bodyBottom) {
            themeEffectPreview.draw(themeGroupId(), graphics, font, new Bounds(x, origin + 172, contentWidth, 76),
                    themeGroupName(), mouseX, mouseY, preferences);
        }
        if (searchQuery.isBlank() && selectedArea == Area.MUONIUM) {
            int lineY = origin + 286;
            for (var line : font.split(label("muonium.independent"), contentWidth)) {
                graphics.drawString(font, line, x, lineY, LiquidGlassTheme.mutedTextColor(), false);
                lineY += font.lineHeight;
            }
        }
        if (searchQuery.isBlank() && selectedArea == Area.DEVELOPER) {
            graphics.drawString(font, label("developer_config.tools"), x, origin + 78, LiquidGlassTheme.mutedTextColor(), false);
        }
        for (AbstractWidget widget : rows.keySet()) {
            int y = widget.getY();
            if (y + widget.getHeight() > bodyTop && y < bodyBottom) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderPreview(GuiGraphics graphics, int x, int origin) {
        int originalX = sidePreview ? x + glassWidth + 2 * GAP : x;
        int sampleWidth = sidePreview ? x + contentWidth - originalX : (glassWidth - GAP) / 2;
        int previewX = sidePreview ? originalX : x + sampleWidth + GAP;
        int originalY = origin + (sidePreview ? 18 : glassStackPreviewTop());
        int previewY = sidePreview ? origin + 75 : originalY;
        int sampleHeight = sidePreview ? 36 : 28;
        drawSample(graphics, originalX, originalY, sampleWidth, sampleHeight);
        drawSample(graphics, previewX, previewY, sampleWidth, sampleHeight);
        var backdrop = ThemeEffects.glass();
        boolean sampled = GlassBlur.tryBackdrop(graphics, new GuiRect(previewX, previewY, sampleWidth, sampleHeight),
                1f, GlassStyle.LIQUID_REGULAR, 9f, 6f, backdrop);
        Component previewLabel = sampled ? label("preview")
                : label(backdrop.enabled() && backdrop.strength() > 0.01f ? "preview.unavailable" : "preview.off");
        int color = preferences.highContrast() ? LiquidGlassTheme.textColor() : LiquidGlassTheme.mutedTextColor();
        graphics.drawString(font, UiRichText.fit(font, label("original"), sampleWidth), originalX, originalY - 12,
                color, false);
        graphics.drawString(font, UiRichText.fit(font, previewLabel, sampleWidth), previewX, previewY - 12,
                color, false);
    }

    private static void drawSample(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fillGradient(x, y, x + width, y + height, 0xFF2B5287, 0xFF15243C);
        for (int stripe = x + 2; stripe < x + width - 1; stripe += 6) {
            graphics.fill(stripe, y, stripe + 1, y + height, 0x805B93B1);
        }
        int segment = width / 4;
        RoundedCorners.draw(graphics, x + segment / 2, y + 5, segment, height - 10,
                4, 0, 0, 0xFF79C5EA);
        RoundedCorners.draw(graphics, x + segment * 2, y + 7, segment, height - 14,
                4, 0, 0, 0xFFDB9A72);
    }

    private static Component label(String key, Object... arguments) {
        return MpTypography.text(Component.translatable("sh_better_ui.panel." + key, arguments));
    }

    private enum Section {
        GLASS("glass", GraphicIcon.SETTINGS),
        DISPLAY("display", GraphicIcon.MONITOR),
        FEEDBACK("feedback", GraphicIcon.ENABLE),
        TABS("tabs", GraphicIcon.MONITOR),
        SHORTCUT("shortcut", GraphicIcon.KEYBOARD),
        DYNAMIC_THEME("dynamic_theme", GraphicIcon.CUSTOM_THEME);

        private final UiSectionBar.Section tab;

        Section(String key, GraphicIcon icon) {
            tab = new UiSectionBar.Section(label("section." + key), icon);
        }
    }

    private enum Area {
        CONFIGURATION("configuration", GraphicIcon.SETTINGS),
        MUONIUM("muonium", GraphicIcon.SETTINGS),
        THEMES("themes", GraphicIcon.CUSTOM_THEME),
        MUSIC("music", GraphicIcon.MONITOR),
        DEVELOPER("developer_config", GraphicIcon.CODE),
        OVERVIEW("overview", GraphicIcon.INFO),
        LINKS("links", GraphicIcon.CHAT);

        private final UiSectionBar.Section tab;

        Area(String key, GraphicIcon icon) {
            tab = new UiSectionBar.Section(label("section." + key), icon);
        }
    }

    private enum FeedbackLink {
        GITHUB("github", GraphicIcon.GITHUB, "https://github.com/dishanhai/SH-Better-UI"),
        ISSUES("issues", GraphicIcon.GITHUB_ISSUE, "https://github.com/dishanhai/SH-Better-UI/issues"),
        AFDIAN("afdian", GraphicIcon.AFDIAN, "https://afdian.com/a/dishanhai?utm_source=copylink&utm_medium=link");

        private final String key;
        private final GraphicIcon icon;
        private final String url;

        FeedbackLink(String key, GraphicIcon icon, String url) {
            this.key = "links." + key;
            this.icon = icon;
            this.url = url;
        }
    }

    private record Draft(ClientUiSettings settings, Shortcut shortcut) {}

    private record ToggleOption(UiToggleButton control, BooleanSupplier read) {}
    private record SliderOption(UiSlider control, IntSupplier read) {}

    private record Shortcut(InputConstants.Key key, String modifier) {
        boolean matches(InputConstants.Key input, String activeModifier) {
            return !key.equals(InputConstants.UNKNOWN) && key.equals(input)
                    && ("NONE".equals(modifier) || modifier.equals(activeModifier));
        }

        Component displayName(KeyMapping mapping) {
            var nativeModifier = Enum.valueOf(mapping.getDefaultKeyModifier().getDeclaringClass(), modifier);
            return nativeModifier.getCombinedName(key, key::getDisplayName);
        }

        void assign(KeyMapping mapping) {
            var nativeModifier = Enum.valueOf(mapping.getDefaultKeyModifier().getDeclaringClass(), modifier);
            mapping.setKeyModifierAndCode(nativeModifier, key);
            KeyMapping.resetMapping();
        }
    }

    private enum Scale {
        RADIUS("radius", 200), STRENGTH("strength", 100), TINT("tint", 200), GRAIN("grain", 200),
        SATURATION("saturation", 200), LIFT("lift", 15), RIM("rim", 200), REFRACTION("refraction", 200);

        private final String key;
        private final int maxPercent;

        Scale(String key, int maxPercent) {
            this.key = key;
            this.maxPercent = maxPercent;
        }

        private ConfigPage.NumberOption<GlassBackdropSettings> option() {
            return new ConfigPage.NumberOption<>(key, label(key), label(key + ".tooltip"), 0, maxPercent, false,
                    settings -> new BigDecimal(Float.toString(read(settings))).movePointRight(2).doubleValue(),
                    (settings, value) -> write(settings, (float) (value / 100)));
        }

        private float read(GlassBackdropSettings settings) {
            return switch (this) {
                case RADIUS -> settings.blurRadiusScale();
                case STRENGTH -> settings.strength();
                case TINT -> settings.tintScale();
                case GRAIN -> settings.grainScale();
                case SATURATION -> settings.saturationScale();
                case LIFT -> settings.lift();
                case RIM -> settings.rimScale();
                case REFRACTION -> settings.refractionScale();
            };
        }

        private GlassBackdropSettings write(GlassBackdropSettings settings, float value) {
            return new GlassBackdropSettings(settings.enabled(),
                    this == RADIUS ? value : settings.blurRadiusScale(),
                    this == STRENGTH ? value : settings.strength(),
                    this == TINT ? value : settings.tintScale(),
                    this == GRAIN ? value : settings.grainScale(),
                    this == SATURATION ? value : settings.saturationScale(),
                    this == LIFT ? value : settings.lift(),
                    this == RIM ? value : settings.rimScale(),
                    this == REFRACTION ? value : settings.refractionScale(),
                    settings.denseSampling());
        }
    }

}
