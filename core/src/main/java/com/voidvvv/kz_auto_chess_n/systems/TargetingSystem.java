package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;

/**
 * 索敌系统（battle §三优先链；无状态实例类）。
 *
 * <p>优先级：specialPriority != null ? specialPriority : defaultPriority（UnitData 词表）。
 * 排序键：NEAREST=min 曼哈顿距离 | BACKLINE=max |y−3|（口径 #23，两侧对称）|
 * LOWEST_HP=min hpRatio | HIGHEST_ATK=max 有效 attack。
 * 平局链统一为：主键相等 → 到自身距离 → id 升序（开局发号序 = startBattle 部署扫描序 y↑x↑）。
 * 候选 = 存活敌方（含濒死未清扫）。
 */
public final class TargetingSystem {

    /** 为 self 选目标；无候选返回 null */
    public BattleUnit findTarget(BattleState state, BattleUnit self) {
        TargetPriority priority = self.getTemplate().getSpecialPriority() != null
                ? self.getTemplate().getSpecialPriority()
                : self.getTemplate().getDefaultPriority();

        BattleUnit best = null;
        float bestKey = 0f;
        int bestDistance = 0;
        for (BattleUnit candidate : state.getUnits()) {
            if (candidate.getSide() == self.getSide() || !candidate.isActable()) {
                continue; // 过滤点三（C7 ③）：倒地者不被索敌
            }
            float key = sortKey(priority, self, candidate);
            int distance = manhattan(self, candidate);
            if (best == null
                    || (priority == TargetPriority.BACKLINE || priority == TargetPriority.HIGHEST_ATK
                        ? key > bestKey : key < bestKey)
                    || (key == bestKey && (distance < bestDistance
                        || (distance == bestDistance && candidate.getId() < best.getId())))) {
                best = candidate;
                bestKey = key;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** 每 120 tick（RETARGET_INTERVAL）全局强制重评估（id 序，含清空无候选者的目标；
     *  嘲讽 override 期豁免——K7，旗手指向不被冲掉） */
    public void retargetAll(BattleState state) {
        for (BattleUnit unit : state.getUnits()) {
            if (unit.isCleaned()) {
                continue;
            }
            if (unit.isTaunted(state.getTick())) {
                continue; // 嘲讽期不参与全局重评估（CP23 K7）
            }
            BattleUnit target = findTarget(state, unit);
            unit.setTargetId(target == null ? -1 : target.getId());
        }
    }

    /** 清扫后立即重选：只影响目标指向亡者的存活单位（battle §三） */
    public void retargetOnDeath(BattleState state, int deadId) {
        for (BattleUnit unit : state.getUnits()) {
            if (unit.isCleaned() || unit.getTargetId() != deadId) {
                continue;
            }
            BattleUnit target = findTarget(state, unit);
            unit.setTargetId(target == null ? -1 : target.getId());
        }
    }

    private static float sortKey(TargetPriority priority, BattleUnit self, BattleUnit candidate) {
        switch (priority) {
            case NEAREST:
            case BACKLINE:
                return coordinateSortKey(priority, self.getGridX(), self.getGridY(),
                        candidate.getGridX(), candidate.getGridY()); // 坐标类主键单源（§1.1 预判复用）
            case LOWEST_HP:
                return candidate.hpRatio();
            case HIGHEST_ATK:
                return candidate.getEffective(StatKey.ATTACK);
            default:
                throw new IllegalArgumentException("未知 TargetPriority: " + priority);
        }
    }

    /**
     * 坐标排序键（品质改造 §1.1 战前预判复用；与 {@link #findTarget} 主键同式单源）：
     * NEAREST = 曼哈顿距离（min 胜）| BACKLINE = |y−3| 中线偏离（max 胜，口径 #23 两侧对称）。
     * 仅坐标类优先级——LOWEST_HP / HIGHEST_ATK 依赖战斗属性，不在战前预判范围（越界即抛）。
     */
    public static float coordinateSortKey(TargetPriority priority, int selfGridX, int selfGridY,
                                          int candidateGridX, int candidateGridY) {
        switch (priority) {
            case NEAREST:
                return Math.abs(selfGridX - candidateGridX) + Math.abs(selfGridY - candidateGridY);
            case BACKLINE:
                return Math.abs(candidateGridY - 3);
            default:
                throw new IllegalArgumentException("预判仅支持坐标类优先级: " + priority);
        }
    }

    private static int manhattan(BattleUnit a, BattleUnit b) {
        return Math.abs(a.getGridX() - b.getGridX()) + Math.abs(a.getGridY() - b.getGridY());
    }
}
