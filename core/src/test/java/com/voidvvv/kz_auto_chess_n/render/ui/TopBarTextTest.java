package com.voidvvv.kz_auto_chess_n.render.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TopBar 文案数据源测试（CP21）：轮次/金币/等级+经验的单行拼装与 Lv.7 MAX 口径。
 * 纯静态函数直测——UI Actor 本体不 headless 测（Phase 4 先例，绘制走 lwjgl3 手验）。
 */
class TopBarTextTest {

    @Test
    @DisplayName("开局文案：ROUND 1/25 + GOLD 10 + LV 1 (0/4)")
    void openingText() {
        assertThat(TopBar.statusText(1, 10, 1, 0))
                .isEqualTo("ROUND 1/25  GOLD 10  LV 1 (0/4)");
    }

    @Test
    @DisplayName("Lv.7 封顶显示 MAX（expToNextLevel(7)=0）")
    void maxLevelShowsMax() {
        assertThat(TopBar.statusText(7, 33, 7, 0))
                .isEqualTo("ROUND 7/25  GOLD 33  LV 7 (MAX)");
    }

    @Test
    @DisplayName("中期数值随动：round/金币/经验进度逐字段核对")
    void midRunFields() {
        assertThat(TopBar.statusText(25, 0, 3, 5))
                .isEqualTo("ROUND 25/25  GOLD 0  LV 3 (5/16)");
        assertThat(TopBar.statusText(12, 47, 6, 55))
                .isEqualTo("ROUND 12/25  GOLD 47  LV 6 (55/56)");
    }

    @Test
    @DisplayName("同值两次调用结果相等（值变更才 setText 的判定基准）")
    void deterministicForSameInputs() {
        assertThat(TopBar.statusText(4, 9, 2, 3))
                .isEqualTo(TopBar.statusText(4, 9, 2, 3));
    }
}
