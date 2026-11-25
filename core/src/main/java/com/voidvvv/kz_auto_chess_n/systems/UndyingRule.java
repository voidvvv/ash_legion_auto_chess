package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.UndyingSpec;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;

/**
 * 不死判定（C1/C7，CP17）：undying 单位首次 HP≤0 → 倒地（非真死：不触发亡语/名册/点算，
 * 不再触发不死）；倒地期满原地站起。倒地五项：不可行动（hasControl）／不可被索敌
 * （四过滤点）／不可被伤害治疗（DamagePipeline 入口防御）／状态时钟冻结（tickStatuses
 * 跳过）／占格维持 + 能量保留。
 */
final class UndyingRule {
    /** 时间边界容差 = 半个逻辑步（沿 StatusSystem.TIME_EPSILON 惯例：dt 步进累积浮点微差不改到期语义） */
    private static final float TIME_EPSILON = GameBalance.LOGIC_STEP / 2f;

    private UndyingRule() {
    }

    /** 死亡清扫 pass1 调用：命中则单位转入倒地并返回 true（不产死亡记录、不走清扫） */
    static boolean tryEnterDowned(BattleState state, BattleUnit unit) {
        UndyingSpec spec = unit.getTemplate().getUndying();
        if (spec == null || unit.isUndyingUsed() || unit.isDowned()) {
            return false;
        }
        unit.enterDowned(spec.getReviveDelaySeconds());
        state.record(CombatEvent.unitDowned(state.getTick(), unit.getId()));
        return true;
    }

    /** step ③ 计时循环调用：倒地期满 → 原地站起（占格从未释放，「格被占」为防御分支） */
    static void tryRevive(BattleState state, BattleUnit unit) {
        if (!unit.isDowned() || unit.getDownedTimerRemaining() > TIME_EPSILON) {
            return;
        }
        if (state.unitAt(unit.getGridX(), unit.getGridY()) != unit) { // 防御：格被占 → 最近空格
            relocateToNearestFreeCell(state, unit);
        }
        UndyingSpec spec = unit.getTemplate().getUndying();
        unit.revive(spec == null ? 0.4f : spec.getReviveHpRatio());
        state.record(CombatEvent.unitRevived(state.getTick(), unit.getId()));
    }

    /** 曼哈顿最近空格（平局 x 升序后 y 升序）；全满抛错（防御，理论不可达——人口 < 格数） */
    private static void relocateToNearestFreeCell(BattleState state, BattleUnit unit) {
        int fromX = unit.getGridX();
        int fromY = unit.getGridY();
        int bestX = -1;
        int bestY = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
            for (int y = 0; y < GameBalance.BOARD_ROWS; y++) {
                if (state.unitAt(x, y) != null) {
                    continue;
                }
                int distance = Math.abs(x - fromX) + Math.abs(y - fromY);
                if (distance < bestDistance) { // 同距不换位：扫描序 x↑y↑ 即平局序
                    bestDistance = distance;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        if (bestX < 0) {
            throw new IllegalStateException("防御分支：无空格可复活落位 unit=" + unit.getId());
        }
        state.placeUnit(unit, bestX, bestY);
    }
}
