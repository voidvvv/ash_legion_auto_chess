package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.render.board.HitShakeAxis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 受击抖动水平轴决策测试（attack_feedback FP2）：攻击者来向 / 同列奇偶回退 /
 * 无攻击者（sourceId=-1 / 视图缺失）奇偶回退 / 返回值恒 ±1。
 */
class HitShakeAxisTest {

    @Test
    @DisplayName("攻击者在受击者左侧 → +1（首拍向右弹开）；右侧 → −1")
    void resolveByAttackerSide() {
        assertThat(HitShakeAxis.resolve(100, 200, 7)).isEqualTo(1);
        assertThat(HitShakeAxis.resolve(300, 200, 8)).isEqualTo(-1);
    }

    @Test
    @DisplayName("攻击者与受击者同列（x 相等）→ 按受击者 id 奇偶回退")
    void resolveSameColumnFallsBackToParity() {
        assertThat(HitShakeAxis.resolve(200, 200, 8)).isEqualTo(1);
        assertThat(HitShakeAxis.resolve(200, 200, 7)).isEqualTo(-1);
    }

    @Test
    @DisplayName("无攻击者回退：id 偶 +1 / 奇 −1（确定性，无随机源；负 id 防御）")
    void fallbackByParity() {
        assertThat(HitShakeAxis.fallback(2)).isEqualTo(1);
        assertThat(HitShakeAxis.fallback(3)).isEqualTo(-1);
        assertThat(HitShakeAxis.fallback(-4)).isEqualTo(1);
        assertThat(HitShakeAxis.fallback(-3)).isEqualTo(-1);
    }

    @Test
    @DisplayName("返回值恒为 ±1（不允许 0 或其他幅度）")
    void alwaysUnitVector() {
        for (int attackerX = -5; attackerX <= 5; attackerX++) {
            for (int targetId = 0; targetId < 5; targetId++) {
                int axis = HitShakeAxis.resolve(attackerX, 0, targetId);
                assertThat(Math.abs(axis)).isEqualTo(1);
            }
        }
    }
}
