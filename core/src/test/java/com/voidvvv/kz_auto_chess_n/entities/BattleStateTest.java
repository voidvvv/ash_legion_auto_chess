package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffect;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.systems.SynergySnapshot;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 战斗状态 BattleState 测试：棋盘记账 / 事件流 / tick 计数 / 终局与存活查询。
 */
class BattleStateTest {

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk", false);
    }

    private static SkillData skill() {
        return new SkillData("sk", "夹具技", "", SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                Collections.singletonList(new SkillEffect(SkillEffectType.DAMAGE, 2f, null, null)));
    }

    private static BattleUnit unit(int id, Side side) {
        return new BattleUnit(id, tpl("u" + id), 1, side, skill(),
                new BattleStats(100f, 10f, 5f, 1f, 1f, 1f, 0f, 100f, 0f));
    }

    private static BattleState state(BattleUnit... units) {
        return new BattleState(Arrays.asList(units), new RandomGenerator(42L),
                SynergySnapshot.EMPTY, SynergySnapshot.EMPTY);
    }

    @Test
    @DisplayName("placeUnit/unitAt 记账一致：落格后可查、单位坐标同步")
    void placeUnitBookkeeping() {
        BattleUnit a = unit(1, Side.PLAYER);
        BattleUnit b = unit(2, Side.ENEMY);
        BattleState state = state(a, b);
        state.placeUnit(a, 2, 5);
        state.placeUnit(b, 3, 0);
        assertThat(state.unitAt(2, 5)).isSameAs(a);
        assertThat(state.unitAt(3, 0)).isSameAs(b);
        assertThat(a.getGridX()).isEqualTo(2);
        assertThat(a.getGridY()).isEqualTo(5);
        assertThat(state.getUnits()).containsExactly(a, b); // 构造序 = id 升序
    }

    @Test
    @DisplayName("placeUnit 到占用格抛 IllegalStateException、越界抛 IllegalArgumentException")
    void placeUnitValidates() {
        BattleUnit a = unit(1, Side.PLAYER);
        BattleUnit b = unit(2, Side.PLAYER);
        BattleState state = state(a, b);
        state.placeUnit(a, 2, 5);
        assertThatThrownBy(() -> state.placeUnit(b, 2, 5))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> state.placeUnit(b, 6, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> state.placeUnit(b, 2, 7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("removeFromGrid 腾格：unitAt 变 null")
    void removeFromGridFreesCell() {
        BattleUnit a = unit(1, Side.PLAYER);
        BattleState state = state(a);
        state.placeUnit(a, 2, 5);
        state.removeFromGrid(a);
        assertThat(state.unitAt(2, 5)).isNull();
    }

    @Test
    @DisplayName("events 视图不可变且 record 追加生效")
    void eventsViewAppendOnly() {
        BattleUnit a = unit(1, Side.PLAYER);
        BattleState state = state(a);
        assertThat(state.getEvents()).isEmpty();
        state.record(CombatEvent.unitDied(3, 1));
        assertThat(state.getEvents()).hasSize(1);
        assertThat(state.getEvents().get(0).getType()).isEqualTo(CombatEvent.Type.UNIT_DIED);
        assertThatThrownBy(() -> state.getEvents().remove(0))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("beginTick 计数：tick 递增、elapsed 按 LOGIC_STEP 累计")
    void beginTickAdvancesClock() {
        BattleState state = state(unit(1, Side.PLAYER));
        assertThat(state.getTick()).isEqualTo(0);
        assertThat(state.getElapsed()).isEqualTo(0f);
        state.beginTick();
        state.beginTick();
        assertThat(state.getTick()).isEqualTo(2);
        assertThat(state.getElapsed()).isCloseTo(2 * GameBalance.LOGIC_STEP, within(1e-6f));
    }

    @Test
    @DisplayName("finish 置 outcome 后 isOver；aliveCount 按侧过滤清扫单位")
    void finishAndAliveQueries() {
        BattleUnit a = unit(1, Side.PLAYER);
        BattleUnit b = unit(2, Side.ENEMY);
        BattleState state = state(a, b);
        state.placeUnit(a, 0, 4);
        state.placeUnit(b, 0, 2);
        assertThat(state.aliveCount(Side.PLAYER)).isEqualTo(1);
        assertThat(state.aliveCount(Side.ENEMY)).isEqualTo(1);
        assertThat(state.getUnitById(2)).isSameAs(b);

        b.markCleaned();
        assertThat(state.aliveUnits(Side.ENEMY)).isEmpty();
        assertThat(state.aliveCount(Side.PLAYER)).isEqualTo(1);

        state.finish(BattleOutcome.PLAYER_WIN);
        assertThat(state.isOver()).isTrue();
        assertThat(state.getOutcome()).isEqualTo(BattleOutcome.PLAYER_WIN);
        assertThat(state.getOutcome().playerWon()).isTrue();
        assertThat(BattleOutcome.TIMEOUT.playerWon()).isFalse(); // 超时 = 玩家判负（口径 #15）
    }
}
