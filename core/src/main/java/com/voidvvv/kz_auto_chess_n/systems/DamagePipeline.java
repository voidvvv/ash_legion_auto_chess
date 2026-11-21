package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;

import java.util.Objects;

/**
 * 唯一伤害管线（battle §5.2；普攻与技能直伤共用）。结算序（每步事件/状态可观察）：
 * 伤害 = attackPower × multiplier × (1+收割增伤) × (crit ? 暴击倍率 : 1) × (1−免伤)
 *        × 100/(100 + 目标命中时有效护甲 × (1−穿甲))
 * → 先扣盾 → 扣 HP（溢出作废）→ Hit 事件
 * → 攻击者 +10×回能率 / 受击者 +5×回能率（口径 #5/#6；控制期冻结；跨百就地回调）
 * → basicAttack 时吸血（Q3）：实际扣血量 × lifesteal/100，cap maxHp，Healed 事件
 * → 普攻命中落地挂点（旧疤计数 + 附带状态，CP21/CP25）→ 余震反弹（甲后扣血 ×ratio 真伤回攻击者）。
 *
 * <p>受击窗口序 K9（v5 §4.1 缺口②）；DOT/真伤不经 applyDirectHit——
 * 免伤/反弹/旧疤对 DOT 与真伤全线豁免（天然成立）。
 * 护甲按<b>命中时</b>目标有效护甲结算（口径 #13：roll 在发射、公式在命中）；
 * 数值 float 全精度直存（口径 #7）。
 */
public final class DamagePipeline {
    private final CastTrigger castTrigger;

    public DamagePipeline(CastTrigger castTrigger) {
        this.castTrigger = Objects.requireNonNull(castTrigger, "castTrigger 不能为 null");
    }

    /** 直伤命中（普攻与技能同路；skillId null = 普攻）。窗口序 K9：
     *  收割(前) → 免伤(前,直伤专属) → 护甲(×(1−pen)) → 盾 → 血 → HIT → 回能 → 吸血
     *  → 旧疤计数(仅普攻) → 余震反弹 → 普攻附带。DOT/真伤不经此（豁免天然成立）。 */
    public void applyDirectHit(BattleState state, BattleUnit attacker, BattleUnit target,
                               float attackPower, float multiplier, boolean crit, float critBonusPp,
                               boolean basicAttack, String skillId, float armorPenRatio) {
        Objects.requireNonNull(target, "target 不能为 null");
        if (!target.isActable()) {
            return; // C7 ③ 单点兜底：不可被伤害（倒地/已清扫）
        }
        float critMultiplier = attacker == null
                ? StatKey.CRIT_DAMAGE.baseStatsDefault() / 100f
                : attacker.getEffective(StatKey.CRIT_DAMAGE) / 100f + critBonusPp / 100f;
        float executeAmp = executeAmpOf(attacker, target);                          // 收割：目标 <阈值 → +value%
        float reduction = mechanicValueOf(target, SynergyMechanic.DAMAGE_REDUCTION); // 免伤（仅直伤）
        float mitigated = attackPower * multiplier * (1f + executeAmp / 100f)
                * (crit ? critMultiplier : 1f)
                * (1f - reduction / 100f)
                * 100f / (100f + target.getEffective(StatKey.ARMOR) * (1f - armorPenRatio));
        float hpDamage = absorbShield(target, mitigated);
        target.modifyHp(-hpDamage);
        if (hpDamage > 0f && attacker != null) {
            target.setLastDamagerId(attacker.getId()); // 击杀归因（CP13，C5/C12）
        }
        state.record(CombatEvent.hit(state.getTick(),
                attacker == null ? -1 : attacker.getId(), target.getId(), hpDamage, crit, skillId));

        gainEnergy(state, attacker, GameBalance.ENERGY_PER_HIT);
        gainEnergy(state, target, GameBalance.ENERGY_PER_HIT_TAKEN);

        if (basicAttack && attacker != null) {
            applyLifesteal(state, attacker, hpDamage);
        }
        if (basicAttack && hpDamage > 0f) {
            onBasicAttackHit(state, attacker, target, attackPower, hpDamage); // 旧疤计数 + 普攻附带（CP21/CP25）
        }
        applyReflect(state, attacker, target, hpDamage); // 余震：甲后扣血 ×ratio 真伤回攻击者（可致死入级联）
    }

