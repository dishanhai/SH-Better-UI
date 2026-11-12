// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.page.container;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.ServerPageContainers.SlotRule;
import com.dishanhai.sh_better_ui.kernel.api.page.UiBuiltinComponents;
import com.dishanhai.sh_better_ui.kernel.api.page.UiNodeFallback;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageValidator;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class NativePageMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 9;
    private static final int MAX_ROWS = 6;
    private final Binding binding;
    private final Container storage;
    private final Player viewer;
    private final Predicate<ServerPlayer> access;
    private final boolean temporary;
    private boolean removed;

    /** The grid order maps visual slots to the storage indices. */
    public record Binding(UiId pageId, List<Integer> indices, List<SlotRule> rules) {
        public Binding {
            indices = List.copyOf(indices);
            rules = List.copyOf(rules);
            int count = indices.size();
            if (count < COLUMNS || count > COLUMNS * MAX_ROWS || count % COLUMNS != 0
                    || rules.size() != count || new HashSet<>(indices).size() != count
                    || indices.stream().anyMatch(index -> index < 0 || index >= count)) {
                throw new IllegalArgumentException("Native container requires one to six complete rows of unique slots");
            }
        }

        public static Binding from(UiPageDefinition page, List<SlotRule> rules) {
            var root = page.root();
            if (page.type() != UiPageType.CONTAINER || !root.typeId().equals(UiBuiltinComponents.id("grid"))
                    || !root.properties().equals(Map.of("columns", new UiValue.Int(COLUMNS)))
                    || !root.bindings().isEmpty() || root.fallback() != UiNodeFallback.REJECT
                    || !page.actions().isEmpty() || !page.dataSchema().fields().isEmpty()
                    || !new UiPageValidator().validate(page, false).isEmpty()) {
                throw new IllegalArgumentException("Expected a CONTAINER with a static nine-column grid");
            }
            List<Integer> indices = new ArrayList<>();
            for (var node : root.children()) {
                if (!node.typeId().equals(UiBuiltinComponents.id("container_slot"))
                        || !node.bindings().isEmpty() || node.fallback() != UiNodeFallback.REJECT
                        || !node.properties().keySet().equals(Set.of("slot"))
                        || !(node.properties().get("slot") instanceof UiValue.Int index)) {
                    throw new IllegalArgumentException("Expected literal container_slot indices");
                }
                indices.add(index.value());
            }
            return new Binding(page.id(), indices, rules);
        }

        public int rows() {
            return indices.size() / COLUMNS;
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(pageId.toString(), 256);
            buffer.writeByte(indices.size());
            for (int index : indices) buffer.writeByte(index);
            for (var rule : rules) {
                buffer.writeBoolean(rule.mayInsert());
                buffer.writeBoolean(rule.mayExtract());
                buffer.writeBoolean(rule.acceptedItem().isPresent());
                rule.acceptedItem().ifPresent(item -> buffer.writeUtf(item.toString(), 256));
            }
        }

        static Binding read(FriendlyByteBuf buffer) {
            var id = UiId.parse(buffer.readUtf(256));
            int count = buffer.readUnsignedByte();
            if (count < COLUMNS || count > COLUMNS * MAX_ROWS || count % COLUMNS != 0) {
                throw new IllegalArgumentException("Invalid native container slot count");
            }
            List<Integer> indices = new ArrayList<>(count);
            for (int i = 0; i < count; i++) indices.add((int) buffer.readUnsignedByte());
            List<SlotRule> rules = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                boolean insert = buffer.readBoolean();
                boolean extract = buffer.readBoolean();
                Optional<ResourceLocation> item = buffer.readBoolean()
                        ? Optional.of(Objects.requireNonNull(ResourceLocation.tryParse(buffer.readUtf(256)),
                        "Malformed container item filter")) : Optional.empty();
                rules.add(new SlotRule(item, insert, extract));
            }
            return new Binding(id, indices, rules);
        }
    }

    public static NativePageMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        var binding = Binding.read(buffer);
        return new NativePageMenu(containerId, inventory, binding, new SimpleContainer(binding.indices().size()),
                ignored -> false, false);
    }

    NativePageMenu(int containerId, Inventory inventory, Binding binding, Container storage,
                   Predicate<ServerPlayer> access, boolean temporary) {
        super(PlatformPageMenus.TYPE.get(), containerId);
        this.binding = binding;
        this.storage = storage;
        this.viewer = inventory.player;
        this.access = access;
        this.temporary = temporary;
        for (int position = 0; position < binding.indices().size(); position++) {
            int index = binding.indices().get(position);
            var rule = binding.rules().get(index);
            addSlot(new Slot(storage, index, 8 + position % COLUMNS * 18, 18 + position / COLUMNS * 18) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return rule.mayInsert() && storage.canPlaceItem(index, stack)
                            && rule.acceptedItem().map(id -> id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())))
                            .orElse(true);
                }

                @Override public boolean mayPickup(Player player) { return rule.mayExtract(); }
            });
        }
        int inventoryTop = 31 + binding.rows() * 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                addSlot(new Slot(inventory, col + (row + 1) * COLUMNS, 8 + col * 18, inventoryTop + row * 18));
            }
        }
        for (int col = 0; col < COLUMNS; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, inventoryTop + 58));
        }
        storage.startOpen(viewer);
    }

    public Binding binding() { return binding; }

    @Override public boolean stillValid(Player player) {
        return !removed && player == viewer && player.isAlive() && storage.stillValid(player)
                && (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.hasDisconnected()
                && !serverPlayer.isSpectator() && access.test(serverPlayer))
                && !removed && player.containerMenu == this;
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (stillValid(player)) super.clicked(slot, button, type, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var source = slots.get(index);
        if (!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;
        var stack = source.getItem();
        var original = stack.copy();
        int boundary = binding.indices().size();
        if (index < boundary) {
            moveItemStackTo(stack, boundary, slots.size(), true);
        } else {
            moveToStorage(stack, boundary, true);
            moveToStorage(stack, boundary, false);
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) source.set(ItemStack.EMPTY);
        else source.setChanged();
        source.onTake(player, stack);
        return original;
    }

    private void moveToStorage(ItemStack stack, int end, boolean merge) {
        for (int index = 0; index < end && !stack.isEmpty(); index++) {
            var target = slots.get(index);
            if (target.hasItem() == merge && target.mayPlace(stack)) {
                moveItemStackTo(stack, index, index + 1, false);
            }
        }
    }

    @Override public void removed(Player player) {
        if (removed || player != viewer) return;
        removed = true;
        try {
            if (player instanceof ServerPlayer serverPlayer) {
                var cursor = getCarried();
                setCarried(ItemStack.EMPTY);
                settle(serverPlayer, cursor);
                if (temporary) {
                    for (int index = 0; index < storage.getContainerSize(); index++) {
                        settle(serverPlayer, storage.removeItemNoUpdate(index));
                    }
                }
            }
            super.removed(player);
        } finally {
            storage.stopOpen(player);
        }
    }

    private static void settle(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (player.isAlive() && !player.hasDisconnected()) {
            var inventory = player.getInventory();
            while (!stack.isEmpty()) {
                int slot = inventory.getSlotWithRemainingSpace(stack);
                if (slot < 0) slot = inventory.getFreeSlot();
                if (slot < 0) break;
                var present = inventory.getItem(slot);
                int capacity = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize()) - present.getCount();
                int count = Math.min(stack.getCount(), capacity);
                if (count <= 0) break;
                var part = stack.copyWithCount(count);
                inventory.add(slot, part);
                int transferred = count - part.getCount();
                if (transferred == 0) break;
                stack.shrink(transferred);
            }
        }
        // This is overflow or logout settlement, independent of voluntary toss cancellation.
        if (!stack.isEmpty()) player.drop(stack, false, false);
    }
}
