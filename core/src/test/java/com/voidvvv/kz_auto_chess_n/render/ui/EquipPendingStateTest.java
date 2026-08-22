package com.voidvvv.kz_auto_chess_n.render.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 装备待定态测试（CP23；input §2.5 两段式点击中态）：
 * set/clear/hasPending 边界与覆盖语义——纯状态容器直测。
 */
class EquipPendingStateTest {

    @Test
    @DisplayName("初始无待定：hasPending=false、pendingItemId=-1")
    void startsIdle() {
        EquipPendingState pending = new EquipPendingState();
        assertThat(pending.hasPending()).isFalse();
        assertThat(pending.pendingItemId()).isEqualTo(-1);
    }

    @Test
    @DisplayName("set 进入待定态：hasPending=true、id 可读")
    void setMarksPending() {
        EquipPendingState pending = new EquipPendingState();
        pending.set(42);
        assertThat(pending.hasPending()).isTrue();
        assertThat(pending.pendingItemId()).isEqualTo(42);
    }

    @Test
    @DisplayName("clear 回到无待定")
    void clearResets() {
        EquipPendingState pending = new EquipPendingState();
        pending.set(42);
        pending.clear();
        assertThat(pending.hasPending()).isFalse();
        assertThat(pending.pendingItemId()).isEqualTo(-1);
    }

    @Test
    @DisplayName("重复 set 覆盖（换起点物品不残留旧值）")
    void setOverwrites() {
        EquipPendingState pending = new EquipPendingState();
        pending.set(7);
        pending.set(9);
        assertThat(pending.pendingItemId()).isEqualTo(9);
        assertThat(pending.hasPending()).isTrue();
    }

    @Test
    @DisplayName("clear 幂等：无待定时再 clear 无副作用")
    void clearIsIdempotent() {
        EquipPendingState pending = new EquipPendingState();
        pending.clear();
        pending.clear();
        assertThat(pending.hasPending()).isFalse();
    }
}
