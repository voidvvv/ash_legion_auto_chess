package com.voidvvv.kz_auto_chess_n.data;

/**
 * 通用效果条目（synergies.json §六 与 equipments.json §八 共用同一套词汇）。
 *
 * <p>三种形态三选一（v5 §4.1 机制通道，K1）：{@code stat}（属性修正，走修正管线，需配 op）、
 * {@code effect}（无 stat 的特殊效果，如开局 SHIELD）或 {@code mechanic}（触发式机制词）——
 * 同一条目三者只取其一（加载期校验）。吸血以 {@code stat: lifesteal}（ADD，百分点）表达，
 * 不使用 effect 通道（data_schema V1.3）。
 *
 * <p>机制通道辅字段（按词选择必填/可缺，见 {@link SynergyMechanic} 参数约定）：
 * value / value2 / status / duration / trait。
 */
public final class EffectData {
    /** 属性修正键；null 表示走 effect / mechanic 通道 */
    private final StatKey stat;
    /** 特殊效果（SHIELD 等）；null 表示走 stat / mechanic 通道 */
    private final StatusType effect;
    /** 触发式机制词；null 表示走 stat / effect 通道（v5 第三通道，K1） */
    private final SynergyMechanic mechanic;
    /** stat 存在时必填 */
    private final EffectOp op;
    /** 数值；机制词无参时为 null（getValue() 读作 0） */
    private final Float value;
    /** 机制词第二数值（SCAR 层数 / DEATH_TAX 生命 / EXECUTE 阈值 等） */
    private final Float value2;
    /** 机制词状态载荷（BASIC_ATTACK_STATUS 的 SLOW/BURN） */
    private final StatusType status;
    /** 机制词时长（秒） */
    private final Float duration;
    /** 缺省 ALLIES */
    private final EffectTarget target;
    /** target=TRAITS 时的 race 或 class 名；其余 null */
    private final String trait;

    /** 兼容旧签名（stat / effect 双通道；mechanic 通道走全参构造器） */
    public EffectData(StatKey stat, StatusType effect, EffectOp op, float value, EffectTarget target) {
        this(stat, effect, null, op, value, null, null, null, target, null);
    }

    public EffectData(StatKey stat, StatusType effect, SynergyMechanic mechanic, EffectOp op,
                      Float value, Float value2, StatusType status, Float duration,
                      EffectTarget target, String trait) {
        this.stat = stat;
        this.effect = effect;
        this.mechanic = mechanic;
        this.op = op;
        this.value = value;
        this.value2 = value2;
        this.status = status;
        this.duration = duration;
        this.target = target;
        this.trait = trait;
    }

    /** @return 属性修正键，走 effect / mechanic 通道时为 null */
    public StatKey getStat() { return stat; }
    /** @return 特殊效果类型，走 stat / mechanic 通道时为 null */
    public StatusType getEffect() { return effect; }
    /** @return 触发式机制词，走 stat / effect 通道时为 null */
    public SynergyMechanic getMechanic() { return mechanic; }
    /** @return stat 存在时非 null */
    public EffectOp getOp() { return op; }
    /** 数值；未填写（无参机制词）读作 0 */
    public float getValue() { return value == null ? 0f : value; }
    /** @return 机制词第二数值，可能 null */
    public Float getValue2() { return value2; }
    /** @return 机制词状态载荷，可能 null */
    public StatusType getStatus() { return status; }
    /** @return 机制词时长（秒），可能 null */
    public Float getDuration() { return duration; }
    public EffectTarget getTarget() { return target; }
    /** @return target=TRAITS 时的 race/class 名，其余 null */
    public String getTrait() { return trait; }

    /** 是否走属性修正通道（stat 非 null） */
    public boolean isStatChannel() { return stat != null; }

    /** 是否走机制通道（mechanic 非 null；stat/effect/mechanic 三选一互斥，K1） */
    public boolean isMechanicChannel() { return mechanic != null; }
}
