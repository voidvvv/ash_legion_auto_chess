package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.MoveUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.SellUnitCommand;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * 名单系统（Phase 4 由 RunFlowSystem 注册的 MoveUnit 本期迁入 + SellUnit 新增）。
 * 卖出（GDD §3.6）：装备自动卸下回背包、返还累计花费 100%、板上卖出同步释放人口。
 */
public final class RosterSystem {
    public enum Edit { ADD, REPLACE, REMOVE, STAR }

    private final MoveUnitExecutor moveUnitExecutor = new MoveUnitExecutor();

    public void registerHandlers(CommandManager manager) {
        manager.registerHandler(MoveUnitCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING) {
                return false;
            }
            MoveUnitCommand move = (MoveUnitCommand) cmd;
            return moveUnitExecutor.move(ctx.getPlayer(), move.getUnitId(), move.getTarget());
        });
        manager.registerHandler(SellUnitCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING) {
                return false;
            }
            Player player = ctx.getPlayer();
            Unit unit = player.getUnitById(((SellUnitCommand) cmd).getUnitId());
            if (unit == null) {
                return false;
            }
            int refund = unit.getSpend();
            removeWithoutRefund(player, unit);
            player.addGold(refund);
            ctx.getRunState().addNotice("卖出 " + unit.getTemplate().getName() + "（+" + refund + " 金）");
            return true;
        });
    }

    public static boolean isPracticeTemplate(RunContext ctx, UnitData template) {
        return template != null && !template.isBoss()
                && ctx.getRunState().getModifiers().isShopAllowed(template.getId());
    }

    /** Returns a rejection reason or null on success. Validation precedes ID allocation or mutation. */
    public String editPractice(RunContext ctx, PlacementTarget target, int expectedId, int expectedStar,
                               Edit edit, String templateId, int star) {
        if (ctx.getMode().closesArchiveTrack() || !ctx.getRunState().isRunStarted()
                || ctx.getRunState().getPhase() != GamePhase.SHOPPING
                || ctx.getBattleState() != null || ctx.getRunState().getPendingChest() != null) {
            return "只有普通远征的备战阶段可以编辑阵容。";
        }
        Player player = ctx.getPlayer();
        List<Unit> bench = player.getBench();
        int benchSlot = -1;
        PlacementTarget.Cell cell = null;
        Unit unit;
        if (target instanceof PlacementTarget.Bench && MoveUnitExecutor.isBenchSlot((PlacementTarget.Bench) target)) {
            benchSlot = ((PlacementTarget.Bench) target).slotIndex;
            unit = benchSlot < bench.size() ? bench.get(benchSlot) : null;
        } else if (target instanceof PlacementTarget.Cell && MoveUnitExecutor.isPlayerCell((PlacementTarget.Cell) target)) {
            cell = (PlacementTarget.Cell) target;
            unit = player.deployedAt(cell.gridX, cell.gridY);
        } else {
            return "位置无效，只能编辑备战席和己方格。";
        }
        if ((unit == null ? 0 : unit.getId()) != expectedId || (unit == null ? 0 : unit.getStar()) != expectedStar) {
            return "该位置的棋子或星级已变化，请核对后重试。";
        }
        if (edit == null) return "阵容操作无效。";
        if (edit == Edit.ADD && unit != null) return "该位置已有棋子，请使用替换。";
        if (edit != Edit.ADD && unit == null) return "该位置为空，请先新增棋子。";
        if (edit != Edit.REMOVE && (star < 1 || star > 3)) return "星级必须在 1 至 3 之间。";

        if (edit == Edit.STAR) {
            if (unit.getStar() == star) return "该棋子已经是此星级。";
            unit.setStar(star);
        } else if (edit == Edit.REMOVE) {
            removeWithoutRefund(player, unit);
        } else {
            UnitData template = templateId == null ? null : ctx.getGameData().getUnits().get(templateId);
            if (!isPracticeTemplate(ctx, template)) return "棋子模板不存在或未对本次远征开放。";
            if (edit == Edit.ADD && benchSlot >= 0 && benchSlot != bench.size()) {
                return "备战席按顺序排列，请选第一个空位。";
            }
            if (edit == Edit.ADD && cell != null && player.getDeployedUnits().size() >= player.getPopulationCap()) {
                return "已达人口上限，请先移除一名场上棋子或升级。";
            }
            int next = ctx.getRunState().getIdIssuer().peekNext();
            if (next < 1 || next == Integer.MAX_VALUE || player.getUnitById(next) != null
                    || player.findInventoryItem(next) != null || player.findEquipOwner(next) != null) {
                return "棋子编号不可用。";
            }
            Unit replacement = new Unit(ctx.getRunState().getIdIssuer().nextId(), template, star);
            List<Unit> editedBench = new ArrayList<>(bench);
            Unit[] grid = new Unit[GameBalance.BOARD_COLS * 3];
            for (int y = 4; y <= 6; y++) {
                for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                    grid[(y - 4) * GameBalance.BOARD_COLS + x] = player.deployedAt(x, y);
                }
            }
            if (cell != null) {
                grid[(cell.gridY - 4) * GameBalance.BOARD_COLS + cell.gridX] = replacement;
            } else if (unit != null) {
                editedBench.set(benchSlot, replacement);
            } else {
                editedBench.add(replacement);
            }
            player.restoreRoster(editedBench, grid);
            if (unit != null) EquipmentSystem.unequipAll(unit, player);
        }
        ctx.getRunState().markPractice();
        return null;
    }

    private static void removeWithoutRefund(Player player, Unit unit) {
        EquipmentSystem.unequipAll(unit, player);
        player.removeUnit(unit);
    }
}
