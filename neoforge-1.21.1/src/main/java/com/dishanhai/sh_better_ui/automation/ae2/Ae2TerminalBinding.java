// Author: dishanhai
package com.dishanhai.sh_better_ui.automation.ae2;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.PartHelper;
import appeng.menu.AEBaseMenu;
import appeng.parts.AEBasePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

/** A server-created grant to one physical terminal; never deserialize a client-supplied grant. */
public record Ae2TerminalBinding(UUID owner, ResourceKey<Level> dimension, BlockPos position,
                                 Direction side, String partClass, Object originalPart) {

    public Ae2TerminalBinding {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(dimension);
        position = Objects.requireNonNull(position).immutable();
        Objects.requireNonNull(side);
        Objects.requireNonNull(originalPart);
        if (!Ae2Compatibility.supportsPart(partClass)) throw new IllegalArgumentException("Unsupported terminal");
    }

    /** The caller must additionally verify its negotiated AE2 version before invoking this method. */
    public static Ae2TerminalBinding fromOpenMenu(ServerPlayer player, AccessPolicy policy) {
        requireServerThread(player);
        if (!(player.containerMenu instanceof AEBaseMenu menu)
                || !Ae2Compatibility.supportsMenu(menu.getClass().getName()) || !menu.stillValid(player)
                || menu.getPlayer() != player || !(menu.getTarget() instanceof AEBasePart part)
                || !Ae2Compatibility.supportsPart(part.getClass().getName())) {
            throw new IllegalStateException("Unsupported or invalid terminal menu");
        }
        var block = part.getHost().getBlockEntity();
        var binding = new Ae2TerminalBinding(player.getUUID(), player.level().dimension(),
                block.getBlockPos(), part.getSide(), part.getClass().getName(), part);
        if (binding.resolve(player, policy) == null) throw new IllegalStateException("Terminal access denied");
        return binding;
    }

    /** Never loads chunks; identity prevents a destroyed/replaced terminal inheriting an old grant. */
    public IGridNode resolve(ServerPlayer player, AccessPolicy policy) {
        requireServerThread(player);
        if (!owner.equals(player.getUUID()) || player.isRemoved() || player.isSpectator()
                || !dimension.equals(player.level().dimension()) || !player.serverLevel().hasChunkAt(position)
                || player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) > 64
                || !player.serverLevel().mayInteract(player, position) || !policy.mayUse(player, position)) return null;
        var block = player.serverLevel().getBlockEntity(position);
        if (!(originalPart instanceof AEBasePart original) || original.getHost().getBlockEntity() != block) return null;
        var host = original.getHost();
        var part = host.getPart(side);
        if (part != originalPart || !partClass.equals(part.getClass().getName())
                || !(part instanceof IActionHost actionHost)) return null;
        var node = actionHost.getActionableNode();
        return node != null && node.isActive() ? node : null;
    }

    public String targetKey() {
        return dimension.location() + "|" + position.asLong() + "|" + side.getName();
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("owner", owner);
        tag.putString("dimension", dimension.location().toString());
        tag.putLong("position", position.asLong());
        tag.putString("side", side.getName());
        tag.putString("partClass", partClass);
        return tag;
    }

    /** Trusted server save only. An online player must be reachable and permitted again before restoration. */
    public static Ae2TerminalBinding restore(ServerPlayer player, CompoundTag tag, AccessPolicy policy) {
        requireServerThread(player);
        var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension")));
        var position = BlockPos.of(tag.getLong("position"));
        var side = Direction.byName(tag.getString("side"));
        if (!player.getUUID().equals(tag.getUUID("owner")) || !player.level().dimension().equals(dimension)
                || !player.serverLevel().hasChunkAt(position) || side == null) return null;
        var part = PartHelper.getPart(player.serverLevel(), position, side);
        if (part == null || !tag.getString("partClass").equals(part.getClass().getName())) return null;
        var binding = new Ae2TerminalBinding(player.getUUID(), dimension, position, side,
                part.getClass().getName(), part);
        return binding.resolve(player, policy) == null ? null : binding;
    }

    static void requireServerThread(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("AE2 access requires server thread");
    }

    /** Integrators must supply any claim/protection permission checks used by their server. */
    @FunctionalInterface
    public interface AccessPolicy {
        boolean mayUse(ServerPlayer player, BlockPos position);
    }
}
