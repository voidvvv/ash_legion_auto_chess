package com.voidvvv.kz_auto_chess_n.command;

/**
 * 落点四合一（input §4.1）：上场/下场/走位/交换共用同一落点载体——
 * 交换语义（目标处已有单位时互易位）归 systems 判定，命令不感知。
 */
public abstract class PlacementTarget {

    private PlacementTarget() {
        // 密封：仅允许内嵌 Bench / Cell 两形态
    }

    /** 备战席槽位（入席序索引 0~8；换位 = remove + insert） */
    public static final class Bench extends PlacementTarget {
        public final int slotIndex;

        public Bench(int slotIndex) {
            this.slotIndex = slotIndex;
        }

        @Override
        public String toString() {
            return "Bench(" + slotIndex + ")";
        }
    }

    /** 棋盘格（玩家区 y ∈ 4~6；越界/缓冲带拒绝归 MoveUnitExecutor） */
    public static final class Cell extends PlacementTarget {
        public final int gridX;
        public final int gridY;

        public Cell(int gridX, int gridY) {
            this.gridX = gridX;
            this.gridY = gridY;
        }

        @Override
        public String toString() {
            return "Cell(" + gridX + "," + gridY + ")";
        }
    }
}
