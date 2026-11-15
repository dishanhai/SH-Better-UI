package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiMath;
import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiApiContext;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiBottomSheet;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCapsuleReveal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiFrameHandoff;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiFuseButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSwipeRow;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiFolderFloat;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiRubberSegment;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSloshGauge;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiJellyRadio;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCallChip;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPromptBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHoldButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScreenReturn;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextDetails;
import com.dishanhai.sh_better_ui.client.gui.search.SearchField;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** 山海：注册式控件与材质画廊，负责预览布局、输入、代码抽屉和页面过渡。 */
final class DeveloperGalleryScreen extends Screen {
    private static final int MIN_PANEL_HEIGHT = 148;
    private static final int MAX_PANEL_HEIGHT = 344;
    private static final int MAX_PANEL_WIDTH = 600;
    private static final int PADDING = 12;
    private static final int BUTTON_HEIGHT = 22;
    private static final int NAV_WIDTH = 24;
    private static final int FULLSCREEN_INSET = 6;
    private static final int FULLSCREEN_TOOLBAR = BUTTON_HEIGHT + 2 * FULLSCREEN_INSET;
    private static final int MIN_FULLSCREEN_HEIGHT = 124;
    private static final int SAMPLE_Y = 28;
    private static final int PREVIEW_Y = 59;
    private static final int STANDARD_PREVIEW_HEIGHT = 166;
    private static final int FOOTER_GAP = 5;
    private static final int CODE_PADDING = 4;
    private static final int MIN_DRAWER_HEIGHT = 68;
    private static final int DRAWER_HEADER_ALLOWANCE = 18;
    private static final Component BACK = Component.translatable("gui.back");
    private static final Component TOO_SMALL = Component.translatable("sh_better_ui.picker.too_small");
    private static final UiMouseState NO_MOUSE = new UiMouseState(-1, -1, -1, -1, false, false, false, 0);

    static {
        GaussBlur.allowScreen(screen -> screen instanceof DeveloperGalleryScreen);
    }

    private final Screen parent;
    private final Runnable restoreParentFocus;
    private final boolean fullscreenOnly;
    private final String fullscreenId;
    private final UiPreferences preferences;
    private final PreviewSpec.Category category;
    private final PreviewRegistry registry;
    private final UiOverlayHost overlays;
    private final UiBottomSheet codeDrawer = UiBottomSheet.capsule(0, 0f, 1f);
    private UiCapsuleReveal entrance = new UiCapsuleReveal();
    private final UiScreenReturn returning = new UiScreenReturn(this);
    private final UiCapsuleReveal sampleReveal = new UiCapsuleReveal();
    private final UiFrameHandoff sampleTitleHandoff = new UiFrameHandoff();
    private final UiFrameHandoff samplePreviewHandoff = new UiFrameHandoff();
    private final Map<PreviewSpec, Supplier<? extends PreviewScene>> remounts = new IdentityHashMap<>();
    private List<PreviewSpec> registryEntries = List.of();
    private List<PreviewSpec> samples = List.of();
    private List<UiSkeleton.Block> inactiveBlocks = List.of();
    private Supplier<UiButton> entranceSource;
    private PreviewSpec selected;
    private NativeMaterialRoute materialRoute;
    private PreviewSession session;
    private Bounds preview;
    private Bounds toolbar;
    private Bounds codeViewport;
    private UiBottomSheet.Frame codeFrame;
    private UiTextDetails.Layout codeLayout;
    private UiButton previousSample;
    private UiButton nextSample;
    private UiButton directoryButton;
    private UiButton retryButton;
    private UiButton fullscreenButton;
    private UiButton codeToggle;
    private UiButton codeDetailsButton;
    private UiButton copyButton;
    private GuiEventListener clicking;
    private SceneFocus restoreSceneFocus;
    private int panelWidth;
    private int left;
    private int top;
    private int sampleIndex;
    private int pendingStep;
    private boolean continueSampleReveal;
    private boolean openCodeOnEntry;
    private boolean copied;
    private boolean fullscreen;
    private boolean fullscreenEntrance;
    private boolean changeFullscreen;
    private boolean compact;
    private boolean detached;
    private boolean changesQueued;
    private boolean closingToParent;
    private boolean returningToParent;
    private boolean triedMissingPreviewReturn;
    private boolean failed;
    private boolean retryQueued;
    private boolean previewNeedsFullscreen;
    private DeveloperPreviewDirectory directory;
    private boolean leavingForDirectory;

    DeveloperGalleryScreen(DeveloperPanelScreen parent, UiPreferences preferences, PreviewSpec.Category category,
                           String sampleId, Supplier<UiButton> source) {
        this(parent, preferences, category, sampleId, null, parent::restoreReturnFocus);
        beginEntrance(source);
    }

    private DeveloperGalleryScreen(Screen parent, UiPreferences preferences, PreviewSpec.Category category,
                                   String sampleId, PreviewSpec direct, Runnable restoreParentFocus) {
        super(Component.translatable(direct == null ? category.titleKey() : direct.titleKey()));
        this.parent = parent;
        this.preferences = Objects.requireNonNull(preferences);
        this.category = Objects.requireNonNull(category);
        this.restoreParentFocus = restoreParentFocus;
        this.fullscreenOnly = direct != null;
        this.fullscreenId = direct == null ? null : direct.id();
        fullscreen = fullscreenOnly;
        registry = ShBetterUiApi.developerPreviews();
        overlays = new UiOverlayHost(this, preferences);
        selectFromRegistry(registry.entries(), sampleId == null ? null
                : registry.find(sampleId).map(PreviewSpec::id).orElse(null));
        openCodeOnEntry = !fullscreenOnly && sampleId != null && hasCode();
        entrance.setSkeletonLayout(target -> skeletonBlocks(children()));
        sampleReveal.setSkeletonLayout(target -> skeletonBlocks(previewWidgets()));
    }

    static List<String> sampleIds() {
        return ShBetterUiApi.developerPreviews().ids();
    }

    static Screen fullscreen(Screen parent, String id, UiPreferences preferences,
                             Supplier<? extends GuiEventListener> returnFocus) {
        return fullscreen(parent, requirePreview(id), preferences, returnFocus);
    }

    static Screen fullscreenMaterial(Screen parent, String id, UiPreferences preferences,
                                     Supplier<? extends GuiEventListener> returnFocus) {
        var route = NativeMaterialRoute.require(id, ShBetterUiApi.developerPreviews());
        var screen = fullscreen(parent, route.preview(), preferences, returnFocus);
        screen.materialRoute = route;
        return screen;
    }

