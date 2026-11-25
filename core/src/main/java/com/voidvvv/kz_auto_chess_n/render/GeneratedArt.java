package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 原创素材真图层（品质改造第二阶段：全面替换运行时贴图）。
 *
 * <p>数据源 = {@code assets/art/generated_v2/manifest.json}（Codex 交付清单，只读不改）：
 * key → PNG 路径 + 帧裁切 {@code atlas_rect [x,y,w,h]}（像素、左上原点）。覆盖单位图集帧
 * （33 单位 × 12 帧，每单位一张 128×96 四列三行图集）、FX/状态/数字、UI 面板与按钮、
 * 棋盘框与地砖、头像/英雄肖像、装备与羁绊图标、主菜单背景与白色基础纹理。
 *
 * <p>生效契约链不变：{@link Assets#region}/{@link #region(String)} 真素材优先 → 占位层兜底 →
 * 仍缺按各出口既定口径（结构键 fail-fast / FX 软降级）。每个 PNG 只创建一次 {@link Texture}
 * （min/mag = Nearest，像素艺术口径），同 PNG 的多个 key 经 atlas_rect 各取
 * {@link TextureRegion}；dispose 按 Texture 实例整体释放。miss 结果缓存（避免每帧磁盘 stat）。
 *
 * <p>headless 守卫（沿 RealArt/UiArt 先例）：manifest 文件缺失 → 空索引 + 一次日志，全部落占位，
 * 测试期不依赖素材入库。{@link #parse(String)} 为纯函数（JsonReader 零 GL），供 JVM 层对账测试。
 */
public final class GeneratedArt {
    /** 交付清单路径（相对 assets/ 根；Codex 生成文件，运行期只读） */
    public static final String MANIFEST_PATH = "art/generated_v2/manifest.json";

    /** 单位图集帧尺寸（对账锚：33 张单位 PNG 均为 128×96、帧 rect 32×32） */
    public static final int UNIT_ATLAS_W = 128;
    public static final int UNIT_ATLAS_H = 96;
    public static final int UNIT_FRAME_PX = 32;

    /** 索引条目：PNG 路径 + 帧裁切（hasRect = false 表示整图） */
    public static final class Entry {
        private final String path;
        private final int x;
        private final int y;
        private final int w;
        private final int h;

        Entry(String path, int x, int y, int w, int h) {
            this.path = path;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public String path() {
            return path;
        }

        public boolean hasRect() {
            return w > 0 && h > 0;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int w() {
            return w;
        }

        public int h() {
            return h;
        }
    }

    private final Map<String, Entry> entries;
    private final Map<String, TextureRegion> regions = new HashMap<String, TextureRegion>();
    private final Map<String, Texture> texturesByPath = new HashMap<String, Texture>();
    private final Set<String> misses = new HashSet<String>();

    private GeneratedArt(Map<String, Entry> entries) {
        this.entries = entries;
    }

    /** 默认实例：读 assets 内交付清单；文件缺失 → 空索引（占位兜底期，一次日志） */
    public static GeneratedArt loadDefault() {
        FileHandle file = Gdx.files.internal(MANIFEST_PATH);
        if (!file.exists()) {
            System.err.println("[GeneratedArt][软告警] 交付清单缺失: " + MANIFEST_PATH
                    + " —— 真图层置空，全部走占位兜底");
            return new GeneratedArt(new HashMap<String, Entry>());
        }
        return parse(file.readString("UTF-8"));
    }

    /**
     * 解析交付清单（纯函数，headless 可测）：只收带 {@code path} 的条目（字体页等未交付项不入索引）；
     * replacement_key 即运行期 key（与既有 {@link PlaceholderKeys}/{@link UiKeys} 键同源）。
     */
    public static GeneratedArt parse(String manifestJson) {
        Map<String, Entry> map = new HashMap<String, Entry>();
        JsonValue root = new JsonReader().parse(manifestJson);
        JsonValue assets = root.get("assets");
        if (assets == null) {
            throw new IllegalStateException("交付清单缺 assets 数组: " + MANIFEST_PATH);
        }
        for (JsonValue row = assets.child(); row != null; row = row.next) {
            String key = row.getString("replacement_key");
            String path = row.getString("path", null);
            if (key == null || path == null || path.isEmpty()) {
                continue; // 未交付项（如 font/fusion_pixel_12.png）——占位/既有文件继续承担
            }
            JsonValue rect = row.get("atlas_rect");
            Entry entry = rect == null || !rect.isArray() || rect.size < 4
                    ? new Entry(path, 0, 0, 0, 0)
                    : new Entry(path, rect.getInt(0), rect.getInt(1), rect.getInt(2), rect.getInt(3));
            if (map.put(key, entry) != null) {
                throw new IllegalStateException("交付清单 key 重复: " + key);
            }
        }
        return new GeneratedArt(map);
    }

    /** 索引是否含 key（不触盘、不建纹理；键存在性对账用） */
    public boolean containsKey(String key) {
        return key != null && entries.containsKey(key);
    }

    /** 已入索引的 key 清单（不可变快照；对账测试用） */
    public List<String> keys() {
        return new ArrayList<String>(entries.keySet());
    }

    /** 索引条数 */
    public int entryCount() {
        return entries.size();
    }

    /** 索引条目（无则 null；纯数据不触盘） */
    public Entry entry(String key) {
        return key == null ? null : entries.get(key);
    }

    /** 已解析为真素材（region 非 null）——真彩图不做星级 tint 的判定口（缓存后零磁盘） */
    public boolean hasArt(String key) {
        return region(key) != null;
    }

    /** 懒加载查 key；清单未登记或 PNG 缺失返回 null（Assets 层落占位；miss 缓存） */
    public TextureRegion region(String key) {
        if (key == null) {
            return null;
        }
        TextureRegion cached = regions.get(key);
        if (cached != null) {
            return cached;
        }
        if (misses.contains(key)) {
            return null;
        }
        Entry entry = entries.get(key);
        if (entry == null) {
            misses.add(key);
            return null;
        }
        Texture texture = texturesByPath.get(entry.path());
        if (texture == null) {
            FileHandle file = Gdx.files.internal(entry.path());
            if (!file.exists()) {
                misses.add(key); // 清单在库但 PNG 缺失：按 miss 处理落占位（不崩局）
                return null;
            }
            texture = new Texture(file);
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            texturesByPath.put(entry.path(), texture);
        }
        cached = entry.hasRect()
                ? new TextureRegion(texture, entry.x(), entry.y(), entry.w(), entry.h())
                : new TextureRegion(texture);
        regions.put(key, cached);
        return cached;
    }

    /** miss 缓存条数（包级 getter 供 headless 测试观察缓存） */
    int missCount() {
        return misses.size();
    }

    /** 全部 Texture 生命周期释放（Main.dispose 经 Assets 调；每 PNG 一实例整体回收） */
    public void dispose() {
        for (Texture texture : texturesByPath.values()) {
            texture.dispose();
        }
        texturesByPath.clear();
        regions.clear();
        misses.clear();
    }
}
