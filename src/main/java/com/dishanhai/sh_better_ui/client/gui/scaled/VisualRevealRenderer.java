package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur.RevealDirection;
import com.dishanhai.sh_better_ui.client.visual.VisualEffectResult;

import static com.dishanhai.sh_better_ui.client.visual.VisualEffectResult.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;

/** 山海：在 GUI 四边形内绘制独立贴图和径向渐变，资源重载由客户端管理。 */
@Mod.EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VisualRevealRenderer extends RenderType {
    private static ShaderInstance shader;
    private static ShaderInstance horizontalShader;
    private static TextureTarget scratch;
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final RenderType TYPE = create("sh_better_ui_visual_reveal", DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
    private static final RenderType HORIZONTAL = create("sh_better_ui_visual_reveal_horizontal",
            DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> horizontalShader))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private VisualRevealRenderer() {
        super("sh_better_ui_visual_reveal", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        LaserFlowRenderer.registerShaders(event.getResourceProvider(), event::registerShader);
        LightRaysRenderer.registerShaders(event.getResourceProvider(), event::registerShader);
        AntigravityRenderer.registerShaders(event.getResourceProvider(), event::registerShader);
        PatternWavesRenderer.registerShaders(event.getResourceProvider(), event::registerShader);
        ShapeWavesRenderer.registerShaders(event.getResourceProvider(), event::registerShader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "visual_reveal"), DefaultVertexFormat.POSITION_TEX),
                loaded -> shader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "visual_reveal_horizontal"),
                DefaultVertexFormat.POSITION_TEX),
                loaded -> {
                    releaseScratch();
                    horizontalShader = loaded;
                });
    }

    static void drawTexture(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                            RevealProfile profile, float radius) {
        drawTexture(graphics, texture, x, y, width, height, profile, radius, RevealDirection.LEFT_TO_RIGHT);
    }

    static VisualEffectResult drawTexture(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width,
                                          int height, RevealProfile profile, float radius, RevealDirection direction) {
        graphics.flush();
        if (shader == null) {
            // 归一化纹理范围是整张图，源尺寸与绘制尺寸彼此独立。
            graphics.blit(texture, x, y, width, height, 0f, 0f, 1, 1, 1, 1);
            return FALLBACK;
        }

        shader.safeGetUniform("Reveal").set(radius, profile.fullBlurUntil(), profile.sharpFrom());
        AbstractTexture source = Minecraft.getInstance().getTextureManager().getTexture(texture);
        shader.safeGetUniform("Direction").set(direction.ordinal());
        shader.setSampler("TextureSampler", source);
        try {
            boolean blurred = radius > 0f && horizontalShader != null
                    && blurHorizontally(graphics, source, width, height, profile, radius, direction);
            shader.safeGetUniform("EffectMode").set(blurred ? 0 : 3);
            shader.setSampler("ScratchSampler", blurred ? scratch : null);
            shader.safeGetUniform("Size").set((float) width, (float) height);
            quad(graphics, TYPE, x, y, width, height, graphics.pose().last().pose());
            return blurred ? DRAWN : FALLBACK;
        } finally {
            shader.setSampler("ScratchSampler", null);
            shader.setSampler("TextureSampler", null);
        }
    }

    static void drawRadialGradient(GuiGraphics graphics, int x, int y, int width, int height,
                                   float originU, float originV, int innerArgb, int outerArgb) {
        drawRadialGradient(graphics, x, y, width, height, originU, originV, 1f, innerArgb, outerArgb);
    }

    static VisualEffectResult drawRadialGradient(GuiGraphics graphics, float x, float y, float width, float height,
                                                 float originU, float originV, float radius,
                                                 int innerArgb, int outerArgb) {
        if (shader == null) return UNAVAILABLE;

        graphics.flush();
        shader.safeGetUniform("EffectMode").set(1);
        shader.safeGetUniform("Origin").set(originU, originV);
        shader.safeGetUniform("GradientRadius").set(radius);
        shader.safeGetUniform("Size").set((float) width, (float) height);
        color("InnerColor", innerArgb);
        color("OuterColor", outerArgb);
        quad(graphics, TYPE, x, y, width, height, graphics.pose().last().pose());
        return DRAWN;
    }

    private static com.dishanhai.sh_better_ui.kernel.api.OwnershipLease scratchLease;

    private static void ownScratch() {
        if (scratchLease != null || scratch == null) return;
        scratchLease = com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.ownNativeGpu(
                com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind.RENDER_TARGET,
                com.dishanhai.sh_better_ui.kernel.api.UiId.parse("sh_better_ui:native/visual_reveal/scratch"),
                VisualRevealRenderer::destroyScratch);
    }

    private static void releaseScratch() {
        var lease = scratchLease;
        if (lease != null) lease.close();
        else destroyScratch();
    }

    private static void destroyScratch() {
        scratchLease = null;
        if (scratch == null) return;
        int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            scratch.destroyBuffers();
            scratch = null;
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
            RenderSystem.bindTexture(boundTexture);
        }
    }

    private static boolean blurHorizontally(GuiGraphics graphics, AbstractTexture source, int width, int height,
                                            RevealProfile profile, float radius, RevealDirection direction) {
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int sourceWidth;
        int sourceHeight;
        try {
            source.bind();
            sourceWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            sourceHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        } finally {
            RenderSystem.bindTexture(boundTexture);
        }
        if (sourceWidth <= 0 || sourceHeight <= 0) return false;

        int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        int[] scissor = new int[4];
        float[] clear = new float[4];
        boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        if (clipped) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
        try {
            RenderSystem.disableScissor();
            if (scratch == null) {
                scratch = new TextureTarget(sourceWidth, sourceHeight, false, Minecraft.ON_OSX);
                linearScratchFilter();
            } else if (scratch.width < sourceWidth || scratch.height < sourceHeight) {
                scratch.resize(Math.max(scratch.width, sourceWidth), Math.max(scratch.height, sourceHeight),
                        Minecraft.ON_OSX);
                linearScratchFilter();
            }
            // 保留原始纹理分辨率。只更新当前贴图覆盖的区域，其余空间留给较大的贴图复用。
            ownScratch();
            scratch.bindWrite(false);
            RenderSystem.viewport(0, 0, sourceWidth, sourceHeight);
            horizontalShader.safeGetUniform("Size").set((float) width, (float) height);
            horizontalShader.safeGetUniform("Reveal").set(radius, profile.fullBlurUntil(), profile.sharpFrom());
            horizontalShader.safeGetUniform("Direction").set(direction.ordinal());
            horizontalShader.setSampler("TextureSampler", source);
            quad(graphics, HORIZONTAL, -1, -1, 2, 2, IDENTITY);
            return true;
        } finally {
            horizontalShader.setSampler("TextureSampler", null);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.clearColor(clear[0], clear[1], clear[2], clear[3]);
            RenderSystem.bindTexture(boundTexture);
            if (clipped) RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            else RenderSystem.disableScissor();
        }
    }

    private static void linearScratchFilter() {
        scratch.bindRead();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    /** 山海：跨平台的粒子顶点写入；由调用方统一提交批次。 */
    static void particleVertex(com.mojang.blaze3d.vertex.VertexConsumer vertices, Matrix4f pose,
                               float x, float y, float u, float v, int argb) {
        vertices.vertex(pose, x, y, 0f).uv(u, v).color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24).endVertex();
    }

    static ShaderInstance handoffShader() {
        return shader;
    }

    static void quad(GuiGraphics graphics, RenderType type, float x, float y, float width, float height, Matrix4f pose) {
        var vertices = graphics.bufferSource().getBuffer(type);
        float right = x + width;
        float bottom = y + height;
        vertices.vertex(pose, x, y, 0f).uv(0f, 0f).endVertex();
        vertices.vertex(pose, x, bottom, 0f).uv(0f, 1f).endVertex();
        vertices.vertex(pose, right, bottom, 0f).uv(1f, 1f).endVertex();
        vertices.vertex(pose, right, y, 0f).uv(1f, 0f).endVertex();
        // uniform 随对象更新，必须在下一次绘制覆盖它们前提交当前四边形。
        graphics.bufferSource().endBatch(type);
    }

    private static void color(String uniform, int argb) {
        shader.safeGetUniform(uniform).set((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f,
                (argb & 255) / 255f, (argb >>> 24) / 255f);
    }
}
