// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.ServerPageContainers;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.internal.page.container.NativePageContainerScreen;
import com.dishanhai.sh_better_ui.kernel.internal.page.container.NativePageMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Integrated server smoke using the client's native container click packets. */
final class NativePageContainerSmoke {
    private static final UiId PAGE = UiId.parse("sh_better_ui:smoke/container");
    private static final int PLAYER_HOTBAR = 36;
    private enum Stage { OPEN, SYNC, SHIFT, FILTER, DRAG, CLOSE, SETTLED, VERIFIED }
    private enum Disconnect { OPEN, PICKUP, CHECK, LEAVE, REJOIN, COUNT, VERIFIED }
    private Stage stage = Stage.OPEN;
    private Disconnect disconnect = Disconnect.OPEN;
    private CompletableFuture<Void> pending;
    private NativePageMenu serverMenu;

    boolean advance(Minecraft client) {
        if (pending != null && !pending.isDone()) return false;
        if (pending != null) pending.join();
        switch (stage) {
            case OPEN -> {
                server(client, player -> {
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, new ItemStack(Items.STONE, 13));
                    player.getInventory().setItem(1, new ItemStack(Items.APPLE, 3));
                    var filter = new ServerPageContainers.SlotRule(
                            Optional.of(BuiltInRegistries.ITEM.getKey(Items.STONE)), true, true);
                    var rules = new ArrayList<ServerPageContainers.SlotRule>();
                    rules.add(filter);
                    rules.add(filter);
                    for (int i = 2; i < 9; i++) rules.add(new ServerPageContainers.SlotRule(Optional.empty(), false, false));
                    require(ServerPageContainers.openTemporary(player, definition(), Component.translatable("container.inventory"),
                            rules, candidate -> candidate == player).isPresent(), "Native container did not open");
                    serverMenu = (NativePageMenu) player.containerMenu;
                    require(ServerPageContainers.openTemporary(player, definition(), Component.translatable("container.inventory"),
                            rules, candidate -> true).isEmpty(), "Native container was replaced with an active transaction");
                });
                stage = Stage.SYNC;
            }
            case SYNC -> {
                if (!(client.screen instanceof NativePageContainerScreen view)
                        || view.getMenu().getSlot(PLAYER_HOTBAR).getItem().getCount() != 13) return false;
                require(view.getMenu().binding().pageId().equals(PAGE), "Container binding page ID changed");
                click(client, PLAYER_HOTBAR, 0, ClickType.QUICK_MOVE);
                stage = Stage.SHIFT;
            }
            case SHIFT -> {
                if (menu(client).getSlot(0).getItem().getCount() != 13) return false;
                click(client, PLAYER_HOTBAR + 1, 0, ClickType.PICKUP);
                click(client, 1, 0, ClickType.PICKUP);
                stage = Stage.FILTER;
            }
            case FILTER -> {
                var menu = menu(client);
                require(menu.getSlot(1).getItem().isEmpty() && menu.getCarried().is(Items.APPLE),
                        "Native slot filter accepted apples");
                click(client, PLAYER_HOTBAR + 1, 0, ClickType.PICKUP);
                click(client, 0, 1, ClickType.PICKUP);
                require(menu.getCarried().getCount() == 7, "Native right click did not split stack");
                click(client, -999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT);
                click(client, 0, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT);
                click(client, 1, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT);
                click(client, -999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT);
                stage = Stage.DRAG;
            }
            case DRAG -> {
                var menu = menu(client);
                require(menu.getSlot(0).getItem().getCount() == 9 && menu.getSlot(1).getItem().getCount() == 3
                                && menu.getCarried().getCount() == 1, "Native drag did not preserve thirteen items");
                server(client, player -> {
                    require(player.containerMenu == serverMenu, "Native server menu lost authority");
                    require(serverMenu.getSlot(0).getItem().getCount() == 9
                                    && serverMenu.getSlot(1).getItem().getCount() == 3
                                    && serverMenu.getCarried().getCount() == 1,
                            "Server did not receive the native quick-craft transaction");
                });
                stage = Stage.CLOSE;
            }
            case CLOSE -> {
                client.screen.onClose();
                stage = Stage.SETTLED;
            }
            case SETTLED -> {
                if (client.screen != null || client.player.containerMenu != client.player.inventoryMenu) return false;
                server(client, player -> {
                    require(player.containerMenu == player.inventoryMenu, "Server container did not close");
                    require(!serverMenu.stillValid(player) && serverMenu.getCarried().isEmpty(),
                            "Closed menu retained a cursor or accepted new input");
                    serverMenu.clicked(0, 0, ClickType.PICKUP, player);
                    serverMenu.removed(player);
                    require(count(player, Items.STONE) == 13 && count(player, Items.APPLE) == 3,
                            "Close or repeated settlement duplicated or lost items");
                    System.out.println("SHB_NATIVE_CONTAINER_SETTLED 13 stone 3 apple");
                });
                stage = Stage.VERIFIED;
            }
            case VERIFIED -> { return true; }
        }
        return false;
    }

    boolean disconnectAndRejoin(Minecraft client, KernelClientSmoke.Platform game, Screen parent, String world) {
        if (pending != null && !pending.isDone()) return false;
        if (pending != null) pending.join();
        switch (disconnect) {
            case OPEN -> {
                server(client, player -> {
                    player.getInventory().clearContent();
                    require(ServerPageContainers.openTemporary(player, definition(), Component.translatable("container.inventory"),
                            Collections.nCopies(9, ServerPageContainers.SlotRule.ANY), candidate -> true).isPresent(),
                            "Failed to open disconnect fixture");
                    serverMenu = (NativePageMenu) player.containerMenu;
                    serverMenu.getSlot(0).set(new ItemStack(Items.COPPER_INGOT, 5));
                    serverMenu.getSlot(1).set(new ItemStack(Items.COPPER_INGOT, 8));
                    serverMenu.broadcastChanges();
                });
                disconnect = Disconnect.PICKUP;
            }
            case PICKUP -> {
                if (!(client.screen instanceof NativePageContainerScreen)
                        || menu(client).getSlot(1).getItem().getCount() != 8) return false;
                click(client, 1, 0, ClickType.PICKUP);
                disconnect = Disconnect.CHECK;
            }
            case CHECK -> {
                if (menu(client).getCarried().getCount() != 8) return false;
                server(client, player -> require(serverMenu.getCarried().getCount() == 8
                        && serverMenu.getSlot(0).getItem().getCount() == 5, "Server did not receive cursor before logout"));
                disconnect = Disconnect.LEAVE;
            }
            case LEAVE -> {
                disconnect = Disconnect.REJOIN;
                game.leave(client, parent);
            }
            case REJOIN -> {
                if (client.player != null || client.getSingleplayerServer() != null) return false;
                disconnect = Disconnect.COUNT;
                game.rejoin(client, parent, world);
            }
            case COUNT -> {
                if (client.player == null || client.getSingleplayerServer() == null || client.screen != null) return false;
                server(client, player -> {
                    int dropped = player.serverLevel().getEntitiesOfClass(ItemEntity.class,
                                    player.getBoundingBox().inflate(12), item -> item.getItem().is(Items.COPPER_INGOT))
                            .stream().mapToInt(item -> item.getItem().getCount()).sum();
                    require(player.containerMenu == player.inventoryMenu, "Rejoin restored the previous menu");
                    require(serverMenu.getCarried().isEmpty() && serverMenu.getSlot(0).getItem().isEmpty()
                            && serverMenu.getSlot(1).getItem().isEmpty(), "Logout retained temporary items");
                    require(dropped + count(player, Items.COPPER_INGOT) == 13,
                            "Logout and rejoin duplicated or lost copper ingots");
                    System.out.println("SHB_NATIVE_CONTAINER_RECONNECT_SETTLED 13 copper");
                });
                disconnect = Disconnect.VERIFIED;
            }
            case VERIFIED -> { return true; }
        }
        return false;
    }

    private static UiPageDefinition definition() {
        return UiPageDefinition.builder(PAGE).type(UiPageType.CONTAINER)
                .root("inventory", "grid", grid -> {
                    grid.property("columns", new UiValue.Int(9));
                    for (int i = 0; i < 9; i++) {
                        int slot = i;
                        grid.child("slot_" + slot, "container_slot",
                                node -> node.property("slot", new UiValue.Int(slot)));
                    }
                }).build();
    }

    private static NativePageMenu menu(Minecraft client) {
        return (NativePageMenu) client.player.containerMenu;
    }

    private static void click(Minecraft client, int slot, int button, ClickType type) {
        client.gameMode.handleInventoryMouseClick(menu(client).containerId, slot, button, type, client.player);
    }

    private void server(Minecraft client, Consumer<ServerPlayer> verify) {
        var server = client.getSingleplayerServer();
        var id = client.player.getUUID();
        var future = new CompletableFuture<Void>();
        pending = future;
        server.execute(() -> {
            try {
                verify.accept(server.getPlayerList().getPlayer(id));
                future.complete(null);
            } catch (RuntimeException | AssertionError failure) {
                future.completeExceptionally(failure);
            }
        });
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
