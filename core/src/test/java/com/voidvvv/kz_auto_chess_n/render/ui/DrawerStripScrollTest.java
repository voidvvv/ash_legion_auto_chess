package com.voidvvv.kz_auto_chess_n.render.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 头像条横向滚动纯逻辑（UI 抽屉化批2·Q4：可见 6、滚轮/滑块看余下 3）——首槽下标钳制、滑块位置 ↔ 下标往返。
 */
class DrawerStripScrollTest {

    @Test
    @DisplayName("最大首槽下标：备战席条目 ≤ 可见 6 槽 → 0（不可滚）；7 → 1；9（满席）→ 3")
    void maxScrollFollowsBenchCount() {
        assertThat(DrawerStripScroll.maxScroll(0)).isZero();
        assertThat(DrawerStripScroll.maxScroll(6)).isZero();
        assertThat(DrawerStripScroll.maxScroll(7)).isEqualTo(1);
        assertThat(DrawerStripScroll.maxScroll(com.voidvvv.kz_auto_chess_n.config.GameBalance.BENCH_SIZE)).isEqualTo(3);
    }

    @Test
    @DisplayName("钳制与步进：下标恒在 [0, max]；滚轮正步 = 看后面的槽（内容左移），到头不越界")
    void clampAndStep() {
        assertThat(DrawerStripScroll.clamp(-3, 9)).isZero();
        assertThat(DrawerStripScroll.clamp(5, 9)).isEqualTo(3);
        assertThat(DrawerStripScroll.clamp(2, 4)).as("条目缩到 ≤ 6 时归零").isZero();
        assertThat(DrawerStripScroll.scroll(0, 9, 1)).isEqualTo(1);
        assertThat(DrawerStripScroll.scroll(1, 9, 2)).isEqualTo(3);
        assertThat(DrawerStripScroll.scroll(3, 9, 1)).as("到头钳住").isEqualTo(3);
        assertThat(DrawerStripScroll.scroll(1, 9, -1)).isZero();
        assertThat(DrawerStripScroll.scroll(0, 9, -1)).isZero();
        assertThat(DrawerStripScroll.scroll(0, 5, 1)).as("不可滚时步进无效").isZero();
    }

    @Test
    @DisplayName("滑块位置：轨 224 / 滑块 12 → 行程 212；首槽 0 → 左端，满席首槽 3 → 右端；不可滚 → 停左端")
    void knobOffsetWithinTrack() {
        int track = DrawerGeometry.stripScrollBar().width();
        int knob = 12;
        assertThat(track).isEqualTo(224);
        assertThat(DrawerStripScroll.knobOffsetX(track, knob, 0, 9)).isZero();
        assertThat(DrawerStripScroll.knobOffsetX(track, knob, 3, 9)).isEqualTo(212);
        assertThat(DrawerStripScroll.knobOffsetX(track, knob, 1, 9)).isEqualTo(71);
        assertThat(DrawerStripScroll.knobOffsetX(track, knob, 2, 9)).isEqualTo(141);
        assertThat(DrawerStripScroll.knobOffsetX(track, knob, 7, 9)).as("越界下标先钳制").isEqualTo(212);
        assertThat(DrawerStripScroll.knobOffsetX(track, knob, 2, 4)).as("不可滚停左端").isZero();
    }

    @Test
    @DisplayName("拖滑块：轨内指针偏移 → 首槽下标；与 knobOffsetX 往返一致；轨外/两端钳制")
    void dragKnobRoundTrip() {
        int track = DrawerGeometry.stripScrollBar().width();
        int knob = 12;
        for (int index = 0; index <= 3; index++) {
            int offset = DrawerStripScroll.knobOffsetX(track, knob, index, 9);
            assertThat(DrawerStripScroll.indexForKnobOffset(track, knob, offset, 9)).isEqualTo(index);
        }
        assertThat(DrawerStripScroll.indexForKnobOffset(track, knob, -50, 9)).isZero();
        assertThat(DrawerStripScroll.indexForKnobOffset(track, knob, 9999, 9)).isEqualTo(3);
        assertThat(DrawerStripScroll.indexForKnobOffset(track, knob, 100, 5)).as("不可滚恒 0").isZero();
    }
}