    /** DOT 真伤：无视护甲、先扣盾、可致死、无回能无吸血无事件（口径 #10）；归因仍记录施加者 */
    public void applyTrueDamage(BattleState state, BattleUnit source, BattleUnit target, float amount) {
        applyTrueDamage(state, source, target, amount, null);
    }

    /** 全参真伤：causeId = 亡语等真伤载荷的事件标注（null = DOT 静默）；hpDamage>0 记归因 + 发 HIT（紫飘字） */
    public void applyTrueDamage(BattleState state, BattleUnit source, BattleUnit target,
                                float amount, String causeId) {
        Objects.requireNonNull(target, "target 不能为 null");
        if (!target.isActable()) {
            return; // C7 ③ 单点兜底：不可被伤害（倒地/已清扫）
        }
        float hpDamage = absorbShield(target, amount);
        target.modifyHp(-hpDamage);
        if (hpDamage > 0f && source != null) {
            target.setLastDamagerId(source.getId()); // 亡语/DOT/反弹致死归因（CP13，K4）
        }
        if (hpDamage > 0f && causeId != null) {
            state.record(CombatEvent.hit(state.getTick(),
                    source == null ? -1 : source.getId(), target.getId(), hpDamage, false, causeId));
        }
    }

    /** 治疗：cap maxHp、溢出作废、Healed 事件（实际回复量；source 即受益者）；倒地不可被治疗（C7 ③） */
    public void applyHeal(BattleState state, BattleUnit target, float amount) {
        Objects.requireNonNull(target, "target 不能为 null");
        if (!target.isActable()) {
            return;
        }
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

    // —— v5 受击窗口机制（CP20/CP21，K9；全部经 mechanic 查询，无机制零开销） ——

    /** 收割增伤（刺客4/6 EXECUTE）：目标 hpRatio < value2% → +value%；严格小于阈值（K9） */
    private static float executeAmpOf(BattleUnit attacker, BattleUnit target) {
        EffectData execute = attacker == null ? null : attacker.getMechanic(SynergyMechanic.EXECUTE);
        if (execute == null || execute.getValue2() == null
                || target.hpRatio() >= execute.getValue2() / 100f) {
            return 0f;
        }
        return execute.getValue();
    }

    /** 机制值查询（无词/无单位 = 0；免伤/反弹共用） */
    private static float mechanicValueOf(BattleUnit unit, SynergyMechanic kind) {
        EffectData data = unit == null ? null : unit.getMechanic(kind);
        return data == null ? 0f : data.getValue();
    }

    /** 余震（巨人4/6 REFLECT）：甲后实际扣血 ×value% 真伤给攻击者——可致死、入级联、归因 = 反弹者 */
    private void applyReflect(BattleState state, BattleUnit attacker, BattleUnit target, float hpDamage) {
        float ratio = mechanicValueOf(target, SynergyMechanic.REFLECT);
        if (ratio <= 0f || hpDamage <= 0f || attacker == null || !attacker.isActable()) {
            return;
        }
        applyTrueDamage(state, target, attacker, hpDamage * ratio / 100f, "reflect");
    }

    /** 普攻命中落地挂点（仅普攻直伤 hpDamage>0 时）：旧疤计数（CP21）+ 附带状态（CP25 充实）。
     *  attackPower = 本发命中所用攻击力（近战 = 出手时有效攻；弹道 = 发射快照——由调用方传入） */
    private void onBasicAttackHit(BattleState state, BattleUnit attacker, BattleUnit target,
                                  float attackPower, float hpDamage) {
        if (hpDamage > 0f) {
            target.onScarHit(); // CP21：受击计数入层（无 SCAR 机制时空操作）
        }
        applyBasicAttackStatusOnPower(state, attacker, target, attackPower); // CP25 充实（批4）
    }

    /** 普攻附带状态（兽人灼烧/野兽围狩）：BURN 取 attackPower×value%、SLOW 取 value 百分点（CP25 充实） */
    void applyBasicAttackStatusOnPower(BattleState state, BattleUnit attacker, BattleUnit target,
                                       float attackPower) {
        // CP25 充实（批4）
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
