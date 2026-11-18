// Author: dishanhai
package com.dishanhai.sh_better_ui.internal.fumo;

import com.dishanhai.sh_better_ui.ShBetterUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Supplies the plush when SH-Common-Lib is absent. */
public final class FumoRegistration {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ShBetterUi.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShBetterUi.MOD_ID);
    private static final net.neoforged.neoforge.registries.DeferredBlock<DishanhaiFumoBlock> FUMO =
            BLOCKS.registerBlock(DishanhaiFumoBlock.ID,
                    properties -> new DishanhaiFumoBlock(properties, FumoRegistration::fumoEntityType));
    private static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> FUMO_ITEM =
            ITEMS.registerSimpleBlockItem(FUMO);
    private static final net.neoforged.neoforge.registries.DeferredBlock<DishanhaiFumoBlock> BEIBEIPUO_FUMO =
            BLOCKS.registerBlock(DishanhaiFumoBlock.BEIBEIPUO_ID,
                    properties -> new DishanhaiFumoBlock(properties, FumoRegistration::fumoEntityType));
    private static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> BEIBEIPUO_FUMO_ITEM =
            ITEMS.registerSimpleBlockItem(BEIBEIPUO_FUMO);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ShBetterUi.MOD_ID);
    private static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DishanhaiFumoBlock.DanceEntity>> FUMO_ENTITY =
            BLOCK_ENTITIES.register(DishanhaiFumoBlock.ID,
                    () -> new BlockEntityType<>(FumoRegistration::newDanceEntity, FUMO.get(), BEIBEIPUO_FUMO.get()));

    private FumoRegistration() {}

    private static BlockEntityType<DishanhaiFumoBlock.DanceEntity> fumoEntityType() {
        return FUMO_ENTITY.get();
    }

    private static DishanhaiFumoBlock.DanceEntity newDanceEntity(BlockPos pos, BlockState state) {
        return new DishanhaiFumoBlock.DanceEntity(fumoEntityType(), pos, state);
    }

    public static void register(IEventBus bus) {
        if (ModList.get().isLoaded("sh_common_lib")) return;

        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.COLORED_BLOCKS)) {
                event.accept(FUMO_ITEM.get());
                event.accept(BEIBEIPUO_FUMO_ITEM.get());
            }
        });
        if (FMLEnvironment.getDist().isClient()) {
            bus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                    event.registerBlockEntityRenderer(FUMO_ENTITY.get(), FumoRenderer::new));
        }
    }
}
