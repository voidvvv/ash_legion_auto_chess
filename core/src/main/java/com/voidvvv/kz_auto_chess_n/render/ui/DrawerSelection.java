package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽屉选中态与「装备」页指纹的纯逻辑（UI 抽屉化批4：自退役的 {@code UnitDetailDialog} 静态助手迁入，语义不变；零 Gdx）。
 * <ul>
 *   <li>{@link #unitGone} / {@link #resolveSelectedId}：选中棋子已不在名单（卖出 / 合并）→ 清选中
 *       （原「详情弹窗过期自动收起」的抽屉形态）；</li>
 *   <li>{@link #equippedIds} / {@link #sameEquippedIds} / {@link #fingerprintChanged}：卸下钮集的构建指纹——
 *       单位 id 或装备 id 序列任一变化才重建（Scene2d ClickListener 要求 down/up 命中同一实例）；</li>
 *   <li>{@link #rowOf}：「装备」页行号 ↔ 装备槽（row 0 = 武器，在上）。</li>
 * </ul>
 */
final class DrawerSelection {

    private DrawerSelection() {
    }

    /** 选中棋子是否已不在名单（板/席）→ true；未选中（-1）与不存在 id 同判 */
    static boolean unitGone(RunContext ctx, int unitId) {
        return ctx.getPlayer().getUnitById(unitId) == null;
    }

    /** 解析当前选中 id：仍在名单 → 原值；已不在名单 / 未选中 → -1（清选中） */
    static int resolveSelectedId(RunContext ctx, int selectedUnitId) {
        return selectedUnitId >= 0 && !unitGone(ctx, selectedUnitId) ? selectedUnitId : -1;
    }

    /** 装备 id 序列（按穿着序；Equipment 按 id 全局唯一实例，id 同即实例同） */
    static List<Integer> equippedIds(List<Equipment> equipped) {
        List<Integer> ids = new ArrayList<Integer>(equipped.size());
        for (Equipment item : equipped) {
            ids.add(item.getId());
        }
        return ids;
    }

    /** 序列比对：同长且逐位 id 相等 → true */
    static boolean sameEquippedIds(List<Integer> a, List<Integer> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 卸下钮集指纹是否变化（决定是否重建按钮）：单位换了 / 装备 id 序列变了 → true；均未变 → false。
     *
     * @param prevUnitId 上次建钮时的单位 id（-1 = 上次无）
     * @param prevIds    上次建钮时的装备 id 序列
     * @param unit       当前选中棋子（null = 当前未选中）
     */
    static boolean fingerprintChanged(int prevUnitId, List<Integer> prevIds, Unit unit) {
        if (unit == null) {
            return prevUnitId != -1;
        }
        if (unit.getId() != prevUnitId) {
            return true;
        }
        return !sameEquippedIds(prevIds, equippedIds(unit.getEquipped()));
    }

    /** 行号 ↔ 装备槽（row 0 = 武器，在上；沿 EquipmentSlot 声明序 WEAPON / ARMOR / TRINKET） */
    static int rowOf(EquipmentSlot slot) {
        EquipmentSlot[] values = EquipmentSlot.values();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == slot) {
                return i;
            }
        }
        return values.length - 1;
    }
}
