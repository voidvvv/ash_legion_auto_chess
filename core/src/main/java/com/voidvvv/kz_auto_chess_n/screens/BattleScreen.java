package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.input.BoardInputProcessor;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.board.BattleRenderer;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.render.ui.BattleHud;
import com.voidvvv.kz_auto_chess_n.render.ui.ResultBanner;
import com.voidvvv.kz_auto_chess_n.render.ui.RunEndPanel;
import com.voidvvv.kz_auto_chess_n.render.ui.ShoppingHud;
import com.voidvvv.kz_auto_chess_n.render.ui.TopBar;
import com.voidvvv.kz_auto_chess_n.systems.BattleSystem;
import com.voidvvv.kz_auto_chess_n.systems.RunFlowSystem;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

/**
 * 战斗屏（render §三/§4.1；screens 是唯一装配点）。
 *
 * <p>帧循环（input §5.3 + 口径 #5/#7）：accumulator 累积（钳 MAX_DELTA、乘 speedFactor）
 * → 固定 LOGIC_STEP 步进：executeAll(runContext) → BATTLE 门控 step + isOver 观察-委托 →
 * RESULT 计时 → 超限丢弃。渲染段：棋盘域自绘 + UI 域 Stage（⑨），可见性按 phase 联动。
 * 阶段推进全部委托 RunFlowSystem——本类只做点火器/观察者（architecture §七）。
 */
public final class BattleScreen implements Screen {
    private final Game game;
    private final Assets assets;
    private final GameData data;

    private final SpriteBatch batch;
    private final OrthographicCamera worldCamera = new OrthographicCamera();
    private final FitViewport worldViewport;
    private final com.badlogic.gdx.scenes.scene2d.Stage uiStage;

    private final BattleSystem battleSystem = new BattleSystem();
    private final RunFlowSystem runFlowSystem = new RunFlowSystem();
    private final CommandManager commandManager = new CommandManager();
    private RunContext runContext;

    private final BattleRenderer battleRenderer;
    private final TopBar topBar;
    private final ShoppingHud shoppingHud;
    private final BattleHud battleHud;
    private final ResultBanner resultBanner;
    private final RunEndPanel runEndPanel;
    private BoardInputProcessor boardProcessor;

    private float accumulator;
    private float renderClock;
    private boolean paused;
    /** ×1/×2 变速（口径 #5：只乘 accumulator 消费速率，不进模拟路径） */
    private float speedFactor = 1f;

