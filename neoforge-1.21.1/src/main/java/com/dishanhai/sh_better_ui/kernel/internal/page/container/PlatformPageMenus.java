// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.page.container;

import com.dishanhai.sh_better_ui.ShBetterUi;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.OptionalInt;
import java.util.function.Predicate;

public final class PlatformPageMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ShBetterUi.MOD_ID);
    static final DeferredHolder<MenuType<?>, MenuType<NativePageMenu>> TYPE =
            MENUS.register("page_container", () -> IMenuTypeExtension.create(NativePageMenu::fromNetwork));

    private PlatformPageMenus() {}

    public static void register(IEventBus bus) { MENUS.register(bus); }

    public static OptionalInt open(ServerPlayer player, Component title, NativePageMenu.Binding binding,
                                   Container storage, Predicate<ServerPlayer> access, boolean temporary) {
        var provider = new SimpleMenuProvider((id, inventory, viewer) ->
                new NativePageMenu(id, inventory, binding, storage, access, temporary), title);
        return player.openMenu(provider, binding::write);
    }

    @EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Client {
        @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
            event.register(TYPE.get(), NativePageContainerScreen::new);
        }
    }
}
