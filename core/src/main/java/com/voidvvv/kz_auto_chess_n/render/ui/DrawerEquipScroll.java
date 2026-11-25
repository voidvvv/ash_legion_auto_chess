package com.voidvvv.kz_auto_chess_n.render.ui;

/**
 * 装备网格纵向滚动纯逻辑（UI 抽屉化批3·Q1：3 列 × 2 行可见 + 滚动；背包无上限的完整列表）。
 * 以「首个可见行」为状态：行 = 3 件；可见 2 行；行数 = ceil(条目数 / 3)。零 Gdx，headless 可测。
 */
public final class DrawerEquipScroll {

    private DrawerEquipScroll() {
    }

    /** 行数（不足一行算一行；0 件 → 0 行） */
    public static int rowCount(int itemCount) {
        return Math.max(0, (itemCount + DrawerGeometry.EQUIP_GRID_COLS - 1) / DrawerGeometry.EQUIP_GRID_COLS);
    }

    /** 最大首行：行数 ≤ 可见行数 → 0（不可滚） */
    public static int maxFirstRow(int itemCount) {
        return Math.max(0, rowCount(itemCount) - DrawerGeometry.EQUIP_GRID_ROWS);
    }

    /** 首行钳制到 [0, maxFirstRow]（条目缩水时自动归零） */
    public static int clampFirstRow(int firstRow, int itemCount) {
        return Math.max(0, Math.min(firstRow, maxFirstRow(itemCount)));
    }

    /** 滚动若干行（正 = 看后面的行；内容上移），到头钳住 */
    public static int scrollRows(int firstRow, int itemCount, int rows) {
        return clampFirstRow(firstRow + rows, itemCount);
    }

    /** 首行 → 首个可见槽的库存下标 */
    public static int firstIndex(int firstRow) {
        return firstRow * DrawerGeometry.EQUIP_GRID_COLS;
    }

    /** 滑块下缘在轨内的偏移（纵向：自下而上为正；y-up 下「后面的行」在上端）；不可滚停在底端 */
    public static int knobOffsetY(int trackLength, int knobSize, int firstRow, int itemCount) {
        int range = Math.max(0, trackLength - knobSize);
        int max = maxFirstRow(itemCount);
        if (max == 0) {
            return 0;
        }
        return Math.round(range * (float) clampFirstRow(firstRow, itemCount) / max);
    }

    /** 拖滑块：滑块下缘在轨内的偏移 → 首行（两端钳制；不可滚恒 0） */
    public static int firstRowForKnob(int trackLength, int knobSize, int knobOffset, int itemCount) {
        int range = Math.max(0, trackLength - knobSize);
        int max = maxFirstRow(itemCount);
        if (range == 0 || max == 0) {
            return 0;
        }
        float ratio = Math.max(0f, Math.min(1f, (float) knobOffset / range));
        return Math.round(ratio * max);
    }
}
