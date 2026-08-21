package com.voidvvv.kz_auto_chess_n.data;

/** 技能效果类型（data_schema §三 SkillEffectType，4 种）。value 单位见 data_schema §5.4。 */
public enum SkillEffectType implements Vocab {
    DAMAGE("DAMAGE"),
    HEAL("HEAL"),
    SHIELD("SHIELD"),
    APPLY_STATUS("APPLY_STATUS");

    private final String jsonName;

    SkillEffectType(String jsonName) {
        this.jsonName = jsonName;
    }

    @Override
    public String jsonName() {
        return jsonName;
    }
}
