package com.voidvvv.kz_auto_chess_n.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UnitShapeKit 测试：图形=种族族、变体=职业 的映射词表与行半宽表（派单渲染要求一，CP9）。
 */
class UnitShapeKitTest {

    @Test
    @DisplayName("种族→图形族：四主族映射 + 未登记风味种族（植物/独眼/构造）STAR 兜底")
    void raceFamilyMapping() {
        assertThat(UnitShapeKit.familyOf("兽人")).isEqualTo(UnitShapeKit.ShapeFamily.TRIANGLE);
        assertThat(UnitShapeKit.familyOf("野兽")).isEqualTo(UnitShapeKit.ShapeFamily.DIAMOND);
        assertThat(UnitShapeKit.familyOf("亡灵")).isEqualTo(UnitShapeKit.ShapeFamily.PENTAGON);
        assertThat(UnitShapeKit.familyOf("巨人")).isEqualTo(UnitShapeKit.ShapeFamily.HEXAGON);
        assertThat(UnitShapeKit.familyOf("植物")).isEqualTo(UnitShapeKit.ShapeFamily.STAR);
        assertThat(UnitShapeKit.familyOf("独眼")).isEqualTo(UnitShapeKit.ShapeFamily.STAR);
        assertThat(UnitShapeKit.familyOf("构造")).isEqualTo(UnitShapeKit.ShapeFamily.STAR);
    }

    @Test
    @DisplayName("职业→族内变体：四职业映射 + Boss/无职业 PLAIN 兜底")
    void classVariantMapping() {
        assertThat(UnitShapeKit.variantOf("战士")).isEqualTo(UnitShapeKit.ClassVariant.WARRIOR);
        assertThat(UnitShapeKit.variantOf("法师")).isEqualTo(UnitShapeKit.ClassVariant.MAGE);
        assertThat(UnitShapeKit.variantOf("刺客")).isEqualTo(UnitShapeKit.ClassVariant.ASSASSIN);
        assertThat(UnitShapeKit.variantOf("游侠")).isEqualTo(UnitShapeKit.ClassVariant.RANGER);
        assertThat(UnitShapeKit.variantOf("Boss")).isEqualTo(UnitShapeKit.ClassVariant.PLAIN);
        assertThat(UnitShapeKit.variantOf("")).isEqualTo(UnitShapeKit.ClassVariant.PLAIN);
    }

    @Test
    @DisplayName("半宽表：全族长度>0 且逐值>0（每行至少 1px，中心对齐）")
    void halfWidthsPositive() {
        for (UnitShapeKit.ShapeFamily family : UnitShapeKit.ShapeFamily.values()) {
            int[] widths = UnitShapeKit.halfWidths(family);
            assertThat(widths).isNotEmpty();
            for (int width : widths) {
                assertThat(width).isPositive();
            }
        }
    }
}
