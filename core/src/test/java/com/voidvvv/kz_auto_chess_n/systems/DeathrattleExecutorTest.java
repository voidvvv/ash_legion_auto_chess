package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.DeathrattleKind;
import com.voidvvv.kz_auto_chess_n.data.DeathrattleSpec;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.EffectTarget;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Projectile;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.state;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.stats;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.tpl;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 个人亡语执行器测试（CP15，C5/C7）：四 kind 载荷、双份抚恤同 tick 两发、
 * 目标候选一律可行动存活（倒地豁免）。
 */
class DeathrattleExecutorTest {

    private static final class Rig {
        final DamagePipeline pipeline = new DamagePipeline(new CastTrigger() {
            @Override
            public void tryCast(BattleState state, BattleUnit caster) {
            }
        });
        final StatusSystem statuses = new StatusSystem(pipeline);
        final DeathrattleExecutor executor = new DeathrattleExecutor(pipeline, statuses);
    }

    /** 带亡语模板（默认 100hp/10atk 快照观察） */
    private static UnitData rattler(String id, DeathrattleSpec spec) {
        return new UnitData(id, "夹具" + id, "亡灵", "战士", 1,
                new BaseStats(100, 10, 0, 1f, 1, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_melee", false, spec, null);
    }

    private static void grantDouble(BattleUnit dead) {
        dead.setMechanics(Arrays.asList(new EffectData(null, null, SynergyMechanic.DOUBLE_DEATHRATTLE,
                null, null, null, null, null, EffectTarget.TRAITS, "亡灵")));
    }

    /** 快照 10 / 尸体 (2,2) 的死亡记录 */
    private static DeathSystem.DeathRecord record(BattleUnit dead, int killerId) {
        return new DeathSystem.DeathRecord(dead.getId(), dead.getGridX(), dead.getGridY(),
                dead.getEffective(com.voidvvv.kz_auto_chess_n.data.StatKey.ATTACK), killerId);
    }

    @Test
    @DisplayName("白骨滩（AREA 半径1 ×0.8 + SLOW30/2.5）：双响两轮各 8 真伤、SLOW 单条只刷新")
    void areaTrueDamageWithDouble() {
        Rig rig = new Rig();
        BattleUnit dead = unit(1, Side.PLAYER,
                rattler("skeleton", new DeathrattleSpec(DeathrattleKind.AREA_TRUE_DAMAGE,
                        0.8f, 1, StatusType.SLOW, 30f, 2.5f, 0f, 0f)), 2, 2);
        grantDouble(dead);
        BattleUnit e1 = unit(2, Side.ENEMY, tpl("e1"), 1, 2); // 距尸体 1
        BattleUnit e2 = unit(3, Side.ENEMY, tpl("e2"), 3, 2); // 距尸体 1
        BattleUnit far = unit(4, Side.ENEMY, tpl("far"), 0, 0); // 距尸体 4——半径外
        BattleState state = state(dead, e1, e2, far);
        dead.markCleaned();

        rig.executor.execute(state, dead, record(dead, -1));

        assertThat(e1.getCurrentHp()).isCloseTo(100f - 2f * 10f * 0.8f, within(1e-6f)); // 两轮各 8
        assertThat(e2.getCurrentHp()).isCloseTo(84f, within(1e-6f));
        assertThat(far.getCurrentHp()).isEqualTo(100f); // 半径外不中
        for (BattleUnit hit : Arrays.asList(e1, e2)) {
            assertThat(hit.getStatuses()).hasSize(1); // SLOW 单条（同 type 取大只刷新）
            assertThat(hit.getStatuses().get(0).getType()).isEqualTo(StatusType.SLOW);
            assertThat(hit.getStatuses().get(0).getPower()).isEqualTo(30f);
            assertThat(hit.getStatuses().get(0).getRemainingTime()).isEqualTo(2.5f);
        }
        assertThat(far.getStatuses()).isEmpty();
        long causeHits = 0;
        for (CombatEvent event : state.getEvents()) {
            if (event.getType() == CombatEvent.Type.HIT) {
                assertThat(event.getSkillId()).isEqualTo("deathrattle_skeleton"); // 紫飘字载荷口径
                causeHits++;
            }
        }
        assertThat(causeHits).isEqualTo(4); // 2 目标 × 双响两轮
    }

    @Test
    @DisplayName("坏账（KILLER ×1.5）：凶手在两发均对凶手；凶手先亡/DOT 无归因 → 每发各自转最近敌")
    void killerTrueDamageFallback() {
        Rig rig = new Rig();
        DeathrattleSpec badDebt = new DeathrattleSpec(DeathrattleKind.KILLER_TRUE_DAMAGE,
                1.5f, 0, null, 0f, 0f, 0f, 0f);

        // ① 凶手在场：双响两发均对凶手
        BattleUnit dead1 = unit(1, Side.PLAYER, rattler("debt1", badDebt), 2, 2);
        grantDouble(dead1);
        BattleUnit killer = unit(2, Side.ENEMY, tpl("killer"), 2, 3);
        BattleUnit bystander = unit(3, Side.ENEMY, tpl("bystander"), 1, 2);
        BattleState state1 = state(dead1, killer, bystander);
        dead1.markCleaned();
        rig.executor.execute(state1, dead1, record(dead1, 2));
        assertThat(killer.getCurrentHp()).isCloseTo(100f - 2f * 15f, within(1e-6f)); // 2×(10×1.5)
        assertThat(bystander.getCurrentHp()).isEqualTo(100f);

        // ② 凶手先亡（cleaned）：两发各自转移最近敌（旁观者距尸体 1）
        BattleUnit dead2 = unit(4, Side.PLAYER, rattler("debt2", badDebt), 2, 2);
        grantDouble(dead2);
        BattleUnit gone = unit(5, Side.ENEMY, tpl("gone"), 2, 1); // 原「凶手」
        BattleUnit near = unit(6, Side.ENEMY, tpl("near"), 1, 2);
        BattleState state2 = state(dead2, gone, near);
        gone.markCleaned();
        dead2.markCleaned();
        rig.executor.execute(state2, dead2, record(dead2, 5));
        assertThat(gone.getCurrentHp()).isEqualTo(100f);       // 已亡不再吃
        assertThat(near.getCurrentHp()).isCloseTo(70f, within(1e-6f)); // 转移目标吃两发

        // ③ DOT 致死（killer=-1）：同样转移
        BattleUnit dead3 = unit(7, Side.PLAYER, rattler("debt3", badDebt), 2, 2);
        BattleUnit near2 = unit(8, Side.ENEMY, tpl("near2"), 3, 2);
        BattleState state3 = state(dead3, near2);
        dead3.markCleaned();
        rig.executor.execute(state3, dead3, record(dead3, -1));
        assertThat(near2.getCurrentHp()).isCloseTo(100f - 10f * 1.5f, within(1e-6f)); // 单发（无双响）
    }

    @Test
    @DisplayName("绝响（DEATH_VOLLEY ×1.8）：单发锁 HIGHEST_ATK；双响两弹 = HIGHEST_ATK + LOWEST_HP，deathrattleBorn")
    void deathVolleyPicksAndFlags() {
        Rig rig = new Rig();
        DeathrattleSpec volley = new DeathrattleSpec(DeathrattleKind.DEATH_VOLLEY,
                1.8f, 0, null, 0f, 0f, 0f, 0f);

        // 单发（无双响）：HIGHEST_ATK
        BattleUnit dead1 = unit(1, Side.PLAYER, rattler("bow1", volley), 2, 4);
        BattleUnit heavy = new BattleUnit(2, tpl("heavy"), 1, Side.ENEMY, meleeSkill(),
                stats(100f, 50f, 0f, 1f, 1f, 1f)); // atk 50
        heavy.setPosition(2, 2);
        BattleUnit wounded = new BattleUnit(3, tpl("wounded"), 1, Side.ENEMY, meleeSkill(),
                stats(100f, 20f, 0f, 1f, 1f, 1f));
        wounded.setPosition(3, 2);
        wounded.modifyHp(-90f); // hpRatio 0.1
        BattleState state1 = state(dead1, heavy, wounded);
        dead1.markCleaned();
        rig.executor.execute(state1, dead1, record(dead1, -1));
        assertThat(state1.getProjectiles()).hasSize(1);
        Projectile shot = state1.getProjectiles().get(0);
        assertThat(shot.getTargetId()).isEqualTo(2);            // HIGHEST_ATK
        assertThat(shot.isDeathrattleBorn()).isTrue();
        assertThat(shot.getSkill()).isNull();
        assertThat(shot.getAttackSnapshot()).isCloseTo(10f * 1.8f, within(1e-6f));

        // 双响：两弹 = HIGHEST_ATK + LOWEST_HP（同 tick 两发）
        BattleUnit dead2 = unit(4, Side.PLAYER, rattler("bow2", volley), 2, 4);
        grantDouble(dead2);
        BattleUnit heavy2 = new BattleUnit(5, tpl("heavy2"), 1, Side.ENEMY, meleeSkill(),
                stats(100f, 50f, 0f, 1f, 1f, 1f));
        heavy2.setPosition(2, 2);
        BattleUnit wounded2 = new BattleUnit(6, tpl("wounded2"), 1, Side.ENEMY, meleeSkill(),
                stats(100f, 20f, 0f, 1f, 1f, 1f));
        wounded2.setPosition(3, 2);
        wounded2.modifyHp(-90f);
        BattleState state2 = state(dead2, heavy2, wounded2);
        dead2.markCleaned();
        rig.executor.execute(state2, dead2, record(dead2, -1));
        assertThat(state2.getProjectiles()).hasSize(2);
        assertThat(state2.getProjectiles().get(0).getTargetId()).isEqualTo(5); // 第 1 发 HIGHEST_ATK
        assertThat(state2.getProjectiles().get(1).getTargetId()).isEqualTo(6); // 第 2 发 LOWEST_HP
        for (Projectile p : state2.getProjectiles()) {
            assertThat(p.isDeathrattleBorn()).isTrue();
        }
    }

    @Test
    @DisplayName("灯留下了（ALLY_REGEN 0.02/5s）：可行动存活友军全员挂 REGEN；倒地友军不吃（C7 ③）")
    void allyRegenSkipsDowned() {
        Rig rig = new Rig();
        BattleUnit dead = unit(1, Side.PLAYER,
                rattler("lamp", new DeathrattleSpec(DeathrattleKind.ALLY_REGEN,
                        0f, 0, null, 0f, 0f, 0.02f, 5f)), 2, 4);
        BattleUnit mate = unit(2, Side.PLAYER, tpl("mate"), 1, 4);
        BattleUnit downed = unit(3, Side.PLAYER, tpl("downed"), 3, 4);
        downed.enterDowned(1.0f); // 测试充当 systems 层
        BattleUnit foe = unit(4, Side.ENEMY, tpl("foe"), 2, 2);
        BattleState state = state(dead, mate, downed, foe);
        dead.markCleaned();

        rig.executor.execute(state, dead, record(dead, -1));

        assertThat(mate.getStatuses()).hasSize(1);
        assertThat(mate.getStatuses().get(0).getType()).isEqualTo(StatusType.REGEN);
        assertThat(mate.getStatuses().get(0).getPower()).isEqualTo(0.02f);
        assertThat(mate.getStatuses().get(0).getRemainingTime()).isEqualTo(5f);
        assertThat(downed.getStatuses()).isEmpty(); // 倒地不吃
        assertThat(foe.getStatuses()).isEmpty();    // 敌方不吃
    }

    private static com.voidvvv.kz_auto_chess_n.data.SkillData meleeSkill() {
        return com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.meleeSkill();
    }
}
