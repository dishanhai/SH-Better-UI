// Author: dishanhai
package com.dishanhai.sh_better_ui.client.spatial;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Point;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.ShadowPath;
import com.dishanhai.sh_better_ui.spatial.SpatialScene.Node;
import com.dishanhai.sh_better_ui.spatial.SpatialUI.Support;
import com.dishanhai.sh_better_ui.spatial.internal.Scene;
import com.dishanhai.sh_better_ui.spatial.internal.Scene.Backdrop;
import com.dishanhai.sh_better_ui.spatial.internal.Scene.Paint;
import com.dishanhai.sh_better_ui.spatial.internal.SpatialMesh.Vertex;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** The game owns the shader and immediate vertex buffers. Only adjacent equivalent commands share a draw. */
public final class SpatialRenderer extends RenderType {
    private static final VertexFormat FORMAT = DefaultVertexFormat.POSITION_TEX_COLOR;
    // Each contour contributes its vertex count followed by its interleaved coordinates.
    private static final int PATH_FLOATS = 2 * ShadowPath.MAX_EDGES + ShadowPath.MAX_EDGES / 3;
    private static volatile ShaderInstance shader;
    private static final RenderType SDF = type("sh_better_ui_spatial", FORMAT, () -> shader);
    private static final RenderType FLAT = type("sh_better_ui_spatial_flat", DefaultVertexFormat.POSITION_COLOR,
            GameRenderer::getPositionColorShader);

    private SpatialRenderer() {
        super("sh_better_ui_spatial", FORMAT, VertexFormat.Mode.TRIANGLES, 256, false, false, () -> {}, () -> {});
    }

