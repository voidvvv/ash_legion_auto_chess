package com.voidvvv.kz_auto_chess_n.data;

/** 技能目标形状（data_schema §三 SkillShape，7 种，V1.2 增 AOE_2）。语义见 data_schema §5.3。 */
public enum SkillShape implements Vocab {
    SINGLE_TARGET("SINGLE_TARGET"),
    SELF("SELF"),
    LOWEST_ALLY("LOWEST_ALLY"),
    ALL_ALLIES("ALL_ALLIES"),
    AOE_1("AOE_1"),
    AOE_2("AOE_2"),
    ALL_ENEMIES("ALL_ENEMIES");

    private final String jsonName;

    SkillShape(String jsonName) {
        this.jsonName = jsonName;
    }

    @Override
    public String jsonName() {
        return jsonName;
    }
}
