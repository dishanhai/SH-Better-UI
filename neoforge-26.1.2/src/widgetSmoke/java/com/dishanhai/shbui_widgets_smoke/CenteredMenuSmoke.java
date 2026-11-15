// Author: dishanhai
package com.dishanhai.shbui_widgets_smoke;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;

final class CenteredMenuSmoke {
    private static Screen screen;
    private static int stage;
    private static int frames;
    private static int[] baseline;
    private static long opened;
    private static boolean pending;

    private CenteredMenuSmoke() {}

    static void frame(Minecraft minecraft) {
        if (WidgetSmokePlatform.hasOverlay(minecraft)) return;
        require(++frames < 3600, "timed out");
        if (screen == null) {
            minecraft.options.guiScale().set(2);
            screen = ShBetterUiApi.settings().developerSampleFullscreen(
                    WidgetSmokePlatform.screen(minecraft), "glide_select", UiPreferences.DEFAULT);
            WidgetSmokePlatform.open(minecraft, screen);
            GLFW.glfwFocusWindow(minecraft.getWindow().handle());
            return;
        }
        if (!minecraft.isWindowActive()) return;
        if (pending || stage == 2 || stage == 1 && Util.getMillis() - opened < 300) return;
        require(screen.getFocused() instanceof AbstractWidget, "preview has initial focus");
        var trigger = (AbstractWidget) screen.getFocused();
        int scale = minecraft.getWindow().getGuiScale();
        int x = trigger.getX() + 12, y = trigger.getY() + trigger.getHeight() + 6 + 36;
        pending = true;
        Screenshot.takeScreenshot(WidgetSmokePlatform.target(minecraft), image -> {
            boolean keepRunning = false;
            try (image) {
                int[] pixels = new int[64];
                for (int i = 0; i < pixels.length; i++) pixels[i] = image.getPixel((x + i) * scale, y * scale);
                if (stage == 0) {
                    baseline = pixels;
                    require(trigger.getMessage().getString().equals("PNG"), "initial option");
                    double clickX = trigger.getX() + trigger.getWidth() / 2.0;
                    double clickY = trigger.getY() + trigger.getHeight() / 2.0;
                    var click = new MouseButtonEvent(clickX, clickY, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
                    require(screen.mouseClicked(click, false), "pointer opens the menu");
                    screen.mouseReleased(click);
                    opened = Util.getMillis();
                    stage = 1;
                    keepRunning = true;
                    return;
                }
                int changed = 0;
                for (int i = 0; i < pixels.length; i++) if (pixels[i] != baseline[i]) changed++;
                if (changed < 16) {
                    image.writeToFile(minecraft.gameDirectory.toPath().resolve("centered-menu-failed.png"));
                }
                require(changed >= 16, "glass reaches the native framebuffer, changed=" + changed);
                require(screen.keyPressed(key(GLFW.GLFW_KEY_DOWN)), "Down moves the cursor");
                require(screen.keyPressed(key(GLFW.GLFW_KEY_ENTER)), "Enter confirms");
                require(trigger.getMessage().getString().equals("JPG"), "keyboard updates the selected option");
                require(screen.getFocused() == trigger, "trigger retains focus");
                image.writeToFile(minecraft.gameDirectory.toPath().resolve("centered-menu.png"));
                stage = 2;
                System.out.println("SHBUI_CENTERED_MENU_PASS backend=" + WidgetSmokePlatform.backend()
                        + " changed=" + changed);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            } finally {
                pending = false;
                if (!keepRunning) minecraft.stop();
            }
        });
    }

    private static KeyEvent key(int key) { return new KeyEvent(key, 0, 0); }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException("Centered menu smoke: " + detail);
    }
}
