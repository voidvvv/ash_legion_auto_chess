package com.voidvvv.kz_auto_chess_n.render.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知日志模型测试（CP28；render §5.5）：有界 200 行 FIFO、小窗恰最近 4 行、
 * 单帧 ≤2 行超限丢弃（WARNING-6）、大窗全量、clear。
 */
class NotificationLogTest {

    private static void fill(NotificationLog log, int count) {
        for (int i = 0; i < count; i++) {
            assertThat(log.appendCapped("行" + i, 0)).isTrue();
        }
    }

    @Test
    @DisplayName("追加成功入日志；null/空白行忽略返回 false")
    void appendsAndIgnoresBlank() {
        NotificationLog log = new NotificationLog();

        assertThat(log.appendCapped(null, 0)).isFalse();
        assertThat(log.appendCapped("", 0)).isFalse();
        assertThat(log.appendCapped("   ", 0)).isFalse();
        assertThat(log.appendCapped("买入兽人战士", 0)).isTrue();

        assertThat(log.visibleLines()).containsExactly("买入兽人战士");
    }

    @Test
    @DisplayName("单帧第 3 行起丢弃（appendCapped 返回 false 且不入日志）")
    void dropsBeyondTwoPerFrame() {
        NotificationLog log = new NotificationLog();

        assertThat(log.appendCapped("第1行", 0)).isTrue();
        assertThat(log.appendCapped("第2行", 1)).isTrue();
        assertThat(log.appendCapped("第3行", 2)).isFalse();
        assertThat(log.appendCapped("第4行", 3)).isFalse();

        assertThat(log.visibleLines()).containsExactly("第1行", "第2行");
    }

    @Test
    @DisplayName("小窗恰显示最近 4 行（更早行仍在日志中）")
    void smallWindowShowsLastFour() {
        NotificationLog log = new NotificationLog();
        fill(log, 6);

        List<String> visible = log.visibleLines();
        assertThat(visible).containsExactly("行2", "行3", "行4", "行5");
    }

    @Test
    @DisplayName("不足 4 行时小窗全量显示")
    void smallWindowShowsAllWhenFewer() {
        NotificationLog log = new NotificationLog();
        fill(log, 2);

        assertThat(log.visibleLines()).containsExactly("行0", "行1");
    }

    @Test
    @DisplayName("有界 200：第 201 行挤掉最老行（FIFO）")
    void boundedAt200Fifo() {
        NotificationLog log = new NotificationLog();
        fill(log, 201);
        log.setLargeMode(true); // 大窗全量核对（小窗只回最近 4 行）

        List<String> visible = log.visibleLines();
        assertThat(visible).hasSize(200);
        assertThat(visible.get(0)).isEqualTo("行1"); // 行0 已被挤出
        assertThat(visible.get(199)).isEqualTo("行200");
    }

    @Test
    @DisplayName("大窗模式返回全量（无过滤，WARNING-6）")
    void largeModeReturnsAll() {
        NotificationLog log = new NotificationLog();
        fill(log, 6);
        log.setLargeMode(true);

        assertThat(log.visibleLines()).hasSize(6);
        assertThat(log.isLargeMode()).isTrue();
    }

    @Test
    @DisplayName("toggleLargeMode 翻转大小窗")
    void toggleFlipsMode() {
        NotificationLog log = new NotificationLog();

        assertThat(log.isLargeMode()).isFalse();
        log.toggleLargeMode();
        assertThat(log.isLargeMode()).isTrue();
        log.toggleLargeMode();
        assertThat(log.isLargeMode()).isFalse();
    }

    @Test
    @DisplayName("clear 清空日志（重开新局）")
    void clearEmptiesLog() {
        NotificationLog log = new NotificationLog();
        fill(log, 5);
        log.clear();

        assertThat(log.visibleLines()).isEmpty();
    }
}
