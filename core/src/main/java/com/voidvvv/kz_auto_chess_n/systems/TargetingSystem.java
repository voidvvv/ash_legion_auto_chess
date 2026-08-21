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
 * 平局链统一为：主键相等 → 到自身距离 → id 升序。候选 = 存活敌方（含濒死未清扫）。
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
            if (candidate.getSide() == self.getSide() || candidate.isCleaned()) {
                continue;
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

    /** 每 120 tick（RETARGET_INTERVAL）全局强制重评估（id 序，含清空无候选者的目标） */
    public void retargetAll(BattleState state) {
        for (BattleUnit unit : state.getUnits()) {
            if (unit.isCleaned()) {
                continue;
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
                return manhattan(self, candidate);
            case BACKLINE:
                return Math.abs(candidate.getGridY() - 3);
            case LOWEST_HP:
                return candidate.hpRatio();
            case HIGHEST_ATK:
                return candidate.getEffective(StatKey.ATTACK);
            default:
                throw new IllegalArgumentException("未知 TargetPriority: " + priority);
        }
    }

    private static int manhattan(BattleUnit a, BattleUnit b) {
        return Math.abs(a.getGridX() - b.getGridX()) + Math.abs(a.getGridY() - b.getGridY());
    }
}
