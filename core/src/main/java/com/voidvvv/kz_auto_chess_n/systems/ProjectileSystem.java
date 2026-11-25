package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.DeathrattleSpec;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Projectile;
import com.voidvvv.kz_auto_chess_n.entities.Side;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 弹道系统（battle §5.3；主循环阶段②）。HOMING 唯一形态（Q2）：
 * 推进朝目标<b>当前格中心</b>欧氏直线逼近，速度 PROJECTILE_SPEED（6 格/秒）；
 * 本 tick 步长可达即命中（无需走满）。目标先一步被清扫/倒地 → 普攻与技能弹立即消散
 * （Fizzled）；亡语源弹改按 LOWEST_HP 重选目标（C6/§五.7，无可选才消散）；
 * 施放者被清扫不影响在途弹（载荷已冻结，口径 #13/#14）。
 * 普攻弹（skill==null 且非亡语源）飞向战士身后目标时被盾墙拦截（CP24，K8）。
 */
public final class ProjectileSystem {
    private final DamagePipeline damagePipeline;
    private final SkillExecutor skillExecutor;

    public ProjectileSystem(DamagePipeline damagePipeline, SkillExecutor skillExecutor) {
        this.damagePipeline = Objects.requireNonNull(damagePipeline, "damagePipeline 不能为 null");
        this.skillExecutor = Objects.requireNonNull(skillExecutor, "skillExecutor 不能为 null");
    }

    /** 阶段②：推进所有在途弹并分发命中/拦截/消散（亡语源弹目标失效改重选，C6/§五.7） */
    public void advanceAll(BattleState state, float dt) {
        List<Projectile> snapshot = new ArrayList<Projectile>(state.getProjectiles());
        for (Projectile projectile : snapshot) {
            BattleUnit target = state.getUnitById(projectile.getTargetId());
            if (target == null || !target.isActable()) {
                if (projectile.isDeathrattleBorn()) { // §五.7：亡语源弹道按 LOWEST_HP 重选
                    BattleUnit source = state.getUnitById(projectile.getSourceId());
                    BattleUnit reselected = source == null ? null
                            : DeathrattleExecutor.pickLowestHpActableEnemy(state, source);
                    if (reselected != null) {
                        projectile.retarget(reselected.getId());
                        continue;
                    }
                }
                state.record(CombatEvent.projectileFizzled(state.getTick(),
                        projectile.getSourceId(), projectile.getTargetId()));
                state.removeProjectile(projectile);
                continue;
            }
            if (projectile.getSkill() == null && !projectile.isDeathrattleBorn()
                    && tryShieldWallIntercept(state, projectile, target)) {
                continue; // 普攻弹被距目标最近的 SHIELD_WALL 战士挡下（只拦不弹）；附带因 impact 未发生而落空
            }
            float centerX = target.getGridX() + 0.5f;
            float centerY = target.getGridY() + 0.5f;
            if (projectile.distanceTo(centerX, centerY) <= GameBalance.PROJECTILE_SPEED * dt) {
                impact(state, projectile, target);
                state.removeProjectile(projectile);
            } else {
                projectile.advance(dt, centerX, centerY);
            }
        }
    }

    private void impact(BattleState state, Projectile projectile, BattleUnit target) {
        BattleUnit caster = state.getUnitById(projectile.getSourceId()); // 可能已清扫——载荷取冻结值
        if (projectile.isDeathrattleBorn()) {
            damagePipeline.applyTrueDamage(state, caster, target,
                    projectile.getAttackSnapshot(), DeathrattleSpec.VOLLEY_SKILL_ID); // 真伤路由（C6）
        } else if (projectile.getSkill() == null) {
            damagePipeline.applyDirectHit(state, caster, target,
                    projectile.getAttackSnapshot(), 1f, projectile.isCrit(),
                    projectile.getCritBonusPp(), true, null, 0f); // critBonusPp 随载荷透传（CP27 K10）
        } else {
            skillExecutor.applyAtImpact(state, caster, target, projectile.getSkill(),
                    projectile.getAttackSnapshot(), projectile.getScaleFactor());
        }
    }

    /** 盾墙（战士(6) SHIELD_WALL，CP24 K8）：目标侧「含机制、可行动存活、目标在其身后」的战士中
     *  距<b>目标</b>（R8 参照）曼哈顿最近（平局 id 小）者挡下普攻弹——只拦不弹、无伤害转移；
     *  技能弹/近战/亡语源弹不受限。 */
    private static boolean tryShieldWallIntercept(BattleState state, Projectile projectile,
                                                  BattleUnit target) {
        if (target == null) {
            return false;
        }
        BattleUnit blocker = null;
        int bestDistance = 0;
        for (BattleUnit candidate : state.getUnits()) {
            if (candidate.getSide() != target.getSide() || !candidate.isActable()
                    || candidate.getMechanic(SynergyMechanic.SHIELD_WALL) == null) {
                continue;
            }
            boolean behind = candidate.getSide() == Side.PLAYER
                    ? target.getGridY() > candidate.getGridY()
                    : target.getGridY() < candidate.getGridY();
            if (!behind) {
                continue;
            }
            int distance = Math.abs(candidate.getGridX() - target.getGridX())
                    + Math.abs(candidate.getGridY() - target.getGridY());
            if (blocker == null || distance < bestDistance
                    || (distance == bestDistance && candidate.getId() < blocker.getId())) {
                blocker = candidate;
                bestDistance = distance;
            }
        }
        if (blocker == null) {
            return false;
        }
        state.record(CombatEvent.projectileBlocked(state.getTick(), blocker.getId(), target.getId()));
        state.removeProjectile(projectile);
        return true;
    }
}
