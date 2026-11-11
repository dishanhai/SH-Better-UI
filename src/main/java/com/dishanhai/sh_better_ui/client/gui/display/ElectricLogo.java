package com.dishanhai.sh_better_ui.client.gui.display;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** 山海：按图片轮廓绘制电丝、光晕和电弧。每个视图持有自己的动画状态及 GPU 贴图。 */
public final class ElectricLogo implements AutoCloseable {
    static final ResourceLocation SHADER_ID = ResourceLocation.tryParse("sh_better_ui:electric_logo");
    private static final String[] PULSE_UNIFORMS = {"Pulse0", "Pulse1", "Pulse2"};
    private static final float PULSE_LIFE = 1.8f;
    private static final float ARC_LIFE = 1.1f;
    private static final double MORPH_LIFE = 1.2;

    private ResourceLocation source;
    private ResourceLocation preparedSource;
    private ShaderInstance preparedFor;
    private ElectricLogoShape shape;
    private ElectricLogoShape previousShape;
    private double morphStarted;
    private static long nextField;
    private final long fieldIdentity = ++nextField;
    private DynamicTexture field;
    private com.dishanhai.sh_better_ui.kernel.api.OwnershipLease fieldLease;

    private void ownField() {
        if (field == null || fieldLease != null) return;
        fieldLease = com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.ownNativeGpu(
                com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind.TEXTURE_RESOURCE,
                new com.dishanhai.sh_better_ui.kernel.api.UiId("sh_better_ui", "native/electric_logo/" + fieldIdentity),
                this::destroyField);
    }

    private void destroyField() {
        fieldLease = null;
        var texture = field;
        field = null;
        if (texture != null) texture.close();
    }
    private int atlasWidth;
    private int atlasHeight;
    private boolean closed;

    private final Pulse[] pulses = new Pulse[PULSE_UNIFORMS.length];
    private int nextPulse;
    private long lastMillis = Long.MIN_VALUE;
    private double time;
    private double nextAmbient;
    private Arc ambient;
    private Arc burst;
    private View view;
    private boolean clickable;
    private boolean clickArcs;
    private float clickRadius;

    public ElectricLogo() {
        this(null);
    }

    /** 使用完整图片资源路径。传 null 使用内置闪电。 */
    public ElectricLogo(ResourceLocation source) {
        this.source = source;
    }

    /** 在客户端渲染线程换图。绘制时过渡到最新请求的轮廓，传 null 恢复内置闪电。 */
    public void setTexture(ResourceLocation source) {
        if (closed) return;
        RenderSystem.assertOnRenderThread();
        this.source = source;
    }

    public boolean draw(GuiGraphics graphics, int x, int y, int width, int height, double mouseX, double mouseY) {
        return draw(graphics, x, y, width, height, mouseX, mouseY,
                ThemeEffects.profile("electricLogo", ElectricLogoProfile.DEFAULT), UiPreferences.configured());
    }

