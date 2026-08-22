package com.voidvvv.kz_auto_chess_n.entities;

/** RUN_END 成因（RunEndPanel 文案区分；GDD §2.1 胜利条件 / 放弃远征） */
public enum RunEndCause {
    /** 击败第 25 轮最终 Boss（通关） */
    COMPLETED,
    /** 暂停菜单放弃远征（AbandonRun） */
    ABANDONED
}
