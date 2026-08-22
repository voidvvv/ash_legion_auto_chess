package com.voidvvv.kz_auto_chess_n.command;

/**
 * 开战命令（Q2 命令集）：无载荷单例——布阵锁定、派生 BattleState、进 BATTLE 期。
 * 零棋子允许开战（门控归 RunFlowSystem）。
 */
public final class StartBattleCommand implements GameCommand {
    public static final StartBattleCommand INSTANCE = new StartBattleCommand();

    private StartBattleCommand() {
    }
}
