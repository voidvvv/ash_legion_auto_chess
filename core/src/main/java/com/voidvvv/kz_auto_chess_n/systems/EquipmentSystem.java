package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.EquipItemCommand;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.UnequipItemCommand;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
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
    public enum InventoryEdit { ADD, REPLACE, REMOVE }

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
            equip(ctx.getPlayer(), unit, item);
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
                    unequip(ctx.getPlayer(), owner, item);
                    ctx.getRunState().addNotice("卸下 " + item.getTemplate().getName());
                    return true;
                }
            }
            return false;
        });
    }

    /** Edit the compact bag against the revision and exact position displayed by the caller. */
    public String editPracticeInventory(RunContext ctx, long revision, InventoryEdit edit,
                                        int index, int expectedId, String templateId) {
        Player player = ctx.getPlayer();
        String blocked = practiceBlock(ctx, revision);
        if (blocked != null) return blocked;
        List<Equipment> bag = player.getInventory();
        if (edit == null) return "请选择背包操作。";
        if (edit != InventoryEdit.ADD && (index < 0 || index >= bag.size()
                || bag.get(index).getId() != expectedId)) {
            return "所选背包装备已变化，请重新选择。";
        }
        if (edit == InventoryEdit.REMOVE) {
            player.removeFromInventory(bag.get(index));
        } else {
            EquipmentData template = templateId == null ? null : ctx.getGameData().getEquipments().get(templateId);
            if (template == null) return "请选择有效的装备模板。";
            int next = ctx.getRunState().getIdIssuer().peekNext();
            if (next < 1 || next == Integer.MAX_VALUE || player.getUnitById(next) != null
                    || player.findInventoryItem(next) != null || player.findEquipOwner(next) != null) {
                return "装备编号不可用。";
            }
            Equipment item = new Equipment(ctx.getRunState().getIdIssuer().nextId(), template);
            if (edit == InventoryEdit.ADD) player.addToInventory(item);
            else player.replaceInventoryItem(index, item);
        }
        ctx.getRunState().markPractice();
        return null;
    }

    /** Zero incoming ID unloads the observed slot. A nonzero ID equips one existing bag item. */
    public String editPracticeSlot(RunContext ctx, long revision, PlacementTarget target, int unitId,
                                   EquipmentSlot slot, int expectedId, int incomingId) {
        String blocked = practiceBlock(ctx, revision);
        if (blocked != null) return blocked;
        Player player = ctx.getPlayer();
        Unit unit = unitAt(player, target);
        if (unit == null || unit.getId() != unitId) return "该位置的棋子已变化，请重新选择。";
        if (slot == null) return "请选择装备槽。";
        Equipment outgoing = unit.equippedIn(slot);
        if ((outgoing == null ? 0 : outgoing.getId()) != expectedId) return "该槽装备已变化，请重新选择。";
        if (incomingId == 0) {
            if (outgoing == null) return "该槽没有可卸下的装备。";
            unequip(player, unit, outgoing);
        } else {
            Equipment incoming = player.findInventoryItem(incomingId);
            if (incoming == null) return "所选装备已离开背包，请重新选择。";
            if (incoming.getTemplate().getSlot() != slot) return "该装备与所选装备槽不兼容。";
            if (outgoing == null && unit.getEquipped().size() >= GameBalance.EQUIP_SLOTS_PER_UNIT) {
                return "棋子的装备槽已满。";
            }
            if (outgoing != null) unequip(player, unit, outgoing);
            equip(player, unit, incoming);
        }
        ctx.getRunState().markPractice();
        return null;
    }

    private static String practiceBlock(RunContext ctx, long revision) {
        if (ctx.getMode().closesArchiveTrack() || !ctx.getRunState().isRunStarted()
                || ctx.getRunState().getPhase() != GamePhase.SHOPPING
                || ctx.getBattleState() != null || ctx.getRunState().getPendingChest() != null) {
            return "只能在普通远征的备战阶段编辑装备。";
        }
        return ctx.getPlayer().getInventoryRevision() == revision ? null : "背包已变化，请核对后重试。";
    }

    private static Unit unitAt(Player player, PlacementTarget target) {
        if (target instanceof PlacementTarget.Bench) {
            PlacementTarget.Bench bench = (PlacementTarget.Bench) target;
            if (MoveUnitExecutor.isBenchSlot(bench) && bench.slotIndex < player.getBench().size()) {
                return player.getBench().get(bench.slotIndex);
            }
        } else if (target instanceof PlacementTarget.Cell) {
            PlacementTarget.Cell cell = (PlacementTarget.Cell) target;
            if (MoveUnitExecutor.isPlayerCell(cell)) return player.deployedAt(cell.gridX, cell.gridY);
        }
        return null;
    }

    private static void equip(Player player, Unit unit, Equipment item) {
        player.removeFromInventory(item);
        unit.equip(item);
    }

    private static void unequip(Player player, Unit unit, Equipment item) {
        unit.unequip(item);
        player.addToInventory(item);
    }

    /** 卖出/合成共用：卸下单位全部装备回背包（GDD §3.6——不随棋子消失） */
    public static void unequipAll(Unit unit, Player player) {
        List<Equipment> equipped = new ArrayList<Equipment>(unit.getEquipped());
        for (Equipment item : equipped) {
            unequip(player, unit, item);
        }
    }
}
