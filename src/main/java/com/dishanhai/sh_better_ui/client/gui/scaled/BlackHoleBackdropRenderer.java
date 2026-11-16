package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Stationary physical hit maps are progressively cached; emission animates at every GUI frame. */
final class BlackHoleBackdropRenderer extends RenderType {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final int CAPACITY = 1024, BLOOM_CAPACITY = 256;
    private static final BlackHoleGeometryPlan.Budget BUDGET = new BlackHoleGeometryPlan.Budget(16_384);
    private static ShaderInstance traceShader, shadeShader, bloomShader, compositeShader;
    private static final Slot[] SLOTS = {new Slot(), new Slot()};
    private static TextureTarget scene, bloomA, bloomB;
    private static OwnershipLease bundleLease;
    private static boolean failedUntilReload;
    private static long useSequence;
    private static final RenderType TRACE = offscreen("trace", () -> traceShader);
    private static final RenderType SHADE = offscreen("shade", () -> shadeShader);
    private static final RenderType BLOOM = offscreen("bloom", () -> bloomShader);
    private static final RenderType COMPOSITE = create("sh_better_ui_black_hole_composite", DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> compositeShader)).setTransparencyState(WaveRenderState.ALPHA_BLEND)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private static final class Slot {
        TextureTarget texture;
        BlackHoleGeometryPlan.Key key;
        int rows;
        long used;
        boolean complete() { return key != null && rows == key.height(); }
    }
    private static RenderType offscreen(String name, java.util.function.Supplier<ShaderInstance> shader) {
        return create("sh_better_ui_black_hole_" + name, DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(shader)).setTransparencyState(NO_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    }
    private BlackHoleBackdropRenderer() {
        super("sh_better_ui_black_hole", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }
    private static void clearSamplers() {
        if (shadeShader != null) shadeShader.setSampler("Geometry", null);
        if (bloomShader != null) bloomShader.setSampler("Source", null);
        if (compositeShader != null) {
            compositeShader.setSampler("Scene", null);
            compositeShader.setSampler("Bloom", null);
        }
    }
    private static void destroyBundle() {
        clearSamplers();
        for (Slot slot : SLOTS) {
            if (slot.texture != null) slot.texture.destroyBuffers();
            slot.texture = null; slot.key = null; slot.rows = 0; slot.used = 0;
        }
        if (scene != null) scene.destroyBuffers();
        if (bloomA != null) bloomA.destroyBuffers();
        if (bloomB != null) bloomB.destroyBuffers();
        scene = bloomA = bloomB = null;
        bundleLease = null;
        BUDGET.clear();
    }
    private static void releaseBundle() {
        if (bundleLease != null) bundleLease.close();
        else destroyBundle();
    }
    static void registerShaders(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register) {
        releaseBundle(); failedUntilReload = false;
        traceShader = shadeShader = bloomShader = compositeShader = null;
        register(resources, register, "black_hole_backdrop", loaded -> traceShader = loaded);
        register(resources, register, "black_hole_shade", loaded -> shadeShader = loaded);
        register(resources, register, "black_hole_bloom", loaded -> bloomShader = loaded);
        register(resources, register, "black_hole_composite", loaded -> compositeShader = loaded);
    }
    private static void register(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register,
                                 String name, Consumer<ShaderInstance> loaded) {
        var id = ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, name);
        try { register.accept(new ShaderInstance(resources, id, DefaultVertexFormat.POSITION_TEX), loaded); }
        catch (IOException exception) { ShBetterUi.LOGGER.warn("Could not load GUI shader {}", id, exception); }
    }
    static boolean draw(GuiGraphics g, Bounds area, BlackHoleBackdropProfile p, float phase, Object owner) {
        if (failedUntilReload || traceShader == null || shadeShader == null || bloomShader == null || compositeShader == null) return false;
        if (area.width() <= 0 || area.height() <= 0 || p.opacity() <= 0) return true;
        if (!Float.isFinite(phase)) return false;
        var window = Minecraft.getInstance().getWindow();
        var pose = g.pose().last().pose();
        double pixelScaleX = (double) window.getWidth() / Math.max(1, window.getGuiScaledWidth());
        double pixelScaleY = (double) window.getHeight() / Math.max(1, window.getGuiScaledHeight());
        double xLength = Math.hypot(pose.m00() * pixelScaleX, pose.m01() * pixelScaleY);
        double yLength = Math.hypot(pose.m10() * pixelScaleX, pose.m11() * pixelScaleY);
        int pixelsX = Math.max(1, (int) Math.min(Integer.MAX_VALUE, Math.ceil(area.width() * xLength)));
        int pixelsY = Math.max(1, (int) Math.min(Integer.MAX_VALUE, Math.ceil(area.height() * yLength)));
        var key = BlackHoleGeometryPlan.key(p, pixelsX, pixelsY, (float) area.width() / area.height());
        g.flush();
        WaveRenderState state = WaveRenderState.capture();
        int active = GlStateManager._getActiveTexture();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.activeTexture(GL13.GL_TEXTURE1);
        int texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        try {
            Slot display = find(key);
            offscreen(g, key, p, phase);
            display = find(key);
            if (display == null) return false; // never display half-built or unrelated geometry
            display.used = ++useSequence;
            int width = key.width(), height = key.height();
            int bw = Math.min(BLOOM_CAPACITY, width);
            int bh = Math.max(1, Math.round((float) height * bw / width));
            if (bh > BLOOM_CAPACITY) { bh = BLOOM_CAPACITY; bw = Math.max(1, Math.round((float) width * bh / height)); }
            compositeShader.setSampler("Scene", scene);
            compositeShader.setSampler("Bloom", bloomB == null ? scene : bloomB);
            compositeShader.safeGetUniform("ContentScale").set((float) width / CAPACITY, (float) height / CAPACITY,
                    (float) bw / BLOOM_CAPACITY, (float) bh / BLOOM_CAPACITY);
            compositeShader.safeGetUniform("Finish").set(1f / width, 1f / height, p.brightness() <= 0 ? 0 : p.glow(), p.opacity());
            VisualRevealRenderer.quad(g, COMPOSITE, area.x(), area.y(), area.width(), area.height(), pose);
            return true;
        } catch (RuntimeException failure) {
            releaseBundle(); failedUntilReload = true;
            ShBetterUi.LOGGER.warn("Disabling black-hole targets until resource reload", failure);
            return false;
        } finally {
            clearSamplers();
            RenderSystem.activeTexture(GL13.GL_TEXTURE1); RenderSystem.bindTexture(texture1);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0); RenderSystem.bindTexture(texture0);
            RenderSystem.activeTexture(active);
            state.restore();
        }
    }
    private static Slot find(BlackHoleGeometryPlan.Key key) {
        for (Slot slot : SLOTS) if (slot.complete() && key.equals(slot.key)) return slot;
        return null;
    }
    private static TextureTarget allocate(int size, boolean geometry) {
        var target = new TextureTarget(size, size, false, Minecraft.ON_OSX);
        try {
        if (geometry) {
            RenderSystem.bindTexture(target.getColorTextureId());
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, size, size, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
        if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Unsupported black-hole target format");
        target.setFilterMode(geometry ? GL11.GL_NEAREST : GL11.GL_LINEAR);
        if (bundleLease == null) bundleLease = ClientUiKernel.ownNativeGpu(ModuleDescriptor.ResourceKind.RENDER_TARGET,
                UiId.parse("sh_better_ui:native/black_hole/progressive_bundle"), BlackHoleBackdropRenderer::destroyBundle);
        return target;
        } catch (RuntimeException failure) {
            target.destroyBuffers();
            throw failure;
        }
    }
    private static void bindTarget(TextureTarget target, int width, int height) {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
        RenderSystem.viewport(0, 0, width, height);
    }
    private static void offscreen(GuiGraphics g, BlackHoleGeometryPlan.Key requested, BlackHoleBackdropProfile p, float phase) {
        int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] viewport = new int[4], scissor = new int[4]; float[] clear = new float[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport); GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
        boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (clipped) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        try {
            RenderSystem.disableScissor(); RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            Slot building = null;
            for (Slot slot : SLOTS) if (slot.key != null && !slot.complete()) building = slot;
            // Never preempt a build when another panel requests different geometry.
            if (building == null && find(requested) == null) {
                building = SLOTS[0].key == null ? SLOTS[0] : SLOTS[1].key == null ? SLOTS[1]
                        : SLOTS[0].used <= SLOTS[1].used ? SLOTS[0] : SLOTS[1];
                building.key = requested; building.rows = 0;
            }
            if (building != null) {
                var key = building.key;
                int rows = BUDGET.takeRows(UiFrameScheduler.nowMs(), key.width(), key.height() - building.rows);
                if (rows > 0) {
                    if (building.texture == null) building.texture = allocate(CAPACITY, true);
                    bindTarget(building.texture, key.width(), key.height());
                    RenderSystem.enableScissor(0, building.rows, key.width(), rows);
                    traceShader.safeGetUniform("Surface").set(key.aspect(), key.size(), 0f, key.tilt());
                    traceShader.safeGetUniform("Physics").set(key.inclination(), key.diskOuterRadius(), 0f, 0f);
                    traceShader.safeGetUniform("Integration").set(key.stepSize(), (float) key.steps());
                    VisualRevealRenderer.quad(g, TRACE, -1, -1, 2, 2, IDENTITY);
                    building.rows += rows;
                    if (building.complete()) building.used = ++useSequence;
                    RenderSystem.disableScissor();
                }
            }
            Slot display = find(requested);
            if (display == null) return;
            int width = requested.width(), height = requested.height();
            if (scene == null) scene = allocate(CAPACITY, false);
            bindTarget(scene, width, height);
            shadeShader.setSampler("Geometry", display.texture);
            shadeShader.safeGetUniform("ContentScale").set((float) width / CAPACITY, (float) height / CAPACITY);
            shadeShader.safeGetUniform("Motion").set(phase, p.brightness(), p.outline(), 0f);
            color(shadeShader, "InkColor", p.color()); color(shadeShader, "BackgroundColor", p.backgroundColor());
            VisualRevealRenderer.quad(g, SHADE, -1, -1, 2, 2, IDENTITY);
            shadeShader.setSampler("Geometry", null);
            if (p.glow() > 0 && p.brightness() > 0) {
                if (bloomA == null) bloomA = allocate(BLOOM_CAPACITY, false);
                if (bloomB == null) bloomB = allocate(BLOOM_CAPACITY, false);
                float scale = Math.min(1f, (float) BLOOM_CAPACITY / Math.max(width, height));
                int bw = Math.max(1, Math.round(width * scale)), bh = Math.max(1, Math.round(height * scale));
                float spread = 1f + p.glow() * 1.5f;
                blur(g, scene, CAPACITY, width, height, bloomA, bw, bh, spread / bw, 0, .35f);
                blur(g, bloomA, BLOOM_CAPACITY, bw, bh, bloomB, bw, bh, 0, spread / bh, 0);
            }
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack);
            var main = Minecraft.getInstance().getMainRenderTarget();
            if (main != null && main.frameBufferId == drawTarget) main.bindWrite(false);
            else GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.clearColor(clear[0], clear[1], clear[2], clear[3]);
            if (clipped) RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]); else RenderSystem.disableScissor();
        }
    }
    private static void blur(GuiGraphics g, TextureTarget source, int capacity, int width, int height,
                             TextureTarget target, int tw, int th, float dx, float dy, float threshold) {
        bindTarget(target, tw, th);
        bloomShader.setSampler("Source", source);
        bloomShader.safeGetUniform("ContentScale").set((float) width / capacity, (float) height / capacity);
        bloomShader.safeGetUniform("Texel").set(1f / width, 1f / height);
        bloomShader.safeGetUniform("Filter").set(dx, dy, threshold, 0f);
        VisualRevealRenderer.quad(g, BLOOM, -1, -1, 2, 2, IDENTITY);
        bloomShader.setSampler("Source", null);
    }
    private static void color(ShaderInstance shader, String name, int argb) {
        shader.safeGetUniform(name).set((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f,
                (argb & 255) / 255f, (argb >>> 24) / 255f);
    }
}
