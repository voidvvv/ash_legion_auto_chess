package com.voidvvv.kz_auto_chess_n.data;

/** 效果作用目标（data_schema §三 EffectTarget）：MVP 仅 ALLIES 一档，预留 TRAITS。 */
public enum EffectTarget implements Vocab {
    ALLIES("ALLIES"),
    TRAITS("TRAITS");

    private final String jsonName;

    EffectTarget(String jsonName) {
        this.jsonName = jsonName;
    }

    @Override
    public String jsonName() {
        return jsonName;
    }
}
