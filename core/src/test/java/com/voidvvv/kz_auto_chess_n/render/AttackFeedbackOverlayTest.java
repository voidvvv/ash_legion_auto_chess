package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.render.board.UnitAnimState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 攻击摆动 / 受击抖动叠加层测试（attack_feedback FP1/FP2/FP3）：
 * 公式探针（1 来回衰减正弦 / 整数像素阶梯）、重复触发刷新满、不占状态位、
 * 施法不抑制、死亡立即清零且死后忽略触发、摆动与抖动并行不互斥、常量锚定。
 */
class AttackFeedbackOverlayTest {

    private static final float T = UnitAnimState.ATTACK_SWING_SECONDS;     // 0.25f
    private static final float D = UnitAnimState.HIT_SHAKE_SECONDS;        // 0.15f
    private static final float P = UnitAnimState.HIT_SHAKE_PERIOD_SECONDS; // 0.07f

    /** 触发摆动并推进到 elapsed 秒 */
    private static UnitAnimState swingAt(float elapsed) {
        UnitAnimState state = new UnitAnimState();
        state.triggerAttackSwing();
        state.update(elapsed);
        return state;
    }

    /** 触发抖动（给定轴）并推进到 elapsed 秒 */
    private static UnitAnimState shakeAt(float elapsed, int axis) {
        UnitAnimState state = new UnitAnimState();
        state.triggerHitShake(axis);
        state.update(elapsed);
        return state;
    }

    @Test
    @DisplayName("未触发：摆角/摆动位移/抖动位移全为 0")
    void idleStateHasNoOverlayOffset() {
        UnitAnimState state = new UnitAnimState();
        assertThat(state.attackSwingDegrees()).isZero();
        assertThat(state.attackSwingDx()).isZero();
        assertThat(state.hitShakeDx()).isZero();
    }

    @Test
    @DisplayName("旋转公式：T/4 前倾峰值 +0.75A=4.5°、T/2 归零、3T/4 回摆 −0.25A=−1.5°、T 播完归零")
    void swingDegreesFormula() {
        assertThat(swingAt(T / 4f).attackSwingDegrees()).isCloseTo(4.5f, within(1e-4f));
        assertThat(swingAt(T / 2f).attackSwingDegrees()).isCloseTo(0f, within(1e-4f));
        assertThat(swingAt(3f * T / 4f).attackSwingDegrees()).isCloseTo(-1.5f, within(1e-4f));
        assertThat(swingAt(T).attackSwingDegrees()).isZero();
    }

    @Test
    @DisplayName("平移阶梯：+2 → 0 → −2 → 0（1 个来回，整数像素）")
    void swingTranslateLadder() {
        assertThat(swingAt(T / 4f).attackSwingDx()).isEqualTo(2);
        assertThat(swingAt(T / 2f).attackSwingDx()).isEqualTo(0);
        assertThat(swingAt(3f * T / 4f).attackSwingDx()).isEqualTo(-2);
        assertThat(swingAt(T).attackSwingDx()).isEqualTo(0);
    }

    @Test
    @DisplayName("抖动阶梯与线性衰减（axis=+1）：t=P/4 → +2、3P/4 → −1、5P/4 → +1、7P/4 → 0、播完 0")
    void hitShakeLadderDecays() {
        assertThat(shakeAt(P / 4f, 1).hitShakeDx()).isEqualTo(2);
        assertThat(shakeAt(P / 2f, 1).hitShakeDx()).isEqualTo(0);
        assertThat(shakeAt(3f * P / 4f, 1).hitShakeDx()).isEqualTo(-1);
        assertThat(shakeAt(5f * P / 4f, 1).hitShakeDx()).isEqualTo(1);
        assertThat(shakeAt(7f * P / 4f, 1).hitShakeDx()).isEqualTo(0);
        assertThat(shakeAt(D, 1).hitShakeDx()).isEqualTo(0);
    }

    @Test
    @DisplayName("抖动方向轴：axis=−1 全程取反；axis=0 归一为 +1")
    void hitShakeAxisSign() {
        assertThat(shakeAt(P / 4f, -1).hitShakeDx()).isEqualTo(-2);
        assertThat(shakeAt(3f * P / 4f, -1).hitShakeDx()).isEqualTo(1);
        assertThat(shakeAt(P / 4f, 0).hitShakeDx()).isEqualTo(2);
    }

