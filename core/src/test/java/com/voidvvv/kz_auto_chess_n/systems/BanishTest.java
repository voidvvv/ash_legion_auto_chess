package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.state;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.unit;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.tpl;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 放逐原语测试（C10 非死亡移除，v5 CP8）：独立路径——无 UNIT_DIED、有 UNIT_BANISHED、
 * 棋盘格腾空、对已清扫者幂等。
 */
class BanishTest {

    @Test
    @DisplayName("放逐：isCleaned+isBanished、无 UNIT_DIED、有 UNIT_BANISHED、棋盘格腾空")
    void banishRemovesWithoutDeathEvent() {
        BattleUnit victim = unit(2, Side.ENEMY, tpl("t"), 2, 2);
        BattleUnit attacker = unit(1, Side.PLAYER, tpl("a"), 2, 4);
        BattleState state = state(attacker, victim);

        Banish.execute(state, victim);

        assertThat(victim.isCleaned()).isTrue();
        assertThat(victim.isBanished()).isTrue();
        assertThat(state.unitAt(2, 2)).isNull(); // 棋盘格腾空
        assertThat(state.getEvents()).hasSize(1);
        CombatEvent event = state.getEvents().get(0);
        assertThat(event.getType()).isEqualTo(CombatEvent.Type.UNIT_BANISHED);
        assertThat(event.getTargetId()).isEqualTo(2);
        assertThat(event.getSourceId()).isEqualTo(-1);
        assertThat(state.getEvents())
                .noneMatch(e -> e.getType() == CombatEvent.Type.UNIT_DIED); // 非死亡事件（C10）
    }

    @Test
    @DisplayName("幂等：对已清扫（普通死亡）者放逐为空操作；null 安全")
    void banishIsIdempotentOnCleaned() {
        BattleUnit victim = unit(2, Side.ENEMY, tpl("t"), 2, 2);
        BattleState state = state(victim);
        victim.markCleaned(); // 普通死亡路径先行清扫

        Banish.execute(state, victim);
        Banish.execute(state, null); // null 防御

        assertThat(victim.isBanished()).isFalse(); // 未叠加放逐标记
        assertThat(state.getEvents()).isEmpty();   // 零事件零重复
    }

    @Test
    @DisplayName("放逐即时反映在存活计数（aliveCount 减一，⑤ 判定口径）")
    void banishReducesAliveCount() {
        BattleUnit victim = unit(2, Side.ENEMY, tpl("t"), 2, 2);
        BattleUnit other = unit(3, Side.ENEMY, tpl("o"), 3, 2);
        BattleState state = state(victim, other);
        assertThat(state.aliveCount(Side.ENEMY)).isEqualTo(2);

        Banish.execute(state, victim);

        assertThat(state.aliveCount(Side.ENEMY)).isEqualTo(1);
    }
}
