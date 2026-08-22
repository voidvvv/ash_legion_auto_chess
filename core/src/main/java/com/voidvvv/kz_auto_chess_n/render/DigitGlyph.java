package com.voidvvv.kz_auto_chess_n.render;

/**
 * 0~9 的 3×5 点阵（口径 #15：fx_digit_0~9 用点阵绘制；纯数据零 Gdx）。
 * 行 × 列，true = 点亮像素。
 */
public final class DigitGlyph {

    public static final int COLS = 3;
    public static final int ROWS = 5;

    private static final boolean[][][] GLYPHS = {
            // 0
            {{true, true, true}, {true, false, true}, {true, false, true}, {true, false, true}, {true, true, true}},
            // 1
            {{false, true, false}, {true, true, false}, {false, true, false}, {false, true, false}, {true, true, true}},
            // 2
            {{true, true, true}, {false, false, true}, {true, true, true}, {true, false, false}, {true, true, true}},
            // 3
            {{true, true, true}, {false, false, true}, {true, true, true}, {false, false, true}, {true, true, true}},
            // 4
            {{true, false, true}, {true, false, true}, {true, true, true}, {false, false, true}, {false, false, true}},
            // 5
            {{true, true, true}, {true, false, false}, {true, true, true}, {false, false, true}, {true, true, true}},
            // 6
            {{true, true, true}, {true, false, false}, {true, true, true}, {true, false, true}, {true, true, true}},
            // 7
            {{true, true, true}, {false, false, true}, {false, false, true}, {false, true, false}, {false, true, false}},
            // 8
            {{true, true, true}, {true, false, true}, {true, true, true}, {true, false, true}, {true, true, true}},
            // 9
            {{true, true, true}, {true, false, true}, {true, true, true}, {false, false, true}, {true, true, true}},
    };

    private DigitGlyph() {
    }

    /** 点阵像素：digit 0~9、row 0~4、col 0~2 */
    public static boolean pixel(int digit, int row, int col) {
        if (digit < 0 || digit > 9) {
            throw new IllegalArgumentException("数字必须在 0~9，实际=" + digit);
        }
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            throw new IllegalArgumentException(
                    "点阵坐标必须在 row 0~" + (ROWS - 1) + " / col 0~" + (COLS - 1) + "，实际=(" + row + "," + col + ")");
        }
        return GLYPHS[digit][row][col];
    }
}
