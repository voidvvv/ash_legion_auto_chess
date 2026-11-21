package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 整体渲染缩放布局数学断言（2026-11-21 回归修复固化：0.8 缩放后备战席/装备栏/出售区出屏）。
 * 根因公式：libGDX zoom = 可见世界 ÷ 画布（>1 拉远）——内容缩小 s 倍 ⇔ zoom = 基准/s（除法）；
 * 初版误乘 s（zoom=0.8 = 放大 1.25）→ 世界域屏幕映射 screen(v)=(v−320)/s'+320 挤出视口。
 * 两域（世界相机 / UI Stage 根缩放）统一映射 screen(v) = (v−C)·s + C，同中心 ⇒ 相对布局零变化。
 */
class RenderScaleFitTest {

    /** 缩小档位矩阵（0.7~1.0 = 全部布局必须完整可见；1.25 = 放大档——数学上必然溢出，单测锁定边界语义） */
    private static final float[] SHRINK_SCALES = {0.7f, 0.8f, 0.9f, 1.0f};

    private static boolean contains(float[] rect, float x, float y, float w, float h) {
        return rect[0] <= x && rect[1] <= y && x + w <= rect[0] + rect[2] && y + h <= rect[1] + rect[3];
    }

    @Test
    @DisplayName("缩放档位矩阵：s∈{0.7,0.8,0.9,1.0} 下可见世界矩形 ⊇ 全部 640×360 世界域元件")
    void worldElementsFitVisibleRectAtAllShrinkScales() {
        // 世界域元件（BattleRenderer 经 worldCamera 绘制）：④ 棋盘 / ② 备战席 / ⑦ 出售区
        int[][] elements = {
                {BoardGeometry.BOARD_X, BoardGeometry.BOARD_Y, BoardGeometry.BOARD_W, BoardGeometry.BOARD_H},
                {BoardGeometry.BENCH_X, BoardGeometry.BENCH_Y, BoardGeometry.BENCH_W, BoardGeometry.BENCH_H},
                {BoardGeometry.INVENTORY_X, BoardGeometry.INVENTORY_Y, BoardGeometry.INVENTORY_W, BoardGeometry.INVENTORY_H},
                {BoardGeometry.SELL_ZONE_X, BoardGeometry.SELL_ZONE_Y, BoardGeometry.SELL_ZONE_W, BoardGeometry.SELL_ZONE_H},
        };
        for (float scale : SHRINK_SCALES) {
            // 战斗稳态 zoom = 1/s 是可见性最紧档（SHOPPING 1.06/s 更宽）——绑定约束按最紧断言
            float[] visible = BoardGeometry.visibleWorldRect(1f / scale);
            assertThat(visible[2]).as("s=%f 可见宽 = 640/s", scale).isCloseTo(640f / scale, within(1e-4f));
            assertThat(contains(visible, 0, 0, 640, 360)).as("s=%f 全画布 ⊆ 可见世界", scale).isTrue();
            for (int[] e : elements) {
                assertThat(contains(visible, e[0], e[1], e[2], e[3]))
                        .as("s=%f 世界元件 (%d,%d,%d,%d) 完整可见", scale, e[0], e[1], e[2], e[3]).isTrue();
            }
            // SHOPPING 拉远档（1.06/s）⊇ 战斗档（更宽，天然覆盖）
            float[] shopping = BoardGeometry.visibleWorldRect(1.06f / scale);
            assertThat(shopping[2]).isGreaterThan(visible[2]);
        }
    }

    @Test
    @DisplayName("缩放档位矩阵：s∈{0.7,0.8,0.9,1.0} 下 UI 内容矩形 ⊆ 640×360 视口（边缘锚定 UI 件随块整体在界内）")
    void uiContentRectStaysInsideViewport() {
        for (float scale : SHRINK_SCALES) {
            float[] ui = BoardGeometry.uiContentRect(scale);
            assertThat(contains(new float[]{0, 0, 640, 360}, ui[0], ui[1], ui[2], ui[3]))
                    .as("s=%f UI 内容块完整在视口内", scale).isTrue();
        }
    }

