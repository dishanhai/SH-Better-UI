// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.kernel.internal.appearance.ThemeCustomizationScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;

/** Isolated, opt-in end-to-end editing and native image upload smoke; never included in release JARs. */
@Mod.EventBusSubscriber(modid = "sh_better_ui", value = Dist.CLIENT)
public final class CustomThemeSmoke {
    private static int stage;
    private static long started, changed;
    private static boolean finished;
    private static boolean queued;
    private static ControlPanelScreen panel;
    private static ThemeCustomizationScreen editor;
    private static Path output;
    private static java.util.concurrent.CompletableFuture<Void> reload;

    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        String path = System.getProperty("shb.customTheme.smoke");
        if (path == null || finished || queued || event.phase != TickEvent.Phase.END) return;
        queued = true;
        Minecraft.getInstance().tell(() -> {
            try { advance(path); } finally { queued = false; }
        });
    }

    private static void advance(String path) {
        var client = Minecraft.getInstance();
        if (client.getOverlay() != null) return;
        long now = System.nanoTime() / 1_000_000;
        if (started == 0) started = now;
        try {
            require(now - started < 90_000, "Timed out at stage " + stage);
            output = Path.of(path);
            Files.createDirectories(output);
            switch (stage) {
                case 0 -> {
                    if (!(client.screen instanceof TitleScreen)) return;
                    var image = new java.awt.image.BufferedImage(160, 90, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    var paint = image.createGraphics();
                    paint.setColor(java.awt.Color.ORANGE); paint.fillRect(0, 0, 80, 90);
                    paint.setColor(java.awt.Color.BLUE); paint.fillRect(80, 0, 80, 90); paint.dispose();
                    javax.imageio.ImageIO.write(image, "png", client.gameDirectory.toPath().resolve("background.png").toFile());
                    require(ShBetterUiApi.settings().save(ClientUiSettings.DEFAULT.withDisplay(false, true, true)
                            .withTheme(ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.CUSTOM))), "Could not initialize isolated preferences");
                    panel = ShBetterUiApi.settings().controlPanel(null, new KeyMapping("test", GLFW.GLFW_KEY_F8, "test"));
                    client.setScreen(panel);
                    var bar = panel.children().stream().filter(UiScrollableSectionBar.class::isInstance)
                            .map(UiScrollableSectionBar.class::cast).filter(b -> b.buttons().stream()
                                    .anyMatch(button -> key(button.getMessage()).equals("sh_better_ui.panel.section.display")))
                            .findFirst().orElseThrow();
                    panel.setFocused(bar);
                    bar.focus(1);
                    panel.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                    changed = now; stage++;
                }
                case 1 -> {
                    if (now - changed < 600) return;
                    if (panel.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast)
                            .noneMatch(b -> key(b.getMessage()).equals("sh_better_ui.panel.theme.customize"))) {
                        var bar = panel.children().stream().filter(UiScrollableSectionBar.class::isInstance)
                                .map(UiScrollableSectionBar.class::cast).filter(b -> b.buttons().stream()
                                        .anyMatch(button -> key(button.getMessage()).equals("sh_better_ui.panel.section.display")))
                                .findFirst().orElseThrow();
                        panel.setFocused(bar); bar.focus(1);
                        panel.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                        changed = now;
                        return;
                    }
                    capture(client, "panel-before.png");
                    button(client, "panel.theme.customize").onPress();
                    editor = (ThemeCustomizationScreen) client.screen;
                    var input = editor.children().stream().filter(UiTextField.class::isInstance)
                            .map(UiTextField.class::cast).findFirst().orElseThrow();
                    input.setFocused(true);
                    for (char c : "#336699".toCharArray()) require(input.charTyped(c, 0), "Color input rejected a character");
                    require(ShBetterUiApi.settings().current().theme().background().isEmpty(), "Color preview persisted early");
                    changed = now; stage++;
                }
                case 2 -> {
                    if (now - changed < 500) return;
                    capture(client, "colors.png");
                    button(client, "custom_theme.section.backgrounds").onPress();
                    button(client, "custom_theme.background.image").onPress();
                    button(client, "custom_theme.configure_background").onPress();
                    require(client.screen instanceof ThemeEffectEditor, "Image editor unavailable");
                    var input = client.screen.children().stream().filter(EditBox.class::isInstance)
                            .map(EditBox.class::cast).findFirst().orElseThrow();
                    input.setValue("background.png");
                    changed = now; stage++;
                }
                case 3 -> {
                    if (now - changed < 1500) return;
                    capture(client, "image-parameters.png");
                    button(client, "theme_editor.done").onPress();
                    require(client.screen == editor, "Image editor returned to the wrong parent");
                    button(client, "custom_theme.done").onPress();
                    require(client.screen == panel, "Custom editor returned to the wrong parent");
                    require(ShBetterUiApi.settings().current().theme().background().isEmpty(), "Done bypassed Apply");
                    button(client, "panel.apply").onPress();
                    require("#336699".equals(ShBetterUiApi.settings().current().theme().background()), "Palette did not save");
                    require("sh_better_ui:image".equals(ShBetterUiApi.settings().current().theme().backgroundEffect()), "Image selection did not save");
                    changed = now; stage++;
                }
                case 4 -> {
                    if (now - changed < 1500) return;
                    try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                        require((image.getPixelRGBA(3, image.getHeight() / 2) & 0xFFFFFF) == 0x00C8FF,
                                "Native image did not render its orange left half");
                        require((image.getPixelRGBA(image.getWidth() - 4, image.getHeight() / 2) & 0xFFFFFF) == 0xFF0000,
                                "Native image did not render its blue right half");
                    }
                    capture(client, "panel-applied.png");
                    button(client, "panel.theme.background.select").onPress();
                    editor = (ThemeCustomizationScreen) client.screen;
                    button(client, "custom_theme.background.plain").onPress();
                    button(client, "custom_theme.cancel").onPress();
                    require(client.screen == panel, "Esc did not return");
                    require(!button(client, "panel.apply").active, "Cancel changed the parent's draft");
                    button(client, "panel.theme.background.select").onPress();
                    changed = now; stage++;
                }
                case 5 -> {
                    if (now - changed < 500) return;
                    capture(client, "backgrounds.png");
                    button(client, "custom_theme.next").onPress();
                    require(button(client, "custom_theme.configure_background").active,
                            "Background configuration disappeared after changing pages");
                    capture(client, "backgrounds-next-page.png");
                    button(client, "custom_theme.section.effects").onPress();
                    changed = now; stage++;
                }
                case 6 -> {
                    if (now - changed < 500) return;
                    capture(client, "effects.png");
                    button(client, "panel.theme.group.glass").onPress();
                    require(client.screen instanceof ThemeEffectEditor, "Effect parameters unavailable");
                    client.screen.onClose();
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1200, 800);
                    client.options.guiScale().set(3); client.resizeDisplay();
                    changed = now; stage++;
                }
                case 7 -> {
                    if (now - changed < 700) return;
                    require(client.getWindow().getGuiScale() == 3, "GUI scale 3 was not applied");
                    capture(client, "scale-3.png");
                    reload = client.reloadResourcePacks();
                    changed = now; stage++;
                }
                case 8 -> {
                    if (!reload.isDone() || now - changed < 1500) return;
                    reload.join();
                    capture(client, "resource-reload.png");
                    Files.writeString(output.resolve("success.txt"), "palette,image,pixel-colors,selection,apply,cancel,pagination,effects,scale3,resource-reload\n");
                    finished = true;
                    client.stop();
                }
                default -> throw new AssertionError(stage);
            }
        } catch (Throwable failure) {
            finished = true;
            try { Files.writeString(output.resolve("failure.txt"), failure.toString()); }
            catch (Exception ignored) {}
            failure.printStackTrace();
            client.stop();
        }
    }

    private static UiButton button(Minecraft client, String suffix) {
        return client.screen.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast)
                .filter(b -> key(b.getMessage()).equals("sh_better_ui." + suffix)).findFirst().orElseThrow(() ->
                        new AssertionError("Missing button " + suffix));
    }
    private static String key(Component text) {
        return text.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }
    private static void capture(Minecraft client, String name) throws java.io.IOException {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) { image.writeToFile(output.resolve(name)); }
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
