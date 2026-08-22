package com.voidvvv.kz_auto_chess_n.render.ui;

/**
 * 悬停显示状态机（Phase 5.1 R1，裁决 A：固定锚悬停卡）：候选源稳定驻留
 * ≥ {@link #DELAY_SECONDS} 才显示。纯逻辑零 Gdx（headless 可测）；
 * GL 线程单实例（沿 BoardInputProcessor.DragContext 可变先例）。
 *
 * <p>状态：ARMING（计时期，候选变化即重置）→ VISIBLE（稳定命中保持显示）；
 * 任一帧候选为空或 suppressed（拖拽/模态/暂停冻结）立即隐藏并清零计时。
 * 抑制条件的施加位置见计划 §5.3-8（查询侧归一，本类只认 candidate 与 suppressed）。
 */
public final class HoverStateMachine {

    /** 悬停驻留阈值（裁决 A：~250ms） */
    public static final float DELAY_SECONDS = 0.25f;

    private int candidate = -1;
    private float elapsed;
    private int visible = -1;

    /**
     * 每帧推进。
     *
     * @param candidate 当前候选源 id（棋盘 unitId / 商店槽位索引；无候选 = -1）
     * @param suppressed 冻结位（BattleScreen frozen：paused || 弹窗模态）
     * @param delta      帧间隔秒（冻结时调用方传 0）
     */
    public void update(int candidate, boolean suppressed, float delta) {
        if (candidate != this.candidate) {
            this.candidate = candidate;
            this.elapsed = 0f;
            this.visible = -1;
        }
        if (candidate < 0 || suppressed) {
            this.elapsed = 0f;
            this.visible = -1;
            return;
        }
        if (this.visible >= 0) {
            return; // 已显示：保持（无需继续累计）
        }
        this.elapsed += delta;
        if (this.elapsed >= DELAY_SECONDS) {
            this.visible = candidate;
        }
    }

    /** 当前可见候选 id（未显示 = -1） */
    public int visibleId() {
        return visible;
    }
}
