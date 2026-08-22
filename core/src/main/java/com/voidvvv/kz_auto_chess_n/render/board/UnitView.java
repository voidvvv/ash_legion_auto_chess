package com.voidvvv.kz_auto_chess_n.render.board;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;

/**
 * 单位视图（render §六铁律 3：生命周期 = BattleState——rebuild 整体重建、clear 整体销毁）。
 *
 * <p>持 BattleUnit 只读引用（铁律 1：只调读方法）；跳格插值走 LerpMotion（render §4.2）、
 * 动画 FSM 走 UnitAnimState（§5.1）。血条/能量条 = 1×1 白 region tint（口径 #19）；
 * 星级 = 脚下 1~3 个 2px 色点；死亡 = 缩放淡出 0.5s（口径 #13，像素规则例外）。
 */
public final class UnitView {
    private static final Color BAR_RED = new Color(0.75f, 0.15f, 0.15f, 1f);
    private static final Color BAR_GREEN = new Color(0.2f, 0.8f, 0.25f, 1f);
    private static final Color BAR_YELLOW = new Color(0.95f, 0.85f, 0.2f, 1f);
    private static final Color STAR_GOLD = new Color(1f, 0.85f, 0.3f, 1f);
    private static final Color WHITE = new Color(Color.WHITE);

    private final BattleUnit unit;
    private final Assets assets;
    private final UnitAnimState anim = new UnitAnimState();
    private final LerpMotion motion;
    private final boolean enemy;
    private int lastGridX;
    private int lastGridY;

    /**
     * @param startGridX 起始格 x（P1c：备战期位置——首帧 update 轮询到锚点差分即经 LerpMotion 滑入；
     *                   无匹配备战位置时传战斗锚点，行为等同直落）
     */
    public UnitView(BattleUnit unit, Assets assets, int startGridX, int startGridY) {
        this.unit = unit;
        this.assets = assets;
        this.motion = new LerpMotion(Math.max(0.25f, unit.getEffective(StatKey.MOVE_SPEED)));
        this.motion.reset(startGridX, startGridY);
        this.lastGridX = startGridX;
        this.lastGridY = startGridY;
        this.enemy = unit.getSide() == Side.ENEMY;
    }

    public int unitId() {
        return unit.getId();
    }

    public BattleUnit unit() {
        return unit;
    }

    public UnitAnimState anim() {
        return anim;
    }

    /** 每渲染帧：轮询逻辑坐标差分驱动插值与 FSM */
    public void update(float renderClock, float dt) {
        if (unit.getGridX() != lastGridX || unit.getGridY() != lastGridY) {
            motion.onCellPolled(unit.getGridX(), unit.getGridY(), renderClock);
            lastGridX = unit.getGridX();
            lastGridY = unit.getGridY();
        }
        anim.setMoving(!motion.isSettled(renderClock));
        anim.update(dt);
    }

    /** 插值中的虚拟像素中心（整数吸附，render §八.2） */
    public int virtualX(float renderClock) {
        return Math.round(BoardGeometry.BOARD_X
                + motion.positionX(renderClock) * BoardGeometry.CELL + BoardGeometry.CELL / 2f);
    }

    public int virtualY(float renderClock) {
        return Math.round(BoardGeometry.BOARD_Y + BoardGeometry.BOARD_H
                - (motion.positionY(renderClock) + 1f) * BoardGeometry.CELL + BoardGeometry.CELL / 2f);
    }

    public void draw(SpriteBatch batch, float renderClock) {
        float fade = anim.deathFadeRatio();
        if (unit.isCleaned() && fade >= 1f) {
            return; // 淡出完毕不再绘制
        }
        int cx = virtualX(renderClock);
        int cy = virtualY(renderClock);
        float alpha = 1f - fade;
        float size = BoardGeometry.CELL * (1f - 0.4f * fade); // 死亡缩放（例外允许）

        TextureRegion region = assets.region(PlaceholderKeys.unitFrame(
                unit.getTemplate().getId(), animName(), anim.frameIndex()));
        boolean wasFlip = region.isFlipX();
        if (enemy != wasFlip) {
            region.flip(true, false);
        }
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(region, cx - size / 2f, cy - size / 2f, size, size);
        if (anim.hitFlashRatio() > 0f) { // 受击白闪叠加层
            batch.setColor(1f, 1f, 1f, anim.hitFlashRatio() * 0.8f * alpha);
            batch.draw(region, cx - size / 2f, cy - size / 2f, size, size);
        }
        batch.setColor(WHITE);
        if (region.isFlipX() != wasFlip) {
            region.flip(true, false); // 用后即还
        }
        if (fade == 0f) {
            drawBars(batch, cx, cy);
            SideColors.drawBorder(batch, assets.region(PlaceholderKeys.WHITE), cx, cy,
                    enemy ? SideColors.ENEMY : SideColors.PLAYER); // 敌我色框（P1b，与备战期同语义）
        }
    }

    // —— 血条（红绿 2px）/ 能量条（黄 1px）/ 星级色点（口径 #19） ——

    private void drawBars(SpriteBatch batch, int cx, int cy) {
        TextureRegion white = assets.region(PlaceholderKeys.WHITE);
        batch.setColor(BAR_RED);
        batch.draw(white, cx - 12f, cy + 17f, 24f, 2f);
        float hp = Math.max(0f, Math.min(1f, unit.hpRatio()));
        batch.setColor(BAR_GREEN);
        batch.draw(white, cx - 12f, cy + 17f, 24f * hp, 2f);
        batch.setColor(BAR_YELLOW);
        batch.draw(white, cx - 12f, cy + 19f, 24f * unit.getEnergy() / GameBalance.ENERGY_MAX, 1f);
        batch.setColor(STAR_GOLD);
        for (int i = 0; i < unit.getStar(); i++) { // 脚下星级点
            batch.draw(white, cx - (unit.getStar() * 4f - 2f) / 2f + i * 4f, cy - 19f, 2f, 2f);
        }
        batch.setColor(WHITE);
    }

    private String animName() {
        switch (anim.current()) {
            case WALK: return PlaceholderKeys.ANIM_WALK;
            case ATTACK: return PlaceholderKeys.ANIM_ATTACK;
            case CAST: return PlaceholderKeys.ANIM_CAST;
            case DEATH: return PlaceholderKeys.ANIM_DEATH;
            default: return PlaceholderKeys.ANIM_IDLE;
        }
    }
}
