package com.voidvvv.kz_auto_chess_n.gm;

import imgui.ImDrawData;
import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.ImVec4;
import imgui.flag.ImGuiBackendFlags;
import imgui.type.ImInt;

import java.nio.ByteBuffer;

import static org.lwjgl.opengles.GLES20.*;

/** Renders Dear ImGui's native draw lists on the GM window's private GLES context. */
final class GlesRenderer implements AutoCloseable {
    final GmIcons icons = new GmIcons();
    private static final String VERTEX = "#version 100\n"
            + "attribute vec2 position; attribute vec2 uv; attribute vec4 color;\n"
            + "uniform vec2 origin; uniform vec2 size; varying vec2 texCoord; varying vec4 tint;\n"
            + "void main() { vec2 p = (position - origin) / size;\n"
            + "gl_Position = vec4(p.x * 2.0 - 1.0, 1.0 - p.y * 2.0, 0.0, 1.0);\n"
            + "texCoord = uv; tint = color; }";
    private static final String FRAGMENT = "#version 100\n"
            + "precision mediump float; uniform sampler2D atlas; varying vec2 texCoord; varying vec4 tint;\n"
            + "void main() { gl_FragColor = tint * texture2D(atlas, texCoord); }";

    private final ImVec4 clip = new ImVec4();
    private final int stride = ImDrawData.sizeOfImDrawVert();
    private final int indexSize = ImDrawData.sizeOfImDrawIdx();
    private int program;
    private int vertices;
    private int indices;
    private int atlasTexture;
    private int atlasUniform;
    private int originUniform;
    private int sizeUniform;

    void create() {
        if (stride != 20 || indexSize != 2) throw new IllegalStateException("Unsupported ImGui draw layout");
        int vs = shader(GL_VERTEX_SHADER, VERTEX);
        int fs = 0;
        try {
            fs = shader(GL_FRAGMENT_SHADER, FRAGMENT);
            program = glCreateProgram();
            glAttachShader(program, vs);
            glAttachShader(program, fs);
            glBindAttribLocation(program, 0, "position");
            glBindAttribLocation(program, 1, "uv");
            glBindAttribLocation(program, 2, "color");
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                throw new IllegalStateException(glGetProgramInfoLog(program));
            }
        } finally {
            glDeleteShader(vs);
            if (fs != 0) glDeleteShader(fs);
        }
        vertices = glGenBuffers();
        indices = glGenBuffers();
        atlasTexture = glGenTextures();
        atlasUniform = glGetUniformLocation(program, "atlas");
        originUniform = glGetUniformLocation(program, "origin");
        sizeUniform = glGetUniformLocation(program, "size");
        glBindTexture(GL_TEXTURE_2D, atlasTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        ImFontAtlas atlas = ImGui.getIO().getFonts();
        ImInt width = new ImInt(), height = new ImInt();
        ByteBuffer pixels = GmFont.build(atlas, width, height);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(), height.get(), 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        atlas.setTexID(atlasTexture);
        atlas.clearTexData();
        icons.create();
        int error = glGetError();
        if (error != GL_NO_ERROR) throw new IllegalStateException("Could not upload Dear ImGui font, GL error " + error);
        ImGui.getIO().setBackendRendererName("ember_imgui_gles2");
        ImGui.getIO().addBackendFlags(ImGuiBackendFlags.RendererHasVtxOffset);
    }

    void draw(ImDrawData data, int fbWidth, int fbHeight) {
        glViewport(0, 0, fbWidth, fbHeight);
        glDisable(GL_SCISSOR_TEST);
        glClearColor(0.07f, 0.08f, 0.10f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);
        glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glActiveTexture(GL_TEXTURE0);
        glUseProgram(program);
        glUniform1i(atlasUniform, 0);
        float ox = data.getDisplayPosX(), oy = data.getDisplayPosY();
        float sx = data.getFramebufferScaleX(), sy = data.getFramebufferScaleY();
        glUniform2f(originUniform, ox, oy);
        glUniform2f(sizeUniform, data.getDisplaySizeX(), data.getDisplaySizeY());
        glBindBuffer(GL_ARRAY_BUFFER, vertices);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indices);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        for (int list = 0; list < data.getCmdListsCount(); list++) {
            glBufferData(GL_ARRAY_BUFFER, data.getCmdListVtxBufferData(list), GL_STREAM_DRAW);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, data.getCmdListIdxBufferData(list), GL_STREAM_DRAW);
            for (int cmd = 0; cmd < data.getCmdListCmdBufferSize(list); cmd++) {
                data.getCmdListCmdBufferClipRect(clip, list, cmd);
                int left = Math.max(0, (int) Math.floor((clip.x - ox) * sx));
                int top = Math.max(0, (int) Math.floor((clip.y - oy) * sy));
                int right = Math.min(fbWidth, (int) Math.ceil((clip.z - ox) * sx));
                int bottom = Math.min(fbHeight, (int) Math.ceil((clip.w - oy) * sy));
                if (left >= right || top >= bottom) continue;
                glScissor(left, fbHeight - bottom, right - left, bottom - top);
                glBindTexture(GL_TEXTURE_2D, (int) data.getCmdListCmdBufferTextureId(list, cmd));
                long offset = (long) data.getCmdListCmdBufferVtxOffset(list, cmd) * stride;
                glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, offset);
                glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, offset + 8);
                glVertexAttribPointer(2, 4, GL_UNSIGNED_BYTE, true, stride, offset + 16);
                glDrawElements(GL_TRIANGLES, data.getCmdListCmdBufferElemCount(list, cmd),
                        GL_UNSIGNED_SHORT, (long) data.getCmdListCmdBufferIdxOffset(list, cmd) * indexSize);
            }
        }
    }

    @Override
    public void close() {
        icons.close();
        glUseProgram(0);
        glDeleteTextures(atlasTexture);
        glDeleteBuffers(vertices);
        glDeleteBuffers(indices);
        glDeleteProgram(program);
    }

    private static int shader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String error = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException(error);
        }
        return shader;
    }
}
