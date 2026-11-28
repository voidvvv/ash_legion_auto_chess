package com.voidvvv.kz_auto_chess_n.gm;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImFontGlyph;
import imgui.type.ImInt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Supplies the game's CJK glyphs to Dear ImGui's font atlas with owned bitmap data. */
final class GmFont {
    private static final int SIZE = 18;

    private GmFont() {}

    static ByteBuffer build(ImFontAtlas atlas, ImInt width, ImInt height) {
        return build(atlas, width, height, Gdx.files.internal("font/ember-legion-hei.ttf"),
                Gdx.files.internal("gm/ember-legion-gm.otf"),
                Gdx.files.internal("font/charset.txt").readString("UTF-8")
                        + Gdx.files.internal("gm/glyphs.txt").readString("UTF-8"));
    }

    static ByteBuffer build(ImFontAtlas atlas, ImInt width, ImInt height,
                            FileHandle baseFont, FileHandle extraFont, String chars) {
        ImFontConfig config = new ImFontConfig();
        ImFont font;
        try {
            config.setSizePixels(SIZE);
            font = atlas.addFontDefault(config);
        } finally {
            config.destroy();
        }
        if (!atlas.build()) throw new IllegalStateException("Could not build the Dear ImGui font");

        // imgui-java's glyph-range overloads retain a JNI array address past its pin.
        // FreeType rasterizes CJK glyphs, then ImGui packs and draws them as ordinary glyphs.
        List<Bitmap> glyphs = new ArrayList<>();
        FreeType.Library library = FreeType.initFreeType();
        FreeType.Face base = null;
        FreeType.Face extra = null;
        try {
            base = library.newFace(baseFont, 0);
            extra = library.newFace(extraFont, 0);
            base.setPixelSizes(0, SIZE);
            extra.setPixelSizes(0, SIZE);
            for (int code : chars.codePoints().distinct().toArray()) {
                if (code < 128 || code > Character.MAX_VALUE || font.findGlyphNoFallback(code).ptr != 0) continue;
                FreeType.Face face = base.getCharIndex(code) != 0 ? base : extra;
                if (face.getCharIndex(code) == 0 || !face.loadChar(code, FreeType.FT_LOAD_RENDER)) continue;
                FreeType.GlyphSlot slot = face.getGlyph();
                FreeType.Bitmap bitmap = slot.getBitmap();
                if (bitmap.getWidth() == 0 || bitmap.getRows() == 0) continue;
                atlas.addCustomRectFontGlyph(font, (short) code, bitmap.getWidth(), bitmap.getRows(),
                        slot.getAdvanceX() / 64f, slot.getBitmapLeft(), font.getAscent() - slot.getBitmapTop());
                glyphs.add(new Bitmap(code, bitmap));
            }
        } finally {
            if (extra != null) extra.dispose();
            if (base != null) base.dispose();
            library.dispose();
        }

        if (!atlas.build()) throw new IllegalStateException("Could not pack the Dear ImGui CJK font");
        ByteBuffer pixels = atlas.getTexDataAsRGBA32(width, height);
        int atlasWidth = width.get(), atlasHeight = height.get();
        for (Bitmap bitmap : glyphs) {
            ImFontGlyph glyph = font.findGlyphNoFallback(bitmap.code);
            int left = Math.round(glyph.getU0() * atlasWidth);
            int top = Math.round(glyph.getV0() * atlasHeight);
            for (int y = 0; y < bitmap.height; y++) {
                for (int x = 0; x < bitmap.width; x++) {
                    int dst = ((top + y) * atlasWidth + left + x) * 4;
                    pixels.put(dst, (byte) 255);
                    pixels.put(dst + 1, (byte) 255);
                    pixels.put(dst + 2, (byte) 255);
                    pixels.put(dst + 3, bitmap.alpha[y * bitmap.width + x]);
                }
            }
        }
        return pixels;
    }

    private static final class Bitmap {
        final int code, width, height;
        final byte[] alpha;

        Bitmap(int code, FreeType.Bitmap bitmap) {
            this.code = code;
            width = bitmap.getWidth();
            height = bitmap.getRows();
            alpha = new byte[width * height];
            ByteBuffer buffer = bitmap.getBuffer();
            int pitch = bitmap.getPitch();
            for (int y = 0; y < height; y++) {
                int row = pitch >= 0 ? y * pitch : (height - y - 1) * -pitch;
                buffer.position(row);
                buffer.get(alpha, y * width, width);
            }
        }
    }
}
