package com.voidvvv.kz_auto_chess_n.command;

/** 卖出（GDD §3.6）：板/席皆可；返还 = Unit.spend 累计花费 100% */
public final class SellUnitCommand implements GameCommand {
    private final int unitId;

    public SellUnitCommand(int unitId) { this.unitId = unitId; }

    public int getUnitId() { return unitId; }

    @Override
    public String toString() { return "SellUnit(unit=" + unitId + ")"; }
}
