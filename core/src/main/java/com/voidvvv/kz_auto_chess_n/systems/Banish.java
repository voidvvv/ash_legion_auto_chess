package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;

/**
 * 放逐（C10）：独立移除路径——不走伤害管线与清扫，不触发亡语/不死/名册/点算，不视为击杀。
 * 索敌失效依赖 currentTarget 对 cleaned 的惰性重选（现行为），不强制 retarget。
 */
public final class Banish {
    private Banish() {
    }

    /** 移除一个在场单位（对已清扫者幂等空操作） */
    public static void execute(BattleState state, BattleUnit victim) {
        if (victim == null || victim.isCleaned()) {
            return;
        }
        victim.markBanished();
        state.removeFromGrid(victim);
        state.record(CombatEvent.unitBanished(state.getTick(), victim.getId()));
    }
}
