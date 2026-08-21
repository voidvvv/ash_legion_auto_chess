package com.voidvvv.kz_auto_chess_n.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DigitGlyph 测试：0~9 的 3×5 点阵（口径 #15：fx_digit_0~9 用点阵绘制）。
 */
class DigitGlyphTest {

    @Test
    @DisplayName("点阵 0~9 齐全且每字至少 2 个亮点")
    void allDigitsPresentWithEnoughPixels() {
        for (int digit = 0; digit <= 9; digit++) {
            int lit = 0;
            for (int row = 0; row < DigitGlyph.ROWS; row++) {
                for (int col = 0; col < DigitGlyph.COLS; col++) {
                    if (DigitGlyph.pixel(digit, row, col)) {
                        lit++;
                    }
                }
            }
            assertThat(lit).as("digit %d 点亮像素数", digit).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("尺寸常量：3 列 × 5 行")
    void dimensions() {
        assertThat(DigitGlyph.COLS).isEqualTo(3);
        assertThat(DigitGlyph.ROWS).isEqualTo(5);
    }

    @Test
    @DisplayName("字形抽查：0 全边框、1 中列、8 与 0 不同")
    void glyphShapes() {
        // 0：顶行与底行全亮
        for (int col = 0; col < 3; col++) {
            assertThat(DigitGlyph.pixel(0, 0, col)).isTrue();
            assertThat(DigitGlyph.pixel(0, 4, col)).isTrue();
        }
        // 1：中列全亮
        for (int row = 0; row < 5; row++) {
            assertThat(DigitGlyph.pixel(1, row, 1)).isTrue();
        }
        // 8 与 0 至少一处不同（中行补中点）
        boolean differs = false;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 3; col++) {
                if (DigitGlyph.pixel(8, row, col) != DigitGlyph.pixel(0, row, col)) {
                    differs = true;
                }
            }
        }
        assertThat(differs).isTrue();
    }

    @Test
    @DisplayName("越界数字抛 IllegalArgumentException（防御）")
    void rejectsOutOfRangeDigit() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> DigitGlyph.pixel(-1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> DigitGlyph.pixel(10, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
