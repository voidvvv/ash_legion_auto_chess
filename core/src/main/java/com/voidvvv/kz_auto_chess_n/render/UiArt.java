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
 * UI 真素材层（UI 抽屉化批0；与 {@link RealArt} 同构、分根）。
 * id → assets/art/ui/{id}.png 懒加载（GL 线程），未命中返回 null（Assets 落占位）。
 * miss 结果缓存（避免每帧磁盘 stat）；pathOf 纯函数可 headless 测。
 *
 * <p>键集与切分线见 {@link UiKeys}（1:1 列；{@code art/ui/1_25x/} 备用列不进代码——
 * 抽屉走不缩放层，1 虚拟像素 = 1 舞台像素）。只经 {@link Assets#uiRegion}/{@link Assets#uiPatch} 访问。
 */
public final class UiArt {
    static final String ROOT = "art/ui/";

    private final Map<String, TextureRegion> cache = new HashMap<String, TextureRegion>();
    private final Set<String> misses = new HashSet<String>();
    private final List<Texture> textures = new ArrayList<Texture>();

    /** id → 素材相对路径（纯函数，测试用） */
    public static String pathOf(String id) {
        return ROOT + id + ".png";
    }

    /** 懒加载查 id；文件不存在返回 null（回退占位；miss 缓存） */
    public TextureRegion region(String id) {
        TextureRegion cached = cache.get(id);
        if (cached != null) {
            return cached;
        }
        if (misses.contains(id)) {
            return null;
        }
        FileHandle file = Gdx.files.internal(pathOf(id));
        if (!file.exists()) {
            misses.add(id);
            return null;
        }
        Texture texture = new Texture(file);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        textures.add(texture);
        cached = new TextureRegion(texture);
        cache.put(id, cached);
        return cached;
    }

    /** miss 缓存条数（包级实例 getter 供 headless 测试观察缓存——沿 ShopBar.getHoveredSlot 暴露形态） */
    int missCount() {
        return misses.size();
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
