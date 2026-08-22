package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;

/**
 * 熟练度结算纯函数接口（GDD §8.1：每已达 1 轮 +3；放弃远征同口径按已达轮数——GDD §2.1）。
 * Phase 5 stub（Q5 裁决）：产出暂存 RunState.masteryAwarded，Phase 6 接档案域持久化。
 */
@FunctionalInterface
public interface MasteryCalculator {

    /** cause 目前不参与基线公式（预留：通关加成/放弃折扣等未来口径） */
    int settle(RunEndCause cause, int roundsReached);

    MasteryCalculator GDD_BASIC = new MasteryCalculator() {
        @Override
        public int settle(RunEndCause cause, int roundsReached) {
            return roundsReached * 3;
        }
    };
}
