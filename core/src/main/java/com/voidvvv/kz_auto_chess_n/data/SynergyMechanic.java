package com.voidvvv.kz_auto_chess_n.data;

/**
 * 羁绊机制词表（v5 §4.1 依赖清单；EffectData 第三通道，K1）。
 *  参数约定（value/value2/status/duration/trait 按词选择必填/可缺，加载期逐词校验）：
 *  BASIC_ATTACK_STATUS: status+value+duration（value=DOT 类为 atk 百分比、其余为 power 百分点）
 *  SCAR: value=每层承受普攻次数, value2=层数上限（每层幅度走 GameBalance 常量）
 *  DEATH_TAX: value=攻 ADD, value2=生命上限 ADD（继承立即回复 value2）
 *  DOUBLE_DEATHRATTLE / KNOCKBACK_IMMUNE / SHIELD_WALL / KILL_ENERGY_REFUND: 无参
 *  DEATH_AUDIT: value=生命比例阈值（百分点，25 = <25%）
 *  ENEMY_DEATH_SURGE: value=攻速增益百分点, duration=秒
 *  REFLECT: value=反弹百分比（甲后伤害 ×value%）
 *  DAMAGE_REDUCTION: value=免伤百分比
 *  WARCRY_TAUNT: value=开战延迟秒, value2=嘲讽持续秒（半径 GameBalance 常量）
 *  SPELL_PEN: value=无视护甲百分比（仅技能直伤）
 *  OPENING_ENERGY / AFTER_CAST_ENERGY: value=能量值（走 gainEnergy 加权）
 *  EXECUTE: value=增伤百分比, value2=生命比例阈值（百分点，35 = <35%）
 *  CRIT_RHYTHM: value=第 N 刀必暴, value2=必暴刀暴伤额外百分点
 */
public enum SynergyMechanic implements Vocab {
    BASIC_ATTACK_STATUS("BASIC_ATTACK_STATUS"),
    SCAR("SCAR"),
    DEATH_TAX("DEATH_TAX"),
    DOUBLE_DEATHRATTLE("DOUBLE_DEATHRATTLE"),
    DEATH_AUDIT("DEATH_AUDIT"),
    ENEMY_DEATH_SURGE("ENEMY_DEATH_SURGE"),
    REFLECT("REFLECT"),
    DAMAGE_REDUCTION("DAMAGE_REDUCTION"),
    KNOCKBACK_IMMUNE("KNOCKBACK_IMMUNE"),
    WARCRY_TAUNT("WARCRY_TAUNT"),
    SHIELD_WALL("SHIELD_WALL"),
    SPELL_PEN("SPELL_PEN"),
    OPENING_ENERGY("OPENING_ENERGY"),
    AFTER_CAST_ENERGY("AFTER_CAST_ENERGY"),
    EXECUTE("EXECUTE"),
    KILL_ENERGY_REFUND("KILL_ENERGY_REFUND"),
    CRIT_RHYTHM("CRIT_RHYTHM");

    private final String jsonName;

    SynergyMechanic(String jsonName) { this.jsonName = jsonName; }

    @Override
    public String jsonName() { return jsonName; }
}
