package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;

/**
 * 熟练度结算纯函数接口（GDD §8.1：通关 +60 + 每已达 1 轮 +3；放弃远征同口径按已达轮数——GDD §2.1）。
 * 产出为纯模拟态（RunState.masteryAwarded）；档案入账归 ProfileService.settle（Phase 6，
 * Screen 观察触发——裁决 D11），本接口不做任何 IO。
 */
@FunctionalInterface
public interface MasteryCalculator {

    int settle(RunEndCause cause, int roundsReached);

    /** GDD 基线口径（裁决 D3）：COMPLETED = 通关加成 + 轮数×3；ABANDONED = 轮数×3 */
    MasteryCalculator GDD_BASIC = new MasteryCalculator() {
        @Override
        public int settle(RunEndCause cause, int roundsReached) {
            if (cause == RunEndCause.COMPLETED) {
                return GameBalance.MASTERY_COMPLETE_BONUS
                        + roundsReached * GameBalance.MASTERY_EXP_PER_ROUND;
            }
            return roundsReached * GameBalance.MASTERY_EXP_PER_ROUND;
        }
    };
}
