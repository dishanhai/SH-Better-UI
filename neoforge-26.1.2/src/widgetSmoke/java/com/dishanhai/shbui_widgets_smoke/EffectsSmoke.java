// Author: dishanhai
package com.dishanhai.shbui_widgets_smoke;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/** Exercises the packaged effects and native settings screen with GPU and input assertions. */
final class EffectsSmoke {
    private static final int DARK = 0xFF202020;
    private static final int LIGHT = 0xFFE0E0E0;
    private static final int MARK = 0xFFFF4100;
    private static final int HIGH_CONTRAST_FILL = 0xFF274362;
    private static final UiPreferences OPTICAL = new UiPreferences(false, true, true);
    private static final UiPreferences CONTRAST = new UiPreferences(true, true, true);
    private static final GlassOptics CLEAR = new GlassOptics(1, 0, 0, 0, 0, 4, 0);
    private static final GlassOptics SOFT = new GlassOptics(1, 0, 0, 0, 0, 4, 6);
    private static final GlassOptics LENS = new GlassOptics(1, 8, 0, 0, 0, 4, 0);
    private static int stage;
    private static int frames;
    private static boolean capturePending;
    private static boolean captured;
    private static boolean complete;
    private static Screen screen;
    private static CompletableFuture<Void> reload;

    private EffectsSmoke() {}

    static boolean frame(Minecraft minecraft) {
        if (complete) return true;
        if (screen == null || captured) {
            if (captured) stage++;
            captured = false;
            capturePending = false;
            frames = 0;
            switch (stage) {
                case 0 -> {
                    minecraft.options.menuBackgroundBlurriness().set(8);
                    screen = new PatternScreen(false);
                    WidgetSmokePlatform.open(minecraft, screen);
                }
                case 1 -> {
                    screen = new PatternScreen(true);
                    WidgetSmokePlatform.open(minecraft, screen);
                }
                case 2 -> {
                    ShBetterUiApi.settings().openControlPanel();
                    screen = WidgetSmokePlatform.screen(minecraft);
                    require(screen instanceof ControlPanelScreen, "native panel opens");
                }
                case 3 -> {
                    verifyTools(minecraft, (ControlPanelScreen) screen);
                    minecraft.options.guiScale().set(1);
                    reload = minecraft.reloadResourcePacks();
                }
                case 4 -> {
                    require(minecraft.getWindow().getGuiScale() == 1, "overview rendered at GUI Scale 1");
                    var panel = (ControlPanelScreen) screen;
                    verifyOverviewBackend(panel);
                    panel.onClose();
                    require(focused(panel, "section.overview"), "overview returns to its tools entry after reload");
                    minecraft.options.guiScale().set(2);
                    verifyPanel(minecraft);
                    complete = true;
                    return true;
                }
                default -> throw new IllegalStateException("Unknown smoke stage");
            }
            return false;
        }
        if (++frames > 900) throw new IllegalStateException("Effects smoke timed out at stage " + stage);
        if (capturePending || frames < 5 || WidgetSmokePlatform.screen(minecraft) != screen
                || WidgetSmokePlatform.hasOverlay(minecraft)) return false;
        if (stage == 3) {
            if (!reload.isDone()) return false;
            reload.join();
        }
        capturePending = true;
        Screenshot.takeScreenshot(WidgetSmokePlatform.target(minecraft), image -> {
            try (image) {
                String name = switch (stage) {
                    case 0 -> "background-blur";
                    case 1 -> "liquid-glass";
                    case 2 -> "control-panel";
                    case 3 -> "control-panel-overview";
                    default -> throw new IllegalStateException("Unknown capture");
                };
                image.writeToFile(minecraft.gameDirectory.toPath().resolve(name + ".png"));
                int scale = minecraft.getWindow().getGuiScale();
                if (stage == 0) verifyBlur(image, scale);
                if (stage == 1) verifyGlass(image, scale);
                captured = true;
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            } finally {
                if (!captured) minecraft.stop();
            }
        });
        return false;
    }

