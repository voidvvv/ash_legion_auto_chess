package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.DeathrattleKind;
import com.voidvvv.kz_auto_chess_n.data.DeathrattleSpec;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.state;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.tpl;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 两遍式级联清扫测试（C2/C9，CP14）：同批 DIED 连续、载荷按 id 序、
 * 亡语致死并入下一轮（级联收敛）、攻击快照先于清状态、killer 归因透传。
 */
class DeathSystemTest {

    private static final class Rig {
        final DamagePipeline pipeline = new DamagePipeline(new CastTrigger() {
            @Override
            public void tryCast(BattleState state, BattleUnit caster) {
            }
        });
        final StatusSystem statuses = new StatusSystem(pipeline);
        final DeathSystem death = new DeathSystem(new TargetingSystem(), pipeline, statuses);
    }

    /** 带 AREA 亡语的模板（快照 10 × ratio；ratio 10 = 100 真伤一击必杀，1 = 15 观察精确值） */
    private static UnitData areaRattler(String id, int radius, float ratio) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 0, 1f, 1, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_melee", false,
                new DeathrattleSpec(DeathrattleKind.AREA_TRUE_DAMAGE, ratio, radius,
                        null, 0f, 0f, 0f, 0f), null);
    }

    @Test
    @DisplayName("同 tick 双死（互灭）：两条 UNIT_DIED 同 tick 均在清扫内、killer 互相归因")
    void mutualKillSameTickBothRecorded() {
        Rig rig = new Rig();
        BattleUnit p = unit(1, Side.PLAYER, tpl("p"), 2, 4);
        BattleUnit e = unit(2, Side.ENEMY, tpl("e"), 2, 2);
        BattleState state = state(p, e);
        p.setLastDamagerId(2);
        e.setLastDamagerId(1);
        p.modifyHp(-999f);
        e.modifyHp(-999f);

        rig.death.sweep(state);

        List<CombatEvent.Type> types = new ArrayList<CombatEvent.Type>();
        for (CombatEvent event : state.getEvents()) {
            types.add(event.getType());
        }
        assertThat(types).containsExactly(CombatEvent.Type.UNIT_DIED, CombatEvent.Type.UNIT_DIED);
        assertThat(state.getEvents().get(0).getSourceId()).isEqualTo(1); // id 序
        assertThat(state.getEvents().get(0).getKillerId()).isEqualTo(2);
        assertThat(state.getEvents().get(1).getSourceId()).isEqualTo(2);
        assertThat(state.getEvents().get(1).getKillerId()).isEqualTo(1);
        assertThat(state.getEvents().get(0).getTick()).isEqualTo(state.getEvents().get(1).getTick());
        assertThat(p.isCleaned()).isTrue();
        assertThat(e.isCleaned()).isTrue();
        assertThat(state.unitAt(2, 4)).isNull();
        assertThat(state.unitAt(2, 2)).isNull();
    }

    @Test
    @DisplayName("级联收敛：A 亡语炸死 B、B 亡语炸死 D——一次 sweep 三级级联、事件序 DIED/载荷交替、亡语致死归因亡者")
    void deathrattleChainCascadesWithinOneSweep() {
        Rig rig = new Rig();
        BattleUnit a = unit(1, Side.PLAYER, areaRattler("a", 2, 10f), 2, 4); // 尸体 (2,4) 半径 2
        BattleUnit b = unit(2, Side.ENEMY, areaRattler("b", 3, 10f), 2, 2);  // 尸体 (2,2) 半径 3
        BattleUnit d = unit(3, Side.PLAYER, tpl("d"), 2, 5);             // 距 b 尸体 3（在 b 半径内）
        BattleState state = state(a, b, d);
        a.setLastDamagerId(2); // killer 归因观察
        a.modifyHp(-999f);     // 只有 A 先死

        rig.death.sweep(state);

        List<CombatEvent.Type> types = new ArrayList<CombatEvent.Type>();
        for (CombatEvent event : state.getEvents()) {
            types.add(event.getType());
        }
        // 两遍式：每轮 markBatch 先发 DIED，再结算载荷 → DIED,HIT,DIED,HIT,DIED
        assertThat(types).containsExactly(
                CombatEvent.Type.UNIT_DIED, CombatEvent.Type.HIT,
                CombatEvent.Type.UNIT_DIED, CombatEvent.Type.HIT,
                CombatEvent.Type.UNIT_DIED);
        assertThat(state.getEvents().get(0).getSourceId()).isEqualTo(1);
        assertThat(state.getEvents().get(1).getTargetId()).isEqualTo(2);  // A 的载荷 → B
        assertThat(state.getEvents().get(2).getSourceId()).isEqualTo(2);  // B 并入下一轮
        assertThat(state.getEvents().get(3).getTargetId()).isEqualTo(3);  // B 的载荷 → D
        assertThat(state.getEvents().get(4).getSourceId()).isEqualTo(3);
        assertThat(state.getEvents().get(4).getKillerId()).isEqualTo(2);  // D 的死因归因 B（亡语致死）
        assertThat(a.isCleaned()).isTrue();
        assertThat(b.isCleaned()).isTrue();
        assertThat(d.isCleaned()).isTrue();
        int tick = state.getEvents().get(0).getTick();
        for (CombatEvent event : state.getEvents()) {
            assertThat(event.getTick()).isEqualTo(tick); // 同一次 sweep 同 tick
        }
    }

    @Test
    @DisplayName("攻击快照先于清状态：ATK_UP +50% 的亡者载荷按 15 结算（死后状态清空不影响）")
    void attackSnapshotTakenBeforeStatusClear() {
        Rig rig = new Rig();
        BattleUnit a = unit(1, Side.PLAYER, areaRattler("a2", 1, 1f), 2, 4);
        a.addStatus(new ActiveStatus(StatusType.ATK_UP, -1, 50f, 5f)); // atk 10 → 15
        BattleUnit b = unit(2, Side.ENEMY, tpl("b2"), 2, 3);           // 距尸体 (2,4) 为 1，半径内
        BattleState state = state(a, b);
        a.modifyHp(-999f);

        rig.death.sweep(state);

        assertThat(a.getStatuses()).isEmpty();                          // markCleaned 清状态
        assertThat(b.getCurrentHp()).isCloseTo(85f, within(1e-6f));     // 100 − 15×1（快照含 ATK_UP）
    }

    @Test
    @DisplayName("无归因死亡（DOT 来源先亡）：UNIT_DIED killer=-1")
    void unattributedDeathCarriesMinusOneKiller() {
        Rig rig = new Rig();
        BattleUnit p = unit(1, Side.PLAYER, tpl("p2"), 2, 4);
        BattleUnit e = unit(2, Side.ENEMY, tpl("e2"), 2, 2);
        BattleState state = state(p, e);
        p.modifyHp(-999f); // lastDamagerId 未记 → -1

        rig.death.sweep(state);

        assertThat(state.getEvents().get(0).getKillerId()).isEqualTo(-1);
    }
}
