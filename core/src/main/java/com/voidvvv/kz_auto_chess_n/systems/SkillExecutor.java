package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffect;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Projectile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 技能执行器（battle §六三步执行模型）：
 * ① shape 解析目标 → ② 载体投送 → ③ 逐效果应用。
 *
 * <p>载荷冻结（口径 #13）：施放时冻结攻击力快照 + 公共缩放因子
 * （= skillStarScale(star) × (1 + skillPower 快照/100)），护甲公式按命中时目标有效护甲。
 * 延后施放（口径 #18）：主目标无效/LOWEST_ALLY 全满 → 返回 false，能量保留由调用方语义保证。
 * LINE 弹道本期不做（Q2 裁决）：遇 LINE 技能抛 UnsupportedOperationException。
 */
public final class SkillExecutor {
    private final DamagePipeline damagePipeline;
    private final StatusSystem statusSystem;

    public SkillExecutor(DamagePipeline damagePipeline, StatusSystem statusSystem) {
        this.damagePipeline = Objects.requireNonNull(damagePipeline, "damagePipeline 不能为 null");
        this.statusSystem = Objects.requireNonNull(statusSystem, "statusSystem 不能为 null");
    }

    /**
     * 施放（能量满或就地触发时由行动链/回能回调调用）。
     *
     * @return true = 施放成功（能量已清零、Cast 事件已发）；false = 延后（能量不动）
     */
    public boolean cast(BattleState state, BattleUnit caster) {
        Objects.requireNonNull(state, "state 不能为 null");
        Objects.requireNonNull(caster, "caster 不能为 null");
        if (caster.hasControl()) {
            return false; // 控制期不施放
        }
        SkillData skill = caster.getSkill();
        if (skill.getDelivery() == Delivery.LINE) {
            throw new UnsupportedOperationException("LINE 弹道本期不做（Q2 裁决）：skill=" + skill.getId());
        }
        if (isAllyShape(skill.getShape()) && skill.getDelivery() == Delivery.HOMING) {
            throw new IllegalStateException("增益形状（ally）不支持 HOMING 载体：skill=" + skill.getId());
        }

        BattleUnit mainTarget = resolveMainTarget(state, caster, skill);
        if (mainTarget == null) {
            return false; // 延后（口径 #18）
        }

        float attackSnapshot = caster.getEffective(StatKey.ATTACK);
        float scaleFactor = GameBalance.skillStarScale(caster.getStar())
                * (1f + caster.getEffective(StatKey.SKILL_POWER) / 100f);

        // Cast 事件先于其效果事件（施放是效果的可观察起点）
        state.record(CombatEvent.cast(state.getTick(), caster.getId(), mainTarget.getId(), skill.getId()));
        caster.setEnergy(0f);

        if (skill.getDelivery() == Delivery.HOMING) {
            state.spawnProjectile(new Projectile(caster.getId(), mainTarget.getId(),
                    caster.getGridX() + 0.5f, caster.getGridY() + 0.5f,
                    attackSnapshot, false, skill, scaleFactor));
        } else {
            List<BattleUnit> targets = resolveTargets(state, caster, skill, mainTarget);
            applyEffects(state, caster, skill, targets, attackSnapshot, scaleFactor);
        }
        return true;
    }

    /** HOMING 技能弹到达后的落点应用（AOE 以命中时刻目标所在格展开） */
    public void applyAtImpact(BattleState state, BattleUnit caster, BattleUnit impactTarget,
                              SkillData skill, float attackSnapshot, float scaleFactor) {
        List<BattleUnit> targets = resolveTargets(state, caster, skill, impactTarget);
        applyEffects(state, caster, skill, targets, attackSnapshot, scaleFactor);
    }

    // —— ① shape 解析 ——

    /** 主目标：SINGLE/AOE = 锁定目标；SELF/LOWEST_ALLY/ALL_ALLIES = caster；ALL_ENEMIES = 锁定或首敌 */
    private static BattleUnit resolveMainTarget(BattleState state, BattleUnit caster, SkillData skill) {
        switch (skill.getShape()) {
            case SINGLE_TARGET:
            case AOE_1:
            case AOE_2:
                return validLockedTarget(state, caster);
            case SELF:
            case LOWEST_ALLY:
            case ALL_ALLIES:
                if (skill.getShape() == SkillShape.LOWEST_ALLY) {
                    return lowestAlly(state, caster);
                }
                return caster;
            case ALL_ENEMIES: {
                BattleUnit locked = validLockedTarget(state, caster);
                if (locked != null) {
                    return locked;
                }
                List<BattleUnit> enemies = aliveEnemies(state, caster);
                return enemies.isEmpty() ? null : enemies.get(0);
            }
            default:
                throw new IllegalArgumentException("未知 SkillShape: " + skill.getShape());
        }
    }