    private static void verifyBlur(NativeImage image, int scale) {
        double contrast = contrast(image, scale, 26, 106, 50);
        require(contrast < 80, "menu blur suppresses high-frequency stripes: " + contrast);
        exact(image, scale, 25, 102, MARK, "foreground drawn after blur");
        System.out.println("SHBUI_BACKGROUND_BLUR_PASS stripeContrast=" + contrast);
    }

    private static void verifyGlass(NativeImage image, int scale) {
        double outside = contrast(image, scale, 26, 106, 116);
        double blurred = contrast(image, scale, 26, 106, 50);
        require(outside > 175, "capture preserves unfiltered background: " + outside);
        require(blurred < outside * 0.75, "local optical blur: " + blurred + " vs " + outside);
        exact(image, scale, 16, 24, DARK, "glass rounded corner");
        exact(image, scale, 158, 50, LIGHT, "clear pane transmits same-frame pixel");
        exact(image, scale, 25, 102, MARK, "foreground drawn after glass");
        exact(image, scale, 176, 143, HIGH_CONTRAST_FILL, "high-contrast opaque fallback");
        exact(image, scale, 371, 55, DARK, "scissor excludes right side of optical pane");
        double button = contrast(image, scale, 30, 102, 139);
        require(button > 18, "prepared backdrop reaches liquid glass button: " + button);
        int shifted = 0;
        for (int y = 40; y < 62; y++) {
            for (int x = 241; x < 255; x++) {
                int original = channel(image, scale, x, 116);
                if (Math.abs(channel(image, scale, x, y) - original) > 36) shifted++;
            }
        }
        require(shifted > 20, "lens shifts sampled stripe positions: " + shifted);
        System.out.println("SHBUI_LIQUID_GLASS_PASS blurContrast=" + blurred + " lensShiftPixels=" + shifted);
    }

    private static void verifyPanel(Minecraft minecraft) {
        var settings = ShBetterUiApi.settings();
        var panel = (ControlPanelScreen) screen;
        var original = settings.current();
        var next = original.theme().mode() == ThemeSettings.Mode.LIQUID_GLASS
                ? ThemeSettings.Mode.DARK : ThemeSettings.Mode.LIQUID_GLASS;
        click(panel, "section.appearance");
        click(panel, next == ThemeSettings.Mode.DARK ? "theme.dark" : "theme.liquid_glass");
        var focus = ((AbstractWidget) panel.getFocused()).getMessage();
        require(settings.current().equals(original), "draft keeps applied settings");
        panel.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        require(WidgetSmokePlatform.screen(minecraft) instanceof ConfirmScreen, "dirty close asks before discard");
        var confirmation = WidgetSmokePlatform.screen(minecraft);
        clickMessage(confirmation, CommonComponents.GUI_CANCEL);
        require(WidgetSmokePlatform.screen(minecraft) == panel, "cancel returns to draft");
        require(panel.getFocused() instanceof AbstractWidget focused && focused.getMessage().equals(focus),
                "cancel restores original control focus");
        click(panel, "apply");
        require(settings.current().theme().mode() == next && UiTheme.current().liquidGlass()
                        == (next == ThemeSettings.Mode.LIQUID_GLASS),
                "apply updates runtime palette");
        var properties = new Properties();
        var file = minecraft.gameDirectory.toPath().resolve("config/sh_better_ui/control-panel.properties");
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        require(next.name().equals(properties.getProperty("theme")), "theme saved in profile");
        panel.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        require(WidgetSmokePlatform.screen(minecraft) instanceof PatternScreen, "saved panel returns to parent");
        System.out.println("SHBUI_CONTROL_PANEL_PASS backend=" + WidgetSmokePlatform.backend());
    }