    /**
     * 在渲染线程绘制，鼠标坐标与绘制区域使用同一 GUI 坐标系。传入 NaN 可关闭本帧悬停。
     * 返回 false 表示没有可绘制的轮廓或着色器，宿主可自行提供后备内容。
     */
    public boolean draw(GuiGraphics graphics, int x, int y, int width, int height, double mouseX, double mouseY,
                        ElectricLogoProfile profile, UiPreferences preferences) {
        clickable = false;
        if (closed || width <= 0 || height <= 0) return false;
        RenderSystem.assertOnRenderThread();
        ShaderInstance shader = GraphicIconShader.find(SHADER_ID);
        if (shader == null) return false;
        var interaction = profile.interaction();
        var motion = profile.motion();
        boolean reduced = preferences.reducedMotion();
        advance(PageTransition.nowMs(), motion.speed(), reduced);
        boolean animate = !reduced && motion.speed() > 0f;
        if (!animate || time - morphStarted >= MORPH_LIFE) previousShape = null;
        if (shader != preparedFor) prepare(shader, false);
        else if (previousShape == null && !Objects.equals(source, preparedSource)) prepare(shader, animate);
        if (shape == null) return false;

        view = new View(x, y, width, height, fit(shape, width, height, profile.lines().scale()));
        if (reduced) clearInteraction();
        else if (!interaction.enabled()) clearPulses();
        if (motion.arcs() == 0f) {
            ambient = null;
            burst = null;
            nextAmbient = time;
        } else if (animate && previousShape == null && time >= nextAmbient) {
            ambient = chooseArc(null, 0f);
            nextAmbient = time + (2.2 + ThreadLocalRandom.current().nextDouble()) / motion.arcs();
        }
        float charge = interaction.enabled() && view.contains(mouseX, mouseY) ? interaction.cursorIntensity() : 0f;
        drawQuad(graphics, shader, profile, preferences, mouseX, mouseY, charge);
        clickable = animate && interaction.enabled();
        clickArcs = previousShape == null && motion.arcs() > 0f;
        clickRadius = interaction.cursorRadius();
        return true;
    }

    private static float fit(ElectricLogoShape shape, int width, int height, float scale) {
        return Math.max(Float.MIN_NORMAL,
                Math.min((float) width / shape.logoWidth(), (float) height / shape.logoHeight()) * scale);
    }

    /** 由宿主转发主键按下。返回 true 表示已触发效果，宿主仍可处理自己的点击。 */
    public boolean pulse(double mouseX, double mouseY) {
        if (!clickable || !view.contains(mouseX, mouseY)) return false;
        Pulse pulse = new Pulse((float) ((mouseX - view.x) / view.width - 0.5),
                (float) ((mouseY - view.y) / view.height - 0.5), time);
        pulses[nextPulse] = pulse;
        nextPulse = (nextPulse + 1) % pulses.length;
        if (clickArcs) burst = chooseArc(pulse, clickRadius);
        return true;
    }

    private void advance(long now, float speed, boolean reduced) {
        if (!reduced && lastMillis != Long.MIN_VALUE) {
            time += Math.min(100L, Math.max(0L, now - lastMillis)) * 0.001 * speed;
        }
        lastMillis = now;
    }

    private void clearPulses() {
        Arrays.fill(pulses, null);
        nextPulse = 0;
        burst = null;
    }

    private void clearInteraction() {
        clickable = false;
        clearPulses();
        ambient = null;
        nextAmbient = time;
    }

    private void prepare(ShaderInstance shader, boolean animate) {
        releaseField();
        clearInteraction();
        preparedFor = shader;
        preparedSource = source;
        ElectricLogoShape from = shape;
        shape = readShape();
        previousShape = animate && shape != null ? from : null;
        morphStarted = time;
        if (shape == null) return;
        atlasWidth = shape.width();
        atlasHeight = shape.height();
        if (previousShape != null) {
            atlasWidth += previousShape.width();
            atlasHeight = Math.max(atlasHeight, previousShape.height());
        }
    }

    private ElectricLogoShape readShape() {
        if (source == null) {
            return ElectricLogoShape.bolt();
        }
        try (var input = Minecraft.getInstance().getResourceManager().open(source)) {
            var image = ImageIO.read(input);
            return image == null ? null : ElectricLogoShape.trace(image);
        } catch (IOException exception) {
            ShBetterUi.LOGGER.warn("Could not read electric logo texture {}", source, exception);
            return null;
        }
    }

    private DynamicTexture fieldTexture() {
        if (field != null) {
            ownField();
            return field;
        }
        DynamicTexture texture = new DynamicTexture(atlasWidth, atlasHeight, true);
        try {
            var image = Objects.requireNonNull(texture.getPixels());
            copyField(image, shape, 0);
            if (previousShape != null) copyField(image, previousShape, shape.width());
            texture.upload();
            texture.bind();
            texture.setFilter(true, false);
            field = texture;
            ownField();
            return field;
        } catch (RuntimeException | Error failure) {
            if (field == texture) field = null;
            texture.close();
            throw failure;
        }
    }

