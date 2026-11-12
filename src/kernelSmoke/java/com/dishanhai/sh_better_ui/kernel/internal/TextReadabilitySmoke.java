// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Native Phase 0 fixture. Readback is used only to assert the final test image. */
public final class TextReadabilitySmoke extends Screen {
    private static final int BLACK = 0xff000000;
    private static final int WHITE = 0xffffffff;
    private static final int X = 24;
    private static final int CONTROL_Y = 30;
    private static final int EARLY_Y = 80;
    private static final int FOREGROUND_Y = 120;
    private static final int MARKER_X = 8;
    private static final int MARKER_Y = 8;
    private static final String GLYPHS = "MMMMMMMMMM";

    private static final String VERTEX = """
            #version 150
            void main() {
                vec2 p = vec2(float(gl_VertexID & 1), float(gl_VertexID >> 1));
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 150
            uniform sampler2D backdrop;
            uniform sampler2D glyphMask;
            uniform sampler2D previousFrame;
            uniform ivec2 marker;
            uniform vec4 bounds;
            uniform bool nextFrame;
            out vec4 color;

            void main() {
                vec2 p = gl_FragCoord.xy;
                if (any(lessThan(p, bounds.xy)) || any(greaterThanEqual(p, bounds.zw))) discard;
                ivec2 pixel = ivec2(p);
                vec4 background = texelFetch(backdrop, pixel, 0);
                float coverage = texelFetch(glyphMask, pixel, 0).r;
                float state = texelFetch(backdrop, marker, 0).r;
                float previous = texelFetch(previousFrame, marker, 0).r;
                bool stateValid = nextFrame ? state > 0.7 && previous < 0.3 : state < 0.3;
                float ink = background.r < 0.05 && stateValid ? 1.0 : 0.0;
                color = vec4(mix(background.rgb, vec3(ink), coverage), 1.0);
            }
            """;

    private final GlassSceneCapture first = capture();
    private final GlassSceneCapture second = capture();
    private final GlassSceneCapture mask = capture();
    private GlassSceneCapture.Snapshot previous;
    private int program;
    private int vao;
    private int frames;
    private int control;
    private int blurred;
    private Throwable failure;

    public TextReadabilitySmoke() {
        super(Component.translatable("gui.done"));
        GaussBlur.allowScreen(screen -> screen instanceof TextReadabilitySmoke);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (failure != null || frames >= 2) return;
        graphics.fill(0, 0, width, height, BLACK);
        graphics.fill(MARKER_X, MARKER_Y, MARKER_X + 3, MARKER_Y + 3, frames == 0 ? 0xff404040 : 0xffc0c0c0);
        graphics.drawString(font, GLYPHS, X, CONTROL_Y, WHITE, false);
        graphics.drawString(font, GLYPHS, X, EARLY_Y, WHITE, false);

        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        GaussBlur.request(0, pixel(60, scale), pixel(width, scale), pixel(88, scale),
                1, pixel(10, scale), 0, 0, 0, 0, GlassStyle.NONE);
        GaussBlur.deferTopLayer(this, afterBlur -> {
            try {
                drawForeground(afterBlur, scale);
                frames++;
            } catch (RuntimeException | AssertionError error) {
                failure = error;
            }
        });
    }

