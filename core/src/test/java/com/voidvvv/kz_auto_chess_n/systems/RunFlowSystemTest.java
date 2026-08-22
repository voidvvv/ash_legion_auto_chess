package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.MoveUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.StartBattleCommand;
import com.voidvvv.kz_auto_chess_n.command.SurrenderCommand;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RunFlowSystem 测试：阶段状态机门控 / 演示名单 / 轮开始事件子集 / 战后流转 / 同 seed 重演一致
 * （Q2/Q3 + 口径 #22；验收标准 §九第 3 条确定性对照）。
 */
class RunFlowSystemTest {

    private static final int MAX_TICKS = 4000;

    // —— 演示数据集：三名玩家演示兵 + 敌池杂兵 + Boss（覆盖 25 轮全流程） ——

    private static UnitData unit(String id, int hp, int atk, int range, boolean boss) {
        return new UnitData(id, "演示" + id, "兽人", "战士", 1,
                new BaseStats(hp, atk, 0, 1f, range, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, boss);
    }

    private static GameData demoData() {
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        units.put("unit_warrior_01", unit("unit_warrior_01", 200, 20, 1, false));
        units.put("unit_assassin_01", unit("unit_assassin_01", 150, 25, 1, false));
        units.put("unit_ranger_01", unit("unit_ranger_01", 100, 15, 2, false));
        units.put("unit_goblin_01", unit("unit_goblin_01", 60, 6, 1, false));
        units.put("unit_boss_01", unit("unit_boss_01", 400, 30, 1, true));

        Map<String, SkillData> skills = new LinkedHashMap<String, SkillData>();
        for (String id : Arrays.asList("unit_warrior_01", "unit_assassin_01", "unit_ranger_01",
                "unit_goblin_01", "unit_boss_01")) {
            skills.put("sk_" + id, BattleTestFixtures.skill("sk_" + id, SkillShape.SINGLE_TARGET,
                    Delivery.MELEE_INSTANT, BattleTestFixtures.effect(SkillEffectType.DAMAGE, 2f, null, null)));
        }

        Map<Integer, String> bosses = new LinkedHashMap<Integer, String>();
        bosses.put(7, "unit_boss_01");
        bosses.put(15, "unit_boss_01");
        bosses.put(25, "unit_boss_01");
        SceneData scene = new SceneData("scene_forest", "森林", null,
                Arrays.asList(new SceneData.EnemyPoolEntry("unit_goblin_01", 1, 1)), bosses);
        Map<String, SceneData> scenes = new LinkedHashMap<String, SceneData>();
        scenes.put("scene_forest", scene);
        return new GameData(units, skills, new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                scenes, new ArrayList<String>());
    }

    private static RunContext newContext(GameData data) {
        return new RunContext(new Player(GameBalance.START_GOLD),
                new RunState(RunFlowSystem.DEMO_SEED, "scene_forest", new SequentialIdIssuer()),
                data, new RandomGenerator(RunFlowSystem.DEMO_SEED));
    }

    private static CommandManager armedManager(RunFlowSystem flow) {
        CommandManager manager = new CommandManager();
        flow.registerHandlers(manager);
        return manager;
    }

    // —— startNewRun / beginRound ——

    @Test
    @DisplayName("startNewRun：3 演示兵只入备战席（不预部署）、round=1、敌阵已生成")
    void startNewRunGrantsDemoRoster() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        RunContext ctx = newContext(data);
        flow.startNewRun(ctx);
        assertThat(ctx.getRunState().getRound()).isEqualTo(1);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);
        assertThat(ctx.getBattleState()).isNull();
        List<String> benchTemplateIds = new ArrayList<String>();
        for (com.voidvvv.kz_auto_chess_n.entities.Unit benchUnit : ctx.getPlayer().getBench()) {
            benchTemplateIds.add(benchUnit.getTemplate().getId());
        }
        assertThat(benchTemplateIds)
                .containsExactly("unit_warrior_01", "unit_assassin_01", "unit_ranger_01");
        for (int y = 4; y <= 6; y++) { // 不预部署：玩家区全空（布阵是玩家操作，反馈 #1）
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                assertThat(ctx.getPlayer().deployedAt(x, y)).isNull();
            }
        }
        assertThat(ctx.getRunState().getEnemyWave()).isNotEmpty();
    }

    @Test
    @DisplayName("beginRound 敌阵确定性：同 seed 两 run 逐位 equals；RNG 消耗 = 杂兵数（round 1 = 1）")
    void beginRoundDeterministicWithKnownRngCost() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        RunContext a = newContext(data);
        RunContext b = newContext(data);
        flow.startNewRun(a);
        flow.startNewRun(b);
        assertThat(a.getRunState().getEnemyWave())
                .containsExactlyElementsOf(b.getRunState().getEnemyWave());
        assertThat(a.getRng().getConsumedCount())
                .isEqualTo(GameBalance.enemyCount(1)); // 波次生成即全部消耗（无商店/无其他 RNG 点）
    }

    // —— 门控矩阵（architecture §5.2 精神：BATTLE 拒布阵/开战，SHOPPING 拒投降） ——

    @Test
    @DisplayName("门控：BATTLE 期 MoveUnit 被拒（名单不变）")
    void moveUnitGatedToShoppingPhase() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startNewRun(ctx);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        int benchUnitId = ctx.getPlayer().getBench().get(0).getId();
        manager.addCommand(new MoveUnitCommand(benchUnitId, new PlacementTarget.Cell(0, 4)));
        manager.executeAll(ctx);
        assertThat(ctx.getPlayer().deployedAt(0, 4)).isNull();
        assertThat(ctx.getPlayer().getBench()).hasSize(3); // BATTLE 门控拒绝：备战席原样
    }

    @Test
    @DisplayName("门控：BATTLE 期 StartBattle 被拒（battleState 不被替换）")
    void startBattleGatedToShoppingPhase() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startNewRun(ctx);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        BattleState first = ctx.getBattleState();
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        assertThat(ctx.getBattleState()).isSameAs(first);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.BATTLE);
    }

    @Test
    @DisplayName("门控：SHOPPING 期 Surrender 被拒（battleState 为 null 不炸）")
    void surrenderGatedToBattlePhase() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startNewRun(ctx);
        manager.addCommand(SurrenderCommand.INSTANCE);
        manager.executeAll(ctx);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);
    }

    // —— StartBattle / Surrender ——

    @Test
    @DisplayName("StartBattle：部署演示兵后派生 BattleState、phase=BATTLE、双方单位就位")
    void startBattleDerivesStateAndEntersBattle() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startNewRun(ctx);
        manager.addCommand(new MoveUnitCommand(ctx.getPlayer().getBench().get(0).getId(),
                new PlacementTarget.Cell(2, 5))); // 玩家手动上阵一个演示兵
        manager.executeAll(ctx);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        BattleState state = ctx.getBattleState();
        assertThat(state).isNotNull();
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.BATTLE);
        assertThat(state.getUnits()).hasSize(2); // 1 已部署演示兵 + round1 敌兵 1
    }

    @Test
    @DisplayName("StartBattle 零棋子允许开战（空名单不炸、照常进 BATTLE）")
    void startBattleAllowsZeroDeployed() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data); // 不调 startNewRun：零名单
        flow.beginRound(ctx);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        assertThat(ctx.getBattleState()).isNotNull();
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.BATTLE);
        assertThat(ctx.getBattleState().getUnits()).hasSize(1); // 仅敌方
    }

    @Test
    @DisplayName("Surrender：立即判负 ENEMY_WIN")
    void surrenderFinishesEnemyWin() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startNewRun(ctx);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        manager.addCommand(SurrenderCommand.INSTANCE);
        manager.executeAll(ctx);
        assertThat(ctx.getBattleState().isOver()).isTrue();
        assertThat(ctx.getBattleState().getOutcome()).isEqualTo(BattleOutcome.ENEMY_WIN);
    }

    @Test
    @DisplayName("Surrender 幂等：重复投降 outcome 与 tick 不变")
    void surrenderIdempotent() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startNewRun(ctx);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        manager.addCommand(SurrenderCommand.INSTANCE);
        manager.executeAll(ctx);
        int tickAfterFirst = ctx.getBattleState().getTick();
        manager.addCommand(SurrenderCommand.INSTANCE);
        manager.executeAll(ctx);
        assertThat(ctx.getBattleState().getOutcome()).isEqualTo(BattleOutcome.ENEMY_WIN);
        assertThat(ctx.getBattleState().getTick()).isEqualTo(tickAfterFirst);
    }

    // —— 战后流转（Q3） ——

    @Test
    @DisplayName("onBattleOver → RESULT：battleState 保留供横幅读 outcome")
    void onBattleOverEntersResultKeepingState() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startNewRun(ctx);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        manager.addCommand(SurrenderCommand.INSTANCE);
        manager.executeAll(ctx);
        flow.onBattleOver(ctx);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.RESULT);
        assertThat(ctx.getBattleState()).isNotNull();
        assertThat(ctx.getBattleState().getOutcome()).isEqualTo(BattleOutcome.ENEMY_WIN);
    }

    @Test
    @DisplayName("continueAfterResult：round+1、敌阵重生成、battleState=null、回 SHOPPING")
    void continueAfterResultAdvancesRound() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startNewRun(ctx);
        List<com.voidvvv.kz_auto_chess_n.entities.WaveSpec> round1Wave =
                new ArrayList<com.voidvvv.kz_auto_chess_n.entities.WaveSpec>(ctx.getRunState().getEnemyWave());
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        manager.addCommand(SurrenderCommand.INSTANCE);
        manager.executeAll(ctx);
        flow.onBattleOver(ctx);
        flow.continueAfterResult(ctx);
        assertThat(ctx.getRunState().getRound()).isEqualTo(2);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);
        assertThat(ctx.getBattleState()).isNull();
        assertThat(ctx.getRunState().getEnemyWave()).isNotEmpty();
        assertThat(ctx.getRunState().getEnemyWave().size())
                .isEqualTo(GameBalance.enemyCount(2)); // 敌阵已按 round 2 重生成
        assertThat(round1Wave).hasSize(GameBalance.enemyCount(1));
    }

    @Test
    @DisplayName("tickResult 累积满 RESULT_BANNER_SECONDS 自动推进（round+1 回 SHOPPING）")
    void tickResultAutoAdvancesAfterBannerSeconds() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startNewRun(ctx);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        manager.addCommand(SurrenderCommand.INSTANCE);
        manager.executeAll(ctx);
        flow.onBattleOver(ctx);
        flow.tickResult(ctx, GameBalance.RESULT_BANNER_SECONDS - 0.01f);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.RESULT); // 未满不推进
        flow.tickResult(ctx, 0.01f);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);
        assertThat(ctx.getRunState().getRound()).isEqualTo(2);
    }

    @Test
    @DisplayName("第 25 轮战毕 → RUN_END 终态")
    void round25LeadsToRunEnd() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startNewRun(ctx);
        BattleSystem battleSystem = new BattleSystem();
        for (int round = 1; round <= GameBalance.TOTAL_ROUNDS; round++) {
            manager.addCommand(StartBattleCommand.INSTANCE);
            manager.executeAll(ctx);
            battleSystem.runToEnd(ctx.getBattleState(), MAX_TICKS);
            assertThat(ctx.getBattleState().isOver()).isTrue();
            flow.onBattleOver(ctx);
            flow.continueAfterResult(ctx);
        }
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.RUN_END);
        assertThat(ctx.getRunState().getRound()).isEqualTo(GameBalance.TOTAL_ROUNDS);
    }

    @Test
    @DisplayName("restart 同 seed 重演一致：两 run 全事件流与各轮敌阵逐位 equals（验收 §九第 3 条）")
    void restartReplaysIdenticalEventStreams() {
        GameData data = demoData();
        List<List<CombatEvent>> runA = playFullRun(data, true);
        List<List<CombatEvent>> runB = playFullRun(data, false);
        assertThat(runA).hasSize(GameBalance.TOTAL_ROUNDS);
        assertThat(runB).hasSize(GameBalance.TOTAL_ROUNDS);
        for (int round = 0; round < GameBalance.TOTAL_ROUNDS; round++) {
            assertThat(runB.get(round)).containsExactlyElementsOf(runA.get(round));
        }
    }

    /** 整局推演：每轮 开战 → runToEnd → RESULT → 推进；useRestart=true 经 restart 复入 */
    private List<List<CombatEvent>> playFullRun(GameData data, boolean useRestart) {
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        if (useRestart) {
            flow.restart(ctx);
        } else {
            flow.startNewRun(ctx);
        }
        BattleSystem battleSystem = new BattleSystem();
        List<List<CombatEvent>> rounds = new ArrayList<List<CombatEvent>>();
        while (ctx.getRunState().getPhase() != GamePhase.RUN_END) {
            manager.addCommand(StartBattleCommand.INSTANCE);
            manager.executeAll(ctx);
            BattleState state = ctx.getBattleState();
            battleSystem.runToEnd(state, MAX_TICKS);
            assertThat(state.isOver()).isTrue();
            rounds.add(new ArrayList<CombatEvent>(state.getEvents()));
            flow.onBattleOver(ctx);
            flow.continueAfterResult(ctx);
        }
        return rounds;
    }
}
