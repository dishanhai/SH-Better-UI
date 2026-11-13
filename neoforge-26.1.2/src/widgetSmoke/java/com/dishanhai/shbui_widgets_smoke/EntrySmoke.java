// Author: dishanhai
package com.dishanhai.shbui_widgets_smoke;

import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperGalleryScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.widget.ModsButton;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;

import java.time.Duration;

/** Verifies the shipped panel through the real mod-list button and in-world keyboard callback. */
final class EntrySmoke {
    private static final String WORLD = "shbui-entry-smoke";
    private static final String DEVELOPER_KEY = "sh_better_ui.panel.developer.title";
    private static final String CONTROLS_KEY = "sh_better_ui.panel.developer.gallery.controls";
    private static final long TIMEOUT_NANOS = Duration.ofMinutes(2).toNanos();
    private static Stage stage = Stage.START;
    private static long startedAt;
    private static TitleScreen title;
    private static Screen mods;
    private static ControlPanelScreen panel;
    private static DeveloperGalleryScreen gallery;
    private static DeveloperPanelScreen developer;
    private static boolean pauseOnLostFocus;

    private EntrySmoke() {}

    static void frame(Minecraft minecraft) {
        if (stage == Stage.DONE) return;
        if (stage != Stage.START && System.nanoTime() - startedAt > TIMEOUT_NANOS) {
            throw new IllegalStateException("Panel entry smoke timed out at " + stage
                    + " screen=" + WidgetSmokePlatform.screen(minecraft));
        }
        if (WidgetSmokePlatform.hasOverlay(minecraft)) return;

        switch (stage) {
            case START -> {
                startedAt = System.nanoTime();
                pauseOnLostFocus = minecraft.options.pauseOnLostFocus;
                minecraft.options.pauseOnLostFocus = false;
                title = new TitleScreen();
                stage = Stage.TITLE;
                WidgetSmokePlatform.open(minecraft, title);
            }
            case TITLE -> {
                require(WidgetSmokePlatform.screen(minecraft) == title, "title screen active");
                var button = title.children().stream().filter(ModsButton.class::isInstance)
                        .map(ModsButton.class::cast).findFirst().orElseThrow();
                click(title, button);
                mods = WidgetSmokePlatform.screen(minecraft);
                require(mods != title && mods != null, "title Mods button opens the mod list");
                stage = Stage.MOD_LIST;
            }
            case MOD_LIST -> {
                openConfigFromList();
                panel = requirePanel(minecraft, "mod-list Config button");
                openGallery(minecraft, Stage.MOD_LIST_GALLERY);
            }
            case MOD_LIST_GALLERY -> {
                closeGallery(minecraft);
                panel.onClose();
                require(WidgetSmokePlatform.screen(minecraft) == mods, "panel returns to the same mod list");
                mods.onClose();
                require(WidgetSmokePlatform.screen(minecraft) == title, "mod list returns to title");
                System.out.println("SHBUI_CONTROL_PANEL_MOD_LIST_PASS backend=" + WidgetSmokePlatform.backend());
                stage = Stage.WAIT_WORLD;
                // Creating a world can render a progress screen synchronously. Run it outside
                // the current render event, before the next frame acquires its surface.
                minecraft.schedule(() -> openWorld(minecraft));
            }
            case WAIT_WORLD -> {
                if (minecraft.level == null || minecraft.player == null
                        || WidgetSmokePlatform.screen(minecraft) != null) return;
                require(minecraft.hasSingleplayerServer(), "F8 test uses an integrated server");
                stage = Stage.WAIT_F8;
                pressKey(minecraft, GLFW.GLFW_KEY_F8);
            }
            case WAIT_F8 -> {
                if (WidgetSmokePlatform.screen(minecraft) == null) return;
                panel = requirePanel(minecraft, "in-world F8");
                openGallery(minecraft, Stage.IN_WORLD_GALLERY);
            }
            case IN_WORLD_GALLERY -> {
                closeGallery(minecraft);
                panel.onClose();
                require(WidgetSmokePlatform.screen(minecraft) == null && minecraft.player != null,
                        "F8 panel returns to the same world");
                System.out.println("SHBUI_CONTROL_PANEL_F8_PASS backend=" + WidgetSmokePlatform.backend());
                System.out.println("SHBUI_CONTROL_PANEL_ENTRY_PASS backend=" + WidgetSmokePlatform.backend());
                stage = Stage.DONE;
                minecraft.options.pauseOnLostFocus = pauseOnLostFocus;
                minecraft.stop();
            }
            case DONE -> {}
        }
    }

