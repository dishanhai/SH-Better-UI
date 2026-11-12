// Author: dishanhai
package com.dishanhai.shbui_exchange;

import com.dishanhai.sh_better_ui.bridge.api.UiBridge;
import com.dishanhai.sh_better_ui.bridge.api.UiBridgeProvider;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.page.UiDataSchema;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValueSchema;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionPrompt;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiErrorCode;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiOpenResult;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.ResourceBundle;

public final class ExchangeExample implements CommandExecutor, AutoCloseable {
    private static final UiId PAGE = UiId.parse("shbuiexchange:exchange");
    private static final UiId EXCHANGE = UiId.parse("shbuiexchange:redeem_credit");
    private static final String BALANCE = "player.balance";
    private static final String PERMISSION = "shbui.example.exchange";
    private static final String BRIDGE_PERMISSION = "shbetteruibridge.use";
    private static final int INITIAL_CREDITS = 5;
    private static final ResourceBundle TEXT = ResourceBundle.getBundle("com.dishanhai.shbui_exchange.messages");

    private final Plugin owner;
    private final UiBridge bridge;
    private final NamespacedKey creditKey;
    private final UiBridge.Registration registration;

    public ExchangeExample(Plugin owner) {
        this.owner = owner;
        bridge = UiBridgeProvider.get(owner);
        creditKey = new NamespacedKey(owner, "demo_credits");
        UiPageDefinition page = UiPageDefinition.builder(PAGE)
                .title(TEXT.getString("exchange.title"))
                .field(BALANCE, UiValueSchema.integer(0, INITIAL_CREDITS))
                .action(new UiActionDefinition(EXCHANGE, UiActionPolicy.CONFIRMED, UiDataSchema.EMPTY))
                .root("root", "column", root -> root
                        .child("reward", "text", node -> node.text(TEXT.getString("exchange.reward")))
                        .child("balance", "text", node -> node.bind("text", BALANCE))
                        .child("redeem", "button", node -> node.label(TEXT.getString("exchange.button")).action(EXCHANGE)))
                .build();
        registration = bridge.registerPage(owner, page, ExchangeExample::allowed,
                Map.of(EXCHANGE, UiBridge.ActionHandler.confirmed(context -> new UiActionPrompt(
                                TEXT.getString("exchange.prompt.subject"), 1, TEXT.getString("exchange.prompt.effect")),
                        context -> redeem(context.player()))));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TEXT.getString("exchange.player_only"));
            return true;
        }
        if (args.length == 2 && "redeem".equalsIgnoreCase(args[0]) && "confirm".equalsIgnoreCase(args[1])) {
            var result = redeem(player);
            player.sendMessage(result.error().isPresent()
                    ? TEXT.getString("exchange.failure")
                    : TEXT.getString("exchange.success").formatted(credits(player)));
            return true;
        }
        if (args.length != 0) return false;
        bridge.openPage(owner, player, PAGE, data(credits(player))).thenAccept(result -> {
            if (!player.isOnline() || !owner.isEnabled() || result.status() == UiOpenResult.Status.DISPLAYED) return;
            UiErrorCode error = result.error().orElse(UiErrorCode.INTERNAL_ERROR);
            if (error == UiErrorCode.CLIENT_UNAVAILABLE || error == UiErrorCode.PROTOCOL_INCOMPATIBLE) {
                player.sendMessage(TEXT.getString("exchange.fallback").formatted(credits(player)));
            } else {
                player.sendMessage(TEXT.getString("exchange.unavailable").formatted(error));
            }
        });
        return true;
    }

    @Override
    public void close() {
        registration.close();
    }

    private UiBridge.ActionResult redeem(Player player) {
        if (!allowed(player) || !player.hasPermission(BRIDGE_PERMISSION)) {
            return UiBridge.ActionResult.failure(UiErrorCode.PERMISSION_DENIED);
        }
        int balance = credits(player);
        if (balance == 0) return UiBridge.ActionResult.failure(UiErrorCode.ACTION_INVALID);
        int remaining = balance - 1;
        player.getPersistentDataContainer().set(creditKey, PersistentDataType.INTEGER, remaining);
        player.giveExp(1);
        return UiBridge.ActionResult.success(data(remaining));
    }

    private static boolean allowed(Player player) {
        return player.isOnline() && player.hasPermission(PERMISSION);
    }

    private int credits(Player player) {
        int value = player.getPersistentDataContainer().getOrDefault(creditKey, PersistentDataType.INTEGER, INITIAL_CREDITS);
        return Math.max(0, Math.min(value, INITIAL_CREDITS));
    }

    private static Map<String, UiValue> data(int balance) {
        return Map.of(BALANCE, new UiValue.Int(balance));
    }
}
