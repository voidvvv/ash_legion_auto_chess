package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleStats;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierBlock;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 两级属性管线（battle §八；无状态纯函数）。
 *
 * <p>第一级基准（battle §8.1）：raw = 模板值 × starStatMultiplier × scale，
 * 基准 = (raw + Σ sources.ADD) × (1 + Σ sources.PCT/100)——先加后乘，顺序写死。
 * 修正源列表（Q4）：本期唯一源 = 羁绊快照；Phase 5 装备源追加即插，结算器零改动。
 *
 * <p>第二级有效（battle §8.2）：(基准 + Σ状态ADD) × (1 + Σ状态PCT/100)，
 * 由 BattleUnit 的脏标记缓存按需重算。
 */
public final class StatPipeline {
    private StatPipeline() {
    }

    /** 第一级：模板 × 星级 × scale，套修正源列表（先加后乘） */
    public static BattleStats deriveBaseline(UnitData template, int star, float scale,
                                             List<StatModifierSource> sources) {
        Objects.requireNonNull(template, "template 不能为 null");
        Objects.requireNonNull(sources, "sources 不能为 null");
        float starMult = GameBalance.starStatMultiplier(template.getUpgradeMultiplier(), star);
        BaseStats base = template.getBaseStats();

        StatModifierBlock merged = StatModifierBlock.empty();
        for (StatModifierSource source : sources) {
            merged = merged.plus(source.modifiers());
        }

        float[] values = new float[StatKey.values().length];
        for (StatKey key : StatKey.values()) {
            float raw = templateValue(base, key) * starMult * scale;
            values[key.ordinal()] = combine(raw, merged.addOf(key), merged.pctOf(key));
        }
        return build(values);
    }

    /** 第二级：基准 + 状态修正块（先加后乘） */
    public static BattleStats deriveEffective(BattleStats base, StatModifierBlock statusModifiers) {
        Objects.requireNonNull(base, "base 不能为 null");
        Objects.requireNonNull(statusModifiers, "statusModifiers 不能为 null");
        float[] values = new float[StatKey.values().length];
        for (StatKey key : StatKey.values()) {
            values[key.ordinal()] = combine(base.get(key), statusModifiers.addOf(key), statusModifiers.pctOf(key));
        }
        return build(values);
    }

    /**
     * StatusType → StatKey 修正映射（口径 #8，写死于此单点）：
     * ATK_UP→attack·PCT(+v)、ATK_DOWN→attack·PCT(−v)、ASPD_UP→attackSpeed·PCT(+v)、
     * SLOW→moveSpeed·PCT(−v)（v = 百分点）；STUN/BLEED/POISON/REGEN/SHIELD 非属性类。
     * 同 type 多条时 power 取大（不叠加，口径 #11 的防御性对称）。
     */
    public static StatModifierBlock statusModifiers(List<ActiveStatus> statuses) {
        Objects.requireNonNull(statuses, "statuses 不能为 null");
        Map<StatusType, Float> maxPower = new HashMap<StatusType, Float>();
        for (ActiveStatus status : statuses) {
            if (isAttributeType(status.getType())) {
                Float current = maxPower.get(status.getType());
                if (current == null || status.getPower() > current) {
                    maxPower.put(status.getType(), status.getPower());
                }
            }
        }
        StatModifierBlock block = StatModifierBlock.empty();
        for (Map.Entry<StatusType, Float> e : maxPower.entrySet()) {
            block = block.plus(attributeMapping(e.getKey(), e.getValue()));
        }
        return block;
    }

    // —— 内部 ——

    private static boolean isAttributeType(StatusType type) {
        return type == StatusType.ATK_UP || type == StatusType.ATK_DOWN
                || type == StatusType.ASPD_UP || type == StatusType.SLOW;
    }

    private static StatModifierBlock attributeMapping(StatusType type, float power) {
        switch (type) {
            case ATK_UP:
                return StatModifierBlock.of(StatKey.ATTACK, EffectOp.PCT, power);
            case ATK_DOWN:
                return StatModifierBlock.of(StatKey.ATTACK, EffectOp.PCT, -power);
            case ASPD_UP:
                return StatModifierBlock.of(StatKey.ATTACK_SPEED, EffectOp.PCT, power);
            case SLOW:
                return StatModifierBlock.of(StatKey.MOVE_SPEED, EffectOp.PCT, -power);
            default:
                return StatModifierBlock.empty();
        }
    }

    /** 先加后乘（PCT 为百分点，÷100） */
    private static float combine(float raw, float add, float pct) {
        return (raw + add) * (1f + pct / 100f);
    }

    private static BattleStats build(float[] byOrdinal) {
        return new BattleStats(
                byOrdinal[StatKey.HP.ordinal()], byOrdinal[StatKey.ATTACK.ordinal()],
                byOrdinal[StatKey.ARMOR.ordinal()], byOrdinal[StatKey.ATTACK_SPEED.ordinal()],
                byOrdinal[StatKey.RANGE.ordinal()], byOrdinal[StatKey.MOVE_SPEED.ordinal()],
                byOrdinal[StatKey.LIFESTEAL.ordinal()], byOrdinal[StatKey.ENERGY_GAIN_RATE.ordinal()],
                byOrdinal[StatKey.SKILL_POWER.ordinal()]);
    }

    private static float templateValue(BaseStats base, StatKey key) {
        switch (key) {
            case HP: return base.getHp();
            case ATTACK: return base.getAttack();
            case ARMOR: return base.getArmor();
            case ATTACK_SPEED: return base.getAttackSpeed();
            case MOVE_SPEED: return base.getMoveSpeed();
            case RANGE: return base.getRange();
            case LIFESTEAL: return base.getLifesteal();
            case ENERGY_GAIN_RATE: return base.getEnergyGainRate();
            case SKILL_POWER: return base.getSkillPower();
            default:
                throw new IllegalArgumentException("未知 StatKey: " + key);
        }
    }
}
