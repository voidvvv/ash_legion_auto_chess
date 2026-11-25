package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import com.voidvvv.kz_auto_chess_n.entities.RunMode;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver.Session;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对拍局流程与商店分派验收件（品质改造 §0.2）：局口径（固定森林/无英雄/EMPTY 修正/10 金/空名册）、
 * 新轮免费五槽 + 付费序号归零、付费刷新 ①②③ 循环与余额不足拒绝、重开回首轮免费槽位、
 * 第 3 轮胜箱领完进入对拍终局（「对拍完成」）。账本核对见 {@link SampleDuelLedgerTest}。
 */
@DisplayName("对拍局流程与商店分派：局口径 / 新轮免费槽 / 付费循环 / 终局（品质改造 §0.2）")
class SampleDuelFlowTest {

    private static GameData data;

    @BeforeAll
    static void loadRealData() {
        data = PaiDuelDriver.realData();
    }

    // —— 局口径 ——

    @Test
    @DisplayName("对拍局口径：SAMPLE_DUEL / 固定森林 / 无英雄 / EMPTY 修正 / 10 金 / 空名册 / 题号与标题冻结")
    void duelContextContract() {
        RunContext ctx = Session.start(data, PaiTestScript.Topic.A, 42L).ctx();
        assertThat(ctx.getMode()).isEqualTo(RunMode.SAMPLE_DUEL);
        assertThat(ctx.getRunState().getSceneId()).isEqualTo("scene_forest");
        assertThat(ctx.getRunState().getHeroId()).isNull();
        assertThat(ctx.getRunState().getModifiers()).isSameAs(RunModifiers.EMPTY);
        assertThat(ctx.getPlayer().getGold())
                .as("对拍 10 金 = START_GOLD（Q4 恰好口径，不叠加熟练度/英雄加成）")
                .isEqualTo(GameBalance.START_GOLD).isEqualTo(10);
        assertThat(ctx.getPlayer().getRosterSize()).isZero(); // 空名册
        assertThat(ctx.getSampleTopic()).isEqualTo(PaiTestScript.Topic.A);
        assertThat(ctx.getHudTitle()).isEqualTo("对拍 A · 42");
        assertThat(ctx.totalRounds()).isEqualTo(3);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);
        assertThat(ctx.getRunState().isRunStarted()).isTrue(); // StartRun 已发（回放第 0 条）
        assertThat(ctx.getMode().closesArchiveTrack()).isTrue(); // 档案轨关闭（RunMode.closesArchiveTrack）
    }

    @Test
    @DisplayName("B 题同口径：题号 B / 标题「对拍 B · 42」")
    void duelContextContractTopicB() {
        RunContext ctx = Session.start(data, PaiTestScript.Topic.B, 42L).ctx();
        assertThat(ctx.getSampleTopic()).isEqualTo(PaiTestScript.Topic.B);
        assertThat(ctx.getHudTitle()).isEqualTo("对拍 B · 42");
        assertThat(ctx.getRunState().getEnemyWave()).hasSize(1); // B 首战 = 暗影之刃（题表）
    }

    // —— 新轮免费五槽 + 付费序号归零 ——

    @Test
    @DisplayName("每次进入新轮写该轮免费五槽并把付费序号归零（胜后推进）")
    void newRoundWritesFreeSlotsAndResetsPaidIndex() {
        Session session = Session.start(data, PaiTestScript.Topic.A, 42L);
        assertThat(session.slotIds()).containsExactly(
                PaiTestScript.W, PaiTestScript.W, PaiTestScript.M, PaiTestScript.S, PaiTestScript.P);

        buyWms(session);
        session.injectWin();
        session.pickChestByKind(ChestOption.Kind.GOLD); // 第 1 轮胜箱金币 → 第 2 轮
        assertThat(session.ctx().getRunState().getRound()).isEqualTo(2);
        assertThat(session.slotIds()).containsExactly(
                PaiTestScript.L, PaiTestScript.P, PaiTestScript.M, PaiTestScript.S, PaiTestScript.W);
        assertThat(session.ctx().getShop().getPaidRefreshIndex()).isZero();

        session.injectWin();
        session.pickChestByKind(ChestOption.Kind.GOLD); // → 第 3 轮
        assertThat(session.ctx().getRunState().getRound()).isEqualTo(3);
        assertThat(session.slotIds()).containsExactly(
                PaiTestScript.W, PaiTestScript.P, PaiTestScript.L, PaiTestScript.S, PaiTestScript.M);
        assertThat(session.ctx().getShop().getPaidRefreshIndex()).isZero();
    }

    // —— 付费刷新循环（① ② ③ → ①） ——

    @Test
    @DisplayName("付费刷新：① → ② → ③ → 第四次回 ①，序号随成功支付推进并循环")
    void paidRefreshCycles() {
        Session session = Session.start(data, PaiTestScript.Topic.A, 42L);
        session.refreshPaid();
        assertThat(session.slotIds()).containsExactly(
                PaiTestScript.W, PaiTestScript.L, PaiTestScript.M, PaiTestScript.P, PaiTestScript.S); // ①
        assertThat(session.ctx().getShop().getPaidRefreshIndex()).isEqualTo(1);
        assertThat(session.ctx().getPlayer().getGold()).isEqualTo(GameBalance.START_GOLD - 2);

        session.refreshPaid();
        assertThat(session.slotIds()).containsExactly(
                PaiTestScript.P, PaiTestScript.S, PaiTestScript.L, PaiTestScript.W, PaiTestScript.M); // ②
        assertThat(session.ctx().getShop().getPaidRefreshIndex()).isEqualTo(2);

        session.refreshPaid();
        assertThat(session.slotIds()).containsExactly(
                PaiTestScript.L, PaiTestScript.M, PaiTestScript.W, PaiTestScript.S, PaiTestScript.P); // ③
        assertThat(session.ctx().getShop().getPaidRefreshIndex()).isEqualTo(0); // 序号循环（0 = ①）

        session.refreshPaid();
        assertThat(session.slotIds()).containsExactly(
                PaiTestScript.W, PaiTestScript.L, PaiTestScript.M, PaiTestScript.P, PaiTestScript.S); // ④ 回 ①
        assertThat(session.ctx().getPlayer().getGold()).isEqualTo(GameBalance.START_GOLD - 8);
    }

    @Test
    @DisplayName("刷新余额不足：拒绝扣款且不动槽序与付费序号")
    void paidRefreshRejectedWhenShort() {
        Session session = Session.start(data, PaiTestScript.Topic.A, 42L);
        buyAllFive(session);                       // 10 金买满五槽 → 余 5
        session.buyExp();                          // 4 金 → 余 1（< 刷新价 2）
        List<String> before = session.slotIds();
        session.refreshPaid();
        assertThat(session.ctx().getPlayer().getGold()).isEqualTo(1);  // 未扣款
        assertThat(session.slotIds()).isEqualTo(before);               // 槽序不动
        assertThat(session.ctx().getShop().getPaidRefreshIndex()).isZero(); // 序号不推进
    }

    // —— 终局与重开 ——

    @Test
    @DisplayName("第 3 轮胜箱领完走现有终局分支：RUN_END + COMPLETED（对拍完成）")
    void thirdWinChestLeadsToSampleDuelEnd() {
        Session session = Session.start(data, PaiTestScript.Topic.A, 42L);
        for (int round = 1; round <= PaiTestScript.TOTAL_ROUNDS; round++) {
            session.injectWin();
            assertThat(session.ctx().getRunState().getPendingChest()).isNotNull();
            session.pickChestByKind(ChestOption.Kind.GOLD);
        }
        assertThat(session.ctx().getRunState().getPhase()).isEqualTo(GamePhase.RUN_END);
        assertThat(session.ctx().getRunState().getEndCause()).isEqualTo(RunEndCause.COMPLETED);
    }

    @Test
    @DisplayName("对拍重开（同种子重建上下文）：回首轮免费槽位且付费序号归零")
    void restartReturnsToFirstRoundFreeSlots() {
        Session first = Session.start(data, PaiTestScript.Topic.A, 42L);
        buyWms(first);
        first.refreshPaid();
        first.injectWin();
        first.pickChestByKind(ChestOption.Kind.GOLD);
        assertThat(first.ctx().getRunState().getRound()).isEqualTo(2);

        // 重开 = 以原题原种子重建整个上下文（BattleScreen.restartRun → RunAssembler 装配）
        Session restarted = Session.start(data, PaiTestScript.Topic.A, 42L);
        assertThat(restarted.ctx()).isNotSameAs(first.ctx());
        assertThat(restarted.ctx().getRunState().getRound()).isEqualTo(1);
        assertThat(restarted.ctx().getShop().getPaidRefreshIndex()).isZero();
        assertThat(restarted.slotIds()).containsExactly(
                PaiTestScript.W, PaiTestScript.W, PaiTestScript.M, PaiTestScript.S, PaiTestScript.P);
        assertThat(restarted.ctx().getPlayer().getGold()).isEqualTo(GameBalance.START_GOLD);
    }

    // —— 夹具 ——

    private static void buyWms(Session session) {
        session.buyTemplate(PaiTestScript.W);
        session.buyTemplate(PaiTestScript.M);
        session.buyTemplate(PaiTestScript.S);
    }

    private static void buyAllFive(Session session) {
        for (int slot = 0; slot < GameBalance.SHOP_SLOTS; slot++) {
            session.buy(slot);
        }
    }
}
