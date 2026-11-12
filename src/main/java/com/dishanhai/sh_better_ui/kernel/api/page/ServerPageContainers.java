// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.internal.page.container.NativePageMenu;
import com.dishanhai.sh_better_ui.kernel.internal.page.container.PlatformPageMenus;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

/** Binds a declarative CONTAINER grid to a server-owned native inventory. */
public final class ServerPageContainers {
    private ServerPageContainers() {}

    public record SlotRule(Optional<ResourceLocation> acceptedItem, boolean mayInsert, boolean mayExtract) {
        public static final SlotRule ANY = new SlotRule(Optional.empty(), true, true);

        public SlotRule {
            Objects.requireNonNull(acceptedItem);
            if (acceptedItem.isPresent() && acceptedItem.orElseThrow().toString().length() > 256) {
                throw new IllegalArgumentException("Container filter ID exceeds 256 characters");
            }
        }
    }

    /** Uses persistent storage owned by the caller. The menu settles the cursor on close. */
    public static OptionalInt openPersistent(ServerPlayer player, UiPageDefinition page, Component title,
                                             Container storage, List<SlotRule> rules,
                                             Predicate<ServerPlayer> access) {
        var binding = NativePageMenu.Binding.from(page, rules);
        return open(player, title, binding, storage, access, false);
    }

    /** Creates private temporary storage and returns its contents on close or disconnect. */
    public static OptionalInt openTemporary(ServerPlayer player, UiPageDefinition page, Component title,
                                            List<SlotRule> rules, Predicate<ServerPlayer> access) {
        var binding = NativePageMenu.Binding.from(page, rules);
        return open(player, title, binding, new SimpleContainer(binding.rules().size()), access, true);
    }

    private static OptionalInt open(ServerPlayer player, Component title, NativePageMenu.Binding binding,
                                    Container storage, Predicate<ServerPlayer> access, boolean temporary) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(title);
        Objects.requireNonNull(storage);
        Objects.requireNonNull(access);
        if (!player.server.isSameThread()) throw new IllegalStateException("Native container requires the server thread");
        if (storage instanceof Inventory || storage.getContainerSize() != binding.rules().size()) {
            throw new IllegalArgumentException("Native container storage must match the declared slots");
        }
        if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()
                || player.isSpectator() || !player.isAlive() || player.hasDisconnected()
                || !storage.stillValid(player) || !access.test(player)) return OptionalInt.empty();
        if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()
                || !player.isAlive() || player.hasDisconnected()) return OptionalInt.empty();
        return PlatformPageMenus.open(player, title, binding, storage, access, temporary);
    }
}
