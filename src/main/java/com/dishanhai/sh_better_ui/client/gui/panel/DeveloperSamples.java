package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec.Category;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;

import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec.Category.CONTROLS;
import static com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec.Category.MATERIALS;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.DEFAULT_HEIGHT;

/** 山海：开发者图库使用的控件、材质和对应 Java 接入示例。 */
enum DeveloperSamples {
    BUTTON("button", CONTROLS, 2, UiStyle.DEFAULT),
    TOGGLE("toggle", 3, ControlPreviewScenes.Toggle::new, """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.NetworkToggle;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.network.chat.Component;

            private NetworkToggle toggle;
            private void addToggle(int x, int y) {
                toggle = new NetworkToggle(new Bounds(x, y, 140, 22),
                        Component.translatable("options.on"), false,
                        UiPreferences.configured(), value -> toggle.setValue(value));
                addRenderableWidget(toggle);
            }
            """),
    SECTIONS("sections", 4, ControlPreviewScenes.Sections::new, """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
            import net.minecraft.network.chat.Component;
            import java.util.List;
            import java.util.function.IntConsumer;

            private void addSections(int x, int y, IntConsumer onSelect) {
                var bar = new UiSectionBar(x, y, 164, 22, UiPreferences.configured(), List.of(
                        new UiSectionBar.Section(Component.translatable("options.title"), GraphicIcon.SETTINGS),
                        new UiSectionBar.Section(Component.translatable("gui.stats"), GraphicIcon.MONITOR)),
                        0, onSelect);
                bar.buttons().forEach(this::addRenderableWidget);
            }
            """),
    UNDERLINED_TABS("underlined_tabs", 0, 60, ControlPreviewScenes.Tabs::new, """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

            private void addTabs(int x, int y) {
                addRenderableWidget(ShBetterUiApi.controls().underlinedTabs(
                        new Bounds(x, y, 200, 48), index -> {}));
            }
            """),
    STEPPER("stepper", 1, 60, ControlPreviewScenes.Stepper::new, """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiStepper;

            private UiStepper steps;

            private void addStepper(int x, int y) {
                steps = addRenderableWidget(ShBetterUiApi.controls().stepper(
                        new Bounds(x, y, 200, 44), 0, index -> {}));
            }

            private void onValidated() {
                steps.progress().advance();
            }
            """),
    DISCLOSURE("disclosure", 5, ControlPreviewScenes.Disclosure::new, """
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiDisclosure;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.network.chat.Component;

            private final UiDisclosure disclosure = new UiDisclosure(false);

            private void addDisclosure(int x, int y) {
                addRenderableWidget(disclosure.button(x, y, 120, 22,
                        Component.translatable("sh_better_ui.panel.developer.gallery.expand"),
                        Component.translatable("sh_better_ui.panel.developer.gallery.collapse"),
                        UiPreferences.configured(), expanded -> {}));
            }

            private boolean showDetails() {
                return disclosure.expanded();
            }
            """),
    TEXT_DETAILS("text_details", 6, 72, ControlPreviewScenes.TextDetails::new, """
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextDetails;
            import net.minecraft.client.gui.GuiGraphics;
            import net.minecraft.network.chat.Component;

            private final UiTextDetails text = new UiTextDetails(
                    Component.translatable("sh_better_ui.panel.developer.gallery.sample.text_details"),
                    Component.translatable("sh_better_ui.panel.developer.gallery.long_text"), 1);
            private UiTextDetails.Layout detailsLayout;

            private void addDetails(int x, int buttonY, int width) {
                var prefs = UiPreferences.configured();
                detailsLayout = text.layout(font, width);
                int half = (width - 6) / 2;
                text.inlineButton(detailsLayout, x, buttonY, half, 22, prefs,
                        expanded -> detailsLayout = text.layout(font, width))
                        .ifPresent(this::addRenderableWidget);
                text.pageButton(detailsLayout, this, x + half + 6, buttonY,
                        width - half - 6, 22, prefs).ifPresent(this::addRenderableWidget);
            }

            private void drawDetails(GuiGraphics graphics, int x, int y) {
                detailsLayout.render(graphics, font, x, y, 0xFFFFFFFF, false, UiPreferences.configured());
            }
            """),
    SLIDER("slider", 7, ControlPreviewScenes.Slider::new, """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
            import net.minecraft.network.chat.Component;

            private int sliderValue = 50;

            private void addSlider(int x, int y) {
                var slider = new UiSlider(new Bounds(x, y, 164, 22), 0, 100, 5,
                        sliderValue, UiPreferences.configured(),
                        value -> Component.translatable("sh_better_ui.panel.developer.gallery.slider_value", value),
                        value -> sliderValue = value);
                addRenderableWidget(slider);
            }
            """),
    FRAME_TIMELINE("frame_timeline", 8, DEFAULT_HEIGHT + FrameTimelineGalleryPreview.PREVIEW_EXTRA, FrameTimelineGalleryPreview::new, """
            import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiTimeline;

            private final UiTimeline timeline = new UiTimeline(30, 120);
            private final UiTimeline.Sequence caption = new UiTimeline.Sequence(30, 90);

            private void play() { timeline.play(PageTransition.nowMs()); }
            private void pause() { timeline.pause(PageTransition.nowMs()); }
            private void seek(int frame) { timeline.seek(frame); }
            private void reset() { timeline.reset(); }

            private void drawFrame() {
                var frame = timeline.sample(PageTransition.nowMs());
                float x = frame.interpolate(0, 90, 0f, 100f);
                float opacity = frame.interpolate(0, 20, 0f, 1f);
                boolean showCaption = caption.contains(frame);
                int localFrame = caption.localFrame(frame);
                // Use x / opacity to draw; bind UiSlider to seek(frame).
                // Pause when the screen is removed. Reduced motion: seek(119).
            }
            """),
    ELECTRIC_LOGO("electric_logo", 9, DEFAULT_HEIGHT + ElectricLogoGalleryPreview.PREVIEW_EXTRA, ElectricLogoGalleryPreview::new, """
            import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogo;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
            import net.minecraft.client.gui.GuiGraphics;

            private final ElectricLogo logo = new ElectricLogo();

            private void drawLogo(GuiGraphics graphics, int x, int y, int width, int height,
                                  int mouseX, int mouseY) {
                logo.draw(graphics, x, y, width, height, mouseX, mouseY);
            }

            private void clickLogo(double mouseX, double mouseY) {
                logo.pulse(mouseX, mouseY);
            }

            private void changeLogo() {
                logo.setTexture(GraphicIcon.FAVORITE.texture());
            }

            @Override
            public void removed() {
                logo.close();
                super.removed();
            }
            """),
    LOCAL_MODAL("local_modal", 25, LocalModalGalleryPreview.HEIGHT, LocalModalGalleryPreview::new, """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiExpansionController;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlideConfirm;
            import net.minecraft.network.chat.Component;

            private final UiLocalModal local = ShBetterUiApi.interactions().localModal(this, UiPreferences.configured());

            private void addExpandButton(int x, int y, Runnable onConfirm) {
                var button = new UiButton(x, y, 62, 22,
                        Component.translatable("sh_better_ui.panel.developer.gallery.local_modal_left"),
                        source -> local.openSlide((UiButton) source, new Bounds(0, 0, width, height),
                                UiExpansionController.anchored(180, 22, 0, 0),
                                UiExpansionController.Config.DEFAULT, UiSlideConfirm.LocalConfig.DEFAULT,
                                Component.translatable("sh_better_ui.slide_confirm.label"), onConfirm));
                addRenderableWidget(button);
            }
            """),
    SLIDE_CONFIRM("slide_confirm", 13, ControlPreviewScenes.SlideConfirm::new, """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlideConfirm;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
            import net.minecraft.network.chat.Component;

            private void addSlideConfirm(int x, int y, Runnable onConfirm) {
                var prefs = UiPreferences.configured();
                var slide = addRenderableWidget(new UiSlideConfirm(
                        this, new Bounds(x, y, 164, 22), prefs, onConfirm));
                var reset = new UiButton(x + 170, y, 60, 22, Component.translatable("controls.reset"),
                        button -> slide.reset(), prefs, false, UiStyle.LIQUID_GLASS);
                reset.setLeadingIcon(GraphicIcon.RESET);
                addRenderableWidget(reset);
            }
            """),
    CAPSULE_TOGGLE("capsule_toggle", 14, ControlPreviewScenes.Capsule::new, """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
            import net.minecraft.network.chat.Component;

            private UiToggleButton capsule;
            private boolean enabled;

            private void addCapsule(int x, int y) {
                capsule = new UiToggleButton(new Bounds(x, y, 164, 22), enabled,
                        UiPreferences.configured(), UiStyle.LIQUID_GLASS,
                        value -> Component.translatable(value ? "options.on" : "options.off"),
                        value -> {
                            enabled = value;
                            capsule.setValue(enabled);
                        });
                capsule.setIcons(GraphicIcon.ENABLE, GraphicIcon.DISABLE);
                capsule.setModeIndicatorsVisible(true);
                addRenderableWidget(capsule.button());
            }
            """),
    MODE_INDICATORS("mode_indicators", 15, ControlPreviewScenes.Mode::new, """
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
            import net.minecraft.network.chat.Component;

            private int mode;

            private void addModes(int x, int y) {
                int modeCount = 3;
                var button = new UiButton(x, y, 164, 22, modeLabel(), pressed -> {
                    mode = (mode + 1) % modeCount;
                    var control = (UiButton) pressed;
                    control.setMessage(modeLabel());
                    control.setModeIndicators(modeCount, mode);
                }, UiPreferences.configured(), false, UiStyle.LIQUID_GLASS);
                button.setModeIndicators(modeCount, mode);
                addRenderableWidget(button);
            }

            private Component modeLabel() {
                return Component.translatable("sh_better_ui.panel.developer.gallery.mode", mode + 1);
            }
            """),
    PAGE_SLIDE("page_slide", 16, MotionPreviewScenes.HEIGHT, MotionPreviewScenes.PageSlide::new, """
            import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPageSlide;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.client.gui.GuiGraphics;

            private final UiPageSlide slide = new UiPageSlide();
            private UiPageSlide.Effects effects = UiPageSlide.Effects.NONE;

            private void beginSlide(Bounds viewport, UiPageSlide.Direction direction, boolean depth) {
                var mode = depth ? UiPageSlide.Motion.DEPTH : UiPageSlide.Motion.FLAT;
                effects = depth ? new UiPageSlide.Effects(true, true, GlassStyle.LIQUID_REGULAR)
                        : UiPageSlide.Effects.NONE;
                int distance = switch (direction) {
                    case LEFT, RIGHT -> viewport.width();
                    case UP, DOWN -> viewport.height();
                };
                slide.start(direction, mode, distance, PageTransition.nowMs(), UiPreferences.configured());
            }

            private void drawSlide(GuiGraphics graphics, Bounds viewport, Runnable drawPage) {
                var prefs = UiPreferences.configured();
                var frame = slide.sample(PageTransition.nowMs(), prefs);
                UiPageSlide.draw(graphics, viewport, frame, prefs, effects, () -> {
                    var pose = graphics.pose();
                    pose.pushPose();
                    try {
                        double depth = Math.abs(Math.sin(Math.toRadians(frame.yaw()))) * viewport.width()
                                + Math.abs(Math.sin(Math.toRadians(frame.pitch()))) * viewport.height();
                        if (depth > 0) pose.translate(0f, 0f, (float) (depth / 2 + 1));
                        frame.applyDepth(pose, viewport.x() + viewport.width() / 2f,
                                viewport.y() + viewport.height() / 2f);
                        drawPage.run();
                    } finally {
                        pose.popPose();
                    }
                });
            }
            """),
    RECIPE_TRANSITION("recipe_transition", 17, MotionPreviewScenes.HEIGHT, MotionPreviewScenes.RecipeSlide::new, """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPageSlide.Direction;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiRecipePageTransition;
            import net.minecraft.client.gui.GuiGraphics;

            private final UiRecipePageTransition transition = new UiRecipePageTransition();

            private void beginSwitch(Bounds viewport, Direction direction) {
                transition.start(direction, viewport, PageTransition.nowMs(), UiPreferences.configured());
            }

            private void drawPages(GuiGraphics graphics, Bounds viewport, Runnable oldPage, Runnable newPage) {
                var prefs = UiPreferences.configured();
                var frame = transition.sample(PageTransition.nowMs(), prefs);
                UiRecipePageTransition.draw(graphics, viewport, frame, prefs, oldPage, newPage);
            }
            """),
    DYNAMIC_STATUS("dynamic_status", 18, MotionPreviewScenes.DynamicStatus::new, """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback.Status;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiDynamicStatus;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.client.gui.GuiGraphics;

            private UiDynamicStatus.Snapshot current = new UiDynamicStatus.Snapshot(Status.READY);
            private final UiDynamicStatus indicator = new UiDynamicStatus(() -> current);

            private void onProgress(int percent) {
                current = new UiDynamicStatus.Snapshot(Status.PENDING, percent);
            }

            private void onCompleted() {
                current = new UiDynamicStatus.Snapshot(Status.SUCCEEDED);
            }

            private void drawStatus(GuiGraphics graphics, Bounds area) {
                indicator.render(graphics, font, area, UiPreferences.configured());
            }
            """),
    LOADING_SPINNER("loading_spinner", 19, MotionPreviewScenes.HEIGHT, MotionPreviewScenes.Spinner::new, """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiLoading;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.client.gui.GuiGraphics;

            private void drawListLoading(GuiGraphics graphics, Bounds viewport,
                                         boolean refreshing, boolean loadingMore) {
                var prefs = UiPreferences.configured();
                long now = PageTransition.nowMs();
                if (refreshing) UiLoading.edgeSpinner(graphics, viewport, UiLoading.Edge.TOP, now, prefs);
                if (loadingMore) UiLoading.edgeSpinner(graphics, viewport, UiLoading.Edge.BOTTOM, now, prefs);
            }

            private void drawOrganizing(GuiGraphics graphics, Bounds iconArea, float progress) {
                UiLoading.spinner(graphics, iconArea, progress, UiPreferences.configured());
            }
            """),
    LOADING_PROGRESS("loading_progress", 20, MotionPreviewScenes.HEIGHT, MotionPreviewScenes.Progress::new, """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiLoading;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.client.gui.GuiGraphics;

            private void drawTransfer(GuiGraphics graphics, Bounds track, Bounds viewport,
                                      long transferred, long total) {
                var prefs = UiPreferences.configured();
                if (total > 0) {
                    float progress = (float) ((double) transferred / total);
                    UiLoading.progressBar(graphics, track, progress, prefs);
                    UiLoading.topBar(graphics, viewport, progress, prefs);
                } else {
                    UiLoading.topBar(graphics, viewport, PageTransition.nowMs(), prefs);
                }
            }
            """),
    LOADING_SKELETON("loading_skeleton", 21, MotionPreviewScenes.HEIGHT, MotionPreviewScenes.Skeleton::new, """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton;
            import net.minecraft.client.gui.GuiGraphics;

            private void drawCard(GuiGraphics graphics, Bounds area, boolean loading, Runnable drawContent) {
                if (loading) {
                    UiSkeleton.card(graphics, area, PageTransition.nowMs(), UiPreferences.configured());
                } else {
                    drawContent.run();
                }
            }
            """),
    SEARCH("search", 22, ControlPreviewScenes.Search::new, """
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
            import com.dishanhai.sh_better_ui.client.gui.search.SearchField;
            import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
            import net.minecraft.client.gui.GuiGraphics;
            import net.minecraft.network.chat.Component;
            import org.lwjgl.glfw.GLFW;
            import java.util.List;

            private SearchField<String> search;

            private void addSearch(int x, int y) {
                var index = new SearchIndex<>(List.of(new SearchIndex.Entry<>(
                        "minecraft:apple", Component.translatable("item.minecraft.apple").getString(),
                        List.of("minecraft:apple"), List.of())));
                search = addRenderableWidget(new SearchField<>(font, x, y, 160, 22,
                        index, UiStyle.LIQUID_GLASS, UiPreferences.configured()));
            }

            @Override
            public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                super.render(graphics, mouseX, mouseY, partialTick);
                search.renderSuggestions(graphics, mouseX, mouseY, height);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                return search.mouseClickedSuggestion(mouseX, mouseY, button, height)
                        || super.mouseClicked(mouseX, mouseY, button);
            }

            @Override
            public boolean keyPressed(int key, int scan, int modifiers) {
                return (key == GLFW.GLFW_KEY_ESCAPE && search.dismissSuggestions())
                        || super.keyPressed(key, scan, modifiers);
            }
            """),
    DIALOG("dialog", 23, () -> ControlPreviewScenes::dialog, DeveloperPanelScreen.DIALOG_CODE),
    TOAST("toast", 24, 64, ControlPreviewScenes.Toast::new, DeveloperPanelScreen.TOAST_CODE),
    MUSIC_HUD("music_hud", MATERIALS, 13, MusicHudGalleryPreview.HEIGHT, 0, MusicHudGalleryPreview::new, """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.api.UiFrame;
            import com.dishanhai.sh_better_ui.client.audio.AudioFrame;
            import com.dishanhai.sh_better_ui.client.audio.MusicRhythm;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.MusicHud;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;

            private final MusicRhythm rhythm = ShBetterUiApi.motion().musicRhythm();

            private void drawHud(UiFrame ui, AudioFrame audio, Bounds scope, UiButton button) {
                var prefs = ui.preferences();
                var frame = rhythm.sample(audio, ui.nowMs(), prefs.reducedMotion() || prefs.highContrast());
                MusicHud.drawPanel(ui.graphics(), ui.viewport(), frame, prefs);
                MusicHud.drawScope(ui.graphics(), scope, frame, prefs);
                button.setIconOffsetY(-Math.round(2 * frame.lift(0.4f)));
                // Draw text and controls after the decorations.
            }
            """),
    MUSIC_ISLAND("music_island", MATERIALS, 14, MusicIslandGalleryPreview.HEIGHT, 0, MusicIslandGalleryPreview::new, """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.audio.AudioFrame;
            import com.dishanhai.sh_better_ui.client.audio.MusicRhythm;
            import com.dishanhai.sh_better_ui.client.gui.scaled.MusicIsland;

            private final MusicRhythm rhythm = ShBetterUiApi.motion().musicRhythm();

            // Keep compactBounds and waveBounds fixed while playing.
            var prefs = ui.preferences();
            var motion = rhythm.sample(playing ? audio : AudioFrame.silence(ui.nowMs()),
                    ui.nowMs(), prefs.reducedMotion() || prefs.highContrast());
            var outline = MusicIsland.outline(compactBounds, panelBounds, expansionProgress);
            MusicIsland.drawShell(ui.graphics(), outline, prefs, hovered);
            MusicIsland.drawWaveform(ui.graphics(), waveBounds, motion, trackAccent, prefs);
            // Draw localized metadata and native playback controls on top.
            """),
    LOCAL_MUSIC("local_music", MATERIALS, 18, LocalMusicGalleryPreview.HEIGHT, 0, LocalMusicGalleryPreview::new, """
            var card = ShBetterUiApi.music().island();
            card.setExpanded(true, ui.nowMs(), ui.preferences().reducedMotion());
            card.render(ui, font, compactBounds, panelBounds, hovered);
            """),
    LYRICS("lyrics", MATERIALS, 15, LyricGalleryPreview.HEIGHT, 0, LyricGalleryPreview::new, """
            // Adapted from Link-Lyric by beipuo
            // https://github.com/beipuo/Link-Lyric
            import com.dishanhai.sh_better_ui.client.api.LyricApi;
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.api.UiFrame;
            import com.dishanhai.sh_better_ui.client.lyrics.common.hud.FloatingLyricRenderer;
            import com.dishanhai.sh_better_ui.client.lyrics.common.hud.LocalLyricFeed;
            import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
            import com.dishanhai.sh_better_ui.client.lyrics.core.sync.TrackIdentity;
            import net.minecraft.client.gui.Font;
            import net.minecraft.util.FormattedCharSequence;

            private final LyricApi lyrics = ShBetterUiApi.lyrics();
            private final LocalLyricFeed feed = lyrics.localFeed("example:player");
            private final FloatingLyricRenderer renderer = lyrics.renderer(feed);

            private void drawLyrics(UiFrame frame, Font font, TrackIdentity track,
                                    LyricTimeline timeline, long positionMs, boolean playing) {
                feed.publish(track, timeline, positionMs, playing);
                lyrics.draw(renderer, frame, font, frame.viewport());
            }

            private FormattedCharSequence colorize(FormattedCharSequence text, float progress) {
                return ShBetterUiApi.graphics().colorizeLyricText(text, progress, 0x7FDBFF, 0x91A4BE);
            }
            """),
    DOT_MATRIX("dot_matrix", MATERIALS, 0, DEFAULT_HEIGHT + DotMatrixGalleryPreview.PREVIEW_EXTRA,
            DotMatrixGalleryPreview.TOOLBAR_INSET, DotMatrixGalleryPreview::new, """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotMatrix;
            import net.minecraft.client.gui.GuiGraphics;
            import java.util.List;

            private final UiDotMatrix dots = new UiDotMatrix();

            private void drawDots(GuiGraphics graphics, Bounds area, int mouseX, int mouseY) {
                dots.render(graphics, area, mouseX, mouseY);
            }

            private void pulseDots(Bounds area, double x, double y) {
                dots.pulse(area, x, y);
            }

            private void updateDots(int backgroundArgb, int dotArgb, int highlightArgb,
                                    DotMatrixProfile.Effect effect, int floatPercent, int maxFloatingDots,
                                    int fadePercent, int gradientPeriodMs) {
                var base = DotMatrixProfile.VIOLET;
                var motion = base.motion();
                var gradient = new DotMatrixProfile.Gradient(List.of(
                        new DotMatrixProfile.ColorStop(0f, 0xFFFF71AA),
                        new DotMatrixProfile.ColorStop(0.22f, 0xC079D5FF),
                        new DotMatrixProfile.ColorStop(0.66f, 0xFFB2FFB5)),
                        gradientPeriodMs, (x, y, phase) -> phase - x * x - 0.35f * y);
                dots.setProfile(base
                        .withPalette(new DotMatrixProfile.Palette(backgroundArgb, dotArgb, highlightArgb,
                                base.palette().glow()))
                        .withMotion(new DotMatrixProfile.Motion(effect, motion.periodMs(), floatPercent / 100f,
                                maxFloatingDots, motion.floatDistance()))
                        .withFade(new DotMatrixProfile.Fade(fadePercent / 100f, 2200))
                        .withPointerWave(DotMatrixProfile.PointerWave.DEFAULT)
                        .withGradient(gradient));
            }
            """),
    ANTIGRAVITY("antigravity", 11, DEFAULT_HEIGHT + AntigravityGalleryPreview.PREVIEW_EXTRA, AntigravityGalleryPreview::new, """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.Antigravity;
            import com.dishanhai.sh_better_ui.client.gui.scaled.AntigravityProfile;
            import net.minecraft.client.gui.GuiGraphics;

            private final Antigravity particles = ShBetterUiApi.graphics().antigravity(
                    AntigravityProfile.builder().count(1500).ringRadius(7).magnetRadius(9).build());

            private void drawParticles(GuiGraphics graphics, Bounds area, boolean inputAllowed) {
                var mouse = inputAllowed ? ShBetterUiApi.interactions().mouseState() : null;
                particles.draw(graphics, area, mouse, ShBetterUiApi.settings().preferences());
            }

            private void changeShape() {
                particles.setProfile(particles.profile().toBuilder()
                        .particleShape(AntigravityProfile.Shape.SPHERE).build());
            }

            // 键盘动作可调用 particles.burst(1)。离页时停止采样，保留位置。
            @Override
            public void removed() {
                particles.suspend();
                super.removed();
            }
            """),
    LIGHT_RAYS("light_rays", 12, DEFAULT_HEIGHT + LightRaysGalleryPreview.PREVIEW_EXTRA, LightRaysGalleryPreview::new, """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.LightRays;
            import com.dishanhai.sh_better_ui.client.gui.scaled.LightRaysProfile;
            import net.minecraft.client.gui.GuiGraphics;

            private final LightRaysProfile.Builder settings = LightRaysProfile.builder();
            private final LightRays rays = ShBetterUiApi.graphics().lightRays(settings.build());

            private void changePreview(boolean lightBoard, int spreadPercent) {
                rays.setProfile(settings.lightMode(lightBoard).lightSpread(spreadPercent / 100f).build());
            }

            private void drawRays(GuiGraphics graphics, Bounds area, boolean inputAllowed) {
                int color = rays.profile().lightMode() ? 0xFFF0F4FF : 0xFF0B1220;
                graphics.fill(area.x(), area.y(), area.right(), area.bottom(), color);
                var mouse = inputAllowed ? ShBetterUiApi.interactions().mouseState() : null;
                rays.draw(graphics, area, mouse, ShBetterUiApi.settings().preferences());
            }

            @Override
            public void removed() {
                rays.suspend();
                super.removed();
            }
            """),
    PATTERN_WAVES("pattern_waves", 10, DEFAULT_HEIGHT + PatternWavesGalleryPreview.PREVIEW_EXTRA, PatternWavesGalleryPreview::new, """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PatternWaves;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PatternWavesProfile;
            import net.minecraft.client.gui.GuiGraphics;

            private final PatternWaves waves = ShBetterUiApi.motion().patternWaves();

            private void drawWaves(GuiGraphics graphics, Bounds area) {
                var mouse = ShBetterUiApi.interactions().mouseState();
                waves.draw(graphics, area, mouse, ShBetterUiApi.settings().preferences());
            }

            @Override
            public boolean mouseClicked(double x, double y, int button) {
                waves.mousePressed(x, y, button);
                return super.mouseClicked(x, y, button);
            }

            private void selectPreset(PatternWavesProfile.Preset preset) {
                waves.setProfile(preset.profile());
            }

            private void togglePause() { waves.setPaused(!waves.isPaused()); }
            """),
    LIQUID_BUTTON("liquid_button", MATERIALS, 1, UiStyle.LIQUID_GLASS),
    DARK_BUTTON("dark_button", MATERIALS, 2, UiStyle.DEFAULT),
    GRADIENT_CARD("gradient_card", MATERIALS, 3, MaterialPreviewScenes.GradientCard::new, """
            import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.client.gui.GuiGraphics;

            private final DarkModeTheme.Interaction cardMotion = DarkModeTheme.Interaction.card();

            private void drawCard(GuiGraphics graphics, int x, int y, int width, int height, Runnable drawContent) {
                var state = cardMotion.sample(false, false, false, true,
                        UiPreferences.configured(), PageTransition.nowMs());
                DarkModeTheme.card(graphics, x, y, width, height, state, drawContent);
            }
            """),
    NETWORK_TAB("network_tab", MATERIALS, 4, UiStyle.NETWORK),
    CLEAR_GLASS("clear_glass", 5, GlassStyle.CLEAR, "CLEAR"),
    FROSTED_GLASS("frosted_glass", 6, GlassStyle.FROSTED, "FROSTED"),
    RESET_ICON("reset_icon", 7, GraphicIcon.RESET, "RESET"),
    SETTINGS_ICON("settings_icon", 8, GraphicIcon.SETTINGS, "SETTINGS"),
    COPY_ICON("copy_icon", 9, GraphicIcon.COPY, "COPY"),
    STATUS_ICON("status_icon", MATERIALS, 10, MaterialPreviewScenes.IconMotion::new, """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicIconMotion;

            GraphicIcon.STATUS_PENDING.withMotion(GraphicIconMotion.SPIN).draw(graphics, x, y, 2, 0xFFFFFFFF);
            GraphicIcon.STATUS_PENDING.withMotion(GraphicIconMotion.FLOAT).draw(graphics, x + 40, y, 2, 0xFFFFFFFF);
            GraphicIcon.STATUS_PENDING.withMotion(GraphicIconMotion.BOUNCE).draw(graphics, x + 80, y, 2, 0xFFFFFFFF);
            """),
    CODE_RAIN("code_rain", MATERIALS, 16, MaterialPreviewScenes.EFFECT_HEIGHT, 0,
            MaterialPreviewScenes.CodeRainPreview::new, """
            import com.dishanhai.sh_better_ui.client.gui.scaled.CodeRain;
            import com.dishanhai.sh_better_ui.client.gui.scaled.CodeRainEffects;
            import com.dishanhai.sh_better_ui.client.gui.scaled.CodeRainProfile;

            private final CodeRain rain = new CodeRain(CodeRainProfile.DEFAULT,
                    CodeRainEffects.interaction(true, CodeRainEffects.Click.WAVE, .18f));

            // 在底板之后、前景控件之前调用；关闭画面时调用 rain.suspend()。
            rain.draw(graphics, font, bounds);
            """),
    LASER_FLOW("laser_flow", MATERIALS, 17, MaterialPreviewScenes.EFFECT_HEIGHT, 0,
            MaterialPreviewScenes.LaserFlowPreview::new, """
            import com.dishanhai.sh_better_ui.client.gui.scaled.LaserFlow;
            import com.dishanhai.sh_better_ui.client.gui.scaled.LaserFlowProfile;
            import com.dishanhai.sh_better_ui.client.gui.scaled.LaserFlowSurface;

            private final LaserFlow laser = new LaserFlow(
                    LaserFlowProfile.builder().mouseInteraction(true).build());

            // cardBounds 与 bounds 使用同一 GUI 坐标系。
            laser.draw(graphics, bounds, new LaserFlowSurface(cardBounds, 6), null);
            """);

