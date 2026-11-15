package com.dishanhai.sh_better_ui.client.jei;

import com.dishanhai.sh_better_ui.client.gui.layout.TooltipBounds;
import com.dishanhai.sh_better_ui.client.gui.layout.BoundedTooltipGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.IntFunction;

/** 山海：可选 JEI 客户端接口。调用方在客户端线程查询和绘制。 */
public final class JeiIntegration {
    public enum Kind { ITEM, FLUID }
    public enum Direction { OBTAINING, USES }

    public interface Ingredient {
        /** 当前运行期内区分素材变体的键，不能用注册 ID 替代。 */
        String key();
        String name();
        ResourceLocation id();
        String modName();
        List<String> aliases();
        default List<ResourceLocation> tags() { return List.of(); }
        /** 返回独立素材副本，未知类型或已失效的素材返回空值。 */
        default <T> Optional<T> copy(Class<T> type) {
            Objects.requireNonNull(type);
            return Optional.empty();
        }
        Kind kind();
        boolean visible();
        int width();
        int height();
        void render(GuiGraphics graphics, int x, int y);
        List<Component> tooltip();

        default void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
            List<Component> lines = tooltip();
            if (!lines.isEmpty()) {
                graphics.renderTooltip(Minecraft.getInstance().font, lines, Optional.empty(), mouseX, mouseY);
            }
        }

        /** Wraps and places the native ingredient tooltip before the exclusive right boundary. */
        default void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY, int rightBoundary) {
            int right = TooltipBounds.rightEdge(graphics.guiWidth(), rightBoundary);
            var bounded = new BoundedTooltipGraphics(graphics, right);
            graphics.flush();
            graphics.enableScissor(0, 0, right, graphics.guiHeight());
            try {
                renderTooltip(bounded, Math.min(mouseX, right), mouseY);
            } finally {
                bounded.flush();
                graphics.disableScissor();
            }
        }
    }

    public interface Recipe {
        Component title();
        Optional<RecipeLayout> layout();
    }

    public interface RecipeLayout {
        int width();
        int height();
        /** Full drawable bounds include JEI's background and border. */
        default int outerWidth() { return width(); }
        default int outerHeight() { return height(); }
        default int contentOffsetX() { return 0; }
        default int contentOffsetY() { return 0; }
        void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY);
        void renderOverlays(GuiGraphics graphics, int mouseX, int mouseY);
        void tick();
    }

    interface Backend {
        long revision();
        List<Ingredient> ingredients();
        List<Recipe> recipes(Ingredient ingredient, Direction direction, int limit);
        boolean open(Ingredient ingredient, Direction direction);
    }

    private static final Map<Screen, IntFunction<? extends Collection<Rect2i>>> EXTRA_AREAS = new WeakHashMap<>();
    private static Backend backend;
    private static long revision;
    private static long backendRevision;

    private JeiIntegration() {}

    public static boolean isAvailable() {
        return backend != null;
    }

    /** 安装、离开世界和素材变化都会使版本前进。素材快照应随版本变化重新获取。 */
    public static long revision() {
        if (backend != null) {
            long current = backend.revision();
            if (current != backendRevision) {
                backendRevision = current;
                revision++;
            }
        }
        return revision;
    }

    /** 返回当前 JEI 可见的物品和流体。JEI 不可用时返回空列表。 */
    public static List<Ingredient> ingredients() {
        return backend == null ? List.of() : backend.ingredients();
    }

    public static List<Recipe> recipes(Ingredient ingredient, Direction direction, int limit) {
        return backend == null || ingredient == null || direction == null || limit <= 0
                ? List.of() : backend.recipes(ingredient, direction, limit);
    }

    public static boolean open(Ingredient ingredient, Direction direction) {
        return backend != null && ingredient != null && direction != null && backend.open(ingredient, direction);
    }

    /** 供应器接收当前 GUI 高度。使用面板方法引用，避免供应器持有 Screen。传 null 可撤销注册。 */
    public static void setExtraAreas(Screen screen, IntFunction<? extends Collection<Rect2i>> provider) {
        Objects.requireNonNull(screen);
        if (provider == null) EXTRA_AREAS.remove(screen);
        else EXTRA_AREAS.put(screen, provider);
    }

    static Collection<Rect2i> extraAreas() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? List.of() : extraAreas(client.screen);
    }

    static Collection<Rect2i> extraAreas(Screen screen) {
        var provider = EXTRA_AREAS.get(screen);
        return provider == null ? List.of() : provider.apply(screen.height);
    }

    static void install(Backend replacement) {
        backend = replacement;
        backendRevision = replacement.revision();
        revision++;
    }

    static void uninstall(Backend current) {
        if (backend == current) {
            backend = null;
            revision++;
        }
    }
}
