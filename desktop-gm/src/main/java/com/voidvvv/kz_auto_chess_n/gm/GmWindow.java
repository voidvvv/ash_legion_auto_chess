package com.voidvvv.kz_auto_chess_n.gm;

import com.badlogic.gdx.Gdx;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.internal.ImGuiContext;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.opengles.GLES;
import org.lwjgl.opengles.GLESCapabilities;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengles.GLES20.GL_VERSION;
import static org.lwjgl.opengles.GLES20.glGetString;

/** Owns one native window, one ImGui context and one unshared GL context. Game thread only. */
final class GmWindow implements AutoCloseable {
    private final long handle;
    private final GmPanel panel;
    private final Runnable onCloseRequested;
    private GLESCapabilities capabilities;
    private ImGuiContext context;
    private GlesRenderer renderer;
    private boolean closing;
    private double lastFrame;

    private GmWindow(long handle, PracticeAccess access, Runnable onCloseRequested) {
        this.handle = handle;
        this.onCloseRequested = onCloseRequested;
        panel = new GmPanel(access, Gdx.app::postRunnable, this::requestClose);
    }

    static GmWindow open(PracticeAccess access, Runnable onCloseRequested) {
        glfwDefaultWindowHints();
        long handle;
        try {
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_ES_API);
            glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
            handle = glfwCreateWindow(1100, 660, "余烬军团 - Dear ImGui GM", 0, 0);
        } finally {
            glfwDefaultWindowHints();
        }
        if (handle == 0) throw new IllegalStateException("Could not create the GM EGL window");
        GmWindow window = new GmWindow(handle, access, onCloseRequested);
        try {
            window.inContext(window::initialize);
            glfwSetWindowSizeLimits(handle, 840, 525, GLFW_DONT_CARE, GLFW_DONT_CARE);
            glfwShowWindow(handle);
            return window;
        } catch (RuntimeException | LinkageError ex) {
            window.close();
            throw ex;
        }
    }

    private void initialize() {
        capabilities = GLES.createCapabilities();
        glfwSwapInterval(0);
        context = ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.setLogFilename(null);
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        ImGui.styleColorsDark();
        renderer = new GlesRenderer();
        renderer.create();
        GlfwInput.install(handle, io, this::requestClose);
        Gdx.app.log("GM", "Attached Dear ImGui " + ImGui.getVersion() + " using " + glGetString(GL_VERSION));
    }

    boolean shouldClose() {
        return closing || glfwWindowShouldClose(handle);
    }

    private void requestClose() {
        if (closing) return;
        closing = true;
        panel.close(); // Native callbacks invalidate queued work before the next game frame.
        Gdx.app.postRunnable(onCloseRequested); // Destroy outside GLFW callback dispatch, including while minimized.
    }

    void draw() {
        if (glfwGetWindowAttrib(handle, GLFW_ICONIFIED) == GLFW_TRUE) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1);
            glfwGetWindowSize(handle, w, h);
            int width = w.get(0), height = h.get(0);
            glfwGetFramebufferSize(handle, w, h);
            int fbWidth = w.get(0), fbHeight = h.get(0);
            if (width <= 0 || height <= 0 || fbWidth <= 0 || fbHeight <= 0) return;
            inContext(() -> {
                double now = glfwGetTime();
                ImGuiIO io = ImGui.getIO();
                io.setDeltaTime(lastFrame == 0 ? 1f / 60 : Math.max(0.001f, (float) (now - lastFrame)));
                lastFrame = now;
                io.setDisplaySize(width, height);
                io.setDisplayFramebufferScale((float) fbWidth / width, (float) fbHeight / height);
                ImGui.newFrame();
                panel.draw(width, height);
                ImGui.render();
                renderer.draw(ImGui.getDrawData(), fbWidth, fbHeight);
                glfwSwapBuffers(handle);
            });
        }
    }

    private void inContext(Runnable work) {
        long previous = glfwGetCurrentContext();
        GLESCapabilities previousCapabilities = GLES.getCapabilities();
        try {
            glfwMakeContextCurrent(handle);
            if (capabilities != null) GLES.setCapabilities(capabilities);
            work.run();
        } finally {
            glfwMakeContextCurrent(previous);
            GLES.setCapabilities(previousCapabilities);
        }
    }

    @Override
    public void close() {
        closing = true;
        panel.close();
        Callbacks.glfwFreeCallbacks(handle);
        try {
            if (context != null) {
                inContext(() -> {
                    try {
                        if (renderer != null) renderer.close();
                        ImGui.getIO().setBackendPlatformName(null);
                        ImGui.getIO().setBackendRendererName(null);
                    } finally {
                        ImGui.destroyContext(context);
                    }
                });
            }
        } finally {
            glfwDestroyWindow(handle);
            Gdx.app.log("GM", "Detached Dear ImGui window and callbacks");
        }
    }
}