    final PreviewSpec spec;

    DeveloperSamples(String key, int order, Supplier<? extends PreviewScene> factory, String code) {
        this(key, CONTROLS, order, DEFAULT_HEIGHT, 0, factory, code);
    }

    DeveloperSamples(String key, int order, int height, Supplier<? extends PreviewScene> factory, String code) {
        this(key, CONTROLS, order, height, 0, factory, code);
    }

    DeveloperSamples(String key, Category category, int order, Supplier<? extends PreviewScene> factory, String code) {
        this(key, category, order, DEFAULT_HEIGHT, 0, factory, code);
    }

    DeveloperSamples(String key, Category category, int order, UiStyle style) {
        this(key, category, order, () -> new MaterialPreviewScenes.StyledButton(style), """
                import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
                import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
                import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
                import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
                import net.minecraft.network.chat.Component;

                private void addButton(int x, int y) {
                    var button = new UiButton(x, y, 140, 22,
                            Component.translatable("gui.done"),
                            pressed -> {
                                var control = (UiButton) pressed;
                                control.setSelected(!control.isSelected());
                            },
                            UiPreferences.configured(), false, UiStyle.%s);
                    button.setLeadingIcon(GraphicIcon.RESET);
                    addRenderableWidget(button);
                }
                """.formatted(style.name()));
    }

