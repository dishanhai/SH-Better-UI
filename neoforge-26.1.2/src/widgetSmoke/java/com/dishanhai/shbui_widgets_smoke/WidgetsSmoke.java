// Author: dishanhai
package com.dishanhai.shbui_widgets_smoke;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.config.TabScrollSettings;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Mod(WidgetsSmoke.ID)
@EventBusSubscriber(modid = WidgetsSmoke.ID, value = Dist.CLIENT)
public final class WidgetsSmoke {
    static final String ID = "shbui_widgets_smoke";
    private static final int BACKGROUND = 0xFF111827;
    private static final int GREEN = 0xFF00FF00;
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static Probe screen;
    private static boolean started;
    private static int renderedFrames;
    private static int frames;
    private static boolean requested;
    private static boolean widgetsPassed;

    @SubscribeEvent
    public static void start(ClientStartedEvent event) {
        started = true;
    }

    @SubscribeEvent
    public static void capture(RenderFrameEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (!started) return;
        if (Boolean.getBoolean("shbui.widgetSmoke.readability")) {
            ReadabilitySmoke.frame(minecraft);
            return;
        }
        if (System.getProperty("shbui.widgetSmoke.layoutScale") != null) {
            if (LayoutSmoke.frame(minecraft)) EntrySmoke.frame(minecraft);
            return;
        }
        if (widgetsPassed) {
            if (EffectsSmoke.frame(minecraft) && GallerySmoke.frame(minecraft)) EntrySmoke.frame(minecraft);
            return;
        }
        if (++frames > 3_600) throw new IllegalStateException("Widget smoke timed out");
        if (WidgetSmokePlatform.hasOverlay(minecraft)) return;
        if (screen == null) {
            String backend = WidgetSmokePlatform.backend();
            require(backend.equalsIgnoreCase(System.getProperty("shbui.widgetSmoke.expectedBackend")),
                    "graphics backend " + backend);
            String expectedPreference = System.getProperty("shbui.widgetSmoke.expectedPreference");
            if (expectedPreference != null) {
                require(expectedPreference.equals(WidgetSmokePlatform.backendPreference(minecraft)),
                        "graphics preference after native fallback");
            }
            minecraft.options.guiScale().set(2);
            screen = new Probe();
            WidgetSmokePlatform.open(minecraft, screen);
            return;
        }
        if (requested || renderedFrames < 2) return;
        requested = true;
        int scale = minecraft.getWindow().getGuiScale();
        require(scale == 2, "GUI scale");
        Screenshot.takeScreenshot(WidgetSmokePlatform.target(minecraft), image -> {
            try (image) {
                image.writeToFile(minecraft.gameDirectory.toPath().resolve("widget-smoke.png"));
                checkPixel(image, scale, 300, 31, BACKGROUND, "rounded corner");
                checkPixel(image, scale, 320, 31, RED, "rounded inset stroke");
                checkPixel(image, scale, 320, 48, GREEN, "rounded interior");
                checkPixel(image, scale, 370, 45, BACKGROUND, "hollow stroke interior");
                checkPixel(image, scale, 320, 75, BLUE, "inside scissor");
                checkPixel(image, scale, 344, 75, BACKGROUND, "outside scissor");
                require(regionHasColor(image, scale, 19, 22, 135, 50), "native button and text");
                require(regionHasColor(image, scale, 145, 22, 259, 50), "icon toggle");
                require(regionHasColor(image, scale, 19, 52, 259, 80), "slider");
                require(regionHasColor(image, scale, 19, 82, 259, 121), "stepper");
                require(regionHasColor(image, scale, 19, 121, 259, 198), "tab and section rows");
                System.out.println("SHBUI_WIDGET_SMOKE_PASS backend=" + WidgetSmokePlatform.backend() + " scale=" + scale);
                widgetsPassed = true;
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            } finally {
                if (!widgetsPassed) minecraft.stop();
            }
        });
    }

    private static void checkPixel(NativeImage image, int scale, int x, int y, int expected, String name) {
        int actual = image.getPixel(x * scale, y * scale);
        require((actual & 0xFFFFFF) == (expected & 0xFFFFFF),
                name + " expected=" + Integer.toHexString(expected) + " actual=" + Integer.toHexString(actual));
    }

