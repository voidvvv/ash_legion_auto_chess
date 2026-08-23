package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.AbandonRunCommand;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.PickChestCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.StartBattleCommand;
import com.voidvvv.kz_auto_chess_n.command.StartRunCommand;
import com.voidvvv.kz_auto_chess_n.command.SurrenderCommand;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentPassive;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
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
 * RunFlowSystem 测试（CP15 新口径，Q6 裁决：无演示名单，兵源 = 商店自购）：
 * StartRun 命令化与防重入 / 判负同轮重试（1C-R）与怜悯 / 胜局宝箱流转（必须 PickChest）/
 * 第 25 轮经领箱通关 / AbandonRun 两阶段 / 同 seed 重演一致（验收标准 §九第 3 条）。
 */
class RunFlowSystemTest {

    /** 原 RunFlowSystem.DEMO_SEED（Q3 裁决后 seed 由 UI 域给定，测试改本地常量） */
    private static final long TEST_SEED = 42L;
    private static final int MAX_TICKS = 4000;

    // —— 演示数据集：可购池 + 必胜/必败夹具兵 + 敌池杂兵 + Boss + 装备两件 ——

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
        units.put("unit_champion_01", unit("unit_champion_01", 5000, 500, 1, false)); // 必胜夹具
        units.put("unit_trainee_01", unit("unit_trainee_01", 30, 1, 1, false));       // 必败夹具
        units.put("unit_goblin_01", unit("unit_goblin_01", 60, 6, 1, false));
        units.put("unit_boss_01", unit("unit_boss_01", 400, 30, 1, true));

        Map<String, SkillData> skills = new LinkedHashMap<String, SkillData>();
        for (String id : Arrays.asList("unit_warrior_01", "unit_assassin_01", "unit_ranger_01",
                "unit_champion_01", "unit_trainee_01", "unit_goblin_01", "unit_boss_01")) {
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

        Map<String, EquipmentData> equipments = new LinkedHashMap<String, EquipmentData>();
        equipments.put("eq_t_sword", new EquipmentData("eq_t_sword", "铁剑", EquipmentSlot.WEAPON,
                EquipmentRarity.WHITE, Arrays.asList(
                        new EquipmentEffect(StatKey.ATTACK, EffectOp.PCT, 20f)), null));
        equipments.put("eq_t_heart", new EquipmentData("eq_t_heart", "龙心", EquipmentSlot.ARMOR,
                EquipmentRarity.LEGENDARY, Arrays.asList(
                        new EquipmentEffect(StatKey.HP, EffectOp.ADD, 400f)),
                new EquipmentPassive(StatusType.REGEN, 0.02f, 5f)));
        return new GameData(units, skills, new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                scenes, equipments, new ArrayList<String>());
    }

    private static RunContext newContext(GameData data) {
        return new RunContext(new Player(GameBalance.START_GOLD),
                new RunState(TEST_SEED, "scene_forest", new SequentialIdIssuer()),
                data, new RandomGenerator(TEST_SEED));
    }

    private static CommandManager armedManager(RunFlowSystem flow) {
        CommandManager manager = new CommandManager();
        flow.registerHandlers(manager);
        return manager;
    }

    /** 测试充当 systems 层直接布阵（MoveUnit handler 已迁 RosterSystem，CP12） */
    private static void deploy(RunContext ctx, String templateId, int x, int y) {
        Unit unit = new Unit(ctx.getRunState().getIdIssuer().nextId(),
                ctx.getGameData().getUnit(templateId), 1);
        ctx.getPlayer().addToBench(unit);
        ctx.getPlayer().deploy(unit, x, y);
    }

    private static void defeatBySurrender(CommandManager manager, RunFlowSystem flow, RunContext ctx) {
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        manager.addCommand(SurrenderCommand.INSTANCE);
        manager.executeAll(ctx);
        flow.onBattleOver(ctx);
    }

    // —— startRun（Q6：无演示名单，商店免费刷新） ——

