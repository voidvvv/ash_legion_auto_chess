package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 真素材层（Q4=B：itch 免费小人替换 2~3 棋子验证精灵动画流水线）。
 * key → assets/art/units/{key}.png 懒加载（GL 线程），未命中返回 null（Assets 落占位）。
 * miss 结果缓存（避免每帧磁盘 stat）；pathOf 纯函数可 headless 测。
 */
public final class RealArt {
    static final String ROOT = "art/units/";

    private final Map<String, TextureRegion> cache = new HashMap<String, TextureRegion>();
    private final Set<String> misses = new HashSet<String>();
    private final List<Texture> textures = new ArrayList<Texture>();

    /** key → 素材相对路径（纯函数，测试用） */
    public static String pathOf(String key) {
        return ROOT + key + ".png";
    }

    /** 懒加载查 key；文件不存在返回 null（回退占位；miss 缓存） */
    public TextureRegion region(String key) {
        TextureRegion cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (misses.contains(key)) {
            return null;
        }
        FileHandle file = Gdx.files.internal(pathOf(key));
        if (!file.exists()) {
            misses.add(key);
            return null;
        }
        Texture texture = new Texture(file);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        textures.add(texture);
        cached = new TextureRegion(texture);
        cache.put(key, cached);
        return cached;
    }

    public void dispose() {
        for (Texture texture : textures) {
            texture.dispose();
        }
        textures.clear();
        cache.clear();
        misses.clear();
    }
}
