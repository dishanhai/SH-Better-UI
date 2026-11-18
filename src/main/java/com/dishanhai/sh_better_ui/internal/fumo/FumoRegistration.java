// Author: dishanhai
package com.dishanhai.sh_better_ui.internal.fumo;

import com.dishanhai.sh_better_ui.ShBetterUi;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Supplies the plush when SH-Common-Lib is absent. */
public final class FumoRegistration {
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ShBetterUi.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ShBetterUi.MOD_ID);
    private static final RegistryObject<DishanhaiFumoBlock> FUMO = BLOCKS.register(DishanhaiFumoBlock.ID,
            () -> new DishanhaiFumoBlock(BlockBehaviour.Properties.of(), FumoRegistration::fumoEntityType));
    private static final RegistryObject<BlockItem> FUMO_ITEM = ITEMS.register(DishanhaiFumoBlock.ID,
            () -> new BlockItem(FUMO.get(), new Item.Properties()));
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ShBetterUi.MOD_ID);
    private static final RegistryObject<BlockEntityType<DishanhaiFumoBlock.DanceEntity>> FUMO_ENTITY =
            BLOCK_ENTITIES.register(DishanhaiFumoBlock.ID,
                    () -> BlockEntityType.Builder.of(FumoRegistration::newDanceEntity, FUMO.get()).build(null));

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
            if (event.getTabKey().equals(CreativeModeTabs.COLORED_BLOCKS)) event.accept(FUMO_ITEM.get());
        });
        if (FMLEnvironment.dist == Dist.CLIENT) {
            bus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                    event.registerBlockEntityRenderer(FUMO_ENTITY.get(), FumoRenderer::new));
        }
    }
}
