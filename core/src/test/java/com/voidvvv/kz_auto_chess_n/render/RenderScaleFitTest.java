package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.render.ui.DrawerGeometry;
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
        // 世界域元件（BattleRenderer 经 worldCamera 绘制）：④ 棋盘 / ⑦ 出售区；② 备战席矩形 = 退役前左列极端坐标样本（③ 背包已迁抽屉，见 DrawerGeometryTest）
        int[][] elements = {
                {BoardGeometry.BOARD_X, BoardGeometry.BOARD_Y, BoardGeometry.BOARD_W, BoardGeometry.BOARD_H},
                {BoardGeometry.BENCH_X, BoardGeometry.BENCH_Y, BoardGeometry.BENCH_W, BoardGeometry.BENCH_H},
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
                BoardGeometry.SELL_ZONE_X, BoardGeometry.SELL_ZONE_Y,
                BoardGeometry.SELL_ZONE_W, BoardGeometry.SELL_ZONE_H))
                .as("出售区完整可见").isTrue();
    }

    // —— UI 抽屉化批1：抽屉（不缩放层——舞台几何固定，不随 s 缩放；Q7 甲案 244×218 / Q8） ——

    /** 展开态抽屉与 ④ 棋盘左缘的舞台间隙（备战期真实世界缩放；负值 = 压棋盘） */
    private static float drawerBoardGap(float scale) {
        float boardLeft = BoardGeometry.screenX(BoardGeometry.BOARD_X, scale / GameBalance.SHOPPING_CAMERA_ZOOM);
        return boardLeft - (DrawerGeometry.DRAWER_X + DrawerGeometry.DRAWER_W);
    }

    @Test
    @DisplayName("抽屉两态完整在视口内：s∈{0.7~1.0} 展开态主体 / 收起态把手与角标均在 640×360 舞台内；收起态主体完全出屏")
    void drawerStatesStayInsideViewportAtAllShrinkScales() {
        float[] viewport = {0, 0, BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H};
        for (float scale : SHRINK_SCALES) {
            DrawerGeometry.Rect expanded = DrawerGeometry.drawerRect(1f);
            assertThat(contains(viewport, expanded.x(), expanded.y(), expanded.width(), expanded.height()))
                    .as("s=%f 展开态主体完整在视口内", scale).isTrue();
            DrawerGeometry.Rect handle = DrawerGeometry.handleRect();
            assertThat(contains(viewport, handle.x(), handle.y(), handle.width(), handle.height()))
                    .as("s=%f 把手完整在视口内", scale).isTrue();
            DrawerGeometry.Rect badge = DrawerGeometry.badgeRect();
            assertThat(contains(viewport, badge.x(), badge.y(), badge.width(), badge.height()))
                    .as("s=%f 角标完整在视口内", scale).isTrue();
            assertThat(DrawerGeometry.drawerRect(0f).right())
                    .as("s=%f 收起态主体右缘 ≤ 0（完全出屏，不露边）", scale).isLessThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("抽屉屏幕右缘 ≤ 棋盘屏幕左缘：生产档 s=0.8 及 0.7 两态成立（展开态右缘 246、收起态把手右缘 14；备战期真实世界缩放）")
    void drawerClearsBoardLeftEdgeAtSupportedScales() {
        for (float scale : new float[]{0.7f, 0.8f}) {
            assertThat(drawerBoardGap(scale)).as("s=%f 展开态：抽屉右缘 ≤ 棋盘左缘", scale).isPositive();
            assertThat(drawerBoardGap(scale) + DrawerGeometry.DRAWER_X + DrawerGeometry.DRAWER_W
                    - DrawerGeometry.handleRect().right())
                    .as("s=%f 收起态：把手右缘 ≤ 棋盘左缘", scale).isPositive();
        }
        assertThat(drawerBoardGap(GameBalance.RENDER_GLOBAL_SCALE)).isCloseTo(1.55f, within(0.01f));
    }

    @Test
    @DisplayName("边界锁（计划 §4.5「矩阵 {0.7~1.0} 右缘 ≤ 棋盘左缘」中不可满足的部分）：抽屉舞台几何按 s=0.8 定制，仅 s∈[0.793,0.817] 不压 ④⑧①")
    void drawerLeftColumnFitBoundaryLock() {
        // 推导（舞台坐标）：x：246 ≤ 320 − 96·s/1.06 ⇔ s ≤ 0.8171；
        //                  y 下：88 ≥ 180 − 116·s（⑧ 商店栏顶 64）⇔ s ≥ 0.7931；y 上：306 ≤ 180 + 160·s（① 顶栏文字底 340）⇔ s ≥ 0.7875
        float maxByBoard = (BoardGeometry.VIRTUAL_W / 2f - DrawerGeometry.DRAWER_X - DrawerGeometry.DRAWER_W)
                * GameBalance.SHOPPING_CAMERA_ZOOM / (BoardGeometry.VIRTUAL_W / 2f - BoardGeometry.BOARD_X);
        float minByShopBar = (BoardGeometry.VIRTUAL_H / 2f - DrawerGeometry.DRAWER_Y)
                / (BoardGeometry.VIRTUAL_H / 2f - (BoardGeometry.VIRTUAL_H - BoardGeometry.SHOP_BAR_Y));
        float minByTopBar = (DrawerGeometry.DRAWER_Y + DrawerGeometry.DRAWER_H - BoardGeometry.VIRTUAL_H / 2f)
                / (BoardGeometry.VIRTUAL_H - 18 - 2 - BoardGeometry.VIRTUAL_H / 2f);
        assertThat(maxByBoard).isCloseTo(0.8171f, within(1e-4f));
        assertThat(minByShopBar).isCloseTo(0.7931f, within(1e-4f));
        assertThat(minByTopBar).isCloseTo(0.7875f, within(1e-4f));
        // 生产档 0.8 落在可用带内
        float s = GameBalance.RENDER_GLOBAL_SCALE;
        assertThat(s).isBetween(minByShopBar, maxByBoard);
        // 矩阵其余档位逐一说明越界方向（改 RENDER_GLOBAL_SCALE 出带即须重校 DrawerGeometry）
        assertThat(drawerBoardGap(0.7f)).as("s=0.7：x 不压棋盘").isPositive();
        assertThat(BoardGeometry.screenY(BoardGeometry.VIRTUAL_H - BoardGeometry.SHOP_BAR_Y, 0.7f))
                .as("s=0.7：⑧ 商店栏顶 98.8 > 抽屉底 88（压商店栏）").isGreaterThan(DrawerGeometry.DRAWER_Y);
        assertThat(drawerBoardGap(0.9f)).as("s=0.9：压棋盘第 0 列").isNegative();
        assertThat(drawerBoardGap(1.0f)).as("s=1.0：压棋盘第 0 列").isNegative();
        // 可用带端点：0.795 三项皆成立；0.79 压商店栏；0.82 压棋盘
        assertThat(drawerBoardGap(0.795f)).isPositive();
        assertThat(BoardGeometry.screenY(BoardGeometry.VIRTUAL_H - BoardGeometry.SHOP_BAR_Y, 0.795f))
                .isLessThanOrEqualTo(DrawerGeometry.DRAWER_Y);
        assertThat(BoardGeometry.screenY(BoardGeometry.VIRTUAL_H - 18 - 2, 0.795f))
                .isGreaterThanOrEqualTo(DrawerGeometry.DRAWER_Y + DrawerGeometry.DRAWER_H);
        assertThat(BoardGeometry.screenY(BoardGeometry.VIRTUAL_H - BoardGeometry.SHOP_BAR_Y, 0.79f))
                .isGreaterThan(DrawerGeometry.DRAWER_Y);
        assertThat(drawerBoardGap(0.82f)).isNegative();
    }
}
