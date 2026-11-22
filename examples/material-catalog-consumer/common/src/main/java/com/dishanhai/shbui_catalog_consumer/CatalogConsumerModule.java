// Author: dishanhai
package com.dishanhai.shbui_catalog_consumer;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScreenService;
import com.dishanhai.sh_better_ui.kernel.api.TaskHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class CatalogConsumerModule implements UIModule {
    public static final String MOD_ID = "shbui_catalog_consumer";
    private static final String GLASS_ID = "sh_better_ui:liquid_glass";
    private static CatalogConsumerModule activeModule;

    private ModuleContext module;
    private ScreenService screens;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(new UiId(MOD_ID, "consumer")).version(UiVersion.parse("1.0.0"))
                .requestPermission(UIPermission.REGISTER_RENDER_PASS)
                .requiredCapability(UIApis.NATIVE_SCREEN, VersionRange.ANY)
                .requiredCapability(UIApis.NATIVE_RENDER, VersionRange.ANY)
                .requiredCapability(UIApis.MATERIAL_DISCOVERY, VersionRange.ANY)
                .build();
    }

    @Override public void register(ModuleContext context) {}

    @Override
    public void start(ModuleContext context) {
        module = context;
        screens = context.engine().require(UIApis.SCREENS);
        activeModule = this;
    }

    @Override
    public void stop(ModuleContext context) {
        if (activeModule == this) activeModule = null;
    }

    static void addTitleEntry(Screen screen, Consumer<GuiEventListener> add) {
        if (!(screen instanceof TitleScreen)) return;
        int width = Math.min(132, Math.max(1, screen.width - 16));
        add.accept(Button.builder(label("title"), ignored -> {
            if (activeModule != null) activeModule.open(screen);
        }).bounds(Math.max(0, screen.width - width - 8), 8, width, 20).build());
    }

    private void open(Screen parent) {
        if (module.engine().materialCatalog().find(GLASS_ID).isEmpty()) return;
        var page = new ConsumerPage();
        var title = label("title");
        var builder = ShBetterUiApi.screen(title).parent(parent)
                .returnFocus(() -> parent.children().stream()
                        .filter(child -> child instanceof Button button && button.getMessage().equals(title))
                        .findFirst().orElse(null))
                .onInit(page::init).onRender(page::render).onRemoved(ignored -> page.close());
        screens.create(module, builder).open();
    }

    private static Component label(String key) {
        return Component.translatable(MOD_ID + "." + key);
    }

    private final class ConsumerPage {
        private ScopeHandle scope;
        private TaskHandle paint;

        void init(UiScreen active) {
            if (scope == null || !scope.isOpen()) {
                scope = screens.mount(module, active, new UiId(MOD_ID, "glass_screen"));
            }
        }

        void render(UiScreen active, UiFrame frame) {
            if (scope == null || !scope.isOpen() || active.width < 64 || active.height < 48) return;
            var canvas = frame.canvas();
            int width = Math.min(212, active.width - 32);
            int x = (active.width - width) / 2;
            int y = (active.height - 30) / 2;
            canvas.fill(new Bounds(0, 0, active.width, active.height), 0xff172437);
            for (int band = 0; band < 10; band++) {
                int left = x + band * width / 10;
                int right = x + (band + 1) * width / 10;
                int color = band % 2 == 0 ? 0xff204677 : 0xff15273d;
                canvas.fill(new Bounds(left, y - 8, right - left, 46), color);
            }

            if (paint != null && !paint.status().isTerminal()) return;
            var region = GlassRegion.capsule(x, y, width, 30);
            paint = module.engine().require(UIApis.RENDER).submit(module, scope, RenderService.Surface.SCREEN,
                    RenderService.Layer.OVERLAY, 0, pass -> {
                        // shbui-material-anchor: liquid_glass region=region foreground=LIGHT
                        pass.text(label("caption"), x + 14, y + 11, 0xffffffff);
                    });
        }

        void close() {
            if (scope != null && scope.isOpen()) scope.close();
        }
    }
}
