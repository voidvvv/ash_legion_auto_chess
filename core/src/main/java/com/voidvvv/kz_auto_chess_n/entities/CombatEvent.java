package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.StatusType;

import java.util.Objects;

/**
 * 纯数据战斗事件（battle §二事件表 9 类；口径 #21）。
 *
 * <p>扁平结构 + 静态工厂：字段 nullable 语义由各工厂文档化；
 * {@code amount}/{@code amount2} 均为 float（口径 #7，显示层取整留 Phase 4）。
 * 实现 equals/hashCode 供确定性对拍（同 seed 事件流逐位比较，沿 WaveSpec 先例）。
 * 移动不产事件（口径 #20）；能量变化不入事件（常量 × 回能率可推导）。
 */
public final class CombatEvent {
    public enum Type {
        ATTACK_LAUNCHED, HIT, CAST, STATUS_APPLIED, HEALED, SHIELDED,
        UNIT_DIED, PROJECTILE_FIZZLED, BATTLE_ENDED
    }

    private final int tick;
    private final Type type;
    private final int sourceId;
    private final int targetId;
    private final float amount;
    private final float amount2;
    private final boolean crit;
    private final StatusType statusType;   // STATUS_APPLIED 专用，其余 null
    private final String skillId;          // HIT/CAST 专用，普攻 HIT 为 null
    private final BattleOutcome outcome;   // BATTLE_ENDED 专用，其余 null

    private CombatEvent(int tick, Type type, int sourceId, int targetId, float amount, float amount2,
                        boolean crit, StatusType statusType, String skillId, BattleOutcome outcome) {
        this.tick = tick;
        this.type = type;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.amount = amount;
        this.amount2 = amount2;
        this.crit = crit;
        this.statusType = statusType;
        this.skillId = skillId;
        this.outcome = outcome;
    }

    /** 普攻出手（近战即时与远程发射统一；amount 无语义 = 0） */
    public static CombatEvent attackLaunched(int tick, int sourceId, int targetId) {
        return new CombatEvent(tick, Type.ATTACK_LAUNCHED, sourceId, targetId, 0f, 0f, false, null, null, null);
    }

    /** 直伤命中：amount = 实际造成伤害（扣盾后扣血量）；skillId null = 普攻 */
    public static CombatEvent hit(int tick, int sourceId, int targetId, float amount, boolean crit, String skillId) {
        return new CombatEvent(tick, Type.HIT, sourceId, targetId, amount, 0f, crit, null, skillId, null);
    }

    /** 技能施放：targetId = 主目标（锁定目标或 caster 自身） */
    public static CombatEvent cast(int tick, int sourceId, int targetId, String skillId) {
        return new CombatEvent(tick, Type.CAST, sourceId, targetId, 0f, 0f, false, null, skillId, null);
    }

    /** 状态施加：amount = power、amount2 = duration（秒；SHIELD 走 SHIELDED 不发此事件） */
    public static CombatEvent statusApplied(int tick, int sourceId, int targetId,
                                            StatusType statusType, float amount, float amount2) {
        return new CombatEvent(tick, Type.STATUS_APPLIED, sourceId, targetId, amount, amount2,
                false, statusType, null, null);
    }

    /** 治疗：amount = 实际回复量（溢出作废后） */
    public static CombatEvent healed(int tick, int sourceId, int targetId, float amount) {
        return new CombatEvent(tick, Type.HEALED, sourceId, targetId, amount, 0f, false, null, null, null);
    }

    /** 护盾落地：amount = 吸收点数（开局盾与技能盾统一，防双事件——口径 #21） */
    public static CombatEvent shielded(int tick, int sourceId, int targetId, float amount) {
        return new CombatEvent(tick, Type.SHIELDED, sourceId, targetId, amount, 0f, false, null, null, null);
    }

    /** 单位死亡：sourceId = 亡者 id */
    public static CombatEvent unitDied(int tick, int unitId) {
        return new CombatEvent(tick, Type.UNIT_DIED, unitId, -1, 0f, 0f, false, null, null, null);
    }

    /** 在途弹消散（目标先一步被清扫） */
    public static CombatEvent projectileFizzled(int tick, int sourceId, int targetId) {
        return new CombatEvent(tick, Type.PROJECTILE_FIZZLED, sourceId, targetId, 0f, 0f, false, null, null, null);
    }

    /** 战斗终局 */
    public static CombatEvent battleEnded(int tick, BattleOutcome outcome) {
        return new CombatEvent(tick, Type.BATTLE_ENDED, -1, -1, 0f, 0f, false, null, null, outcome);
    }

    public int getTick() { return tick; }
    public Type getType() { return type; }
    public int getSourceId() { return sourceId; }
    public int getTargetId() { return targetId; }
    public float getAmount() { return amount; }
    public float getAmount2() { return amount2; }
    public boolean isCrit() { return crit; }
    /** @return STATUS_APPLIED 时非 null */
    public StatusType getStatusType() { return statusType; }
    /** @return HIT/CAST 时非 null（普攻 HIT 为 null） */
    public String getSkillId() { return skillId; }
    /** @return BATTLE_ENDED 时非 null */
    public BattleOutcome getOutcome() { return outcome; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CombatEvent)) {
            return false;
        }
        CombatEvent e = (CombatEvent) o;
        return tick == e.tick
                && type == e.type
                && sourceId == e.sourceId
                && targetId == e.targetId
                && Float.floatToIntBits(amount) == Float.floatToIntBits(e.amount)
                && Float.floatToIntBits(amount2) == Float.floatToIntBits(e.amount2)
                && crit == e.crit
                && statusType == e.statusType
                && Objects.equals(skillId, e.skillId)
                && outcome == e.outcome;
    }

    @Override
    public int hashCode() {
        int result = tick;
        result = 31 * result + type.hashCode();
        result = 31 * result + sourceId;
        result = 31 * result + targetId;
        result = 31 * result + Float.floatToIntBits(amount);
        result = 31 * result + Float.floatToIntBits(amount2);
        result = 31 * result + (crit ? 1 : 0);
        result = 31 * result + (statusType == null ? 0 : statusType.hashCode());
        result = 31 * result + (skillId == null ? 0 : skillId.hashCode());
        result = 31 * result + (outcome == null ? 0 : outcome.hashCode());
        return result;
    }

    /** 控制台一行式（BattleConsoleMain 事件流打印） */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("tick ").append(tick).append(" | ").append(type);
        if (sourceId > 0 || type == Type.UNIT_DIED) {
            sb.append(" | src ").append(sourceId);
        }
        if (targetId > 0) {
            sb.append(" -> tgt ").append(targetId);
        }
        if (amount != 0f) {
            sb.append(" | amount ").append(amount);
        }
        if (amount2 != 0f) {
            sb.append(" | amount2 ").append(amount2);
        }
        if (crit) {
            sb.append(" | CRIT");
        }
        if (statusType != null) {
            sb.append(" | status ").append(statusType);
        }
        if (skillId != null) {
            sb.append(" | skill ").append(skillId);
        }
        if (outcome != null) {
            sb.append(" | outcome ").append(outcome);
        }
        return sb.toString();
    }
}
