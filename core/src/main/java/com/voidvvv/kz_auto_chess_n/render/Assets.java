package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.Objects;

/**
 * 资源门面（render §7.6 注入式；只允许出现在 render/ 与 screens/，禁静态持有）。
 *
 * <p>region 逐 key 兜底（真图集接入点在 Phase 5+：先查真图集、未命中落占位），
 * 缺 key 直接抛错（fail-fast，不静默给错图）。font 为 libGDX 内置默认（Q4 占位；
 * Phase 5 换 Fusion Pixel 零改调用方）。skin/sound 推 Phase 5/7（差异声明 #4）。
 */
public final class Assets {
    private final PlaceholderArt art;
    private BitmapFont font;

    public Assets(PlaceholderArt art) {
        this.art = Objects.requireNonNull(art, "art 不能为 null");
    }

    /** @throws IllegalStateException key 未生成（占位图集与 PlaceholderKeys 对账后仍缺 = 程序错误） */
    public TextureRegion region(String key) {
        TextureRegion region = art.region(key);
        if (region == null) {
            throw new IllegalStateException("资源缺 key: " + key);
        }
        return region;
    }

    /** 内置默认字体（懒构造单例；dispose 后重新获取会重建） */
    public BitmapFont font() {
        if (font == null) {
            font = new BitmapFont();
        }
        return font;
    }

    /** Main.dispose 调：字体与占位 Texture 全弃 */
    public void dispose() {
        if (font != null) {
            font.dispose();
            font = null;
        }
        art.dispose();
    }
}
