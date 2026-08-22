package com.voidvvv.kz_auto_chess_n.render.board;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;

/**
 * 布局常量与换算（render §九坐标表；纯 Java 可测，零 Gdx）。
 *
 * <p>统一虚拟坐标（render §2.2，640×360）：棋盘 ④ (224,50,192,224)、备战席 ② (20,48,108,120)。
 * 行 0（敌区）在顶——格坐标 y 向上、屏幕 y 向下，cellCenterY 做翻转。
 * 全部输出整数虚拟像素（int 运算天然吸附，render §八.2）。
 */
public final class BoardGeometry {
    public static final int VIRTUAL_W = 640;
    public static final int VIRTUAL_H = 360;

    /** ④ 棋盘 6×7×32 */
    public static final int BOARD_X = 224;
    public static final int BOARD_Y = 50;
    public static final int BOARD_W = 192;
    public static final int BOARD_H = 224;
    /** ② 备战席 3×3（槽 36×40） */
    public static final int BENCH_X = 20;
    public static final int BENCH_Y = 48;
    public static final int BENCH_W = 108;
    public static final int BENCH_H = 120;

    /** ③ 装备背包 3×2（UI 域 InventoryPanel 定位同源；槽 36×50） */
    public static final int INVENTORY_X = 20;
    public static final int INVENTORY_Y = 140;
    public static final int INVENTORY_W = 108;
    public static final int INVENTORY_H = 100;
    /** ⑤ 羁绊面板（UI 域） */
    public static final int SYNERGY_X = 508;
    public static final int SYNERGY_Y = 48;
    public static final int SYNERGY_W = 112;
    public static final int SYNERGY_H = 144;
    /** ⑦ 出售区（棋盘域拖拽终点，仅 SHOPPING） */
    public static final int SELL_ZONE_X = 564;
    public static final int SELL_ZONE_Y = 246;
    public static final int SELL_ZONE_W = 56;
    public static final int SELL_ZONE_H = 46;
    /** ⑧ 商店栏（UI 域 ShopBar，全宽 640） */
    public static final int SHOP_BAR_Y = 296;
    public static final int SHOP_BAR_H = 64;
    /** ⑨ 事件通知小窗（render §九原值 y=230 与 ③ 底边 240 重叠 10px——差异声明 #4，改 244 起） */
    public static final int NOTIFY_X = 20;
    public static final int NOTIFY_Y = 244;
    public static final int NOTIFY_W = 128;
    public static final int NOTIFY_H = 46;

    public static final int CELL = 32;
    public static final int BENCH_SLOT_W = 36;
    public static final int BENCH_SLOT_H = 40;
    public static final int INVENTORY_SLOT_W = 36;
    public static final int INVENTORY_SLOT_H = 50;

    private BoardGeometry() {
    }

    // —— 棋盘格 ↔ 像素 ——

    public static int[] cellCenter(int gridX, int gridY) {
        return new int[]{cellCenterX(gridX), cellCenterY(gridY)};
    }

    /** 格中心 x（热路径零分配版） */
    public static int cellCenterX(int gridX) {
        return BOARD_X + gridX * CELL + CELL / 2;
    }

    /** 格中心 y：行 0（敌区）在顶（屏幕 y 向下翻转） */
    public static int cellCenterY(int gridY) {
        return BOARD_Y + BOARD_H - (gridY + 1) * CELL + CELL / 2;
    }

    // —— 连续格坐标 ↔ 像素（模拟层弹道口径：格中心 = gridX + 0.5；差异 #P0 修正） ——

    /** 连续格坐标 x → 像素中心 x（0.5×32=16 浮点精确，与 cellCenterX(gridX) 等价） */
    public static float continuousCenterX(float x) {
        return BOARD_X + x * CELL;
    }

    /** 连续格坐标 y → 像素中心 y（行 0 在顶，翻转口径同 cellCenterY） */
    public static float continuousCenterY(float y) {
        return BOARD_Y + BOARD_H - y * CELL;
    }

    /** 像素 → 格坐标；界外返回 null（boardProcessor 命中判定） */
    public static int[] pixelToCell(int px, int py) {
        if (px < BOARD_X || px >= BOARD_X + BOARD_W || py < BOARD_Y || py >= BOARD_Y + BOARD_H) {
            return null;
        }
        int gridX = (px - BOARD_X) / CELL;
        int gridY = GameBalance.BOARD_ROWS - 1 - (py - BOARD_Y) / CELL;
        return new int[]{gridX, gridY};
    }

    // —— 备战席槽 ↔ 像素（列主序：slot = col×3 + row） ——

    public static int[] benchSlotCenter(int slotIndex) {
        int col = slotIndex / 3;
        int row = slotIndex % 3;
        return new int[]{BENCH_X + col * BENCH_SLOT_W + BENCH_SLOT_W / 2,
                BENCH_Y + row * BENCH_SLOT_H + BENCH_SLOT_H / 2};
    }

    /** 像素 → 槽索引；界外返回 -1 */
    public static int pixelToBenchSlot(int px, int py) {
        if (px < BENCH_X || px >= BENCH_X + BENCH_W || py < BENCH_Y || py >= BENCH_Y + BENCH_H) {
            return -1;
        }
        int col = (px - BENCH_X) / BENCH_SLOT_W;
        int row = (py - BENCH_Y) / BENCH_SLOT_H;
        return col * 3 + row;
    }

    // —— Phase 5 CP17：⑦ 出售区命中 / ③ 背包槽定位 ——

    /** 像素点是否在 ⑦ 出售区内（boardProcessor 拖拽终点判定） */
    public static boolean isInSellZone(int px, int py) {
        return px >= SELL_ZONE_X && px < SELL_ZONE_X + SELL_ZONE_W
                && py >= SELL_ZONE_Y && py < SELL_ZONE_Y + SELL_ZONE_H;
    }

    /** ③ 背包槽中心（3 列 × 2 行；row 0 在下——scene2d y 向上） */
    public static int[] inventorySlotCenter(int slotIndex) {
        int col = slotIndex / 2;
        int row = slotIndex % 2;
        return new int[]{INVENTORY_X + col * INVENTORY_SLOT_W + INVENTORY_SLOT_W / 2,
                INVENTORY_Y + INVENTORY_H - row * INVENTORY_SLOT_H - INVENTORY_SLOT_H / 2};
    }
}
