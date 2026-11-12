// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.Connection;

import java.util.Optional;
import java.util.function.Consumer;

final class ExampleClient {
    private static ConnectionExample connection;
    private static ExampleUiModule module;

    private ExampleClient() {}

    static void addTitleEntry(Screen screen, Consumer<GuiEventListener> add) {
        if (connection != null) connection.screen(screen);
        if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen)) return;
        int width = Math.min(142, Math.max(1, screen.width - 16));
        add.accept(Button.builder(ExampleUiModule.label("title"), button -> {
            if (!UnifiedUI.isAvailable()) return;
            var ui = UnifiedUI.get();
            if (ui.capabilities().supports(ExampleUiModule.CONTROLS)) {
                ui.require(CounterService.KEY).open(screen, () -> titleEntry(screen).orElse(null));
            }
        }).bounds(Math.max(0, screen.width - width - 8), 8, width, 20).build());
        add.accept(Button.builder(ExampleUiModule.label("rich_text.title"), button -> {
            if (module != null) module.openRichText(screen, () -> richTextEntry(screen).orElse(null));
        }).bounds(Math.max(0, screen.width - width - 8), 32, width, 20).build());
        if (module != null && module.hasDeclarativePages()) {
            add.accept(Button.builder(ExampleUiModule.label("pages.entry"), button -> {
                if (module != null) module.openDeclarativePages();
            }).bounds(Math.max(0, screen.width - width - 8), 56, width, 20).build());
        }
        if (module != null && module.hasTextReadability()) {
            add.accept(Button.builder(ExampleUiModule.label("readability.title"), button -> {
                if (module != null) module.openTextReadability(screen, () -> readabilityEntry(screen).orElse(null));
            }).bounds(Math.max(0, screen.width - width - 8), 80, width, 20).build());
        }
    }

    static void connection(ConnectionExample example) { connection = example; }
    static void module(ExampleUiModule example) { module = example; }

    static void joined(Connection network) {
        Minecraft.getInstance().execute(() -> {
            if (connection != null) connection.joined(network);
        });
    }

    static void left(Connection network) {
        Minecraft.getInstance().execute(() -> {
            if (connection != null) connection.left(network);
        });
    }

    static Optional<Button> titleEntry(Screen screen) {
        return entry(screen, "title");
    }

    static Optional<Button> richTextEntry(Screen screen) {
        return entry(screen, "rich_text.title");
    }

    static Optional<Button> readabilityEntry(Screen screen) {
        return entry(screen, "readability.title");
    }

    private static Optional<Button> entry(Screen screen, String label) {
        return screen.children().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getMessage().equals(ExampleUiModule.label(label)))
                .findFirst();
    }
}
