package com.voidvvv.kz_auto_chess_n.data;

/**
 * 可修改属性键白名单（data_schema §三 statKey，11 键；v5 §五.5 暴击属性化 +2）。
 *
 * <p>JSON 的 stat 名、状态 type、代码枚举三处共用本词表，杜绝字符串魔法。
 *
 * <p>百分比刻度约定（data_schema §三 V1.4）：百分比类 stat（lifesteal / skillPower / energyGainRate /
 * critChance / critDamage）以<b>百分点整数</b>存储（基准 0 / 0 / 100 / 20 / 150），结算处统一 ÷100
 * 换算（如 energyGainRate 115 → ×1.15、critChance 35 → 35%），保证 ADD/PCT 运算语义跨全部键一致、
 * 管线零特例。
 *
 * <p>{@link #baseStatsDefault()} 为 {@code null} 表示 baseStats 中必填；非 null 为缺省值。
 */
public enum StatKey implements Vocab {
    HP("hp", null),
    ATTACK("attack", null),
    ARMOR("armor", null),
    ATTACK_SPEED("attackSpeed", null),
    MOVE_SPEED("moveSpeed", null),
    RANGE("range", null),
    LIFESTEAL("lifesteal", 0),
    ENERGY_GAIN_RATE("energyGainRate", 100),
    SKILL_POWER("skillPower", 0),
    /** 暴击率，百分点（20 = 20%；§五.5 属性化迁移，D1 已裁） */
    CRIT_CHANCE("critChance", 20),
    /** 暴击伤害倍率，百分点刻度（150 = ×1.5） */
    CRIT_DAMAGE("critDamage", 150);

    private final String jsonName;
    /** baseStats 中的缺省值；null = 必填（data_schema §4.1） */
    private final Integer baseStatsDefault;

    StatKey(String jsonName, Integer baseStatsDefault) {
        this.jsonName = jsonName;
        this.baseStatsDefault = baseStatsDefault;
    }

    @Override
    public String jsonName() {
        return jsonName;
    }

    /** baseStats 缺省值；null 表示该键必填 */
    public Integer baseStatsDefault() {
        return baseStatsDefault;
    }

    /** 是否百分比类 stat（百分点整数刻度，结算 ÷100，battle_design §8.2；v5 §五.5 暴击两键同刻度） */
    public boolean isPercentScale() {
        return this == LIFESTEAL || this == ENERGY_GAIN_RATE || this == SKILL_POWER
                || this == CRIT_CHANCE || this == CRIT_DAMAGE;
    }
}
