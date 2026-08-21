package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;

import java.util.Objects;

/**
 * 唯一伤害管线（battle §5.2；普攻与技能直伤共用）。结算序（每步事件/状态可观察）：
 * 伤害 = attackPower × multiplier × (crit ? 1.5 : 1) × 100/(100 + 目标命中时有效护甲)
 * → 先扣盾 → 扣 HP（溢出作废）→ Hit 事件
 * → 攻击者 +10×回能率 / 受击者 +5×回能率（口径 #5/#6；控制期冻结；跨百就地回调）
 * → basicAttack 时吸血（Q3）：实际扣血量 × lifesteal/100，cap maxHp，Healed 事件。
 *
 * <p>护甲按<b>命中时</b>目标有效护甲结算（口径 #13：roll 在发射、公式在命中）；
 * 数值 float 全精度直存（口径 #7）。
 */
public final class DamagePipeline {
    private final CastTrigger castTrigger;

    public DamagePipeline(CastTrigger castTrigger) {
        this.castTrigger = Objects.requireNonNull(castTrigger, "castTrigger 不能为 null");
    }

    /** 直伤命中（普攻与技能同路；skillId null = 普攻） */
    public void applyDirectHit(BattleState state, BattleUnit attacker, BattleUnit target,
                               float attackPower, float multiplier, boolean crit,
                               boolean basicAttack, String skillId) {
        Objects.requireNonNull(target, "target 不能为 null");
        float mitigated = attackPower * multiplier
                * (crit ? GameBalance.CRIT_MULTIPLIER : 1f)
                * 100f / (100f + target.getEffective(StatKey.ARMOR));
        float hpDamage = absorbShield(target, mitigated);
        target.modifyHp(-hpDamage);
        state.record(CombatEvent.hit(state.getTick(),
                attacker == null ? -1 : attacker.getId(), target.getId(), hpDamage, crit, skillId));

        gainEnergy(state, attacker, GameBalance.ENERGY_PER_HIT);
        gainEnergy(state, target, GameBalance.ENERGY_PER_HIT_TAKEN);

        if (basicAttack && attacker != null) {
            applyLifesteal(state, attacker, hpDamage);
        }
    }

    /** DOT 真伤：无视护甲、先扣盾、可致死、无回能无吸血无事件（口径 #10） */
    public void applyTrueDamage(BattleState state, BattleUnit source, BattleUnit target, float amount) {
        float hpDamage = absorbShield(target, amount);
        target.modifyHp(-hpDamage);
    }

    /** 治疗：cap maxHp、溢出作废、Healed 事件（实际回复量；source 即受益者） */
    public void applyHeal(BattleState state, BattleUnit target, float amount) {
        float before = target.getCurrentHp();
        target.modifyHp(amount);
        float healed = target.getCurrentHp() - before;
        if (healed > 0f) {
            state.record(CombatEvent.healed(state.getTick(), target.getId(), target.getId(), healed));
        }
    }

    /** 回能：乘获得者 energyGainRate/100、控制期冻结、封顶 100、跨百回调就地施放（口径 #5/#19） */
    public void gainEnergy(BattleState state, BattleUnit unit, float baseAmount) {
        if (unit == null || unit.isCleaned() || unit.hasControl()) {
            return; // 控制期完全冻结；已清扫者无观察效应
        }
        float gain = baseAmount * unit.getEffective(StatKey.ENERGY_GAIN_RATE) / 100f;
        float before = unit.getEnergy();
        unit.setEnergy(before + gain);
        if (before < GameBalance.ENERGY_MAX && unit.getEnergy() >= GameBalance.ENERGY_MAX) {
            castTrigger.tryCast(state, unit); // 跨百就地施放（深度保护归实现方）
        }
    }

    /** 吸血（Q3 裁决 A）：仅普攻直伤命中触发，回复 = 护甲后实际扣血 × lifesteal/100 */
    private void applyLifesteal(BattleState state, BattleUnit attacker, float hpDamage) {
        float lifesteal = attacker.getEffective(StatKey.LIFESTEAL) / 100f;
        if (lifesteal <= 0f || hpDamage <= 0f) {
            return;
        }
        float before = attacker.getCurrentHp();
        attacker.modifyHp(hpDamage * lifesteal);
        float healed = attacker.getCurrentHp() - before;
        if (healed > 0f) {
            state.record(CombatEvent.healed(state.getTick(), attacker.getId(), attacker.getId(), healed));
        }
    }

    /** 先扣盾：返回穿透到 HP 的伤害量；盾耗尽移除并打脏标记（口径 #9） */
    private static float absorbShield(BattleUnit target, float amount) {
        float remaining = amount;
        for (ActiveStatus status : new java.util.ArrayList<ActiveStatus>(target.getStatuses())) {
            if (status.getType() != StatusType.SHIELD || remaining <= 0f) {
                continue;
            }
            float absorbed = Math.min(status.getPower(), remaining);
            status.setPower(status.getPower() - absorbed);
            remaining -= absorbed;
            if (status.getPower() <= 0f) {
                target.removeStatus(status); // 自动打脏标记
            }
        }
        return remaining;
    }
}
