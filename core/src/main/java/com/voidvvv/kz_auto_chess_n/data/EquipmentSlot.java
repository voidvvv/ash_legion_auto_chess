package com.voidvvv.kz_auto_chess_n.data;

/** 装备槽位词表（GDD §5.2 三槽；JSON "slot" 字段） */
public enum EquipmentSlot implements Vocab {
    WEAPON("WEAPON"), ARMOR("ARMOR"), TRINKET("TRINKET");

    private final String jsonName;

    EquipmentSlot(String jsonName) { this.jsonName = jsonName; }

    @Override
    public String jsonName() { return jsonName; }
}
