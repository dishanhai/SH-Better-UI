// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.*;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.util.Arrays;

/** Standalone hidden-window GL test. No game instance or production packaging is needed. */
public final class VisualSamplingSmoke {
    private VisualSamplingSmoke() {}
    public static void main(String[] args) {
        require(GLFW.glfwInit(), "GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(64, 64, "SH-Better-UI sampling verification", 0, 0);
        require(window != 0, "Hidden OpenGL context creation failed");
        GLFW.glfwMakeContextCurrent(window); GLFW.glfwSwapInterval(0); GL.createCapabilities();
        RenderSystem.initRenderThread();
        int texture = GL11.glGenTextures(), framebuffer = GL30.glGenFramebuffers(), sentinel = GL15.glGenBuffers();
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 64, 64, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "Smoke framebuffer incomplete");
            GL11.glViewport(0, 0, 64, 64); GL11.glClearColor(0, 0, 1, 1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(0, 32, 32, 32);
            GL11.glClearColor(1, 0, 0, 1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, sentinel); GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, 4096, GL15.GL_STREAM_READ);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, sentinel);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 8); GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 23);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, 2); GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, 3);
            int[] before = state();
            var id = UiId.parse("sh_better_ui:smoke/visual_sampling");
            var roi = new PixelRegion(0, 0, 32, 32);
            try (var sampler = new ClientBackgroundSampler(8, 1_000_000)) {
                sampler.beginFrame(1, 1_000_000, 7);
                require(sampler.request(id, roi, Stage.POST_MATERIAL_PRE_TEXT).validity() == Validity.PENDING,
                        "First sample must be asynchronous");
                require(Arrays.equals(before, state()), "Sampling enqueue leaked GL state");
                GL11.glFlush();
                BackgroundSample measured = await(sampler, id, roi, Stage.POST_MATERIAL_PRE_TEXT, 7, 2);
                require(measured.source() == Source.GPU_DOWNSAMPLED_PIXELS && measured.sampleWidth() == 8,
                        "Readback lost its GPU reduction provenance or dimensions");
                require(Arrays.stream(measured.pixels()).allMatch(pixel -> pixel == 0xffff0000),
                        "Framebuffer top-left ROI was not the red quadrant: " + Integer.toHexString(measured.pixels()[0]));
                require(Arrays.equals(before, state()), "Completed readback leaked GL state");
                // An old in-flight result must not become the first valid observation of a new state version.
                sampler.beginFrame(200, 200_000_000, 8);
                require(sampler.request(id, roi, Stage.POST_MATERIAL_PRE_TEXT).validity() != Validity.VALID,
                        "Previous state pixels escaped the version guard");
                GL11.glFlush();
                require(await(sampler, id, roi, Stage.POST_MATERIAL_PRE_TEXT, 8, 201).stateVersion() == 8,
                        "Completed result has an old state version");
                sampler.reset(); sampler.beginFrame(400, 400_000_000, 9);
                require(sampler.request(id, roi, Stage.POST_MATERIAL_PRE_TEXT).validity() != Validity.VALID,
                        "Reload reset retained an old valid sample");
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Sampler generated an OpenGL error");
                require(Arrays.equals(before, state()), "Resource reset leaked GL state");
                System.out.println("VISUAL_SAMPLING_SMOKE_PASS vendor=" + GL11.glGetString(GL11.GL_VENDOR)
                        + " renderer=" + GL11.glGetString(GL11.GL_RENDERER) + " metrics=" + sampler.metrics());
            }
            verifyGrowingHudRegion(id);
            require(Arrays.equals(before, state()), "Growing HUD region leaked GL state");
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0); GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL15.glDeleteBuffers(sentinel); GL30.glDeleteFramebuffers(framebuffer); GL11.glDeleteTextures(texture);
            GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
        }
    }
    private static void verifyGrowingHudRegion(UiId id) {
        var stage = Stage.POST_MATERIAL_PRE_TEXT;
        var seed = new PixelRegion(16, 16, 16, 16);
        var expanded = new PixelRegion(4, 4, 28, 28);
        try (var sampler = new ClientBackgroundSampler(8, 100_000_000L, true)) {
            sampler.beginFrame(1, 1_000_000, 7);
            sampler.request(id, seed, stage);
            require(sampler.request(id, expanded, stage).validity() == Validity.PENDING
                            && sampler.metrics().scheduled() == 1,
                    "Expansion rescheduled an in-flight GPU observation");
            GL11.glFinish();
            sampler.beginFrame(2, 2_000_000, 7);
            var early = sampler.request(id, expanded, stage);
            require(early.validity() == Validity.VALID && early.region().equals(seed),
                    "Expanding HUD lost the completed sample of its visible region");
            require(Arrays.stream(early.pixels()).allMatch(pixel -> pixel == 0xffff0000),
                    "Expanding HUD sample lost its measured pixels");

            sampler.beginFrame(200, 200_000_000, 7);
            sampler.request(id, expanded, stage);
            GL11.glFinish();
            sampler.beginFrame(201, 201_000_000, 7);
            var refreshed = sampler.request(id, expanded, stage);
            require(refreshed.validity() == Validity.VALID && refreshed.region().equals(expanded),
                    "The HUD did not refresh the sample at its current bounds");

            var moved = new PixelRegion(17, 17, 16, 16);
            require(sampler.request(id, moved, stage).validity() == Validity.PENDING,
                    "HUD reused an observation outside its current bounds");
        }
        System.out.println("GROWING_HUD_SAMPLING_SMOKE_PASS");
    }

    private static BackgroundSample await(ClientBackgroundSampler sampler, UiId id, PixelRegion roi, Stage stage,
                                           long version, long frameStart) {
        long deadline = System.nanoTime() + 2_000_000_000L;
        long frame = frameStart;
        while (System.nanoTime() < deadline) {
            sampler.beginFrame(frame, frame * 1_000_000, version);
            BackgroundSample sample = sampler.request(id, roi, stage);
            if (sample.validity() == Validity.VALID) return sample;
            require(sample.validity() != Validity.UNAVAILABLE, "GPU observation unexpectedly unavailable");
            GL11.glFlush(); GLFW.glfwPollEvents(); Thread.yield(); frame++;
        }
        throw new AssertionError("GPU observation did not complete within the bounded smoke deadline");
    }
    private static int[] state() {
        return new int[]{GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING), GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING),
                GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT), GL11.glGetInteger(GL12.GL_PACK_ROW_LENGTH),
                GL11.glGetInteger(GL12.GL_PACK_SKIP_ROWS), GL11.glGetInteger(GL12.GL_PACK_SKIP_PIXELS),
                GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) ? 1 : 0};
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
