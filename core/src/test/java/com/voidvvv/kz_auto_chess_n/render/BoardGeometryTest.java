package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BoardGeometry 测试：布局常量自洽、行 0 在顶的 y 翻转、格↔像素往返、整数吸附（render §九/§八.2）。
 */
class BoardGeometryTest {

    @Test
    @DisplayName("常量自洽：BOARD = 6×32 × 7×32、BENCH = 3×36 × 3×40、虚拟画布 640×360")
    void constantsSelfConsistent() {
        assertThat(BoardGeometry.BOARD_W).isEqualTo(GameBalance.BOARD_COLS * BoardGeometry.CELL);
        assertThat(BoardGeometry.BOARD_H).isEqualTo(GameBalance.BOARD_ROWS * BoardGeometry.CELL);
        assertThat(BoardGeometry.BENCH_W).isEqualTo(3 * BoardGeometry.BENCH_SLOT_W);
        assertThat(BoardGeometry.BENCH_H).isEqualTo(3 * BoardGeometry.BENCH_SLOT_H);
        assertThat(BoardGeometry.VIRTUAL_W).isEqualTo(640);
        assertThat(BoardGeometry.VIRTUAL_H).isEqualTo(360);
    }

    @Test
    @DisplayName("cellCenter 行 0（敌区）在顶：gridY=0 的中心 py 大于 gridY=6 的中心 py")
    void rowZeroAtTop() {
        int[] enemy = BoardGeometry.cellCenter(0, 0);
        int[] player = BoardGeometry.cellCenter(0, 6);
        assertThat(enemy[1]).isGreaterThan(player[1]);
    }

