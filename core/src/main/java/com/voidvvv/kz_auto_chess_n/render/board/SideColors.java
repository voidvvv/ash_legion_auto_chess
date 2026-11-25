package com.voidvvv.kz_auto_chess_n.render.board;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * 敌我标识（用户反馈 P1b：16px 像素风下"水平翻转 + 轻微变暗"不可辨 → 色框；
 * 品质改造 §1.3：全格红绿框换<b>底角标</b>——两个 6×2px 短条，敌 #E6816B / 友 #84C9AF α0.90）。
 *
 * <p>战斗期（UnitView）与备战期（BattleRenderer 敌阵虚影）共用同一语义；
 * 角标落在<b>所在格</b>左下角（格缘基准，棋子放大到 38px 不漂移、格命中不变）。
 */
public final class SideColors {
    /** 友方底角标（#84C9AF） */
    public static final Color PLAYER_MARK = new Color(0.518f, 0.788f, 0.686f, 0.90f);
    /** 敌方底角标（#E6816B） */
    public static final Color ENEMY_MARK = new Color(0.902f, 0.506f, 0.420f, 0.90f);
    /** 备战期敌阵预览整体透明度（侦察虚影，P1b） */
    public static final float ENEMY_PREVIEW_ALPHA = 0.55f;

    /** 角标短条（§1.3：两个 6×2px） */
    public static final int MARK_ARM_PX = 6;
    public static final int MARK_THICKNESS_PX = 2;

    private SideColors() {
    }

    /**
     * 底角标（品质改造 §1.3）：所在格左下角两个 6×2px 短条（底边横条 + 左缘竖条，组成 L）。
     * cx/cy 为所在格中心（与棋子锚点同源）；格缘基准、整数对齐（像素规则 render §八.2）。
     */
    public static void drawCornerMark(SpriteBatch batch, TextureRegion white, int cx, int cy, Color color) {
        int px = cx - BoardGeometry.CELL / 2;
        int py = cy - BoardGeometry.CELL / 2;
        batch.setColor(color);
        batch.draw(white, px, py, MARK_ARM_PX, MARK_THICKNESS_PX);                      // 底边横条
        batch.draw(white, px, py + MARK_THICKNESS_PX, MARK_THICKNESS_PX, MARK_ARM_PX);  // 左缘竖条
        batch.setColor(Color.WHITE);
    }
}
