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
import com.voidvvv.kz_auto_chess_n.render.StarColors;

/**
 * 单位视图（render §六铁律 3：生命周期 = BattleState——rebuild 整体重建、clear 整体销毁）。
 *
 * <p>持 BattleUnit 只读引用（铁律 1：只调读方法）；跳格插值走 LerpMotion（render §4.2）、
 * 动画 FSM 走 UnitAnimState（§5.1）。品质改造 §1.3：棋体 38px 正方（横向居中、底边 cy−16 锚点，
 * 外接框较 32px 格约大 19%）、脚下 18×4 影子、全格色框换底角标（SideColors.drawCornerMark）、
 * 血条 24×3 / 能量与充能 1px 条按 §1.3 基线；死亡缩放比例不变、自锚点向上收缩。
 */
public final class UnitView {
    /** 血条底（#121820） */
    private static final Color BAR_TRACK = new Color(0.071f, 0.094f, 0.125f, 1f);
    /** 血量（#8DDA9A） */
    private static final Color BAR_HP_FILL = new Color(0.553f, 0.855f, 0.604f, 1f);
    /** 能量条（#7CAEDC α0.75） */
    private static final Color BAR_ENERGY = new Color(0.486f, 0.682f, 0.863f, 0.75f);
    /** 充能条（白 α0.42——§1.3 未单列色，沿白系） */
    private static final Color BAR_CHARGE = new Color(1f, 1f, 1f, 0.42f);
    /** 脚下影子（#080E12）——包级共享（BattleRenderer 备战棋体同源，§1.3） */
    static final Color UNIT_SHADOW = new Color(0.031f, 0.055f, 0.071f, 1f);
    /** 影子透明度（§1.3：己方 1.00；战斗期两侧同值，备战敌影 0.78 在 BattleRenderer） */
    private static final float SHADOW_ALPHA = 1f;
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
        if (unit.isDowned()) {
            alpha *= 0.55f; // 倒地半透明（C7 表现层：未清扫但不可行动，CP19）
        }
        float size = BoardGeometry.UNIT_DRAW_PX * (1f - 0.4f * fade); // 死亡缩放（比例不变，自底锚上收）

        drawShadow(batch, cx, cy, alpha); // 脚下影子（棋体之下——§1.3）
        TextureRegion region = assets.region(PlaceholderKeys.unitFrame(
                unit.getTemplate().getId(), animName(), anim.frameIndex()));
        boolean wasFlip = region.isFlipX();
        if (enemy != wasFlip) {
            region.flip(true, false);
        }
        Color star = StarColors.of(unit.getStar());
        batch.setColor(star.r, star.g, star.b, alpha);
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
            SideColors.drawCornerMark(batch, assets.region(PlaceholderKeys.WHITE), cx, cy,
                    enemy ? SideColors.ENEMY_MARK : SideColors.PLAYER_MARK); // 底角标（§1.3 换全格色框）
        }
    }

    /** 脚下影子（品质改造 §1.3：18×4 #080E12，格内底缘铺开，棋体之下） */
    private void drawShadow(SpriteBatch batch, int cx, int cy, float alpha) {
        batch.setColor(UNIT_SHADOW.r, UNIT_SHADOW.g, UNIT_SHADOW.b, SHADOW_ALPHA * alpha);
        batch.draw(assets.region(PlaceholderKeys.WHITE),
                cx - BoardGeometry.UNIT_SHADOW_W / 2f,
                BoardGeometry.unitDrawBottom(cy),
                BoardGeometry.UNIT_SHADOW_W, BoardGeometry.UNIT_SHADOW_H);
        batch.setColor(WHITE);
    }

    /**
     * 本体精灵绘制（品质改造 §1.3：38px 正方、横向居中、底边 cy−16 锚点）：先叠加受击抖动水平偏移
     * （整数像素），再叠加攻击摆动（ROTATE：底部中心为轴小幅旋转——像素规则第三例外，render §八#3；
     * TRANSLATE：沿朝向整数像素平移探身）。白闪层复用同一变换（attack_feedback FP1/FP2）。
     * 摆动以单位自身朝向为正：y 向上坐标系正角 = 逆时针，玩家单位（朝右）前倾 = 负角，
     * 敌方经 flipX 镜像后取反（左右观感对称，不取攻击目标方位）。
     */
    private void drawBody(SpriteBatch batch, TextureRegion region, int cx, int cy, float size) {
        float x = cx + anim.hitShakeDx() - size / 2f;
        float y = BoardGeometry.unitDrawBottom(cy);
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

    // —— 血条（24×3，基线 cy+21）/ 能量条（1px，cy+25）/ 充能条（1px，cy+27）/ 星级色点（§1.3） ——

    private void drawBars(SpriteBatch batch, int cx, int cy) {
        TextureRegion white = assets.region(PlaceholderKeys.WHITE);
        float barX = cx - BoardGeometry.BAR_HP_W / 2f;
        batch.setColor(BAR_TRACK);
        batch.draw(white, barX, cy + BoardGeometry.BAR_HP_BASELINE,
                BoardGeometry.BAR_HP_W, BoardGeometry.BAR_HP_H);
        float hp = Math.max(0f, Math.min(1f, unit.hpRatio()));
        batch.setColor(BAR_HP_FILL);
        batch.draw(white, barX, cy + BoardGeometry.BAR_HP_BASELINE,
                BoardGeometry.BAR_HP_W * hp, BoardGeometry.BAR_HP_H);
        batch.setColor(BAR_ENERGY);
        batch.draw(white, barX, cy + BoardGeometry.BAR_ENERGY_BASELINE,
                BoardGeometry.BAR_HP_W * unit.getEnergy() / GameBalance.ENERGY_MAX, 1f);
        // 攻击蓄力条（render §5.6 第四条微条）：轮询 attackTimer/attackInterval、钳制满格（眩晕时满格悬停）；纯表现零事件
        float charge = Math.min(1f, unit.getAttackTimer() / unit.attackInterval());
        batch.setColor(BAR_CHARGE);
        batch.draw(white, barX, cy + BoardGeometry.BAR_CHARGE_BASELINE,
                BoardGeometry.BAR_HP_W * charge, 1f);
        batch.setColor(STAR_GOLD);
        for (int i = 0; i < unit.getStar(); i++) { // 脚下星级点（§1.3：保留底部点位与原星级色）
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
