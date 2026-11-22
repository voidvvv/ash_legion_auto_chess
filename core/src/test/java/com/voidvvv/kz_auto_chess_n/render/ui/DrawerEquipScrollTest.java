package com.voidvvv.kz_auto_chess_n.render.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 装备网格纵向滚动纯逻辑（UI 抽屉化批3·Q1：3 列 × 2 行可见 + 滚动；取消「前 6 件 +N」）。
 */
class DrawerEquipScrollTest {

    @Test
    @DisplayName("行数 / 最大首行：0 件 → 0 行；≤6 件不可滚；7 件 → 1；12 件 → 2；13 件 → 3")
    void rowCountAndMaxFirstRow() {
        assertThat(DrawerEquipScroll.rowCount(0)).isZero();
        assertThat(DrawerEquipScroll.rowCount(1)).isEqualTo(1);
        assertThat(DrawerEquipScroll.rowCount(6)).isEqualTo(2);
        assertThat(DrawerEquipScroll.rowCount(7)).isEqualTo(3);
        assertThat(DrawerEquipScroll.maxFirstRow(0)).isZero();
        assertThat(DrawerEquipScroll.maxFirstRow(6)).isZero();
        assertThat(DrawerEquipScroll.maxFirstRow(7)).isEqualTo(1);
        assertThat(DrawerEquipScroll.maxFirstRow(12)).isEqualTo(2);
        assertThat(DrawerEquipScroll.maxFirstRow(13)).isEqualTo(3);
    }

    @Test
    @DisplayName("钳制与步进：首行恒在 [0, max]；滚轮正步 = 看后面的行；条目缩水自动归零")
    void clampAndScroll() {
        assertThat(DrawerEquipScroll.clampFirstRow(-2, 20)).isZero();
        assertThat(DrawerEquipScroll.clampFirstRow(9, 13)).isEqualTo(3);
        assertThat(DrawerEquipScroll.clampFirstRow(2, 4)).as("缩到 ≤ 6 件归零").isZero();
        assertThat(DrawerEquipScroll.scrollRows(0, 13, 1)).isEqualTo(1);
        assertThat(DrawerEquipScroll.scrollRows(1, 13, 5)).as("到头钳住").isEqualTo(3);
        assertThat(DrawerEquipScroll.scrollRows(1, 13, -1)).isZero();
        assertThat(DrawerEquipScroll.scrollRows(0, 5, 1)).as("不可滚时步进无效").isZero();
    }

    @Test
    @DisplayName("首行 → 首个可见槽下标 = 首行 × 3（可见 6 槽映射库存 [firstIndex, firstIndex+6)）")
    void firstIndexIsRowTimesCols() {
        assertThat(DrawerEquipScroll.firstIndex(0)).isZero();
        assertThat(DrawerEquipScroll.firstIndex(3)).isEqualTo(9);
    }

    @Test
    @DisplayName("滑块位置 ↔ 首行往返：轨 72 / 滑块 8 → 行程 64；首行 0 停底端、首行到最大到顶端；不可滚停底端")
    void knobOffsetRoundTrip() {
        int track = DrawerGeometry.equipScrollBar().height();
        int knob = DrawerGeometry.EQUIP_KNOB;
        assertThat(track).isEqualTo(72);
        assertThat(DrawerEquipScroll.knobOffsetY(track, knob, 0, 13)).isZero();
        assertThat(DrawerEquipScroll.knobOffsetY(track, knob, 3, 13)).isEqualTo(64);
        assertThat(DrawerEquipScroll.knobOffsetY(track, knob, 1, 13)).isEqualTo(21);
        assertThat(DrawerEquipScroll.knobOffsetY(track, knob, 9, 13)).as("越界先钳制").isEqualTo(64);
        assertThat(DrawerEquipScroll.knobOffsetY(track, knob, 1, 4)).as("不可滚停底端").isZero();
        for (int row = 0; row <= 3; row++) {
            int offset = DrawerEquipScroll.knobOffsetY(track, knob, row, 13);
            assertThat(DrawerEquipScroll.firstRowForKnob(track, knob, offset, 13)).isEqualTo(row);
        }
        assertThat(DrawerEquipScroll.firstRowForKnob(track, knob, -9, 13)).isZero();
        assertThat(DrawerEquipScroll.firstRowForKnob(track, knob, 999, 13)).isEqualTo(3);
        assertThat(DrawerEquipScroll.firstRowForKnob(track, knob, 30, 5)).as("不可滚恒 0").isZero();
    }
}