    public BattleScreen(Game game, Assets assets, GameData data) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.batch = new SpriteBatch();
        this.worldViewport = new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H, worldCamera);
        this.uiStage = new com.badlogic.gdx.scenes.scene2d.Stage(
                new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H));
        this.battleRenderer = new BattleRenderer(assets);
        this.topBar = new TopBar(assets);
        this.shoppingHud = new ShoppingHud(commandManager, assets);
        this.battleHud = new BattleHud(commandManager, assets, new BattleHud.SpeedListener() {
            @Override
            public void onSpeedChanged(float factor) {
                speedFactor = factor;
            }
        });
        this.resultBanner = new ResultBanner(runFlowSystem, new java.util.function.Supplier<RunContext>() {
            @Override
            public RunContext get() {
                return runContext;
            }
        }, assets);
        this.runEndPanel = new RunEndPanel(assets, new RunEndPanel.RestartListener() {
            @Override
            public void onRestart() {
                restartRun();
            }
        }, new java.util.function.Supplier<RunContext>() {
            @Override
            public RunContext get() {
                return runContext;
            }
        });
        uiStage.addActor(topBar);
        uiStage.addActor(shoppingHud);
        uiStage.addActor(battleHud);
        uiStage.addActor(resultBanner);
        uiStage.addActor(runEndPanel);
    }

    // —— 生命周期 ——

    @Override
    public void show() {
        this.runContext = newContext();
        runFlowSystem.registerHandlers(commandManager);
        runFlowSystem.startNewRun(runContext);
        accumulator = 0f;
        renderClock = 0f;
        paused = false;
        speedFactor = 1f;
        battleHud.resetSpeed();
        this.boardProcessor = new BoardInputProcessor(worldViewport, commandManager,
                new java.util.function.Supplier<RunContext>() {
                    @Override
                    public RunContext get() {
                        return runContext;
                    }
                },
                new java.util.function.BooleanSupplier() { // 模态阻断位（本期常 false；Phase 5 接 UIDialogManager）
                    @Override
                    public boolean getAsBoolean() {
                        return false;
                    }
                });
        // 两层 multiplexer（口径 #20）：uiStage > boardProcessor（dialogStage/keyProcessor 位预留）
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(boardProcessor);
        Gdx.input.setInputProcessor(multiplexer);
    }

    @Override
    public void render(float delta) {
        if (!paused) {
            stepSimulation(delta);
            renderClock += delta;
        }
        float alpha = paused ? 0f : accumulator / GameBalance.LOGIC_STEP;
        worldViewport.apply();
        batch.setProjectionMatrix(worldCamera.combined);
        battleRenderer.draw(batch, runContext, alpha, renderClock, paused ? 0f : delta, boardProcessor);
        GamePhase phase = runContext.getRunState().getPhase();
        topBar.refresh(runContext);
        shoppingHud.setVisible(phase == GamePhase.SHOPPING);
        battleHud.setVisible(phase == GamePhase.BATTLE);
        if (phase == GamePhase.BATTLE) {
            battleHud.refresh(runContext.getBattleState());
        }
        resultBanner.setVisible(phase == GamePhase.RESULT);
        if (phase == GamePhase.RESULT && runContext.getBattleState() != null) {
            resultBanner.refresh(runContext.getBattleState().getOutcome()); // 横幅读 outcome（口径 #6）
        }
        runEndPanel.setVisible(phase == GamePhase.RUN_END);
        uiStage.act(delta);
        uiStage.getViewport().apply();
        uiStage.draw();
    }

    /** 固定步长逻辑段（input §5.3） */
    private void stepSimulation(float delta) {
        accumulator += Math.min(delta, GameBalance.MAX_DELTA) * speedFactor;
        int ticks = 0;
        while (accumulator >= GameBalance.LOGIC_STEP && ticks < GameBalance.MAX_TICKS_PER_FRAME) {
            commandManager.executeAll(runContext);
            GamePhase phase = runContext.getRunState().getPhase();
            if (phase == GamePhase.BATTLE && runContext.getBattleState() != null) {
                battleSystem.step(runContext.getBattleState());
                if (runContext.getBattleState().isOver()) {
                    runFlowSystem.onBattleOver(runContext); // 观察-委托（口径 #7）
                }
            } else if (phase == GamePhase.RESULT) {
                runFlowSystem.tickResult(runContext, GameBalance.LOGIC_STEP); // 横幅自动推进
            }
            accumulator -= GameBalance.LOGIC_STEP;
            ticks++;
        }
        if (accumulator >= GameBalance.LOGIC_STEP) {
            accumulator = 0f; // 超限丢弃（死亡螺旋防御，与 MAX_TICKS 双保险）
        }
    }

    @Override
    public void resize(int width, int height) {
        worldViewport.update(width, height, true); // 双 viewport 同参数同步（render §2.1）
        uiStage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
        paused = true; // 冻结 accumulator（Android 挂起）
    }

    @Override
    public void resume() {
        paused = false;
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null); // 防僵尸监听（input §2.3）
    }

    @Override
    public void dispose() {
        batch.dispose();
        uiStage.dispose();
        // Assets 归 Main 持有，不在此弃
    }

    /** 同 DEMO_SEED 组装新鲜上下文（口径 #22：重开确定性对照） */
    private RunContext newContext() {
        String sceneId = data.getScenes().keySet().iterator().next(); // 首场景（种子仅森林）
        return new RunContext(new Player(GameBalance.START_GOLD),
                new RunState(RunFlowSystem.DEMO_SEED, sceneId, new SequentialIdIssuer()),
                data, new RandomGenerator(RunFlowSystem.DEMO_SEED));
    }

    /** RUN_END 重开：换新鲜上下文后复入 startNewRun（RunFlowSystem.restart 契约） */
    private void restartRun() {
        this.runContext = newContext();
        runFlowSystem.restart(runContext);
        accumulator = 0f;
        battleHud.resetSpeed();
    }
}
