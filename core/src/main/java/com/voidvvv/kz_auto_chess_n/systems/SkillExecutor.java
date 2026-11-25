package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.CastCondition;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffect;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
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
        if (!castConditionMet(skill, mainTarget)) {
            return false; // 条件不满足 → 延后（依账除名「持能等待」）
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
        EffectData afterCast = caster.getMechanic(SynergyMechanic.AFTER_CAST_ENERGY);
        if (afterCast != null) {
            damagePipeline.gainEnergy(state, caster, afterCast.getValue()); // 群星响应：施法后 +value（CP22）
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
        if (target == null || !target.isActable() || target.getSide() == caster.getSide()) {
            return null; // 过滤点四（C7 ③）
        }
        return target;
    }

    /** HP% 最低友军（含自己）；全满返回 null（延后）；平局取 id 小者（倒地者不可疗——C7 ③） */
    private static BattleUnit lowestAlly(BattleState state, BattleUnit caster) {
        BattleUnit best = null;
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() != caster.getSide() || !unit.isActable()) {
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
            if (unit.getSide() != caster.getSide() && unit.isActable()) { // 过滤点四（C7 ③）
                enemies.add(unit);
            }
        }
        return enemies;
    }

    /** AOE_1 十字 / AOE_2 菱形（曼哈顿 ≤ radius，含边界格），命中区域内全部可行动存活敌方 */
    private static List<BattleUnit> enemiesWithin(BattleState state, BattleUnit caster,
                                                  int centerX, int centerY, int radius) {
        List<BattleUnit> hits = new ArrayList<BattleUnit>();
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() == caster.getSide() || !unit.isActable()) { // 过滤点四（C7 ③）
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
            case DAMAGE: {
                // 穿甲（法师(4)/(6) SPELL_PEN）：技能直伤无视目标护甲 value%（CP20 遗留 → 批4 接线，K9）
                float armorPenRatio = mechanicRatioOf(caster, SynergyMechanic.SPELL_PEN);
                damagePipeline.applyDirectHit(state, caster, target, attackSnapshot,
                        value * scaleFactor, false, 0f, false, skill.getId(), armorPenRatio);
                break;
            }
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
                float power = StatusSystem.isDotType(status) ? attackSnapshot * value : value;
                float duration = effect.getDuration() == null ? 0f : effect.getDuration();
                statusSystem.apply(state, target, status, power, duration, caster.getId());
                break;
            }
            case GAIN_ENERGY:
                damagePipeline.gainEnergy(state, target, value); // 走回能管线（受 energyGainRate 加权）
                break;
            case KNOCKBACK:
                applyKnockback(state, target, caster, (int) value);
                break;
            case BANISH:
                Banish.execute(state, target);
                break;
            default:
                throw new IllegalArgumentException("未知 SkillEffectType: " + effect.getEffect());
        }
    }

    /** 技能级施放条件（v5「依账除名」）：condition 为 null 恒满足 */
    private static boolean castConditionMet(SkillData skill, BattleUnit mainTarget) {
        CastCondition condition = skill.getCondition();
        if (condition == null) {
            return true;
        }
        if (condition.getType() == CastCondition.Type.TARGET_HP_BELOW) {
            return mainTarget.hpRatio() < condition.getValue();
        }
        throw new IllegalArgumentException("未知施放条件: " + condition.getType());
    }

    /**
     * 击退（薄执行，K13）：KNOCKBACK_IMMUNE 机制（经 startBattle 下发）或已倒地（CP17 起判定）/清扫者不动；
     * 方向 = 主轴远离施放者（|dx|≥|dy| 取 x 向，否则 y 向；同格不动），
     * 逐格 removeFromGrid+placeUnit，目标格被占/越界即止（保留已推进格）。
     */
    private static void applyKnockback(BattleState state, BattleUnit target, BattleUnit caster, int distance) {
        if (target.isCleaned() || target.getMechanic(SynergyMechanic.KNOCKBACK_IMMUNE) != null) {
            return;
        }
        int dx = target.getGridX() - caster.getGridX();
        int dy = target.getGridY() - caster.getGridY();
        if (dx == 0 && dy == 0) {
            return; // 同格不动
        }
        boolean alongX = Math.abs(dx) >= Math.abs(dy);
        int stepX = alongX ? Integer.signum(dx) : 0;
        int stepY = alongX ? 0 : Integer.signum(dy);
        int fromX = target.getGridX();
        int fromY = target.getGridY();
        int x = fromX;
        int y = fromY;
        state.removeFromGrid(target);
        for (int i = 0; i < distance; i++) {
            int nextX = x + stepX;
            int nextY = y + stepY;
            if (nextX < 0 || nextX >= GameBalance.BOARD_COLS
                    || nextY < 0 || nextY >= GameBalance.BOARD_ROWS
                    || state.unitAt(nextX, nextY) != null) {
                break; // 越界或被占即止
            }
            x = nextX;
            y = nextY;
        }
        state.placeUnit(target, x, y); // 一步未进时 x/y = from，原地放回
    }

    /** 机制比例查询（SPELL_PEN 等，无词 = 0）——百分比刻度 ÷100 在结算点（约定） */
    private static float mechanicRatioOf(BattleUnit unit, SynergyMechanic kind) {
        EffectData data = unit == null ? null : unit.getMechanic(kind);
        return data == null ? 0f : data.getValue() / 100f;
    }

    private static List<BattleUnit> singleList(BattleUnit unit) {
        List<BattleUnit> list = new ArrayList<BattleUnit>(1);
        list.add(unit);
        return list;
    }
}
