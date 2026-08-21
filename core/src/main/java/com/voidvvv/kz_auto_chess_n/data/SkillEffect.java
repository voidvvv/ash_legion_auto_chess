package com.voidvvv.kz_auto_chess_n.data;

/**
 * 技能效果条目（data_schema §5.2 effects[]）。
 *
 * <p>字段配平规则（data_schema §九.5）：DAMAGE/HEAL/SHIELD 必有 value &gt; 0；
 * APPLY_STATUS 必有合法 status 与 duration &gt; 0（STUN 无 value，DOT 类 value = 每跳伤害倍率）。
 *
 * <p>value 单位（data_schema §5.4）：DAMAGE=攻击力倍率；HEAL/SHIELD=maxHp 比例；
 * APPLY_STATUS=状态强度（ATK_UP 30 = +30%；DOT 0.1 = 每跳攻击力 × 0.1）。
 * 语义上可缺省的项以 null 表示（加载校验保证何时必有值）。
 */
public final class SkillEffect {
    private final SkillEffectType effect;
    /** 语义上可缺省（如 STUN 无 value），null = 未填写 */
    private final Float value;
    /** APPLY_STATUS 必填，其他类型为 null */
    private final StatusType status;
    /** APPLY_STATUS 必填（秒），其他类型为 null */
    private final Float duration;

    public SkillEffect(SkillEffectType effect, Float value, StatusType status, Float duration) {
        this.effect = effect;
        this.value = value;
        this.status = status;
        this.duration = duration;
    }

    public SkillEffectType getEffect() { return effect; }
    /** @return 可能 null（未填写时） */
    public Float getValue() { return value; }
    /** @return APPLY_STATUS 时非 null */
    public StatusType getStatus() { return status; }
    /** @return APPLY_STATUS 时非 null（秒） */
    public Float getDuration() { return duration; }
}
