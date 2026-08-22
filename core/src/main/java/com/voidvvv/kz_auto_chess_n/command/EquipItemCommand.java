package com.voidvvv.kz_auto_chess_n.command;

/** 穿戴（背包→棋子）：槽位由装备类型推导（武器/盔甲/饰品，architecture §4.1） */
public final class EquipItemCommand implements GameCommand {
    private final int itemId;
    private final int unitId;

    public EquipItemCommand(int itemId, int unitId) {
        this.itemId = itemId;
        this.unitId = unitId;
    }

    public int getItemId() { return itemId; }
    public int getUnitId() { return unitId; }

    @Override
    public String toString() { return "EquipItem(item=" + itemId + ", unit=" + unitId + ")"; }
}
