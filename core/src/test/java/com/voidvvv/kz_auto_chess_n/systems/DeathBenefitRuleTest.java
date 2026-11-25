package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.EffectTarget;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.state;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.tpl;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 死亡收益结算测试（CP16/CP19）：阵亡名册（C8/C9）、大公点算（C10/C11）、
 * 兽潮（C13）、连环清账（C12）——真死才触发、放逐不触发。
 */
class DeathBenefitRuleTest {

    private static final class Rig {
        final DamagePipeline pipeline = new DamagePipeline(new CastTrigger() {
            @Override
            public void tryCast(BattleState state, BattleUnit caster) {
            }
        });
        final StatusSystem statuses = new StatusSystem(pipeline);
    }

    private static UnitData undead(String id, boolean boss) {
        return new UnitData(id, "夹具" + id, "亡灵", "战士", boss ? 0 : 1,
                new BaseStats(100, 10, 0, 1f, 1, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_melee", false);
    }

    private static EffectData tax(int atk, int hp) {
        return new EffectData(null, null, SynergyMechanic.DEATH_TAX, null,
                (float) atk, (float) hp, null, null, EffectTarget.TRAITS, "亡灵");
    }

    private static EffectData audit(int thresholdPct) {
        return new EffectData(null, null, SynergyMechanic.DEATH_AUDIT, null,
                (float) thresholdPct, null, null, null, EffectTarget.ALLIES, null);
    }

    private static void grant(BattleUnit unit, EffectData... effects) {
        unit.setMechanics(Arrays.asList(effects));
    }

    private static DeathSystem.DeathRecord recordOf(BattleUnit dead, int killerId) {
        return new DeathSystem.DeathRecord(dead.getId(), dead.getGridX(), dead.getGridY(),
                10f, killerId);
    }

    // —— 阵亡名册（C8/C9）——

    @Test
    @DisplayName("名册：最近可行动亡灵继承遗物 +4/+20（攻 ADD、上限 ADD 并立即回复）；非 trait 友方更近也不继承")
    void deathTaxNearestTraitHeirInherits() {
        Rig rig = new Rig();
        BattleUnit dead = unit(1, Side.PLAYER, undead("dead1", false), 2, 4);
        BattleUnit heir = unit(2, Side.PLAYER, undead("heir", false), 3, 4); // 距尸体 1
        grant(heir, tax(4, 20)); // K1 下发：trait 命中者持有同档机制（死者已清扫，由存活方读取）
        BattleUnit closerOrc = unit(3, Side.PLAYER, tpl("orc"), 2, 3);       // 距尸体 1 但非亡灵
        BattleState state = state(dead, heir, closerOrc);
        dead.markCleaned();
        state.removeFromGrid(dead);
        float heirHpBefore = heir.getCurrentHp();

        DeathBenefitRule.settleDeathTax(state, dead, recordOf(dead, -1));

        assertThat(heir.getCombatCounters().getRelicAtkAdd()).isEqualTo(4f);
        assertThat(heir.getCombatCounters().getRelicHpAdd()).isEqualTo(20f);
        assertThat(heir.getEffective(StatKey.ATTACK)).isCloseTo(14f, within(1e-6f)); // 10 + 4
        assertThat(heir.getCurrentHp()).isCloseTo(heirHpBefore + 20f, within(1e-6f)); // 立即回复（新上限内）
        assertThat(closerOrc.getCombatCounters().getRelicAtkAdd()).isEqualTo(0f);    // C9 非 trait 不继承
    }

    @Test
    @DisplayName("名册平局：等距双亡灵取 id 小；死者自身不参与（C9）")
    void deathTaxTieBreaksBySmallerId() {
        Rig rig = new Rig();
        BattleUnit dead = unit(3, Side.PLAYER, undead("dead3", false), 2, 4); // id 3 最大也最先死
        BattleUnit left = unit(1, Side.PLAYER, undead("left", false), 1, 4);  // 距尸体 1
        BattleUnit right = unit(2, Side.PLAYER, undead("right", false), 3, 4); // 距尸体 1
        grant(dead, tax(5, 25));  // K1 下发：trait 命中者（含存活继承人）人人持有同档机制
        grant(left, tax(5, 25));
        grant(right, tax(5, 25));
        BattleState state = state(dead, left, right);
        dead.markCleaned();
        state.removeFromGrid(dead);

        DeathBenefitRule.settleDeathTax(state, dead, recordOf(dead, -1));

        assertThat(left.getCombatCounters().getRelicAtkAdd()).isEqualTo(5f);  // id 1 胜
        assertThat(right.getCombatCounters().getRelicAtkAdd()).isEqualTo(0f);
    }

    @Test
    @DisplayName("名册作废（C8 倒地窗口）：唯一亡灵继承者倒地中 → 无入账无回复")
    void deathTaxVoidWhenOnlyHeirDowned() {
        Rig rig = new Rig();
        BattleUnit dead = unit(1, Side.PLAYER, undead("dead2", false), 2, 4);
        BattleUnit downedHeir = unit(2, Side.PLAYER, undead("dk", false), 3, 4);
        grant(dead, tax(4, 20));
        grant(downedHeir, tax(4, 20)); // 真实对局：trait 命中者人人持有
        downedHeir.enterDowned(1.0f);  // 测试充当 systems 层
        BattleState state = state(dead, downedHeir);
        dead.markCleaned();
        state.removeFromGrid(dead);
        float hpBefore = downedHeir.getCurrentHp();

        DeathBenefitRule.settleDeathTax(state, dead, recordOf(dead, -1));

        assertThat(downedHeir.getCombatCounters().getRelicAtkAdd()).isEqualTo(0f);
        assertThat(downedHeir.getCurrentHp()).isEqualTo(hpBefore); // 无回复
    }

    // —— 大公点算（C10/C11）——

    @Test
    @DisplayName("点算：<25% 最低者被放逐（UNIT_BANISHED、无 UNIT_DIED、腾格）；=25% 与 ≥25% 不触发")
    void deathAuditBanishesLowestBelowThreshold() {
        Rig rig = new Rig();
        BattleUnit dead = unit(1, Side.PLAYER, undead("auditor", false), 2, 4);
        BattleUnit holder = unit(4, Side.PLAYER, tpl("holder"), 1, 4); // 同侧存活持有者（ALLIES 下发）
        grant(holder, audit(25));
        BattleUnit low = unit(2, Side.ENEMY, tpl("low"), 2, 2);
        low.modifyHp(-85f); // 15%
        BattleUnit edge = unit(3, Side.ENEMY, tpl("edge"), 3, 2);
        edge.modifyHp(-75f); // 恰 25%
        BattleState state = state(dead, holder, low, edge);
        dead.markCleaned();
        state.removeFromGrid(dead);

        DeathBenefitRule.settleDeathAudit(state, dead, recordOf(dead, -1));

        assertThat(low.isCleaned()).isTrue();
        assertThat(low.isBanished()).isTrue();
        assertThat(state.unitAt(2, 2)).isNull(); // 腾格
        assertThat(state.getEvents())
                .anyMatch(e -> e.getType() == CombatEvent.Type.UNIT_BANISHED);
        assertThat(state.getEvents())
                .noneMatch(e -> e.getType() == CombatEvent.Type.UNIT_DIED); // 非死亡（C10）
        assertThat(edge.isCleaned()).isFalse(); // =25% 严格小于才合格
    }

    @Test
    @DisplayName("点算平局取 id 小；Boss 亦可被移除（D3）；无 <阈值 目标不触发不缓存（C11）")
    void deathAuditTieBossAndNoQualified() {
        Rig rig = new Rig();
        BattleUnit dead = unit(1, Side.PLAYER, undead("auditor2", false), 2, 4);
        BattleUnit holder = unit(4, Side.PLAYER, tpl("holder2"), 1, 4); // 同侧存活持有者（ALLIES 下发）
        grant(holder, audit(25));
        BattleUnit e1 = unit(2, Side.ENEMY, tpl("e1"), 2, 2);
        e1.modifyHp(-80f); // 20%
        BattleUnit e2 = unit(3, Side.ENEMY, tpl("e2"), 3, 2);
        e2.modifyHp(-80f); // 20% 平局
        BattleUnit aliveBoss = unit(5, Side.ENEMY, undead("boss5", true), 4, 2);
        BattleState state = state(dead, holder, e1, e2, aliveBoss);
        dead.markCleaned();
        state.removeFromGrid(dead);

        DeathBenefitRule.settleDeathAudit(state, dead, recordOf(dead, -1));
        assertThat(e1.isCleaned()).isTrue();  // 平局 id 小（2 < 3）
        assertThat(e2.isCleaned()).isFalse();
        assertThat(aliveBoss.isCleaned()).isFalse(); // 满血 Boss 不合格

        // Boss 残血 10% → 可被移除（D3）
        BattleUnit dead2 = unit(6, Side.PLAYER, undead("auditor3", false), 2, 4);
        BattleUnit holder2 = unit(8, Side.PLAYER, tpl("holder3"), 1, 4);
        grant(holder2, audit(25));
        BattleUnit woundedBoss = unit(7, Side.ENEMY, undead("boss7", true), 2, 2);
        woundedBoss.modifyHp(-90f); // 10%
        BattleState state2 = state(dead2, holder2, woundedBoss);
        dead2.markCleaned();
        state2.removeFromGrid(dead2);
        DeathBenefitRule.settleDeathAudit(state2, dead2, recordOf(dead2, -1));
        assertThat(woundedBoss.isBanished()).isTrue(); // D3：Boss 不豁免

        // 无合格目标：全员 ≥25% → 不触发（无事件、无人 cleaned）
        BattleUnit dead3 = unit(9, Side.PLAYER, undead("auditor4", false), 2, 4);
        BattleUnit holder3 = unit(11, Side.PLAYER, tpl("holder4"), 1, 4);
        grant(holder3, audit(25));
        BattleUnit healthy = unit(10, Side.ENEMY, tpl("healthy"), 2, 2);
        BattleState state3 = state(dead3, holder3, healthy);
        dead3.markCleaned();
        state3.removeFromGrid(dead3);
        DeathBenefitRule.settleDeathAudit(state3, dead3, recordOf(dead3, -1));
        assertThat(healthy.isCleaned()).isFalse();
        assertThat(state3.getEvents()).isEmpty(); // 不触发不缓存（C11）
    }

    // —— 兽潮（C13）+ 连环清账（C12）——

    @Test
    @DisplayName("兽潮：敌方真死 → 对侧含机制者挂 ASPD_UP 25/4s（刷新制不叠双份）；同 tick 两死只刷新；放逐不触发")
    void enemyDeathSurgeAppliesAndRefreshes() {
        Rig rig = new Rig();
        BattleUnit beast = unit(2, Side.PLAYER, tpl("beast"), 2, 4);
        grant(beast, new EffectData(null, null, SynergyMechanic.ENEMY_DEATH_SURGE, null,
                25f, null, null, 4f, EffectTarget.TRAITS, "野兽"));
        BattleUnit downedBeast = unit(3, Side.PLAYER, tpl("dbeast"), 1, 4);
        grant(downedBeast, new EffectData(null, null, SynergyMechanic.ENEMY_DEATH_SURGE, null,
                25f, null, null, 4f, EffectTarget.TRAITS, "野兽"));
        downedBeast.enterDowned(1.0f);
        BattleState state = state(beast, downedBeast);
        BattleUnit deadEnemy = unit(4, Side.ENEMY, tpl("prey"), 2, 2);

        DeathBenefitRule.settleEnemyDeathSurge(state, deadEnemy, rig.statuses);
        assertThat(beast.getStatuses()).hasSize(1);
        assertThat(beast.getStatuses().get(0).getType()).isEqualTo(StatusType.ASPD_UP);
        assertThat(beast.getStatuses().get(0).getPower()).isEqualTo(25f);
        assertThat(beast.getStatuses().get(0).getRemainingTime()).isEqualTo(4f);
        assertThat(downedBeast.getStatuses()).isEmpty(); // 倒地不吃

        // 第二次敌方真死：同 type 取大 = 刷新制（仍单条、时长不叠）
        DeathBenefitRule.settleEnemyDeathSurge(state, deadEnemy, rig.statuses);
        assertThat(beast.getStatuses()).hasSize(1);
        assertThat(beast.getStatuses().get(0).getPower()).isEqualTo(25f);

        // 放逐路径不触发（Banish 无 DeathSystem 足迹）：放逐后无新状态、无 STATUS_APPLIED
        BattleState state2 = state(beast);
        BattleUnit banished = unit(5, Side.ENEMY, tpl("banished"), 2, 2);
        Banish.execute(state2, banished);
        assertThat(beast.getStatuses()).hasSize(1); // 仍是那一条，未新增
        assertThat(state2.getEvents())
                .noneMatch(e -> e.getType() == CombatEvent.Type.STATUS_APPLIED);
    }

    @Test
    @DisplayName("清账：真死归因凶手含机制且可行动 → 能量回满；无归因/凶手亡/无机制不触发；放逐不触发")
    void killEnergyRefundOnTrueDeath() {
        BattleUnit assassin = unit(1, Side.PLAYER, tpl("assassin"), 2, 4);
        grant(assassin, new EffectData(null, null, SynergyMechanic.KILL_ENERGY_REFUND, null,
                null, null, null, null, EffectTarget.TRAITS, "刺客"));
        BattleUnit victim = unit(2, Side.ENEMY, tpl("victim"), 2, 2);
        BattleUnit deadKiller = unit(3, Side.PLAYER, tpl("dk3"), 1, 4);
        grant(deadKiller, new EffectData(null, null, SynergyMechanic.KILL_ENERGY_REFUND, null,
                null, null, null, null, EffectTarget.TRAITS, "刺客"));
        BattleUnit plain = unit(4, Side.PLAYER, tpl("plain"), 0, 4);
        BattleState state = state(assassin, victim, deadKiller, plain);

        assassin.setEnergy(30f);
        DeathBenefitRule.settleKillEnergyRefund(state, recordOf(victim, 1));
        assertThat(assassin.getEnergy()).isEqualTo(GameBalance.ENERGY_MAX); // 回满

        // 无归因（DOT 来源先亡）
        assassin.setEnergy(30f);
        DeathBenefitRule.settleKillEnergyRefund(state, recordOf(victim, -1));
        assertThat(assassin.getEnergy()).isEqualTo(30f);

        // 凶手先亡（cleaned，如亡语致死且亡语主人已倒下）——归因门拦下
        deadKiller.markCleaned();
        deadKiller.setEnergy(10f);
        DeathBenefitRule.settleKillEnergyRefund(state, recordOf(victim, 3));
        assertThat(deadKiller.getEnergy()).isEqualTo(10f); // 不可行动不回

        // 无机制凶手
        plain.setEnergy(20f);
        DeathBenefitRule.settleKillEnergyRefund(state, recordOf(victim, 4));
        assertThat(plain.getEnergy()).isEqualTo(20f);
    }
}
