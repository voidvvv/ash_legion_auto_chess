package com.voidvvv.kz_auto_chess_n.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 宝箱三选一 offer（不可变；roll 于胜局进 RESULT 时一次性；领取后 RunState.pendingChest 置 null） */
public final class ChestOffer {
    private final int round;
    private final boolean boss;
    private final List<ChestOption> options;

    public ChestOffer(int round, boolean boss, List<ChestOption> options) {
        this.round = round;
        this.boss = boss;
        this.options = Collections.unmodifiableList(new ArrayList<ChestOption>(
                Objects.requireNonNull(options, "options 不能为 null")));
        if (options.size() != 3) {
            throw new IllegalArgumentException("宝箱必须恰有三个选项，实际=" + options.size());
        }
    }

    /** 选项（槽序固定：0=金币 1=经验书 2=装备，实现口径 #1） */
    public ChestOption optionAt(int index) {
        if (index < 0 || index >= options.size()) {
            return null;
        }
        return options.get(index);
    }

    public int getRound() { return round; }
    public boolean isBoss() { return boss; }
    public List<ChestOption> getOptions() { return options; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChestOffer)) {
            return false;
        }
        ChestOffer that = (ChestOffer) o;
        return round == that.round && boss == that.boss && options.equals(that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(round, boss, options);
    }
}
