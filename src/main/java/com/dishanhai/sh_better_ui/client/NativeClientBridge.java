// Author: dishanhai
package com.dishanhai.sh_better_ui.client;

import com.dishanhai.sh_better_ui.client.audio.ClientMusicPulse;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSwipeToast;
import com.dishanhai.sh_better_ui.client.theme.MinecraftThemes;
import com.dishanhai.sh_better_ui.client.visual.VisualFrameCoordinator;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.NativeInput;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/** Shared event order. The loaders translate their event fields and apply the cancellation result. */
public final class NativeClientBridge {
    public static final String MODULE_DESCRIPTOR = "sh_better_ui.modules";
    private static final ThreadLocal<RepeatedKey> REPEAT = new ThreadLocal<>();
    private static int frameDepth;
    private static boolean kernelFrame;

    private NativeClientBridge() {}

    static void frameStart() {
        // World transitions can synchronously render a loading screen inside an outer native frame.
        if (++frameDepth != 1) return;
        try {
            kernelFrame = ClientUiKernel.beginNativeFrame(Minecraft.getInstance(),
                    nanos -> UiFrameScheduler.beginFrame(nanos / 1_000_000L));
            if (!kernelFrame) {
                UiFrameScheduler.beginFrame();
                if (!ClientUiKernel.hasNativeAuthority()) MinecraftThemes.frame();
            }
            var settings = ClientUiSettings.current();
            ClientMusicPulse.global().update(UiFrameScheduler.nowMs(),
                    !settings.reducedMotion() && !settings.highContrast());
        } catch (RuntimeException | Error failure) {
            frameDepth = 0;
            kernelFrame = false;
            UiFrameScheduler.abortFrame();
            throw failure;
        }
    }

    static void frameEnd() {
        if (frameDepth != 1) {
            if (frameDepth > 1) frameDepth--;
            return;
        }
        try {
            if (kernelFrame) ClientUiKernel.finishNativeFrame();
        } finally {
            frameDepth = 0;
            kernelFrame = false;
            UiClientRendering.endFrame();
        }
    }

