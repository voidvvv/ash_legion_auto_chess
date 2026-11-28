package com.voidvvv.kz_auto_chess_n.gm;

import imgui.ImGuiIO;
import imgui.flag.ImGuiKey;

import static org.lwjgl.glfw.GLFW.*;

/** Single-window platform callbacks. No process-wide GLFW callbacks or cursors. */
final class GlfwInput {
    private GlfwInput() {}

    static void install(long window, ImGuiIO io, Runnable close) {
        io.setBackendPlatformName("ember_imgui_glfw");
        glfwSetWindowCloseCallback(window, handle -> close.run());
        glfwSetWindowFocusCallback(window, (handle, focus) -> io.addFocusEvent(focus));
        glfwSetCursorPosCallback(window, (handle, x, y) -> io.addMousePosEvent((float) x, (float) y));
        glfwSetCursorEnterCallback(window, (handle, entered) -> {
            if (!entered) io.addMousePosEvent(-Float.MAX_VALUE, -Float.MAX_VALUE);
        });
        glfwSetMouseButtonCallback(window, (handle, button, action, mods) -> {
            modifiers(io, mods);
            if (button >= 0 && button < 5) io.addMouseButtonEvent(button, action == GLFW_PRESS);
        });
        glfwSetScrollCallback(window, (handle, x, y) -> io.addMouseWheelEvent((float) x, (float) y));
        glfwSetCharCallback(window, (handle, codepoint) -> io.addInputCharacter(codepoint));
        glfwSetKeyCallback(window, (handle, key, scancode, action, mods) -> {
            if (action != GLFW_PRESS && action != GLFW_RELEASE) return;
            if (key == GLFW_KEY_F10 && action == GLFW_PRESS) {
                close.run();
                return;
            }
            modifiers(io, mods);
            int mapped = key(key);
            if (mapped != ImGuiKey.None) {
                io.addKeyEvent(mapped, action == GLFW_PRESS);
                io.setKeyEventNativeData(mapped, key, scancode);
            }
        });
    }

    private static void modifiers(ImGuiIO io, int mods) {
        io.addKeyEvent(ImGuiKey.ModCtrl, (mods & GLFW_MOD_CONTROL) != 0);
        io.addKeyEvent(ImGuiKey.ModShift, (mods & GLFW_MOD_SHIFT) != 0);
        io.addKeyEvent(ImGuiKey.ModAlt, (mods & GLFW_MOD_ALT) != 0);
        io.addKeyEvent(ImGuiKey.ModSuper, (mods & GLFW_MOD_SUPER) != 0);
    }

    private static int key(int key) {
        if (key >= GLFW_KEY_A && key <= GLFW_KEY_Z) return ImGuiKey.A + key - GLFW_KEY_A;
        if (key >= GLFW_KEY_0 && key <= GLFW_KEY_9) return ImGuiKey._0 + key - GLFW_KEY_0;
        switch (key) {
            case GLFW_KEY_TAB: return ImGuiKey.Tab;
            case GLFW_KEY_LEFT: return ImGuiKey.LeftArrow;
            case GLFW_KEY_RIGHT: return ImGuiKey.RightArrow;
            case GLFW_KEY_UP: return ImGuiKey.UpArrow;
            case GLFW_KEY_DOWN: return ImGuiKey.DownArrow;
            case GLFW_KEY_PAGE_UP: return ImGuiKey.PageUp;
            case GLFW_KEY_PAGE_DOWN: return ImGuiKey.PageDown;
            case GLFW_KEY_HOME: return ImGuiKey.Home;
            case GLFW_KEY_END: return ImGuiKey.End;
            case GLFW_KEY_INSERT: return ImGuiKey.Insert;
            case GLFW_KEY_DELETE: return ImGuiKey.Delete;
            case GLFW_KEY_BACKSPACE: return ImGuiKey.Backspace;
            case GLFW_KEY_SPACE: return ImGuiKey.Space;
            case GLFW_KEY_ENTER: return ImGuiKey.Enter;
            case GLFW_KEY_KP_ENTER: return ImGuiKey.KeypadEnter;
            case GLFW_KEY_ESCAPE: return ImGuiKey.Escape;
            case GLFW_KEY_LEFT_CONTROL: return ImGuiKey.LeftCtrl;
            case GLFW_KEY_RIGHT_CONTROL: return ImGuiKey.RightCtrl;
            case GLFW_KEY_LEFT_SHIFT: return ImGuiKey.LeftShift;
            case GLFW_KEY_RIGHT_SHIFT: return ImGuiKey.RightShift;
            case GLFW_KEY_LEFT_ALT: return ImGuiKey.LeftAlt;
            case GLFW_KEY_RIGHT_ALT: return ImGuiKey.RightAlt;
            case GLFW_KEY_LEFT_SUPER: return ImGuiKey.LeftSuper;
            case GLFW_KEY_RIGHT_SUPER: return ImGuiKey.RightSuper;
            default: return ImGuiKey.None;
        }
    }
}