    private static void openConfigFromList() {
        var search = mods.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow();
        var mod = ModList.get().getModContainerById("sh_better_ui").orElseThrow();
        search.setValue(mod.getModInfo().getDisplayName());
        // The old mod list refreshes on tick. The new list refreshes in the search responder.
        mods.tick();
        var entries = (AbstractSelectionList<?>) mods.children().stream()
                .filter(AbstractSelectionList.class::isInstance).findFirst().orElseThrow();
        require(entries.children().size() == 1, "search isolates the packaged SH-Better-UI mod");
        if (entries.getSelected() == null) {
            click(mods, entries.getRowLeft() + 5, entries.getRowTop(0) + 5);
        }
        require(entries.getSelected() != null, "SH-Better-UI mod selected");
        var config = mods.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(EntrySmoke::isConfigButton).findFirst().orElseThrow();
        click(mods, config);
    }

    private static void openGallery(Minecraft minecraft, Stage next) {
        click(panel, findByKey(panel, "sh_better_ui.panel.section.tools"));
        click(panel, findByKey(panel, DEVELOPER_KEY));
        require(WidgetSmokePlatform.screen(minecraft) instanceof DeveloperPanelScreen, "developer panel opened");
        developer = (DeveloperPanelScreen) WidgetSmokePlatform.screen(minecraft);
        click(developer, findByKey(developer, CONTROLS_KEY));
        var active = WidgetSmokePlatform.screen(minecraft);
        if (!(active instanceof DeveloperGalleryScreen opened)) {
            throw new IllegalStateException("Panel entry smoke: developer gallery opened " + active);
        }
        gallery = opened;
        stage = next;
    }

    private static void closeGallery(Minecraft minecraft) {
        require(WidgetSmokePlatform.screen(minecraft) == gallery, "gallery rendered in the active screen");
        gallery.onClose();
        require(WidgetSmokePlatform.screen(minecraft) == developer, "gallery returns to the developer panel");
        require(developer.getFocused() == findByKey(developer, CONTROLS_KEY), "gallery restores developer entry focus");
        developer.onClose();
        require(WidgetSmokePlatform.screen(minecraft) == panel, "developer panel returns to the same settings panel");
        require(panel.getFocused() == findByKey(panel, DEVELOPER_KEY), "developer panel restores settings focus");
    }

    private static AbstractWidget findByKey(Screen screen, String key) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getContents() instanceof TranslatableContents title
                        && key.equals(title.getKey())).findFirst().orElseThrow();
    }

    private static boolean isConfigButton(AbstractWidget widget) {
        return widget.getMessage().getContents() instanceof TranslatableContents title
                && switch (title.getKey()) {
                    case "fml.menu.mods.config", "neoforge.screen.mods.button.config" -> true;
                    default -> false;
                };
    }

    private static void openWorld(Minecraft minecraft) {
        var flows = minecraft.createWorldOpenFlows();
        if (minecraft.getLevelSource().levelExists(WORLD)) {
            flows.openWorld(WORLD, () -> WidgetSmokePlatform.open(minecraft, title));
            return;
        }
        var settings = new LevelSettings(WORLD, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                false, WorldDataConfiguration.DEFAULT);
        flows.createFreshLevel(WORLD, settings, new WorldOptions(4_413L, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(), title);
    }

    static void pressKey(Minecraft minecraft, int key) {
        long window = minecraft.getWindow().handle();
        GLFWKeyCallback callback = GLFW.glfwSetKeyCallback(window, null);
        require(callback != null, "Minecraft registered a keyboard callback");
        // Restore the original before dispatch. Invoking it uses Minecraft's KeyboardHandler
        // and NeoForge input hooks without sending global OS keyboard events.
        GLFW.glfwSetKeyCallback(window, callback);
        int scanCode = GLFW.glfwGetKeyScancode(key);
        callback.invoke(window, key, scanCode, GLFW.GLFW_PRESS, 0);
        callback.invoke(window, key, scanCode, GLFW.GLFW_RELEASE, 0);
    }

    private static ControlPanelScreen requirePanel(Minecraft minecraft, String route) {
        var screen = WidgetSmokePlatform.screen(minecraft);
        if (!(screen instanceof ControlPanelScreen panel)) {
            throw new IllegalStateException("Panel entry smoke: " + route + " opened " + screen);
        }
        return panel;
    }

    private static void click(Screen screen, AbstractWidget widget) {
        require(widget.active && widget.visible, "enabled widget " + widget.getMessage().getString());
        click(screen, widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0);
    }

    private static void click(Screen screen, double x, double y) {
        var event = new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException("Panel entry smoke: " + description);
    }

    private enum Stage {
        START, TITLE, MOD_LIST, MOD_LIST_GALLERY, WAIT_WORLD, WAIT_F8, IN_WORLD_GALLERY, DONE
    }
}
