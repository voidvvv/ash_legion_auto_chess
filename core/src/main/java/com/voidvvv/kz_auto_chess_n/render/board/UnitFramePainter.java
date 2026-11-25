package com.voidvvv.kz_auto_chess_n.render.board;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.StarColors;

/**
 * 棋子帧绘制助手（UI 抽屉化批2·UI_PLAN §2.3「棋子帧绘制助手化」）：
 * 取帧 → 朝向翻转（用后即还，region 共享不残留状态）→ 星级色 {@link StarColors} tint → 画 → 批次色还原白。
 * 棋盘域 {@link BattleRenderer}（备战部署帧 / 敌阵虚影）与抽屉（头像格 1:1、立绘 ×2）共用同一套观感；
 * 接受任意 {@link Batch}（SpriteBatch 与 Scene2d 批次皆可）。
 *
 * <p><b>原创素材期 tint 口径</b>（品质改造第二阶段）：占位单位为白体，星级色 tint 是唯一分星手段；
 * 原创单位帧自带颜色，tint 会整帧偏色——命中 generated_v2 真素材时 tint 自动降为白
 * （星级仍由脚下星级点 / 头像格角标表达，见 {@link Assets#isGeneratedArt}）。
 */
public final class UnitFramePainter {

    private UnitFramePainter() {
    }

    /**
     * 以左下角 (x, y) 为锚画一帧（正方形 size×size）。
     *
     * @param enemyFace true = 水平翻转（敌我相对而立，单套贴图）
     * @param alpha     整体透明度
     * @param tint      星级色（{@link StarColors#of}；原创真素材命中时内部降为白）
     */
    public static void draw(Batch batch, Assets assets, String unitId, String anim, int frame,
                            float x, float y, float size, boolean enemyFace, float alpha, Color tint) {
        String key = PlaceholderKeys.unitFrame(unitId, anim, frame);
        TextureRegion region = assets.region(key);
        boolean wasFlip = region.isFlipX();
        if (enemyFace != wasFlip) {
            region.flip(true, false);
        }
        if (assets.isGeneratedArt(key)) {
            batch.setColor(1f, 1f, 1f, alpha); // 原创真彩图：不做星级 tint（防整帧偏色）
        } else {
            batch.setColor(tint.r, tint.g, tint.b, alpha);
        }
        batch.draw(region, x, y, size, size);
        batch.setColor(Color.WHITE);
        if (region.isFlipX() != wasFlip) {
            region.flip(true, false); // 用后即还
        }
    }
}
