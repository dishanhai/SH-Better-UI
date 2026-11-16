package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.OwnershipLease;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Bounded Schwarzschild trace target, reused between screens; composited with actual texture bloom. */
final class BlackHoleBackdropRenderer extends RenderType {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static ShaderInstance traceShader, compositeShader;
    private static TextureTarget scene;
    private static OwnershipLease sceneLease;
    private static BlackHoleBackdropProfile cachedProfile;
    private static WeakReference<Object> cachedOwner = new WeakReference<>(null);
    private static float cachedPhase, cachedAspect;
    private static long tracedAt;
    private static int cachedWidth, cachedHeight;
    private static boolean failedUntilReload;
    private static final int CAPACITY = 384;
    private static final RenderType TRACE = create("sh_better_ui_black_hole_trace", DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> traceShader)).setTransparencyState(NO_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private static final RenderType COMPOSITE = create("sh_better_ui_black_hole_composite", DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> compositeShader)).setTransparencyState(WaveRenderState.ALPHA_BLEND)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private BlackHoleBackdropRenderer() {
        super("sh_better_ui_black_hole", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }
    private static void destroyScene() {
        if (compositeShader != null) compositeShader.setSampler("Scene", null);
        var previous = scene;
        scene = null;
        sceneLease = null;
        cachedProfile = null;
        cachedOwner.clear();
        if (previous != null) previous.destroyBuffers();
    }
    private static void releaseScene() {
        if (sceneLease != null) sceneLease.close();
        else destroyScene();
    }
    static void registerShaders(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register) {
        releaseScene();
        failedUntilReload = false;
        traceShader = null;
        compositeShader = null;
        register(resources, register, "black_hole_backdrop", loaded -> traceShader = loaded);
        register(resources, register, "black_hole_composite", loaded -> compositeShader = loaded);
    }
    private static void register(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register,
                                 String name, Consumer<ShaderInstance> loaded) {
        var id = ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, name);
        try { register.accept(new ShaderInstance(resources, id, DefaultVertexFormat.POSITION_TEX), loaded); }
        catch (IOException exception) { ShBetterUi.LOGGER.warn("Could not load GUI shader {}", id, exception); }
    }
    static boolean draw(GuiGraphics g, Bounds area, BlackHoleBackdropProfile p, float phase, Object owner) {
        if (failedUntilReload || traceShader == null || compositeShader == null) return false;
        if (area.width() <= 0 || area.height() <= 0 || p.opacity() <= 0) return true;
        if (!Float.isFinite(phase)) return false;
        float aspect = (float) area.width() / area.height();
        int cap = p.quality().resolution();
        float scale = Math.min(1f, (float) cap / Math.max(area.width(), area.height()));
        int width = Math.max(1, Math.round(area.width() * scale));
        int height = Math.max(1, Math.round(area.height() * scale));
        g.flush();
        WaveRenderState state = WaveRenderState.capture();
        int active = GlStateManager._getActiveTexture();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            long now = System.nanoTime();
            boolean changed = scene == null || cachedOwner.get() != owner || cachedWidth != width || cachedHeight != height
                    || !p.equals(cachedProfile) || aspect != cachedAspect;
            // Still scenes reuse exactly; animation has a maximum trace cadence of 30 Hz.
            if (changed || (phase != cachedPhase && now - tracedAt >= 33_333_333L)) {
                trace(g, area, p, phase, width, height);
                cachedProfile = p;
                if (cachedOwner.get() != owner) cachedOwner = new WeakReference<>(owner);
                cachedPhase = phase;
                cachedAspect = aspect;
                cachedWidth = width;
                cachedHeight = height;
                tracedAt = now;
            }
            compositeShader.setSampler("Scene", scene);
            compositeShader.safeGetUniform("ContentScale").set((float) width / CAPACITY, (float) height / CAPACITY);
            compositeShader.safeGetUniform("Finish").set(1f / width, 1f / height,
                    p.brightness() <= 0 ? 0 : p.glow(), p.opacity());
            VisualRevealRenderer.quad(g, COMPOSITE, area.x(), area.y(), area.width(), area.height(), g.pose().last().pose());
            return true;
        } catch (RuntimeException failure) {
            releaseScene();
            failedUntilReload = true;
            ShBetterUi.LOGGER.warn("Disabling black-hole render target until resource reload", failure);
            return false;
        } finally {
            compositeShader.setSampler("Scene", null);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(texture);
            RenderSystem.activeTexture(active);
            state.restore();
        }
    }
    private static void trace(GuiGraphics g, Bounds area, BlackHoleBackdropProfile p, float phase, int width, int height) {
        int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] viewport = new int[4], scissor = new int[4];
        float[] clear = new float[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
        boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (clipped) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        try {
            RenderSystem.disableScissor();
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            if (scene == null) {
                // One bounded allocation; alternating panels and quality settings never resize it.
                scene = new TextureTarget(CAPACITY, CAPACITY, false, Minecraft.ON_OSX);
                scene.setFilterMode(GL11.GL_LINEAR);
                sceneLease = ClientUiKernel.ownNativeGpu(ModuleDescriptor.ResourceKind.RENDER_TARGET,
                        UiId.parse("sh_better_ui:native/black_hole/scene"), BlackHoleBackdropRenderer::destroyScene);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
            RenderSystem.viewport(0, 0, width, height);
            traceShader.safeGetUniform("Surface").set((float) area.width() / area.height(), p.size(), phase, p.tilt());
            traceShader.safeGetUniform("Physics").set(p.inclination(), p.diskOuterRadius(), p.brightness(), p.outline());
            traceShader.safeGetUniform("Integration").set(p.quality().stepSize(), (float) p.quality().steps());
            color(traceShader, "InkColor", p.color());
            color(traceShader, "BackgroundColor", p.backgroundColor());
            VisualRevealRenderer.quad(g, TRACE, -1, -1, 2, 2, IDENTITY);
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack);
            var main = Minecraft.getInstance().getMainRenderTarget();
            if (main != null && main.frameBufferId == drawTarget) main.bindWrite(false);
            else GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.clearColor(clear[0], clear[1], clear[2], clear[3]);
            if (clipped) RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            else RenderSystem.disableScissor();
        }
    }
    private static void color(ShaderInstance shader, String name, int argb) {
        shader.safeGetUniform(name).set((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f,
                (argb & 255) / 255f, (argb >>> 24) / 255f);
    }
}
