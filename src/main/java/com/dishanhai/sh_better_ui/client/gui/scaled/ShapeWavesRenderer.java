package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 山海：绘制几何波面，按文字和字体复用裁切纹理。 */
final class ShapeWavesRenderer extends RenderType {
    private static final int MAX_MASK_SIZE = 1024;
    private static final int MAX_CACHED_MASKS = 4;
    private static final FontRenderContext FONT_CONTEXT = new FontRenderContext(null, true, true);
    private static final LinkedHashMap<MaskKey, Mask> MASKS = new LinkedHashMap<>(MAX_CACHED_MASKS, .75f, true);
    private static ShaderInstance shader;
    private static final RenderType TYPE = create("sh_better_ui_shape_waves", DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader))
                    .setTransparencyState(WaveRenderState.ALPHA_BLEND)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE).createCompositeState(false));

    private ShapeWavesRenderer() {
        super("sh_better_ui_shape_waves", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }

    static void registerShaders(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register) {
        var id = ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "shape_waves");
        try {
            register.accept(new ShaderInstance(resources, id, DefaultVertexFormat.POSITION_TEX), loaded -> {
                java.util.List.copyOf(MASKS.values()).forEach(Mask::close);
                MASKS.clear();
                shader = loaded;
            });
        } catch (IOException exception) {
            shader = null;
            ShBetterUi.LOGGER.warn("Could not load GUI shader {}", id, exception);
        }
    }

    static boolean draw(GuiGraphics graphics, Bounds bounds, ShapeWavesProfile profile, float phase,
                        float driftXCells, float driftYCells, float intro, long now, WavePointer.Ripple[] ripples) {
        if (shader == null) return false;
        WaveRenderState state = WaveRenderState.capture();
        int activeTexture = GlStateManager._getActiveTexture();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            graphics.flush();
            configure(bounds, profile, phase, driftXCells, driftYCells, intro, now, ripples);
            VisualRevealRenderer.quad(graphics, TYPE, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    graphics.pose().last().pose());
            return true;
        } finally {
            shader.setSampler("MaskSampler", null);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(boundTexture);
            RenderSystem.activeTexture(activeTexture);
            state.restore();
        }
    }

    private static void configure(Bounds bounds, ShapeWavesProfile profile, float phase, float driftXCells,
                                  float driftYCells, float intro, long now, WavePointer.Ripple[] ripples) {
        Mask mask = profile.text().isBlank() ? Mask.EMPTY : mask(profile);
        if (mask.texture != null) {
            float height = Math.min(bounds.height() * profile.textSize(), bounds.width() * .9f / mask.aspect);
            float width = height * mask.aspect;
            shader.safeGetUniform("MaskRect").set((bounds.width() - width) * .5f, (bounds.height() - height) * .5f,
                    width, height);
        }
        shader.setSampler("MaskSampler", mask.texture);
        shader.safeGetUniform("Area").set((float) bounds.width(), (float) bounds.height(), profile.cellSize(), profile.dotSize());
        shader.safeGetUniform("Motion").set(phase, profile.scale(), driftXCells, driftYCells);
        shader.safeGetUniform("Tone").set(profile.contrast(), profile.brightness(), profile.fade(), profile.glow());
        shader.safeGetUniform("Effect").set(intro, profile.splashRadius(), mask.texture == null ? 0f : 1f, 0f);
        shader.safeGetUniform("ShapeMode").set(profile.shapes().ordinal());
        color("InkColor", profile.color());
        color("HoverColor", profile.hoverColor());
        color("BackgroundColor", profile.backgroundColor());
        for (int i = 0; i < ripples.length; i++) {
            var ripple = ripples[i];
            var uniform = shader.safeGetUniform("Ripple" + i);
            if (ripple == null || now - ripple.startedAt() >= WavePointer.RIPPLE_MS) uniform.set(0f, 0f, 0f, 0f);
            else uniform.set(ripple.x(), ripple.y(), Math.max(0, now - ripple.startedAt()) / (float) WavePointer.RIPPLE_MS,
                    ripple.strength() * profile.splashStrength());
        }
    }

    private static void color(String uniform, int argb) {
        shader.safeGetUniform(uniform).set((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f,
                (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    private static Mask mask(ShapeWavesProfile profile) {
        var key = new MaskKey(profile.text(), profile.fontFamily(), profile.fontWeight());
        Mask mask = MASKS.computeIfAbsent(key, ShapeWavesRenderer::createMask);
        if (MASKS.size() > MAX_CACHED_MASKS) {
            var entries = MASKS.values().iterator();
            Mask removed = entries.next();
            entries.remove();
            removed.close();
        }
        mask.own();
        return mask;
    }

    private static Mask createMask(MaskKey key) {
        float weight = key.weight <= 400 ? .5f + (key.weight - 100) / 600f : 1f + (key.weight - 400) / 500f;
        Font font = new Font(key.family, Font.PLAIN, 160).deriveFont(Map.of(TextAttribute.WEIGHT, weight));
        var text = new TextLayout(key.text, font, FONT_CONTEXT);
        var outline = text.getBounds();
        if (outline.isEmpty()) return Mask.EMPTY;

        double scale = Math.min(1, (MAX_MASK_SIZE - 2.0) / Math.max(outline.getWidth(), outline.getHeight()));
        int width = (int) Math.ceil(outline.getWidth() * scale) + 2;
        int height = (int) Math.ceil(outline.getHeight() * scale) + 2;
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var paint = image.createGraphics();
        try {
            paint.setColor(Color.WHITE);
            paint.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paint.translate(1, 1);
            paint.scale(scale, scale);
            paint.translate(-outline.getX(), -outline.getY());
            paint.fill(text.getOutline(null));
        } finally {
            paint.dispose();
        }
        var texture = new DynamicTexture(width, height, true);
        try {
            NativeImage pixels = texture.getPixels();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) pixels.setPixelRGBA(x, y, image.getRGB(x, y) | 0xFFFFFF);
            }
            texture.upload();
            texture.setFilter(true, false);
        } catch (RuntimeException exception) {
            texture.close();
            throw exception;
        }
        return new Mask(texture, width / (float) height);
    }

    private record MaskKey(String text, String family, int weight) {}

    private static long nextMask;

    private static final class Mask implements AutoCloseable {
        private static final Mask EMPTY = new Mask(null, 1);
        private final DynamicTexture texture;
        private final float aspect;
        private final long identity = ++nextMask;
        private com.dishanhai.sh_better_ui.kernel.api.OwnershipLease lease;
        private boolean closed;

        private Mask(DynamicTexture texture, float aspect) {
            this.texture = texture;
            this.aspect = aspect;
        }

        private void own() {
            if (texture == null || closed || lease != null) return;
            lease = com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.ownNativeGpu(
                    com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind.TEXTURE_RESOURCE,
                    new com.dishanhai.sh_better_ui.kernel.api.UiId("sh_better_ui", "native/shape_waves/mask/" + identity),
                    this::destroy);
        }

        private void destroy() {
            if (closed || texture == null) return;
            closed = true;
            lease = null;
            MASKS.values().removeIf(value -> value == this);
            texture.close();
        }

        @Override public void close() {
            if (lease != null) lease.close();
            else destroy();
        }
    }
}