    private static DeveloperGalleryScreen fullscreen(Screen parent, PreviewSpec spec, UiPreferences preferences,
                                                      Supplier<? extends GuiEventListener> returnFocus) {
        Objects.requireNonNull(returnFocus);
        return new DeveloperGalleryScreen(parent, preferences, spec.category(), spec.id(), spec, () -> {
            var target = returnFocus.get();
            if (target != null && parent != null && parent.children().contains(target)) parent.setFocused(target);
        });
    }

    static Screen fullscreenFrom(DeveloperPanelScreen parent, String id, UiPreferences preferences,
                                 Supplier<UiButton> source) {
        PreviewSpec spec = requirePreview(id);
        var screen = new DeveloperGalleryScreen(parent, preferences, spec.category(), spec.id(), spec,
                parent::restoreReturnFocus);
        screen.fullscreenEntrance = true;
        screen.beginEntrance(source);
        return screen;
    }

    private void beginEntrance(Supplier<UiButton> source) {
        entranceSource = source;
        entrance.startFrom(source);
        returning.beginHandoff(parent, preferences);
    }

    private static PreviewSpec requirePreview(String id) {
        return Optional.ofNullable(id).flatMap(ShBetterUiApi.developerPreviews()::find)
                .orElseThrow(() -> new IllegalArgumentException("Unknown developer sample: " + id));
    }

    @Override
    public void added() {
        if (detached && (closingToParent || entrance.closing())) resetEntrance();
        closingToParent = false;
        detached = false;
        super.added();
    }

    @Override
    protected void init() {
        if (isActiveOwner() && registryEntries != registry.entries()) refreshRegistry(registry.entries());
        fullscreenButton = null;
        previousSample = null;
        nextSample = null;
        directoryButton = null;
        retryButton = null;
        codeToggle = null;
        codeDetailsButton = null;
        copyButton = null;
        codeFrame = null;
        previewNeedsFullscreen = false;
        compact = width < 212 || height < (fullscreen ? MIN_FULLSCREEN_HEIGHT : MIN_PANEL_HEIGHT + 8);
        if (compact) {
            suspendScene();
            entrance.finish();
            sampleReveal.finish();
            clearSampleHandoff();
            if (width >= 36 && height >= BUTTON_HEIGHT + 8) {
                int buttonWidth = Math.min(100, width - 8);
                fullscreenButton = addButton((width - buttonWidth) / 2, height - BUTTON_HEIGHT - 4,
                        buttonWidth, fullscreen && !fullscreenOnly ? label("gallery.exit_fullscreen") : BACK,
                        GraphicIcon.BACK, this::onClose);
            }
            return;
        }
        if (fullscreen) {
            initFullscreen();
            return;
        }

        panelWidth = Math.min(width - 16, MAX_PANEL_WIDTH);
        var fit = fitPreview(width, height, selected == null ? 0 : selected.preferredHeight(), hasCode());
        previewNeedsFullscreen = fit.needsFullscreen();
        left = (width - panelWidth) / 2;
        top = fit.top();
        int contentX = left + PADDING;
        int contentRight = left + panelWidth - PADDING;
        int contentWidth = contentRight - contentX;
        preview = new Bounds(contentX, top + PREVIEW_Y, contentWidth, fit.previewHeight());
        toolbar = new Bounds(contentX, top + 5, contentWidth, BUTTON_HEIGHT);
        int footerY = preview.bottom() + FOOTER_GAP;
        int drawerY = footerY + BUTTON_HEIGHT + PADDING + FOOTER_GAP;
        codeViewport = fit.inlineCode() ? new Bounds(contentX, drawerY, contentWidth, MIN_DRAWER_HEIGHT) : null;

        previousSample = addArrow(contentX, top + SAMPLE_Y, false, "gallery.previous_sample", () -> requestStep(-1));
        Bounds title = sampleTitleBounds();
        directoryButton = addButton(title.x(), title.y(), title.width(), sampleTitle(), GraphicIcon.LIST,
                this::openDirectory);
        directoryButton.setPreview(label("gallery.directory_hint", sampleTitle()));
        nextSample = addArrow(contentRight - NAV_WIDTH, top + SAMPLE_Y, true, "gallery.next_sample", () -> requestStep(1));
        updateSampleArrows(sampleIndex);
        if (previewNeedsFullscreen) suspendScene();
        else layoutScene();
        addRetryButton();
        addFooter(contentX, contentRight, footerY, fit.inlineCode());
        restoreSceneFocus();
    }

    static PreviewFit fitPreview(int width, int height, int preferredHeight, boolean hasCode) {
        int frameHeight = Math.min(height - 8, MAX_PANEL_HEIGHT);
        int chromeHeight = PREVIEW_Y + FOOTER_GAP + BUTTON_HEIGHT + PADDING;
        int previewHeight = Math.min(STANDARD_PREVIEW_HEIGHT, frameHeight - chromeHeight);
        boolean needsFullscreen = preferredHeight > 0 && (previewHeight < preferredHeight || width < 240);
        int panelHeight = chromeHeight + previewHeight;
        int drawerHeight = FOOTER_GAP + MIN_DRAWER_HEIGHT;
        // Reserve the same drawer space on every page so the panel stays put when changing samples.
        boolean roomForDrawer = frameHeight - panelHeight >= drawerHeight;
        int top = Math.min((height - panelHeight) / 2, height - 4 - panelHeight - (roomForDrawer ? drawerHeight : 0));
        return new PreviewFit(top, previewHeight, hasCode && roomForDrawer, needsFullscreen);
    }

    record PreviewFit(int top, int previewHeight, boolean inlineCode, boolean needsFullscreen) {}

    private void initFullscreen() {
        top = 0;
        preview = new Bounds(FULLSCREEN_INSET, FULLSCREEN_TOOLBAR, width - 2 * FULLSCREEN_INSET,
                height - FULLSCREEN_TOOLBAR - FULLSCREEN_INSET);
        Component exit = fullscreenOnly ? BACK : label("gallery.exit_fullscreen");
        int exitWidth = Math.min(width / 2, Math.max(100, font.width(exit) + 34));
        fullscreenButton = addButton(FULLSCREEN_INSET, FULLSCREEN_INSET, exitWidth, exit, GraphicIcon.BACK, this::onClose);
        int toolbarX = fullscreenTitleX();
        toolbar = new Bounds(toolbarX, FULLSCREEN_INSET, preview.right() - toolbarX, BUTTON_HEIGHT);
        layoutScene();
        addRetryButton();
        setInitialFocus(fullscreenButton);
        restoreSceneFocus();
    }

    private int toolbarInset() {
        return selected == null ? 0 : selected.toolbarRightInset();
    }

    private int fullscreenTitleX() {
        return fullscreenButton.getX() + fullscreenButton.getWidth() + FULLSCREEN_INSET;
    }

