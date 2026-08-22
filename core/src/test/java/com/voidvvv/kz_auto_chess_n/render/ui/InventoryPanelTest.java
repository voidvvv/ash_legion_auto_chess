package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InventoryPanel 失配自动取消测试（CP23）：待定物品已不在背包（已穿/卖出）时
 * reconcilePending 清待定态；仍在包则保留。纯静态函数直测，
 * 槽位点击交互与绘制走 lwjgl3 手验。
 */
class InventoryPanelTest {

    private static Equipment sword(int id) {
        EquipmentData template = new EquipmentData("eq_sword_" + id, "铁剑", EquipmentSlot.WEAPON,
                EquipmentRarity.WHITE, Collections.<EquipmentEffect>emptyList(), null);
        return new Equipment(id, template);
    }

    @Test
    @DisplayName("待定物品仍在背包：待定态保留")
    void keepsPendingWhileItemInInventory() {
        Player player = new Player(10);
        Equipment item = sword(5);
        player.addToInventory(item);
        EquipPendingState pending = new EquipPendingState();
        pending.set(5);

        InventoryPanel.reconcilePending(pending, player);

        assertThat(pending.hasPending()).isTrue();
        assertThat(pending.pendingItemId()).isEqualTo(5);
    }

    @Test
    @DisplayName("待定物品已不在背包（已穿/卖出）：自动取消")
    void clearsPendingWhenItemGone() {
        Player player = new Player(10);
        player.addToInventory(sword(5)); // 入包后又移除（如穿上）
        player.removeFromInventory(player.getInventory().get(0));
        EquipPendingState pending = new EquipPendingState();
        pending.set(5);

        InventoryPanel.reconcilePending(pending, player);

        assertThat(pending.hasPending()).isFalse();
    }

    @Test
    @DisplayName("背包里是其他物品（id 不匹配）：同样取消")
    void clearsPendingWhenOnlyOtherItemsRemain() {
        Player player = new Player(10);
        player.addToInventory(sword(6));
        EquipPendingState pending = new EquipPendingState();
        pending.set(5);

        InventoryPanel.reconcilePending(pending, player);

        assertThat(pending.hasPending()).isFalse();
    }

    @Test
    @DisplayName("无待定态时调用为无操作")
    void noPendingIsNoOp() {
        Player player = new Player(10);
        EquipPendingState pending = new EquipPendingState();

        InventoryPanel.reconcilePending(pending, player);

        assertThat(pending.hasPending()).isFalse();
        assertThat(pending.pendingItemId()).isEqualTo(-1);
    }
}