    private void drawForeground(GuiGraphics graphics, double scale) {
        require(GaussBlur.isReady(), "Frame-end blur is unavailable");
        if (program == 0) createProgram();
        var source = frames == 0 ? first : second;
        var backdrop = snapshot(source);
        var view = backdrop.viewport();
        int right = X + font.width(GLYPHS);
        int bottom = FOREGROUND_Y + font.lineHeight;
        graphics.fill(X, FOREGROUND_Y, right, bottom, BLACK);
        graphics.drawString(font, GLYPHS, X, FOREGROUND_Y, WHITE, false);
        graphics.flush();
        var glyphs = snapshot(mask);
        graphics.fill(X, FOREGROUND_Y, right, bottom, BLACK);
        graphics.flush();
        if (frames == 0) previous = backdrop;

        int markerX = pixel(MARKER_X + 1, scale);
        int markerY = view.height() - pixel(MARKER_Y + 1, scale) - 1;
        try (var draw = new GlassDrawState();
             var glyphTexture = new TextureUnit(1, glyphs.texture());
             var previousTexture = new TextureUnit(2, previous.texture())) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            draw.bind(program, vao, backdrop.texture());
            GL20.glUniform1i(uniform("backdrop"), 0);
            GL20.glUniform1i(uniform("glyphMask"), 1);
            GL20.glUniform1i(uniform("previousFrame"), 2);
            GL20.glUniform2i(uniform("marker"), markerX, markerY);
            GL20.glUniform1i(uniform("nextFrame"), frames);
            GL20.glUniform4f(uniform("bounds"), pixel(X, scale), view.height() - pixel(bottom, scale),
                    pixel(right, scale), view.height() - pixel(FOREGROUND_Y, scale));
            GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        }
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native text feasibility path generated a GL error");
    }

    public boolean verify(Minecraft client, Path output) throws IOException {
        if (failure != null) throw new AssertionError("Native text readability probe failed", failure);
        if (frames == 0) return false;
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            double scale = client.getWindow().getGuiScale();
            int currentControl = whitePixels(image, CONTROL_Y, scale);
            int currentBlurred = whitePixels(image, EARLY_Y, scale);
            int foreground = whitePixels(image, FOREGROUND_Y, scale);
            int background = image.getPixelRGBA(pixel(X - 2, scale), pixel(CONTROL_Y + 2, scale)) | 0xff000000;
            require(background == BLACK && currentControl > 0, "White-on-black control did not retain white");
            require(currentBlurred < currentControl / 4, "Frame-end blur did not substantially erase the early glyphs");
            require(foreground == currentControl, "Sampled post-effect glyphs lost white coverage in frame " + frames);
            if (frames == 1) {
                control = currentControl;
                blurred = currentBlurred;
                return false;
            }
            image.writeToFile(output.resolve("text-readability.png").toFile());
            Files.writeString(output.resolve("success.txt"), "Text readability native feasibility passed"
                    + "\nJava=" + Runtime.version()
                    + "\nGL renderer=" + GL11.glGetString(GL11.GL_RENDERER)
                    + "\nGL version=" + GL11.glGetString(GL11.GL_VERSION)
                    + "\nGLSL version=" + GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION)
                    + "\nFramebuffer=" + image.getWidth() + "x" + image.getHeight() + ", GUI scale=" + scale
                    + "\nWhite pixels (control/early/post-effect) frame 1=" + control + "/" + blurred + "/" + control
                    + ", frame 2=" + currentControl + "/" + currentBlurred + "/" + foreground + "\n");
            return true;
        }
    }

    @Override
    public void removed() {
        first.close();
        second.close();
        mask.close();
        GL20.glDeleteProgram(program);
        GL30.glDeleteVertexArrays(vao);
    }

    private int whitePixels(NativeImage image, int y, double scale) {
        int white = 0;
        for (int row = pixel(y, scale); row < pixel(y + font.lineHeight, scale); row++) {
            for (int col = pixel(X, scale); col < pixel(X + font.width(GLYPHS), scale); col++) {
                if ((image.getPixelRGBA(col, row) & 0xffffff) == 0xffffff) white++;
            }
        }
        return white;
    }

    private static int pixel(int gui, double scale) {
        return (int) Math.round(gui * scale);
    }

    private static GlassSceneCapture capture() {
        return new GlassSceneCapture((message, error) -> { throw new AssertionError(message, error); });
    }

    private static GlassSceneCapture.Snapshot snapshot(GlassSceneCapture capture) {
        capture.begin();
        try {
            var snap = capture.snapshot();
            require(snap != null && snap.viewport().x() == 0 && snap.viewport().y() == 0
                    && snap.viewport().width() == snap.size().width()
                    && snap.viewport().height() == snap.size().height(), "Fixture requires an unscaled origin-aligned capture");
            return snap;
        } finally {
            capture.end();
        }
    }

    private int uniform(String name) {
        return GL20.glGetUniformLocation(program, name);
    }

    private void createProgram() {
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        try {
            int fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
            try {
                program = GL20.glCreateProgram();
                GL20.glAttachShader(program, vertex);
                GL20.glAttachShader(program, fragment);
                GL20.glLinkProgram(program);
                require(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE,
                        GL20.glGetProgramInfoLog(program));
                vao = GL30.glGenVertexArrays();
            } finally {
                GL20.glDeleteShader(fragment);
            }
        } finally {
            GL20.glDeleteShader(vertex);
        }
    }

    private static int compile(int kind, String source) {
        int shader = GL20.glCreateShader(kind);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new AssertionError(log);
        }
        return shader;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class TextureUnit implements AutoCloseable {
        private final int unit;
        private final int texture;
        private final int sampler;

        TextureUnit(int unit, int texture) {
            this.unit = unit;
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            this.texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            sampler = GL.getCapabilities().glBindSampler == 0 ? -1 : GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            if (sampler >= 0) GL33.glBindSampler(unit, 0);
        }

        @Override
        public void close() {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            if (sampler >= 0) GL33.glBindSampler(unit, sampler);
        }
    }
}
