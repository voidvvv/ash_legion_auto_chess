package com.voidvvv.kz_auto_chess_n.data;

/** 不死参数（units.json "undying"；誓约未竟=单位属性非亡语，C1） */
public final class UndyingSpec {
    private final float reviveDelaySeconds;
    private final float reviveHpRatio;

    public UndyingSpec(float reviveDelaySeconds, float reviveHpRatio) {
        this.reviveDelaySeconds = reviveDelaySeconds;
        this.reviveHpRatio = reviveHpRatio;
    }
    public float getReviveDelaySeconds() { return reviveDelaySeconds; }
    public float getReviveHpRatio() { return reviveHpRatio; }
}
