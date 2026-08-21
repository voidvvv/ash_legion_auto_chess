package com.voidvvv.kz_auto_chess_n.data;

/**
 * 通用效果条目（synergies.json §六 与 equipments.json §八 共用同一套词汇）。
 *
 * <p>两种形态二选一：{@code stat}（属性修正，走修正管线，需配 op）或
 * {@code effect}（无 stat 的特殊效果，如 SHIELD）——同一条目两者只取其一（加载期校验）。
 * 吸血以 {@code stat: lifesteal}（ADD，百分点）表达，不使用 effect 通道（data_schema V1.3）。
 */
public final class EffectData {
    /** 属性修正键；null 表示走 effect 通道 */
    private final StatKey stat;
    /** 特殊效果（SHIELD 等）；null 表示走 stat 通道 */
    private final StatusType effect;
    /** stat 存在时必填 */
    private final EffectOp op;
    private final float value;
    /** 缺省 ALLIES */
    private final EffectTarget target;

    public EffectData(StatKey stat, StatusType effect, EffectOp op, float value, EffectTarget target) {
        this.stat = stat;
        this.effect = effect;
        this.op = op;
        this.value = value;
        this.target = target;
    }

    /** @return 属性修正键，走 effect 通道时为 null */
    public StatKey getStat() { return stat; }
    /** @return 特殊效果类型，走 stat 通道时为 null */
    public StatusType getEffect() { return effect; }
    /** @return stat 存在时非 null */
    public EffectOp getOp() { return op; }
    public float getValue() { return value; }
    public EffectTarget getTarget() { return target; }

    /** 是否走属性修正通道（stat 非 null） */
    public boolean isStatChannel() { return stat != null; }
}
