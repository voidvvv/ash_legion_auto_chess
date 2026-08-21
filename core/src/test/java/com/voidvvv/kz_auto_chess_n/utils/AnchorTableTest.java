package com.voidvvv.kz_auto_chess_n.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 锚点分段线性插值表测试（敌方人口曲线与商店费阶概率共用，data_schema §十锚点表的插值工具）。
 */
class AnchorTableTest {

    @Test
    @DisplayName("精确命中锚点时返回锚点值")
    void returnsAnchorValueOnExactKey() {
        AnchorTable table = new AnchorTable(new float[]{1, 5, 10}, new float[]{100, 70, 50});
        assertThat(table.valueAt(1f)).isEqualTo(100f);
        assertThat(table.valueAt(5f)).isEqualTo(70f);
        assertThat(table.valueAt(10f)).isEqualTo(50f);
    }

    @Test
    @DisplayName("锚点之间线性插值")
    void interpolatesLinearlyBetweenAnchors() {
        AnchorTable table = new AnchorTable(new float[]{1, 5, 10}, new float[]{100, 70, 50});
        // 中点 3：100→70 的中点 = 85
        assertThat(table.valueAt(3f)).isCloseTo(85f, within(1e-6f));
        // 中点 7.5：70→50 的中点 = 60
        assertThat(table.valueAt(7.5f)).isCloseTo(60f, within(1e-6f));
    }

    @Test
    @DisplayName("低于首锚点取首值，高于末锚点取末值")
    void clampsToEndpointValuesOutOfRange() {
        AnchorTable table = new AnchorTable(new float[]{3, 5}, new float[]{100, 70});
        assertThat(table.valueAt(1f)).isEqualTo(100f);
        assertThat(table.valueAt(2.99f)).isEqualTo(100f);
        assertThat(table.valueAt(5f)).isEqualTo(70f);
        assertThat(table.valueAt(99f)).isEqualTo(70f);
    }

    @Test
    @DisplayName("构造校验：键不升序/重复/长度不匹配/空表时拒绝")
    void rejectsInvalidConstruction() {
        assertThatThrownBy(() -> new AnchorTable(new float[]{1, 5, 3}, new float[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnchorTable(new float[]{1, 5}, new float[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnchorTable(new float[]{1, 1}, new float[]{1, 2}))
                .isInstanceOf(IllegalArgumentException.class); // 重复键
        assertThatThrownBy(() -> new AnchorTable(new float[]{}, new float[]{}))
                .isInstanceOf(IllegalArgumentException.class); // 空表
    }
}
