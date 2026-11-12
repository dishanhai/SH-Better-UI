// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial.Foreground;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassQuality;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Owns one linked program and the empty VAO used for vertex-ID quads. */
final class GlassProgram implements AutoCloseable {
    private final int program, vertices;
    private final int backdrop, viewport, quad, geometry, radius, optics, material, light, flatColor, mode, layerDepth;
    private final int opticalProfile, foregroundBounds, blurMaxLod;

    private GlassProgram(int program, int vertices) throws IOException {
        this.program = program;
        this.vertices = vertices;
        backdrop = uniform("Backdrop");
        viewport = uniform("Viewport");
        quad = uniform("Quad");
        geometry = uniform("Geometry");
        radius = uniform("Radius");
        optics = uniform("Optics");
        material = uniform("Material");
        light = uniform("Light");
        flatColor = uniform("FlatColor");
        mode = uniform("Mode");
        layerDepth = uniform("LayerDepth");
        // Shader packs may omit the optional optical refinements.
        opticalProfile = GL20.glGetUniformLocation(program, "OpticalProfile");
        foregroundBounds = GL20.glGetUniformLocation(program, "ForegroundBounds");
        blurMaxLod = GL20.glGetUniformLocation(program, "BlurMaxLod");
    }

    static GlassProgram load(ResourceProvider resources) throws IOException {
        RenderSystem.assertOnRenderThread();
        if (!GL.getCapabilities().OpenGL32) {
            throw new IOException("GLSL 150 is required, driver reports " + GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));
        }
        int vertex = 0, fragment = 0, program = 0, vao = 0;
        boolean loaded = false;
        try {
            vertex = compile(resources, "managed_glass.vsh", GL20.GL_VERTEX_SHADER);
            fragment = compile(resources, "managed_glass.fsh", GL20.GL_FRAGMENT_SHADER);
            program = GL20.glCreateProgram();
            if (program == 0) throw new IOException("Could not create the Liquid Glass program");
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IOException("Could not link Liquid Glass: " + GL20.glGetProgramInfoLog(program));
            }
            vao = GL30.glGenVertexArrays();
            if (vao == 0) throw new IOException("Could not create the Liquid Glass VAO");
            var result = new GlassProgram(program, vao);
            GL20.glDetachShader(program, vertex);
            GL20.glDetachShader(program, fragment);
            loaded = true;
            return result;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
            if (!loaded) {
                if (program != 0) GL20.glDeleteProgram(program);
                if (vao != 0) GL30.glDeleteVertexArrays(vao);
            }
        }
    }

    private static int compile(ResourceProvider resources, String filename, int kind) throws IOException {
        String source;
        try (var input = resources.open(ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "shaders/" + filename))) {
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        int shader = GL20.glCreateShader(kind);
        if (shader == 0) throw new IOException("Could not create " + filename);
        boolean compiled = false;
        try {
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                throw new IOException(filename + ": " + GL20.glGetShaderInfoLog(shader));
            }
            compiled = true;
            return shader;
        } finally {
            if (!compiled) GL20.glDeleteShader(shader);
        }
    }

    private int uniform(String name) throws IOException {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0) throw new IOException("Missing Liquid Glass uniform " + name);
        return location;
    }

    void bind(GlassDrawState state, GlassSceneCapture.Snapshot scene, float depth) {
        var view = scene.viewport();
        state.bind(program, vertices, scene.texture());
        GL20.glUniform1i(backdrop, 0);
        GL20.glUniform4f(viewport, view.x(), view.y(), view.width(), view.height());
        GL20.glUniform1f(layerDepth, depth);
    }

    boolean supportsBlurMipmaps() { return blurMaxLod >= 0; }

    void setBlurLevel(int level) { GL20.glUniform1f(blurMaxLod, level); }

    boolean draw(GlassDraw draw, GlassSceneCapture.Viewport view, float centerX, float centerY,
                 float halfWidth, float halfHeight, float unit, boolean highContrast) {
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
                || !Float.isFinite(halfWidth) || !Float.isFinite(halfHeight)
                || halfWidth <= 0 || halfHeight <= 0) return false;
        var preset = draw.material();
        var parameters = preset.optics();
        float thickness = Math.min(Math.min(halfWidth, halfHeight), parameters.thickness() * unit);
        float shadow = Math.min(18f, thickness * .9f + 1f);
        float margin = preset.quality() == GlassQuality.FALLBACK ? 2f : shadow * 4f + 2f;
        float left = Math.max(view.x(), centerX - halfWidth - margin);
        float bottom = Math.max(view.y(), centerY - halfHeight - margin);
        float right = Math.min(view.x() + view.width(), centerX + halfWidth + margin);
        float top = Math.min(view.y() + view.height(), centerY + halfHeight + margin);
        if (left >= right || bottom >= top) return false;

        int shape = switch (draw.region().shape()) {
            case ROUNDED, CAPSULE -> 0;
            case ELLIPSE -> 2;
            case CONTINUOUS -> 3;
        };
        int quality = switch (preset.quality()) {
            case FALLBACK -> 0;
            case PERFORMANCE -> 1;
            case BALANCED -> 2;
            case ULTRA -> 3;
        };
        GL20.glUniform4f(quad, left, bottom, right, top);
        GL20.glUniform4f(geometry, centerX, centerY, halfWidth, halfHeight);
        GL20.glUniform1f(radius, draw.region().radius() * unit);
        GL20.glUniform4f(optics, parameters.refraction() * unit, parameters.lensing(), parameters.scattering(),
                parameters.edgeHighlight());
        GL20.glUniform4f(material, parameters.transmission(), thickness, parameters.blurRadius() * unit,
                preset.foreground() == Foreground.LIGHT ? 1f : 0f);
        GL20.glUniform3f(light, draw.lightX(), -draw.lightY(), draw.lightStrength());
        int argb = preset.foreground().fallbackArgb();
        GL20.glUniform3f(flatColor, (argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f);
        GL20.glUniform3i(mode, shape, quality, highContrast ? 1 : 0);
        int profile = draw.profile() == GlassDraw.Profile.LEGACY ? 0
                : preset.variant() == GlassVariant.CLEAR ? 2 : 1;
        GL20.glUniform1i(opticalProfile, profile);
        var text = draw.foregroundBounds();
        if (profile == 0 || text == null) {
            GL20.glUniform4f(foregroundBounds, 0f, 0f, 0f, 0f);
        } else {
            var region = draw.region();
            // Relative GUI bounds map to the optical geometry even under different X and Y scales.
            GL20.glUniform4f(foregroundBounds,
                    (text.x() - region.x()) / region.width(), (text.y() - region.y()) / region.height(),
                    (text.x() + text.width() - region.x()) / region.width(),
                    (text.y() + text.height() - region.y()) / region.height());
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        return true;
    }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        GL20.glDeleteProgram(program);
        GL30.glDeleteVertexArrays(vertices);
    }
}
