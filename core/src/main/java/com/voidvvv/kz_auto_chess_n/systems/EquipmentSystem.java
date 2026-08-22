package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.EquipItemCommand;
import com.voidvvv.kz_auto_chess_n.command.UnequipItemCommand;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * 装备系统（GDD §5.2 B2 灵活可拆卸）：穿脱命令 handler + 卖出/合成共用的卸下助手。
 * EquipItem 槽位被占 → 拒绝（实现口径 #6：先手动卸下，UI 提示）。
 */
public final class EquipmentSystem {

    /** 注册穿脱命令 handler（门控：SHOPPING） */
    public void registerHandlers(CommandManager manager) {
        manager.registerHandler(EquipItemCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING) {
                return false;
            }
            EquipItemCommand equip = (EquipItemCommand) cmd;
            Equipment item = ctx.getPlayer().findInventoryItem(equip.getItemId());
            Unit unit = ctx.getPlayer().getUnitById(equip.getUnitId());
            if (item == null || unit == null
                    || unit.equippedIn(item.getTemplate().getSlot()) != null) {
                return false; // 物品不在包 / 棋子不在名单 / 槽位被占
            }
            ctx.getPlayer().removeFromInventory(item);
            unit.equip(item);
            ctx.getRunState().addNotice(
                    unit.getTemplate().getName() + " 穿戴 " + item.getTemplate().getName());
            return true;
        });
        manager.registerHandler(UnequipItemCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING) {
                return false;
            }
            UnequipItemCommand unequip = (UnequipItemCommand) cmd;
            Unit owner = ctx.getPlayer().findEquipOwner(unequip.getItemId());
            if (owner == null) {
                return false;
            }
            for (Equipment item : owner.getEquipped()) {
                if (item.getId() == unequip.getItemId()) {
                    owner.unequip(item);
                    ctx.getPlayer().addToInventory(item);
                    ctx.getRunState().addNotice("卸下 " + item.getTemplate().getName());
                    return true;
                }
            }
            return false;
        });
    }

    /** 卖出/合成共用：卸下单位全部装备回背包（GDD §3.6——不随棋子消失） */
    public static void unequipAll(Unit unit, Player player) {
        List<Equipment> equipped = new ArrayList<Equipment>(unit.getEquipped());
        for (Equipment item : equipped) {
            unit.unequip(item);
            player.addToInventory(item);
        }
    }
}
