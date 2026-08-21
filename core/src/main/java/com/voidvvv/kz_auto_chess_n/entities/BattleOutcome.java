package com.voidvvv.kz_auto_chess_n.entities;

/**
 * 战斗结局（battle §二）。TIMEOUT 为独立枚举供 UI 区分文案——
 * 语义上等同玩家判负（GDD §6.4：超时 = 玩家失败，口径 #15）。
 */
public enum BattleOutcome {
    PLAYER_WIN,
    ENEMY_WIN,
    TIMEOUT;

    /** 玩家是否胜利（TIMEOUT 计为失败） */
    public boolean playerWon() {
        return this == PLAYER_WIN;
    }
}
