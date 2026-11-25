package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 赛后归因验收件（品质改造 §1.4，纯 JVM 合成事件流）：九行文案表逐条对照 + 候选优先级
 * （认输 → 超时 → 零部署 → 关联死亡 → A≥2 → 其它关键命中 → 施法无正扣血 → 施法前倒下 → 通用）
 * + 同 tick 群伤去重、晚到追踪命中、死亡归属错配、倒地/复活/消散不参与。
 */
@DisplayName("赛后归因：九行文案表与候选优先级（品质改造 §1.4）")
class BattleAttributionTest {

    private static GameData data;

    @BeforeAll
    static void loadRealData() {
        data = PaiDuelDriver.realData();
    }

    /** 夹具：玩家 W/M/S（id 1/2/3）+ 冰霜巨人(10) + 暗影之刃(11)，已落格 */
    private static BattleState battle() {
        BattleUnit w = BattleTestFixtures.placed(1, Side.PLAYER, data.getUnit(PaiTestScript.W), 2, 4);
        BattleUnit m = BattleTestFixtures.placed(2, Side.PLAYER, data.getUnit(PaiTestScript.M), 1, 4);
        BattleUnit s = BattleTestFixtures.placed(3, Side.PLAYER, data.getUnit(PaiTestScript.S), 2, 5);
        BattleUnit giant = BattleTestFixtures.placed(10, Side.ENEMY,
                data.getUnit(ThreatPredictor.UNIT_GLACIAL_GIANT), 1, 1);
        BattleUnit blade = BattleTestFixtures.placed(11, Side.ENEMY,
                data.getUnit(ThreatPredictor.UNIT_SHADOW_BLADE), 2, 2);
        return BattleTestFixtures.state(w, m, s, giant, blade);
    }

    /** A 群伤事件组：CAST + 指定目标数的正扣血（同 tick 同源同技能） */
    private static void recordStomp(BattleState state, int tick, int[] targets) {
        state.record(CombatEvent.cast(tick, 10, targets[0], ThreatPredictor.SKILL_BREAK_STOMP));
        for (int target : targets) {
            state.record(CombatEvent.hit(tick, 10, target, 12f, false, ThreatPredictor.SKILL_BREAK_STOMP));
        }
    }

    /** B 单目标事件组：CAST（锁定 target）+ 实际命中 victim（HOMING 追踪落点） */
    private static void recordExecute(BattleState state, int tick, int lockedTarget, int victim) {
        state.record(CombatEvent.cast(tick, 11, lockedTarget, ThreatPredictor.SKILL_EXECUTE));
        state.record(CombatEvent.hit(tick, 11, victim, 20f, false, ThreatPredictor.SKILL_EXECUTE));
    }

    private static String attribute(BattleState state) {
        return BattleAttribution.attribute(state, data, state.isSurrendered());
    }

    // —— 优先级 ①②③ ——

