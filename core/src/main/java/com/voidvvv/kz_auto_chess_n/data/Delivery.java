package com.voidvvv.kz_auto_chess_n.data;

/** 弹道载体（data_schema §三 Delivery）。执行语义见 battle_design §五/§六。 */
public enum Delivery implements Vocab {
    MELEE_INSTANT("MELEE_INSTANT"),
    HOMING("HOMING"),
    LINE("LINE");

    private final String jsonName;

    Delivery(String jsonName) {
        this.jsonName = jsonName;
    }

    @Override
    public String jsonName() {
        return jsonName;
    }
}
