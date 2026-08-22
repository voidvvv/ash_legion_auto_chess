package com.voidvvv.kz_auto_chess_n.data;

import java.util.Objects;

/** 穿着期常驻状态（data_schema §八 passiveStatus：type/power/tick 秒）——装备入口进 StatusSystem 的第二种形态 */
public final class EquipmentPassive {
    private final StatusType type;
    private final float power;
    private final float tickInterval;

    public EquipmentPassive(StatusType type, float power, float tickInterval) {
        this.type = Objects.requireNonNull(type, "type 不能为 null");
        this.power = power;
        this.tickInterval = tickInterval;
    }

    public StatusType getType() { return type; }
    /** REGEN 语义：maxHp 比例/跳（battle §7.2） */
    public float getPower() { return power; }
    /** 心跳间隔（秒）；技能/羁绊缺省 1s */
    public float getTickInterval() { return tickInterval; }
}
