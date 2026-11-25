package com.voidvvv.kz_auto_chess_n.data;

import java.util.Objects;

/** 个人亡语参数（units.json "deathrattle"；不可变）。全载荷走真伤（阵营轴：亡者的攻击无视生者的盔甲）。 */
public final class DeathrattleSpec {

    /** 亡语直伤事件的合成 skillId 前缀（HIT.skillId 可追溯标注；渲染 FX 键 fx_{skillId}_burst 派生源） */
    public static final String CAUSE_SKILL_ID_PREFIX = "deathrattle_";
    /** 亡语弹（DEATH_VOLLEY）命中事件的合成 skillId（ProjectileSystem 真伤路由 C6） */
    public static final String VOLLEY_SKILL_ID = CAUSE_SKILL_ID_PREFIX + "projectile";

    /** 亡语直伤合成 skillId：deathrattle_{unitId}（DeathrattleExecutor/HIT 事件/FX 键三方单一事实源） */
    public static String causeSkillId(String unitId) {
        return CAUSE_SKILL_ID_PREFIX + unitId;
    }

    private final DeathrattleKind kind;
    private final float ratio;        // AREA/KILLER/VOLLEY：攻击快照 ×ratio
    private final int radius;         // AREA：曼哈顿半径
    private final StatusType statusType;      // AREA 可选附带（SLOW）
    private final float statusPower;  // 百分点（SLOW 30 = −30% 移速）
    private final float statusDuration;
    private final float regenRatio;   // ALLY_REGEN：maxHp 比例/秒（0.02 = 2%/s）
    private final float duration;     // ALLY_REGEN 秒

    public DeathrattleSpec(DeathrattleKind kind, float ratio, int radius,
                           StatusType statusType, float statusPower, float statusDuration,
                           float regenRatio, float duration) {
        this.kind = Objects.requireNonNull(kind, "kind 不能为 null");
        this.ratio = ratio; this.radius = radius;
        this.statusType = statusType; this.statusPower = statusPower; this.statusDuration = statusDuration;
        this.regenRatio = regenRatio; this.duration = duration;
    }

    public DeathrattleKind getKind() { return kind; }
    public float getRatio() { return ratio; }
    public int getRadius() { return radius; }
    public StatusType getStatusType() { return statusType; }
    public float getStatusPower() { return statusPower; }
    public float getStatusDuration() { return statusDuration; }
    public float getRegenRatio() { return regenRatio; }
    public float getDuration() { return duration; }
}
