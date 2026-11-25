package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知面板绘制闸与附着守卫验收件（品质改造 §1.5b，headless）：
 * 同 BattleState 连刷多帧只前进游标（修同实例逐帧 attach 从头回读）、隐藏期 / 悬停卡重叠的
 * 绘制闸谓词、按当前字体测宽省略（日志存入保留长版本）。
 */
@DisplayName("通知绘制闸与附着守卫（品质改造 §1.5b）")
class NotificationPanelGateTest {

    private static final NotificationFormat.WidthMeasurer FAKE_MEASURE =
            text -> text == null ? 0f : text.length() * 10f; // 夹具：每字符 10px

    // —— 附着守卫：同 BattleState 连刷三帧只前进游标（§1.5b 验收） ——

    @Test
    @DisplayName("同 BattleState 连续三帧 syncBattle：事件只追加一次，不从头回读")
    void sameBattleStateAcrossFramesAppendsOnce() {
        NotificationPanel.BattleScope scope = new NotificationPanel.BattleScope();
        BattleState state = BattleTestFixtures.state(
                BattleTestFixtures.placed(1, Side.PLAYER, BattleTestFixtures.tpl("u_a"), 2, 4));
        state.record(CombatEvent.cast(10, 1, -1, "sk_x"));

        scope.sync(state);
        int[] firstFrame = {0};
        scope.forEachNew(event -> firstFrame[0]++);
        assertThat(firstFrame[0]).as("第一帧消费 1 条").isEqualTo(1);

        state.record(CombatEvent.unitDied(20, 1));
        scope.sync(state); // 同实例再同步：守卫拦下重附着
        int[] secondFrame = {0};
        scope.forEachNew(event -> secondFrame[0]++);
        assertThat(secondFrame[0]).as("同实例重同步只消费新增段（修复前会从头回读 2 条）").isEqualTo(1);

        scope.sync(state); // 第三帧：无新事件
        int[] thirdFrame = {0};
        scope.forEachNew(event -> thirdFrame[0]++);
        assertThat(thirdFrame[0]).as("第三帧零追加").isEqualTo(0);
    }

    @Test
    @DisplayName("战斗实例更换：新引用重附着（新战斗从零消费），置 null 解除附着")
    void scopeRebindsOnReferenceChange() {
        NotificationPanel.BattleScope scope = new NotificationPanel.BattleScope();
        BattleState first = BattleTestFixtures.state(
                BattleTestFixtures.placed(1, Side.PLAYER, BattleTestFixtures.tpl("u_a"), 2, 4));
        first.record(CombatEvent.cast(10, 1, -1, "sk_x"));
        scope.sync(first);
        int[] consumed = {0};
        scope.forEachNew(event -> consumed[0]++);
        assertThat(consumed[0]).isEqualTo(1);

        BattleState next = BattleTestFixtures.state(
                BattleTestFixtures.placed(2, Side.PLAYER, BattleTestFixtures.tpl("u_a"), 2, 4));
        scope.sync(next); // 新战斗实例 → 重附着（回放流第 0 条语义）
        int[] rebound = {0};
        scope.forEachNew(event -> rebound[0]++);
        assertThat(rebound[0]).as("新实例无事件").isEqualTo(0);

        scope.sync(null);
        int[] afterDetach = {0};
        scope.forEachNew(event -> afterDetach[0]++);
        assertThat(afterDetach[0]).as("未附着不消费").isEqualTo(0);
        assertThat(scope.boundState()).isNull();
    }

    // —— 绘制闸谓词 ——

    @Test
    @DisplayName("隐藏规则：抽屉展开 / 结果终局 / 弹窗可见任一成立 → 只收集不画（§1.5b）")
    void suppressedByLayout() {
        assertThat(NotificationPanel.suppressedByLayout(true, false, false)).isTrue();
        assertThat(NotificationPanel.suppressedByLayout(false, true, false)).isTrue();
        assertThat(NotificationPanel.suppressedByLayout(false, false, true)).isTrue();
        assertThat(NotificationPanel.suppressedByLayout(false, false, false)).isFalse();
        assertThat(NotificationPanel.isResultOrEndPhase(GamePhase.RESULT)).isTrue();
        assertThat(NotificationPanel.isResultOrEndPhase(GamePhase.RUN_END)).isTrue();
        assertThat(NotificationPanel.isResultOrEndPhase(GamePhase.SHOPPING)).isFalse();
        assertThat(NotificationPanel.isResultOrEndPhase(GamePhase.BATTLE)).isFalse();
    }

    @Test
    @DisplayName("重叠闸：仅展开态且与可见悬停卡矩形相交才暂停绘制（小窗恒不触发）")
    void overlapsVisibleHoverCard() {
        // 与展开窗 (20,92,196,188) 相交：x 100..150 落在 20..216、y 200..250 落在 92..280
        assertThat(NotificationPanel.overlapsVisibleHoverCard(true, new int[]{100, 200, 50, 50})).isTrue();
        // 不相交（悬停卡在右侧远端）
        assertThat(NotificationPanel.overlapsVisibleHoverCard(true, new int[]{300, 300, 50, 50})).isFalse();
        // 小窗不判重叠（位置恒在两锚区之外）
        assertThat(NotificationPanel.overlapsVisibleHoverCard(false, new int[]{100, 200, 50, 50})).isFalse();
        assertThat(NotificationPanel.overlapsVisibleHoverCard(true, null)).isFalse();
    }

    @Test
    @DisplayName("小窗与两悬停卡锚区几何不相交（§1.5b：重叠闸恒不误伤常驻小窗）")
    void smallWindowNeverOverlapsHoverAnchors() {
        int sx = com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.NOTIFY_SMALL_X;
        int sy = com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.NOTIFY_SMALL_Y;
        int sw = com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.NOTIFY_SMALL_W;
        int sh = com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.NOTIFY_SMALL_H;
        assertThat(com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.rectsOverlap(
                sx, sy, sw, sh,
                com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.BOARD_HOVER_X,
                com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.BOARD_HOVER_Y,
                com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.BOARD_HOVER_W,
                com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.BOARD_HOVER_H)).isFalse();
        assertThat(com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.rectsOverlap(
                sx, sy, sw, sh,
                com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.SHOP_HOVER_X,
                com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.SHOP_HOVER_Y,
                com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.SHOP_HOVER_W,
                com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.SHOP_HOVER_H)).isFalse();
    }

    // —— 按测宽省略（两尺寸共用实现；§1.5b「按当前字体测宽」） ——

    @Test
    @DisplayName("ellipsize：未超宽原样返回；超宽取最长可容纳前缀 + 「…」")
    void ellipsizeTruncatesByMeasuredWidth() {
        assertThat(NotificationFormat.ellipsize("abc", 30f, FAKE_MEASURE)).isEqualTo("abc");
        assertThat(NotificationFormat.ellipsize("abcdef", 30f, FAKE_MEASURE)).isEqualTo("ab…");
        assertThat(NotificationFormat.ellipsize("abcdef", 60f, FAKE_MEASURE)).isEqualTo("abcdef");
        assertThat(NotificationFormat.ellipsize("", 0f, FAKE_MEASURE)).isEmpty();
        assertThat(NotificationFormat.ellipsize(null, 30f, FAKE_MEASURE)).isNull();
    }

    @Test
    @DisplayName("ellipsize：零前缀放得下时仅返回省略号（防御）")
    void ellipsizeFallsBackToEllipsisOnly() {
        assertThat(NotificationFormat.ellipsize("长文本", 10f, FAKE_MEASURE)).isEqualTo("…");
    }
}
