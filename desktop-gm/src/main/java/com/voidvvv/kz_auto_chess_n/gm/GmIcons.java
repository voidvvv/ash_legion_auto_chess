package com.voidvvv.kz_auto_chess_n.gm;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import imgui.ImGui;

import static org.lwjgl.opengles.GLES20.*;

/** One texture per existing slot icon, owned by the GM context. */
final class GmIcons implements AutoCloseable {
    private final int[] textures = new int[EquipmentSlot.values().length];

    void create() {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            String name = slot.name().toLowerCase(java.util.Locale.ROOT);
            Pixmap image = new Pixmap(Gdx.files.internal("art/generated_v2/ui/ui_icon_equip_" + name + ".png"));
            try {
                int texture = glGenTextures();
                textures[slot.ordinal()] = texture;
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexImage2D(GL_TEXTURE_2D, 0, image.getGLInternalFormat(),
                        image.getWidth(), image.getHeight(), 0, image.getGLFormat(), image.getGLType(),
                        image.getPixels());
            } finally {
                image.dispose();
            }
        }
    }

    void draw(EquipmentSlot slot) {
        ImGui.image(textures[slot.ordinal()], 18, 18);
    }

    @Override
    public void close() {
        for (int texture : textures) glDeleteTextures(texture);
    }
}
