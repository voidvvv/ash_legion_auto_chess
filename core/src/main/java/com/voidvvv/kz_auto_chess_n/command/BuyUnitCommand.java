package com.voidvvv.kz_auto_chess_n.command;

/** 购买（GDD §3.4）：载荷仅槽位索引——查价不信任载荷（input §6.3） */
public final class BuyUnitCommand implements GameCommand {
    private final int slotIndex;

    public BuyUnitCommand(int slotIndex) { this.slotIndex = slotIndex; }

    public int getSlotIndex() { return slotIndex; }

    @Override
    public String toString() { return "BuyUnit(slot=" + slotIndex + ")"; }
}
