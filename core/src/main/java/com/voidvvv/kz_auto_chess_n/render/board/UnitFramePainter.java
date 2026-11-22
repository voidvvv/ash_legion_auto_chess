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
 */
public final class UnitFramePainter {

    private UnitFramePainter() {
    }

    /**
     * 以左下角 (x, y) 为锚画一帧（正方形 size×size）。
     *
     * @param enemyFace true = 水平翻转（敌我相对而立，单套贴图）
     * @param alpha     整体透明度
     * @param tint      星级色（{@link StarColors#of}）
     */
    public static void draw(Batch batch, Assets assets, String unitId, String anim, int frame,
                            float x, float y, float size, boolean enemyFace, float alpha, Color tint) {
        TextureRegion region = assets.region(PlaceholderKeys.unitFrame(unitId, anim, frame));
        boolean wasFlip = region.isFlipX();
        if (enemyFace != wasFlip) {
            region.flip(true, false);
        }
        batch.setColor(tint.r, tint.g, tint.b, alpha);
        batch.draw(region, x, y, size, size);
        batch.setColor(Color.WHITE);
        if (region.isFlipX() != wasFlip) {
            region.flip(true, false); // 用后即还
        }
    }
}
