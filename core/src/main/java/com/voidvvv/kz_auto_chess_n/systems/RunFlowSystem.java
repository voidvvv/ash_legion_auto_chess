package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.AbandonRunCommand;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.PickChestCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.StartBattleCommand;
import com.voidvvv.kz_auto_chess_n.command.StartRunCommand;
import com.voidvvv.kz_auto_chess_n.command.SurrenderCommand;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.ChestOffer;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.entities.ChestOrigin;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;

import java.util.List;
import java.util.Objects;

/**
 * 局内流程守卫（architecture §五）：阶段推进 / 轮开始事件 / 宝箱与败箱流转（3 次机会制判负三分岔，GDD §2.2）/
 * 五个流程命令 handler。
 *
 * <p>系统行为不经命令队列（input §7.1）——阶段推进由 BattleScreen 在逻辑 tick 内观察
 * {@code isOver} 后委托。handler 拆分（input §6.1）：经营归 ShopSystem、名单归 RosterSystem、
 * 穿脱归 EquipmentSystem（MoveUnit 注册块已迁 RosterSystem，见 §6.CP12）。
 * 零 Gdx（JUnit 零后端前提不破）。Q6 裁决：演示名单已删，兵源 = 商店自购。
 */
public final class RunFlowSystem {

    private final WaveGenerator waveGenerator = new WaveGenerator();
    private final BattleSystem battleSystem = new BattleSystem();
    private final ChestSystem chestSystem = new ChestSystem();
    private final MasteryCalculator masteryCalculator;
    /** RESULT 横幅已停留秒数（onBattleOver 归零；仅败局自动推进消费） */
    private float resultTimer;

    /** 注入式熟练度结算（Q5 裁决：纯函数 stub，Phase 6 接档案域换实现） */
    public RunFlowSystem(MasteryCalculator masteryCalculator) {
        this.masteryCalculator = Objects.requireNonNull(masteryCalculator, "masteryCalculator 不能为 null");
    }

    public RunFlowSystem() {
        this(MasteryCalculator.GDD_BASIC);
    }

