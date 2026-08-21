package com.voidvvv.kz_auto_chess_n.data;

/**
 * 可修改属性键白名单（data_schema §三 statKey，9 键）。
 *
 * <p>JSON 的 stat 名、状态 type、代码枚举三处共用本词表，杜绝字符串魔法。
 *
 * <p>百分比刻度约定（data_schema §三 V1.4）：百分比类 stat（lifesteal / skillPower / energyGainRate）
 * 以<b>百分点整数</b>存储（基准 0 / 0 / 100），结算处统一 ÷100 换算（如 energyGainRate 115 → ×1.15），
 * 保证 ADD/PCT 运算语义跨全部 9 键一致、管线零特例。
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
    SKILL_POWER("skillPower", 0);

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

    /** 是否百分比类 stat（百分点整数刻度，结算 ÷100，battle_design §8.2） */
    public boolean isPercentScale() {
        return this == LIFESTEAL || this == ENERGY_GAIN_RATE || this == SKILL_POWER;
    }
}
