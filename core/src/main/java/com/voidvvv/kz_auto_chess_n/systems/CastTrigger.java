package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;

/**
 * 就地施放触发器（battle §5.2 回能步的回调出口）：
 * 单位能量跨越 100 时由 {@code DamagePipeline.gainEnergy} 回调，尝试立即施放。
 *
 * <p>实现方（BattleSystem）负责嵌套深度保护（口径 #19：上限 MAX_INLINE_CAST_DEPTH，
 * 超限推迟到该单位下一行动 tick）——管线侧不感知深度。
 */
public interface CastTrigger {

    /** 尝试就地施放（能量已 ≥ 100）；实现不得抛出（推迟语义优先） */
    void tryCast(BattleState state, BattleUnit caster);
}
