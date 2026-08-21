package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.state;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.tpl;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 移动系统测试（battle §四贪婪步）：全场 6×7 可通行可停留（口径 #1）；
 * 4 邻空格取到目标曼哈顿最小者，平局 上>下>左>右；无递减空格等待；
 * 同 tick 抢格由行动序天然解决（先行动者先占）。
 */
class MovementSystemTest {

    private static final MovementSystem SYSTEM = new MovementSystem();

    @Test
    @DisplayName("朝目标贪婪步：玩家单位从 (2,4) 向 (2,0) 走一格到 (2,3)")
    void greedyStepTowardTarget() {
        BattleUnit mover = unit(1, Side.PLAYER, tpl("p"), 2, 4);
        BattleUnit target = unit(2, Side.ENEMY, tpl("e"), 2, 0);
        BattleState state = state(mover, target);
        assertThat(SYSTEM.tryStep(state, mover, target)).isTrue();
        assertThat(mover.getGridX()).isEqualTo(2);
        assertThat(mover.getGridY()).isEqualTo(3);
    }

    @Test
    @DisplayName("平局顺序 上>下>左>右：下与左等距最优时选下（(2,4)→(2,5) 而非 (1,4)）")
    void tiePrefersDownOverLeft() {
        BattleUnit mover = unit(1, Side.PLAYER, tpl("p"), 2, 4);
        BattleUnit target = unit(2, Side.ENEMY, tpl("e"), 0, 6);
        BattleState state = state(mover, target);
        // 距离：当前 4；上(2,3)=5、下(2,5)=3、左(1,4)=3、右(3,4)=5 → 下与左并列最优，取下
        assertThat(SYSTEM.tryStep(state, mover, target)).isTrue();
        assertThat(mover.getGridX()).isEqualTo(2);
        assertThat(mover.getGridY()).isEqualTo(5);
    }

    @Test
    @DisplayName("平局顺序 上>左：上与左等距最优时选上（(2,5)→(2,4) 而非 (1,5)）")
    void tiePrefersUpOverLeft() {
        BattleUnit mover = unit(1, Side.PLAYER, tpl("p"), 2, 5);
        BattleUnit target = unit(2, Side.ENEMY, tpl("e"), 0, 3);
        BattleState state = state(mover, target);
        // 距离：当前 4；上(2,4)=3、下(2,6)=5、左(1,5)=3、右(3,5)=5 → 上与左并列最优，取上
        assertThat(SYSTEM.tryStep(state, mover, target)).isTrue();
        assertThat(mover.getGridX()).isEqualTo(2);
        assertThat(mover.getGridY()).isEqualTo(4);
    }

    @Test
    @DisplayName("穿缓冲行第 3 行（口径 #1）并停留对方布阵区")
    void canCrossBufferRowAndStayInEnemyZone() {
        BattleUnit mover = unit(1, Side.PLAYER, tpl("p"), 2, 4);
        BattleUnit target = unit(2, Side.ENEMY, tpl("e"), 2, 0);
        BattleState state = state(mover, target);
        // 连续走 3 步：y 4→3（缓冲行）→2（敌区）→1（近战接敌位 d=1）
        int[] expectedY = {3, 2, 1};
        for (int y : expectedY) {
            assertThat(SYSTEM.tryStep(state, mover, target)).isTrue();
            assertThat(mover.getGridY()).isEqualTo(y);
        }
        // 目标格 (2,0) 被目标占据，不可再进 → 等待并停留在对方布阵区
        assertThat(SYSTEM.tryStep(state, mover, target)).isFalse();
        assertThat(mover.getGridY()).isEqualTo(1);
        assertThat(state.unitAt(2, 1)).isSameAs(mover);
        assertThat(state.unitAt(2, 4)).isNull();
    }

    @Test
    @DisplayName("四邻全被占 → 等待不动（返回 false、坐标不变）")
    void waitsWhenAllNeighborsOccupied() {
        BattleUnit mover = unit(1, Side.PLAYER, tpl("p"), 2, 4);
        BattleUnit target = unit(2, Side.ENEMY, tpl("e"), 2, 0);
        BattleUnit blockerUp = unit(3, Side.PLAYER, tpl("b1"), 2, 3);
        BattleUnit blockerDown = unit(4, Side.PLAYER, tpl("b2"), 2, 5);
        BattleUnit blockerLeft = unit(5, Side.PLAYER, tpl("b3"), 1, 4);
        BattleUnit blockerRight = unit(6, Side.PLAYER, tpl("b4"), 3, 4);
        BattleState state = state(mover, target, blockerUp, blockerDown, blockerLeft, blockerRight);
        assertThat(SYSTEM.tryStep(state, mover, target)).isFalse();
        assertThat(mover.getGridX()).isEqualTo(2);
        assertThat(mover.getGridY()).isEqualTo(4);
    }

