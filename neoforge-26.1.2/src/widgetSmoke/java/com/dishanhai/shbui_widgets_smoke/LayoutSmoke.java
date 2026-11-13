// Author: dishanhai
package com.dishanhai.shbui_widgets_smoke;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperGalleryScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.ScreenNarrationCollector;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Verifies native GUI scaling and exercises narrower Screen layouts inside that client. */
final class LayoutSmoke {
    private static final String PANEL = "sh_better_ui.panel.";
    private static final String GALLERY = PANEL + "developer.gallery.";
    private static final int SCALE = Integer.getInteger("shbui.widgetSmoke.layoutScale", 3);
    private static boolean started;
    private static boolean requested;
    private static boolean complete;
    private static CompletableFuture<Void> languageReload;
    private static int frames;

    private LayoutSmoke() {}

    static boolean frame(Minecraft minecraft) {
        if (complete) return true;
        if (WidgetSmokePlatform.hasOverlay(minecraft)) return false;
        if (SCALE == 4) {
            if (languageReload == null) {
                var packs = minecraft.getResourcePackRepository();
                String pack = "file/long-labels";
                require(packs.getSelectedIds().contains(pack) || packs.addPack(pack), "test resource pack is available");
                minecraft.getLanguageManager().setSelected("en_xa");
                languageReload = minecraft.reloadResourcePacks();
                return false;
            }
            if (!languageReload.isDone()) return false;
            languageReload.join();
        }
        if (!started) {
            started = true;
            String backend = WidgetSmokePlatform.backend();
            require(backend.equalsIgnoreCase(System.getProperty("shbui.widgetSmoke.expectedBackend")),
                    "expected graphics backend");
            String preference = System.getProperty("shbui.widgetSmoke.expectedPreference");
            if (preference != null) {
                require(preference.equals(WidgetSmokePlatform.backendPreference(minecraft)), "fallback preference kept");
            }
            minecraft.options.guiScale().set(SCALE);
            require(minecraft.getWindow().getGuiScale() == SCALE, "native GUI Scale " + SCALE);
            verifyLayout(minecraft);
            return false;
        }
        if (++frames < 5 || requested) return false;
        requested = true;
        Screenshot.takeScreenshot(WidgetSmokePlatform.target(minecraft), image -> {
            try (image) {
                image.writeToFile(minecraft.gameDirectory.toPath().resolve("gallery-gui-scale-" + SCALE + ".png"));
                System.out.println("SHBUI_LAYOUT_REVIEW_PASS backend=" + WidgetSmokePlatform.backend() + " scale=" + SCALE);
                complete = true;
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            } finally {
                if (!complete) minecraft.stop();
            }
        });
        return false;
    }