    DeveloperSamples(String key, int order, GlassStyle style, String constant) {
        this(key, MATERIALS, order, () -> new MaterialPreviewScenes.Glass(style), """
                import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
                import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
                import com.dishanhai.sh_better_ui.client.glass.GuiRect;
                import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
                import net.minecraft.client.gui.GuiGraphics;

                private void drawMaterial(GuiGraphics graphics, int x, int y, int w, int h) {
                    var prefs = UiPreferences.configured();
                    if (prefs.useBackdropBlur()) {
                        GlassBlur.tryBackdrop(graphics, new GuiRect(x, y, w, h),
                                1f, GlassStyle.%s, 12f, 4f);
                    }
                }
                """.formatted(constant));
    }

    DeveloperSamples(String key, int order, GraphicIcon icon, String constant) {
        this(key, MATERIALS, order, () -> new MaterialPreviewScenes.Icon(icon), """
                import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
                import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
                import net.minecraft.client.gui.GuiGraphics;

                private void drawIcon(GuiGraphics graphics, int x, int y) {
                    GraphicIcon.%s.draw(graphics, x, y, 2, 0xFFFFFFFF,
                            UiPreferences.configured());
                }
                """.formatted(constant));
    }

    DeveloperSamples(String key, Category category, int order, int height, int toolbarInset,
                     Supplier<? extends PreviewScene> factory, String code) {
        spec = new PreviewSpec("sh_better_ui:" + key, category, order,
                "sh_better_ui.panel.developer.gallery.sample." + key, height, toolbarInset, code, factory);
    }
}
