// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.DynamicThemeSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.dishanhai.sh_better_ui.client.gui.scaled.DynamicThemeSmoke.require;

/** Verifies a genuine control-panel Apply followed by a separate process reading the TOML. */
final class DynamicThemeSettingsSmoke {
    private static final String PREFIX = "sh_better_ui.panel.";
    private static final DynamicThemeSettings EXPECTED = new DynamicThemeSettings("sh_better_ui:aurora", 200, true);
    private static final String TAB = PREFIX + "section.dynamic_theme";
    private static long deadline;
    private static long editedAt;
    private static int stage;
    private static TitleScreen title;
    private static ControlPanelScreen panel;
    private static long baselineOpticalDraws;
    private static int worldPixel;

    private DynamicThemeSettingsSmoke() {}

    static boolean hudFrame(Minecraft client, DynamicThemeWorldSmoke.Hooks platform, Path output, long now)
            throws IOException {
        if (deadline == 0) deadline = now + 120_000;
        require(now < deadline, "Timed out in HUD glass settings stage " + stage);
        var engine = UnifiedUI.get();
        if (stage == 0) {
            if (!(client.screen instanceof TitleScreen menu)) return false;
            title = menu;
            client.tell(() -> {
                require(ShBetterUiApi.settings().save(ClientUiSettings.DEFAULT.withDisplay(false, true, true)),
                        "Could not reset HUD glass test preferences");
                client.options.guiScale().set(2);
                client.resizeDisplay();
                platform.create(client, title, "hud-glass-" + Long.toUnsignedString(System.nanoTime()));
            });
            stage++;
        } else if (stage == 1) {
            if (client.player == null || (client.screen != null && !(client.screen instanceof PauseScreen))) return false;
            panel = ShBetterUiApi.settings().controlPanel(null, new KeyMapping("key.sh_better_ui.control_panel",
                    GLFW.GLFW_KEY_F8, "key.categories.sh_better_ui"));
            client.setScreen(panel);
            stage++;
        } else if (stage == 2) {
            if (panel.children().stream().noneMatch(child -> child instanceof UiButton button
                    && key(button.getMessage()).equals(PREFIX + "theme.dark"))) {
                focusDisplayTab();
                return false;
            }
            for (String theme : new String[]{"dark", "light", "custom", "liquid_glass"}) {
                button("theme." + theme).onPress();
            }
            require(button("theme.hud_glass").visible, "HUD glass theme is missing");
            require(!button("theme.hud_background.configure").active, "An off background should have no editor");
            require(engine.configs().get(UIApis.CLIENT_SETTINGS).theme().mode() == ThemeSettings.Mode.DARK,
                    "HUD glass draft modified the saved mode");
            panel.setFocused(button("theme.hud_background"));
            baselineOpticalDraws = engine.require(UIApis.GLASS).diagnostics().opticalDraws();
            editedAt = now;
            stage++;
        } else if (stage == 3) {
            if (now - editedAt < 500) return false;
            require(engine.require(UIApis.GLASS).diagnostics().opticalDraws() > baselineOpticalDraws,
                    "HUD glass draft did not draw an optical surface");
            Files.createDirectories(output);
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                worldPixel = image.getPixelRGBA(4, image.getHeight() / 2);
                image.writeToFile(output.resolve("hud-glass-world.png"));
            }
            var background = button("theme.hud_background");
            require(background.active && background.visible, "HUD background choice is unavailable");
            background.onPress();
            editedAt = now;
            stage++;
        } else if (stage == 4) {
            if (now - editedAt < 500) return false;
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                require(image.getPixelRGBA(4, image.getHeight() / 2) != worldPixel,
                        "The dot matrix draft did not change the backdrop");
                image.writeToFile(output.resolve("hud-glass-dots.png"));
            }
            var configure = button("theme.hud_background.configure");
            require(configure.visible && configure.active, "HUD background editor is unavailable");
            panel.setFocused(configure);
            configure.onPress();
            stage++;
        } else if (stage == 5) {
            require(client.screen instanceof ThemeEffectEditor, "The HUD background did not open the UUK editor");
            editorButton(client, "preset").onPress();
            editorButton(client, "done").onPress();
            require(client.screen == panel && panel.getFocused() == button("theme.hud_background.configure"),
                    "Finishing the editor did not restore the control panel focus");
            require(!engine.configs().get(UIApis.CLIENT_SETTINGS).theme().effects().contains("dotMatrix"),
                    "Finishing the editor committed a draft before Apply");
            var slider = hudBlurSlider();
            require(slider.value() == BuiltinThemes.DEFAULT_HUD_BLUR_RADIUS, "Unexpected default HUD blur");
            setHudBlur(slider, false);
            editedAt = now;
            stage++;
        } else if (stage == 6) {
            if (now - editedAt < 500) return false;
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                image.writeToFile(output.resolve("hud-blur-0.png"));
            }
            setHudBlur(hudBlurSlider(), true);
            editedAt = now;
            stage++;
        } else if (stage == 7) {
            if (now - editedAt < 500) return false;
            require(engine.configs().get(UIApis.CLIENT_SETTINGS).theme().hudBlurRadius()
                    == BuiltinThemes.DEFAULT_HUD_BLUR_RADIUS, "HUD blur draft changed the saved setting");
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                image.writeToFile(output.resolve("hud-blur-max.png"));
            }
            client.tell(() -> button("apply").onPress());
            stage++;
        } else if (stage == 8) {
            if (!engine.require(UIApis.THEME).snapshot().themeIds().contains(BuiltinThemes.HUD_GLASS)) return false;
            var theme = engine.configs().get(UIApis.CLIENT_SETTINGS).theme();
            require(theme.mode() == ThemeSettings.Mode.HUD_GLASS
                    && theme.hudBackgroundEffect().equals(ThemeSettings.DEFAULT_BACKGROUND_EFFECT)
                    && theme.effects().contains("dotMatrix")
                    && theme.hudBlurRadius() == ThemeSettings.MAX_HUD_BLUR_RADIUS,
                    "HUD glass theme, background, UUK profile or blur did not save");
            platform.leave(client, title);
            stage++;
        } else if (client.level == null) {
            return true;
        }
        return false;
    }

    private static UiSlider hudBlurSlider() {
        return panel.children().stream().filter(UiSlider.class::isInstance).map(UiSlider.class::cast)
                .filter(slider -> key(slider.getMessage()).equals(PREFIX + "theme.hud_blur"))
                .findFirst().orElseThrow();
    }

    private static void setHudBlur(UiSlider slider, boolean max) {
        panel.setFocused(slider);
        int x = slider.getX() + (max ? slider.getWidth() - 4 : 4);
        int y = slider.getY() + slider.getHeight() / 2;
        require(slider.mouseClicked(x, y, 0), "HUD blur slider did not accept input");
        slider.mouseReleased(x, y, 0);
        require(slider.value() == (max ? ThemeSettings.MAX_HUD_BLUR_RADIUS : 0), "HUD blur endpoint unreachable");
    }

    private static UiButton editorButton(Minecraft client, String suffix) {
        return client.screen.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast)
                .filter(button -> key(button.getMessage()).equals("sh_better_ui.theme_editor." + suffix))
                .findFirst().orElseThrow();
    }

    private static void focusDisplayTab() {
        var bar = panel.children().stream().filter(UiScrollableSectionBar.class::isInstance)
                .map(UiScrollableSectionBar.class::cast)
                .filter(tabs -> tabs.buttons().stream()
                        .anyMatch(button -> key(button.getMessage()).equals(PREFIX + "section.display")))
                .findFirst().orElseThrow();
        var tab = bar.buttons().stream().filter(button -> key(button.getMessage()).equals(PREFIX + "section.display"))
                .findFirst().orElseThrow();
        panel.setFocused(bar);
        bar.focus(bar.buttons().indexOf(tab));
        panel.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    }

    static boolean frame(Minecraft client, Path output, String mode, long now) throws IOException {
        if (deadline == 0) deadline = now + 40_000;
        require(now < deadline, "Timed out in dynamic theme settings stage " + stage + " (" + mode + ")");
        if (stage == 0) {
            if (!(client.screen instanceof TitleScreen menu)) return false;
            DynamicThemeSmoke.checkKernel();
            if (mode.equals("read")) {
                assertPersisted();
                return true;
            }
            title = menu;
            var settings = ShBetterUiApi.settings();
            var current = settings.current();
            if (!current.dynamicTheme().equals(DynamicThemeSettings.DEFAULT)) {
                require(settings.save(current.withDynamicTheme(DynamicThemeSettings.DEFAULT)), "Could not reset the test config");
            }
            client.options.guiScale().set(1);
            client.resizeDisplay();
            openPanel(client);
            stage++;
        } else if (stage == 1) {
            var input = input();
            if (input == null) {
                panel.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
                return false;
            }
            stageTheme("invalid");
            require(!button("apply").active, "Invalid theme ID changed the draft");
            stageTheme(EXPECTED.fixedTheme());
            var speed = panel.children().stream().filter(UiSlider.class::isInstance).map(UiSlider.class::cast)
                    .findFirst().orElseThrow();
            panel.setFocused(speed);
            require(speed.mouseClicked(speed.getX() + 4 + (speed.getWidth() - 8) * 0.25, speed.getY() + 4, 0),
                    "Transition speed slider did not accept pointer input");
            speed.mouseReleased(speed.getX(), speed.getY(), 0);
            require(speed.value() == EXPECTED.animationPercent(), "Unexpected transition speed");
            button("dynamic_theme.low_performance").onPress();
            editedAt = now;
            stage++;
        } else if (stage == 2) {
            if (now - editedAt < 1_100) return false;
            Files.createDirectories(output);
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                image.writeToFile(output.resolve("preferences.png"));
            }
            require(button("apply").active, "Valid settings did not create a draft");
            button("apply").onPress();
            require(ShBetterUiApi.settings().current().dynamicTheme().equals(EXPECTED), "Apply did not save the draft");
            stageTheme("sh_better_ui:light");
            require(button("apply").active, "The second edit did not create a draft");
            client.setScreen(title);
            stage++;
        } else {
            if (!ShBetterUiApi.themes().preferences().lowPerformance()) return false;
            assertPersisted();
            return true;
        }
        return false;
    }

    private static void openPanel(Minecraft client) {
        panel = ShBetterUiApi.settings().controlPanel(title, new KeyMapping("key.sh_better_ui.control_panel",
                GLFW.GLFW_KEY_F8, "key.categories.sh_better_ui"));
        client.setScreen(panel);
        var bar = panel.children().stream().filter(UiScrollableSectionBar.class::isInstance)
                .map(UiScrollableSectionBar.class::cast)
                .filter(candidate -> candidate.buttons().stream().anyMatch(button -> key(button.getMessage()).equals(TAB)))
                .findFirst().orElseThrow();
        panel.setFocused(bar);
    }

    private static void stageTheme(String id) {
        input().setValue(id);
        button("dynamic_theme.stage").onPress();
    }

    private static EditBox input() {
        return panel.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> key(box.getMessage()).equals(PREFIX + "dynamic_theme.id")).findFirst().orElse(null);
    }

    private static UiButton button(String suffix) {
        return panel.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast)
                .filter(button -> key(button.getMessage()).equals(PREFIX + suffix)).findFirst().orElseThrow();
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    private static void assertPersisted() {
        require(ShBetterUiApi.settings().current().dynamicTheme().equals(EXPECTED), "Saved settings mismatch");
        require(UnifiedUI.get().configs().get(UIApis.CLIENT_SETTINGS).dynamicTheme().equals(EXPECTED),
                "Kernel User Global settings do not match the saved settings");
        var preferences = ShBetterUiApi.themes().preferences();
        require(EXPECTED.fixedTheme().equals(preferences.fixedThemeId()) && preferences.animationSpeed() == 2
                && preferences.lowPerformance(), "Runtime preferences do not match the saved settings");
        require(ShBetterUiApi.themes().snapshot().themeIds().contains(EXPECTED.fixedTheme()), "Fixed theme was not displayed");
    }
}
