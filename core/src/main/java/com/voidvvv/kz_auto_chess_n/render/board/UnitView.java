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
    private static final Color BAR_DARK = new Color(0.12f, 0.12f, 0.15f, 1f); // 蓄力条底槽（render §5.6，待调）
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
        drawBody(batch, region, cx, cy, size);
        if (anim.hitFlashRatio() > 0f) { // 受击白闪叠加层（与本体共用同一变换）
            batch.setColor(1f, 1f, 1f, anim.hitFlashRatio() * 0.8f * alpha);
            drawBody(batch, region, cx, cy, size);
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

    /**
     * 本体精灵绘制：先叠加受击抖动水平偏移（整数像素），再叠加攻击摆动
     * （ROTATE：底部中心为轴小幅旋转——像素规则第三例外，render §八#3；
     * TRANSLATE：沿朝向整数像素平移探身）。白闪层复用同一变换（attack_feedback FP1/FP2）。
     * 摆动以单位自身朝向为正：y 向上坐标系正角 = 逆时针，玩家单位（朝右）前倾 = 负角，
     * 敌方经 flipX 镜像后取反（左右观感对称，不取攻击目标方位）。
     */
    private void drawBody(SpriteBatch batch, TextureRegion region, int cx, int cy, float size) {
        float x = cx + anim.hitShakeDx() - size / 2f;
        float y = cy - size / 2f;
        if (UnitAnimState.ATTACK_SWING_MODE == UnitAnimState.AttackSwingMode.TRANSLATE) {
            int swingDx = anim.attackSwingDx() * (enemy ? -1 : 1); // 沿朝向前倾（敌方前进 = −x）
            batch.draw(region, x + swingDx, y, size, size);
            return;
        }
        float degrees = -anim.attackSwingDegrees() * (enemy ? -1f : 1f); // 正 = 前倾
        if (degrees == 0f) {
            batch.draw(region, x, y, size, size); // 未摆动：既有四参路径（关断/未触发与现状一致）
            return;
        }
        batch.draw(region, x, y, size / 2f, 0f, size, size, 1f, 1f, degrees); // 底部中心轴
    }

    // —— 血条（红绿 2px）/ 能量条（黄 1px）/ 攻击蓄力条（白 1px，render §5.6）/ 星级色点（口径 #19） ——

    private void drawBars(SpriteBatch batch, int cx, int cy) {
        TextureRegion white = assets.region(PlaceholderKeys.WHITE);
        batch.setColor(BAR_RED);
        batch.draw(white, cx - 12f, cy + 17f, 24f, 2f);
        float hp = Math.max(0f, Math.min(1f, unit.hpRatio()));
        batch.setColor(BAR_GREEN);
        batch.draw(white, cx - 12f, cy + 17f, 24f * hp, 2f);
        batch.setColor(BAR_YELLOW);
        batch.draw(white, cx - 12f, cy + 19f, 24f * unit.getEnergy() / GameBalance.ENERGY_MAX, 1f);
        // 攻击蓄力条（render §5.6 第四条微条）：轮询 attackTimer/attackInterval、钳制满格（眩晕时满格悬停）；纯表现零事件
        float charge = Math.min(1f, unit.getAttackTimer() / unit.attackInterval());
        batch.setColor(BAR_DARK);
        batch.draw(white, cx - 12f, cy + 21f, 24f, 1f);
        batch.setColor(WHITE);
        batch.draw(white, cx - 12f, cy + 21f, 24f * charge, 1f);
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
