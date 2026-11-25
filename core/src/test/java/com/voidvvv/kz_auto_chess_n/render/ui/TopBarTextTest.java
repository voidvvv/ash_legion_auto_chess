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
                .isEqualTo("轮次 25/25  金币 0  等级 3（5/12）"); // 经验表压平（修订二）：Lv.3→4 = 12
        assertThat(TopBar.statusText(12, 47, 6, 55))
                .isEqualTo("轮次 12/25  金币 47  等级 6（55/36）"); // 经验表压平（修订二）：Lv.6→7 = 36
    }

    @Test
    @DisplayName("同值两次调用结果相等（值变更才 setText 的判定基准）")
    void deterministicForSameInputs() {
        assertThat(TopBar.statusText(4, 9, 2, 3))
                .isEqualTo(TopBar.statusText(4, 9, 2, 3));
    }

    // —— 品质改造 §0.1/§0.2：局标题前缀与轮次上限（载体 = RunContext，装配期口径单点） ——

    @Test
    @DisplayName("测试档标题：前缀「测试档 999」，其余字段与正式口径同构（品质改造 §0.1）")
    void testPeriodTitle() {
        assertThat(TopBar.statusText(1, 25, 999, 1, 0, "测试档 999"))
                .isEqualTo("测试档 999  轮次 1/25  金币 999  等级 1（0/4）");
        assertThat(TopBar.statusText(25, 25, 999, 7, 0, "测试档 999"))
                .isEqualTo("测试档 999  轮次 25/25  金币 999  等级 7（满级）");
    }

    @Test
    @DisplayName("对拍标题：前缀「对拍 A · 42」且轮次上限 3（品质改造 §0.2）")
    void sampleDuelTitle() {
        assertThat(TopBar.statusText(2, 3, 7, 1, 0, "对拍 A · 42"))
                .isEqualTo("对拍 A · 42  轮次 2/3  金币 7  等级 1（0/4）");
        assertThat(TopBar.statusText(3, 3, 0, 2, 4, "对拍 B · 44"))
                .isEqualTo("对拍 B · 44  轮次 3/3  金币 0  等级 2（4/8）");
    }

    @Test
    @DisplayName("正式局：无标题前缀且轮次上限 25（与既有 4 参口径逐字一致，存量调用零漂移）")
    void normalModeMatchesLegacySignature() {
        assertThat(TopBar.statusText(7, 25, 33, 7, 0, null))
                .isEqualTo(TopBar.statusText(7, 33, 7, 0))
                .isEqualTo("轮次 7/25  金币 33  等级 7（满级）");
        assertThat(TopBar.statusText(1, 25, 999, 1, 0, null)).doesNotContain("测试档");
    }
}
