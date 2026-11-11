package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 山海：以有上限的共享纹理先计算波面网格，再按 GUI pose 与裁切合成图案。 */
final class PatternWavesRenderer extends RenderType {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final int MAX_GRID = 512;
    private static final int TILE_SIZE = 32;
    private static final int ATLAS_COLUMNS = 8;
    private static ShaderInstance fieldShader;
    private static ShaderInstance markShader;
    private static TextureTarget field;
    private static DynamicTexture atlas;
    private static String atlasCharacters;
    private static com.dishanhai.sh_better_ui.kernel.api.OwnershipLease fieldLease;
    private static com.dishanhai.sh_better_ui.kernel.api.OwnershipLease atlasLease;

    private static void ownField() {
        if (fieldLease != null || field == null) return;
        fieldLease = com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.ownNativeGpu(
                com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind.RENDER_TARGET,
                com.dishanhai.sh_better_ui.kernel.api.UiId.parse("sh_better_ui:native/pattern_waves/field"), () -> {
                    var target = field;
                    field = null;
                    fieldLease = null;
                    if (target != null) target.destroyBuffers();
                });
    }

    private static void ownAtlas() {
        if (atlasLease != null || atlas == null) return;
        atlasLease = com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.ownNativeGpu(
                com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind.TEXTURE_RESOURCE,
                com.dishanhai.sh_better_ui.kernel.api.UiId.parse("sh_better_ui:native/pattern_waves/atlas"),
                PatternWavesRenderer::destroyAtlas);
    }

    private static void destroyAtlas() {
        var texture = atlas;
        atlas = null;
        atlasCharacters = null;
        atlasLease = null;
        if (texture != null) texture.close();
    }

