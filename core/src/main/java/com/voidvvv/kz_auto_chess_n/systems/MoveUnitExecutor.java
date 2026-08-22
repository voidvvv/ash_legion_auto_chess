package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;

import java.util.List;

/**
 * MoveUnit 校验与执行（口径 #8/#9/#26；交换语义唯一归属）。
 *
 * <p>全部纯确定性校验，失败返回 false（静默忽略——不抛错）；全部校验先于任何变更，
 * 失败路径零状态残留（操作原子性）。SHOPPING 期门控由调用方（RunFlowSystem）保证。
 * Phase 5 拆归名单/商店系统。
 */
public final class MoveUnitExecutor {

    /**
     * bench↔board / board↔board（交换）/ bench↔bench（换位）。
     * 校验链：unit 在名单 → 落点合法：
     * Cell: x∈[0,5]·y∈[4,6]；bench 来源落空格须 deployed+1 ≤ populationCap；
     * Bench: slotIndex∈[0,8]；board 来源须 bench &lt; 9。
     */
    public boolean move(Player player, int unitId, PlacementTarget target) {
        List<Unit> bench = player.getBench();
        int sourceSlot = -1;
        Unit unit = null;
        for (int i = 0; i < bench.size(); i++) {
            if (bench.get(i).getId() == unitId) {
                unit = bench.get(i);
                sourceSlot = i;
                break;
            }
        }
        int fromX = -1;
        int fromY = -1;
        if (unit == null) {
            search:
            for (int y = 4; y <= 6; y++) {
                for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                    Unit deployed = player.deployedAt(x, y);
                    if (deployed != null && deployed.getId() == unitId) {
                        unit = deployed;
                        fromX = x;
                        fromY = y;
                        break search;
                    }
                }
            }
        }
        if (unit == null) {
            return false; // 不在名单
        }
        if (target instanceof PlacementTarget.Cell) {
            return moveToCell(player, unit, sourceSlot, fromX, fromY, (PlacementTarget.Cell) target);
        }
        return moveToBench(player, unit, sourceSlot, fromX, fromY, (PlacementTarget.Bench) target);
    }

    // —— 落格（含交换） ——

    private boolean moveToCell(Player player, Unit unit, int sourceSlot, int fromX, int fromY,
                               PlacementTarget.Cell cell) {
        if (cell.gridX < 0 || cell.gridX >= GameBalance.BOARD_COLS || cell.gridY < 4 || cell.gridY > 6) {
            return false;
        }
        Unit occupant = player.deployedAt(cell.gridX, cell.gridY);
        if (occupant == unit) {
            return true; // board→board 同格：幂等无操作
        }
        if (occupant == null) {
            if (sourceSlot < 0) { // board→board 走位，人数不变
                player.undeploy(fromX, fromY);
            } else if (player.getDeployedUnits().size() + 1 > player.getPopulationCap()) {
                return false; // bench→空格须过人口上限（口径 #9）
            }
            player.deploy(unit, cell.gridX, cell.gridY);
            return true;
        }
        // 占用格：交换（原板员去 bench 源槽 / 板上互易位）
        player.undeploy(cell.gridX, cell.gridY);
        if (sourceSlot >= 0) { // bench→board：原板员去 moving unit 腾出的源槽
            player.removeFromBench(occupant); // 暂持（undeploy 先落了 bench 末位）
            player.deploy(unit, cell.gridX, cell.gridY);
            player.insertToBench(occupant, sourceSlot);
        } else { // board→board 互易位
            player.undeploy(fromX, fromY);
            player.deploy(unit, cell.gridX, cell.gridY);
            player.deploy(occupant, fromX, fromY);
        }
        return true;
    }

    // —— 落席（含换位） ——

    private boolean moveToBench(Player player, Unit unit, int sourceSlot, int fromX, int fromY,
                                PlacementTarget.Bench bench) {
        if (bench.slotIndex < 0 || bench.slotIndex >= GameBalance.BENCH_SIZE) {
            return false;
        }
        if (sourceSlot >= 0) { // bench→bench：remove + insert（换位）
            player.removeFromBench(unit);
            player.insertToBench(unit, bench.slotIndex); // 索引钳制 [0,size] 归 Player
            return true;
        }
        if (player.getBench().size() >= GameBalance.BENCH_SIZE) {
            return false; // board→bench 预校验（口径 #26）
        }
        player.undeploy(fromX, fromY);
        player.removeFromBench(unit); // undeploy 落末位，重插到目标槽
        player.insertToBench(unit, bench.slotIndex);
        return true;
    }
}
