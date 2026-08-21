package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.StatKey;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 按 StatKey 聚合的修正块（不可变）：ΣADD（平加）与 ΣPCT（百分比）两条通道分别累计。
 *
 * <p>PCT 以<b>百分点</b>存储（30 = +30%），结算处统一 ÷100（data_schema §三刻度约定）；
 * ADD 对百分比类 stat 键同样以百分点存储（lifesteal ADD 20 = +20pp）。
 * {@link #plus} 返回新对象，原块不变（不可变优先）。
 */
public final class StatModifierBlock {
    private static final StatModifierBlock EMPTY = new StatModifierBlock(
            new EnumMap<StatKey, Float>(StatKey.class), new EnumMap<StatKey, Float>(StatKey.class));

    private final EnumMap<StatKey, Float> adds;
    private final EnumMap<StatKey, Float> pcts;

    private StatModifierBlock(EnumMap<StatKey, Float> adds, EnumMap<StatKey, Float> pcts) {
        this.adds = adds.clone();
        this.pcts = pcts.clone();
    }

    /** 空块单例（恒等元：addOf/pctOf 恒 0） */
    public static StatModifierBlock empty() {
        return EMPTY;
    }

    /** 单条修正（ADD/PCT 分别累计）；value 语义按 op 与 key 的刻度约定 */
    public static StatModifierBlock of(StatKey key, EffectOp op, float value) {
        Objects.requireNonNull(key, "key 不能为 null");
        Objects.requireNonNull(op, "op 不能为 null");
        EnumMap<StatKey, Float> adds = new EnumMap<StatKey, Float>(StatKey.class);
        EnumMap<StatKey, Float> pcts = new EnumMap<StatKey, Float>(StatKey.class);
        put(op == EffectOp.ADD ? adds : pcts, key, value);
        return new StatModifierBlock(adds, pcts);
    }

    /** 合并两块（ADD/PCT 通道分别求和），返回新对象 */
    public StatModifierBlock plus(StatModifierBlock other) {
        Objects.requireNonNull(other, "other 不能为 null");
        EnumMap<StatKey, Float> mergedAdds = adds.clone();
        EnumMap<StatKey, Float> mergedPcts = pcts.clone();
        for (Map.Entry<StatKey, Float> e : other.adds.entrySet()) {
            put(mergedAdds, e.getKey(), e.getValue());
        }
        for (Map.Entry<StatKey, Float> e : other.pcts.entrySet()) {
            put(mergedPcts, e.getKey(), e.getValue());
        }
        return new StatModifierBlock(mergedAdds, mergedPcts);
    }

    /** ΣADD（无条目 = 0） */
    public float addOf(StatKey key) {
        Float v = adds.get(key);
        return v == null ? 0f : v;
    }

    /** ΣPCT（百分点；无条目 = 0） */
    public float pctOf(StatKey key) {
        Float v = pcts.get(key);
        return v == null ? 0f : v;
    }

    public boolean isEmpty() {
        return adds.isEmpty() && pcts.isEmpty();
    }

    private static void put(EnumMap<StatKey, Float> map, StatKey key, float value) {
        Float current = map.get(key);
        map.put(key, (current == null ? 0f : current) + value);
    }
}
