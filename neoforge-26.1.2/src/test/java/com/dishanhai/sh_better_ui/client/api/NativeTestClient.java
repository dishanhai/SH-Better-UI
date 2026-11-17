// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.lang.reflect.Field;
import java.util.List;

/** Scoped headless client for native Screen/input tests. Never starts a window, GPU or audio engine. */
public final class NativeTestClient implements AutoCloseable {
    private final Field singleton;
    private final Field renderThread;
    private final Object previousClient;
    private final Object previousRenderThread;
    private final Minecraft client;
    private boolean closed;

    private NativeTestClient() throws Exception {
        singleton = Minecraft.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        previousClient = singleton.get(null);
        renderThread = RenderSystem.class.getDeclaredField("renderThread");
        renderThread.setAccessible(true);
        previousRenderThread = renderThread.get(null);
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        client = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        var font = Minecraft.class.getDeclaredField("font");
        font.setAccessible(true);
        font.set(client, font());
        var textInput = Minecraft.class.getDeclaredField("textInputManager");
        textInput.setAccessible(true);
        textInput.set(client, new com.mojang.blaze3d.platform.TextInputManager(null));
        renderThread.set(null, null);
        try {
            RenderSystem.initRenderThread();
            singleton.set(null, client);
        } catch (Throwable failure) {
            renderThread.set(null, previousRenderThread);
            singleton.set(null, previousClient);
            throw failure;
        }
    }

    public static NativeTestClient install() throws Exception { return new NativeTestClient(); }
    public Minecraft client() { return client; }

    @Override public void close() throws Exception {
        if (closed) return;
        closed = true;
        try { singleton.set(null, previousClient); }
        finally { renderThread.set(null, previousRenderThread); }
    }

    private static Font font() {
        var splitter = new StringSplitter((codePoint, style) -> 5f);
        return new Font(null) {
            @Override public StringSplitter getSplitter() { return splitter; }
            @Override public String plainSubstrByWidth(String text, int width) {
                return splitter.plainHeadByWidth(text, width, Style.EMPTY);
            }
            @Override public String plainSubstrByWidth(String text, int width, boolean backwards) {
                return backwards ? splitter.plainTailByWidth(text, width, Style.EMPTY)
                        : splitter.plainHeadByWidth(text, width, Style.EMPTY);
            }
            @Override public int width(String text) { return (int) Math.ceil(splitter.stringWidth(text)); }
            @Override public int width(FormattedCharSequence text) { return (int) Math.ceil(splitter.stringWidth(text)); }
            @Override public int width(FormattedText text) { return (int) Math.ceil(splitter.stringWidth(text)); }
            @Override public List<FormattedText> splitIgnoringLanguage(FormattedText text, int width) {
                return splitter.splitLines(text, width, Style.EMPTY);
            }
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                return splitter.splitLines(text, width, Style.EMPTY).stream().map(Language.getInstance()::getVisualOrder).toList();
            }
        };
    }
}
