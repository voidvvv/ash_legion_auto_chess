package com.voidvvv.kz_auto_chess_n.command;

/** 卸下（棋子→背包）：载荷仅 itemId，穿戴者由名单扫描（architecture §4.1） */
public final class UnequipItemCommand implements GameCommand {
    private final int itemId;

    public UnequipItemCommand(int itemId) { this.itemId = itemId; }

    public int getItemId() { return itemId; }

    @Override
    public String toString() { return "UnequipItem(item=" + itemId + ")"; }
}
