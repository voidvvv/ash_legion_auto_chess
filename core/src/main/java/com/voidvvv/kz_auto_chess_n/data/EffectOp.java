package com.voidvvv.kz_auto_chess_n.data;

/** 羁绊/装备效果运算（data_schema §三 EffectOp）：ADD 加算固定值，PCT 百分比。 */
public enum EffectOp implements Vocab {
    ADD("ADD"),
    PCT("PCT");

    private final String jsonName;

    EffectOp(String jsonName) {
        this.jsonName = jsonName;
    }

    @Override
    public String jsonName() {
        return jsonName;
    }
}