    /**
     * 注册流程命令 handler（门控矩阵 architecture §5.2）：
     * StartRun 仅新鲜上下文 / StartBattle 仅 SHOPPING / Surrender 仅 BATTLE /
     * PickChest 仅 RESULT 且有未领宝箱 / AbandonRun 仅 SHOPPING+BATTLE（口径 #14）。
     */
    public void registerHandlers(CommandManager manager) {
        manager.registerHandler(StartRunCommand.class, (cmd, ctx) -> {
            StartRunCommand start = (StartRunCommand) cmd;
            RunState runState = ctx.getRunState();
            if (runState.isRunStarted() || runState.getRound() != 1
                    || runState.getPhase() != GamePhase.SHOPPING
                    || start.getSeed() != runState.getSeed()
                    || !start.getSceneId().equals(runState.getSceneId())
                    || !Objects.equals(start.getHeroId(), runState.getHeroId())) {
                return false; // 非新鲜上下文或装配点错位（口径 #11，静默防线；heroId 同款校验 Phase 6）
            }
            startRun(ctx);
            return true;
        });
        manager.registerHandler(StartBattleCommand.class, (cmd, ctx) -> {
            RunState runState = ctx.getRunState();
            if (runState.getPhase() != GamePhase.SHOPPING) {
                return false;
            }
            BattleState state = battleSystem.startBattle(ctx.getPlayer(), runState.getEnemyWave(),
                    ctx.getGameData(), ctx.getRng(), runState.getIdIssuer(),
                    runState.getModifiers()); // 零棋子允许开战；局外修正透传（Phase 6）
            ctx.setBattleState(state);
            runState.setPhase(GamePhase.BATTLE);
            return true;
        });
        manager.registerHandler(SurrenderCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.BATTLE || ctx.getBattleState() == null) {
                return false;
            }
            ctx.getBattleState().finish(BattleOutcome.ENEMY_WIN); // 幂等（finish 首个结局生效）
            return true;
        });
        manager.registerHandler(PickChestCommand.class, (cmd, ctx) -> {
            RunState runState = ctx.getRunState();
            ChestOffer offer = runState.getPendingChest();
            if (runState.getPhase() != GamePhase.RESULT || offer == null) {
                return false;
            }
            ChestOption option = offer.optionAt(((PickChestCommand) cmd).getOptionIndex());
            if (option == null) {
                return false;
            }
            runState.addNotice(chestSystem.apply(option, ctx.getPlayer(),
                    runState.getIdIssuer(), ctx.getGameData()));
            runState.setPendingChest(null); // 领取即消费（胜局 advanceAfterVictory 亦清；败局分支必先清——守卫拦未领箱）
            if (offer.getOrigin() == ChestOrigin.DEFEAT) {
                continueAfterDefeat(ctx); // 败箱领取 → 同轮重试（机会已在判负时计过）
            } else {
                advanceAfterVictory(ctx); // 胜局领取即推进（唯一出口，口径 #9）
            }
            return true;
        });
        manager.registerHandler(AbandonRunCommand.class, (cmd, ctx) -> {
            GamePhase phase = ctx.getRunState().getPhase();
            if (phase != GamePhase.SHOPPING && phase != GamePhase.BATTLE) {
                return false;
            }
            endRun(ctx, RunEndCause.ABANDONED);
            return true;
        });
    }

    /**
     * 新开一局（StartRun handler 体；测试与 restart 直调）。要求新鲜 RunContext
     * （round=1 / phase=SHOPPING / runStarted=false 由 RunState 初始态保证）。
     * 轮开始事件：敌阵生成（RNG=杂兵数）+ 商店免费刷新（RNG=10）+ 通知行。
     */
    public void startRun(RunContext ctx) {
        RunState runState = ctx.getRunState();
        runState.markRunStarted();
        runState.setPhase(GamePhase.SHOPPING);
        ctx.setBattleState(null);
        beginRound(ctx);
        ctx.getShop().reroll(runState.getRound(), ctx.getGameData(), ctx.getRng(),
                runState.getModifiers()); // 免费刷新同吃概率加成与池门控（Phase 6，口径与 RefreshShop 一致）
        runState.addNotice("第 " + runState.getRound() + " 轮开始（商店免费刷新）");
    }

    /** 轮开始事件子集（Phase 2 口径）：enemyWave = generateEnemyWave(...)（RNG 消耗 = 杂兵数）。 */
    public void beginRound(RunContext ctx) {
        RunState runState = ctx.getRunState();
        List<WaveSpec> wave = waveGenerator.generateEnemyWave(
                runState.getRound(), runState.getSceneId(), ctx.getGameData(), ctx.getRng());
        runState.setEnemyWave(wave);
    }

    /**
     * BATTLE→RESULT（BattleScreen 观察 isOver 后委托，口径 #7）：胜局 roll 宝箱（RNG=2）；
     * 判负瞬间机会 +1（零棋子照扣——D-a 防刷口径，GDD §2.2）并按三分岔决定败箱：
     * 败 1~2 且上场 > 0 → 公式构造败箱（零 RNG）；零棋子败 / 第 3 败 → 无箱横幅。
     * battleState 保留供横幅读 outcome。
     */
    public void onBattleOver(RunContext ctx) {
        if (ctx.getRunState().getPhase() != GamePhase.BATTLE) {
            return;
        }
        resultTimer = 0f;
        RunState runState = ctx.getRunState();
        runState.setPhase(GamePhase.RESULT);
        if (ctx.getBattleState().getOutcome() == BattleOutcome.PLAYER_WIN) {
            runState.setPendingChest(chestSystem.roll(
                    runState.getRound(), ctx.getGameData(), ctx.getRng()));
            return;
        }
        // 判负瞬间机会 +1（零棋子照扣——D-a 防刷口径；GDD §2.2）
        runState.setDefeatCountPerRound(runState.getDefeatCountPerRound() + 1);
        boolean chanceLeft = runState.getDefeatCountPerRound() < GameBalance.DEFEAT_LIMIT_PER_ROUND;
        boolean deployed = playerSideCount(ctx.getBattleState()) > 0;
        if (chanceLeft && deployed) {
            // 败 1~2 且上场 > 0：公式构造败箱（零 RNG），PickChest 唯一出口（GDD §2.2）
            runState.setPendingChest(chestSystem.buildDefeatOffer(runState.getRound()));
        }
        // 其余（零棋子败 / 第 3 败）：无败箱，横幅 3s/点击 → continueAfterDefeat 分岔
    }

    /** RESULT 横幅计时：仅败局自动推进（胜局必须 PickChest，无自动出口——口径 #9） */
    public void tickResult(RunContext ctx, float dt) {
        if (ctx.getRunState().getPhase() != GamePhase.RESULT
                || ctx.getRunState().getPendingChest() != null) {
            return;
        }
        resultTimer += dt;
        if (resultTimer >= GameBalance.RESULT_BANNER_SECONDS) {
            continueAfterDefeat(ctx);
        }
    }

    /**
     * 败局继续（横幅点击或自动）：机会未尽 → 同轮重试（round/敌阵/商店全不变，GDD §2.2）；
     * 机会耗尽（第 3 败）→ endRun(DEFEATED) 失败结算。终局路由单点（点击/自动共用入口）。
     */
    public void continueAfterDefeat(RunContext ctx) {
        RunState runState = ctx.getRunState();
        if (runState.getPhase() != GamePhase.RESULT || runState.getPendingChest() != null) {
            return; // 败箱未领不可推进（PickChest 唯一出口，与胜局同律——口径 #9）
        }
        ctx.setBattleState(null); // 战斗实例整体丢弃（双实体语义）
        if (runState.getDefeatCountPerRound() >= GameBalance.DEFEAT_LIMIT_PER_ROUND) {
            endRun(ctx, RunEndCause.DEFEATED); // 第 3 败终局（GDD §2.2；熟练度 = 轮×3）
            return;
        }
        runState.setPhase(GamePhase.SHOPPING);
    }

    /**
     * 胜局推进（PickChest handler 结算奖励后调用）：round==25 → RUN_END(COMPLETED)；
     * 否则 round+1 + 怜悯双清零 + 敌阵重生成 + 商店免费刷新（architecture §5.1"新轮进入"）。
     */
    public void advanceAfterVictory(RunContext ctx) {
        RunState runState = ctx.getRunState();
        ctx.setBattleState(null);
        runState.setPendingChest(null);
        if (runState.getRound() >= GameBalance.TOTAL_ROUNDS) {
            endRun(ctx, RunEndCause.COMPLETED); // 第 25 轮领箱后通关（architecture §4.4 回放流终点）
            return;
        }
        runState.advanceRound();
        runState.setDefeatCountPerRound(0); // 新轮机会清零（§5.1 关键区分：重试不清、新轮清）
        runState.setPhase(GamePhase.SHOPPING);
        beginRound(ctx);
        ctx.getShop().reroll(runState.getRound(), ctx.getGameData(), ctx.getRng(),
                runState.getModifiers()); // 新轮免费刷新同吃加成与门控（Phase 6）
        runState.addNotice("第 " + runState.getRound() + " 轮开始（商店免费刷新）");
    }

    /** RUN_END 进入：endCause + 熟练度结算 stub（Q5 裁决；Phase 6 接档案域） */
    private void endRun(RunContext ctx, RunEndCause cause) {
        RunState runState = ctx.getRunState();
        ctx.setBattleState(null);
        runState.setPendingChest(null);
        runState.setEndCause(cause);
        runState.setMasteryAwarded(masteryCalculator.settle(cause, runState.getRound()));
        runState.setPhase(GamePhase.RUN_END);
    }

    /** RUN_END 重开：调用方（Screen 装配点）已用 UI 边界新 seed 组装新鲜 RunContext，复入 startRun */
    public void restart(RunContext ctx) {
        startRun(ctx);
    }

    /** 刚结束战斗的玩家侧单位总数（含已清扫亡者——零棋子判定，口径 #8） */
    private static int playerSideCount(BattleState state) {
        int count = 0;
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() == Side.PLAYER) {
                count++;
            }
        }
        return count;
    }
}
