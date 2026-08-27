package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MasteryCalculator DEFEATED 口径测试（机会制 CP7，GDD §8.1）：
 * ABANDONED / DEFEATED 同口径 = 轮数×3；COMPLETED = 通关加成 + 轮数×3。
 */
class MasteryCalculatorTest {

    @Test
    @DisplayName("DEFEATED = 轮数×3：settle(DEFEATED, 7) = 21、settle(DEFEATED, 25) = 75")
    void defeatedSettlesRoundsTimesThree() {
        assertThat(MasteryCalculator.GDD_BASIC.settle(RunEndCause.DEFEATED, 7))
                .isEqualTo(21);
        assertThat(MasteryCalculator.GDD_BASIC.settle(RunEndCause.DEFEATED, 25))
                .isEqualTo(75);
    }

    @Test
    @DisplayName("DEFEATED 与 ABANDONED 恒等（同口径回归锚，GDD §8.1）")
    void defeatedEqualsAbandonedForAllRounds() {
        for (int round = 1; round <= GameBalance.TOTAL_ROUNDS; round++) {
            assertThat(MasteryCalculator.GDD_BASIC.settle(RunEndCause.DEFEATED, round))
                    .isEqualTo(MasteryCalculator.GDD_BASIC.settle(RunEndCause.ABANDONED, round));
        }
    }

    @Test
    @DisplayName("COMPLETED = 通关加成 60 + 轮数×3（回归锚，裁决 D3）")
    void completedKeepsBonus() {
        assertThat(MasteryCalculator.GDD_BASIC.settle(RunEndCause.COMPLETED, 25))
                .isEqualTo(GameBalance.MASTERY_COMPLETE_BONUS
                        + 25 * GameBalance.MASTERY_EXP_PER_ROUND);
    }
}
