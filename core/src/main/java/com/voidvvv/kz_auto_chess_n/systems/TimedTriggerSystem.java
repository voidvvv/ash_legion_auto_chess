package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.TimedTrigger;

import java.util.ArrayList;
import java.util.List;

/**
 * 定时触发器系统（CP23，K7）：v5 §4.1「不走开局通道（t=0 语义）；确定性时间点」。
 * 本期唯一词条 = 卫队战吼（WARCRY_TAUNT）：开战 value 秒后，该侧旗手震慑半径内
 * 可行动存活敌人的 targetId 强制指向旗手 value2 秒（嘲讽期豁免全局重评估）。
 */
public final class TimedTriggerSystem {

    /** 开战调度：快照 mechanics 中的 WARCRY_TAUNT（value=延迟秒）入队（BattleSystem.startBattle 调） */
    public void scheduleFrom(BattleState state, SynergySnapshot snapshot, Side side) {
        for (EffectData effect : snapshot.getMechanics()) {
            if (effect.getMechanic() == SynergyMechanic.WARCRY_TAUNT) {
                state.addTimedTrigger(new TimedTrigger(
                        Math.round(effect.getValue() / GameBalance.LOGIC_STEP), effect, side));
            }
        }
    }

    /** step 消费：到期触发（BattleSystem.step 于 beginTick 后调用；入队序 = 消费序） */
    public void advance(BattleState state) {
        List<TimedTrigger> due = new ArrayList<TimedTrigger>();
        state.drainDueTimedTriggers(state.getTick(), due);
        for (TimedTrigger trigger : due) {
            if (trigger.getEffect().getMechanic() == SynergyMechanic.WARCRY_TAUNT) {
                executeWarcry(state, trigger);
            }
        }
    }

    /** 卫队战吼：旗手 = 该侧含机制单位中「最前」（玩家 min y / 敌方 max y，平局 min x）；
     *  曼哈顿 ≤半径 的可行动存活敌人 targetId 强制指向旗手 value2 秒（豁免全局重评估） */
    private void executeWarcry(BattleState state, TimedTrigger trigger) {
        BattleUnit banner = null;
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() != trigger.getSide() || !unit.isActable()
                    || unit.getMechanic(SynergyMechanic.WARCRY_TAUNT) == null) {
                continue;
            }
            int frontness = trigger.getSide() == Side.PLAYER ? -unit.getGridY() : unit.getGridY();
            int bannerFrontness = banner == null ? Integer.MIN_VALUE
                    : (trigger.getSide() == Side.PLAYER ? -banner.getGridY() : banner.getGridY());
            if (banner == null || frontness > bannerFrontness
                    || (frontness == bannerFrontness && unit.getGridX() < banner.getGridX())) {
                banner = unit;
            }
        }
        if (banner == null) {
            return; // 机制持有者全灭/倒地 → 不触发
        }
        int untilTick = state.getTick() + Math.round(
                trigger.getEffect().getValue2() / GameBalance.LOGIC_STEP);
        for (BattleUnit enemy : state.getUnits()) {
            if (enemy.getSide() == trigger.getSide() || !enemy.isActable()) {
                continue;
            }
            int distance = Math.abs(enemy.getGridX() - banner.getGridX())
                    + Math.abs(enemy.getGridY() - banner.getGridY());
            if (distance <= GameBalance.WARCRY_TAUNT_RADIUS) {
                enemy.setTaunted(banner.getId(), untilTick);
            }
        }
    }
}
