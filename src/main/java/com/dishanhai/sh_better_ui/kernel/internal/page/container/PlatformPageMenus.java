// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.page.container;

import com.dishanhai.sh_better_ui.ShBetterUi;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.OptionalInt;
import java.util.function.Predicate;

public final class PlatformPageMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ShBetterUi.MOD_ID);
    static final RegistryObject<MenuType<NativePageMenu>> TYPE =
            MENUS.register("page_container", () -> IForgeMenuType.create(NativePageMenu::fromNetwork));

    private PlatformPageMenus() {}

    public static void register(IEventBus bus) { MENUS.register(bus); }

    public static OptionalInt open(ServerPlayer player, Component title, NativePageMenu.Binding binding,
                                   Container storage, Predicate<ServerPlayer> access, boolean temporary) {
        var provider = new SimpleMenuProvider((id, inventory, viewer) ->
                new NativePageMenu(id, inventory, binding, storage, access, temporary), title);
        NetworkHooks.openScreen(player, provider, binding::write);
        return player.containerMenu instanceof NativePageMenu menu
                ? OptionalInt.of(menu.containerId) : OptionalInt.empty();
    }

    @Mod.EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Client {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> MenuScreens.register(TYPE.get(), NativePageContainerScreen::new));
        }
    }
}