    private static void verifyLayout(Minecraft minecraft) {
        var window = minecraft.getWindow();
        int width = window.getGuiScaledWidth();
        int height = window.getGuiScaledHeight();
        var errors = new ArrayList<String>();
        ShBetterUiApi.settings().openControlPanel();
        var panel = (ControlPanelScreen) WidgetSmokePlatform.screen(minecraft);
        var applied = ShBetterUiApi.settings().current();
        click(panel, PANEL + "section.display");
        click(panel, PANEL + "motion");
        if (SCALE == 4) {
            var motion = (UiButton) find(panel, PANEL + "motion");
            check(errors, narration(motion).contains(translation(PANEL + "motion.tooltip")),
                    "setting retains its explanation after editing");
            check(errors, panel.getNarrationMessage().getString().contains(translation(PANEL + "unsaved")),
                    "panel narrates the full unsaved status");
        }
        panel.resize(260, 140);
        var motion = find(panel, PANEL + "motion");
        var apply = find(panel, PANEL + "apply");
        check(errors, motion.visible && panel.getFocused() == motion, "resize reveals and restores focused settings row");
        var firstVisible = motion.visible ? motion : find(panel, PANEL + "contrast");
        check(errors, firstVisible.getY() + firstVisible.getHeight() <= apply.getY() - 13,
                "settings row stays above the status line at the smallest expanded height");
        panel.setFocused(firstVisible);
        int direction = firstVisible == motion ? GLFW.GLFW_KEY_PAGE_UP : GLFW.GLFW_KEY_PAGE_DOWN;
        panel.keyPressed(key(direction));
        check(errors, panel.getFocused() == find(panel, PANEL + (firstVisible == motion ? "contrast" : "motion")),
                "keyboard paging transfers focus to the newly visible settings row");
        panel.resize(width, height);
        check(errors, find(panel, PANEL + "apply").active && ShBetterUiApi.settings().current().equals(applied),
                "resize retains unapplied settings");
        click(panel, PANEL + "section.tools");
        if (SCALE == 4) {
            var tools = (UiButton) find(panel, PANEL + "section.tools");
            var firstTool = find(panel, PANEL + "section.overview");
            check(errors, narration(tools).contains(translation("sh_better_ui.control.selected")),
                    "selected tab state is narrated with its label");
            panel.setFocused(tools);
            panel.keyPressed(key(GLFW.GLFW_KEY_TAB));
            check(errors, panel.getFocused() == firstTool, "Tab moves from navigation to the rebuilt content");
            panel.setFocused(firstTool);
            panel.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_SHIFT));
            check(errors, panel.getFocused() == tools, "Shift+Tab moves from rebuilt content to navigation");
        }
        click(panel, PANEL + "developer.title");
        var developer = (DeveloperPanelScreen) WidgetSmokePlatform.screen(minecraft);
        click(developer, GALLERY + "materials");
        var gallery = (DeveloperGalleryScreen) WidgetSmokePlatform.screen(minecraft);

        gallery.resize(260, 180);
        var search = gallery.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow();
        search.setValue("sh_better_ui:liquid_glass");
        click(gallery, GALLERY + "sample.liquid_glass");
        var slider = slider(gallery);
        int before = slider.value();
        gallery.setFocused(slider);
        gallery.resize(width, height);
        check(errors, gallery.getFocused() == slider(gallery), "resize restores the refraction slider focus");
        slider = slider(gallery);
        if (SCALE == 4) {
            String caption = slider.getMessage().getString();
            String speech = narration(slider);
            check(errors, caption.endsWith(before + "%") && minecraft.font.width(caption) > slider.getWidth(),
                    "the slider receives an overflowing localized label with its current value");
            check(errors, slider.getTooltip() != null && slider.getTooltip().toCharSequence(minecraft).size() > 1,
                    "the slider exposes the full caption in a wrapped tooltip");
            check(errors, speech.contains(caption) && speech.indexOf(caption) == speech.lastIndexOf(caption),
                    "the full slider caption and value are narrated once");
        }
        var press = new MouseButtonEvent(slider.getX() + slider.getWidth() - 7, slider.getY() + slider.getHeight() / 2.0,
                new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        gallery.mouseClicked(press, false);
        check(errors, slider.value() > before, "refraction responds to pointer before release");
        gallery.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
        check(errors, slider.value() == before && slider(gallery) == slider,
                "Escape cancels the live drag before leaving the preview");
        gallery.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
        check(errors, gallery.getFocused() == find(gallery, GALLERY + "sample.liquid_glass"),
                "return reveals and restores the selected sample after resizing");
        if (SCALE == 4) {
            var entry = (UiButton) find(gallery, GALLERY + "sample.liquid_glass");
            String label = entry.getMessage().getString();
            String speech = narration(entry);
            check(errors, label.endsWith("an independent background sample") && minecraft.font.width(label) > entry.getWidth(),
                    "the test resource pack supplies an overflowing sample label");
            check(errors, entry.getTooltip() != null && entry.getTooltip().toCharSequence(minecraft).size() > 1,
                    "the overflowing sample label wraps in its tooltip");
            check(errors, speech.contains(label) && speech.indexOf(label) == speech.lastIndexOf(label),
                    "the full sample label is narrated once");
        }
        gallery.onClose();
        check(errors, WidgetSmokePlatform.screen(minecraft) == developer
                        && developer.getFocused() == find(developer, GALLERY + "materials"),
                "gallery returns to the developer panel and restores the entry");
        developer.onClose();
        check(errors, WidgetSmokePlatform.screen(minecraft) == panel
                        && panel.getFocused() == find(panel, PANEL + "developer.title"),
                "developer panel returns to settings and restores the entry");
        check(errors, find(panel, PANEL + "apply").active && ShBetterUiApi.settings().current().equals(applied),
                "gallery keeps the pending panel draft");
        if (!errors.isEmpty()) throw new IllegalStateException("Layout smoke: " + String.join("; ", errors));

        click(panel, PANEL + "section.display");
        click(panel, PANEL + "motion");
        click(panel, PANEL + "section.tools");
        click(panel, PANEL + "developer.title");
        developer = (DeveloperPanelScreen) WidgetSmokePlatform.screen(minecraft);
        click(developer, GALLERY + "materials");
    }

    private static UiSlider slider(Screen screen) {
        return screen.children().stream().filter(UiSlider.class::isInstance).map(UiSlider.class::cast)
                .findFirst().orElseThrow();
    }

    private static String narration(AbstractWidget widget) {
        var collector = new ScreenNarrationCollector();
        collector.update(widget::updateNarration);
        return collector.collectNarrationText(true);
    }

    private static String translation(String key) { return Component.translatable(key).getString(); }

    private static AbstractWidget find(Screen screen, String key) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(w -> w.getMessage().getContents() instanceof TranslatableContents label && label.getKey().equals(key))
                .findFirst().orElseThrow(() -> new IllegalStateException("Layout smoke control missing: " + key));
    }

    private static void click(Screen screen, String key) {
        var button = find(screen, key);
        require(button.active && button.visible, "interactive control " + key);
        var press = new MouseButtonEvent(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0,
                new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(press, false);
        screen.mouseReleased(press);
    }

    private static KeyEvent key(int code) { return new KeyEvent(code, 0, 0); }

    private static void check(List<String> errors, boolean condition, String message) {
        if (!condition) errors.add(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Layout smoke: " + message);
    }
}
