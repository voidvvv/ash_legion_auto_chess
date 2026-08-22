package com.voidvvv.kz_auto_chess_n.render.board;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * 敌我标识色框（用户反馈 P1b：16px 像素风下"水平翻转 + 轻微变暗"不可辨）。
 *
 * <p>备战期预览（BattleRenderer.drawShopping）与战斗期 UnitView 共用同一语义：
 * 玩家蓝框 / 敌方红框；边框 = WHITE region tint、2px、整数对齐（像素规则 render §八.2）。
 */
public final class SideColors {
    /** 玩家侧边框蓝 */
    public static final Color PLAYER = new Color(0.25f, 0.6f, 1f, 1f);
    /** 敌方侧边框红 */
    public static final Color ENEMY = new Color(1f, 0.3f, 0.25f, 1f);
    /** 备战期敌阵预览整体透明度（侦察虚影，P1b） */
    public static final float ENEMY_PREVIEW_ALPHA = 0.55f;

    private SideColors() {
    }

    /** 32px 格四周 2px 色框（对边整宽 + 两侧内缩 2px，四角不重叠；整数坐标） */
    public static void drawBorder(SpriteBatch batch, TextureRegion white, int cx, int cy, Color color) {
        int half = BoardGeometry.CELL / 2;
        int x = cx - half;
        int y = cy - half;
        batch.setColor(color);
        batch.draw(white, x, y, BoardGeometry.CELL, 2f);
        batch.draw(white, x, y + BoardGeometry.CELL - 2, BoardGeometry.CELL, 2f);
        batch.draw(white, x, y + 2, 2f, BoardGeometry.CELL - 4f);
        batch.draw(white, x + BoardGeometry.CELL - 2, y + 2, 2f, BoardGeometry.CELL - 4f);
        batch.setColor(Color.WHITE);
    }
}