    private static void copyField(NativeImage image, ElectricLogoShape shape, int offsetX) {
        int[] argb = shape.argb();
        for (int y = 0; y < shape.height(); y++) {
            for (int x = 0; x < shape.width(); x++) {
                int pixel = argb[y * shape.width() + x];
                // NativeImage 接收 ABGR，距离场编码使用 R 高字节和 G 低字节。
                image.setPixelRGBA(offsetX + x, y, (pixel & 0xFF00FF00) | ((pixel >>> 16) & 255));
            }
        }
    }

    private void drawQuad(GuiGraphics graphics, ShaderInstance shader, ElectricLogoProfile profile,
                          UiPreferences preferences, double mouseX, double mouseY, float charge) {
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blending = GL11.glIsEnabled(GL11.GL_BLEND);
        int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        try {
            graphics.flush();
            if (!depthTest) RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            GraphicIconShader.drawTexturedQuad(graphics, shader, view.x, view.y, view.width, view.height,
                    this::fieldTexture, () -> uniforms(shader, profile, preferences, mouseX, mouseY, charge));
        } finally {
            RenderSystem.depthMask(depthWrite);
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
            RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            if (blending) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
        }
    }

    private void uniforms(ShaderInstance shader, ElectricLogoProfile profile, UiPreferences preferences,
                          double mouseX, double mouseY, float charge) {
        var palette = profile.palette();
        var motion = profile.motion();
        shader.safeGetUniform("Field").set((float) shape.width(), (float) shape.height(), view.scale,
                (float) ElectricLogoShape.DISTANCE_LIMIT);
        shader.safeGetUniform("AtlasSize").set((float) atlasWidth, (float) atlasHeight);
        if (previousShape == null) {
            shader.safeGetUniform("FromField").set(1f, 1f, 1f, 1f);
        } else {
            shader.safeGetUniform("FromField").set((float) previousShape.width(), (float) previousShape.height(),
                    fit(previousShape, view.width, view.height, profile.lines().scale()),
                    (float) ((time - morphStarted) / MORPH_LIFE));
        }
        shader.safeGetUniform("Region").set((float) view.width, (float) view.height,
                profile.lines().thickness(), (float) profile.lines().strands());
        shader.safeGetUniform("Effect").set((float) (time % 4096.0), motion.bend(), motion.crackle(),
                preferences.reducedMotion() ? 0f : motion.flicker());
        shader.safeGetUniform("Light").set(palette.intensity(),
                palette.glow() * (preferences.highContrast() ? 0.45f : 1f), palette.fill(),
                palette.theme() == ElectricLogoProfile.Theme.LIGHT ? 1f : 0f);
        color(shader, "CoreColor", palette.coreArgb());
        color(shader, "GlowColor", palette.glowArgb());
        shader.safeGetUniform("Pointer").set(charge == 0f ? 0f : (float) (mouseX - view.x - view.width * 0.5),
                charge == 0f ? 0f : (float) (mouseY - view.y - view.height * 0.5),
                profile.interaction().cursorRadius(), charge);
        for (int i = 0; i < pulses.length; i++) {
            Pulse pulse = pulses[i];
            float age = pulse == null ? PULSE_LIFE : (float) (time - pulse.born);
            if (age < PULSE_LIFE) {
                shader.safeGetUniform(PULSE_UNIFORMS[i]).set(pulse.x * view.width, pulse.y * view.height,
                        age / PULSE_LIFE, 1f);
            } else {
                pulses[i] = null;
                shader.safeGetUniform(PULSE_UNIFORMS[i]).set(0f, 0f, 0f, 0f);
            }
        }
        float ambientStrength = arcUniform(shader, "Arc0", ambient);
        float burstStrength = arcUniform(shader, "Arc1", burst);
        shader.safeGetUniform("ArcMotion").set(ambientStrength == 0f ? 0f : ambient.bow * view.scale,
                ambientStrength, burstStrength == 0f ? 0f : burst.bow * view.scale, burstStrength);
    }

