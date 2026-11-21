package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 资源门面（render §7.6 注入式；只允许出现在 render/ 与 screens/，禁静态持有）。
 *
 * <p>region 逐 key 兜底：先真素材层（Q4=B：art/units/ 逐帧 PNG，miss 回退）、未命中落占位，
 * 仍缺直接抛错（fail-fast，不静默给错图）。font 换载 Fusion Pixel 12px 位图字体
 * （文件缺失回退内置默认——headless 测试不依赖素材存在，Q4 守卫沿用）。
 * skin/sound 推 Phase 5/7（差异声明 #4）。
 *
 * <p><b>FX 软路径</b>（2026-11-21 亡语 FX 缺口回归加固）：装饰性特效键走 {@link #regionFx}——
 * 缺键软告警（每键一次，与 JsonLoader 软告警同口径）+ 降级通用爆点/白片，<b>装饰性贴图永不崩游戏</b>；
 * 结构性键（单位帧/面板/白片）仍走 {@link #region} 硬失败——缺结构图属程序错误必须即死。
 */
public final class Assets {
    /** 真素材层字体文件（Hiero 生成 .fnt + .png；缺文件回退内置默认） */
    static final String FONT_PATH = "font/fusion_pixel_12.fnt";

    private final PlaceholderArt art;
    /** 真素材层（Q4=B：itch 小人；miss 自动回退占位） */
    private final RealArt realArt = new RealArt();
    private BitmapFont font;
    /** FX 软告警去重（每键一次） */
    private final Set<String> warnedFxKeys = new HashSet<String>();

    public Assets(PlaceholderArt art) {
        this.art = Objects.requireNonNull(art, "art 不能为 null");
    }

    /** 真素材优先、未命中落占位；仍缺抛 IllegalStateException（= 程序错误；结构性键专用，FX 走 regionFx） */
    public TextureRegion region(String key) {
        TextureRegion real = realArt.region(key);
        if (real != null) {
            return real;
        }
        TextureRegion region = art.region(key);
        if (region == null) {
            throw new IllegalStateException("资源缺 key: " + key);
        }
        return region;
    }

    /**
     * FX 软路径（装饰性特效专用）：真素材 → 占位 → 仍缺则软告警（每键一次）并降级通用爆点
     * （再缺降白片）——特效缺图跳过视觉不中断对局；键集完备性由 FxKeyCoverageTest 在 JVM 层锁定。
     */
    public TextureRegion regionFx(String key) {
        TextureRegion real = realArt.region(key);
        if (real != null) {
            return real;
        }
        TextureRegion region = art.region(key);
        if (region != null) {
            return region;
        }
        if (warnedFxKeys.add(key)) {
            System.err.println("[Assets][软告警] FX 缺 key: " + key + " —— 降级通用爆点（装饰性贴图不崩局）");
        }
        TextureRegion fallback = art.region(PlaceholderKeys.HIT_DEFAULT);
        return fallback != null ? fallback : art.region(PlaceholderKeys.WHITE);
    }

    /** Fusion Pixel 12px 位图字体（Q4=B；文件缺失回退内置默认——headless 测试不依赖素材存在） */
    public BitmapFont font() {
        if (font == null) {
            FileHandle fontFile = Gdx.files.internal(FONT_PATH);
            font = fontFile.exists() ? new BitmapFont(fontFile, false) : new BitmapFont();
        }
        return font;
    }

    /** Main.dispose 调：字体、真素材与占位 Texture 全弃 */
    public void dispose() {
        if (font != null) {
            font.dispose();
            font = null;
        }
        realArt.dispose();
        art.dispose();
    }
}
