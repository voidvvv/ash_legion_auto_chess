package com.voidvvv.kz_auto_chess_n.screens;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试样片菜单文案验收件（品质改造 §0.2）：入口/两题/返回按钮文案对照设计书原文，
 * 种子按钮文案随共用种子刷新（42 → 43 → 44 → 42，循环公式归 PaiTestScript）。
 */
@DisplayName("SampleMenuScreen 文案：测试样片菜单按钮与种子（品质改造 §0.2）")
class SampleMenuScreenTextTest {

    @Test
    @DisplayName("按钮文案：破阵踏对拍 / 处决对拍 / 返回（§0.2 原文）")
    void buttonLabels() {
        assertThat(SampleMenuScreen.BUTTON_DUEL_A).isEqualTo("破阵踏对拍");
        assertThat(SampleMenuScreen.BUTTON_DUEL_B).isEqualTo("处决对拍");
        assertThat(SampleMenuScreen.BUTTON_BACK).isEqualTo("返回");
    }

    @Test
    @DisplayName("种子按钮文案：种子 42（默认 42——§0.2 共用一组种子按钮）")
    void seedLabelShowsCurrentSeed() {
        assertThat(SampleMenuScreen.seedLabel(42)).isEqualTo("种子 42");
        assertThat(SampleMenuScreen.seedLabel(43)).isEqualTo("种子 43");
        assertThat(SampleMenuScreen.seedLabel(44)).isEqualTo("种子 44");
    }
}
