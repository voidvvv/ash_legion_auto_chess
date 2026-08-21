package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;

/**
 * 移动系统（battle §四离散跳格；无状态实例类）。
 *
 * <p>贪婪步：4 邻空格取到目标曼哈顿距离最小者；平局 上&gt;下&gt;左&gt;右；
 * 无递减空格 → 等待（返回 false）。全场 6×7 可通行可停留（口径 #1，含缓冲第 3 行
 * 与对方布阵区）；同 tick 抢格由行动序天然解决（先行动者先占）。
 * 计时器消耗不在此处——归 BattleSystem 行动链（口径 #3 互斥单行动）。
 */
public final class MovementSystem {
    /** 平局优先序：上(y−1) > 下(y+1) > 左(x−1) > 右(x+1)——玩家在下方、敌方在上方 */
    private static final int[][] DIRECTIONS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    /**
     * 尝试走一步。
     *
     * @return true = 已跳格（记账同步）；false = 等待
     */
    public boolean tryStep(BattleState state, BattleUnit mover, BattleUnit target) {
        int currentDistance = manhattan(mover, target);
        int bestDistance = currentDistance;
        int bestX = -1;
        int bestY = -1;
        for (int[] dir : DIRECTIONS) {
            int nx = mover.getGridX() + dir[0];
            int ny = mover.getGridY() + dir[1];
            if (nx < 0 || nx >= GameBalance.BOARD_COLS || ny < 0 || ny >= GameBalance.BOARD_ROWS) {
                continue;
            }
            if (state.unitAt(nx, ny) != null) {
                continue;
            }
            int distance = Math.abs(nx - target.getGridX()) + Math.abs(ny - target.getGridY());
            if (distance < bestDistance) { // 严格小于：保留先遍历方向（平局序）
                bestDistance = distance;
                bestX = nx;
                bestY = ny;
            }
        }
        if (bestX < 0) {
            return false; // 无递减空格 → 等待
        }
        state.removeFromGrid(mover);
        state.placeUnit(mover, bestX, bestY);
        return true;
    }

    private static int manhattan(BattleUnit a, BattleUnit b) {
        return Math.abs(a.getGridX() - b.getGridX()) + Math.abs(a.getGridY() - b.getGridY());
    }
}
