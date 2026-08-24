package com.voidvvv.kz_auto_chess_n.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 宝箱 offer（不可变；胜局三选一 roll 于进 RESULT 时一次性，败局败箱二选一公式构造零 RNG——GDD §2.2；
 *  领取后 RunState.pendingChest 置 null） */
public final class ChestOffer {
    private final int round;
    private final boolean boss;
    private final ChestOrigin origin;
    private final List<ChestOption> options;

    /** 便捷构造（胜箱，存量调用点兼容） */
    public ChestOffer(int round, boolean boss, List<ChestOption> options) {
        this(round, boss, ChestOrigin.VICTORY, options);
    }

    /** canonical：胜箱 3 选项 / 败箱 2 选项（槽序 0=金币 1=经验书，无装备槽） */
    public ChestOffer(int round, boolean boss, ChestOrigin origin, List<ChestOption> options) {
        this.round = round;
        this.boss = boss;
        this.origin = Objects.requireNonNull(origin, "origin 不能为 null");
        this.options = Collections.unmodifiableList(new ArrayList<ChestOption>(
                Objects.requireNonNull(options, "options 不能为 null")));
        if (options.size() < 2 || options.size() > 3) {
            throw new IllegalArgumentException("宝箱选项必须为 2~3 个，实际=" + options.size());
        }
    }

    /** 选项（胜箱槽序：0=金币 1=经验书 2=装备，实现口径 #1；败箱槽序：0=金币 1=经验书，无装备槽） */
    public ChestOption optionAt(int index) {
        if (index < 0 || index >= options.size()) {
            return null;
        }
        return options.get(index);
    }

    public int getRound() { return round; }
    public boolean isBoss() { return boss; }
    public ChestOrigin getOrigin() { return origin; }
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
        return round == that.round && boss == that.boss && origin == that.origin
                && options.equals(that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(round, boss, origin, options);
    }
}
