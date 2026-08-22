package com.voidvvv.kz_auto_chess_n.render.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 悬停驻留状态机测试（Phase 5.1 CP5，裁决 A：~250ms 固定锚悬停卡）：
 * ARMING（候选变化即重置）→ VISIBLE；候选为空或 suppressed 立即隐藏并清零。
 * 纯逻辑零 Gdx，headless 直测。
 */
class HoverStateMachineTest {

    @Test
    @DisplayName("驻留不足不显示：0.1s × 2 帧（累计 0.2s < 0.25s）")
    void insufficientDwellStaysHidden() {
        HoverStateMachine machine = new HoverStateMachine();
        machine.update(1, false, 0.1f);
        machine.update(1, false, 0.1f);
        assertThat(machine.visibleId()).isEqualTo(-1);
    }

    @Test
    @DisplayName("累计 ≥ 0.25s 显示：0.13 + 0.13 = 0.26s → 可见且等于候选")
    void cumulativeDwellShows() {
        HoverStateMachine machine = new HoverStateMachine();
        machine.update(1, false, 0.13f);
        assertThat(machine.visibleId()).isEqualTo(-1);
        machine.update(1, false, 0.13f);
        assertThat(machine.visibleId()).isEqualTo(1);
    }

    @Test
    @DisplayName("候选变化重置计时：换候选即隐藏，重新计满 0.25s 才显示新候选（同格微移不重抖）")
    void candidateChangeResetsTimer() {
        HoverStateMachine machine = new HoverStateMachine();
        machine.update(1, false, 0.2f); // 候选 1 计时中（0.2s < 0.25s 未显示）
        machine.update(2, false, 0.2f); // 换候选：计时重置，0.2s 仍隐藏
        assertThat(machine.visibleId()).isEqualTo(-1);
        machine.update(2, false, 0.04f); // 0.24s 仍不足
        assertThat(machine.visibleId()).isEqualTo(-1);
        machine.update(2, false, 0.01f); // 补满 0.25s → 显示新候选
        assertThat(machine.visibleId()).isEqualTo(2);
    }

    @Test
    @DisplayName("suppressed 立即隐藏并清零：解除后需重新计满 0.25s")
    void suppressedHidesImmediatelyAndClearsTimer() {
        HoverStateMachine machine = new HoverStateMachine();
        machine.update(1, false, 0.3f); // 已显示
        assertThat(machine.visibleId()).isEqualTo(1);
        machine.update(1, true, 0.1f); // suppress 一帧：立即隐藏
        assertThat(machine.visibleId()).isEqualTo(-1);
        machine.update(1, false, 0.24f); // 解除后 0.24s 仍不足
        assertThat(machine.visibleId()).isEqualTo(-1);
        machine.update(1, false, 0.01f); // 补满 0.25s
        assertThat(machine.visibleId()).isEqualTo(1);
    }

    @Test
    @DisplayName("candidate=-1 同 suppressed：无候选立即隐藏清零")
    void noCandidateBehavesLikeSuppressed() {
        HoverStateMachine machine = new HoverStateMachine();
        machine.update(1, false, 0.3f);
        assertThat(machine.visibleId()).isEqualTo(1);
        machine.update(-1, false, 0.1f);
        assertThat(machine.visibleId()).isEqualTo(-1);
        machine.update(-1, false, 1f); // 候选为空累计多久都不显示
        assertThat(machine.visibleId()).isEqualTo(-1);
    }

    @Test
    @DisplayName("显示后持续 update 保持显示（不闪烁、无需重新计时）")
    void visibleStateHoldsAcrossUpdates() {
        HoverStateMachine machine = new HoverStateMachine();
        machine.update(1, false, 0.25f);
        assertThat(machine.visibleId()).isEqualTo(1);
        for (int i = 0; i < 10; i++) {
            machine.update(1, false, 0.016f);
            assertThat(machine.visibleId()).as("第 %d 帧保持显示", i).isEqualTo(1);
        }
    }
}
