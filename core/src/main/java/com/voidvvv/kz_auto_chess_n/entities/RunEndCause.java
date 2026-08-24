package com.voidvvv.kz_auto_chess_n.entities;

/** RUN_END 成因（RunEndPanel 文案区分；GDD §2.1 胜利条件 / 放弃远征 / §2.2 第 3 败终局） */
public enum RunEndCause {
    /** 击败第 25 轮最终 Boss（通关） */
    COMPLETED,
    /** 暂停菜单放弃远征（AbandonRun） */
    ABANDONED,
    /** 本轮第 3 次战败（机会耗尽，GDD §2.2 3 次机会制；熟练度同 ABANDONED = 轮×3） */
    DEFEATED
}
