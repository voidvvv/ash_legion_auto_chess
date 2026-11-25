package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver.Layout;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver.Session;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §1.1 探测表三种子复现（品质改造批3，真实命令路径重测）：A 聚拢→分散的首个破阵踏命中目标
 * 2→1，B 祭司在后→战士在后的首个处决命中对象祭司→战士，并记录胜负。事件口径与 §1.4 同式：
 * A 取首个施法同 tick 同源同技能正扣血命中去重数目标；B 以单目标 HIT 为证据（HOMING 追踪落点，
 * 不把施法时锁定目标当作已命中）。模板映射按 battle id → 模板（id 由 startBattle 重发）。
 */
@DisplayName("三种子命中指标复现：A 2→1 / B 祭司→战士（品质改造 §1.1）")
class ThreatProbeTest {

    private static final long[] SEEDS = {42L, 43L, 44L};
    private static GameData data;

    @BeforeAll
    static void loadRealData() {
        data = PaiDuelDriver.realData();
    }

    @Test
    @DisplayName("A 破阵踏：三种子首个施法命中目标数 聚拢 = 2 → 分散 = 1（拉开站位有效）")
    void breakStompTargetsCollapseFromTwoToOne() {
        for (long seed : SEEDS) {
            Session clustered = PaiDuelDriver.openLayout(data, Layout.A_CLUSTER, seed);
            clustered.battleToEnd();
            assertThat(firstKeyedHitTargets(clustered, ThreatPredictor.SKILL_BREAK_STOMP))
                    .as("A 聚拢 seed %d 首个破阵踏命中目标数", seed).isEqualTo(2);

            Session scattered = PaiDuelDriver.openLayout(data, Layout.A_SCATTER, seed);
            scattered.battleToEnd();
            assertThat(firstKeyedHitTargets(scattered, ThreatPredictor.SKILL_BREAK_STOMP))
                    .as("A 分散 seed %d 首个破阵踏命中目标数", seed).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("A 胜负记录：聚拢 负/负/胜，分散 胜/胜/胜（种子 42/43/44）")
    void breakStompOutcomesRecorded() {
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            Session clustered = PaiDuelDriver.openLayout(data, Layout.A_CLUSTER, seed);
            clustered.battleToEnd();
            BattleOutcome clusteredOutcome = clustered.lastBattle().getOutcome();
            Session scattered = PaiDuelDriver.openLayout(data, Layout.A_SCATTER, seed);
            scattered.battleToEnd();
            BattleOutcome scatteredOutcome = scattered.lastBattle().getOutcome();
            boolean clusteredWins = i == SEEDS.length - 1; // 探测表：聚拢 负/负/胜
            assertThat(clusteredOutcome == BattleOutcome.PLAYER_WIN).as("A 聚拢 seed %d", seed)
                    .isEqualTo(clusteredWins);
            assertThat(scatteredOutcome).as("A 分散 seed %d", seed).isEqualTo(BattleOutcome.PLAYER_WIN);
        }
    }

    @Test
    @DisplayName("B 处决：三种子首个命中对象 祭司在后 = 祭司 → 战士在后 = 战士（诱饵换位有效）")
    void executeVictimSwapsFromPriestToWarrior() {
        for (long seed : SEEDS) {
            Session priestBack = PaiDuelDriver.openLayout(data, Layout.B_PRIEST_BACK, seed);
            priestBack.battleToEnd();
            assertThat(firstExecuteVictim(priestBack)).as("B 祭司在后 seed %d 首个处决命中对象", seed)
                    .isEqualTo(PaiTestScript.M);

            Session warriorBack = PaiDuelDriver.openLayout(data, Layout.B_WARRIOR_BACK, seed);
            warriorBack.battleToEnd();
            assertThat(firstExecuteVictim(warriorBack)).as("B 战士在后 seed %d 首个处决命中对象", seed)
                    .isEqualTo(PaiTestScript.W);
        }
    }

    @Test
    @DisplayName("B 胜负记录：两种站位三种子均胜（§1.1 探测表）")
    void executeOutcomesRecorded() {
        for (long seed : SEEDS) {
            for (Layout layout : new Layout[]{Layout.B_PRIEST_BACK, Layout.B_WARRIOR_BACK}) {
                Session session = PaiDuelDriver.openLayout(data, layout, seed);
                session.battleToEnd();
                assertThat(session.lastBattle().getOutcome()).as("%s seed %d", layout, seed)
                        .isEqualTo(BattleOutcome.PLAYER_WIN);
            }
        }
    }

    @Test
    @DisplayName("首个关键施法 tick 与首个正扣血命中对象逐种子记录（§1.1 验证记录字段）")
    void firstCastTickAndHitObjectRecorded() {
        for (long seed : SEEDS) {
            Session a = PaiDuelDriver.openLayout(data, Layout.A_SCATTER, seed);
            a.battleToEnd();
            CombatEvent cast = firstCast(a, ThreatPredictor.SKILL_BREAK_STOMP);
            assertThat(cast).as("A seed %d 首个破阵踏施法", seed).isNotNull();
            assertThat(cast.getTick()).as("A seed %d 首个破阵踏 tick", seed).isGreaterThan(0);
            CombatEvent hit = firstKeyedHit(a, ThreatPredictor.SKILL_BREAK_STOMP);
            assertThat(hit).as("A seed %d 首个破阵踏命中", seed).isNotNull();
            assertThat(hit.getTick()).isEqualTo(cast.getTick()); // MELEE_INSTANT 即时结算
            assertThat(hit.getAmount()).isGreaterThan(0f);
        }
    }

    /** 首个关键技施法的同 tick 正扣血命中去重目标数（A 口径——§1.4 同式） */
    private static int firstKeyedHitTargets(Session session, String skillId) {
        CombatEvent cast = firstCast(session, skillId);
        if (cast == null) {
            return 0;
        }
        Set<Integer> targets = new HashSet<Integer>();
        for (CombatEvent event : session.events()) {
            if (event.getTick() > cast.getTick()) {
                break; // 首个技能窗口结束
            }
            if (event.getType() == CombatEvent.Type.HIT && skillId.equals(event.getSkillId())
                    && event.getTick() == cast.getTick() && event.getAmount() > 0f) {
                targets.add(event.getTargetId());
            }
        }
        return targets.size();
    }

    /** 首个关键技施法事件（无则 null） */
    private static CombatEvent firstCast(Session session, String skillId) {
        for (CombatEvent event : session.events()) {
            if (event.getType() == CombatEvent.Type.CAST && skillId.equals(event.getSkillId())) {
                return event;
            }
        }
        return null;
    }

    /** 首个关键技正扣血命中事件（无则 null） */
    private static CombatEvent firstKeyedHit(Session session, String skillId) {
        for (CombatEvent event : session.events()) {
            if (event.getType() == CombatEvent.Type.HIT && skillId.equals(event.getSkillId())
                    && event.getAmount() > 0f) {
                return event;
            }
        }
        return null;
    }

    /** 首个处决命中对象模板 id（B 口径：以 HIT 证据为准，HOMING 追踪落点） */
    private static String firstExecuteVictim(Session session) {
        CombatEvent hit = firstKeyedHit(session, ThreatPredictor.SKILL_EXECUTE);
        if (hit == null) {
            return null;
        }
        return session.lastBattle().getUnitById(hit.getTargetId()).getTemplate().getId();
    }
}
