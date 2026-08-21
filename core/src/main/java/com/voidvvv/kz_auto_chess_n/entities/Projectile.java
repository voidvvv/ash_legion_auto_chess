package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.SkillData;

import java.util.Objects;

/**
 * 在途锁定弹（HOMING 唯一形态，Q2 裁决；battle §5.3）。
 *
 * <p>连续坐标（格单位），出生 = 施放者格中心；推进朝<b>目标当前格中心</b>欧氏直线逼近
 * （口径 #14）。载荷在发射/施放时冻结（口径 #13）：攻击力快照 + 暴击标志 +
 * 公共缩放因子（技能 = 星级缩放 × (1+skillPower 快照/100)；普攻 = 1）——
 * 施放者被清扫不影响在途弹，命中结算全部取冻结值。
 *
 * <p>位置为受控可变（framework-internal：仅供 systems 包推进）。
 */
public final class Projectile {
    private final int sourceId;
    private final int targetId;
    private final float attackSnapshot;
    private final boolean crit;
    private final SkillData skill;        // null = 普攻弹
    private final float scaleFactor;     // 技能公共缩放因子；普攻 = 1
    private float posX;
    private float posY;

    public Projectile(int sourceId, int targetId, float posX, float posY,
                      float attackSnapshot, boolean crit, SkillData skill, float scaleFactor) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.posX = posX;
        this.posY = posY;
        this.attackSnapshot = attackSnapshot;
        this.crit = crit;
        this.skill = skill;
        this.scaleFactor = scaleFactor;
    }

    public int getSourceId() { return sourceId; }
    public int getTargetId() { return targetId; }
    public float getPosX() { return posX; }
    public float getPosY() { return posY; }
    /** 出手时有效攻击快照（口径 #13） */
    public float getAttackSnapshot() { return attackSnapshot; }
    /** 普攻发射时已 roll 的暴击标志；技能弹恒 false */
    public boolean isCrit() { return crit; }
    /** @return 技能弹非 null，普攻弹为 null */
    public SkillData getSkill() { return skill; }
    /** 公共缩放因子：技能 = value 外的星级 × skillPower 部分；普攻 = 1 */
    public float getScaleFactor() { return scaleFactor; }

    /**
     * framework-internal：朝目标点直线推进一个 dt（是否到达由 ProjectileSystem 判定——
     * 推进前距离 ≤ 本 tick 步长即命中，无需走满）。
     */
    public void advance(float dt, float targetX, float targetY) {
        float dx = targetX - posX;
        float dy = targetY - posY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float step = GameBalance.PROJECTILE_SPEED * dt;
        if (distance <= step || distance <= 0f) {
            posX = targetX;
            posY = targetY;
            return;
        }
        posX += dx / distance * step;
        posY += dy / distance * step;
    }

    /** 到目标的欧氏距离 */
    public float distanceTo(float targetX, float targetY) {
        float dx = targetX - posX;
        float dy = targetY - posY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
