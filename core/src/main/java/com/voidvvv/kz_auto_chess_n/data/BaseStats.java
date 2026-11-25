package com.voidvvv.kz_auto_chess_n.data;

/**
 * 棋子基础属性（data_schema §4.1 baseStats 字段终版；v5 §五.5 暴击属性化 +2 键）。
 *
 * <p>必填六键：hp / attack / armor / attackSpeed / range / moveSpeed；
 * 可选五键（百分点整数刻度，缺省见 {@link StatKey#baseStatsDefault()}）：
 * lifesteal=0、energyGainRate=100、skillPower=0、critChance=20、critDamage=150。
 *
 * <p>近似不可变：private final 无 setter，JsonLoader 是唯一写入点（data_schema §二.4）。
 */
public final class BaseStats {
    private final int hp;
    private final int attack;
    private final int armor;
    private final float attackSpeed;
    private final int range;
    private final float moveSpeed;
    private final int lifesteal;
    private final int energyGainRate;
    private final int skillPower;
    private final int critChance;
    private final int critDamage;

    public BaseStats(int hp, int attack, int armor, float attackSpeed, int range, float moveSpeed,
                     int lifesteal, int energyGainRate, int skillPower,
                     int critChance, int critDamage) {
        this.hp = hp;
        this.attack = attack;
        this.armor = armor;
        this.attackSpeed = attackSpeed;
        this.range = range;
        this.moveSpeed = moveSpeed;
        this.lifesteal = lifesteal;
        this.energyGainRate = energyGainRate;
        this.skillPower = skillPower;
        this.critChance = critChance;
        this.critDamage = critDamage;
    }

    public int getHp() { return hp; }
    public int getAttack() { return attack; }
    public int getArmor() { return armor; }
    public float getAttackSpeed() { return attackSpeed; }
    public int getRange() { return range; }
    public float getMoveSpeed() { return moveSpeed; }
    /** 吸血，百分点（20 = 20%，结算 ÷100） */
    public int getLifesteal() { return lifesteal; }
    /** 回能速率，百分点（100 = ×1.0，115 = ×1.15） */
    public int getEnergyGainRate() { return energyGainRate; }
    /** 技能数值幅度加成，百分点（作用于 DAMAGE/HEAL/SHIELD，与星级缩放叠乘） */
    public int getSkillPower() { return skillPower; }
    /** 暴击率，百分点（20 = 20%；§五.5 属性化迁移） */
    public int getCritChance() { return critChance; }
    /** 暴击伤害倍率，百分点（150 = ×1.5） */
    public int getCritDamage() { return critDamage; }
}