    private static void releaseAtlas() {
        if (atlasLease != null) atlasLease.close();
        else destroyAtlas();
    }
    private static final RenderType FIELD_TYPE = create("sh_better_ui_pattern_waves_field", DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> fieldShader))
                    .setTransparencyState(NO_TRANSPARENCY).setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private static final RenderType MARK_TYPE = create("sh_better_ui_pattern_waves", DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> markShader))
                    .setTransparencyState(WaveRenderState.ALPHA_BLEND).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));

    private PatternWavesRenderer() {
        super("sh_better_ui_pattern_waves", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }

    static void registerShaders(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register) {
        register(resources, register, "pattern_waves_field", loaded -> {
            if (fieldLease != null) fieldLease.close();
            else if (field != null) {
                var target = field;
                field = null;
                target.destroyBuffers();
            }
            fieldShader = loaded;
        });
        register(resources, register, "pattern_waves", loaded -> {
            releaseAtlas();
            markShader = loaded;
        });
    }

    private static void register(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register,
                                 String path, Consumer<ShaderInstance> loaded) {
        var id = ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, path);
        try {
            register.accept(new ShaderInstance(resources, id, DefaultVertexFormat.POSITION_TEX), loaded);
        } catch (IOException exception) {
            loaded.accept(null);
            ShBetterUi.LOGGER.warn("Could not load GUI shader {}", id, exception);
        }
    }

    static boolean draw(GuiGraphics graphics, Bounds bounds, PatternWavesProfile profile, float phase, float intro,
                        long now, WavePointer.Ripple[] ripples) {
        if (fieldShader == null || markShader == null) return false;
        Grid grid = Grid.forBounds(bounds, profile);
        WaveRenderState state = WaveRenderState.capture();
        int textureUnit = GlStateManager._getActiveTexture();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.activeTexture(GL13.GL_TEXTURE1);
        int texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        try {
            graphics.flush();
            drawField(graphics, grid, bounds, profile, phase, intro, now, ripples);
            boolean glyphs = profile.pattern() == PatternWavesProfile.Pattern.GLYPH;
            markShader.setSampler("FieldSampler", field);
            if (glyphs) markShader.setSampler("GlyphSampler", atlas(profile.characters()));
            else markShader.setSampler("GlyphSampler", field);
            markShader.safeGetUniform("Area").set((float) bounds.width(), (float) bounds.height(), grid.pitchX, grid.pitchY);
            markShader.safeGetUniform("Grid").set(grid.columns, grid.rows);
            markShader.safeGetUniform("Pattern").set(profile.pattern().ordinal());
            markShader.safeGetUniform("GlyphCount").set(glyphs ? profile.characters().codePointCount(0, profile.characters().length()) : 0);
            markShader.safeGetUniform("MarkSize").set(profile.markSize());
            color("InkColor", profile.color());
            color("BackgroundColor", profile.backgroundColor());
            VisualRevealRenderer.quad(graphics, MARK_TYPE, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    graphics.pose().last().pose());
            return true;
        } finally {
            markShader.setSampler("FieldSampler", null);
            markShader.setSampler("GlyphSampler", null);
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);
            RenderSystem.bindTexture(texture1);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(texture0);
            RenderSystem.activeTexture(textureUnit);
            state.restore();
        }
    }

    private static void drawField(GuiGraphics graphics, Grid grid, Bounds bounds, PatternWavesProfile profile,
                                  float phase, float intro, long now, WavePointer.Ripple[] ripples) {
        int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        int[] scissor = new int[4];
        boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        if (clipped) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        boolean grow = field == null || field.width < grid.columns || field.height < grid.rows;
        float[] clear = grow ? new float[4] : null;
        if (grow) GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
        try {
            RenderSystem.disableScissor();
            if (field == null) field = new TextureTarget(grid.columns, grid.rows, false, Minecraft.ON_OSX);
            else if (grow) field.resize(Math.max(field.width, grid.columns), Math.max(field.height, grid.rows), Minecraft.ON_OSX);
            ownField();
            // 波面只写离屏颜色。避免通知光影管线宿主的主目标已被替换。
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, field.frameBufferId);
            RenderSystem.viewport(0, 0, grid.columns, grid.rows);
            configureField(grid, bounds, profile, phase, intro, now, ripples);
            VisualRevealRenderer.quad(graphics, FIELD_TYPE, -1, -1, 2, 2, IDENTITY);
        } finally {
            var minecraft = grow ? Minecraft.getInstance() : null;
            var main = minecraft == null ? null : minecraft.getMainRenderTarget();
            // TextureTarget 的创建/扩容会调用 bindWrite；主目标必须经同一入口恢复。
            if (main != null && main.frameBufferId == drawTarget) main.bindWrite(false);
            else GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (grow) RenderSystem.clearColor(clear[0], clear[1], clear[2], clear[3]);
            if (clipped) RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            else RenderSystem.disableScissor();
        }
    }

    private static void configureField(Grid grid, Bounds bounds, PatternWavesProfile profile, float phase, float intro,
                                       long now, WavePointer.Ripple[] ripples) {
        fieldShader.safeGetUniform("Area").set((float) bounds.width(), (float) bounds.height(), grid.pitchX, grid.pitchY);
        fieldShader.safeGetUniform("Grid").set(grid.columns, grid.rows);
        fieldShader.safeGetUniform("Shape").set(profile.wave().ordinal());
        fieldShader.safeGetUniform("Fade").set(profile.fade().ordinal());
        fieldShader.safeGetUniform("Surface").set(phase, profile.depth(), profile.scale(), intro);
        fieldShader.safeGetUniform("Lighting").set(profile.light(), profile.shine(), profile.contrast(), profile.direction());
        float background = (profile.backgroundColor() >>> 24) > 5 ? luminance(profile.backgroundColor()) : .5f;
        fieldShader.safeGetUniform("Finish").set(profile.opacity(), profile.fadeSize(),
                luminance(profile.color()) < background ? 1f : 0f, profile.cursorSize());
        for (int i = 0; i < ripples.length; i++) {
            WavePointer.Ripple ripple = ripples[i];
            var uniform = fieldShader.safeGetUniform("Ripple" + i);
            if (ripple == null || now - ripple.startedAt() >= WavePointer.RIPPLE_MS) uniform.set(0f, 0f, 0f, 0f);
            else uniform.set(ripple.x(), ripple.y(), Math.max(0, now - ripple.startedAt()) / (float) WavePointer.RIPPLE_MS,
                    ripple.strength() * profile.cursorStrength());
        }
    }

    private static float luminance(int argb) {
        return ((argb >> 16 & 255) * .2126f + (argb >> 8 & 255) * .7152f + (argb & 255) * .0722f) / 255f;
    }

    private static void color(String uniform, int argb) {
        markShader.safeGetUniform(uniform).set((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f,
                (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    private static DynamicTexture atlas(String characters) {
        if (characters.equals(atlasCharacters)) return atlas;
        int[] codePoints = characters.codePoints().toArray();
        int width = ATLAS_COLUMNS * TILE_SIZE;
        int height = (PatternWavesProfile.MAX_GLYPHS / ATLAS_COLUMNS) * TILE_SIZE;
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var paint = image.createGraphics();
        try {
            paint.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 25));
            paint.setColor(Color.WHITE);
            paint.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            var metrics = paint.getFontMetrics();
            int baseline = (TILE_SIZE + metrics.getAscent() - metrics.getDescent()) / 2;
            for (int i = 0; i < codePoints.length; i++) {
                String glyph = Character.toString(codePoints[i]);
                int x = (i % ATLAS_COLUMNS) * TILE_SIZE;
                int y = (i / ATLAS_COLUMNS) * TILE_SIZE;
                paint.setClip(x, y, TILE_SIZE, TILE_SIZE);
                paint.drawString(glyph, x + (TILE_SIZE - metrics.stringWidth(glyph)) / 2, y + baseline);
            }
        } finally {
            paint.dispose();
        }
        var texture = new DynamicTexture(width, height, true);
        NativeImage pixels = texture.getPixels();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) pixels.setPixelRGBA(x, y, image.getRGB(x, y) | 0xFFFFFF);
        }
        texture.upload();
        texture.setFilter(true, false);
        releaseAtlas();
        atlas = texture;
        atlasCharacters = characters;
        ownAtlas();
        return atlas;
    }

    private record Grid(float pitchX, float pitchY, int columns, int rows) {
        static Grid forBounds(Bounds bounds, PatternWavesProfile profile) {
            float pitchY = Math.max(profile.spacing(), bounds.height() / (MAX_GRID - 2f));
            float pitchX = profile.pattern() == PatternWavesProfile.Pattern.LINE ? pitchY * .25f : pitchY;
            pitchX = Math.max(pitchX, bounds.width() / (MAX_GRID - 2f));
            return new Grid(pitchX, pitchY, Math.min(MAX_GRID, (int) Math.ceil(bounds.width() / pitchX) + 2),
                    Math.min(MAX_GRID, (int) Math.ceil(bounds.height() / pitchY) + 2));
        }
    }
}
