// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.kernel.api.RegistrationHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.ServerPages;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionPrompt;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiErrorCode;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiOpenResult;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Optional server example. The demo credit account lives for one server run. */
final class ExampleServerPages {
    private static final UiId PAGE = ExchangePage.id("exchange");
    private static final ResourceBundle TEXT = ResourceBundle.getBundle("com.dishanhai.shbui_kernel_example.exchange");
    private static final Map<MinecraftServer, ExampleServerPages> SERVERS = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> credits = new HashMap<>();
    private final RegistrationHandle registration;

    private ExampleServerPages(MinecraftServer server) {
        var page = ExchangePage.create(PAGE, UiActionPolicy.CONFIRMED, TEXT::getString);
        registration = ServerPages.register(server, page, ExampleServerPages::mayExchange,
                Map.of(ExchangePage.REDEEM, ServerPages.ActionHandler.confirmed(ExampleServerPages::mayExchange,
                        action -> new UiActionPrompt(TEXT.getString("prompt.subject"), 1, TEXT.getString("prompt.effect")),
                        action -> redeem(action.player()))));
    }

    static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shbui-example-exchange").executes(command -> {
            ServerPlayer player = command.getSource().getPlayerOrException();
            var example = SERVERS.computeIfAbsent(command.getSource().getServer(), ExampleServerPages::new);
            example.open(player);
            return Command.SINGLE_SUCCESS;
        }));
    }

    static void stop(MinecraftServer server) {
        var example = SERVERS.remove(server);
        if (example != null) example.registration.close();
    }

    private void open(ServerPlayer player) {
        ServerPages.open(player, PAGE, ExchangePage.data(balance(player))).thenAccept(result -> {
            if (result.status() != UiOpenResult.Status.DISPLAYED && mayExchange(player)) {
                var code = result.error().orElse(UiErrorCode.INTERNAL_ERROR);
                player.sendSystemMessage(Component.literal(TEXT.getString("unavailable").formatted(code)));
            }
        });
    }

    private ServerPages.Outcome redeem(ServerPlayer player) {
        if (!mayExchange(player)) return ServerPages.Outcome.failed(UiErrorCode.PERMISSION_DENIED);
        int balance = balance(player);
        if (balance == 0) return ServerPages.Outcome.failed(UiErrorCode.ACTION_INVALID);
        int remaining = balance - 1;
        credits.put(player.getUUID(), remaining);
        player.giveExperiencePoints(1);
        return ServerPages.Outcome.success(ExchangePage.data(remaining));
    }

    private int balance(ServerPlayer player) {
        return credits.getOrDefault(player.getUUID(), ExchangePage.INITIAL_CREDITS);
    }

    private static boolean mayExchange(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && server.getPlayerList().getPlayer(player.getUUID()) == player && player.isAlive();
    }
}
