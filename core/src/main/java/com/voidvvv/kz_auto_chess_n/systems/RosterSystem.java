package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.MoveUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.SellUnitCommand;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;

/**
 * 名单系统（Phase 4 由 RunFlowSystem 注册的 MoveUnit 本期迁入 + SellUnit 新增）。
 * 卖出（GDD §3.6）：装备自动卸下回背包、返还累计花费 100%、板上卖出同步释放人口。
 */
public final class RosterSystem {

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
            EquipmentSystem.unequipAll(unit, player); // GDD §3.6：不随棋子消失
            int refund = unit.getSpend();
            player.removeUnit(unit);
            player.addGold(refund);
            ctx.getRunState().addNotice("卖出 " + unit.getTemplate().getName() + "（+" + refund + " 金）");
            return true;
        });
    }
}
