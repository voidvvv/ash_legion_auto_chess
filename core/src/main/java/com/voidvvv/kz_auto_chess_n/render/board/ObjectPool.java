package com.voidvvv.kz_auto_chess_n.render.board;

import java.util.ArrayList;
import java.util.List;

/**
 * 泛型对象池（render §十性能预算：飘字/特效共用；渲染段零分配）。
 * 非线程安全（GL 线程单消费者）。
 */
public final class ObjectPool<T> {

    /** 实例工厂（池空时新建） */
    public interface Factory<T> {
        T create();
    }

    private final Factory<T> factory;
    private final List<T> free = new ArrayList<T>();

    public ObjectPool(Factory<T> factory) {
        this.factory = factory;
    }

    public T obtain() {
        if (free.isEmpty()) {
            return factory.create();
        }
        return free.remove(free.size() - 1);
    }

    public void free(T obj) {
        if (obj != null) {
            free.add(obj);
        }
    }

    /** 空闲实例数（观测/测试用） */
    public int freeCount() {
        return free.size();
    }
}
