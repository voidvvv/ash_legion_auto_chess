package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.render.board.LerpMotion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * LerpMotion 测试：跳格插值（render §4.2：fromCell/切换时刻 × moveSpeed，渲染帧时钟计时）。
 */
class LerpMotionTest {

    @Test
    @DisplayName("初始 reset 直落：位置即格坐标")
    void resetDropsDirectly() {
        LerpMotion motion = new LerpMotion(1f);
        motion.reset(2, 5);
        assertThat(motion.positionX(0f)).isEqualTo(2f);
        assertThat(motion.positionY(0f)).isEqualTo(5f);
    }

    @Test
    @DisplayName("跳格后按 moveSpeed 推进：半程时刻在中点")
    void interpolatesAtMoveSpeed() {
        LerpMotion motion = new LerpMotion(1f); // 1 格/秒
        motion.reset(2, 5);
        motion.onCellPolled(4, 5, 10f); // t0=10
        assertThat(motion.positionX(10f)).isEqualTo(2f);     // 起跳瞬间仍在旧格
        assertThat(motion.positionX(10.5f)).isCloseTo(3f, within(1e-6f)); // 半程中点
        assertThat(motion.positionY(10.5f)).isEqualTo(5f);
    }

    @Test
    @DisplayName("t≥1 停稳：到达后位置恒为新格")
    void settlesAtTarget() {
        LerpMotion motion = new LerpMotion(2f); // 2 格/秒，半秒走完 1 格
        motion.reset(0, 0);
        motion.onCellPolled(1, 0, 5f);
        assertThat(motion.positionX(5.5f)).isEqualTo(1f);
        assertThat(motion.positionX(100f)).isEqualTo(1f); // 停稳待机
    }

    @Test
    @DisplayName("连续两跳 from 链正确：第二跳以旧目标为起点")
    void chainedJumpsChainFromPreviousTarget() {
        LerpMotion motion = new LerpMotion(1f);
        motion.reset(0, 0);
        motion.onCellPolled(1, 0, 0f);   // 第一跳 0→1
        motion.onCellPolled(2, 0, 10f);  // 第二跳 1→2（from = 旧 to = 1）
        assertThat(motion.positionX(10f)).isEqualTo(1f);
        assertThat(motion.positionX(10.5f)).isCloseTo(1.5f, within(1e-6f));
        assertThat(motion.positionX(11f)).isEqualTo(2f);
    }

    @Test
    @DisplayName("行进中 reset 直落新格（战斗重建场景）")
    void resetDuringFlightDropsToCell() {
        LerpMotion motion = new LerpMotion(1f);
        motion.reset(0, 0);
        motion.onCellPolled(3, 3, 0f);
        motion.reset(3, 3); // 行进一半时直落
        assertThat(motion.positionX(0.01f)).isEqualTo(3f);
        assertThat(motion.positionY(0.01f)).isEqualTo(3f);
    }

    @Test
    @DisplayName("同格重复 poll 无扰动（位置与起点不变）")
    void sameCellPollIsNoOp() {
        LerpMotion motion = new LerpMotion(1f);
        motion.reset(2, 5);
        motion.onCellPolled(2, 5, 1f);
        motion.onCellPolled(2, 5, 2f);
        assertThat(motion.positionX(2f)).isEqualTo(2f);
        assertThat(motion.positionY(2f)).isEqualTo(5f);
    }
}
