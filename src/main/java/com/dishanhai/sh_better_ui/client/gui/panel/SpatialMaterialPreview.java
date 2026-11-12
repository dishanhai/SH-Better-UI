// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.spatial.SpatialRenderer;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;
import com.dishanhai.sh_better_ui.spatial.SpatialScene;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Light;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.LightType;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Material;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Quality;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Settings;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Shadow;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.ShadowType;
import com.dishanhai.sh_better_ui.spatial.internal.Scene;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Each gallery mount owns a scene with its own layout, lighting and shadow caches. */
final class SpatialMaterialPreview implements AutoCloseable {
    private static final UiId LIGHT = new UiId("sh_better_ui", "gallery/point");
    private final Bounds area;
    private final Scene scene = new Scene(RenderSystem::assertOnRenderThread);
    private final boolean lighting;

    private SpatialMaterialPreview(Bounds area, boolean lighting) {
        this.area = area;
        this.lighting = lighting;
    }

    static SpatialMaterialPreview surfaces(Bounds area) {
        var preview = new SpatialMaterialPreview(area, false);
        var materials = List.of(Material.FLAT, Material.SOFT_RAISED, Material.SOFT_INSET, Material.GLASS,
                Material.METALLIC, Material.HOLOGRAPHIC);
        for (int i = 0; i < materials.size(); i++) preview.tile(i, 3, 2, 3).material(materials.get(i));
        return preview;
    }

    static SpatialMaterialPreview shadows(Bounds area) {
        var preview = new SpatialMaterialPreview(area, false);
        var types = List.of(ShadowType.DROP, ShadowType.INNER, ShadowType.AMBIENT,
                ShadowType.CONTACT, ShadowType.DIRECTIONAL, ShadowType.GLOW);
        for (int i = 0; i < types.size(); i++) {
            var node = preview.tile(i, 3, 2, 5);
            node.material(Material.SOFT_RAISED);
            var type = types.get(i);
            node.shadows(List.of(new Shadow(type, 0, 3, 4, 1, .9,
                    type == ShadowType.GLOW ? 0xff85c7ff : 0xff0c1724, true)));
        }
        return preview;
    }

    static SpatialMaterialPreview lighting(Bounds area) {
        var preview = new SpatialMaterialPreview(area, true);
        for (int i = 0; i < 3; i++) preview.tile(i, 3, 1, 4).material(Material.METALLIC);
        return preview;
    }

    private SpatialScene.Node tile(int index, int columns, int rows, int gap) {
        int width = area.width() / columns, height = area.height() / rows;
        var shape = Shape.rounded(width - gap * 2, height - gap * 2, 4);
        var transform = new Transform(area.x() + index % columns * width + gap,
                area.y() + index / columns * height + gap, 1, 1, 0, 0, 0);
        return scene.create(new SpatialScene.Spec(new UiId("sh_better_ui", "gallery/tile/" + index),
                null, shape, transform, index, 0, false, false));
    }

    void draw(UiFrame frame) {
        NativeEffectPreviews.drawPattern(frame.graphics(), area);
        scene.settings(new Settings(true, !frame.preferences().highContrast(), true,
                frame.preferences().useBackdropBlur(), Quality.HIGH));
        if (lighting) {
            double x = area.x() + area.width() / 2.0, y = area.y() + area.height() / 2.0;
            var mouse = frame.mouse();
            if (mouse != null && mouse.available() && area.contains(mouse.x(), mouse.y())) {
                x = mouse.x();
                y = mouse.y();
            } else if (!frame.preferences().reducedMotion()) {
                x += Math.sin(frame.nowMs() / 440.0) * area.width() * .32;
            }
            scene.light(LIGHT, new Light(LightType.POINT, x, y, 24, 1.3, Math.max(36, area.width() * .38),
                    0xff8ccaff, 0, Math.PI));
        }
        var window = Minecraft.getInstance().getWindow();
        SpatialRenderer.draw(frame.graphics(), scene, window.getGuiScaledWidth(),
                window.getGuiScaledHeight(), window.getGuiScale());
    }

    @Override
    public void close() {
        scene.close();
    }
}
