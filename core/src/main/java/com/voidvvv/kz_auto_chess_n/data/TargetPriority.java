package com.voidvvv.kz_auto_chess_n.data;

/** 索敌优先级（data_schema §三 TargetPriority）。排序键与平局决胜见 battle_design §三。 */
public enum TargetPriority implements Vocab {
    NEAREST("NEAREST"),
    BACKLINE("BACKLINE"),
    LOWEST_HP("LOWEST_HP"),
    HIGHEST_ATK("HIGHEST_ATK");

    private final String jsonName;

    TargetPriority(String jsonName) {
        this.jsonName = jsonName;
    }

    @Override
    public String jsonName() {
        return jsonName;
    }
}
