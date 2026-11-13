package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.glass.GlassTooltip;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPullRefresh;
import com.dishanhai.sh_better_ui.client.gui.selection.IngredientValues;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import com.dishanhai.sh_better_ui.compat.ldlib2.LdLib2UiBridge;
import com.dishanhai.sh_better_ui.compat.ldlib2.LdLib2UiBridge.PullRefreshHandle;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.renderer.Rect2i;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.fml.ModList;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.time.Duration;

/** 山海：NeoForge 1.21.1 的原生类型与可选 LDLib2 接入。 */
public final class PlatformUiApi {
    /** GT 山海在场时由它负责旧格式的原版接入。 */
    public static boolean handlesLegacyText() {
        var mods = net.neoforged.fml.ModList.get();
        return mods != null && !mods.isLoaded("gt_shanhai");
    }

    PlatformUiApi() {}

    /** 山海：统一两平台原生控件的提示延迟，单位为毫秒。 */
    public static void setTooltipDelay(AbstractWidget widget, int delayMs) {
        widget.setTooltipDelay(Duration.ofMillis(delayMs));
    }

    /** 将滚轮事件传给原生控件，使用该版本支持的滚动方向。 */
    public static boolean scrollWidget(GuiEventListener child, double x, double y, double deltaX, double deltaY) {
        return child.mouseScrolled(x, y, deltaX, deltaY);
    }

    /** 山海：仅包含加载器元数据，不触发可选模组的类加载。 */
    public record ModInfo(String name, String version) {}

    public static String loaderId() {
        return "neoforge";
    }

    public static int loadedModCount() {
        return ModList.get().getMods().size();
    }

    public static Optional<ModInfo> modInfo(String modId) {
        return ModList.get().getMods().stream().filter(info -> info.getModId().equals(modId)).findFirst()
                .map(info -> new ModInfo(info.getDisplayName(), info.getVersion().toString()));
    }

    public Ingredient fluid(FluidStack stack) {
        return IngredientValues.fluid(stack);
    }

    public void renderTooltip(RenderTooltipEvent.Pre event) {
        GlassTooltip.render(event);
    }

    public void renderTooltip(RenderTooltipEvent.Pre event, boolean smoke) {
        GlassTooltip.render(event, smoke);
    }

    public LdLib ldlib() {
        return LdLib.INSTANCE;
    }

    /** 使用以下接口的客户端需安装 NeoForge LDLib2。 */
    public static final class LdLib {
        private static final LdLib INSTANCE = new LdLib();

        private LdLib() {}

        public UiOverlayHost overlay(Screen owner, UiPreferences preferences) {
            return LdLib2UiBridge.overlay(owner, preferences);
        }

        public Supplier<Rect2i> anchor(Screen owner, UIElement element) {
            return LdLib2UiBridge.anchor(owner, element);
        }

        /** 将把手加入客户端 UI 树，并将其 focusTarget 注册到宿主 Screen。 */
        public PullRefreshHandle createPullRefreshHandle(Screen owner, UIElement viewport, UiPullRefresh refresh,
                                                         BooleanSupplier atTop, UiPreferences preferences) {
            return LdLib2UiBridge.createPullRefreshHandle(owner, viewport, refresh, atTop, preferences);
        }
    }
}
