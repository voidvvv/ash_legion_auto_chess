package com.voidvvv.kz_auto_chess_n.render.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 战前提示文案排版验收件（品质改造 §1.1，纯函数）：单威胁 = 规则行 + 开局预判说明；
 * 双威胁 = 「开局预判」栏头缩写入首行 + 两条正文各占一行；几何常量对照设计书原值。
 * 字宽实测在运行时由字体测量，此处以 12px/字 × 字数 ≤ 可用宽度作静态上界断言。
 */
@DisplayName("战前提示文案与几何：单/双威胁两行装配（品质改造 §1.1）")
class ThreatHintLinesTest {

    @Test
    @DisplayName("无威胁：返回空数组（不绘制提示区）")
    void noThreatProducesNoLines() {
        assertThat(BoardGeometry.threatHintLines(0, BoardGeometry.THREAT_COPY_BREAK_STOMP, null))
                .isEmpty();
    }

    @Test
    @DisplayName("单威胁：第一行规则原文 + 第二行「开局预判，目标会随交战变化」（§1.1 原文）")
    void singleThreatLines() {
        assertThat(BoardGeometry.threatHintLines(1,
                BoardGeometry.THREAT_COPY_BREAK_STOMP, null))
                .containsExactly("破阵踏：目标及相邻四格，拉开站位", "开局预判，目标会随交战变化");
        assertThat(BoardGeometry.threatHintLines(1,
                BoardGeometry.THREAT_COPY_EXECUTE, null))
                .containsExactly("处决：优先盯最靠后的棋子，让战士站最后", "开局预判，目标会随交战变化");
    }

    @Test
    @DisplayName("双威胁：「开局预判」栏头缩写入首行 + 规则 A / 规则 B 各占一行（维持可读空间）")
    void twoThreatLines() {
        assertThat(BoardGeometry.threatHintLines(2,
                BoardGeometry.THREAT_COPY_BREAK_STOMP, BoardGeometry.THREAT_COPY_EXECUTE))
                .containsExactly("开局预判 破阵踏：目标及相邻四格，拉开站位",
                        "处决：优先盯最靠后的棋子，让战士站最后");
    }

    @Test
    @DisplayName("双威胁缺第二条正文：按单威胁口径回退（防御，恒 ≤2 条威胁）")
    void twoThreatsWithoutSecondCopyFallsBack() {
        assertThat(BoardGeometry.threatHintLines(2,
                BoardGeometry.THREAT_COPY_BREAK_STOMP, null))
                .containsExactly(BoardGeometry.THREAT_COPY_BREAK_STOMP,
                        BoardGeometry.THREAT_NOTE_SINGLE);
    }

    @Test
    @DisplayName("几何常量：提示区 (224,14,324,32)、两行基线 42/28（§1.1 原值）")
    void hintGeometryMatchesSpec() {
        assertThat(BoardGeometry.THREAT_HINT_X).isEqualTo(224);
        assertThat(BoardGeometry.THREAT_HINT_Y).isEqualTo(14);
        assertThat(BoardGeometry.THREAT_HINT_W).isEqualTo(324);
        assertThat(BoardGeometry.THREAT_HINT_H).isEqualTo(32);
        assertThat(BoardGeometry.THREAT_HINT_LINE1_Y).isEqualTo(42);
        assertThat(BoardGeometry.THREAT_HINT_LINE2_Y).isEqualTo(28);
        assertThat(BoardGeometry.THREAT_HINT_LINE1_Y)
                .isGreaterThan(BoardGeometry.THREAT_HINT_LINE2_Y); // y-up：第一行在上
    }

    @Test
    @DisplayName("文案宽度上界：任一行 12px/字 × 字数 ≤ 提示区可用宽度（字体缺文件也不溢出设计区）")
    void linesFitInsideHintRect() {
        int usable = BoardGeometry.THREAT_HINT_W - BoardGeometry.THREAT_HINT_TEXT_INSET_PX;
        for (String line : BoardGeometry.threatHintLines(2,
                BoardGeometry.THREAT_COPY_BREAK_STOMP, BoardGeometry.THREAT_COPY_EXECUTE)) {
            assertThat(line.length() * 12)
                    .as("行宽上界（12px 位图字体，%s）", line).isLessThanOrEqualTo(usable);
        }
    }
}
