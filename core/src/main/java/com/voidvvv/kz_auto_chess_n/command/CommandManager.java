package com.voidvvv.kz_auto_chess_n.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 命令管理器（input §4.1/§5.1）：输入侧入队、固定逻辑 tick 内统一消费。
 *
 * <p>历史以 {@code (tick, cmd)} 二元组记录：tick 为<b>执行时</b>的 {@link RunState#getLogicTick()}
 * （唯一逻辑钟——Phase 4 口径 #11 统一销账，实现口径 #17；回放按执行序重演等价），
 * 与 {@code BattleState.tick} 独立；本期只记录不消费（回放轨 Phase 6+）。
 * {@code onExecuted(cmd, success)} 为通知面板数据源（Phase 5 订阅）。
 */
public final class CommandManager {

    /** (执行 tick, 命令) 二元组：回放轨的最小记录单位（tick 取 RunState 逻辑钟） */
    public static final class StampedCommand {
        private final int tick;
        private final GameCommand command;

        StampedCommand(int tick, GameCommand command) {
            this.tick = tick;
            this.command = Objects.requireNonNull(command, "command 不能为 null");
        }

        public int getTick() { return tick; }
        public GameCommand getCommand() { return command; }
    }

    /** 命令执行通知（成功才送达；cmd 透传） */
    @FunctionalInterface
    public interface CommandExecutedListener {
        void onExecuted(GameCommand command, boolean success);
    }

    private final Queue<GameCommand> commandQueue = new ConcurrentLinkedQueue<GameCommand>();
    private final List<StampedCommand> history = new ArrayList<StampedCommand>();
    private final Map<Class<?>, CommandHandler> handlers = new HashMap<Class<?>, CommandHandler>();
    private final List<CommandExecutedListener> listeners = new ArrayList<CommandExecutedListener>();

    /** 入队（tick 戳改在消费时盖——RunState.getLogicTick 为唯一逻辑钟，实现口径 #17） */
    public void addCommand(GameCommand cmd) {
        Objects.requireNonNull(cmd, "cmd 不能为 null");
        commandQueue.add(cmd);
    }

    /** 丢弃未消费的排队命令（重开新局前清残留——实现口径 #12；已消费历史保留） */
    public void discardPending() {
        commandQueue.clear();
    }

    public void registerHandler(Class<?> type, CommandHandler handler) {
        Objects.requireNonNull(type, "type 不能为 null");
        Objects.requireNonNull(handler, "handler 不能为 null");
        handlers.put(type, handler);
    }

    public void addListener(CommandExecutedListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener 不能为 null"));
    }

    public void removeListener(CommandExecutedListener listener) {
        listeners.remove(listener);
    }

    /**
     * 固定逻辑 tick 内消费全部命令（BattleScreen 调用）。
     * 无 handler 的命令丢弃并记日志（不炸）；handler 返回 false 静默忽略（不通知）；
     * handler 抛异常向上抛（不入 onExecuted，口径 #10）。
     */
    public void executeAll(RunContext ctx) {
        Objects.requireNonNull(ctx, "ctx 不能为 null");
        GameCommand cmd;
        while ((cmd = commandQueue.poll()) != null) {
            history.add(new StampedCommand(ctx.getRunState().getLogicTick(), cmd)); // 消费时盖执行 tick
            CommandHandler handler = handlers.get(cmd.getClass());
            if (handler == null) {
                System.err.println("[CommandManager] 未注册 handler，命令丢弃: " + cmd.getClass().getSimpleName());
                continue;
            }
            if (handler.handle(cmd, ctx)) {
                for (CommandExecutedListener listener : listeners) {
                    listener.onExecuted(cmd, true);
                }
            }
        }
        ctx.getRunState().advanceTick(); // 逻辑钟唯一归属：RunState
    }

    /** 历史（不可变视图；只记录不消费，回放轨 Phase 6+） */
    public List<StampedCommand> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public void clearHistory() {
        history.clear();
    }
}