    private static RenderType type(String name, VertexFormat format, java.util.function.Supplier<ShaderInstance> shader) {
        return create(name, format, VertexFormat.Mode.TRIANGLES, 2048, false, false, CompositeState.builder()
                .setShaderState(new ShaderStateShard(shader))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setCullState(NO_CULL)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false));
    }

    public static void register(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register) {
        shader = null;
        try {
            var program = new ShaderInstance(resources,
                    ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "spatial"), FORMAT);
            for (String uniform : List.of("SizeMode", "Radii", "Shadow", "Surface", "Light")) {
                if (program.getUniform(uniform) == null) {
                    program.close();
                    throw new IOException("Missing spatial shader uniform " + uniform);
                }
            }
            register.accept(program, loaded -> shader = loaded);
        } catch (IOException | RuntimeException failure) {
            ShBetterUi.LOGGER.warn("Spatial SDF shader unavailable; using tessellated surfaces", failure);
        }
    }

    public static boolean ready() { return shader != null; }

    public static boolean arbitraryPathReady() { return pathUniform(shader) != null; }

    private static Uniform pathUniform(ShaderInstance program) {
        if (program == null) return null;
        var uniform = program.getUniform("PathVertices");
        return uniform != null && uniform.getCount() == PATH_FLOATS
                && uniform.getType() == Uniform.getTypeFromString("float") ? uniform : null;
    }

    public static Support gpuTimingSupport() { return SpatialGpuTiming.support(); }

    public static void releaseTiming() { SpatialGpuTiming.release(); }

    public static boolean backdropReady() {
        return ClientUiSettings.current().widgetBackdropBlur() && GaussBlur.isReady();
    }

    public static void draw(GuiGraphics graphics, Scene scene, int width, int height, double guiScale) {
        draw(graphics, scene, width, height, guiScale, null);
    }

    public static void draw(GuiGraphics graphics, Scene scene, int width, int height, double guiScale,
                            Consumer<Node> content) {
        RenderSystem.assertOnRenderThread();
        long start = System.nanoTime();
        try (var state = new RenderState()) {
            graphics.flush();
            var batch = new Batch(graphics, shader, content);
            try (var timer = scene.settings().profiling() ? SpatialGpuTiming.begin(scene) : null) {
                try {
                    scene.draw(width, height, guiScale, batch.program != null, batch.pathUniform != null,
                            batch, content != null);
                } finally {
                    batch.flush();
                }
            }
            scene.recordSubmission(batch.batches, batch.blurred ? 1 : 0, System.nanoTime() - start);
        }
    }

    /** Draws a filled mesh in the current GUI pose and scissor using the native position-color shader. */
    public static void drawFlatGeometry(GuiGraphics graphics, FilledGeometry<Local> geometry, int argb) {
        RenderSystem.assertOnRenderThread();
        if (geometry.triangles().isEmpty() || (argb >>> 24) == 0) return;
        if (GameRenderer.getPositionColorShader() == null) {
            throw new IllegalStateException("Native position-color shader is unavailable");
        }
        try (var state = new RenderState()) {
            graphics.flush();
            var color = RenderSystem.getShaderColor().clone();
            try {
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                var buffers = graphics.bufferSource();
                var out = buffers.getBuffer(FLAT);
                var matrix = graphics.pose().last().pose();
                try {
                    for (var triangle : geometry.triangles()) {
                        emitFlat(out, matrix, triangle.a(), argb);
                        emitFlat(out, matrix, triangle.b(), argb);
                        emitFlat(out, matrix, triangle.c(), argb);
                    }
                } finally {
                    buffers.endBatch(FLAT);
                }
            } finally {
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }

    private static void emitFlat(VertexConsumer out, Matrix4f pose, Point<Local> point, int color) {
        PlatformSpatialVertices.emit(out, pose, new Vertex(point.x(), point.y(), 0, 0), color, false);
    }

    private static final class Batch implements Scene.Sink {
        private final GuiGraphics graphics;
        private final ShaderInstance program;
        private final Uniform pathUniform;
        private final RenderType type;
        private final Consumer<Node> content;
        private Paint current;
        private int vertices;
        private int batches;
        private boolean attemptedBlur;
        private boolean blurred;
        private float[] pathValues;

        Batch(GuiGraphics graphics, ShaderInstance program, Consumer<Node> content) {
            this.graphics = graphics;
            this.program = program;
            pathUniform = pathUniform(program);
            this.content = content;
            type = program == null ? FLAT : SDF;
        }

        @Override public void emit(Paint paint, List<Vertex> polygon, Backdrop backdrop) {
            if (backdrop != null && !attemptedBlur && ClientUiSettings.current().widgetBackdropBlur()) {
                flush();
                attemptedBlur = true;
                blurred = GlassBlur.tryBackdrop(graphics,
                        new GuiRect(backdrop.x(), backdrop.y(), backdrop.width(), backdrop.height()), 1,
                        GlassStyle.NONE, backdrop.radius(), backdrop.corner());
            }
            if (vertices >= 32768 || program != null && !equivalent(current, paint)) flush();
            current = paint;
            var out = graphics.bufferSource().getBuffer(type);
            var matrix = graphics.pose().last().pose();
            var first = polygon.get(0);
            for (int i = 1; i < polygon.size() - 1; i++) {
                PlatformSpatialVertices.emit(out, matrix, first, paint.color(), program != null);
                PlatformSpatialVertices.emit(out, matrix, polygon.get(i), paint.color(), program != null);
                PlatformSpatialVertices.emit(out, matrix, polygon.get(i + 1), paint.color(), program != null);
                vertices += 3;
            }
        }

        @Override public void endNode(Node node) {
            flush();
            try {
                content.accept(node);
            } finally {
                graphics.flush();
            }
        }

        void flush() {
            if (vertices == 0) return;
            if (program != null) {
                var shape = current.shape();
                float mode = switch (current.type()) {
                    case SURFACE -> 0;
                    case INNER -> 2;
                    default -> current.clipNode() ? 3 : 1;
                };
                program.safeGetUniform("SizeMode").set((float) shape.width(), (float) shape.height(), mode,
                        (float) shape.kind().ordinal());
                program.safeGetUniform("Radii").set((float) shape.topLeft(), (float) shape.topRight(),
                        (float) shape.bottomRight(), (float) shape.bottomLeft());
                program.safeGetUniform("Shadow").set((float) current.x(), (float) current.y(),
                        (float) current.blur(), (float) current.spread());
                program.safeGetUniform("Surface").set(current.highlight(), current.border(), current.depth(),
                        current.roughness());
                program.safeGetUniform("Light").set(current.lightX(), current.lightY(), current.soft() ? 1 : 0,
                        setPath(current.shadowPath()));
            }
            vertices = 0;
            graphics.bufferSource().endBatch(type);
            batches++;
        }

        private int setPath(ShadowPath path) {
            if (path == null) return 0;
            if (pathValues == null) pathValues = new float[PATH_FLOATS];
            int cursor = 0;
            for (var contour : path.contours()) {
                pathValues[cursor++] = contour.size();
                for (var point : contour) {
                    pathValues[cursor++] = (float) point.x();
                    pathValues[cursor++] = (float) point.y();
                }
            }
            pathUniform.set(pathValues);
            return cursor;
        }

        private static boolean equivalent(Paint first, Paint second) {
            return first != null && first.shape().equals(second.shape()) && first.type() == second.type()
                    && Objects.equals(first.shadowPath(), second.shadowPath())
                    && first.x() == second.x() && first.y() == second.y() && first.blur() == second.blur()
                    && first.spread() == second.spread() && first.soft() == second.soft()
                    && first.clipNode() == second.clipNode() && first.highlight() == second.highlight()
                    && first.border() == second.border() && first.depth() == second.depth()
                    && first.roughness() == second.roughness() && first.lightX() == second.lightX()
                    && first.lightY() == second.lightY();
        }
    }

    /** RenderType restores vanilla defaults. Preserve caller state around the entire spatial pass. */
    private static final class RenderState implements AutoCloseable {
        private final ShaderInstance previous = RenderSystem.getShader();
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean writeDepth = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int rgbEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        private final int alphaEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        private final int colorMask;

        RenderState() {
            try (var stack = MemoryStack.stackPush()) {
                var channels = stack.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, channels);
                colorMask = (channels.get(0) != 0 ? 1 : 0) | (channels.get(1) != 0 ? 2 : 0)
                        | (channels.get(2) != 0 ? 4 : 0) | (channels.get(3) != 0 ? 8 : 0);
            }
        }

        @Override public void close() {
            RenderSystem.setShader(() -> previous);
            GlStateManager._glUseProgram(program);
            GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            GL20.glBlendEquationSeparate(rgbEquation, alphaEquation);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.depthFunc(depthFunc);
            RenderSystem.depthMask(writeDepth);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.colorMask((colorMask & 1) != 0, (colorMask & 2) != 0, (colorMask & 4) != 0, (colorMask & 8) != 0);
        }
    }
}
