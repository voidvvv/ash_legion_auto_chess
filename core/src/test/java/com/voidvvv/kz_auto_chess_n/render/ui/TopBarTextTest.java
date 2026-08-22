package com.voidvvv.kz_auto_chess_n.render.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TopBar 文案数据源测试（CP21；CP10 中文化）：轮次/金币/等级+经验的单行拼装与 Lv.7 满级口径。
 * 纯静态函数直测——UI Actor 本体不 headless 测（Phase 4 先例，绘制走 lwjgl3 手验）。
 */
class TopBarTextTest {

    @Test
    @DisplayName("开局文案：轮次 1/25 + 金币 10 + 等级 1（0/4）")
    void openingText() {
        assertThat(TopBar.statusText(1, 10, 1, 0))
                .isEqualTo("轮次 1/25  金币 10  等级 1（0/4）");
    }

    @Test
    @DisplayName("Lv.7 封顶显示 满级（expToNextLevel(7)=0）")
    void maxLevelShowsMax() {
        assertThat(TopBar.statusText(7, 33, 7, 0))
                .isEqualTo("轮次 7/25  金币 33  等级 7（满级）");
    }

    @Test
    @DisplayName("中期数值随动：round/金币/经验进度逐字段核对")
    void midRunFields() {
        assertThat(TopBar.statusText(25, 0, 3, 5))
                .isEqualTo("轮次 25/25  金币 0  等级 3（5/16）");
        assertThat(TopBar.statusText(12, 47, 6, 55))
                .isEqualTo("轮次 12/25  金币 47  等级 6（55/56）");
    }

    @Test
    @DisplayName("同值两次调用结果相等（值变更才 setText 的判定基准）")
    void deterministicForSameInputs() {
        assertThat(TopBar.statusText(4, 9, 2, 3))
                .isEqualTo(TopBar.statusText(4, 9, 2, 3));
    }
}