    private static boolean regionHasColor(NativeImage image, int scale, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if ((image.getPixel(x * scale, y * scale) & 0xFFFFFF) != (BACKGROUND & 0xFFFFFF)) return true;
            }
        }
        return false;
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("Widget smoke: " + name);
    }

    private static final class Probe extends Screen {
        private int presses;
        private int commit = -1;
        private int cancel = -1;
        private boolean requestedToggle;
        private UiSlider slider;

        Probe() { super(Component.translatable("options.title")); }

        @Override
        protected void init() {
            var controls = ShBetterUiApi.controls();
            var prefs = new UiPreferences(true, true, false);
            var button = addRenderableWidget(controls.button(new Bounds(20, 24, 112, 22),
                    Component.translatable("gui.done"), ignored -> presses++, prefs, true, UiStyle.DEFAULT));
            button.setLeadingIcon(GraphicIcon.SUCCESS);
            var toggle = controls.toggle(new Bounds(144, 24, 112, 22), false, prefs, UiStyle.LIQUID_GLASS,
                    value -> Component.translatable(value ? "options.on" : "options.off"),
                    value -> requestedToggle = value);
            toggle.setIcons(GraphicIcon.SUCCESS, GraphicIcon.CLOSE);
            toggle.setModeIndicatorsVisible(true);
            addRenderableWidget(toggle.button());
            slider = addRenderableWidget(controls.slider(new Bounds(20, 54, 236, 22),
                    0, 10, 3, 3, prefs,
                    value -> Component.translatable("sh_better_ui.panel.developer.gallery.slider_value", value * 10),
                    ignored -> {}));
            slider.setOnCommit(value -> commit = value, value -> cancel = value);
            var stepper = addRenderableWidget(controls.stepper(new Bounds(20, 84, 236, 36), 2, ignored -> {}));
            var tabs = addRenderableWidget(controls.underlinedTabs(new Bounds(20, 122, 236, 32), ignored -> {}));
            var sections = List.of(
                    new UiSectionBar.Section(Component.translatable("options.title"), GraphicIcon.INFO),
                    new UiSectionBar.Section(Component.translatable("gui.done"), GraphicIcon.SUCCESS),
                    new UiSectionBar.Section(Component.translatable("gui.cancel"), GraphicIcon.CLOSE));
            var bar = controls.sectionBar(new Bounds(20, 156, 236, 20), prefs, sections, 0, ignored -> {});
            bar.buttons().forEach(this::addRenderableWidget);
            var scrolling = addRenderableWidget(controls.scrollableSectionBar(new Bounds(20, 180, 152, 20),
                    prefs, sections, 0, ignored -> {}, TabScrollSettings.DEFAULT, 82));
            var disclosure = controls.disclosure(false);
            var disclosureButton = addRenderableWidget(disclosure.button(178, 180, 78, 20,
                    Component.translatable("gui.proceed"), Component.translatable("gui.back"), prefs, ignored -> {}));

            setInitialFocus(button);
            require(keyPressed(key(GLFW.GLFW_KEY_ENTER)) && presses == 1, "keyboard button activation");
            click(190, 33);
            require(requestedToggle && !toggle.value(), "toggle waits for confirmed state");
            toggle.setValue(true);
            require(toggle.button().isSelected() && toggle.button().getSelectedModeIndex() == 1, "confirmed toggle");
            mouseClicked(mouse(250, 65), false);
            mouseDragged(mouse(270, 65), 20, 0);
            require(slider.value() == 10 && commit == -1, "slider drag preview");
            require(keyPressed(key(GLFW.GLFW_KEY_ESCAPE)) && slider.value() == 3 && cancel == 3, "slider cancel");
            mouseReleased(mouse(270, 65));
            click(250, 65);
            require(slider.value() == 10 && commit == 10, "slider commit on release");
            setInitialFocus(stepper);
            require(keyPressed(key(GLFW.GLFW_KEY_LEFT)) && stepper.progress().currentIndex() == 1, "stepper keys");
            setInitialFocus(tabs);
            require(keyPressed(key(GLFW.GLFW_KEY_RIGHT)) && tabs.selection().selectedIndex() == 1, "tab keys");
            require(mouseScrolled(45, 190, 0, -1) && scrolling.scrollOffset() > 0, "section wheel");
            setInitialFocus(disclosureButton);
            require(keyPressed(key(GLFW.GLFW_KEY_ENTER)) && disclosure.expanded(), "disclosure activation");
            setInitialFocus(button);
        }

        private void click(int x, int y) {
            mouseClicked(mouse(x, y), false);
            mouseReleased(mouse(x, y));
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            return (event.isEscape() && getFocused() == slider && slider.keyPressed(event)) || super.keyPressed(event);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, width, height, BACKGROUND);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            RoundedCorners.draw(graphics, 300, 30, 40, 36, 12, 3, RED, GREEN);
            RoundedCorners.draw(graphics, 354, 30, 32, 30, 12, 3, RED, 0);
            graphics.enableScissor(305, 70, 335, 85);
            try {
                RoundedCorners.draw(graphics, 300, 68, 55, 25, 8, 0, 0, BLUE);
            } finally {
                graphics.disableScissor();
            }
            renderedFrames++;
        }

        private static MouseButtonEvent mouse(double x, double y) {
            return new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        }

        private static KeyEvent key(int key) {
            return new KeyEvent(key, 0, 0);
        }
    }
}
