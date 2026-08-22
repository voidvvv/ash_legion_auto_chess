package com.voidvvv.kz_auto_chess_n.entities;

/**
 * 顺序发号默认实现：从 1 起严格递增（测试与控制台用）。
 * 复原构造（快照轨）从指定下一号续发——单一 id 空间跨挂起不断档。
 * 非线程安全——战斗逻辑在单线程固定步内推进（architecture §六）。
 * 发号零 RNG 消耗（口径 #16：固定序确定性的最后一道保险）。
 */
public final class SequentialIdIssuer implements IdIssuer {
    private int next = 1;

    public SequentialIdIssuer() {
    }

    /** 复原构造：从指定下一号续发（≥ 1） */
    public SequentialIdIssuer(int next) {
        if (next < 1) {
            throw new IllegalArgumentException("下一号必须 ≥ 1，实际=" + next);
        }
        this.next = next;
    }

    @Override
    public int nextId() {
        return next++;
    }

    @Override
    public int peekNext() {
        return next;
    }
}