    @Test
    @DisplayName("移动方向不增距即等待：四邻距离全 ≥ 当前")
    void waitsWhenNoNeighborReducesDistance() {
        // 目标在正上紧邻一格（当前 d=1）：上 (2,3) 为目标占据不可通行，
        // 其余三邻（下/左/右）距离均为 2 > 1 → 等待
        BattleUnit mover = unit(1, Side.PLAYER, tpl("p"), 2, 4);
        BattleUnit target = unit(2, Side.ENEMY, tpl("e"), 2, 3);
        BattleState state = state(mover, target);
        assertThat(SYSTEM.tryStep(state, mover, target)).isFalse();
        assertThat(mover.getGridY()).isEqualTo(4);
    }

    @Test
    @DisplayName("grid 记账同步：旧格腾空、新格可查")
    void gridBookkeepingSynced() {
        BattleUnit mover = unit(1, Side.PLAYER, tpl("p"), 2, 4);
        BattleUnit target = unit(2, Side.ENEMY, tpl("e"), 5, 0);
        BattleState state = state(mover, target);
        SYSTEM.tryStep(state, mover, target); // 最优 (3,4) 或 (2,3)：dx=3,dy=4 → 右(3,4)d=6、上(2,3)d=6 → 上
        assertThat(state.unitAt(2, 4)).isNull();
        assertThat(state.unitAt(mover.getGridX(), mover.getGridY())).isSameAs(mover);
    }

    @Test
    @DisplayName("先行动者占格后后者改选（同 tick 抢格由行动序解决）")
    void firstMoverTakesCellSecondAdapts() {
        BattleUnit first = unit(1, Side.PLAYER, tpl("p1"), 2, 4);
        BattleUnit second = unit(2, Side.PLAYER, tpl("p2"), 3, 4);
        BattleUnit target = unit(3, Side.ENEMY, tpl("e"), 2, 0);
        BattleState state = state(first, second, target);
        // first 最优：上 (2,3) d=3；second 到 (2,0) 的最优也是 (2,3)（被占）→ 改选 (3,3) d=4
        assertThat(SYSTEM.tryStep(state, first, target)).isTrue();
        assertThat(first.getGridX()).isEqualTo(2);
        assertThat(first.getGridY()).isEqualTo(3);
        assertThat(SYSTEM.tryStep(state, second, target)).isTrue();
        assertThat(second.getGridX()).isEqualTo(3);
        assertThat(second.getGridY()).isEqualTo(3);
    }

    @Test
    @DisplayName("连续多步逼近：路径单调减距直至接敌（d=1 后等待）")
    void repeatedStepsConverge() {
        BattleUnit mover = unit(1, Side.PLAYER, tpl("p"), 5, 6);
        BattleUnit target = unit(2, Side.ENEMY, tpl("e"), 0, 0);
        BattleState state = state(mover, target);
        int distance = 11; // 5 + 6
        while (distance > 1) {
            assertThat(SYSTEM.tryStep(state, mover, target)).isTrue();
            int newDistance = Math.abs(mover.getGridX() - target.getGridX())
                    + Math.abs(mover.getGridY() - target.getGridY());
            assertThat(newDistance).isLessThan(distance);
            distance = newDistance;
        }
        // 接敌位：目标自身格不可进，等待
        assertThat(SYSTEM.tryStep(state, mover, target)).isFalse();
        assertThat(distance).isEqualTo(1);
    }

    @Test
    @DisplayName("tryStep 不消耗移动计时器（消耗归 BattleSystem 行动链）")
    void tryStepDoesNotConsumeTimer() {
        BattleUnit mover = unit(1, Side.PLAYER, tpl("p"), 2, 4);
        BattleUnit target = unit(2, Side.ENEMY, tpl("e"), 2, 0);
        BattleState state = state(mover, target);
        float timerBefore = mover.getMoveTimer();
        SYSTEM.tryStep(state, mover, target);
        assertThat(mover.getMoveTimer()).isEqualTo(timerBefore);
    }
}
