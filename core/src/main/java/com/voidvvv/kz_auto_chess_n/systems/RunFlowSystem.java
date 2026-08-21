package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.MoveUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.StartBattleCommand;
import com.voidvvv.kz_auto_chess_n.command.SurrenderCommand;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.IdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;

import java.util.List;

/**
 * 局内流程守卫（Q2/Q3）：阶段推进 / 轮开始事件子集 / 演示名单 / 三个命令 handler 注册。
 *
 * <p>系统行为不经命令队列（input §7.1）——阶段推进由 BattleScreen 在逻辑 tick 内观察
 * {@code isOver} 后委托（口径 #7）。零 Gdx（JUnit 零后端前提不破）。
 * 演示名单沿 {@code tools/BattleConsoleMain.buyAndDeploy} 先例：经济与买卖归 Phase 5。
 */
public final class RunFlowSystem {

    /** 演示局固定 seed（口径 #22：重开同 seed 确定性对照；Phase 5 StartRun 命令化后由 UI 域给定） */
    public static final long DEMO_SEED = 42L;

    private final WaveGenerator waveGenerator = new WaveGenerator();
    private final MoveUnitExecutor moveUnitExecutor = new MoveUnitExecutor();
    private final BattleSystem battleSystem = new BattleSystem();
    /** RESULT 横幅已停留秒数（onBattleOver 归零） */
    private float resultTimer;

    /**
     * 注册本期三个命令 handler（input §6.1：handler 由所属 system 注册；Phase 5 拆分）。
     * 门控矩阵（architecture §5.2 精神）：MoveUnit/StartBattle 仅 SHOPPING、Surrender 仅 BATTLE。
     */
    public void registerHandlers(CommandManager manager) {
        manager.registerHandler(MoveUnitCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING) {
                return false;
            }
            MoveUnitCommand move = (MoveUnitCommand) cmd;
            return moveUnitExecutor.move(ctx.getPlayer(), move.getUnitId(), move.getTarget());
        });
        manager.registerHandler(StartBattleCommand.class, (cmd, ctx) -> {
            RunState runState = ctx.getRunState();
            if (runState.getPhase() != GamePhase.SHOPPING) {
                return false;
            }
            BattleState state = battleSystem.startBattle(ctx.getPlayer(), runState.getEnemyWave(),
                    ctx.getGameData(), ctx.getRng(), runState.getIdIssuer()); // 零棋子允许开战
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
    }

    /**
     * 新开一局（要求新鲜 RunContext：round=1 / phase=SHOPPING 由 RunState 初始态保证）：
     * 发放演示名单（战士(2,5) / 刺客(3,5) / 游侠(2,6)，1 星）→ beginRound。
     */
    public void startNewRun(RunContext ctx) {
        RunState runState = ctx.getRunState();
        runState.setPhase(GamePhase.SHOPPING);
        ctx.setBattleState(null);
        grantDemoRoster(ctx);
        beginRound(ctx);
    }

    /** 轮开始事件子集（Q3）：enemyWave = generateEnemyWave(round, sceneId, data, rng)（RNG 消耗 = 杂兵数）。
     *  商店免费刷新 / 怜悯推 Phase 5。 */
    public void beginRound(RunContext ctx) {
        RunState runState = ctx.getRunState();
        List<WaveSpec> wave = waveGenerator.generateEnemyWave(
                runState.getRound(), runState.getSceneId(), ctx.getGameData(), ctx.getRng());
        runState.setEnemyWave(wave);
    }

    /** BATTLE→RESULT（BattleScreen 观察 isOver 后委托，口径 #7）；battleState 保留供横幅读 outcome */
    public void onBattleOver(RunContext ctx) {
        if (ctx.getRunState().getPhase() != GamePhase.BATTLE) {
            return;
        }
        resultTimer = 0f;
        ctx.getRunState().setPhase(GamePhase.RESULT);
    }

    /** RESULT 横幅计时：累积满 RESULT_BANNER_SECONDS 自动推进（Q3） */
    public void tickResult(RunContext ctx, float dt) {
        if (ctx.getRunState().getPhase() != GamePhase.RESULT) {
            return;
        }
        resultTimer += dt;
        if (resultTimer >= GameBalance.RESULT_BANNER_SECONDS) {
            continueAfterResult(ctx);
        }
    }

    /**
     * 点击继续（横幅 Actor 调用）：round==TOTAL_ROUNDS → RUN_END；
     * 否则 round+1 + battleState=null + phase=SHOPPING + beginRound（敌阵重生成）。
     * 本期胜负统一推进轮次（差异声明 #6：判负同轮重试与怜悯推 Phase 5）。
     */
    public void continueAfterResult(RunContext ctx) {
        RunState runState = ctx.getRunState();
        if (runState.getPhase() != GamePhase.RESULT) {
            return;
        }
        ctx.setBattleState(null); // 战斗实例整体丢弃（双实体语义）
        if (runState.getRound() >= GameBalance.TOTAL_ROUNDS) {
            runState.setPhase(GamePhase.RUN_END);
            return;
        }
        runState.advanceRound();
        runState.setPhase(GamePhase.SHOPPING);
        beginRound(ctx);
    }

    /**
     * RUN_END 重开：同 seed 重建——调用方（Screen 装配点）以 {@link #DEMO_SEED} 组装
     * 新鲜 RunContext（新 Player / RunState / RandomGenerator）后复入 startNewRun。
     */
    public void restart(RunContext ctx) {
        startNewRun(ctx);
    }

    /** 演示名单（Q2 兵源：沿 BattleConsoleMain.java:62-64 先例，经济不动） */
    private void grantDemoRoster(RunContext ctx) {
        Player player = ctx.getPlayer();
        IdIssuer idIssuer = ctx.getRunState().getIdIssuer();
        GameData data = ctx.getGameData();
        buyAndDeploy(player, idIssuer, data, "unit_warrior_01", 2, 5);
        buyAndDeploy(player, idIssuer, data, "unit_assassin_01", 3, 5);
        buyAndDeploy(player, idIssuer, data, "unit_ranger_01", 2, 6);
    }

    /** 发号 → 入席 → 部署（沿 BattleConsoleMain.buyAndDeploy 先例） */
    private static void buyAndDeploy(Player player, IdIssuer idIssuer, GameData data,
                                     String unitId, int gridX, int gridY) {
        Unit unit = new Unit(idIssuer.nextId(), data.getUnit(unitId), 1);
        player.addToBench(unit);
        player.deploy(unit, gridX, gridY);
    }
}
