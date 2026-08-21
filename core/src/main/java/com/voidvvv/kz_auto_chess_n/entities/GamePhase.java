package com.voidvvv.kz_auto_chess_n.entities;

/**
 * 逻辑阶段（architecture §5.1 状态图；命名避开 libGDX {@code Screen} 词义，口径 #1）。
 *
 * <p>本期流转（Q3）：SHOPPING → BATTLE → RESULT（横幅瞬态）→ SHOPPING（round+1）……
 * 第 25 轮战毕 RESULT 后进 RUN_END 终态，可同 seed 重开。
 */
public enum GamePhase {
    /** 备战期：布阵（本期兵源为固定演示名单，经济 Phase 5） */
    SHOPPING,
    /** 战斗期：BattleState 逐逻辑步推进 */
    BATTLE,
    /** 战毕横幅瞬态：点击或数秒后自动回 SHOPPING */
    RESULT,
    /** 终局：25 轮打完，文字 + 重开 */
    RUN_END
}
