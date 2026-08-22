package com.voidvvv.kz_auto_chess_n.command;

import java.util.Objects;

/**
 * 移动/布阵命令（Q2 命令集）：bench↔board / board↔board（交换）/ bench↔bench（换位）
 * 的统一载荷；校验与执行归 {@code systems/MoveUnitExecutor}。
 */
public final class MoveUnitCommand implements GameCommand {
    private final int unitId;
    private final PlacementTarget target;

    public MoveUnitCommand(int unitId, PlacementTarget target) {
        this.unitId = unitId;
        this.target = Objects.requireNonNull(target, "target 不能为 null");
    }

    public int getUnitId() { return unitId; }
    public PlacementTarget getTarget() { return target; }

    @Override
    public String toString() {
        return "MoveUnit(unit=" + unitId + ", to=" + target + ")";
    }
}
