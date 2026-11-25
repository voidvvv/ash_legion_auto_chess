package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver.Session;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对拍确定性验收件（品质改造 §0.2）：同题同种子同命令双跑逐项一致、付费刷新不影响交战随机与
 * 胜箱、败箱返回保留槽位与付费序号、每战独立 RNG 起点。全部走真实 RunFlowSystem/ShopSystem/
 * RosterSystem/BattleSystem 链路，题表与种子公式取自 {@link PaiTestScript}（单一来源）。
 */
@DisplayName("对拍确定性：双跑逐项一致 / 付费刷新不变性 / 败箱返回保留（品质改造 §0.2）")
class SampleDuelDeterminismTest {

    private static GameData data;

    @BeforeAll
    static void loadRealData() {
        data = PaiDuelDriver.realData();
    }

    /** §1.1 对拍购买固定：首屏 W/M/S（按 W、M、S 顺序操作），余 7 金，均一星且无装备 */
    private static void buyFixedOpening(Session session) {
        session.buyTemplate(PaiTestScript.W);
        session.buyTemplate(PaiTestScript.M);
        session.buyTemplate(PaiTestScript.S);
    }

    /** §1.1 A 聚拢站位：W(2,4) / M(1,4) / S(2,5)（按席序 W、M、S 依次部署） */
    private static void deployCluster(Session session) {
        session.deployNextBench(2, 4);
        session.deployNextBench(1, 4);
        session.deployNextBench(2, 5);
    }

    /** §1.1 A 分散站位：W(2,4) / M(0,4) / S(0,6)（按席序 W、M、S 依次部署） */
    private static void deployScatter(Session session) {
        session.deployNextBench(2, 4);
        session.deployNextBench(0, 4);
        session.deployNextBench(0, 6);
    }

    @Test
    @DisplayName("同题同种子同命令从新局跑两次：槽序 / 敌阵 / 原始事件 / 结局逐项一致")
    void sameTopicSameSeedSameCommandsMatchesItemByItem() {
        Session first = Session.start(data, PaiTestScript.Topic.A, 42L);
        Session second = Session.start(data, PaiTestScript.Topic.A, 42L);
        assertThat(first.slotIds()).isEqualTo(second.slotIds()); // 新轮免费五槽同源（题表）

        for (Session session : List.of(first, second)) {
            buyFixedOpening(session);
            deployCluster(session);
            session.battleToEnd();
        }
        assertThat(first.ctx().getPlayer().getGold()).isEqualTo(7); // 余 7 金（§1.1 对拍购买固定）
        assertThat(second.ctx().getPlayer().getGold()).isEqualTo(first.ctx().getPlayer().getGold());
        assertThat(second.wave()).isEqualTo(first.wave());                    // 敌阵（模板引用判等）
        assertThat(second.events()).isEqualTo(first.events());                // 原始事件逐位一致
        assertThat(second.lastBattle().getOutcome())
                .isEqualTo(first.lastBattle().getOutcome());                  // 结局
        assertThat(second.lastBattle().aliveCount(Side.PLAYER))
                .isEqualTo(first.lastBattle().aliveCount(Side.PLAYER));       // 存活数
        assertThat(second.lastBattle().aliveCount(Side.ENEMY))
                .isEqualTo(first.lastBattle().aliveCount(Side.ENEMY));
    }

    @Test
    @DisplayName("首个关键施法 tick 与首个技能命中对象：双跑一致（§0.2 验证记录字段）")
    void firstCastAndFirstHitMatchAcrossRuns() {
        Session first = Session.start(data, PaiTestScript.Topic.A, 42L);
        Session second = Session.start(data, PaiTestScript.Topic.A, 42L);
        for (Session session : List.of(first, second)) {
            buyFixedOpening(session);
            deployCluster(session);
            session.battleToEnd();
        }
        assertThat(firstCast(first)).isEqualTo(firstCast(second));
        assertThat(firstHitTarget(first)).isEqualTo(firstHitTarget(second));
    }

    /** 首个施法事件的 (tick, skillId, targetId)——无施法返回 null */
    private static String firstCast(Session session) {
        for (CombatEvent event : session.events()) {
            if (event.getType() == CombatEvent.Type.CAST) {
                return event.getTick() + "/" + event.getSkillId() + "/" + event.getTargetId();
            }
        }
        return null;
    }

