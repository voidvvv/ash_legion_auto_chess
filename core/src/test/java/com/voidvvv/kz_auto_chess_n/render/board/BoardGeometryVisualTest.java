package com.voidvvv.kz_auto_chess_n.render.board;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §1.3 棋盘视觉焦点几何/色彩验收件（纯 JVM）：棋体 38px（外接框 ≈+19%）、底边 cy−16 锚点、
 * 脚下影子 18×4、条带基线、合法落点绿线内缩、三区格底亮度差 ≤4、矩形相交。
 */
@DisplayName("§1.3 棋盘视觉焦点几何与色彩（品质改造批4）")
class BoardGeometryVisualTest {

    /** 相对亮度（Rec.709 加权，0~255 刻度——设计书「邻格亮度差最多 4 RGB 数值」的度量口径） */
    private static float luminance255(Color color) {
        return 0.2126f * color.r * 255f + 0.7152f * color.g * 255f + 0.0722f * color.b * 255f;
    }

    @Test
    @DisplayName("棋体 38px 正方：外接框较 32px 格 +18.75%（≈设计书「约大 19%」）")
    void unitDrawSizeIs38() {
        assertThat(BoardGeometry.UNIT_DRAW_PX).isEqualTo(38);
        assertThat((BoardGeometry.UNIT_DRAW_PX - BoardGeometry.CELL) / (float) BoardGeometry.CELL)
                .as("外接框放大比例").isBetween(0.18f, 0.20f);
    }

    @Test
    @DisplayName("底边锚点 cy−16 与横向居中：脚部基准不变、格命中不受棋体放大影响")
    void footAnchorAndHorizontalCentering() {
        assertThat(BoardGeometry.UNIT_FOOT_ANCHOR_Y).isEqualTo(16);
        assertThat(BoardGeometry.unitDrawBottom(100)).isEqualTo(84);          // cy − 16
        assertThat(BoardGeometry.unitDrawLeft(300)).isEqualTo(281);           // cx − 19（横向居中）
        assertThat(BoardGeometry.unitDrawLeft(300) + BoardGeometry.UNIT_DRAW_PX / 2f)
                .isEqualTo(300f);                                             // 体中心 = 格中心
    }

    @Test
    @DisplayName("脚下影子 18×4，铺在棋体底缘（格内底缘）")
    void shadowFootprint() {
        assertThat(BoardGeometry.UNIT_SHADOW_W).isEqualTo(18);
        assertThat(BoardGeometry.UNIT_SHADOW_H).isEqualTo(4);
        assertThat(BoardGeometry.unitDrawBottom(100)).isEqualTo(84); // 影子底缘 = 棋体底缘
    }

    @Test
    @DisplayName("条带基线：血条 24×3 @cy+21、能量 @cy+25、充能 @cy+27（§1.3）")
    void barBaselines() {
        assertThat(BoardGeometry.BAR_HP_W).isEqualTo(24);
        assertThat(BoardGeometry.BAR_HP_H).isEqualTo(3);
        assertThat(BoardGeometry.BAR_HP_BASELINE).isEqualTo(21);
        assertThat(BoardGeometry.BAR_ENERGY_BASELINE).isEqualTo(25);
        assertThat(BoardGeometry.BAR_CHARGE_BASELINE).isEqualTo(27);
        assertThat(BoardGeometry.BAR_ENERGY_BASELINE - BoardGeometry.BAR_HP_BASELINE)
                .isEqualTo(BoardGeometry.BAR_HP_H + 1); // 血条之上留 1px 空隙
    }

    @Test
    @DisplayName("合法落点绿线内缩 5px（§1.3：与威胁边均露出）")
    void dropOkLineInset() {
        assertThat(BoardGeometry.DROP_OK_LINE_INSET_PX).isEqualTo(5);
        assertThat(BoardGeometry.DROP_OK_LINE_INSET_PX).isGreaterThan(2); // 与威胁边内缩 2px 区分（两层同读）
    }

    @Test
    @DisplayName("三区格底亮度差 ≤4（§1.3：邻格亮度差最多 4 RGB 数值；直读 BattleRenderer 三区常量）")
    void zoneLuminanceGapWithinFour() {
        float enemyL = luminance255(BattleRenderer.ENEMY_ZONE_TINT);
        float bufferL = luminance255(BattleRenderer.BUFFER_ZONE_TINT);
        float playerL = luminance255(BattleRenderer.PLAYER_ZONE_TINT);
        assertThat(Math.abs(bufferL - enemyL)).as("敌区↔缓冲带").isLessThanOrEqualTo(4f);
        assertThat(Math.abs(playerL - bufferL)).as("缓冲带↔友区").isLessThanOrEqualTo(4f);
    }

    @Test
    @DisplayName("矩形相交：相交/相离/贴边（§1.5b 展开通知重叠闸的几何基准）")
    void rectsOverlap() {
        assertThat(BoardGeometry.rectsOverlap(0, 0, 10, 10, 5, 5, 10, 10)).isTrue();
        assertThat(BoardGeometry.rectsOverlap(0, 0, 10, 10, 20, 20, 5, 5)).isFalse();
        assertThat(BoardGeometry.rectsOverlap(0, 0, 10, 10, 10, 0, 5, 5)).isFalse(); // 边贴边不相交
        assertThat(BoardGeometry.rectsOverlap(0, 0, 10, 10, -5, -5, 5, 5)).isFalse();
    }
}
