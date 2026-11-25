package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver.Session;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 前三战可负担组合账本（品质改造 §0.3）：用真实购买、部署、刷新、买经验与领奖命令逐项核对
 * 设计书三条路线在第 1/2/3 战前的金币、星级、等级、经验和人口。胜局注入
 * （{@link Session#injectWin()}）以独立验证账本——不依赖交战强度（§0.3 明文）。
 * 商店槽位全部来自 {@link PaiTestScript} 固定表（免费页按模板寻位 = 「买免费页 X」的操作语义）。
 */
@DisplayName("前三战可负担组合账本：三条路线逐项核对（品质改造 §0.3）")
class SampleDuelLedgerTest {

    private static GameData data;

    @BeforeAll
    static void loadRealData() {
        data = PaiDuelDriver.realData();
    }

    @Test
    @DisplayName("首轮人口：4 金经验 + 4 金四棋上 4 → 余 2；两箱取金 → 第 3 战前余 0")
    void routeFirstRoundPopulation() {
        Session session = Session.start(data, PaiTestScript.Topic.A, 42L);
        Player player = session.ctx().getPlayer();

        session.buyExp();                                   // 4 金 = 4 经验 → Lv.2（人口 4）
        session.buyTemplate(PaiTestScript.W);               // W/M/S/P 各 1（4 金）
        session.buyTemplate(PaiTestScript.M);
        session.buyTemplate(PaiTestScript.S);
        session.buyTemplate(PaiTestScript.P);
        session.deployNextBench(2, 4);
        session.deployNextBench(1, 4);
        session.deployNextBench(2, 5);
        session.deployNextBench(5, 4);                      // 上 4
        assertThat(player.getGold()).as("第 1 战前").isEqualTo(2);
        assertThat(player.getLevel()).isEqualTo(2);
        assertThat(player.getCurrentExp()).isZero();
        assertThat(player.getDeployedUnits()).hasSize(4);
        assertThat(player.getBench()).isEmpty();

        session.injectWin();
        session.pickChestByKind(ChestOption.Kind.GOLD);     // 第 1 箱取 3 金
        assertThat(player.getGold()).isEqualTo(5);

        session.buyTemplate(PaiTestScript.L);               // 免费页 L（1 金）
        session.refreshPaid();                              // 2 金
        session.buyTemplate(PaiTestScript.W);               // 一张备用棋（1 金）
        assertThat(player.getGold()).as("第 2 战前").isEqualTo(1);
        assertThat(player.getDeployedUnits()).hasSize(4);   // 上 4
        assertThat(player.getBench()).hasSize(2);           // L + 备用棋在席

        session.injectWin();
        session.pickChestByKind(ChestOption.Kind.GOLD);     // 第 2 箱取 3 金
        assertThat(player.getGold()).isEqualTo(4);

        session.buyTemplate(PaiTestScript.P);               // 两张替换棋（2 金，避开第三张 W 触发 3 合 1）
        session.buyTemplate(PaiTestScript.L);
        session.refreshPaid();                              // 2 金
        assertThat(player.getGold()).as("第 3 战前").isZero();
        assertThat(player.getDeployedUnits()).hasSize(4);   // 上 4
        assertThat(session.ctx().getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);
    }

    @Test
    @DisplayName("首轮升星：W/W/M/S + 刷新 + 第三张 W 合成二星 → 余 3；取书升 Lv.2 → 第 3 战前余 0（4/8）")
    void routeFirstRoundStarUp() {
        Session session = Session.start(data, PaiTestScript.Topic.A, 42L);
        Player player = session.ctx().getPlayer();

        session.buyTemplate(PaiTestScript.W);               // W/W/M/S（4 金）
        session.buyTemplate(PaiTestScript.W);
        session.buyTemplate(PaiTestScript.M);
        session.buyTemplate(PaiTestScript.S);
        session.refreshPaid();                              // 2 金
        session.buyTemplate(PaiTestScript.W);               // 第三张 W（1 金）→ 合成二星
        session.deployNextBench(2, 4);                      // 二星 W
        session.deployNextBench(2, 5);                      // M
        session.deployNextBench(2, 6);                      // S（上 3）
        assertThat(player.getGold()).as("第 1 战前").isEqualTo(3);
        assertThat(player.getLevel()).isEqualTo(1);
        assertThat(player.getDeployedUnits()).hasSize(3);
        assertThat(player.getRosterSize()).isEqualTo(3);    // 3 合 1 已折叠
        assertThat(starOfWarrior(player)).isEqualTo(2);     // 二星 W

        session.injectWin();
        session.pickChestByKind(ChestOption.Kind.EXP_BOOK); // 第 1 箱取书 → Lv.2
        assertThat(player.getLevel()).isEqualTo(2);
        assertThat(player.getCurrentExp()).isZero();
        assertThat(player.getGold()).isEqualTo(3);          // 取书不进金

        session.buyTemplate(PaiTestScript.P);               // 免费页 P（1 金）
        session.deployNextBench(5, 4);                      // 上 4
        assertThat(player.getGold()).as("第 2 战前").isEqualTo(2);
        assertThat(player.getDeployedUnits()).hasSize(4);

        session.injectWin();
        session.pickChestByKind(ChestOption.Kind.GOLD);     // 第 2 箱取 3 金
        assertThat(player.getGold()).isEqualTo(5);

        session.buyExp();                                   // 4 经验 → Lv.2 进度 4/8
        session.buyTemplate(PaiTestScript.W);               // 补一张棋（1 金）
        assertThat(player.getGold()).as("第 3 战前").isZero();
        assertThat(player.getLevel()).isEqualTo(2);
        assertThat(player.getCurrentExp()).isEqualTo(4);
        assertThat(player.getDeployedUnits()).hasSize(4);   // 上 4
    }

    @Test
    @DisplayName("留钱后升人口：W/M/S 上 3 余 7；两箱取书 → 4 经验到 Lv.3 + 免费页 L 上 5 余 1")
    void routeSaveThenPopulation() {
        Session session = Session.start(data, PaiTestScript.Topic.A, 42L);
        Player player = session.ctx().getPlayer();

        session.buyTemplate(PaiTestScript.W);               // W/M/S（3 金）
        session.buyTemplate(PaiTestScript.M);
        session.buyTemplate(PaiTestScript.S);
        session.deployNextBench(2, 4);
        session.deployNextBench(1, 4);
        session.deployNextBench(2, 5);                      // 上 3
        assertThat(player.getGold()).as("第 1 战前").isEqualTo(7);
        assertThat(player.getLevel()).isEqualTo(1);
        assertThat(player.getDeployedUnits()).hasSize(3);

        session.injectWin();
        session.pickChestByKind(ChestOption.Kind.EXP_BOOK); // 第 1 箱取书 → Lv.2
        assertThat(player.getLevel()).isEqualTo(2);
        assertThat(player.getGold()).isEqualTo(7);          // 留钱

        session.buyTemplate(PaiTestScript.P);               // 免费页 P（1 金）
        session.deployNextBench(5, 4);                      // 上 4
        assertThat(player.getGold()).as("第 2 战前").isEqualTo(6);
        assertThat(player.getDeployedUnits()).hasSize(4);

        session.injectWin();
        session.pickChestByKind(ChestOption.Kind.EXP_BOOK); // 第 2 箱取书 → Lv.2 进度 4/8
        assertThat(player.getLevel()).isEqualTo(2);
        assertThat(player.getCurrentExp()).isEqualTo(4);
        assertThat(player.getGold()).isEqualTo(6);

        session.buyExp();                                   // 4 经验 → Lv.3（人口 5）
        session.buyTemplate(PaiTestScript.L);               // 免费页 L（1 金）
        session.deployNextBench(0, 6);                      // 上 5
        assertThat(player.getGold()).as("第 3 战前").isEqualTo(1);
        assertThat(player.getLevel()).isEqualTo(3);
        assertThat(player.getCurrentExp()).isZero();
        assertThat(player.getDeployedUnits()).hasSize(5);
    }

    @Test
    @DisplayName("三条路线均可在第 3 轮领胜箱后走对拍终局（COMPLETED）")
    void allRoutesReachSampleDuelEnd() {
        for (Route route : Route.values()) {
            Session session = Session.start(data, PaiTestScript.Topic.A, 42L);
            route.play(session);
            assertThat(session.ctx().getRunState().getPhase())
                    .as("路线 %s 终局", route).isEqualTo(GamePhase.RUN_END);
            assertThat(session.ctx().getRunState().getEndCause())
                    .as("路线 %s 成因", route).isEqualTo(RunEndCause.COMPLETED);
        }
    }

    /** 三条账本路线的可重放编排（账本断言与终局断言共用同一命令序） */
    private enum Route {
        FIRST_ROUND_POPULATION {
            @Override
            void play(Session session) {
                session.buyExp();
                buyTemplates(session, PaiTestScript.W, PaiTestScript.M, PaiTestScript.S,
                        PaiTestScript.P);
                deployBench(session, 0, 4);
                winTake(session, ChestOption.Kind.GOLD);
                session.buyTemplate(PaiTestScript.L);
                session.refreshPaid();
                session.buyTemplate(PaiTestScript.W);
                winTake(session, ChestOption.Kind.GOLD);
                buyTemplates(session, PaiTestScript.P, PaiTestScript.L);
                session.refreshPaid();
                winTake(session, ChestOption.Kind.GOLD); // 第 3 战 → 对拍终局
            }
        },
        FIRST_ROUND_STAR_UP {
            @Override
            void play(Session session) {
                buyTemplates(session, PaiTestScript.W, PaiTestScript.W, PaiTestScript.M,
                        PaiTestScript.S);
                session.refreshPaid();
                session.buyTemplate(PaiTestScript.W);
                deployBench(session, 0, 3);
                winTake(session, ChestOption.Kind.EXP_BOOK);
                session.buyTemplate(PaiTestScript.P);
                deployBench(session, 3, 1);
                winTake(session, ChestOption.Kind.GOLD);
                session.buyExp();
                session.buyTemplate(PaiTestScript.W);
                winTake(session, ChestOption.Kind.GOLD); // 第 3 战 → 对拍终局
            }
        },
        SAVE_THEN_POPULATION {
            @Override
            void play(Session session) {
                buyTemplates(session, PaiTestScript.W, PaiTestScript.M, PaiTestScript.S);
                deployBench(session, 0, 3);
                winTake(session, ChestOption.Kind.EXP_BOOK);
                session.buyTemplate(PaiTestScript.P);
                deployBench(session, 3, 1);
                winTake(session, ChestOption.Kind.EXP_BOOK);
                session.buyExp();
                session.buyTemplate(PaiTestScript.L);
                deployBench(session, 4, 1);
                winTake(session, ChestOption.Kind.GOLD); // 第 3 战 → 对拍终局
            }
        };

        abstract void play(Session session);
    }

    private static void buyTemplates(Session session, String... unitIds) {
        for (String unitId : unitIds) {
            session.buyTemplate(unitId);
        }
    }

    /** 确定性占位池（同一局内按序取用，格位互不重叠——避免撞上 MoveUnit 的交换语义） */
    private static final int[][] DEPLOY_CELLS = {
            {2, 4}, {1, 4}, {2, 5}, {5, 4}, {0, 6}, {3, 5}, {4, 6}, {0, 5}, {1, 5}};

    /** 从占位池第 {@code fromCell} 格起连续部署备战席前 {@code count} 枚（账本只核对人口，不核对格位） */
    private static void deployBench(Session session, int fromCell, int count) {
        for (int i = 0; i < count; i++) {
            session.deployNextBench(DEPLOY_CELLS[fromCell + i][0], DEPLOY_CELLS[fromCell + i][1]);
        }
    }

    private static void winTake(Session session, ChestOption.Kind kind) {
        session.injectWin();
        session.pickChestByKind(kind);
    }

    /** 名单中二星战士的星级（无则 -1） */
    private static int starOfWarrior(Player player) {
        for (Unit unit : player.getDeployedUnits()) {
            if (unit.getTemplate().getId().equals(PaiTestScript.W)) {
                return unit.getStar();
            }
        }
        for (Unit unit : player.getBench()) {
            if (unit.getTemplate().getId().equals(PaiTestScript.W)) {
                return unit.getStar();
            }
        }
        return -1;
    }
}