    private void addFooter(int x, int right, int footerY, boolean inlineCode) {
        int fullscreenX = right - 74 - NAV_WIDTH;
        if (previewNeedsFullscreen) {
            fullscreenButton = addPreviewAction("gallery.fullscreen", GraphicIcon.SCALE, this::openFullscreen);
        } else if (selected != null) {
            fullscreenButton = addButton(fullscreenX, footerY, NAV_WIDTH, label("gallery.fullscreen"),
                    null, this::openFullscreen);
            fullscreenButton.setIcon(GraphicIcon.SCALE);
        }
        if (hasCode()) {
            if (inlineCode) addCodeDrawer(x, right, footerY, fullscreenX);
            else {
                openCodeOnEntry = false;
                var details = new UiTextDetails(label("gallery.code_title"), Component.literal(selected.code()), 1);
                codeDetailsButton = addButton(x, footerY, fullscreenX - NAV_WIDTH - 2 * FOOTER_GAP - x,
                        label("gallery.code_open"), GraphicIcon.CODE, () -> minecraft.setScreen(
                                details.pageFrom(this, preferences, codeDetailsButton, this::codeReturnFocus)));
                copyButton = addButton(fullscreenX - NAV_WIDTH - FOOTER_GAP, footerY, NAV_WIDTH,
                        label("gallery.copy"), null, this::copyCode);
                updateCopyFeedback();
            }
        }
        addButton(right - 68, footerY, 68, BACK, GraphicIcon.BACK, this::onClose);
    }

    private void openFullscreen() {
        if (!sampleTransitioning() && !overlays.isModalOpen()) changeFullscreen = true;
    }

    private void addRetryButton() {
        if (selected == null || previewNeedsFullscreen) return;
        retryButton = addPreviewAction("gallery.retry_preview", GraphicIcon.RESET, () -> retryQueued = true);
        retryButton.visible = failed;
    }

    private UiButton addPreviewAction(String key, GraphicIcon icon, Runnable action) {
        int buttonWidth = Math.min(140, preview.width());
        return addButton(preview.x() + (preview.width() - buttonWidth) / 2,
                preview.y() + (preview.height() - BUTTON_HEIGHT) / 2 + 10, buttonWidth, label(key), icon, action);
    }

    private void retryPreview() {
        retryQueued = false;
        if (!failed || !selectedRegistered()) return;
        closeScene();
        remounts.remove(selected);
        failed = false;
        rebuildWidgets();
        var target = failed ? retryButton : fullscreen ? fullscreenButton : directoryButton;
        if (target != null) setInitialFocus(target);
    }

    private void openDirectory() {
        if (sampleTransitioning() || overlays.isModalOpen()) return;
        if (directory == null) directory = new DeveloperPreviewDirectory(this, preferences, category, registry);
        directory.open(selected);
        leavingForDirectory = true;
        try {
            minecraft.setScreen(directory);
        } finally {
            leavingForDirectory = false;
        }
    }

    void returnFromDirectory(String id, NativeMaterialRoute route) {
        if (id != null) {
            PreviewSpec previous = selected;
            boolean routeChanged = !Objects.equals(materialRoute, route);
            if (previous != null && (!previous.id().equals(id) || materialRoute == null && routeChanged)) rememberScene();
            selectFromRegistry(registry.entries(), id);
            materialRoute = route;
            if (previous != selected || routeChanged) {
                restoreSceneFocus = null;
                resetPreviewState();
            }
        }
        minecraft.setScreen(this);
        if (isActiveOwner() && directoryButton != null) setInitialFocus(directoryButton);
    }

    void abandonDirectory() {
        rememberScene();
        closeScene();
        failed = false;
    }

    private boolean hasCode() {
        return selected != null && selected.code() != null && !selected.code().isBlank();
    }

    private void addCodeDrawer(int x, int right, int footerY, int fullscreenX) {
        int actionY = codeViewport.bottom() - BUTTON_HEIGHT - CODE_PADDING;
        int actionWidth = (codeViewport.width() - 3 * CODE_PADDING) / 2;
        int excerptHeight = actionY - codeViewport.y() - DRAWER_HEADER_ALLOWANCE - 2 * CODE_PADDING;
        UiTextDetails details = new UiTextDetails(label("gallery.code_title"), Component.literal(selected.code()),
                Math.max(1, excerptHeight / font.lineHeight));
        codeLayout = details.layout(font, codeViewport.width() - 2 * CODE_PADDING);
        codeToggle = addButton(x, footerY, fullscreenX - FOOTER_GAP - x, label("gallery.code_open"),
                GraphicIcon.EXPAND, () -> setCodeDrawerOpen(codeDrawer.targetAnchor() == 0));
        codeDetailsButton = addButton(x + CODE_PADDING, actionY, actionWidth, label("gallery.code_details"),
                GraphicIcon.NEXT, () -> minecraft.setScreen(details.pageFrom(
                        this, preferences, codeDetailsButton, this::codeReturnFocus)));
        copyButton = addButton(right - CODE_PADDING - actionWidth, actionY, actionWidth,
                label("gallery.copy"), null, this::copyCode);
        updateCopyFeedback();
        updateCodeDrawerControls(codeDrawer.sample(codeViewport, PageTransition.nowMs(), preferences));
    }

    private void setCodeDrawerOpen(boolean open) {
        openCodeOnEntry = false;
        codeDrawer.animateTo(open ? 1 : 0, PageTransition.nowMs());
        if (codeToggle != null) updateCodeDrawerControls(codeDrawer.sample(codeViewport, PageTransition.nowMs(), preferences));
        codeFrame = null;
    }

    private void updateCodeDrawerControls(UiBottomSheet.Frame frame) {
        boolean open = codeDrawer.targetAnchor() != 0;
        if (open != codeToggle.isSelected()) {
            codeToggle.setSelected(open);
            codeToggle.setMessage(label(open ? "gallery.code_close" : "gallery.code_open"));
            codeToggle.setLeadingIcon(open ? GraphicIcon.COLLAPSE : GraphicIcon.EXPAND);
        }
        boolean showActions = open && frame.panel().width() == frame.viewport().width()
                && frame.content().y() <= codeDetailsButton.getY();
        codeDetailsButton.visible = showActions;
        copyButton.visible = showActions;
        if (!showActions && (getFocused() == codeDetailsButton || getFocused() == copyButton)) setFocused(codeToggle);
    }

    private void copyCode() {
        minecraft.keyboardHandler.setClipboard(selected.code());
        copied = true;
        updateCopyFeedback();
        minecraft.getNarrator().say(label("copied"));
    }

    private void updateCopyFeedback() {
        copyButton.setMessage(label(copied ? "gallery.copied" : "gallery.copy"));
        copyButton.setPreview(null);
        var icon = copied ? GraphicIcon.SUCCESS : GraphicIcon.COPY;
        if (codeToggle == null) copyButton.setIcon(icon);
        else copyButton.setLeadingIcon(icon);
    }

