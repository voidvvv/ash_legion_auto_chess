package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Projectile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 弹道系统（battle §5.3；主循环阶段②）。HOMING 唯一形态（Q2）：
 * 推进朝目标<b>当前格中心</b>欧氏直线逼近，速度 PROJECTILE_SPEED（6 格/秒）；
 * 本 tick 步长可达即命中（无需走满）。目标先一步被清扫 → 立即消散（Fizzled）；
 * 施放者被清扫不影响在途弹（载荷已冻结，口径 #13/#14）。
 */
public final class ProjectileSystem {
    private final DamagePipeline damagePipeline;
    private final SkillExecutor skillExecutor;

    public ProjectileSystem(DamagePipeline damagePipeline, SkillExecutor skillExecutor) {
        this.damagePipeline = Objects.requireNonNull(damagePipeline, "damagePipeline 不能为 null");
        this.skillExecutor = Objects.requireNonNull(skillExecutor, "skillExecutor 不能为 null");
    }

    /** 阶段②：推进所有在途弹并分发命中/消散 */
    public void advanceAll(BattleState state, float dt) {
        List<Projectile> snapshot = new ArrayList<Projectile>(state.getProjectiles());
        for (Projectile projectile : snapshot) {
            BattleUnit target = state.getUnitById(projectile.getTargetId());
            if (target == null || target.isCleaned()) {
                state.record(CombatEvent.projectileFizzled(state.getTick(),
                        projectile.getSourceId(), projectile.getTargetId()));
                state.removeProjectile(projectile);
                continue;
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
        if (projectile.getSkill() == null) {
            damagePipeline.applyDirectHit(state, caster, target,
                    projectile.getAttackSnapshot(), 1f, projectile.isCrit(), true, null);
        } else {
            skillExecutor.applyAtImpact(state, caster, target, projectile.getSkill(),
                    projectile.getAttackSnapshot(), projectile.getScaleFactor());
        }
    }
}