    @Test
    @DisplayName("重复触发刷新满计时（高攻速连击 → 持续小幅摆动；同 HitFlash 惯例）")
    void retriggerRefreshes() {
        UnitAnimState state = new UnitAnimState();
        state.triggerAttackSwing();
        state.update(T / 2f);
        state.triggerAttackSwing(); // 刷新
        state.update(T / 4f);
        assertThat(state.attackSwingDegrees()).isCloseTo(4.5f, within(1e-4f));

        state.triggerHitShake(1);
        state.update(D / 2f);
        state.triggerHitShake(1); // 刷新
        state.update(P / 4f);
        assertThat(state.hitShakeDx()).isEqualTo(2);
    }

    @Test
    @DisplayName("不占状态位：触发摆动/抖动后动画态仍 Idle")
    void overlaysDoNotOccupyAnimState() {
        UnitAnimState state = new UnitAnimState();
        state.triggerAttackSwing();
        state.triggerHitShake(1);
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.IDLE);
    }

    @Test
    @DisplayName("施法不抑制：CAST 只切动画态，摆动叠加层照播（FP3）")
    void castDoesNotSuppressSwing() {
        UnitAnimState state = new UnitAnimState();
        state.triggerAttackSwing();
        state.onEvent(CombatEvent.Type.CAST);
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.CAST);
        state.update(T / 4f);
        assertThat(state.attackSwingDegrees()).isCloseTo(4.5f, within(1e-4f));
    }

    @Test
    @DisplayName("死亡立即清零：DEATH 锁定瞬间摆动/抖动中止（不与缩放淡出叠加）")
    void deathClearsOverlays() {
        UnitAnimState state = new UnitAnimState();
        state.triggerAttackSwing();
        state.triggerHitShake(1);
        state.update(0.05f);
        assertThat(state.attackSwingDegrees()).isNotZero(); // 前置：确实在播
        state.onEvent(CombatEvent.Type.UNIT_DIED);
        assertThat(state.attackSwingDegrees()).isZero();
        assertThat(state.attackSwingDx()).isZero();
        assertThat(state.hitShakeDx()).isZero();
    }

    @Test
    @DisplayName("死亡后忽略新触发：出手同拍被反杀不残留反馈")
    void deathIgnoresNewTriggers() {
        UnitAnimState state = new UnitAnimState();
        state.onEvent(CombatEvent.Type.UNIT_DIED);
        state.triggerAttackSwing();
        state.triggerHitShake(1);
        assertThat(state.attackSwingDegrees()).isZero();
        assertThat(state.hitShakeDx()).isZero();
    }

    @Test
    @DisplayName("并行不互斥：同一单位刚出手即被打，两计时器各播各的")
    void swingAndShakeRunInParallel() {
        UnitAnimState state = new UnitAnimState();
        state.triggerAttackSwing();
        state.triggerHitShake(1);
        state.update(P / 4f); // t=0.0175：抖动首拍 +2，摆动早期前倾约 2.376°
        assertThat(state.hitShakeDx()).isEqualTo(2);
        assertThat(state.attackSwingDegrees()).isCloseTo(2.376f, within(1e-2f));
    }

    @Test
    @DisplayName("常量锚定（GDD §8 缺省值）：ROTATE / 6° / 2px / 0.25s / 2px / 0.15s / 0.07s")
    void constantsMatchGddDefaults() {
        assertThat(UnitAnimState.ATTACK_SWING_MODE).isEqualTo(UnitAnimState.AttackSwingMode.ROTATE);
        assertThat(UnitAnimState.ATTACK_SWING_DEGREES).isEqualTo(6f);
        assertThat(UnitAnimState.ATTACK_SWING_PIXELS).isEqualTo(2f);
        assertThat(UnitAnimState.ATTACK_SWING_SECONDS).isEqualTo(0.25f);
        assertThat(UnitAnimState.HIT_SHAKE_PIXELS).isEqualTo(2f);
        assertThat(UnitAnimState.HIT_SHAKE_SECONDS).isEqualTo(0.15f);
        assertThat(UnitAnimState.HIT_SHAKE_PERIOD_SECONDS).isEqualTo(0.07f);
    }
}