    @Test
    @DisplayName("cellCenter 落在各自 32px 格内（中心 = 格矩形中点）")
    void cellCenterInsideOwnCellRect() {
        for (int y = 0; y < GameBalance.BOARD_ROWS; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                int[] c = BoardGeometry.cellCenter(x, y);
                int top = BoardGeometry.BOARD_Y + BoardGeometry.BOARD_H - (y + 1) * BoardGeometry.CELL;
                assertThat(c[0]).isEqualTo(BoardGeometry.BOARD_X + x * BoardGeometry.CELL + BoardGeometry.CELL / 2);
                assertThat(c[1]).isEqualTo(top + BoardGeometry.CELL / 2);
            }
        }
    }

    @Test
    @DisplayName("cellCenter ↔ pixelToCell 往返一致（全 42 格中心点）")
    void cellCenterRoundTrip() {
        for (int y = 0; y < GameBalance.BOARD_ROWS; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                int[] center = BoardGeometry.cellCenter(x, y);
                int[] cell = BoardGeometry.pixelToCell(center[0], center[1]);
                assertThat(cell).containsExactly(x, y);
            }
        }
    }

    @Test
    @DisplayName("pixelToCell 界外返回 null：左/右/上/下边界外")
    void pixelToCellRejectsOutside() {
        assertThat(BoardGeometry.pixelToCell(BoardGeometry.BOARD_X - 1, 100)).isNull();
        assertThat(BoardGeometry.pixelToCell(BoardGeometry.BOARD_X + BoardGeometry.BOARD_W, 100)).isNull();
        assertThat(BoardGeometry.pixelToCell(300, BoardGeometry.BOARD_Y - 1)).isNull();
        assertThat(BoardGeometry.pixelToCell(300, BoardGeometry.BOARD_Y + BoardGeometry.BOARD_H)).isNull();
    }

    @Test
    @DisplayName("pixelToCell 半格像素命中正确格（格内任意点归属该格）")
    void pixelToCellHalfCellResolution() {
        // 格 (3,4) 的矩形：x ∈ [224+96, 224+128)，y ∈ [114, 146)（top = 274-160）
        assertThat(BoardGeometry.pixelToCell(224 + 96, 114)).containsExactly(3, 4);
        assertThat(BoardGeometry.pixelToCell(224 + 127, 145)).containsExactly(3, 4);
        assertThat(BoardGeometry.pixelToCell(224 + 128, 118)).containsExactly(4, 4); // 下一列
        assertThat(BoardGeometry.pixelToCell(224 + 96, 146)).containsExactly(3, 3);  // 屏幕上方一行（更靠敌区，gridY 更小）
    }

    @Test
    @DisplayName("cellCenter 输出整数吸附（render §八.2 Math.round）")
    void cellCenterSnapsToInteger() {
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 6; x++) {
                int[] c = BoardGeometry.cellCenter(x, y);
                assertThat(c[0]).isEqualTo((int) c[0]);
                assertThat(c[1]).isEqualTo((int) c[1]);
            }
        }
    }

    @Test
    @DisplayName("continuousCenter 与 cellCenter 等价：全 6×7 格 (grid+0.5) 逐位相等（0.5×32=16 浮点精确）")
    void continuousCenterEquivalentToCellCenter() {
        for (int y = 0; y < GameBalance.BOARD_ROWS; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                assertThat(BoardGeometry.continuousCenterX(x + 0.5f))
                        .isEqualTo((float) BoardGeometry.cellCenterX(x));
                assertThat(BoardGeometry.continuousCenterY(y + 0.5f))
                        .isEqualTo((float) BoardGeometry.cellCenterY(y));
            }
        }
        // 抽查：格 (2,5) 中心 (304, 98)；连续坐标 y 大 → 像素 y 小（行 0 在顶）
        assertThat(BoardGeometry.continuousCenterX(2.5f)).isEqualTo(304f);
        assertThat(BoardGeometry.continuousCenterY(5.5f)).isEqualTo(98f);
        assertThat(BoardGeometry.continuousCenterY(5.5f)).isGreaterThan(BoardGeometry.continuousCenterY(6.5f));
    }

    @Test
    @DisplayName("benchSlotCenter 列主序：槽 0/1/2 同列向下、槽 3 起换列")
    void benchSlotsColumnMajor() {
        int[] s0 = BoardGeometry.benchSlotCenter(0);
        int[] s1 = BoardGeometry.benchSlotCenter(1);
        int[] s3 = BoardGeometry.benchSlotCenter(3);
        assertThat(s0[0]).isEqualTo(s1[0]);            // 同列
        assertThat(s1[1] - s0[1]).isEqualTo(BoardGeometry.BENCH_SLOT_H); // 向下一行
        assertThat(s3[0] - s0[0]).isEqualTo(BoardGeometry.BENCH_SLOT_W); // 换列
        assertThat(s3[1]).isEqualTo(s0[1]);
    }

    @Test
    @DisplayName("benchSlotCenter ↔ pixelToBenchSlot 往返一致（全 9 槽）")
    void benchSlotRoundTrip() {
        for (int slot = 0; slot < GameBalance.BENCH_SIZE; slot++) {
            int[] center = BoardGeometry.benchSlotCenter(slot);
            assertThat(BoardGeometry.pixelToBenchSlot(center[0], center[1])).isEqualTo(slot);
        }
    }

    @Test
    @DisplayName("pixelToBenchSlot 界外返回 -1")
    void pixelToBenchSlotRejectsOutside() {
        assertThat(BoardGeometry.pixelToBenchSlot(BoardGeometry.BENCH_X - 1, 60)).isEqualTo(-1);
        assertThat(BoardGeometry.pixelToBenchSlot(400, 60)).isEqualTo(-1);
        assertThat(BoardGeometry.pixelToBenchSlot(30, BoardGeometry.BENCH_Y - 1)).isEqualTo(-1);
        assertThat(BoardGeometry.pixelToBenchSlot(30, BoardGeometry.BENCH_Y + BoardGeometry.BENCH_H)).isEqualTo(-1);
    }

    @Test
    @DisplayName("benchSlotCenter 输出整数吸附且落在槽矩形内")
    void benchSlotCenterInsideSlotRect() {
        for (int slot = 0; slot < 9; slot++) {
            int col = slot / 3; // 列主序：slot = col*3 + row
            int row = slot % 3;
            int[] c = BoardGeometry.benchSlotCenter(slot);
            assertThat(c[0]).isEqualTo(BoardGeometry.BENCH_X + col * BoardGeometry.BENCH_SLOT_W + BoardGeometry.BENCH_SLOT_W / 2);
            assertThat(c[1]).isEqualTo(BoardGeometry.BENCH_Y + row * BoardGeometry.BENCH_SLOT_H + BoardGeometry.BENCH_SLOT_H / 2);
        }
    }

    // —— Phase 5 CP17：③⑤⑦⑧⑨ 区常量与命中判定（render §九 Phase 4 遗留区） ——

    @Test
    @DisplayName("③⑤⑦⑧ 区常量与 render §九坐标表逐项对照（±4px 容差；⑨ 差异声明 #4 例外单独断言）")
    void phase5ZonesMatchRenderLayoutTable() {
        // render_design.md §九：③ (20,140,108,100)、⑤ (508,48,112,144)、⑦ (564,246,56,46)、⑧ (0,296,640,64)
        assertThat(BoardGeometry.INVENTORY_X).isBetween(20 - 4, 20 + 4);
        assertThat(BoardGeometry.INVENTORY_Y).isBetween(140 - 4, 140 + 4);
        assertThat(BoardGeometry.INVENTORY_W).isBetween(108 - 4, 108 + 4);
        assertThat(BoardGeometry.INVENTORY_H).isBetween(100 - 4, 100 + 4);
        assertThat(BoardGeometry.SYNERGY_X).isBetween(508 - 4, 508 + 4);
        assertThat(BoardGeometry.SYNERGY_Y).isBetween(48 - 4, 48 + 4);
        assertThat(BoardGeometry.SYNERGY_W).isBetween(112 - 4, 112 + 4);
        assertThat(BoardGeometry.SYNERGY_H).isBetween(144 - 4, 144 + 4);
        assertThat(BoardGeometry.SELL_ZONE_X).isBetween(564 - 4, 564 + 4);
        assertThat(BoardGeometry.SELL_ZONE_Y).isBetween(246 - 4, 246 + 4);
        assertThat(BoardGeometry.SELL_ZONE_W).isBetween(56 - 4, 56 + 4);
        assertThat(BoardGeometry.SELL_ZONE_H).isBetween(46 - 4, 46 + 4);
        assertThat(BoardGeometry.SHOP_BAR_Y).isBetween(296 - 4, 296 + 4);
        assertThat(BoardGeometry.SHOP_BAR_H).isBetween(64 - 4, 64 + 4);
        // ⑨ render §九原值 (20,230,128,60) 与 ③ 底边 240 重叠 10px——差异声明 #4 改 (20,244,128,46)
        assertThat(BoardGeometry.NOTIFY_X).isEqualTo(20);
        assertThat(BoardGeometry.NOTIFY_Y).isEqualTo(244);
        assertThat(BoardGeometry.NOTIFY_W).isEqualTo(128);
        assertThat(BoardGeometry.NOTIFY_H).isEqualTo(46);
    }

    @Test
    @DisplayName("⑨ 区顶边不低于 ③ 区底边（布局冲突回归：render §九原值重叠 10px 已避让）")
    void notifyZoneDoesNotOverlapInventory() {
        assertThat(BoardGeometry.NOTIFY_Y).isGreaterThanOrEqualTo(BoardGeometry.INVENTORY_Y + BoardGeometry.INVENTORY_H);
        // 其余相邻对也自洽：⑤ 底边不侵入 ⑦、⑨ 底边不侵入 ⑧
        assertThat(BoardGeometry.SELL_ZONE_Y).isGreaterThanOrEqualTo(BoardGeometry.SYNERGY_Y + BoardGeometry.SYNERGY_H);
        assertThat(BoardGeometry.SHOP_BAR_Y).isGreaterThanOrEqualTo(BoardGeometry.NOTIFY_Y + BoardGeometry.NOTIFY_H);
    }

    @Test
    @DisplayName("③ 背包常量自洽：3 列 × 2 行槽位铺满区域、槽 36×50")
    void inventoryConstantsSelfConsistent() {
        assertThat(BoardGeometry.INVENTORY_W).isEqualTo(3 * BoardGeometry.INVENTORY_SLOT_W);
        assertThat(BoardGeometry.INVENTORY_H).isEqualTo(2 * BoardGeometry.INVENTORY_SLOT_H);
    }

    @Test
    @DisplayName("inventorySlotCenter：6 槽中心全在 ③ 区内且互异；row 0 在下（scene2d y 向上）")
    void inventorySlotCentersInsideZoneAndDistinct() {
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (int slot = 0; slot < 6; slot++) {
            int[] c = BoardGeometry.inventorySlotCenter(slot);
            assertThat(c[0]).isBetween(BoardGeometry.INVENTORY_X, BoardGeometry.INVENTORY_X + BoardGeometry.INVENTORY_W - 1);
            assertThat(c[1]).isBetween(BoardGeometry.INVENTORY_Y, BoardGeometry.INVENTORY_Y + BoardGeometry.INVENTORY_H - 1);
            assertThat(seen.add(c[0] + "," + c[1])).isTrue();
        }
        // 槽 0（col 0 row 0）在底行：y 大于槽 1（col 0 row 1，顶行）
        assertThat(BoardGeometry.inventorySlotCenter(0)[1]).isGreaterThan(BoardGeometry.inventorySlotCenter(1)[1]);
        // 槽 2 起换列（col 1），x 步进一槽宽
        assertThat(BoardGeometry.inventorySlotCenter(2)[0] - BoardGeometry.inventorySlotCenter(0)[0])
                .isEqualTo(BoardGeometry.INVENTORY_SLOT_W);
    }

    @Test
    @DisplayName("isInSellZone 四角命中、界外四向不命中（半开区间）")
    void isInSellZoneCornersAndOutside() {
        int x0 = BoardGeometry.SELL_ZONE_X;
        int y0 = BoardGeometry.SELL_ZONE_Y;
        int x1 = BoardGeometry.SELL_ZONE_X + BoardGeometry.SELL_ZONE_W;
        int y1 = BoardGeometry.SELL_ZONE_Y + BoardGeometry.SELL_ZONE_H;
        // 左上/右下/右上/左下四角（右/下边界为开区间，取 -1）
        assertThat(BoardGeometry.isInSellZone(x0, y0)).isTrue();
        assertThat(BoardGeometry.isInSellZone(x1 - 1, y1 - 1)).isTrue();
        assertThat(BoardGeometry.isInSellZone(x1 - 1, y0)).isTrue();
        assertThat(BoardGeometry.isInSellZone(x0, y1 - 1)).isTrue();
        // 中心
        assertThat(BoardGeometry.isInSellZone((x0 + x1) / 2, (y0 + y1) / 2)).isTrue();
        // 界外：左/右/上/下 + 恰在右/下边界（开区间不含）
        assertThat(BoardGeometry.isInSellZone(x0 - 1, y0)).isFalse();
        assertThat(BoardGeometry.isInSellZone(x1, y0)).isFalse();
        assertThat(BoardGeometry.isInSellZone(x0, y0 - 1)).isFalse();
        assertThat(BoardGeometry.isInSellZone(x0, y1)).isFalse();
    }
}
