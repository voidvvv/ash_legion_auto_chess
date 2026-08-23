package com.voidvvv.kz_auto_chess_n.entities;

/**
 * 发号器接口（Phase 3 Q1 裁决的轻量占位）：为战斗实例分配单一 int id 空间内的唯一 id
 * （architecture §2.2）。Phase 5 接入命令系统时归 {@code RunState} 持有。
 */
public interface IdIssuer {

    /** 发出下一个唯一 id（实现保证同实例内严格不重复） */
    int nextId();

    /** 下一待发号（快照捕获用；不消耗） */
    int peekNext();
}
