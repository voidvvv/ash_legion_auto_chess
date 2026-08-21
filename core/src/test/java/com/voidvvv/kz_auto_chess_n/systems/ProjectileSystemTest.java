package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Projectile;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.effect;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.skill;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.state;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.tpl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 弹道系统测试（battle §5.3 + 口径 #13/#14）：HOMING 锁定弹追踪目标当前格中心；
 * 6 格/秒；冻结载荷命中；目标清扫消散；施放者清扫不影响在途弹。
 */
class ProjectileSystemTest {

    private static final class Wired {
        final DamagePipeline pipeline = new DamagePipeline(new CastTrigger() {
            @Override
            public void tryCast(BattleState state, BattleUnit caster) {
            }
        });
        final StatusSystem statusSystem = new StatusSystem(pipeline);
        final SkillExecutor executor = new SkillExecutor(pipeline, statusSystem);
        final ProjectileSystem system = new ProjectileSystem(pipeline, executor);
    }

    private static final Wired W = new Wired();

    /** 0 甲直构单位（格中心 = (x+0.5, y+0.5)） */
    private static BattleUnit u(int id, Side side, int x, int y) {
        BattleUnit unit = new BattleUnit(id, tpl("u" + id), 1, side, skill(
                "sk_" + id, SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 1f, null, null)),
                new com.voidvvv.kz_auto_chess_n.entities.BattleStats(100f, 10f, 0f, 1f, 3f, 1f, 0f, 100f, 0f));
        unit.setPosition(x, y);
        return unit;
    }

    @Test
    @DisplayName("6 格/秒到达 tick 数算例：3 格距离 → 30±1 tick 命中")
    void travelTimeForThreeCells() {
        BattleUnit atk = u(1, Side.PLAYER, 2, 5);
        BattleUnit target = u(2, Side.ENEMY, 2, 2); // 中心距 3.0
        BattleState state = state(atk, target);
        state.spawnProjectile(new Projectile(1, 2, 2.5f, 5.5f, 10f, false, null, 1f));

        for (int i = 0; i < 28; i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(target.getCurrentHp()).isEqualTo(100f); // 未到
        assertThat(state.getProjectiles()).hasSize(1);

        int hitTick = -1;
        for (int i = 28; i < 32; i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
            if (state.getProjectiles().isEmpty()) {
                hitTick = i + 1;
                break;
            }
        }
        assertThat(hitTick).isBetween(29, 31);
        assertThat(target.getCurrentHp()).isCloseTo(90f, within(1e-6f));
    }

    @Test
    @DisplayName("追踪目标移动后的格（改向）：目标换格后弹道折向新格中心并命中")
    void homesOntoMovedTarget() {
        BattleUnit atk = u(1, Side.PLAYER, 2, 5);
        BattleUnit target = u(2, Side.ENEMY, 2, 2);
        BattleState state = state(atk, target);
        state.spawnProjectile(new Projectile(1, 2, 2.5f, 5.5f, 10f, false, null, 1f));

        for (int i = 0; i < 10; i++) { // 前进 1.0 格 → 约 (2.5,4.5)
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        state.removeFromGrid(target);
        state.placeUnit(target, 4, 2); // 目标右移两格

        for (int i = 0; i < 60 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(state.getProjectiles()).isEmpty(); // 命中移动后的目标
        assertThat(target.getCurrentHp()).isCloseTo(90f, within(1e-6f));
    }

    @Test
    @DisplayName("普攻弹命中 = 发射快照 × 命中时护甲：发射后攻击者被降攻仍按快照结算（口径 #13）")
    void basicProjectileUsesFrozenSnapshot() {
        BattleUnit atk = u(1, Side.PLAYER, 2, 5);
        BattleUnit target = u(2, Side.ENEMY, 2, 2);
        BattleState state = state(atk, target);
        state.spawnProjectile(new Projectile(1, 2, 2.5f, 5.5f, 10f, false, null, 1f));
        atk.addStatus(new ActiveStatus(StatusType.ATK_DOWN, 9, 50f, 5f)); // 攻击减半——不应影响在途弹

        for (int i = 0; i < 40 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(target.getCurrentHp()).isCloseTo(90f, within(1e-6f)); // 快照 10 而非当前 5
    }

    @Test
    @DisplayName("暴击标志原样落地：crit 弹伤害 ×1.5、Hit 事件 crit=true")
    void critFlagCarriesThrough() {
        BattleUnit atk = u(1, Side.PLAYER, 2, 5);
        BattleUnit target = u(2, Side.ENEMY, 2, 2);
        BattleState state = state(atk, target);
        state.spawnProjectile(new Projectile(1, 2, 2.5f, 5.5f, 10f, true, null, 1f));

        for (int i = 0; i < 40 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(target.getCurrentHp()).isCloseTo(85f, within(1e-6f));
        CombatEvent hit = state.getEvents().get(0);
        assertThat(hit.getType()).isEqualTo(CombatEvent.Type.HIT);
        assertThat(hit.isCrit()).isTrue();
    }

    @Test
    @DisplayName("目标先一步被清扫 → 消散：Fizzled 事件、无伤害无回能（口径 #14）")
    void fizzledWhenTargetCleaned() {
        BattleUnit atk = u(1, Side.PLAYER, 2, 5);
        BattleUnit target = u(2, Side.ENEMY, 2, 2);
        BattleState state = state(atk, target);
        state.spawnProjectile(new Projectile(1, 2, 2.5f, 5.5f, 10f, false, null, 1f));
        target.markCleaned();

        W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        assertThat(state.getProjectiles()).isEmpty();
        assertThat(target.getCurrentHp()).isEqualTo(100f);
        assertThat(atk.getEnergy()).isEqualTo(0f); // 落空不触发回能/吸血
        assertThat(state.getEvents()).hasSize(1);
        assertThat(state.getEvents().get(0).getType()).isEqualTo(CombatEvent.Type.PROJECTILE_FIZZLED);
    }

    @Test
    @DisplayName("施放者被清扫不影响在途弹（口径 #14）：命中照常结算")
    void casterDeathDoesNotAffectProjectile() {
        BattleUnit atk = u(1, Side.PLAYER, 2, 5);
        BattleUnit target = u(2, Side.ENEMY, 2, 2);
        BattleState state = state(atk, target);
        state.spawnProjectile(new Projectile(1, 2, 2.5f, 5.5f, 10f, false, null, 1f));
        atk.markCleaned();

        for (int i = 0; i < 40 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(state.getProjectiles()).isEmpty();
        assertThat(target.getCurrentHp()).isCloseTo(90f, within(1e-6f));
        assertThat(state.getEvents().get(0).getType()).isEqualTo(CombatEvent.Type.HIT);
    }

    @Test
    @DisplayName("多弹互不碰撞：两弹各自命中各自目标")
    void multipleProjectilesDoNotCollide() {
        BattleUnit atk1 = u(1, Side.PLAYER, 1, 5);
        BattleUnit atk2 = u(2, Side.PLAYER, 4, 5);
        BattleUnit t3 = u(3, Side.ENEMY, 1, 2);
        BattleUnit t4 = u(4, Side.ENEMY, 4, 2);
        BattleState state = state(atk1, atk2, t3, t4);
        state.spawnProjectile(new Projectile(1, 3, 1.5f, 5.5f, 10f, false, null, 1f));
        state.spawnProjectile(new Projectile(2, 4, 4.5f, 5.5f, 10f, false, null, 1f));

        for (int i = 0; i < 40 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(state.getProjectiles()).isEmpty();
        assertThat(t3.getCurrentHp()).isCloseTo(90f, within(1e-6f));
        assertThat(t4.getCurrentHp()).isCloseTo(90f, within(1e-6f));
    }

    @Test
    @DisplayName("MELEE_INSTANT 技能不产弹（cast 即结算）")
    void meleeSkillSpawnsNoProjectile() {
        SkillData melee = skill("sk_m", SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 2f, null, null));
        BattleUnit caster = new BattleUnit(1, tpl("c"), 1, Side.PLAYER, melee,
                new com.voidvvv.kz_auto_chess_n.entities.BattleStats(100f, 10f, 0f, 1f, 1f, 1f, 0f, 100f, 0f));
        caster.setPosition(2, 4);
        BattleUnit target = u(2, Side.ENEMY, 2, 2);
        caster.setTargetId(2);
        BattleState state = state(caster, target);
        assertThat(W.executor.cast(state, caster)).isTrue();
        assertThat(state.getProjectiles()).isEmpty();
        assertThat(target.getCurrentHp()).isCloseTo(80f, within(1e-6f)); // 已即时结算
    }

    @Test
    @DisplayName("技能弹命中走 applyAtImpact：HIT 事件带 skillId、伤害按冻结载荷")
    void skillProjectileAppliesOnImpact() {
        SkillData homing = skill("sk_h", SkillShape.SINGLE_TARGET, Delivery.HOMING,
                effect(SkillEffectType.DAMAGE, 2f, null, null));
        BattleUnit atk = u(1, Side.PLAYER, 2, 5);
        BattleUnit target = u(2, Side.ENEMY, 2, 2);
        BattleState state = state(atk, target);
        state.spawnProjectile(new Projectile(1, 2, 2.5f, 5.5f, 10f, false, homing, 1f));

        for (int i = 0; i < 40 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(target.getCurrentHp()).isCloseTo(80f, within(1e-6f)); // 10 × 2
        assertThat(state.getEvents().get(0).getType()).isEqualTo(CombatEvent.Type.HIT);
        assertThat(state.getEvents().get(0).getSkillId()).isEqualTo("sk_h");
    }
}
