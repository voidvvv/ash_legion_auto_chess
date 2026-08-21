package com.voidvvv.kz_auto_chess_n.data;

/** 异常状态类型（data_schema §三 StatusType，MVP 集 9 种；扩展需登记 battle_design §七）。 */
public enum StatusType implements Vocab {
    STUN("STUN"),
    BLEED("BLEED"),
    POISON("POISON"),
    SLOW("SLOW"),
    ATK_UP("ATK_UP"),
    ATK_DOWN("ATK_DOWN"),
    ASPD_UP("ASPD_UP"),
    SHIELD("SHIELD"),
    REGEN("REGEN");

    private final String jsonName;

    StatusType(String jsonName) {
        this.jsonName = jsonName;
    }

    @Override
    public String jsonName() {
        return jsonName;
    }
}
