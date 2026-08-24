package com.voidvvv.kz_auto_chess_n.entities;

/** 宝箱来源：胜局三选一（VICTORY，RNG roll）/ 败局 1~2 败补给二选一（DEFEAT，公式构造零 RNG——GDD §2.2） */
public enum ChestOrigin {
    VICTORY,
    DEFEAT
}
