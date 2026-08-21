package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.StatKey;

import java.util.Objects;

/**
 * 不可变 9 键属性块（battle §八两级管线的产物：第一级基准 / 第二级有效）。
 *
 * <p>float 全精度直存（口径 #7：无中间取整，显示层取整留 Phase 4）。
 * 百分比类键（lifesteal / energyGainRate / skillPower）仍以百分点刻度存储，
 * 结算处统一 ÷100（StatKey.isPercentScale 约定）。
 */
public final class BattleStats {
    private final float hp;
    private final float attack;
    private final float armor;
    private final float attackSpeed;
    private final float range;
    private final float moveSpeed;
    private final float lifesteal;
    private final float energyGainRate;
    private final float skillPower;

    public BattleStats(float hp, float attack, float armor, float attackSpeed, float range, float moveSpeed,
                       float lifesteal, float energyGainRate, float skillPower) {
        this.hp = hp;
        this.attack = attack;
        this.armor = armor;
        this.attackSpeed = attackSpeed;
        this.range = range;
        this.moveSpeed = moveSpeed;
        this.lifesteal = lifesteal;
        this.energyGainRate = energyGainRate;
        this.skillPower = skillPower;
    }

    /** 按词表键取值（9 键全覆盖，互不串键） */
    public float get(StatKey key) {
        switch (key) {
            case HP: return hp;
            case ATTACK: return attack;
            case ARMOR: return armor;
            case ATTACK_SPEED: return attackSpeed;
            case MOVE_SPEED: return moveSpeed;
            case RANGE: return range;
            case LIFESTEAL: return lifesteal;
            case ENERGY_GAIN_RATE: return energyGainRate;
            case SKILL_POWER: return skillPower;
            default:
                throw new IllegalArgumentException("未知 StatKey: " + key);
        }
    }

    public float getHp() { return hp; }
    public float getAttack() { return attack; }
    public float getArmor() { return armor; }
    public float getAttackSpeed() { return attackSpeed; }
    public float getRange() { return range; }
    public float getMoveSpeed() { return moveSpeed; }
    /** 吸血，百分点（20 = 20%，结算 ÷100） */
    public float getLifesteal() { return lifesteal; }
    /** 回能速率，百分点（100 = ×1.0） */
    public float getEnergyGainRate() { return energyGainRate; }
    /** 技能数值幅度加成，百分点 */
    public float getSkillPower() { return skillPower; }

    /** 值语义判等（测试断言"empty 恒等"等场景用；float 按位比较） */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BattleStats)) {
            return false;
        }
        BattleStats s = (BattleStats) o;
        return Float.floatToIntBits(hp) == Float.floatToIntBits(s.hp)
                && Float.floatToIntBits(attack) == Float.floatToIntBits(s.attack)
                && Float.floatToIntBits(armor) == Float.floatToIntBits(s.armor)
                && Float.floatToIntBits(attackSpeed) == Float.floatToIntBits(s.attackSpeed)
                && Float.floatToIntBits(range) == Float.floatToIntBits(s.range)
                && Float.floatToIntBits(moveSpeed) == Float.floatToIntBits(s.moveSpeed)
                && Float.floatToIntBits(lifesteal) == Float.floatToIntBits(s.lifesteal)
                && Float.floatToIntBits(energyGainRate) == Float.floatToIntBits(s.energyGainRate)
                && Float.floatToIntBits(skillPower) == Float.floatToIntBits(s.skillPower);
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(hp);
        result = 31 * result + Float.floatToIntBits(attack);
        result = 31 * result + Float.floatToIntBits(armor);
        result = 31 * result + Float.floatToIntBits(attackSpeed);
        result = 31 * result + Float.floatToIntBits(range);
        result = 31 * result + Float.floatToIntBits(moveSpeed);
        result = 31 * result + Float.floatToIntBits(lifesteal);
        result = 31 * result + Float.floatToIntBits(energyGainRate);
        result = 31 * result + Float.floatToIntBits(skillPower);
        return result;
    }

    @Override
    public String toString() {
        return "BattleStats{hp=" + hp + ", attack=" + attack + ", armor=" + armor
                + ", aspd=" + attackSpeed + ", range=" + range + ", ms=" + moveSpeed
                + ", ls=" + lifesteal + ", egr=" + energyGainRate + ", sp=" + skillPower + "}";
    }
}
