package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.StatusType;

import java.util.Objects;

/**
 * 活跃状态实例（battle §7.1 结构）：挂在 BattleUnit 上的受控可变条目。
 *
 * <p>power 语义随 type 不同（battle §7.2 / 口径 #9/#10）：
 * DOT（BLEED/POISON）= 每跳点数；REGEN = maxHp 比例/跳；ATK_UP 等属性类 = 百分点；SHIELD = 吸收点数。
 *
 * <p>duration 缺省无限：SHIELD 传 {@link Float#POSITIVE_INFINITY}（战斗期常驻）。
 */
public final class ActiveStatus {
    private final StatusType type;
    /** 施加者 unit id；开局效果等无单位来源时为 -1 */
    private final int sourceId;
    private float remainingTime;
    /** DOT/REGEN 的 1s 心跳累积器（施加时 0，首跳在满 1s——口径 #10） */
    private float tickTimer;
    private float power;

    public ActiveStatus(StatusType type, int sourceId, float power, float duration) {
        this.type = Objects.requireNonNull(type, "type 不能为 null");
        this.sourceId = sourceId;
        this.power = power;
        this.remainingTime = duration;
    }

    public StatusType getType() { return type; }
    public int getSourceId() { return sourceId; }
    public float getRemainingTime() { return remainingTime; }
    public float getTickTimer() { return tickTimer; }
    public float getPower() { return power; }

    public boolean isExpired() {
        return remainingTime <= 0f;
    }

    /** framework-internal：仅供 systems 包在战斗作用域内调用（口径 #22）。刷新合并（口径 #11）与心跳推进写这里。 */
    public void setRemainingTime(float remainingTime) {
        this.remainingTime = remainingTime;
    }

    /** framework-internal：同 type 刷新时 power 取更大（口径 #11） */
    public void setPower(float power) {
        this.power = power;
    }

    /** framework-internal：DOT/REGEN 心跳结转（tickTimer -= 间隔） */
    public void setTickTimer(float tickTimer) {
        this.tickTimer = tickTimer;
    }
}