    private static void verifyTools(Minecraft minecraft, ControlPanelScreen panel) {
        var original = ShBetterUiApi.settings().current();
        click(panel, "section.display");
        click(panel, "motion");
        require(find(panel, "apply").active, "display edit creates a draft");
        click(panel, "section.tools");
        var mapping = KeyMapping.get("key.sh_better_ui.control_panel");
        require(mapping != null, "panel shortcut is registered in vanilla");
        var oldKey = mapping.getKey();
        var oldModifier = mapping.getKeyModifier();
        click(panel, "shortcut.binding");
        require(WidgetSmokePlatform.screen(minecraft) instanceof KeyBindsScreen, "opens vanilla key binding screen");
        var keys = (KeyBindsScreen) WidgetSmokePlatform.screen(minecraft);
        var alternate = new KeyEvent(GLFW.GLFW_KEY_F7, 0, 0);
        keys.selectedKey = mapping;
        keys.keyPressed(alternate);
        keys.keyReleased(alternate);
        try {
            require(mapping.getKey().equals(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F7)),
                    "vanilla screen updates the registered shortcut");
            keys.onClose();
            require(WidgetSmokePlatform.screen(minecraft) == panel && focused(panel, "shortcut.binding")
                            && find(panel, "shortcut.binding").getMessage().getString()
                            .contains(mapping.getTranslatedKeyMessage().getString()),
                    "return focuses shortcut and shows its new value");
        } finally {
            mapping.setKeyModifierAndCode(oldModifier, oldKey);
            KeyMapping.resetMapping();
            minecraft.options.save();
        }
        click(panel, "section.links");
        boolean links = minecraft.options.chatLinks().get();
        boolean prompt = minecraft.options.chatLinksPrompt().get();
        try {
            minecraft.options.chatLinks().set(false);
            click(panel, "links.issues");
            require(WidgetSmokePlatform.screen(minecraft) == panel && panel.getNarrationMessage().getString()
                    .contains(Component.translatable("sh_better_ui.panel.links.unavailable.details").getString()),
                    "disabled links stay in panel and report the reason");
            minecraft.options.chatLinks().set(true);
            minecraft.options.chatLinksPrompt().set(true);
            verifyLink(minecraft, panel, "github", "https://github.com/dishanhai/SH-Better-UI");
            verifyLink(minecraft, panel, "issues", "https://github.com/dishanhai/SH-Better-UI/issues");
            verifyLink(minecraft, panel, "afdian", "https://afdian.com/a/dishanhai?utm_source=copylink&utm_medium=link");
        } finally {
            minecraft.options.chatLinks().set(links);
            minecraft.options.chatLinksPrompt().set(prompt);
        }
        clickMessage(panel, CommonComponents.GUI_BACK);
        require(focused(panel, "section.links"), "feedback Back button returns focus to the entry");
        click(panel, "section.overview");
        require(focused(panel, "overview.details.name"), "mouse navigation focuses the overview");
        verifyOverviewBackend(panel);
        require(panel.keyPressed(new KeyEvent(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0)) && !find(panel, "overview.details.name").visible,
                "overview scrolls by keyboard");
        require(panel.keyPressed(new KeyEvent(GLFW.GLFW_KEY_PAGE_UP, 0, 0)) && find(panel, "overview.details.name").visible,
                "overview scrolls back by keyboard");
        require(find(panel, "apply").active && ShBetterUiApi.settings().current().equals(original),
                "tools preserve the unapplied settings draft");
        // Restore the draft value before the independent theme apply/discard test.
        click(panel, "section.display");
        click(panel, "motion");
        require(!find(panel, "apply").active, "original draft restored");
        click(panel, "section.tools");
        System.out.println("SHBUI_CONTROL_PANEL_TOOLS_PASS backend=" + WidgetSmokePlatform.backend());
    }

    private static void verifyOverviewBackend(ControlPanelScreen panel) {
        var row = find(panel, "overview.device.backend");
        require(row.visible, "active backend is visible without scrolling");
        var message = (TranslatableContents) row.getMessage().getContents();
        require(WidgetSmokePlatform.backend().equalsIgnoreCase(String.valueOf(message.getArgs()[0])),
                "overview reports active backend");
    }

    private static void verifyLink(Minecraft minecraft, ControlPanelScreen panel, String name, String url) {
        String key = "links." + name;
        click(panel, key);
        var confirmation = WidgetSmokePlatform.screen(minecraft);
        require(confirmation instanceof ConfirmLinkScreen && confirmation.getNarrationMessage().getString().contains(url),
                "link asks before opening " + name);
        clickMessage(confirmation, CommonComponents.GUI_CANCEL);
        require(WidgetSmokePlatform.screen(minecraft) == panel && focused(panel, key),
                "cancel restores the feedback row " + name);
    }

    private static boolean focused(Screen screen, String suffix) {
        return screen.getFocused() == find(screen, suffix);
    }

    private static void click(Screen screen, String suffix) {
        click(screen, find(screen, suffix));
    }

    private static AbstractWidget find(Screen screen, String suffix) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(w -> w.getMessage().getContents() instanceof TranslatableContents key
                        && key.getKey().equals("sh_better_ui.panel." + suffix))
                .findFirst().orElseThrow(() -> new IllegalStateException("Panel control missing: " + suffix));
    }

    private static void clickMessage(Screen screen, Component message) {
        var widget = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(w -> w.getMessage().equals(message)).findFirst().orElseThrow();
        click(screen, widget);
    }

    private static void click(Screen screen, AbstractWidget widget) {
        require(widget.active && widget.visible, "visible panel control " + widget.getMessage().getString());
        var event = new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0,
                widget.getY() + widget.getHeight() / 2.0, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        require(screen.mouseClicked(event, false), "panel accepted click");
        screen.mouseReleased(event);
    }

    private static double contrast(NativeImage image, int scale, int x0, int x1, int y) {
        int sum = 0;
        int count = 0;
        for (int x = x0; x + 4 < x1; x += 8) {
            sum += Math.abs(channel(image, scale, x, y) - channel(image, scale, x + 4, y));
            count++;
        }
        return (double) sum / count;
    }

    private static int channel(NativeImage image, int scale, int x, int y) {
        return image.getPixel(x * scale, y * scale) & 0xFF;
    }

    private static void exact(NativeImage image, int scale, int x, int y, int color, String message) {
        int actual = image.getPixel(x * scale, y * scale);
        require(actual == color, message + " expected=" + Integer.toHexString(color)
                + " actual=" + Integer.toHexString(actual));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Effects smoke: " + message);
    }

    private static final class PatternScreen extends Screen {
        private final boolean optical;

        PatternScreen(boolean optical) {
            super(Component.translatable("sh_better_ui.panel.title"));
            this.optical = optical;
        }

        @Override
        protected void init() {
            if (!optical) return;
            var button = ShBetterUiApi.controls().button(new Bounds(20, 125, 96, 28), CommonComponents.EMPTY,
                    ignored -> {}, OPTICAL, false, UiStyle.LIQUID_GLASS);
            button.setBackgroundBlurRadius(0);
            addRenderableWidget(button);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            for (int x = 0; x < width; x += 4) {
                graphics.fill(x, 0, Math.min(x + 4, width), height, (x & 4) == 0 ? DARK : LIGHT);
            }
            if (optical) {
                require(ShBetterUiApi.blur().prepareBackdrop(graphics), "host prepares GPU snapshot");
            } else {
                require(ShBetterUiApi.blur().background(graphics, OPTICAL), "native menu blur requested");
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            if (optical) {
                LiquidGlassSurface.draw(graphics, 16, 24, 96, 56, 12, 0, 0, SOFT, OPTICAL);
                LiquidGlassSurface.draw(graphics, 128, 24, 96, 56, 12, 0, 0, CLEAR, OPTICAL);
                LiquidGlassSurface.draw(graphics, 240, 24, 96, 56, 12, 0, 0, LENS, OPTICAL);
                LiquidGlassSurface.draw(graphics, 142, 125, 92, 34, 8, HIGH_CONTRAST_FILL, LIGHT, LENS, CONTRAST);
                graphics.enableScissor(351, 24, 365, 80);
                try {
                    LiquidGlassSurface.draw(graphics, 347, 24, 42, 56, 8, 0x804448FF, 0, SOFT, OPTICAL);
                } finally {
                    graphics.disableScissor();
                }
            }
            graphics.fill(22, 99, 30, 106, MARK);
            super.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }
}
