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
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.board.BattleRenderer;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
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
        uiStage.addActor(topBar);
        uiStage.addActor(shoppingHud);
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
        // 两层 multiplexer（口径 #20）：uiStage > boardProcessor（提交 9 接入；dialogStage/keyProcessor 位预留）
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uiStage);
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
        battleRenderer.draw(batch, runContext, alpha, renderClock, paused ? 0f : delta);
        topBar.refresh(runContext);
        shoppingHud.setVisible(runContext.getRunState().getPhase() == GamePhase.SHOPPING);
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

    /** 同 DEMO_SEED 组装新鲜上下文（口径 #22；restart 重开由提交 9 的 RunEndPanel 接入） */
    private RunContext newContext() {
        String sceneId = data.getScenes().keySet().iterator().next(); // 首场景（种子仅森林）
        return new RunContext(new Player(GameBalance.START_GOLD),
                new RunState(RunFlowSystem.DEMO_SEED, sceneId, new SequentialIdIssuer()),
                data, new RandomGenerator(RunFlowSystem.DEMO_SEED));
    }
}
