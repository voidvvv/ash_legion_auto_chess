package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 帧事件缓冲（render §4.3；cursor 游标实现，差异声明 #2）。
 *
 * <p>{@code BattleState.getEvents()} 为追加式视图且每次调用新建包装（口径 #12）——
 * attach 时缓存一次、渲染帧零分配；forEachNew 回调消费 {@code [cursor, size)} 新段，
 * cursor 只前进不回退（逻辑帧 ↔ 渲染帧严格对应、不重播）。
 */
public final class EventInbox {
    private List<CombatEvent> events; // attach 缓存的视图；null = 未附着
    private int cursor;

    /** 附着战斗状态（缓存视图一次；重置 cursor 从头消费） */
    public void attach(BattleState state) {
        this.events = Objects.requireNonNull(state, "state 不能为 null").getEvents();
        this.cursor = 0;
    }

    public void detach() {
        this.events = null;
        this.cursor = 0;
    }

    /** 消费新事件段（consumer 为长持有字段引用，渲染段零分配） */
    public void forEachNew(Consumer<CombatEvent> consumer) {
        if (events == null) {
            return;
        }
        while (cursor < events.size()) {
            consumer.accept(events.get(cursor));
            cursor++;
        }
    }
}
