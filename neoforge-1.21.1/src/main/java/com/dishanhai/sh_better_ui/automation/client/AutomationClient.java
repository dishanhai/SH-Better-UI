// 作者：山海／dishanhai
package com.dishanhai.sh_better_ui.automation.client;

import com.dishanhai.sh_better_ui.automation.AutomationPage;
import com.dishanhai.sh_better_ui.automation.ae2.Ae2Compatibility;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

/** 客户端只负责发起显式页面请求和编辑草稿；所有操作由原服务端页面会话执行。 */
@EventBusSubscriber(modid = "sh_better_ui", value = Dist.CLIENT)
public final class AutomationClient {
    private static KeyMapping key;
    private static Screen decorated;
    private static final java.util.Set<Screen> attemptedTerminals = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private AutomationClient() {}

    @EventBusSubscriber(modid = "sh_better_ui", value = Dist.CLIENT)
    public static final class Keys {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            key = new KeyMapping("key.sh_better_ui.automation", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.sh_better_ui");
            event.register(key);
        }
    }

    public static UiId pageId() {
        String language = Minecraft.getInstance().options.languageCode;
        if (!language.equals("zh_cn") && !language.equals("zh_tw")) language = "en_us";
        return UiId.parse("sh_better_ui:automation/" + language);
    }

    private static UiId pageId(Screen owner) {
        var id = pageId();
        return supportedTerminal(owner) ? new UiId(id.namespace(), id.path() + "/terminal") : id;
    }

    public static void open(Screen owner) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.screen != owner) return;
        if (owner == null) ClientUiKernel.requestServerPage(client, pageId());
        else if (!ClientUiKernel.requestServerPage(client, pageId(owner), owner)) client.player.sendSystemMessage(
                Component.translatable("sh_better_ui.pages.request.unavailable", pageId(owner).toString()));
    }

    @SubscribeEvent
    public static void screenInit(ScreenEvent.Init.Post event) {
        var screen = event.getScreen();
        if (screen == decorated) decorated = null;
        if (supportedTerminal(screen)) {
            int x = Math.max(4, screen.width - 116);
            event.addListener(new UiButton(x, 6, 102, 20,
                    Component.translatable("sh_better_ui.automation.open"), ignored -> open(screen)));
            event.addListener(new UiButton(x, 30, 102, 20,
                    replacementLabel(screen), button -> {
                        boolean next = !AutomationClientPreferences.enabled("ae2", screen.getClass().getName());
                        if (AutomationClientPreferences.set("ae2", screen.getClass().getName(), next)) {
                            button.setMessage(replacementLabel(screen));
                            // A preference change takes effect on the next newly opened terminal.
                            attemptedTerminals.add(screen);
                        } else if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(
                                Component.translatable("sh_better_ui.automation.preference_failed"));
                    }));
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        while (key != null && key.consumeClick()) if (client.screen == null) open(null);
        var screen = client.screen;
        if (screen != null && !attemptedTerminals.contains(screen) && supportedTerminal(screen)
                && AutomationClientPreferences.enabled("ae2", screen.getClass().getName())
                && ClientUiKernel.requestServerPage(client, pageId(screen), screen)) {
            attemptedTerminals.add(screen);
        }
        if (screen != decorated && screen instanceof UiScreen ui) {
            ClientUiKernel.activeServerPage().filter(snapshot -> snapshot.pageId().namespace().equals("sh_better_ui")
                    && (snapshot.pageId().equals(AutomationPage.ID) || snapshot.pageId().path().startsWith("automation/")))
                    .ifPresent(snapshot -> {
                        decorated = screen;
                        int x = Math.max(8, ui.width - 202);
                        ui.add(new UiButton(x, ui.viewport().y() + 4, 94, 20,
                                Component.translatable("sh_better_ui.automation.visual"),
                                button -> AutomationEditor.open(ui, (UiButton) button, false)));
                        ui.add(new UiButton(x + 100, ui.viewport().y() + 4, 94, 20,
                                Component.translatable("sh_better_ui.automation.script"),
                                button -> AutomationEditor.open(ui, (UiButton) button, true)));
                    });
        }
        if (screen == null) decorated = null;
        if (client.level == null) attemptedTerminals.clear();
    }

    private static Component replacementLabel(Screen screen) {
        return Component.translatable("sh_better_ui.automation.default_replacement",
                Component.translatable(AutomationClientPreferences.enabled("ae2", screen.getClass().getName()) ? "options.on" : "options.off"));
    }

    private static boolean supportedTerminal(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return false;
        var client = Minecraft.getInstance();
        if (client.player == null || container.getMenu() != client.player.containerMenu) return false;
        String version = ModList.get().getModContainerById("ae2")
                .map(mod -> mod.getModInfo().getVersion().toString()).orElse("");
        String screenName = screen.getClass().getName();
        if (!Ae2Compatibility.VERSION.equals(version)
                || !screenName.equals("appeng.client.gui.me.common.MEStorageScreen")
                && !screenName.equals("appeng.client.gui.me.items.CraftingTermScreen")) return false;
        try {
            Object target = container.getMenu().getClass().getMethod("getTarget").invoke(container.getMenu());
            return target != null && Ae2Compatibility.supportsScreen(version, screenName,
                    container.getMenu().getClass().getName(), target.getClass().getName());
        } catch (ReflectiveOperationException | LinkageError incompatible) {
            return false;
        }
    }
}