    static void registerVisualCommand(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        var pages = net.minecraft.commands.Commands.literal("shbetterpages")
                .executes(context -> pagePermission(com.dishanhai.sh_better_ui.kernel.api.page.UiPageType.SCREEN));
        for (var type : java.util.List.of(com.dishanhai.sh_better_ui.kernel.api.page.UiPageType.SCREEN,
                com.dishanhai.sh_better_ui.kernel.api.page.UiPageType.MODAL,
                com.dishanhai.sh_better_ui.kernel.api.page.UiPageType.HUD,
                com.dishanhai.sh_better_ui.kernel.api.page.UiPageType.NOTIFICATION)) {
            pages.then(net.minecraft.commands.Commands.literal(type.name().toLowerCase(java.util.Locale.ROOT))
                    .executes(context -> pagePermission(type)));
        }
        pages.then(net.minecraft.commands.Commands.literal("priority_modal").executes(context -> {
            var client = Minecraft.getInstance();
            client.tell(() -> ClientUiKernel.openPriorityPagePermissions(client));
            return 1;
        }));
        pages.then(net.minecraft.commands.Commands.literal("interaction").executes(context -> {
            var client = Minecraft.getInstance();
            client.tell(() -> ClientUiKernel.openInteractionPagePermissions(client));
            return 1;
        }));
        pages.then(net.minecraft.commands.Commands.literal("request").then(net.minecraft.commands.Commands.argument(
                "page", net.minecraft.commands.arguments.ResourceLocationArgument.id()).executes(context -> {
                    var id = net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "page");
                    var page = com.dishanhai.sh_better_ui.kernel.api.UiId.parse(id.toString());
                    var client = Minecraft.getInstance();
                    client.tell(() -> ClientUiKernel.requestServerPage(client, page));
                    return 1;
                })));
        if (Boolean.getBoolean("shb.pages.trace")) {
            pages.then(net.minecraft.commands.Commands.literal("trace").executes(context -> {
                var client = Minecraft.getInstance();
                client.tell(() -> ClientUiKernel.showPageTrace(client));
                return 1;
            }));
        }
        dispatcher.register(pages);
        dispatcher.register(net.minecraft.commands.Commands.literal("shbetterdock").executes(context -> {
            var client = Minecraft.getInstance();
            client.tell(() -> ClientUiKernel.openDockExample(client));
            return 1;
        }));
        dispatcher.register(net.minecraft.commands.Commands.literal("shbettervisual").executes(context -> {
            var client = Minecraft.getInstance();
            client.tell(() -> ClientUiKernel.openVisualCompositionExample(client));
            return 1;
        }));
        dispatcher.register(net.minecraft.commands.Commands.literal("shbetterrichtext").executes(context -> {
            var client = Minecraft.getInstance();
            client.tell(() -> ClientUiKernel.openRichTextExample(client));
            return 1;
        }));
        dispatcher.register(net.minecraft.commands.Commands.literal("shbetterrichsync").executes(context -> {
            var client = Minecraft.getInstance();
            client.tell(() -> ClientUiKernel.openRichTextPipeline(client));
            return 1;
        }));
        dispatcher.register(net.minecraft.commands.Commands.literal("shbettertextreadability").executes(context -> {
            var client = Minecraft.getInstance();
            client.tell(() -> ClientUiKernel.toggleTextReadabilityOverlay(client));
            return 1;
        }));
        if (ClientUiKernel.richTextEditorEnabled()) {
            dispatcher.register(net.minecraft.commands.Commands.literal("shbetterricheditor").executes(context -> {
                var client = Minecraft.getInstance();
                client.tell(() -> ClientUiKernel.openRichTextEditor(client));
                return 1;
            }));
        }
    }

    private static int pagePermission(com.dishanhai.sh_better_ui.kernel.api.page.UiPageType type) {
        var client = Minecraft.getInstance();
        client.tell(() -> ClientUiKernel.openPagePermissions(client, type));
        return 1;
    }

    static void beginScreen(Screen owner, GuiGraphics graphics, float partialTick) {
        if (frameDepth == 1) VisualFrameCoordinator.begin(owner, graphics, partialTick);
    }

    static void composeScreenPost(Screen owner, GuiGraphics graphics, Runnable legacyBlur) {
        if (frameDepth != 1) {
            legacyBlur.run();
            return;
        }
        try {
            VisualFrameCoordinator.prepareBlur(owner, graphics);
            legacyBlur.run();
        } finally {
            VisualFrameCoordinator.drawForeground(owner, graphics);
        }
    }

    static void render(Screen owner, GuiGraphics graphics, float partialTick) {
        if (frameDepth == 1 && kernelFrame) ClientUiKernel.renderNative(owner, graphics, partialTick);
    }

    static boolean input(Screen screen, NativeInput event) {
        boolean handled;
        if (event instanceof NativeInput.Pointer pointer) {
            handled = pointer(screen, pointer);
        } else if (event instanceof NativeInput.Key key) {
            handled = key(screen, key);
        } else {
            var character = (NativeInput.Character) event;
            var overlay = UiOverlayHost.current(screen);
            var local = UiLocalModal.current(screen);
            handled = overlay != null && overlay.charTyped(character.codeUnit(), character.modifiers())
                    || local != null && local.focusedCovered();
        }
        return handled || ClientUiKernel.nativeInput(screen, event);
    }

    private static boolean pointer(Screen screen, NativeInput.Pointer pointer) {
        int button = pointer.button();
        double x = pointer.x(), y = pointer.y();
        if (pointer.action() == NativeInput.PointerAction.PRESS) {
            if (screen instanceof ControlPanelScreen panel && panel.reopenFromMouse(button)) return true;
            UiPointerCapture.beforePress(screen, button);
        }
        var local = UiLocalModal.current(screen);
        if (pointer.action() == NativeInput.PointerAction.RELEASE && UiPointerCapture.release(screen, button, x, y)
                || pointer.action() == NativeInput.PointerAction.DRAG && UiPointerCapture.drag(screen, button, x, y)) {
            return true;
        }
        if (pointer.action() == NativeInput.PointerAction.SCROLL) {
            if (local != null && local.mouseScrolled(x, y, pointer.deltaX(), pointer.deltaY())) return true;
            if (pointer.deltaX() != 0 || pointer.deltaY() != 0) UiPointerCapture.cancel(screen);
        }
        var overlay = UiOverlayHost.current(screen);
        return switch (pointer.action()) {
            case PRESS -> overlay != null && overlay.mouseClicked(x, y, button)
                    || local != null && local.mouseClicked(x, y, button)
                    || UiSwipeToast.handleOverlayPress(screen, x, y, button) || UiPointerCapture.press(screen, button, x, y);
            case RELEASE -> overlay != null && overlay.mouseReleased(x, y, button)
                    || local != null && local.mouseReleased(x, y, button);
            case DRAG -> overlay != null && overlay.mouseDragged(x, y, button, pointer.deltaX(), pointer.deltaY())
                    || local != null && local.mouseDragged(x, y, button);
            case SCROLL -> overlay != null && overlay.mouseScrolled(x, y, pointer.deltaY());
            case MOVE -> false;
        };
    }

    private static boolean key(Screen screen, NativeInput.Key input) {
        int key = input.key(), scan = input.scanCode(), mods = input.modifiers();
        if (input.action() != NativeInput.KeyAction.RELEASE && screen instanceof ControlPanelScreen panel
                && panel.reopenFromKey(key, scan, mods)) return true;
        var overlay = UiOverlayHost.current(screen);
        var local = UiLocalModal.current(screen);
        if (input.action() == NativeInput.KeyAction.RELEASE) {
            return overlay != null && overlay.keyReleased(key, scan, mods)
                    || local != null && local.keyReleased(key, scan, mods);
        }
        return overlay != null && overlay.keyPressed(key, scan, mods)
                || local != null && local.keyPressed(key, scan, mods)
                || key == GLFW.GLFW_KEY_ESCAPE && (UiPointerCapture.cancel(screen) || UiSwipeToast.handleEscape(screen));
    }

    static NativeInput.Key pressedKey(int key, int scan, int modifiers) {
        var repeat = REPEAT.get();
        var action = repeat != null && repeat.key() == key && repeat.scanCode() == scan
                ? NativeInput.KeyAction.REPEAT : NativeInput.KeyAction.PRESS;
        return new NativeInput.Key(action, key, scan, modifiers);
    }

    /** The Screen Pre hooks receive press and repeat through the same native method. */
    public static void beginKey(long window, int key, int scan, int action) {
        if (action == GLFW.GLFW_REPEAT && Minecraft.getInstance().getWindow().getWindow() == window) {
            REPEAT.set(new RepeatedKey(key, scan));
        } else {
            REPEAT.remove();
        }
    }

    public static void endKey() { REPEAT.remove(); }

    private record RepeatedKey(int key, int scanCode) {}
}