    /** 目标集合：AOE 按落点几何；其余按主目标展开 */
    private static List<BattleUnit> resolveTargets(BattleState state, BattleUnit caster,
                                                   SkillData skill, BattleUnit mainTarget) {
        switch (skill.getShape()) {
            case SINGLE_TARGET:
                return singleList(mainTarget);
            case SELF:
            case LOWEST_ALLY:
                return singleList(mainTarget);
            case ALL_ALLIES: {
                List<BattleUnit> allies = new ArrayList<BattleUnit>();
                for (BattleUnit unit : state.getUnits()) {
                    if (unit.getSide() == caster.getSide() && unit.isAlive()) {
                        allies.add(unit);
                    }
                }
                return allies;
            }
            case ALL_ENEMIES:
                return aliveEnemies(state, caster);
            case AOE_1:
                return enemiesWithin(state, caster, mainTarget.getGridX(), mainTarget.getGridY(), 1);
            case AOE_2:
                return enemiesWithin(state, caster, mainTarget.getGridX(), mainTarget.getGridY(), 2);
            default:
                throw new IllegalArgumentException("未知 SkillShape: " + skill.getShape());
        }
    }

    private static boolean isAllyShape(SkillShape shape) {
        return shape == SkillShape.SELF || shape == SkillShape.LOWEST_ALLY
                || shape == SkillShape.ALL_ALLIES;
    }

    private static BattleUnit validLockedTarget(BattleState state, BattleUnit caster) {
        if (caster.getTargetId() < 0) {
            return null;
        }
        BattleUnit target = state.getUnitById(caster.getTargetId());
        if (target == null || target.isCleaned() || target.getSide() == caster.getSide()) {
            return null;
        }
        return target;
    }

    /** HP% 最低友军（含自己）；全满返回 null（延后）；平局取 id 小者 */
    private static BattleUnit lowestAlly(BattleState state, BattleUnit caster) {
        BattleUnit best = null;
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() != caster.getSide() || unit.isCleaned()) {
                continue;
            }
            if (unit.hpRatio() >= 1f) {
                continue; // 满血不入选
            }
            if (best == null || unit.hpRatio() < best.hpRatio()
                    || (unit.hpRatio() == best.hpRatio() && unit.getId() < best.getId())) {
                best = unit;
            }
        }
        return best;
    }

    private static List<BattleUnit> aliveEnemies(BattleState state, BattleUnit caster) {
        List<BattleUnit> enemies = new ArrayList<BattleUnit>();
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() != caster.getSide() && unit.isAlive()) {
                enemies.add(unit);
            }
        }
        return enemies;
    }

    /** AOE_1 十字 / AOE_2 菱形（曼哈顿 ≤ radius，含边界格），命中区域内全部存活敌方 */
    private static List<BattleUnit> enemiesWithin(BattleState state, BattleUnit caster,
                                                  int centerX, int centerY, int radius) {
        List<BattleUnit> hits = new ArrayList<BattleUnit>();
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() == caster.getSide() || unit.isCleaned()) {
                continue;
            }
            int distance = Math.abs(unit.getGridX() - centerX) + Math.abs(unit.getGridY() - centerY);
            if (distance <= radius) {
                hits.add(unit);
            }
        }
        return hits;
    }

    // —— ③ 逐效果应用 ——

    private void applyEffects(BattleState state, BattleUnit caster, SkillData skill,
                              List<BattleUnit> targets, float attackSnapshot, float scaleFactor) {
        for (SkillEffect effect : skill.getEffects()) {
            for (BattleUnit target : targets) {
                applyOneEffect(state, caster, skill, effect, target, attackSnapshot, scaleFactor);
            }
        }
    }

    private void applyOneEffect(BattleState state, BattleUnit caster, SkillData skill,
                                SkillEffect effect, BattleUnit target,
                                float attackSnapshot, float scaleFactor) {
        float value = effect.getValue() == null ? 0f : effect.getValue();
        switch (effect.getEffect()) {
            case DAMAGE:
                damagePipeline.applyDirectHit(state, caster, target, attackSnapshot,
                        value * scaleFactor, false, false, skill.getId());
                break;
            case HEAL:
                damagePipeline.applyHeal(state, target,
                        target.getEffective(StatKey.HP) * value * scaleFactor);
                break;
            case SHIELD:
                statusSystem.apply(state, target, StatusType.SHIELD,
                        target.getEffective(StatKey.HP) * value * scaleFactor,
                        Float.POSITIVE_INFINITY, caster.getId());
                break;
            case APPLY_STATUS: {
                StatusType status = effect.getStatus();
                float power = isDotType(status) ? attackSnapshot * value : value;
                float duration = effect.getDuration() == null ? 0f : effect.getDuration();
                statusSystem.apply(state, target, status, power, duration, caster.getId());
                break;
            }
            default:
                throw new IllegalArgumentException("未知 SkillEffectType: " + effect.getEffect());
        }
    }

    private static boolean isDotType(StatusType status) {
        return status == StatusType.BLEED || status == StatusType.POISON;
    }

    private static List<BattleUnit> singleList(BattleUnit unit) {
        List<BattleUnit> list = new ArrayList<BattleUnit>(1);
        list.add(unit);
        return list;
    }
}
