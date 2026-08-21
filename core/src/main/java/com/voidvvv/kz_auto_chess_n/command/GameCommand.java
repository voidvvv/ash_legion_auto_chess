package com.voidvvv.kz_auto_chess_n.command;

/**
 * 命令标记接口（input §4.1）：纯数据载体——禁业务方法（{@code run()}/{@code execute()}），
 * 携带可序列化承诺（回放轨 Phase 6+ 消费 {@code CommandManager} 历史）。
 */
public interface GameCommand {
}
