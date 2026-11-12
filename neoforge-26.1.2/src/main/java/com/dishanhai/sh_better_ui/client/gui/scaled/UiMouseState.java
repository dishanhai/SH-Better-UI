package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 山海：客户端鼠标的只读快照。坐标以窗口左上角为原点，{@code x/y} 使用 Screen 的 GUI 单位，
 * {@code windowX/windowY} 使用 GLFW 窗口单位。按钮掩码的第 N 位对应 GLFW 按键编号 N。
 */
public record UiMouseState(double x, double y, double windowX, double windowY,
                           boolean focused, boolean grabbed, boolean insideWindow, int buttons) {
    private static final int BUTTON_MASK = (1 << (GLFW.GLFW_MOUSE_BUTTON_LAST + 1)) - 1;

    public UiMouseState {
        buttons &= BUTTON_MASK;
    }

    /** 在 Minecraft 客户端线程读取一次，可将同一快照传给多个控件。 */
    public static UiMouseState read() {
        Minecraft client = Minecraft.getInstance();
        var window = client.getWindow();
        var mouse = client.mouseHandler;
        long handle = window.handle();
        double windowX = mouse.xpos();
        double windowY = mouse.ypos();
        int width = window.getScreenWidth();
        int height = window.getScreenHeight();
        double x = windowX * window.getGuiScaledWidth() / Math.max(1, width);
        double y = windowY * window.getGuiScaledHeight() / Math.max(1, height);
        boolean insideWindow = windowX >= 0 && windowX < width && windowY >= 0 && windowY < height
                && GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_HOVERED) == GLFW.GLFW_TRUE;
        int buttons = 0;
        for (int button = GLFW.GLFW_MOUSE_BUTTON_1; button <= GLFW.GLFW_MOUSE_BUTTON_LAST; button++) {
            if (GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS) buttons |= 1 << button;
        }
        return new UiMouseState(x, y, windowX, windowY, client.isWindowActive(), mouse.isMouseGrabbed(),
                insideWindow, buttons);
    }

    /** 窗口有焦点、指针处于窗口内且未被游戏捕获时，可用于 GUI 命中。 */
    public boolean available() {
        return focused && !grabbed && insideWindow;
    }

    /** 读取指定 GLFW 按键的采样状态。 */
    public boolean isDown(int button) {
        return button >= GLFW.GLFW_MOUSE_BUTTON_1 && button <= GLFW.GLFW_MOUSE_BUTTON_LAST
                && (buttons & (1 << button)) != 0;
    }

    /** 两次可用快照之间新按下的掩码。首次采样或恢复可用时返回零。 */
    public int pressedSince(UiMouseState previous) {
        return comparableTo(previous) ? buttons & ~previous.buttons : 0;
    }

    /** 两次可用快照之间新放开的掩码。一次采样间隔内完成的点击需由 Screen 事件取得。 */
    public int releasedSince(UiMouseState previous) {
        return comparableTo(previous) ? previous.buttons & ~buttons : 0;
    }

    private boolean comparableTo(UiMouseState previous) {
        return previous != null && available() && previous.available();
    }
}