    private UiButton codeReturnFocus() {
        return codeDetailsButton != null && codeDetailsButton.visible ? codeDetailsButton : codeToggle;
    }

    private UiButton addButton(int x, int y, int buttonWidth, Component text, GraphicIcon icon, Runnable action) {
        return addRenderableWidget(DeveloperPanelScreen.button(x, y, buttonWidth, BUTTON_HEIGHT,
                text, icon, preferences, action));
    }

    private UiButton addArrow(int x, int y, boolean forward, String key, Runnable action) {
        var button = addButton(x, y, NAV_WIDTH, label(key), null, action);
        button.setIcon(forward ? GraphicIcon.NEXT : GraphicIcon.PREVIOUS);
        return button;
    }

    private void requestStep(int direction) {
        if (!isActiveOwner() || fullscreen || changeFullscreen || selected == null) return;
        int requested = sampleIndex + pendingStep;
        int next = UiMath.clamp(requested + direction, 0, samples.size() - 1);
        if (next == requested) return;
        if (pendingStep == 0) {
            continueSampleReveal = sampleTransitioning();
            sampleTitleHandoff.capture(sampleTitleBounds(), preferences);
            samplePreviewHandoff.capture(preview, preferences);
        }
        pendingStep = next - sampleIndex;
        if (pendingStep == 0 && !sampleReveal.active()) clearSampleHandoff();
        updateSampleArrows(next);
    }

    private void updateSampleArrows(int index) {
        if (previousSample == null) return;
        previousSample.active = index > 0;
        nextSample.active = index + 1 < samples.size();
        directoryButton.setMessage(sampleTitle());
        directoryButton.setPreview(label("gallery.directory_hint", sampleTitle()));
    }

    private void queueChanges() {
        if (changesQueued || !isActiveOwner()) return;
        changesQueued = true;
        UiFrameScheduler.afterFrame(this, () -> {
            changesQueued = false;
            if (!isActiveOwner() || closingToParent || entrance.closing()) return;
            var entries = registry.entries();
            if (registryEntries != entries && refreshRegistry(entries)) {
                if (!isActiveOwner()) return;
                if (!(fullscreenOnly && selected == null)) rebuildWidgets();
            }
            if (!isActiveOwner()) return;
            if (hasRevokedMaterialSession()) {
                remounts.remove(selected);
                closeScene();
                if (directoryButton != null) setInitialFocus(directoryButton);
            }
            if (needsMissingPreviewReturn()) {
                triedMissingPreviewReturn = true;
                returnToParent();
            } else if (changeFullscreen) applyFullscreenChange();
            else if (pendingStep != 0) applyPendingStep();
            else if (retryQueued) retryPreview();
        });
    }

    private boolean refreshRegistry(List<PreviewSpec> entries) {
        remounts.keySet().removeIf(spec -> entries.stream().noneMatch(entry -> entry == spec));
        PreviewSpec previous = selected;
        selectFromRegistry(entries, fullscreenOnly ? fullscreenId : previous == null ? null : previous.id());
        if (selected != null) triedMissingPreviewReturn = false;
        if (previous == selected) {
            if (!samples.isEmpty()) {
                pendingStep = UiMath.clamp(sampleIndex + pendingStep, 0, samples.size() - 1) - sampleIndex;
            }
            updateSampleArrows(sampleIndex + pendingStep);
            return false;
        }
        pendingStep = 0;
        resetPreviewState();
        sampleReveal.finish();
        clearSampleHandoff();
        return true;
    }

    private void selectFromRegistry(List<PreviewSpec> entries, String keepId) {
        registryEntries = entries;
        samples = entries.stream().filter(spec -> spec.category() == category).toList();
        for (int index = 0; index < samples.size(); index++) {
            PreviewSpec spec = samples.get(index);
            if (spec.id().equals(keepId)) {
                sampleIndex = index;
                selected = spec;
                return;
            }
        }
        sampleIndex = samples.isEmpty() || fullscreenOnly ? 0 : Math.min(sampleIndex, samples.size() - 1);
        selected = samples.isEmpty() || fullscreenOnly ? null : samples.get(sampleIndex);
    }

    private void applyFullscreenChange() {
        changeFullscreen = false;
        fullscreen = !fullscreen;
        cancelGestures();
        entrance.finish();
        sampleReveal.finish();
        returning.clearHandoff();
        clearSampleHandoff();
        rebuildWidgets();
        if (fullscreenButton != null) setInitialFocus(fullscreenButton);
    }

    private void applyPendingStep() {
        int step = pendingStep;
        pendingStep = 0;
        rememberScene();
        if (!isActiveOwner()) return;
        sampleIndex += step;
        selected = samples.get(sampleIndex);
        materialRoute = null;
        resetPreviewState();
        if (!isActiveOwner()) return;
        rebuildWidgets();
        if (compact || !isActiveOwner()) return;
        UiButton direction = step < 0 ? previousSample : nextSample;
        float centerX = direction.getX() + direction.getWidth() * 0.5f;
        float centerY = preview.y() + 5f;
        if (continueSampleReveal) sampleReveal.retargetAt(centerX, centerY);
        else sampleReveal.startAt(centerX, centerY);
        setInitialFocus(direction.active ? direction : step < 0 ? nextSample : previousSample);
        minecraft.getNarrator().say(sampleTitle());
    }

    private void resetPreviewState() {
        closeScene();
        failed = false;
        retryQueued = false;
        copied = false;
        if (!hasCode()) setCodeDrawerOpen(false);
    }

    private void cancelGestures() {
        if (getFocused() instanceof UiRubberSegment segment) segment.cancelDrag();
        if (getFocused() instanceof UiJellyRadio radio) radio.cancelPress();
        UiLocalModal.interrupt(this);
        UiPointerCapture.cancel(this);
        setDragging(false);
        codeDrawer.cancelGesture(PageTransition.nowMs());
    }

    private Component sampleTitle() {
        return selected == null ? label("gallery.empty") : label("gallery.sample_page",
                Component.translatable(selected.titleKey()), sampleIndex + 1, samples.size());
    }

    private Bounds sampleTitleBounds() {
        int inset = NAV_WIDTH + 4;
        return new Bounds(preview.x() + inset, top + SAMPLE_Y, preview.width() - 2 * inset, BUTTON_HEIGHT);
    }

    private boolean sampleTransitioning() {
        return pendingStep != 0 || sampleReveal.active() || sampleTitleHandoff.active() || samplePreviewHandoff.active();
    }

    private boolean isActiveOwner() {
        return !detached && minecraft != null && minecraft.screen == this;
    }

    private boolean previewInteractive() {
        return isActiveOwner() && selectedRegistered() && !closingToParent && !changeFullscreen
                && !entrance.active() && !sampleTransitioning() && !overlays.isModalOpen();
    }

