package com.voidvvv.kz_auto_chess_n.data;

/** 装备稀有度词表（GDD §5.2 白/成/传；JSON "rarity" 字段；宝箱权重数组序 = values() 序） */
public enum EquipmentRarity implements Vocab {
    WHITE("WHITE"), RARE("RARE"), LEGENDARY("LEGENDARY");

    private final String jsonName;

    EquipmentRarity(String jsonName) { this.jsonName = jsonName; }

    @Override
    public String jsonName() { return jsonName; }
}
