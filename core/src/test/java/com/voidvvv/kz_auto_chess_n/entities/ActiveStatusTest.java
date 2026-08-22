package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ActiveStatus 心跳间隔测试（Phase 5 CP7）：
 * 技能/羁绊缺省 1s（DOT_TICK_INTERVAL），装备 passiveStatus 可自定义（龙心 5s）。
 */
class ActiveStatusTest {

    @Test
    @DisplayName("缺省构造：心跳间隔 = DOT_TICK_INTERVAL（技能/羁绊零感知）")
    void defaultConstructorUsesDotTickInterval() {
        ActiveStatus status = new ActiveStatus(StatusType.REGEN, 1, 0.1f, 3f);
        assertThat(status.getTickInterval()).isEqualTo(GameBalance.DOT_TICK_INTERVAL);
    }

    @Test
    @DisplayName("全参构造：自定义心跳间隔透传")
    void fullConstructorPassesTickIntervalThrough() {
        ActiveStatus status = new ActiveStatus(StatusType.REGEN, 1, 0.02f, 10f, 5f);
        assertThat(status.getTickInterval()).isEqualTo(5f);
    }

    @Test
    @DisplayName("心跳间隔 ≤ 0 抛 IllegalArgumentException")
    void rejectsNonPositiveTickInterval() {
        assertThatThrownBy(() -> new ActiveStatus(StatusType.REGEN, 1, 0.02f, 10f, 0f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("心跳间隔");
        assertThatThrownBy(() -> new ActiveStatus(StatusType.REGEN, 1, 0.02f, 10f, -1f))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
