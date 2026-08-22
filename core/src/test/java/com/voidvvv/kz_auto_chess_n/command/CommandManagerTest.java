package com.voidvvv.kz_auto_chess_n.command;

import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CommandManager 测试：入队与 tick 戳历史、executeAll 分发、onExecuted 成功信号、
 * 无 handler 丢弃、顺序保证、listener 增删与异常口径（input §5.1/§5.2 + 口径 #10/#11）。
 */
class CommandManagerTest {

    /** 测试用哑命令（区分两类型以验证按 Class 分发） */
    private static final class DummyA implements GameCommand { }

    private static final class DummyB implements GameCommand { }

    private RunContext newContext() {
        return new RunContext(new Player(10),
                new RunState(42L, "scene_forest", new SequentialIdIssuer()),
                BattleTestFixtures.microData(BattleTestFixtures.meleeSkill()),
                new RandomGenerator(42L));
    }

    @Test
    @DisplayName("addCommand 入队并入历史：执行前 tick 戳为 0，命令按序保留")
    void enqueueStampsTickZeroBeforeFirstExecute() {
        CommandManager manager = new CommandManager();
        DummyA a = new DummyA();
        DummyB b = new DummyB();
        manager.addCommand(a);
        manager.addCommand(b);
        List<CommandManager.StampedCommand> history = manager.getHistory();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getTick()).isZero();
        assertThat(history.get(0).getCommand()).isSameAs(a);
        assertThat(history.get(1).getTick()).isZero();
        assertThat(history.get(1).getCommand()).isSameAs(b);
    }

    @Test
    @DisplayName("executeAll 后逻辑钟 +1：其后入队的命令盖新 tick 戳（口径 #11）")
    void logicTickAdvancesPerExecuteAll() {
        CommandManager manager = new CommandManager();
        manager.addCommand(new DummyA());
        manager.executeAll(newContext());
        manager.addCommand(new DummyA());
        List<CommandManager.StampedCommand> history = manager.getHistory();
        assertThat(history.get(0).getTick()).isZero();
        assertThat(history.get(1).getTick()).isEqualTo(1);
    }

    @Test
    @DisplayName("executeAll 按 Class 分发到正确 handler")
    void dispatchesToRegisteredHandlerByType() {
        CommandManager manager = new CommandManager();
        List<GameCommand> seenA = new ArrayList<GameCommand>();
        List<GameCommand> seenB = new ArrayList<GameCommand>();
        manager.registerHandler(DummyA.class, (cmd, ctx) -> {
            seenA.add(cmd);
            return true;
        });
        manager.registerHandler(DummyB.class, (cmd, ctx) -> {
            seenB.add(cmd);
            return true;
        });
        DummyA a = new DummyA();
        DummyB b = new DummyB();
        manager.addCommand(a);
        manager.addCommand(b);
        manager.executeAll(newContext());
        assertThat(seenA).containsExactly(a);
        assertThat(seenB).containsExactly(b);
    }

    @Test
    @DisplayName("handler 返回 true 触发 onExecuted 且命令透传；返回 false 不触发")
    void onExecutedSignalsSuccessOnly() {
        CommandManager manager = new CommandManager();
        List<GameCommand> notified = new ArrayList<GameCommand>();
        manager.addListener((cmd, success) -> notified.add(cmd));
        manager.registerHandler(DummyA.class, (cmd, ctx) -> true);
        manager.registerHandler(DummyB.class, (cmd, ctx) -> false);
        DummyA a = new DummyA();
        DummyB b = new DummyB();
        manager.addCommand(a);
        manager.addCommand(b);
        manager.executeAll(newContext());
        assertThat(notified).containsExactly(a);
    }

    @Test
    @DisplayName("未注册 handler 的命令丢弃不炸（记日志），后续命令照常执行")
    void unknownCommandDroppedWithoutFailure() {
        CommandManager manager = new CommandManager();
        List<GameCommand> seen = new ArrayList<GameCommand>();
        manager.registerHandler(DummyA.class, (cmd, ctx) -> {
            seen.add(cmd);
            return true;
        });
        DummyA a = new DummyA();
        manager.addCommand(new DummyB()); // 无 handler
        manager.addCommand(a);
        manager.executeAll(newContext());
        assertThat(seen).containsExactly(a);
    }

    @Test
    @DisplayName("同 tick 多命令按入队序执行")
    void executesInFifoOrder() {
        CommandManager manager = new CommandManager();
        List<Integer> order = new ArrayList<Integer>();
        manager.registerHandler(DummyA.class, (cmd, ctx) -> {
            order.add(1);
            return true;
        });
        manager.registerHandler(DummyB.class, (cmd, ctx) -> {
            order.add(2);
            return true;
        });
        manager.addCommand(new DummyA());
        manager.addCommand(new DummyB());
        manager.addCommand(new DummyA());
        manager.executeAll(newContext());
        assertThat(order).containsExactly(1, 2, 1);
    }

    @Test
    @DisplayName("listener 可增删：移除后不再收到通知")
    void listenerRemovable() {
        CommandManager manager = new CommandManager();
        List<GameCommand> first = new ArrayList<GameCommand>();
        List<GameCommand> second = new ArrayList<GameCommand>();
        CommandManager.CommandExecutedListener l1 = (cmd, success) -> first.add(cmd);
        CommandManager.CommandExecutedListener l2 = (cmd, success) -> second.add(cmd);
        manager.addListener(l1);
        manager.addListener(l2);
        manager.registerHandler(DummyA.class, (cmd, ctx) -> true);
        DummyA a = new DummyA();
        manager.addCommand(a);
        manager.removeListener(l2);
        manager.executeAll(newContext());
        assertThat(first).containsExactly(a);
        assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("handler 抛异常向上抛（不入 onExecuted，口径 #10）")
    void handlerExceptionPropagates() {
        CommandManager manager = new CommandManager();
        List<GameCommand> notified = new ArrayList<GameCommand>();
        manager.addListener((cmd, success) -> notified.add(cmd));
        manager.registerHandler(DummyA.class, (cmd, ctx) -> {
            throw new IllegalStateException("测试异常");
        });
        manager.addCommand(new DummyA());
        assertThatThrownBy(() -> manager.executeAll(newContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("测试异常");
        assertThat(notified).isEmpty();
    }

    @Test
    @DisplayName("clearHistory 清空历史；逻辑钟计数不受影响")
    void clearHistoryKeepsLogicTick() {
        CommandManager manager = new CommandManager();
        manager.registerHandler(DummyA.class, (cmd, ctx) -> true);
        manager.addCommand(new DummyA());
        manager.executeAll(newContext());
        manager.clearHistory();
        assertThat(manager.getHistory()).isEmpty();
        manager.addCommand(new DummyA());
        assertThat(manager.getHistory().get(0).getTick()).isEqualTo(1);
    }

    @Test
    @DisplayName("getHistory 返回不可变视图（add 抛 UnsupportedOperationException）")
    void historyUnmodifiable() {
        CommandManager manager = new CommandManager();
        manager.addCommand(new DummyA());
        assertThatThrownBy(() -> manager.getHistory().add(
                new CommandManager.StampedCommand(0, new DummyA())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("executeAll 消费后队列为空：重复调用零分发")
    void queueDrainedAfterExecuteAll() {
        CommandManager manager = new CommandManager();
        List<GameCommand> seen = new ArrayList<GameCommand>();
        manager.registerHandler(DummyA.class, (cmd, ctx) -> {
            seen.add(cmd);
            return true;
        });
        manager.addCommand(new DummyA());
        manager.executeAll(newContext());
        manager.executeAll(newContext());
        assertThat(seen).hasSize(1);
    }
}
