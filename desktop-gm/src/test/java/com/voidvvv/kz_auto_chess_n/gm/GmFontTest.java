package com.voidvvv.kz_auto_chess_n.gm;

import com.badlogic.gdx.files.FileHandle;
import imgui.ImFontAtlas;
import imgui.ImFontGlyph;
import imgui.ImGui;
import imgui.type.ImInt;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GmFontTest {
    @Test
    void buildsChineseGlyphsAndDestroysNativeContextRepeatedly() {
        FileHandle base = new FileHandle("../assets/font/ember-legion-hei.ttf");
        FileHandle extra = new FileHandle("src/main/resources/gm/ember-legion-gm.otf");
        String chars = "兽人战士替换编辑";
        for (int attempt = 0; attempt < 3; attempt++) {
            ImGui.createContext();
            try {
                ImGui.getIO().setIniFilename(null);
                ImGui.getIO().setDisplaySize(320, 200);
                ImFontAtlas atlas = ImGui.getIO().getFonts();
                ImInt width = new ImInt(), height = new ImInt();
                ByteBuffer pixels = GmFont.build(atlas, width, height, base, extra, chars);
                atlas.setTexID(1);
                ImGui.newFrame();
                for (int i = 0; i < chars.length(); i++) {
                    ImFontGlyph glyph = ImGui.getFont().findGlyphNoFallback(chars.charAt(i));
                    assertTrue(glyph.ptr != 0);
                    assertTrue(hasInk(glyph, width.get(), height.get(), pixels));
                }
                ImGui.endFrame();
            } finally {
                ImGui.destroyContext();
            }
        }
    }

    private static boolean hasInk(ImFontGlyph glyph, int width, int height, ByteBuffer pixels) {
        for (int y = Math.round(glyph.getV0() * height); y < Math.round(glyph.getV1() * height); y++) {
            for (int x = Math.round(glyph.getU0() * width); x < Math.round(glyph.getU1() * width); x++) {
                if (pixels.get((y * width + x) * 4 + 3) != 0) return true;
            }
        }
        return false;
    }
}