    private static void color(ShaderInstance shader, String name, int argb) {
        shader.safeGetUniform(name).set((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f,
                (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    private float arcUniform(ShaderInstance shader, String name, Arc arc) {
        float age = arc == null ? ARC_LIFE : (float) (time - arc.born);
        if (age >= ARC_LIFE) {
            shader.safeGetUniform(name).set(0f, 0f, 0f, 0f);
            return 0f;
        }
        float centerX = shape.width() * 0.5f;
        float centerY = shape.height() * 0.5f;
        shader.safeGetUniform(name).set((arc.ax - centerX) * view.scale, (arc.ay - centerY) * view.scale,
                (arc.bx - centerX) * view.scale, (arc.by - centerY) * view.scale);
        return Math.min(1f, age * 12f + 0.35f) * (1f - age / ARC_LIFE);
    }

    private Arc chooseArc(Pulse focus, float focusRadius) {
        float[] edges = shape.edges();
        int count = edges.length / 2;
        if (count < 2) return null;
        var random = ThreadLocalRandom.current();
        float size = Math.max(shape.logoWidth(), shape.logoHeight());
        int start = random.nextInt(count);
        if (focus != null) {
            float fx = shape.width() * 0.5f + focus.x * view.width / view.scale;
            float fy = shape.height() * 0.5f + focus.y * view.height / view.scale;
            float best = focusRadius / view.scale;
            start = -1;
            for (int i = 0; i < count; i++) {
                float distance = (float) Math.hypot(edges[i * 2] - fx, edges[i * 2 + 1] - fy);
                if (distance < best) {
                    best = distance;
                    start = i;
                }
            }
            if (start < 0) return null;
        }
        float ax = edges[start * 2], ay = edges[start * 2 + 1];
        for (int attempt = 0; attempt < 24; attempt++) {
            int end = random.nextInt(count) * 2;
            float bx = edges[end], by = edges[end + 1];
            float dx = bx - ax, dy = by - ay;
            float length = (float) Math.hypot(dx, dy);
            if (length < size * 0.09f || length > size * 0.38f) continue;
            float bow = length * (0.28f + random.nextFloat() * 0.35f);
            float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f;
            float nx = -dy / length * bow, ny = dx / length * bow;
            int left = encodedDistance(mx + nx, my + ny);
            int right = encodedDistance(mx - nx, my - ny);
            if (Math.max(left, right) > 32768) return new Arc(ax, ay, bx, by, left > right ? bow : -bow, time);
        }
        return null;
    }

    private int encodedDistance(float x, float y) {
        int ix = Math.min(shape.width() - 1, Math.max(0, (int) x));
        int iy = Math.min(shape.height() - 1, Math.max(0, (int) y));
        return (shape.argb()[iy * shape.width() + ix] >>> 8) & 0xFFFF;
    }

    private void releaseField() {
        if (fieldLease != null) fieldLease.close();
        else destroyField();
    }

    /** 视图移除时在客户端渲染线程释放生成的 GPU 贴图。可重复调用。 */
    @Override
    public void close() {
        if (closed) return;
        if (field != null) RenderSystem.assertOnRenderThread();
        releaseField();
        closed = true;
        preparedFor = null;
        shape = null;
        previousShape = null;
        view = null;
        clearInteraction();
    }

    private record View(int x, int y, int width, int height, float scale) {
        boolean contains(double mouseX, double mouseY) {
            double localX = mouseX - x;
            double localY = mouseY - y;
            return localX >= 0.0 && localX < width && localY >= 0.0 && localY < height;
        }
    }

    private record Pulse(float x, float y, double born) {}
    private record Arc(float ax, float ay, float bx, float by, float bow, double born) {}
}
