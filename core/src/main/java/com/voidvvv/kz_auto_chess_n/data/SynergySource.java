package com.voidvvv.kz_auto_chess_n.data;

/** 羁绊来源通道（data_schema §三 SynergySource）：种族与职业各自独立计数（双通道）。 */
public enum SynergySource implements Vocab {
    RACE("RACE"),
    CLASS("CLASS");

    private final String jsonName;

    SynergySource(String jsonName) {
        this.jsonName = jsonName;
    }

    @Override
    public String jsonName() {
        return jsonName;
    }
}