    private boolean selectedRegistered() {
        return selected != null && materialRouteAvailable() && (registryEntries == registry.entries()
                || registry.find(selected.id()).orElse(null) == selected);
    }

    private boolean materialRouteAvailable() {
        return materialRoute == null || materialRoute.available(registry);
    }

    private boolean hasRevokedMaterialSession() {
        return session != null && !materialRouteAvailable();
    }

    private boolean needsMissingPreviewReturn() {
        return fullscreenOnly && !selectedRegistered() && !triedMissingPreviewReturn;
    }

    private void clearSampleHandoff() {
        sampleTitleHandoff.clear();
        samplePreviewHandoff.clear();
    }

    // NeoForge 由 Screen.render 调用，Forge 1.20.1 使用不同签名。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isActiveOwner()) {
            renderInactive(graphics);
            return;
        }
        try {
            var local = UiLocalModal.current(this);
            boolean blocked = local != null && local.blocksPointer(mouseX, mouseY);
            renderScreen(graphics, blocked ? -1 : mouseX, blocked ? -1 : mouseY, partialTick);
        } finally {
            returning.renderHandoff(graphics, preferences);
        }
        if (changeFullscreen || pendingStep != 0 || retryQueued || hasRevokedMaterialSession() || registryEntries != registry.entries()
                || needsMissingPreviewReturn()) queueChanges();
        LayoutContext layout = currentLayout();
        if (layout != null && !layout.pendingWidgets.isEmpty()) {
            UiFrameScheduler.afterFrame(this, layout::attachPendingWidgets);
        }
    }

    private void renderScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (entrance.closing()) returning.renderBackdrop(graphics, width, height, partialTick);
        else renderBackgroundLayer(graphics);
        if (compact) {
            renderCompact(graphics);
        } else if (fullscreen) {
            if (fullscreenEntrance && returning.renderTransition(entrance, graphics, new Bounds(0, 0, width, height),
                    preferences, () -> {
                        renderFullscreenTitle(graphics);
                        renderPreviewContent(graphics, -1, -1, partialTick);
                        super.render(graphics, -1, -1, partialTick);
                    })) return;
            renderFullscreenTitle(graphics);
            renderPreviewContent(graphics, mouseX, mouseY, partialTick);
        } else {
            Bounds panel = panelBounds();
            if (returning.renderTransition(entrance, graphics, panel, preferences, () -> {
                renderPanelContent(graphics, -1, -1, partialTick);
                super.render(graphics, -1, -1, partialTick);
            })) return;
            LiquidGlassTheme.popover(graphics, panel.x(), panel.y(), panel.width(), panel.height(), preferences);
            renderPanelContent(graphics, mouseX, mouseY, partialTick);
            if (codeToggle != null) {
                if (openCodeOnEntry) setCodeDrawerOpen(true);
                codeFrame = codeDrawer.render(graphics, codeViewport, mouseX, mouseY, PageTransition.nowMs(),
                        preferences, this::renderCodeExcerpt);
                updateCodeDrawerControls(codeFrame);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!compact && !fullscreen) sampleTitleHandoff.render(graphics, preferences);
        LayoutContext layout = currentLayout();
        if (layout != null && layout.isCurrent() && !failed) {
            try {
                renderWidgets(layout, layout.overlayWidgets, new Bounds(0, 0, width, height), graphics,
                        previewInteractive(), mouseX, mouseY, partialTick);
            } catch (RuntimeException | LinkageError error) {
                failRendering(layout, error);
            }
        }
        if (previewInteractive() && getFocused() instanceof SearchField<?> field) {
            field.renderSuggestions(graphics, mouseX, mouseY, height);
        }
    }

    private Bounds panelBounds() {
        int bottom = preview.bottom() + FOOTER_GAP + BUTTON_HEIGHT + PADDING;
        return new Bounds(left, top, panelWidth, bottom - top);
    }

    private void renderBackgroundLayer(GuiGraphics graphics) {
        if (fullscreen || minecraft == null || minecraft.level == null) {
            DarkModeTheme.background(graphics, 0, 0, width, height);
        } else {
            graphics.fill(0, 0, width, height, 0x6020252E);
        }
    }

    private void renderCompact(GuiGraphics graphics) {
        int y = PADDING;
        for (var line : font.split(TOO_SMALL, Math.max(1, width - 2 * PADDING))) {
            if (y + font.lineHeight > height - BUTTON_HEIGHT - 6) break;
            graphics.drawString(font, line, PADDING, y, LiquidGlassTheme.textColor(), false);
            y += font.lineHeight + 2;
        }
    }

    private void renderInactive(GuiGraphics graphics) {
        renderBackgroundLayer(graphics);
        if (font == null) return;
        if (compact) {
            renderCompact(graphics);
        } else if (preview != null) {
            Bounds panel = fullscreen ? preview : panelBounds();
            if (fullscreen) renderFullscreenTitle(graphics);
            else {
                LiquidGlassTheme.popover(graphics, panel.x(), panel.y(), panel.width(), panel.height(), preferences);
                renderPanelTitles(graphics);
                var title = sampleTitleBounds();
                drawCentered(graphics, sampleTitle(), title.x(), title.y(), title.width());
                renderPreviewBorder(graphics);
            }
            UiSkeleton.draw(graphics, panel, inactiveBlocks, 0L, preferences, 1f);
        }
    }

    private void renderFullscreenTitle(GuiGraphics graphics) {
        graphics.fill(0, 0, width, FULLSCREEN_TOOLBAR, DarkModeTheme.surfaceColor());
        int x = fullscreenTitleX();
        if (selected != null) {
            drawCentered(graphics, Component.translatable(selected.titleKey()), x, FULLSCREEN_INSET,
                    toolbar.width() - toolbarInset());
        }
    }

    private void renderPanelTitles(GuiGraphics graphics) {
        int titleRight = toolbar.right() - Math.min(toolbar.width(), toolbarInset());
        graphics.enableScissor(toolbar.x(), toolbar.y(), titleRight, toolbar.bottom());
        try {
            graphics.drawString(font, getTitle(), preview.x(), top + 11, LiquidGlassTheme.textColor(), false);
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderPanelContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanelTitles(graphics);
        if (!sampleReveal.renderInset(graphics, preview, preferences,
                () -> renderPreviewContent(graphics, -1, -1, partialTick))) {
            renderPreviewBorder(graphics);
            renderPreviewContent(graphics, mouseX, mouseY, partialTick);
        }
        samplePreviewHandoff.render(graphics, preferences);
    }

    private void renderPreviewBorder(GuiGraphics graphics) {
        RoundedCorners.draw(graphics, preview.x(), preview.y(), preview.width(), preview.height(), 5, 1,
                preferences.highContrast() ? DarkModeTheme.textColor() : DarkModeTheme.borderColor(),
                DarkModeTheme.backgroundColor());
    }

    private void renderPreviewContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (previewNeedsFullscreen) {
            drawCentered(graphics, label("gallery.preview_needs_fullscreen"), preview.x() + 8,
                    preview.y() + (preview.height() - BUTTON_HEIGHT) / 2 - 12, preview.width() - 16);
            return;
        }
        LayoutContext layout = currentLayout();
        if (layout == null || !layout.isCurrent() || failed) {
            String message = !materialRouteAvailable() ? "gallery.material_unavailable"
                    : failed ? "gallery.preview_failed" : "gallery.empty";
            drawCentered(graphics, label(message),
                    preview.x() + 8, preview.y() + (preview.height() - BUTTON_HEIGHT) / 2 - (failed ? 12 : 0),
                    preview.width() - 16);
            return;
        }
        boolean interactive = previewInteractive() && mouseX >= 0 && mouseY >= 0;
        graphics.enableScissor(preview.x(), preview.y(), preview.right(), preview.bottom());
        try {
            try {
                session.scene.render(new UiFrame(graphics, preview, interactive ? UiMouseState.read() : NO_MOUSE,
                        preferences, PageTransition.nowMs(), partialTick));
                renderWidgets(layout, layout.widgets, preview, graphics, interactive, mouseX, mouseY, partialTick);
            } catch (RuntimeException | LinkageError error) {
                failRendering(layout, error);
            }
        } finally {
            graphics.disableScissor();
        }
        if (failed || !layout.isCurrent()) return;
        if (toolbar.width() > 0) {
            graphics.enableScissor(toolbar.x(), toolbar.y(), toolbar.right(), toolbar.bottom());
            try {
                renderWidgets(layout, layout.toolbarWidgets, toolbar, graphics, interactive, mouseX, mouseY, partialTick);
            } catch (RuntimeException | LinkageError error) {
                failRendering(layout, error);
            } finally {
                graphics.disableScissor();
            }
        }
    }

    Consumer<RenderService.Context> takeMaterialDraw() {
        LayoutContext layout = currentLayout();
        if (layout == null || !previewInteractive() || failed || UiLocalModal.current(this) != null
                || !(session.scene instanceof LiquidGlassPreview scene)) return null;
        var draw = scene.takeDraw();
        if (draw == null) return null;
        return context -> {
            if (!layout.isCurrent() || !previewInteractive()) return;
            try {
                draw.accept(context);
            } catch (RuntimeException | LinkageError error) {
                failRendering(layout, error);
            }
        };
    }

    private void failRendering(LayoutContext layout, Throwable error) {
        reportFailure("render", error);
        UiFrameScheduler.afterFrame(this, () -> {
            if (currentLayout() == layout && failed) closeScene();
        });
    }

    private static void renderWidgets(LayoutContext layout, List<AbstractWidget> widgets, Bounds clip, GuiGraphics graphics,
                                      boolean interactive, int mouseX, int mouseY, float partialTick) {
        boolean hover = interactive && clip.contains(mouseX, mouseY);
        for (int index = 0; index < widgets.size() && layout.isCurrent(); index++) {
            widgets.get(index).render(graphics, hover ? mouseX : -1, hover ? mouseY : -1, partialTick);
        }
    }

    private List<AbstractWidget> previewWidgets() {
        LayoutContext layout = currentLayout();
        return layout == null ? List.of() : layout.widgets;
    }

    private List<UiSkeleton.Block> skeletonBlocks(List<? extends GuiEventListener> elements) {
        var blocks = new ArrayList<UiSkeleton.Block>();
        for (var element : elements) {
            if (element instanceof AbstractWidget widget && widget.visible && widget.getWidth() > 0 && widget.getHeight() > 0) {
                blocks.add(new UiSkeleton.Block(new Bounds(widget.getX(), widget.getY(),
                        widget.getWidth(), widget.getHeight())));
            }
        }
        if (preview != null && previewWidgets().isEmpty()) blocks.add(new UiSkeleton.Block(preview, 5f));
        return blocks;
    }

    private void renderCodeExcerpt(GuiGraphics graphics, Bounds content) {
        int textY = content.y() + CODE_PADDING;
        int bottom = codeDetailsButton.getY() - CODE_PADDING;
        if (bottom <= textY) return;
        graphics.enableScissor(content.x(), textY, content.right(), bottom);
        try {
            codeLayout.render(graphics, font, content.x() + CODE_PADDING, textY,
                    LiquidGlassTheme.textColor(), false, preferences);
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawCentered(GuiGraphics graphics, Component text, int x, int y, int w) {
        if (w <= 0) return;
        var lines = font.split(text, w);
        int count = Math.min(2, lines.size());
        int textY = y + (BUTTON_HEIGHT - count * font.lineHeight) / 2;
        for (int i = 0; i < count; i++) {
            var line = lines.get(i);
            graphics.drawString(font, line, x + (w - font.width(line)) / 2, textY, LiquidGlassTheme.textColor(), false);
            textY += font.lineHeight;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isActiveOwner() || closingToParent || changeFullscreen) return true;
        if (entrance.closing()) {
            if (entrance.reopenFromSource(mouseX, mouseY, button)) returning.beginHandoff(this, preferences);
            return true;
        }
        if (!entrance.allowsPointer(mouseX, mouseY)) return false;
        if (previewInteractive() && getFocused() instanceof SearchField<?> field
                && field.mouseClickedSuggestion(mouseX, mouseY, button, height)) return true;
        if (codeToggle != null && codeDrawer.beginCaptured(this, codeFrame, button, mouseX, mouseY)) return true;
        for (var child : children()) {
            if (!previewInputAllowed(child, mouseX, mouseY)) continue;
            clicking = child;
            try {
                boolean handled = child.mouseClicked(mouseX, mouseY, button);
                if (!isActiveOwner()) return true;
                if (!handled) continue;
                setFocused(child);
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) setDragging(true);
                return true;
            } finally {
                clicking = null;
            }
        }
        return false;
    }

    private boolean previewInputAllowed(GuiEventListener child, double x, double y) {
        LayoutContext layout = currentLayout();
        if (layout == null) return true;
        if (layout.widgets.contains(child)) return previewInteractive() && preview.contains(x, y);
        if (layout.toolbarWidgets.contains(child)) return previewInteractive() && toolbar.contains(x, y);
        return !layout.overlayWidgets.contains(child) || previewInteractive();
    }

    private boolean hasStalePreviewFocus() {
        LayoutContext layout = currentLayout();
        return layout != null && layout.contains(getFocused()) && !layout.isCurrent();
    }

    @Override
    public Optional<GuiEventListener> getChildAt(double mouseX, double mouseY) {
        return children().stream()
                .filter(child -> previewInputAllowed(child, mouseX, mouseY) && child.isMouseOver(mouseX, mouseY))
                .map(GuiEventListener.class::cast).findFirst();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!isActiveOwner() || closingToParent) return true;
        if (changeFullscreen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) onClose();
            return true;
        }
        if (entrance.closing()) {
            if (entrance.reopenFromKey(key)) returning.beginHandoff(this, preferences);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (previewInteractive() && getFocused() instanceof UiFolderFloat<?> folder
                    && folder.keyPressed(key, scan, modifiers)) {
                setDragging(false);
                return true;
            }
            if (previewInteractive() && getFocused() instanceof UiFuseButton fuse && fuse.keyPressed(key, scan, modifiers)) return true;
            if (previewInteractive() && getFocused() instanceof UiSwipeRow row && row.keyPressed(key, scan, modifiers)) {
                setDragging(false);
                return true;
            }
            if (getFocused() instanceof UiHoldButton hold && hold.cancelHold()) {
                setDragging(false);
                return true;
            }
            if (getFocused() instanceof UiSloshGauge gauge && gauge.cancelDrag()) {
                setDragging(false);
                return true;
            }
            if ((getFocused() instanceof UiRubberSegment segment && segment.cancelDrag())
                    || (getFocused() instanceof UiJellyRadio radio && radio.cancelPress())) {
                setDragging(false);
                return true;
            }
            if (getFocused() instanceof UiCallChip chip && chip.cancelPress()) {
                setDragging(false);
                return true;
            }
            if (previewInteractive() && getFocused() instanceof UiPromptBar prompt && prompt.dismissPopup()) {
                setDragging(false);
                return true;
            }
            if (previewInteractive() && getFocused() instanceof SearchField<?> field && field.dismissSuggestions()) return true;
            if (codeToggle != null && (codeDrawer.targetAnchor() != 0 || openCodeOnEntry && !entrance.active())) {
                setCodeDrawerOpen(false);
                return true;
            }
        }
        LayoutContext layout = currentLayout();
        if (layout != null && !layout.isCurrent() && key != GLFW.GLFW_KEY_ESCAPE) return true;
        if (!entrance.allowsActivation(key, getFocused())) return true;
        if (!previewInteractive() && layout != null && layout.contains(getFocused())
                && key != GLFW.GLFW_KEY_TAB && key != GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        LayoutContext layout = currentLayout();
        if (!previewInteractive() && layout != null && layout.contains(getFocused())) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        return hasStalePreviewFocus() || super.keyReleased(key, scan, modifiers);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && getFocused() instanceof UiSloshGauge gauge) {
            if (!previewInteractive()) gauge.cancelDrag();
            if (gauge.mouseReleased(x, y, button)) {
                setDragging(false);
                return true;
            }
        }
        if (button == 0 && getFocused() instanceof UiSwipeRow row) {
            if (!previewInteractive()) row.cancelPress();
            if (row.mouseReleased(x, y, button)) {
                setDragging(false);
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && getFocused() instanceof UiFuseButton fuse) {
            if (!previewInteractive()) fuse.cancelPress();
            if (fuse.mouseReleased(x, y, button)) {
                setDragging(false);
                return true;
            }
        }
        if (button == 0 && getFocused() instanceof UiCallChip chip) {
            if (!previewInteractive()) chip.cancelPress();
            if (chip.mouseReleased(x, y, button)) {
                setDragging(false);
                return true;
            }
        }
        if (button == 0 && getFocused() instanceof UiHoldButton hold) {
            if (!previewInteractive()) hold.cancelHold();
            if (hold.mouseReleased(x, y, button)) {
                setDragging(false);
                return true;
            }
        }
        if (button == 0 && getFocused() instanceof UiJellyRadio radio) {
            if (!previewInteractive()) radio.cancelPress();
            if (radio.mouseReleased(x, y, button)) {
                setDragging(false);
                return true;
            }
        }
        if (button == 0 && getFocused() instanceof UiRubberSegment segment) {
            if (!previewInteractive()) segment.cancelDrag();
            if (segment.mouseReleased(x, y, button)) {
                setDragging(false);
                return true;
            }
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return hasStalePreviewFocus() || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return mouseScrolled(mouseX, mouseY, 0, delta);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!isActiveOwner() || !entrance.allowsPointer(mouseX, mouseY)) return false;
        if (previewInteractive() && getFocused() instanceof SearchField<?> field
                && field.mouseScrolledSuggestions(mouseX, mouseY, deltaY, height)) return true;
        return getChildAt(mouseX, mouseY)
                .map(widget -> PlatformUiApi.scrollWidget(widget, mouseX, mouseY, deltaX, deltaY)).orElse(false);
    }

    @Override
    public void removed() {
        detached = true;
        LayoutContext layout = currentLayout();
        if (returningToParent) {
            remounts.clear();
            restoreSceneFocus = null;
        } else {
            if (layout != null) {
                int index = sceneChildren(layout).indexOf(clicking == null ? getFocused() : clicking);
                restoreSceneFocus = index < 0 ? null : new SceneFocus(selected, index);
            }
            if (!leavingForDirectory) rememberScene();
        }
        inactiveBlocks = skeletonBlocks(children());
        changeFullscreen = false;
        changesQueued = false;
        retryQueued = false;
        pendingStep = 0;
        returning.clearHandoff();
        clearSampleHandoff();
        if (leavingForDirectory) suspendScene();
        else {
            closeScene();
            failed = false;
        }
        super.removed();
    }

    @Override
    public void onClose() {
        if (!isActiveOwner() || closingToParent) return;
        if (changeFullscreen) {
            if (!fullscreen) changeFullscreen = false;
            return;
        }
        if (fullscreen) {
            if (!fullscreenOnly) {
                changeFullscreen = true;
                return;
            }
            if (!fullscreenEntrance) {
                returnToParent();
                return;
            }
        }
        if (entrance.closing()) return;
        cancelGestures();
        if (pendingStep != 0) {
            pendingStep = 0;
            updateSampleArrows(sampleIndex);
            if (!sampleReveal.active()) clearSampleHandoff();
        }
        if (!compact && !preferences.reducedMotion()) {
            returning.beginHandoff(this, preferences);
            returning.prepare(parent, width, height);
        }
        entrance.close(preferences, () -> {
            closingToParent = true;
            UiFrameScheduler.afterFrame(this, this::returnToParent);
        });
        if (compact) entrance.finish();
    }

    private void returnToParent() {
        if (!isActiveOwner()) return;
        boolean completedEntrance = closingToParent;
        returningToParent = true;
        try {
            minecraft.setScreen(parent);
        } finally {
            returningToParent = false;
            if (isActiveOwner()) {
                closingToParent = false;
                if (completedEntrance) resetEntrance();
            }
        }
        if (parent != null && minecraft.screen == parent) restoreParentFocus.run();
    }

    private void resetEntrance() {
        entrance = new UiCapsuleReveal();
        entrance.setSkeletonLayout(target -> skeletonBlocks(children()));
        if (entranceSource != null) entrance.startFrom(entranceSource);
        entrance.finish();
        returning.clearHandoff();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private LayoutContext currentLayout() {
        return session == null ? null : session.layout;
    }

    private List<? extends GuiEventListener> sceneChildren(LayoutContext layout) {
        return children().stream().filter(layout::contains).toList();
    }

    private void restoreSceneFocus() {
        SceneFocus remembered = restoreSceneFocus;
        if (remembered == null) return;
        restoreSceneFocus = null;
        LayoutContext layout = currentLayout();
        if (layout == null || selected != remembered.spec()) return;
        var children = sceneChildren(layout);
        if (remembered.index() < children.size() && children.get(remembered.index()) instanceof AbstractWidget widget
                && widget.visible && widget.active) setInitialFocus(widget);
    }

    private record SceneFocus(PreviewSpec spec, int index) {}

    private void layoutScene() {
        if (!isActiveOwner() || !selectedRegistered() || failed) return;
        try {
            if (session == null) {
                PreviewSpec spec = selected;
                var remount = remounts.remove(spec);
                PreviewScene scene = Objects.requireNonNull((remount == null ? spec.factory() : remount).get());
                if (!isActiveOwner() || selected != spec || !selectedRegistered()) {
                    scene.close();
                    return;
                }
                session = new PreviewSession(scene);
            }
            if (session.layout != null) {
                cancelGestures();
                overlays.ownerClosed();
            }
            session.clearLayout();
            LayoutContext layout = new LayoutContext(session);
            session.layout = layout;
            try {
                session.scene.layout(layout);
            } finally {
                layout.layingOut = false;
            }
        } catch (RuntimeException | LinkageError error) {
            reportFailure("layout", error);
            closeScene();
        }
    }

    private void rememberScene() {
        if (session == null || failed || !selectedRegistered()) return;
        PreviewSpec spec = selected;
        try {
            var remount = session.scene.saveForRemount();
            if (remount != null) remounts.put(spec, remount);
        } catch (RuntimeException | LinkageError error) {
            ShBetterUi.LOGGER.warn("Could not save developer preview {}", spec.id(), error);
        }
    }

    private void suspendScene() {
        PreviewSession current = session;
        if (current == null || current.layout == null) return;
        try {
            current.clearLayout();
            cancelGestures();
            overlays.ownerClosed();
            if (session == current) current.scene.suspend();
        } catch (RuntimeException | LinkageError error) {
            reportFailure("suspend", error);
            if (session == current) closeScene();
        }
    }

    private void closeScene() {
        PreviewSession previous = session;
        if (previous == null) return;
        session = null;
        try {
            previous.clearLayout();
            cancelGestures();
            overlays.ownerClosed();
        } catch (RuntimeException | LinkageError error) {
            ShBetterUi.LOGGER.warn("Could not clear developer preview controls", error);
        } finally {
            try {
                previous.scene.close();
            } catch (RuntimeException | LinkageError error) {
                ShBetterUi.LOGGER.warn("Could not close developer preview", error);
            } finally {
                try {
                    previous.api.close();
                } catch (RuntimeException | LinkageError error) {
                    ShBetterUi.LOGGER.warn("Could not close developer preview context", error);
                }
            }
        }
    }

    private void reportFailure(String phase, Throwable error) {
        failed = true;
        if (retryButton != null) retryButton.visible = true;
        ShBetterUi.LOGGER.warn("Could not {} developer preview {}", phase, selected == null ? null : selected.id(), error);
    }

    private final class PreviewSession {
        private final PreviewScene scene;
        private final UiApiContext api = new UiApiContext();
        private LayoutContext layout;

        private PreviewSession(PreviewScene scene) {
            this.scene = scene;
        }

        private void clearLayout() {
            LayoutContext previous = layout;
            layout = null;
            if (previous == null) return;
            if (previous.contains(getFocused())) setFocused(null);
            previous.widgets.forEach(DeveloperGalleryScreen.this::removeWidget);
            previous.toolbarWidgets.forEach(DeveloperGalleryScreen.this::removeWidget);
            previous.overlayWidgets.forEach(DeveloperGalleryScreen.this::removeWidget);
            previous.widgets.clear();
            previous.toolbarWidgets.clear();
            previous.overlayWidgets.clear();
            previous.pendingWidgets.clear();
        }
    }

    private final class LayoutContext implements PreviewContext {
        private final PreviewSession owner;
        private final Bounds viewport = preview;
        private final Bounds toolbarArea = toolbar;
        private final List<AbstractWidget> widgets = new ArrayList<>();
        private final List<AbstractWidget> toolbarWidgets = new ArrayList<>();
        private final List<AbstractWidget> overlayWidgets = new ArrayList<>();
        private final List<Runnable> pendingWidgets = new ArrayList<>();
        private boolean layingOut = true;

        private LayoutContext(PreviewSession owner) {
            this.owner = owner;
        }

        private boolean isCurrent() {
            return isActiveOwner() && session == owner && owner.layout == this && selectedRegistered();
        }

        private boolean contains(GuiEventListener widget) {
            return widgets.contains(widget) || toolbarWidgets.contains(widget) || overlayWidgets.contains(widget);
        }

        @Override public Screen screen() { return DeveloperGalleryScreen.this; }
        @Override public Bounds viewport() { return viewport; }
        @Override public Bounds toolbar() { return toolbarArea; }
        @Override public Font font() { return font; }
        @Override public UiPreferences preferences() { return preferences; }
        @Override public UiOverlayHost overlays() { return overlays; }
        @Override public UiApiContext context() { return owner.api; }
        @Override public boolean interactive() { return isCurrent() && !failed && previewInteractive(); }

        @Override
        public <T extends AbstractWidget> T add(T widget) {
            Objects.requireNonNull(widget);
            return register(widget, widget.getY() < viewport.y() ? toolbarWidgets : widgets);
        }

        @Override
        public <T extends AbstractWidget> T addOverlay(T widget) {
            return register(Objects.requireNonNull(widget), overlayWidgets);
        }

        private <T extends AbstractWidget> T register(T widget, List<AbstractWidget> destination) {
            if (!isCurrent() || failed || !minecraft.isSameThread()) return widget;
            Runnable attach = () -> {
                if (!isCurrent() || failed || contains(widget)) return;
                destination.add(widget);
                addWidget(widget);
            };
            if (layingOut) attach.run();
            else pendingWidgets.add(attach);
            return widget;
        }

        private void attachPendingWidgets() {
            pendingWidgets.forEach(Runnable::run);
            pendingWidgets.clear();
        }
    }
}
