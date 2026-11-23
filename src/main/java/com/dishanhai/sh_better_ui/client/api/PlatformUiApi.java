package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.glass.GlassTooltip;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPullRefresh;
import com.dishanhai.sh_better_ui.client.gui.selection.IngredientValues;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import com.dishanhai.sh_better_ui.compat.ldlib.GlassPanelTexture;
import com.dishanhai.sh_better_ui.compat.ldlib.LdLibOverlays;
import com.dishanhai.sh_better_ui.compat.ldlib.LdLibPullRefreshWidget;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 山海：Forge 1.20.1 的原生类型与可选 LDLib 1.x 接入。 */
public final class PlatformUiApi {
    /** GT 山海在场时由它负责旧格式的原版接入。 */
    public static boolean handlesLegacyText() {
        var mods = net.minecraftforge.fml.ModList.get();
        return mods != null && !mods.isLoaded("gt_shanhai");
    }

    PlatformUiApi() {}

    /** 山海：统一两平台原生控件的提示延迟，单位为毫秒。 */
    public static void setTooltipDelay(AbstractWidget widget, int delayMs) {
        widget.setTooltipDelay(delayMs);
    }

    /** 将滚轮事件传给原生控件，使用该版本支持的滚动方向。 */
    public static boolean scrollWidget(GuiEventListener child, double x, double y, double deltaX, double deltaY) {
        return child.mouseScrolled(x, y, deltaY);
    }

    /** 山海：仅包含加载器元数据，不触发可选模组的类加载。 */
    public record ModInfo(String name, String version) {}

    public static String loaderId() {
        return "forge";
    }

    /** The loader's optional automation entry in the control center. */
    public Optional<Consumer<Screen>> controlPanelAutomation() {
        return Optional.empty();
    }

    /** Immutable metadata snapshot; no configuration factories or optional mod classes are loaded. */
    public static java.util.List<com.dishanhai.sh_better_ui.client.gui.panel.InstalledModData> loadedMods() {
        return ModList.get().getMods().stream().map(info -> new com.dishanhai.sh_better_ui.client.gui.panel.InstalledModData(
                info.getModId(), info.getDisplayName(), info.getVersion().toString(), info.getDescription())).toList();
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

    /** 使用以下接口的客户端需安装 Forge LDLib 1.x。 */
    public static final class LdLib {
        private static final LdLib INSTANCE = new LdLib();

        private LdLib() {}

        public GlassPanelTexture root() {
            return GlassPanelTexture.root();
        }

        public GlassPanelTexture panel() {
            return GlassPanelTexture.panel();
        }

        public GlassPanelTexture display() {
            return GlassPanelTexture.display();
        }

        public GlassPanelTexture page() {
            return GlassPanelTexture.page();
        }

        public GlassPanelTexture button() {
            return GlassPanelTexture.button();
        }

        public GlassPanelTexture clearPanel() {
            return GlassPanelTexture.clearPanel();
        }

        public GlassPanelTexture clearButton() {
            return GlassPanelTexture.clearButton();
        }

        public GlassPanelTexture clearButtonOverlay() {
            return GlassPanelTexture.clearButtonOverlay();
        }

        public GlassPanelTexture smokePanel() {
            return GlassPanelTexture.smokePanel();
        }

        public GlassPanelTexture smokeButtonOverlay() {
            return GlassPanelTexture.smokeButtonOverlay();
        }

        public LdLibOverlays overlay(ModularUIGuiContainer owner, UiPreferences preferences) {
            return new LdLibOverlays(owner, preferences);
        }

        /** 把手使用父 Widget 的局部布局坐标，放开范围使用绝对 GUI 坐标。 */
        public LdLibPullRefreshWidget createPullRefreshHandle(Bounds bounds, UiPullRefresh refresh,
                                                              UiPreferences preferences, BooleanSupplier atTop,
                                                              Supplier<Rect2i> releaseBounds, boolean keyboardShortcut) {
            return new LdLibPullRefreshWidget(bounds.x(), bounds.y(), bounds.width(), bounds.height(), refresh,
                    preferences, atTop, releaseBounds, keyboardShortcut);
        }
    }
}
