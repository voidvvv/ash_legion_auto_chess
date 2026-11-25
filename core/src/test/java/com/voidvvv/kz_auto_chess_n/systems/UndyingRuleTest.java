package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UndyingSpec;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.state;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.tpl;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 不死判定测试（C1/C7，CP17）：首倒转倒地（非真死）、四过滤点、状态时钟冻结、
 * 期满原地复活、二次 HP≤0 真死、防御性就近落格。
 */
class UndyingRuleTest {

    /** 死骑型模板：undying 1.0s / 40% 血 */
    private static UnitData deathKnight() {
        return new UnitData("dk", "夹具死骑", "亡灵", "战士", 3,
                new BaseStats(200, 22, 13, 0.9f, 1, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_melee", false,
                null, new UndyingSpec(1.0f, 0.4f));
    }

    private static BattleUnit knight() {
        return unit(1, Side.PLAYER, deathKnight(), 2, 4);
    }

    private static BattleUnit foe(int id, int x, int y) {
        return unit(id, Side.ENEMY, tpl("foe" + id), x, y);
    }

    @Test
    @DisplayName("C1 首倒分流：HP≤0 undying 单位转倒地——无 UNIT_DIED、有 UNIT_DOWNED、aliveCount 不减、占格维持")
    void firstDownIsNotDeath() {
        BattleUnit dk = knight();
        BattleUnit foe = foe(2, 2, 2);
        BattleState state = state(dk, foe);
        dk.setEnergy(77f); // 能量保留观察

        dk.modifyHp(-999f); // HP→0
        boolean diverted = UndyingRule.tryEnterDowned(state, dk);

        assertThat(diverted).isTrue();
        assertThat(dk.isDowned()).isTrue();
        assertThat(dk.isUndyingUsed()).isTrue();
        assertThat(dk.isCleaned()).isFalse();
        assertThat(dk.isActable()).isFalse();
        assertThat(dk.hasControl()).isTrue(); // C7 ②：倒地 ∨ STUN
        assertThat(state.getEvents())
                .noneMatch(e -> e.getType() == CombatEvent.Type.UNIT_DIED); // 非真死（C1）
        assertThat(state.getEvents())
                .anyMatch(e -> e.getType() == CombatEvent.Type.UNIT_DOWNED);
        assertThat(state.aliveCount(Side.PLAYER)).isEqualTo(1); // ⑤ 口径：未清扫含倒地
        assertThat(state.unitAt(2, 4)).isSameAs(dk);            // 占格维持
        assertThat(dk.getEnergy()).isEqualTo(77f);              // 能量保留（冻结）
    }

    @Test
    @DisplayName("无 undying 模板不分流：tryEnterDowned 恒 false（普通单位照旧走真死路径）")
    void plainUnitNeverDowns() {
        BattleUnit plain = unit(1, Side.PLAYER, tpl("p"), 2, 4);
        BattleUnit foe = foe(2, 2, 2);
        BattleState state = state(plain, foe);
        plain.modifyHp(-999f);
        assertThat(UndyingRule.tryEnterDowned(state, plain)).isFalse();
        assertThat(plain.isDowned()).isFalse();
    }

    @Test
    @DisplayName("C7 ③ 过滤点：倒地者不被索敌（敌人改打他人）、不可被直伤/真伤/治疗")
    void downedFilteredFromTargetingAndDamage() {
        BattleUnit dk = knight();
        BattleUnit mate = unit(3, Side.PLAYER, tpl("mate"), 2, 5);
        BattleUnit foe = foe(2, 2, 2);
        BattleState state = state(dk, mate, foe);
        dk.modifyHp(-999f);
        assertThat(UndyingRule.tryEnterDowned(state, dk)).isTrue();

        TargetingSystem targeting = new TargetingSystem();
        assertThat(targeting.findTarget(state, foe).getId()).isEqualTo(mate.getId()); // 索敌跳过倒地

        DamagePipeline pipeline = new DamagePipeline(new CastTrigger() {
            @Override
            public void tryCast(BattleState s, BattleUnit c) {
            }
        });
        int eventsBefore = state.getEvents().size();
        pipeline.applyDirectHit(state, foe, dk, 50f, 1f, false, 0f, true, null, 0f);
        pipeline.applyTrueDamage(state, foe, dk, 50f);
        pipeline.applyHeal(state, dk, 50f);
        assertThat(dk.getCurrentHp()).isEqualTo(0f); // 不可伤不可疗（三路径全空返回）
        assertThat(state.getEvents()).hasSize(eventsBefore);
    }

    @Test
    @DisplayName("C7 ④ 时钟冻结：挂毒后倒地——DOT 心跳与时长递减全部暂停")
    void statusClockFrozenWhileDowned() {
        BattleUnit dk = knight();
        BattleUnit foe = foe(2, 2, 2);
        BattleState state = state(dk, foe);
        DamagePipeline pipeline = new DamagePipeline(new CastTrigger() {
            @Override
            public void tryCast(BattleState s, BattleUnit c) {
            }
        });
        StatusSystem statusSystem = new StatusSystem(pipeline);
        statusSystem.apply(state, dk, StatusType.POISON, 10f, 5f, foe.getId());

        statusSystem.tickStatuses(state, GameBalance.LOGIC_STEP * 30f); // 倒地前：时钟走动
        ActiveStatus poison = dk.getStatuses().get(0);
        float tickTimerBefore = poison.getTickTimer();
        float remainingBefore = poison.getRemainingTime();
        assertThat(tickTimerBefore).isGreaterThan(0f);

        dk.modifyHp(-999f);
        UndyingRule.tryEnterDowned(state, dk);
        statusSystem.tickStatuses(state, GameBalance.LOGIC_STEP * 30f); // 倒地后：整体跳过
        assertThat(poison.getTickTimer()).isEqualTo(tickTimerBefore);   // 心跳冻结
        assertThat(poison.getRemainingTime()).isEqualTo(remainingBefore); // 时长冻结
    }

    @Test
    @DisplayName("C7 ⑤ 期满复活：1.0s 后原地站起 40% 血 + 能量回满 + UNIT_REVIVED；计时未满不复活")
    void reviveAfterDelay() {
        BattleUnit dk = knight();
        BattleUnit foe = foe(2, 2, 2);
        BattleState state = state(dk, foe);
        dk.modifyHp(-999f);
        UndyingRule.tryEnterDowned(state, dk);

        for (int i = 0; i < 59; i++) { // 59/60 tick：差一步
            dk.advanceDownedTimer(GameBalance.LOGIC_STEP);
            UndyingRule.tryRevive(state, dk);
        }
        assertThat(dk.isDowned()).isTrue(); // 未满不复活
        assertThat(state.getEvents())
                .noneMatch(e -> e.getType() == CombatEvent.Type.UNIT_REVIVED);

        dk.advanceDownedTimer(GameBalance.LOGIC_STEP); // 第 60 tick 到点
        UndyingRule.tryRevive(state, dk);
        assertThat(dk.isDowned()).isFalse();
        assertThat(dk.isActable()).isTrue();
        assertThat(dk.getCurrentHp()).isCloseTo(200f * 0.4f, within(1e-6f)); // 40% 血
        assertThat(dk.getEnergy()).isEqualTo(100f);                          // 能量回满
        assertThat(dk.getGridX()).isEqualTo(2);                               // 原地站起
        assertThat(dk.getGridY()).isEqualTo(4);
        assertThat(state.getEvents())
                .anyMatch(e -> e.getType() == CombatEvent.Type.UNIT_REVIVED);
    }

    @Test
    @DisplayName("C7 ① 二次 HP≤0 真死：undyingUsed 后 tryEnterDowned 恒 false（亡语/名册照常结算）")
    void secondDownIsRealDeath() {
        BattleUnit dk = knight();
        BattleUnit foe = foe(2, 2, 2);
        BattleState state = state(dk, foe);
        dk.modifyHp(-999f);
        assertThat(UndyingRule.tryEnterDowned(state, dk)).isTrue();

        dk.advanceDownedTimer(2f); // 直接耗完
        UndyingRule.tryRevive(state, dk);
        assertThat(dk.isDowned()).isFalse();

        dk.modifyHp(-999f); // 二次倒地 → 不再分流
        assertThat(UndyingRule.tryEnterDowned(state, dk)).isFalse();
        assertThat(state.getEvents())
                .noneMatch(e -> e.getType() == CombatEvent.Type.UNIT_DIED); // 真死归 DeathSystem（CP14 联测）
    }

    @Test
    @DisplayName("防御分支：复活格被占 → 曼哈顿最近空格（平局 x 后 y 序）落位")
    void reviveRelocatesToNearestFreeCell() {
        BattleUnit dk = knight();
        BattleUnit foe = foe(2, 2, 2);
        BattleState state = state(dk, foe);
        dk.modifyHp(-999f);
        UndyingRule.tryEnterDowned(state, dk);

        // 模拟外部占格（防御分支触发条件）：腾出 dk 原格并让 foe 占据
        state.removeFromGrid(dk);
        state.removeFromGrid(foe);
        state.placeUnit(foe, 2, 4);
        dk.advanceDownedTimer(2f);
        UndyingRule.tryRevive(state, dk);

        assertThat(dk.isDowned()).isFalse();
        int expectedDistance = Math.abs(dk.getGridX() - 2) + Math.abs(dk.getGridY() - 4);
        assertThat(expectedDistance).isEqualTo(1); // 最近空格（距原格 1；等距取 x 小 → (1,4)）
        assertThat(dk.getGridX()).isEqualTo(1);
        assertThat(dk.getGridY()).isEqualTo(4);
        assertThat(state.unitAt(1, 4)).isSameAs(dk);
    }
}