    @Test
    @DisplayName("① 主动认输最高优先：有关键命中也被认输句覆盖（§1.4）")
    void surrenderTakesPriority() {
        BattleState state = battle();
        state.record(CombatEvent.cast(100, 10, 1, ThreatPredictor.SKILL_BREAK_STOMP));
        state.record(CombatEvent.hit(100, 10, 1, 12f, false, ThreatPredictor.SKILL_BREAK_STOMP));
        state.markSurrendered();
        state.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(state)).isEqualTo("本场主动认输，下次开战前可先检查敌方危险格。");
    }

    @Test
    @DisplayName("② 超时独立结局：TIMEOUT → 时限秒数 + 敌方存活数（§1.4）")
    void timeoutSecond() {
        BattleState state = battle();
        state.finish(BattleOutcome.TIMEOUT);
        assertThat(attribute(state)).isEqualTo("60秒时敌方还有2枚棋子，下次让输出更早接敌。");
    }

    @Test
    @DisplayName("③ 零部署：玩家侧无单位 → 上阵引导（§1.4）")
    void noDeploymentThird() {
        BattleUnit giant = BattleTestFixtures.placed(10, Side.ENEMY,
                data.getUnit(ThreatPredictor.UNIT_GLACIAL_GIANT), 1, 1);
        BattleState state = BattleTestFixtures.state(giant);
        state.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(state)).isEqualTo("这场没有上阵棋子，先拖一枚棋子到己方格。");
    }

    // —— ④⑤⑥ 关键命中文案 ——

    @Test
    @DisplayName("④ A 群伤且有人倒下：同 tick 同 killer 关联死亡 →「打中2人，X随后倒下」")
    void stompWithMatchedDeath() {
        BattleState state = battle();
        recordStomp(state, 100, new int[]{1, 2});
        state.record(CombatEvent.unitDied(100, 1, 10)); // 同 tick、同亡者、同 killerId
        state.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(state))
                .isEqualTo("破阵踏打中2人，兽人战士随后倒下，下次让后排离前排至少两格。");
    }

    @Test
    @DisplayName("⑤ A 群伤 ≥2 人（无死亡）：「破阵踏打中2人，下次让后排离前排至少两格」")
    void stompMultiTarget() {
        BattleState state = battle();
        recordStomp(state, 100, new int[]{1, 2});
        state.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(state)).isEqualTo("破阵踏打中2人，下次让后排离前排至少两格。");
    }

    @Test
    @DisplayName("⑥ A 单目标：「破阵踏只打中X，下次可让其他棋子继续隔两格支援」")
    void stompSingleTarget() {
        BattleState state = battle();
        recordStomp(state, 100, new int[]{1});
        state.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(state)).isEqualTo("破阵踏只打中兽人战士，下次可让其他棋子继续隔两格支援。");
    }

    @Test
    @DisplayName("④ B 有关联死亡：「X被处决打中后倒下，下次把战士摆到最后一排」（死亡匹配优先于战士行）")
    void executeWithMatchedDeath() {
        BattleState state = battle();
        recordExecute(state, 100, 1, 2);
        state.record(CombatEvent.unitDied(100, 2, 11));
        state.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(state)).isEqualTo("焰痕祭司被处决打中后倒下，下次把战士摆到最后一排。");
    }

    @Test
    @DisplayName("⑥ B 命中可确认的战士：「战士X接下处决，下次可以沿用战士后置」")
    void executeHitsWarrior() {
        BattleState state = battle();
        recordExecute(state, 100, 1, 1);
        state.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(state)).isEqualTo("战士兽人战士接下处决，下次可以沿用战士后置。");
    }

    @Test
    @DisplayName("⑥ B 命中其它棋子：「处决打中X，下次可让耐打的棋子站最后」")
    void executeHitsOther() {
        BattleState state = battle();
        recordExecute(state, 100, 2, 2);
        state.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(state)).isEqualTo("处决打中焰痕祭司，下次可让耐打的棋子站最后。");
    }

    // —— §1.4 边界口径 ——

    @Test
    @DisplayName("同 tick 群伤去重：3 条命中含重复目标 → 仍数 2 人（§1.4 去重数目标）")
    void duplicateTargetsDeduped() {
        BattleState state = battle();
        recordStomp(state, 100, new int[]{1, 2});
        state.record(CombatEvent.hit(100, 10, 1, 6f, false, ThreatPredictor.SKILL_BREAK_STOMP)); // 目标 1 重复
        state.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(state)).isEqualTo("破阵踏打中2人，下次让后排离前排至少两格。");
    }

    @Test
    @DisplayName("晚到追踪命中：B 实际命中对象取 HIT 证据（≠ CAST 锁定目标——§1.4 禁止锁定当已命中）")
    void lateHomingHitUsesHitEvidence() {
        BattleState state = battle();
        recordExecute(state, 100, 1, 2); // 施法锁定 1（战士），弹道实际命中 2（祭司）
        state.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(state)).isEqualTo("处决打中焰痕祭司，下次可让耐打的棋子站最后。");
    }

    @Test
    @DisplayName("死亡归属错配：亡者在本命中之后还有正扣血 → 该伤害非最后一条 → 不归因（回 A 单目标口径）")
    void deathAttributionMismatchFallsBack() {
        BattleState state = battle();
        recordStomp(state, 100, new int[]{1, 2});
        state.record(CombatEvent.hit(100, 10, 1, 5f, false, null)); // 同 tick 另一来源补刀（普攻）
        state.record(CombatEvent.unitDied(100, 1, 10));
        state.finish(BattleOutcome.ENEMY_WIN);
        // 破阵踏不是亡者最后一条正扣血 → 无关联死亡 → 回⑤ A 群伤 ≥2
        assertThat(attribute(state)).isEqualTo("破阵踏打中2人，下次让后排离前排至少两格。");
    }

    @Test
    @DisplayName("⑦ 全盾/消散：已施法但无正扣血 →「破阵踏施放后未扣血…」（§1.4）")
    void castWithoutDamage() {
        BattleState state = battle();
        state.record(CombatEvent.cast(100, 10, 1, ThreatPredictor.SKILL_BREAK_STOMP));
        state.record(CombatEvent.hit(100, 10, 1, 0f, false, ThreatPredictor.SKILL_BREAK_STOMP)); // 0 扣血
        state.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(state)).isEqualTo("破阵踏施放后未扣血，下次可先试用相同站位。");
    }

    @Test
    @DisplayName("⑧ 题目敌人在首施法前被击杀：「冰霜巨人在施放破阵踏前倒下…」（§1.4）")
    void killedBeforeFirstCast() {
        BattleState state = battle();
        state.record(CombatEvent.unitDied(50, 10, 1)); // 巨人未施法即倒下
        state.finish(BattleOutcome.PLAYER_WIN);
        assertThat(attribute(state)).isEqualTo("冰霜巨人在施放破阵踏前倒下，下次可试沿用这套开局站位。");
    }

    @Test
    void controlReportUsesActualHostileStatusRecipient() {
        BattleState state = battle();
        // 己方也能施放处决，不能触发题目敌人“施放后未扣血”的结论。
        state.record(CombatEvent.cast(1, 3, 11, ThreatPredictor.SKILL_EXECUTE));
        state.record(CombatEvent.hit(3, 3, 11, 30f, false, ThreatPredictor.SKILL_EXECUTE));
        state.record(CombatEvent.cast(4, 1, 11, "skill_entangle"));
        state.record(CombatEvent.statusApplied(5, 1, 10,
                com.voidvvv.kz_auto_chess_n.data.StatusType.SLOW, 40f, 3f));
        state.finish(BattleOutcome.PLAYER_WIN);
        assertThat(attribute(state)).isEqualTo("己方" + state.getUnitById(1).getTemplate().getName()
                + "对敌方冰霜巨人施加减速。");
    }

    @Test
    @DisplayName("⑨ 通用结果：负局全灭（真清扫后零存活）/ 其它负局 / 胜局沿用（§1.4 末行）")
    void genericResults() {
        // 全灭：玩家 HP 归零 → DeathSystem 真清扫（aliveCount==0 且无关键命中事件）
        BattleState wiped = battle();
        for (BattleUnit unit : wiped.getUnits()) {
            if (unit.getSide() == Side.PLAYER) {
                unit.modifyHp(-unit.getCurrentHp());
            }
        }
        com.voidvvv.kz_auto_chess_n.systems.DamagePipeline pipeline =
                new com.voidvvv.kz_auto_chess_n.systems.DamagePipeline(
                        new com.voidvvv.kz_auto_chess_n.systems.CastTrigger() {
                            @Override
                            public void tryCast(BattleState battleState, BattleUnit caster) {
                                // 归因夹具不注入就地施法
                            }
                        });
        new com.voidvvv.kz_auto_chess_n.systems.DeathSystem(
                new TargetingSystem(), pipeline,
                new com.voidvvv.kz_auto_chess_n.systems.StatusSystem(pipeline)).sweep(wiped);
        assertThat(wiped.aliveCount(Side.PLAYER)).isZero();
        wiped.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(wiped)).isEqualTo("己方" + wiped.getUnits().get(0).getTemplate().getName() + "最先阵亡。");

        BattleState survived = battle();
        survived.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(survived)).isEqualTo("本场战败，下次先把耐打的棋子放到敌人接近的一侧。");

        BattleState won = battle();
        won.finish(BattleOutcome.PLAYER_WIN);
        assertThat(attribute(won)).isEqualTo("己方剩3枚棋子，下次可先沿用这套站位。");
    }

    @Test
    @DisplayName("倒地/复活/放逐/消散/拦截不参与归因（无 UNIT_DIED → 不触发关联死亡）")
    void transientStatesNeverAttributed() {
        BattleState state = battle();
        recordStomp(state, 100, new int[]{1});
        state.record(CombatEvent.unitDowned(100, 1));
        state.record(CombatEvent.unitRevived(120, 1));
        state.record(CombatEvent.projectileFizzled(120, 10, 1));
        state.record(CombatEvent.projectileBlocked(120, 10, 1));
        state.record(CombatEvent.unitBanished(120, 2));
        state.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(state)).isEqualTo("破阵踏只打中兽人战士，下次可让其他棋子继续隔两格支援。");
    }

    @Test
    @DisplayName("同类多条候选：A 命中人数多者优先于 B（§1.4 同类排序键）")
    void multiCandidateOrdering() {
        BattleState state = battle();
        recordStomp(state, 100, new int[]{1, 2});       // A：2 人
        recordExecute(state, 100, 3, 3);                // B：1 人
        state.finish(BattleOutcome.ENEMY_WIN);
        assertThat(attribute(state)).isEqualTo("破阵踏打中2人，下次让后排离前排至少两格。");
    }
}
