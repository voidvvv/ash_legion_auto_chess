package com.voidvvv.kz_auto_chess_n.command;

/**
 * 命令处理器（input §5.2 骨架 + 口径 #10 落定）：
 * 返回 true = 执行成功（{@code onExecuted} 成功信号）；false = 校验不过静默忽略；
 * 抛异常则向上抛（不入 onExecuted）。
 */
@FunctionalInterface
public interface CommandHandler {

    boolean handle(GameCommand cmd, RunContext ctx);
}
