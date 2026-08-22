package com.voidvvv.kz_auto_chess_n.data;

import java.util.Objects;

/** 装备属性修正条目（与 synergies 同一套 {stat, op, value} 词汇，开战进基准快照） */
public final class EquipmentEffect {
    private final StatKey stat;
    private final EffectOp op;
    private final float value;

    public EquipmentEffect(StatKey stat, EffectOp op, float value) {
        this.stat = Objects.requireNonNull(stat, "stat 不能为 null");
        this.op = Objects.requireNonNull(op, "op 不能为 null");
        this.value = value;
    }

    public StatKey getStat() { return stat; }
    public EffectOp getOp() { return op; }
    public float getValue() { return value; }
}
