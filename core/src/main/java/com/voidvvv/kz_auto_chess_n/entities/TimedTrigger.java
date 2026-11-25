package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.EffectData;

import java.util.Objects;

/**
 * 定时触发器（CP23，K7）：确定性时间点触发的机制载荷——不走开局通道（t=0 语义），
 * 由 BattleSystem.startBattle 调度入 BattleState、step 逐 tick 消费。
 *
 * <p>不可变小记录：atTick 到期逻辑 tick、effect 机制词条（本期唯一词条 = WARCRY_TAUNT）、
 * side 触发侧。入队序 = 调度序 = 消费序（同 tick 多触发按先入队先执行）。
 */
public final class TimedTrigger {
    private final int atTick;
    private final EffectData effect;
    private final Side side;

    public TimedTrigger(int atTick, EffectData effect, Side side) {
        this.atTick = atTick;
        this.effect = Objects.requireNonNull(effect, "effect 不能为 null");
        this.side = Objects.requireNonNull(side, "side 不能为 null");
    }

    public int getAtTick() {
        return atTick;
    }

    public EffectData getEffect() {
        return effect;
    }

    public Side getSide() {
        return side;
    }
}