    /** 首个正扣血命中事件的 (tick, targetId, skillId)——无命中返回 null */
    private static String firstHitTarget(Session session) {
        for (CombatEvent event : session.events()) {
            if (event.getType() == CombatEvent.Type.HIT && event.getAmount() > 0f) {
                return event.getTick() + "/" + event.getTargetId() + "/" + event.getSkillId();
            }
        }
        return null;
    }

    @Test
    @DisplayName("增加一次付费刷新但不改参战名册：战斗事件与结局不变、胜箱数据不变（§0.2）")
    void paidRefreshKeepsBattleRngStartAndWinChest() {
        Session plain = Session.start(data, PaiTestScript.Topic.A, 42L);
        buyFixedOpening(plain);
        deployScatter(plain);
        plain.battleToEnd();

        Session refreshed = Session.start(data, PaiTestScript.Topic.A, 42L);
        buyFixedOpening(refreshed);
        refreshed.refreshPaid();               // 唯一差异：多一次付费刷新（2 金）
        deployScatter(refreshed);
        refreshed.battleToEnd();

        // 参战名册不变：均为 3 枚（W/M/S 一星）
        assertThat(plain.ctx().getPlayer().getRosterSize()).isEqualTo(3);
        assertThat(refreshed.ctx().getPlayer().getRosterSize()).isEqualTo(3);
        // 战斗 RNG 起点（事件流为随机流的直接投影）与结局一致
        assertThat(refreshed.events()).isEqualTo(plain.events());
        assertThat(refreshed.lastBattle().getOutcome()).isEqualTo(plain.lastBattle().getOutcome());
        assertThat(refreshed.lastBattle().getRng().getConsumedCount())
                .isEqualTo(plain.lastBattle().getRng().getConsumedCount());
        // 胜箱数据不变（胜箱种子与付费刷新无关——§0.2「避免交战多打几下改变装备选项」）
        assertThat(refreshed.ctx().getRunState().getPendingChest())
                .isEqualTo(plain.ctx().getRunState().getPendingChest());
    }

    @Test
    @DisplayName("败箱返回：槽位（含已买空槽）与付费序号原样保留、留在当前轮（§0.2）")
    void defeatChestRetryKeepsSlotsAndPaidIndex() {
        Session session = Session.start(data, PaiTestScript.Topic.A, 42L);
        buyFixedOpening(session);              // 余 7
        session.refreshPaid();                 // 付费序号 0 → 1，槽 = ①
        session.buyTemplate(PaiTestScript.W);  // 买走 ① 首位 → 槽 0 已买空槽，余 4
        assertThat(session.ctx().getPlayer().getGold()).isEqualTo(4);
        assertThat(session.ctx().getShop().getPaidRefreshIndex()).isEqualTo(1);

        deployCluster(session);
        session.surrender();                   // 确定性败局（3 枚已部署 → 败箱）
        assertThat(session.ctx().getRunState().getDefeatCountPerRound()).isEqualTo(1);
        session.pickChestByKind(ChestOption.Kind.GOLD); // 败箱返回 → 同轮重试唯一出口

        assertThat(session.ctx().getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);
        assertThat(session.ctx().getRunState().getRound()).isEqualTo(1); // 留在当前轮
        assertThat(session.ctx().getShop().getPaidRefreshIndex()).isEqualTo(1);  // 付费序号保留
        assertThat(session.slotIds())                                    // ① 槽序 + 已买空槽保留
                .containsExactly(null, PaiTestScript.L, PaiTestScript.M, PaiTestScript.P,
                        PaiTestScript.S);
    }

    @Test
    @DisplayName("每战独立 RNG 起点：交战消耗后，下一轮开战从 0 重新计数（§0.2）")
    void perBattleRngStartsFresh() {
        Session session = Session.start(data, PaiTestScript.Topic.A, 42L);
        buyFixedOpening(session);
        deployScatter(session);
        session.battleToEnd();                                        // 真实交战消耗战斗 RNG
        assertThat(session.lastBattle().getRng().getConsumedCount()).isGreaterThan(0);
        session.pickChestByKind(ChestOption.Kind.GOLD);               // 胜箱金币 → 第 2 轮
        assertThat(session.ctx().getRunState().getRound()).isEqualTo(2);

        session.injectWin();                                          // 第 2 战开战
        assertThat(session.lastBattle().getRng().getConsumedCount()).isZero(); // 新起点
        assertThat(session.lastBattle().getTick()).isZero();          // 交战钟同样从零起
    }
}
