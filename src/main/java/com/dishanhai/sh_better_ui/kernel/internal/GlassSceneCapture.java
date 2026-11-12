// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.util.function.BiConsumer;

/** A render-thread owned color capture shared by the surfaces of one native pass. */
final class GlassSceneCapture implements AutoCloseable {
    private static final long BYTE_BUDGET = 32L << 20;

    record Size(int width, int height) {
        long bytes() { return 4L * width * height; }

        static Size fit(int width, int height, int maxTexture, long budget) {
            if (width < 1 || height < 1 || maxTexture < 1 || budget < 4) {
                throw new IllegalArgumentException("Invalid glass capture dimensions or budget");
            }
            while (width > maxTexture || height > maxTexture || (long) width * height > budget / 4) {
                width = width / 2 + width % 2;
                height = height / 2 + height % 2;
            }
            return new Size(width, height);
        }
    }

    record MipChain(int levels, long bytes) {
        static MipChain fit(Size base, long budget) {
            int width = base.width(), height = base.height();
            long bytes = base.bytes();
            int levels = 0;
            while (width > 1 || height > 1) {
                width = Math.max(1, width / 2);
                height = Math.max(1, height / 2);
                long next = 4L * width * height;
                if (next > budget - bytes) break;
                bytes += next;
                levels++;
            }
            return new MipChain(levels, bytes);
        }
    }

    record Viewport(int x, int y, int width, int height) {
        static Viewport current() {
            try (var stack = MemoryStack.stackPush()) {
                var values = stack.mallocInt(4);
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, values);
                return new Viewport(values.get(0), values.get(1), values.get(2), values.get(3));
            }
        }
    }

    record Snapshot(Viewport viewport, Size size, int framebuffer, int texture) {}

    private record Source(Viewport viewport, int framebuffer) {}

    private record Target(Size size, int framebuffer, int texture, MipChain blur) {
        void release() {
            GL30.glDeleteFramebuffers(framebuffer);
            GL11.glDeleteTextures(texture);
        }
    }

    private final BiConsumer<String, Throwable> report;
    private Target target;
    private Source failedSource;
    private int maxTexture;
    private boolean inPass;
    private boolean attempted;
    private boolean blurAllocated;
    private int blurLevel;
    private boolean closed;
    private Snapshot snapshot;

    GlassSceneCapture(BiConsumer<String, Throwable> report) {
        this.report = report;
    }

    void begin() {
        if (closed || inPass) throw new IllegalStateException("Glass scene capture pass is unavailable");
        inPass = true;
    }

    void end() {
        snapshot = null;
        attempted = false;
        blurLevel = 0;
        inPass = false;
    }

    Snapshot snapshot() {
        RenderSystem.assertOnRenderThread();
        if (!inPass) throw new IllegalStateException("Glass scene capture requires a native render pass");
        if (attempted) return snapshot;
        attempted = true;
        Viewport viewport = Viewport.current();
        if (viewport.width() < 1 || viewport.height() < 1) return null;
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (failedSource != null && failedSource.framebuffer() == draw && viewport.equals(failedSource.viewport())) return null;
        try {
            snapshot = capture(viewport, draw);
            failedSource = null;
        } catch (RuntimeException | LinkageError failure) {
            failedSource = new Source(viewport, draw);
            report.accept("Liquid Glass scene capture unavailable", failure);
        }
        return snapshot;
    }

    /** Called with the capture texture bound by GlassDrawState and the host FBO restored. */
    int prepareBlur() {
        int levels = target.blur().levels();
        if (blurLevel > 0 || levels == 0) return blurLevel;
        if (!blurAllocated) GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        if (!blurAllocated) {
            if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, levels, GL11.GL_TEXTURE_WIDTH) == 0) {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
                return 0;
            }
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            blurAllocated = true;
        }
        return blurLevel = levels;
    }

    private Snapshot capture(Viewport view, int draw) {
        if (maxTexture == 0) {
            if (!GL.getCapabilities().OpenGL32) {
                throw new IllegalStateException("Liquid Glass needs OpenGL 3.2, found " + GL11.glGetString(GL11.GL_VERSION));
            }
            maxTexture = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        }
        Size size = Size.fit(view.width(), view.height(), maxTexture, BYTE_BUDGET);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int attachment = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0);
        int samples = GL11.glGetInteger(GL13.GL_SAMPLES);
        if (attachment == GL11.GL_NONE || samples > 1 && (size.width() != view.width() || size.height() != view.height())) {
            throw new IllegalStateException("The current color attachment cannot be captured at " + size);
        }

        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, draw);
            int previousAttachment = GL11.glGetInteger(GL11.GL_READ_BUFFER);
            boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            try {
                if (previousAttachment != attachment) GL11.glReadBuffer(attachment);
                if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("The active Liquid Glass backdrop source is incomplete");
                }
                if (draw != 0) {
                    int componentType = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,
                            attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_COMPONENT_TYPE);
                    if (componentType == GL11.GL_INT || componentType == GL11.GL_UNSIGNED_INT) {
                        throw new IllegalStateException("Integer color attachments cannot be sampled as Liquid Glass");
                    }
                }
                Target destination = target(size);
                if (clipped) GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination.framebuffer());
                GL30.glBlitFramebuffer(view.x(), view.y(), view.x() + view.width(), view.y() + view.height(),
                        0, 0, size.width(), size.height(), GL11.GL_COLOR_BUFFER_BIT,
                        samples > 1 ? GL11.GL_NEAREST : GL11.GL_LINEAR);
                return new Snapshot(view, size, destination.framebuffer(), destination.texture());
            } finally {
                if (clipped) GL11.glEnable(GL11.GL_SCISSOR_TEST);
                if (previousAttachment != attachment) GL11.glReadBuffer(previousAttachment);
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        }
    }

    private Target target(Size size) {
        if (target != null && target.size().equals(size)) return target;
        releaseTarget();
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int texture = GL11.glGenTextures();
        int framebuffer = 0;
        try {
            framebuffer = GL30.glGenFramebuffers();
            if (texture == 0 || framebuffer == 0) throw new IllegalStateException("Could not allocate a glass capture target");
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size.width(), size.height(), 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, texture, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Incomplete RGBA8 Liquid Glass capture target at " + size);
            }
            target = new Target(size, framebuffer, texture, MipChain.fit(size, BYTE_BUDGET));
            return target;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack);
            if (target == null) {
                GL30.glDeleteFramebuffers(framebuffer);
                GL11.glDeleteTextures(texture);
            }
        }
    }

    void reset() {
        snapshot = null;
        attempted = false;
        blurLevel = 0;
        failedSource = null;
        maxTexture = 0;
        releaseTarget();
    }

    long allocatedBytes() {
        if (target == null) return 0;
        return blurAllocated ? target.blur().bytes() : target.size().bytes();
    }

    private void releaseTarget() {
        if (target == null) return;
        RenderSystem.assertOnRenderThread();
        target.release();
        target = null;
        blurAllocated = false;
    }

    @Override public void close() {
        reset();
        end();
        closed = true;
    }
}