    @Test
    @DisplayName("两域同中心：UI 内容矩形与可见世界矩形共享画布中心（统一映射 screen(v)=(v−C)·s+C）")
    void bothDomainsShareScreenCenter() {
        for (float scale : SHRINK_SCALES) {
            float[] ui = BoardGeometry.uiContentRect(scale);
            float[] world = BoardGeometry.visibleWorldRect(1f / scale);
            assertThat(ui[0] + ui[2] / 2f).as("s=%f 两域中心 x 一致", scale).isEqualTo(320f);
            assertThat(ui[1] + ui[3] / 2f).as("s=%f 两域中心 y 一致", scale).isEqualTo(180f);
            assertThat(world[0] + world[2] / 2f).isEqualTo(320f);
            assertThat(world[1] + world[3] / 2f).isEqualTo(180f);
            // 统一映射自检：世界 x=0 与 UI 子坐标 x=0 屏幕位置一致（同式）
            assertThat(BoardGeometry.screenX(0f, scale))
                    .isCloseTo(320f * (1f - scale), within(1e-3f));
            assertThat(BoardGeometry.screenY(0f, scale))
                    .isCloseTo(180f * (1f - scale), within(1e-3f));
        }
    }

    @Test
    @DisplayName("悬停卡联动：任意缩放档棋盘卡屏幕右缘 ≤ 棋盘屏幕左缘（跨域走廊不侵入）")
    void hoverCardCorridorClearOfBoardAtAllScales() {
        for (float scale : SHRINK_SCALES) {
            float cardRight = BoardGeometry.screenX(
                    BoardGeometry.BOARD_HOVER_X + BoardGeometry.BOARD_HOVER_W, scale);
            float boardLeft = BoardGeometry.screenX(BoardGeometry.BOARD_X, scale);
            assertThat(cardRight).as("s=%f 卡右缘 %.1f ≤ 棋盘左缘 %.1f", scale, cardRight, boardLeft)
                    .isLessThanOrEqualTo(boardLeft);
        }
    }

    @Test
    @DisplayName("边界语义锁定：s=1.25（放大档）数学上必然溢出——备战席左缘/出售区右缘出屏坐标即回归公式")
    void magnifyScaleOverflowsByFormula() {
        float s = 1.25f;
        float[] visible = BoardGeometry.visibleWorldRect(1f / s); // zoom = 0.8 = 放大
        assertThat(visible[2]).isEqualTo(512f); // 640×0.8
        assertThat(visible[0]).isEqualTo(64f);  // 可见带 [64,576]
        // 出屏公式：screen(v) = (v−320)×1.25 + 320 —— 备战席左缘 20 → −55（左出）；出售区右缘 620 → 695（右出）
        assertThat(BoardGeometry.screenX(BoardGeometry.BENCH_X, s)).isEqualTo(-55f);
        assertThat(BoardGeometry.screenX(BoardGeometry.SELL_ZONE_X + BoardGeometry.SELL_ZONE_W, s))
                .isEqualTo(695f);
        // 放大档不是合法「整体缩小」配置：全画布不再 ⊆ 可见世界（语义边界，防止再次误用乘法公式）
        assertThat(contains(visible, 0, 0, 640, 360)).isFalse();
    }

    @Test
    @DisplayName("回归锚点：s=0.8 正确公式下备战席四角全部落界内（修复前公式致左缘 −55 出屏）")
    void regressionBenchFullyVisibleAtCurrentScale() {
        float s = GameBalance.RENDER_GLOBAL_SCALE;
        float[] visible = BoardGeometry.visibleWorldRect(1f / s);
        assertThat(contains(visible,
                BoardGeometry.BENCH_X, BoardGeometry.BENCH_Y, BoardGeometry.BENCH_W, BoardGeometry.BENCH_H))
                .as("备战席完整可见").isTrue();
        assertThat(contains(visible,
                BoardGeometry.INVENTORY_X, BoardGeometry.INVENTORY_Y,
                BoardGeometry.INVENTORY_W, BoardGeometry.INVENTORY_H))
                .as("装备背包区完整可见").isTrue();
        assertThat(contains(visible,
                BoardGeometry.SELL_ZONE_X, BoardGeometry.SELL_ZONE_Y,
                BoardGeometry.SELL_ZONE_W, BoardGeometry.SELL_ZONE_H))
                .as("出售区完整可见").isTrue();
    }
}