    @Test
    @DisplayName("startRun：零名单（Q6 演示名单已删）、round=1、敌阵已生成、商店 5 槽就位、通知行落 notices")
    void startRunGrantsNoRosterAndRollsShop() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        assertThat(ctx.getRunState().isRunStarted()).isTrue();
        assertThat(ctx.getRunState().getRound()).isEqualTo(1);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);
        assertThat(ctx.getBattleState()).isNull();
        assertThat(ctx.getPlayer().getBench()).isEmpty(); // 无演示名单：兵源 = 商店自购
        assertThat(ctx.getPlayer().getRosterSize()).isZero();
        assertThat(ctx.getRunState().getEnemyWave()).isNotEmpty();
        assertThat(ctx.getShop().getSlots()).hasSize(GameBalance.SHOP_SLOTS);
        assertThat(ctx.getShop().getSlots()).doesNotContainNull();
        assertThat(ctx.getRunState().drainNotices())
                .anyMatch(line -> line.contains("第 1 轮开始"));
    }

    @Test
    @DisplayName("startRun RNG 消耗 = 敌阵杂兵数 + 商店免费刷新 10（round 1 = 1+10）")
    void startRunRngCostIsEnemyCountPlusShopReroll() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        assertThat(ctx.getRng().getConsumedCount())
                .isEqualTo(GameBalance.enemyCount(1) + 2 * GameBalance.SHOP_SLOTS);
    }

    @Test
    @DisplayName("startRun 确定性：同 seed 两上下文敌阵与商店槽逐位 equals")
    void startRunDeterministicAcrossContexts() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        RunContext a = newContext(data);
        RunContext b = newContext(data);
        flow.startRun(a);
        flow.startRun(b);
        assertThat(a.getRunState().getEnemyWave())
                .containsExactlyElementsOf(b.getRunState().getEnemyWave());
        assertThat(a.getShop().getSlots()).containsExactlyElementsOf(b.getShop().getSlots());
    }

    // —— StartRun 命令化（Q3 裁决：回放流第 0 条记录；口径 #11 一致性校验） ——

    @Test
    @DisplayName("StartRunCommand 经队列生效：runStarted + 敌阵 + 商店刷新（与直调 startRun 同效）")
    void startRunCommandExecutesViaManager() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        manager.addCommand(new StartRunCommand(TEST_SEED, "scene_forest", null));
        manager.executeAll(ctx);
        assertThat(ctx.getRunState().isRunStarted()).isTrue();
        assertThat(ctx.getRunState().getEnemyWave()).isNotEmpty();
        assertThat(ctx.getShop().getSlots()).doesNotContainNull();
        assertThat(ctx.getRng().getConsumedCount())
                .isEqualTo(GameBalance.enemyCount(1) + 2 * GameBalance.SHOP_SLOTS);
    }

    @Test
    @DisplayName("StartRunCommand 防重入：已 started 后第二次静默 false（零 RNG 零通知）")
    void startRunCommandRejectedWhenAlreadyStarted() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        ctx.getRunState().drainNotices();
        int consumed = ctx.getRng().getConsumedCount();
        manager.addCommand(new StartRunCommand(TEST_SEED, "scene_forest", null));
        manager.executeAll(ctx);
        assertThat(ctx.getRng().getConsumedCount()).isEqualTo(consumed);
        assertThat(ctx.getRunState().drainNotices()).isEmpty();
        assertThat(ctx.getRunState().getRound()).isEqualTo(1);
    }

    @Test
    @DisplayName("StartRunCommand 一致性校验：seed / sceneId 错位均静默 false（口径 #11）")
    void startRunCommandRejectedOnSeedOrSceneMismatch() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext seedMismatch = newContext(data);
        manager.addCommand(new StartRunCommand(TEST_SEED + 1, "scene_forest", null));
        manager.executeAll(seedMismatch);
        assertThat(seedMismatch.getRunState().isRunStarted()).isFalse();
        assertThat(seedMismatch.getRunState().getEnemyWave()).isEmpty();

        RunContext sceneMismatch = newContext(data);
        manager.addCommand(new StartRunCommand(TEST_SEED, "scene_other", null));
        manager.executeAll(sceneMismatch);
        assertThat(sceneMismatch.getRunState().isRunStarted()).isFalse();
        assertThat(sceneMismatch.getRunState().getEnemyWave()).isEmpty();
    }

    // —— 门控矩阵（architecture §5.2 精神） ——

    @Test
    @DisplayName("门控：BATTLE 期 StartBattle 被拒（battleState 不被替换）")
    void startBattleGatedToShoppingPhase() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
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
        flow.startRun(ctx);
        manager.addCommand(SurrenderCommand.INSTANCE);
        manager.executeAll(ctx);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);
    }

    // —— StartBattle / Surrender ——

    @Test
    @DisplayName("StartBattle：部署后派生 BattleState、phase=BATTLE、双方单位就位")
    void startBattleDerivesStateAndEntersBattle() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        deploy(ctx, "unit_champion_01", 2, 5);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        BattleState state = ctx.getBattleState();
        assertThat(state).isNotNull();
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.BATTLE);
        assertThat(state.getUnits()).hasSize(1 + GameBalance.enemyCount(1));
    }

    @Test
    @DisplayName("StartBattle 零棋子允许开战（空名单不炸、照常进 BATTLE）")
    void startBattleAllowsZeroDeployed() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        assertThat(ctx.getBattleState()).isNotNull();
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.BATTLE);
        assertThat(ctx.getBattleState().getUnits()).hasSize(GameBalance.enemyCount(1)); // 仅敌方
    }

    @Test
    @DisplayName("Surrender：立即判负 ENEMY_WIN；幂等（重复投降 outcome 与 tick 不变）")
    void surrenderFinishesEnemyWinIdempotent() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        manager.addCommand(SurrenderCommand.INSTANCE);
        manager.executeAll(ctx);
        assertThat(ctx.getBattleState().isOver()).isTrue();
        assertThat(ctx.getBattleState().getOutcome()).isEqualTo(BattleOutcome.ENEMY_WIN);
        int tickAfterFirst = ctx.getBattleState().getTick();
        manager.addCommand(SurrenderCommand.INSTANCE);
        manager.executeAll(ctx);
        assertThat(ctx.getBattleState().getOutcome()).isEqualTo(BattleOutcome.ENEMY_WIN);
        assertThat(ctx.getBattleState().getTick()).isEqualTo(tickAfterFirst);
    }

    // —— 战后分流（胜局 roll 宝箱 2 RNG；败局零消耗） ——

    @Test
    @DisplayName("onBattleOver 败局：RESULT 保留 battleState、pendingChest 恒 null、RNG 零消耗")
    void onBattleOverDefeatKeepsStateWithoutChest() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        deploy(ctx, "unit_trainee_01", 2, 5);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        new BattleSystem().runToEnd(ctx.getBattleState(), MAX_TICKS);
        assertThat(ctx.getBattleState().getOutcome()).isEqualTo(BattleOutcome.ENEMY_WIN);
        int consumedBeforeOver = ctx.getRng().getConsumedCount();
        flow.onBattleOver(ctx);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.RESULT);
        assertThat(ctx.getBattleState()).isNotNull();
        assertThat(ctx.getBattleState().getOutcome()).isEqualTo(BattleOutcome.ENEMY_WIN);
        assertThat(ctx.getRunState().getPendingChest()).isNull();
        assertThat(ctx.getRng().getConsumedCount()).isEqualTo(consumedBeforeOver);
    }

    @Test
    @DisplayName("onBattleOver 胜局：pendingChest 非空（三选项）且 roll 恰好 2 RNG（口径 #1）")
    void onBattleOverVictoryRollsChestExactlyTwoRng() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        deploy(ctx, "unit_champion_01", 2, 5);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        new BattleSystem().runToEnd(ctx.getBattleState(), MAX_TICKS);
        assertThat(ctx.getBattleState().getOutcome()).isEqualTo(BattleOutcome.PLAYER_WIN);
        int consumedBeforeOver = ctx.getRng().getConsumedCount();
        flow.onBattleOver(ctx);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.RESULT);
        assertThat(ctx.getRunState().getPendingChest()).isNotNull();
        assertThat(ctx.getRunState().getPendingChest().getOptions()).hasSize(3);
        assertThat(ctx.getRng().getConsumedCount()).isEqualTo(consumedBeforeOver + 2);
    }

    @Test
    @DisplayName("tickResult 仅败局自动推进：胜局 pendingChest 在场时满时也不推进（口径 #9）")
    void tickResultDoesNotAdvanceVictoryPendingChest() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        deploy(ctx, "unit_champion_01", 2, 5);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        new BattleSystem().runToEnd(ctx.getBattleState(), MAX_TICKS);
        flow.onBattleOver(ctx);
        flow.tickResult(ctx, GameBalance.RESULT_BANNER_SECONDS + 1f);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.RESULT);
        assertThat(ctx.getRunState().getPendingChest()).isNotNull();
        assertThat(ctx.getRunState().getRound()).isEqualTo(1);
    }

    @Test
    @DisplayName("tickResult 败局：累积满 RESULT_BANNER_SECONDS 自动回 SHOPPING（同轮重试）")
    void tickResultAutoAdvancesDefeatOnly() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        defeatBySurrender(manager, flow, ctx);
        flow.tickResult(ctx, GameBalance.RESULT_BANNER_SECONDS - 0.01f);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.RESULT); // 未满不推进
        flow.tickResult(ctx, 0.01f);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);
        assertThat(ctx.getRunState().getRound()).isEqualTo(1); // 败局不推轮（1C-R）
    }

    // —— 判负同轮重试（GDD §2.2：敌阵/商店/轮次不变且 RNG 零消耗） ——

    @Test
    @DisplayName("continueAfterDefeat：同轮重试——round/敌阵/商店不变、battleState 丢弃、RNG 零消耗")
    void continueAfterDefeatRetriesSameRound() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        deploy(ctx, "unit_trainee_01", 2, 5);
        List<WaveSpec> waveBefore = new ArrayList<WaveSpec>(ctx.getRunState().getEnemyWave());
        List<com.voidvvv.kz_auto_chess_n.data.UnitData> slotsBefore =
                new ArrayList<com.voidvvv.kz_auto_chess_n.data.UnitData>(ctx.getShop().getSlots());
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        new BattleSystem().runToEnd(ctx.getBattleState(), MAX_TICKS);
        flow.onBattleOver(ctx);
        int consumedAtResult = ctx.getRng().getConsumedCount();
        flow.continueAfterDefeat(ctx);
        assertThat(ctx.getRunState().getRound()).isEqualTo(1);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);
        assertThat(ctx.getBattleState()).isNull();
        assertThat(ctx.getRunState().getEnemyWave()).containsExactlyElementsOf(waveBefore);
        assertThat(ctx.getShop().getSlots()).containsExactlyElementsOf(slotsBefore);
        assertThat(ctx.getRng().getConsumedCount()).isEqualTo(consumedAtResult);
        assertThat(ctx.getRunState().getMercyLossCount()).isEqualTo(1); // 有上场 → 计数
    }

    @Test
    @DisplayName("continueAfterDefeat 零棋子战败：怜悯不计数、金不变（防刷，口径 #8）")
    void continueAfterDefeatZeroUnitsNotCounted() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        new BattleSystem().runToEnd(ctx.getBattleState(), MAX_TICKS);
        assertThat(ctx.getBattleState().getOutcome()).isEqualTo(BattleOutcome.ENEMY_WIN);
        flow.onBattleOver(ctx);
        flow.continueAfterDefeat(ctx);
        assertThat(ctx.getRunState().getMercyLossCount()).isZero();
        assertThat(ctx.getPlayer().getGold()).isEqualTo(GameBalance.START_GOLD);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);
    }

    @Test
    @DisplayName("怜悯：第 3 败起 +1 金、每轮封顶 3（第 6 败不再发——口径 #10）")
    void mercyFromThirdLossCappedAtThreePerRound() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        deploy(ctx, "unit_trainee_01", 2, 5);
        int[] goldAfterLoss = new int[6];
        for (int i = 0; i < 6; i++) {
            defeatBySurrender(manager, flow, ctx);
            flow.continueAfterDefeat(ctx);
            goldAfterLoss[i] = ctx.getPlayer().getGold();
        }
        assertThat(goldAfterLoss[0]).isEqualTo(GameBalance.START_GOLD); // 第 1 败无金
        assertThat(goldAfterLoss[1]).isEqualTo(GameBalance.START_GOLD); // 第 2 败无金
        assertThat(goldAfterLoss[2]).isEqualTo(GameBalance.START_GOLD + 1); // 第 3 败起 +1
        assertThat(goldAfterLoss[5]).isEqualTo(GameBalance.START_GOLD + 3); // 封顶 3
        assertThat(ctx.getRunState().getMercyLossCount()).isEqualTo(6);
        assertThat(ctx.getRunState().getMercyGoldThisRound()).isEqualTo(3);
        assertThat(ctx.getRunState().drainNotices())
                .anyMatch(line -> line.contains("怜悯金币"));
    }

    // —— 胜局推进（PickChest 唯一出口 → 新轮/终局） ——

    @Test
    @DisplayName("PickChest 槽1 金币：入账 chestGold(round) 后推进新轮（敌阵重生成+免费刷新+通知）")
    void pickChestGoldAdvancesToNextRound() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        deploy(ctx, "unit_champion_01", 2, 5);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        new BattleSystem().runToEnd(ctx.getBattleState(), MAX_TICKS);
        flow.onBattleOver(ctx);
        int goldBefore = ctx.getPlayer().getGold();
        int consumedAtResult = ctx.getRng().getConsumedCount();
        manager.addCommand(new PickChestCommand(0));
        manager.executeAll(ctx);
        assertThat(ctx.getPlayer().getGold())
                .isEqualTo(goldBefore + GameBalance.chestGold(1, false));
        assertThat(ctx.getRunState().getRound()).isEqualTo(2);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);
        assertThat(ctx.getRunState().getPendingChest()).isNull();
        assertThat(ctx.getBattleState()).isNull();
        assertThat(ctx.getRunState().getEnemyWave()).hasSize(GameBalance.enemyCount(2));
        assertThat(ctx.getRng().getConsumedCount()) // 宝箱 0 + 敌阵 enemyCount(2) + 免费刷新 10
                .isEqualTo(consumedAtResult + GameBalance.enemyCount(2) + 2 * GameBalance.SHOP_SLOTS);
        assertThat(ctx.getRunState().drainNotices())
                .anyMatch(line -> line.contains("第 2 轮开始"));
    }

    @Test
    @DisplayName("PickChest 槽2 经验书：+CHEST_EXP_BOOK_GAIN 经验（Lv.1 恰好升 Lv.2）")
    void pickChestExpBookAddsExperience() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        deploy(ctx, "unit_champion_01", 2, 5);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        new BattleSystem().runToEnd(ctx.getBattleState(), MAX_TICKS);
        flow.onBattleOver(ctx);
        int goldBefore = ctx.getPlayer().getGold();
        manager.addCommand(new PickChestCommand(1));
        manager.executeAll(ctx);
        assertThat(ctx.getPlayer().getGold()).isEqualTo(goldBefore); // 经验书不动金
        assertThat(ctx.getPlayer().getLevel())
                .isEqualTo(2); // expToNextLevel(1)=4 == CHEST_EXP_BOOK_GAIN
        assertThat(ctx.getPlayer().getCurrentExp()).isZero();
        assertThat(ctx.getRunState().getRound()).isEqualTo(2);
    }

    @Test
    @DisplayName("PickChest 槽3 装备：发号入包（稀有度降级到有池的白剑）后推进")
    void pickChestEquipmentIntoInventory() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        deploy(ctx, "unit_champion_01", 2, 5);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        new BattleSystem().runToEnd(ctx.getBattleState(), MAX_TICKS);
        flow.onBattleOver(ctx);
        manager.addCommand(new PickChestCommand(2));
        manager.executeAll(ctx);
        assertThat(ctx.getPlayer().getInventory()).hasSize(1);
        Equipment gained = ctx.getPlayer().getInventory().get(0);
        assertThat(gained.getTemplate().getId()).isEqualTo("eq_t_sword"); // 成/传池空 → 降级白池
        assertThat(ctx.getRunState().getRound()).isEqualTo(2);
    }

    @Test
    @DisplayName("PickChest 拒绝：SHOPPING 期与非法槽位均静默 false（pendingChest 保留）")
    void pickChestRejectedOutsideResultOrBadIndex() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        manager.addCommand(new PickChestCommand(0)); // SHOPPING 期
        manager.executeAll(ctx);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);

        deploy(ctx, "unit_champion_01", 2, 5);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        new BattleSystem().runToEnd(ctx.getBattleState(), MAX_TICKS);
        flow.onBattleOver(ctx);
        ChestOption pendingBefore = ctx.getRunState().getPendingChest().getOptions().get(0);
        manager.addCommand(new PickChestCommand(99)); // 非法槽位
        manager.executeAll(ctx);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.RESULT);
        assertThat(ctx.getRunState().getPendingChest()).isNotNull();
        assertThat(ctx.getRunState().getPendingChest().getOptions().get(0))
                .isSameAs(pendingBefore); // 未被消费
    }

    @Test
    @DisplayName("新轮进入双清零：连败计数与本轮怜悯金归零（§5.1 关键区分）")
    void advanceAfterVictoryResetsMercyCounters() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        deploy(ctx, "unit_trainee_01", 2, 5);
        for (int i = 0; i < 3; i++) {
            defeatBySurrender(manager, flow, ctx);
            flow.continueAfterDefeat(ctx);
        }
        assertThat(ctx.getRunState().getMercyLossCount()).isEqualTo(3);
        assertThat(ctx.getRunState().getMercyGoldThisRound()).isEqualTo(1);
        ctx.getPlayer().undeploy(2, 5); // 换必胜夹具
        deploy(ctx, "unit_champion_01", 2, 5);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(ctx);
        new BattleSystem().runToEnd(ctx.getBattleState(), MAX_TICKS);
        flow.onBattleOver(ctx);
        manager.addCommand(new PickChestCommand(0));
        manager.executeAll(ctx);
        assertThat(ctx.getRunState().getRound()).isEqualTo(2);
        assertThat(ctx.getRunState().getMercyLossCount()).isZero();
        assertThat(ctx.getRunState().getMercyGoldThisRound()).isZero();
    }

    // —— 终局：第 25 轮经领箱通关（差异声明 #9：第 25 轮仍先领箱再 RUN_END） ——

    @Test
    @DisplayName("25 轮全胜经 PickChest 流转：RUN_END + endCause=COMPLETED + 熟练度 60+25×3=135")
    void round25CompletionThroughPickChestLeadsToRunEnd() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        deploy(ctx, "unit_champion_01", 2, 5);
        BattleSystem battleSystem = new BattleSystem();
        for (int round = 1; round <= GameBalance.TOTAL_ROUNDS; round++) {
            manager.addCommand(StartBattleCommand.INSTANCE);
            manager.executeAll(ctx);
            battleSystem.runToEnd(ctx.getBattleState(), MAX_TICKS);
            assertThat(ctx.getBattleState().getOutcome()).isEqualTo(BattleOutcome.PLAYER_WIN);
            flow.onBattleOver(ctx);
            assertThat(ctx.getRunState().getPendingChest()).isNotNull(); // 每轮胜局必先领箱
            manager.addCommand(new PickChestCommand(0));
            manager.executeAll(ctx);
        }
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.RUN_END);
        assertThat(ctx.getRunState().getRound()).isEqualTo(GameBalance.TOTAL_ROUNDS);
        assertThat(ctx.getRunState().getEndCause()).isEqualTo(RunEndCause.COMPLETED);
        assertThat(ctx.getRunState().getMasteryAwarded())
                .isEqualTo(GameBalance.MASTERY_COMPLETE_BONUS
                        + GameBalance.TOTAL_ROUNDS * GameBalance.MASTERY_EXP_PER_ROUND); // GDD_BASIC 完整口径（裁决 D3）：60 + 75
    }

    // —— StartRun heroId 一致性校验（Phase 6 CP8，沿 seed/sceneId 同款口径 #11） ——

    @Test
    @DisplayName("StartRun heroId 与上下文不一致 → false（零状态残留；null≠非 null 同拒）")
    void startRunRejectsMismatchedHeroId() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = new RunContext(new Player(GameBalance.START_GOLD),
                new RunState(TEST_SEED, "scene_forest", "hero_greg",
                        com.voidvvv.kz_auto_chess_n.entities.RunModifiers.EMPTY,
                        new SequentialIdIssuer()),
                data, new RandomGenerator(TEST_SEED));

        manager.addCommand(new StartRunCommand(TEST_SEED, "scene_forest", "hero_vera"));
        manager.executeAll(ctx);
        assertThat(ctx.getRunState().isRunStarted()).isFalse(); // heroId 错位 → 拒绝

        manager.addCommand(new StartRunCommand(TEST_SEED, "scene_forest", null));
        manager.executeAll(ctx);
        assertThat(ctx.getRunState().isRunStarted()).isFalse(); // null ≠ hero_greg → 拒绝

        manager.addCommand(new StartRunCommand(TEST_SEED, "scene_forest", "hero_greg"));
        manager.executeAll(ctx);
        assertThat(ctx.getRunState().isRunStarted()).isTrue(); // 一致 → 放行
    }

    // —— AbandonRun（Q5 裁决：暂停菜单放弃 → RUN_END 放弃文案） ——

    @Test
    @DisplayName("AbandonRun 两阶段生效：SHOPPING 与 BATTLE 均 RUN_END + ABANDONED + 熟练度按已达轮")
    void abandonRunWorksInShoppingAndBattle() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);

        RunContext shoppingCtx = newContext(data);
        flow.startRun(shoppingCtx);
        manager.addCommand(AbandonRunCommand.INSTANCE);
        manager.executeAll(shoppingCtx);
        assertThat(shoppingCtx.getRunState().getPhase()).isEqualTo(GamePhase.RUN_END);
        assertThat(shoppingCtx.getRunState().getEndCause()).isEqualTo(RunEndCause.ABANDONED);
        assertThat(shoppingCtx.getRunState().getMasteryAwarded()).isEqualTo(3); // round 1 × 3

        RunContext battleCtx = newContext(data);
        flow.startRun(battleCtx);
        deploy(battleCtx, "unit_champion_01", 2, 5);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(battleCtx);
        manager.addCommand(AbandonRunCommand.INSTANCE);
        manager.executeAll(battleCtx);
        assertThat(battleCtx.getRunState().getPhase()).isEqualTo(GamePhase.RUN_END);
        assertThat(battleCtx.getRunState().getEndCause()).isEqualTo(RunEndCause.ABANDONED);
        assertThat(battleCtx.getBattleState()).isNull(); // 战斗实例整体丢弃
    }

    @Test
    @DisplayName("AbandonRun RESULT 期拒绝（口径 #14：胜局必须领箱、败局走继续）")
    void abandonRunRejectedInResult() {
        GameData data = demoData();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        defeatBySurrender(manager, flow, ctx);
        manager.addCommand(AbandonRunCommand.INSTANCE);
        manager.executeAll(ctx);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.RESULT);
        assertThat(ctx.getRunState().getEndCause()).isNull();
    }

    @Test
    @DisplayName("熟练度结算注入式：自定义 MasteryCalculator 收到 (ABANDONED, 轮数) 且产出暂存")
    void masterySettledThroughInjectedCalculator() {
        GameData data = demoData();
        final List<RunEndCause> causes = new ArrayList<RunEndCause>();
        final List<Integer> roundsSeen = new ArrayList<Integer>();
        RunFlowSystem flow = new RunFlowSystem(new MasteryCalculator() {
            @Override
            public int settle(RunEndCause cause, int roundsReached) {
                causes.add(cause);
                roundsSeen.add(roundsReached);
                return 77;
            }
        });
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        flow.startRun(ctx);
        manager.addCommand(AbandonRunCommand.INSTANCE);
        manager.executeAll(ctx);
        assertThat(causes).containsExactly(RunEndCause.ABANDONED);
        assertThat(roundsSeen).containsExactly(1);
        assertThat(ctx.getRunState().getMasteryAwarded()).isEqualTo(77);
    }

    // —— 同 seed 整局对照（验收标准 §九第 3 条；restart 契约 = 新鲜上下文复入 startRun） ——

    @Test
    @DisplayName("restart 同 seed 重演一致：两 run 各轮事件流与敌阵逐位 equals")
    void restartReplaysIdenticalEventStreams() {
        GameData data = demoData();
        RunReplay runA = playFullRun(data, true);
        RunReplay runB = playFullRun(data, false);
        assertThat(runA.rounds).hasSize(GameBalance.TOTAL_ROUNDS);
        assertThat(runB.rounds).hasSize(GameBalance.TOTAL_ROUNDS);
        for (int round = 0; round < GameBalance.TOTAL_ROUNDS; round++) {
            assertThat(runB.rounds.get(round)).containsExactlyElementsOf(runA.rounds.get(round));
            assertThat(runB.waves.get(round)).containsExactlyElementsOf(runA.waves.get(round));
        }
        assertThat(runB.finalGold).isEqualTo(runA.finalGold);
    }

    /** 整局推演（全胜口径）：每轮 开战 → runToEnd → RESULT → PickChest(0)；useRestart=true 经 restart 复入 */
    private RunReplay playFullRun(GameData data, boolean useRestart) {
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = armedManager(flow);
        RunContext ctx = newContext(data);
        if (useRestart) {
            flow.restart(ctx);
        } else {
            flow.startRun(ctx);
        }
        deploy(ctx, "unit_champion_01", 2, 5);
        BattleSystem battleSystem = new BattleSystem();
        RunReplay replay = new RunReplay();
        while (ctx.getRunState().getPhase() != GamePhase.RUN_END) {
            replay.waves.add(new ArrayList<WaveSpec>(ctx.getRunState().getEnemyWave()));
            manager.addCommand(StartBattleCommand.INSTANCE);
            manager.executeAll(ctx);
            BattleState state = ctx.getBattleState();
            battleSystem.runToEnd(state, MAX_TICKS);
            assertThat(state.isOver()).isTrue();
            replay.rounds.add(new ArrayList<CombatEvent>(state.getEvents()));
            flow.onBattleOver(ctx);
            manager.addCommand(new PickChestCommand(0));
            manager.executeAll(ctx);
        }
        replay.finalGold = ctx.getPlayer().getGold();
        return replay;
    }

    /** 整局推演产物（逐轮事件流 / 逐轮敌阵 / 终局金币） */
    private static final class RunReplay {
        final List<List<CombatEvent>> rounds = new ArrayList<List<CombatEvent>>();
        final List<List<WaveSpec>> waves = new ArrayList<List<WaveSpec>>();
        int finalGold;
    }
}
