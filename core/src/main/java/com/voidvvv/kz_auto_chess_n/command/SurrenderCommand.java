package com.voidvvv.kz_auto_chess_n.command;

/**
 * 投降命令（Q2 命令集）：无载荷单例——仅 BATTLE 期有效，立即判负
 * （{@code BattleState.finish(ENEMY_WIN)}，幂等）。
 */
public final class SurrenderCommand implements GameCommand {
    public static final SurrenderCommand INSTANCE = new SurrenderCommand();

    private SurrenderCommand() {
    }
}
