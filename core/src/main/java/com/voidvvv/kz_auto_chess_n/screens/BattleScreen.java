package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.EquipItemCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.StartRunCommand;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.input.BoardInputProcessor;
import com.voidvvv.kz_auto_chess_n.input.GlobalKeyProcessor;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.board.BattleRenderer;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.render.ui.BattleHud;
import com.voidvvv.kz_auto_chess_n.render.ui.ChestDialog;
import com.voidvvv.kz_auto_chess_n.render.ui.EquipPendingState;
import com.voidvvv.kz_auto_chess_n.render.ui.InventoryPanel;
import com.voidvvv.kz_auto_chess_n.render.ui.NotificationPanel;
import com.voidvvv.kz_auto_chess_n.render.ui.PauseMenuDialog;
import com.voidvvv.kz_auto_chess_n.render.ui.ResultBanner;
import com.voidvvv.kz_auto_chess_n.render.ui.RunEndPanel;
import com.voidvvv.kz_auto_chess_n.render.ui.ShopBar;
import com.voidvvv.kz_auto_chess_n.render.ui.ShoppingHud;
import com.voidvvv.kz_auto_chess_n.render.ui.SynergyPanel;
import com.voidvvv.kz_auto_chess_n.render.ui.TopBar;
import com.voidvvv.kz_auto_chess_n.render.ui.UIDialogManager;
import com.voidvvv.kz_auto_chess_n.render.ui.UnitDetailDialog;
import com.voidvvv.kz_auto_chess_n.systems.BattleSystem;
import com.voidvvv.kz_auto_chess_n.systems.EquipmentSystem;
import com.voidvvv.kz_auto_chess_n.systems.RosterSystem;
import com.voidvvv.kz_auto_chess_n.systems.RunFlowSystem;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

/**
 * 战斗屏（render §三/§4.1；screens 是唯一装配点）。
 *
 * <p>帧循环（input §5.3 + 口径 #5/#7）：accumulator 累积（钳 MAX_DELTA、乘 speedFactor）
 * → 固定 LOGIC_STEP 步进：executeAll(runContext) → BATTLE 门控 step + isOver 观察-委托 →
 * RESULT 计时 → 超限丢弃。渲染段：棋盘域自绘 + UI 域 Stage（⑨），可见性按 phase 联动。
 * 阶段推进全部委托 RunFlowSystem——本类只做点火器/观察者（architecture §七）。
 *
 * <p>Phase 5 装配（CP29）：四 system 注册 handler；四层输入 multiplexer
 * （dialogStage &gt; uiStage &gt; boardProcessor &gt; keyProcessor，input §2.2）；
 * 模态冻结 = paused || isShowing（口径 #14）；通知三流挂接；seed 由 UI 域边界给定
 * （MainMenu START 传入、RESTART 换新——Q3 裁决），StartRun 以命令入队 = 回放流第 0 条记录。
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
    private final ShopSystem shopSystem = new ShopSystem();
    private final RosterSystem rosterSystem = new RosterSystem();
    private final EquipmentSystem equipmentSystem = new EquipmentSystem();
    private final EquipPendingState equipPending = new EquipPendingState();
    private RunContext runContext;

    private final BattleRenderer battleRenderer;
    /** 以下面板/弹窗依赖构造参数（assets/data/commandManager）——构造器内初始化 */
    private final UIDialogManager dialogManager;
    private final GlobalKeyProcessor keyProcessor;
    private final TopBar topBar;
    private final ShoppingHud shoppingHud;
    private final BattleHud battleHud;
    private final ResultBanner resultBanner;
    private final RunEndPanel runEndPanel;
    private final ShopBar shopBar;
    private final InventoryPanel inventoryPanel;
    private final SynergyPanel synergyPanel;
    private final NotificationPanel notificationPanel;
    private final ChestDialog chestDialog;
    private final UnitDetailDialog unitDetailDialog;
    private final PauseMenuDialog pauseMenuDialog;
    private BoardInputProcessor boardProcessor;

    /** 本局 seed（UI 域边界给定——MainMenu START 传入；RESTART 换新，Q3 裁决） */
    private long seed;
    /** 宝箱弹窗在栈标记（防重复 push；领取/离开 RESULT 后收起，CP29） */
    private boolean chestShown;

    private float accumulator;
    private float renderClock;
    private boolean paused;
    /** ×1/×2 变速（口径 #5：只乘 accumulator 消费速率，不进模拟路径） */
    private float speedFactor = 1f;

    public BattleScreen(Game game, Assets assets, GameData data, long seed) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.seed = seed;
        this.batch = new SpriteBatch();
        this.worldViewport = new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H, worldCamera);
        this.uiStage = new com.badlogic.gdx.scenes.scene2d.Stage(
                new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H));
        this.battleRenderer = new BattleRenderer(assets);
        this.dialogManager = new UIDialogManager(assets);
        this.topBar = new TopBar(assets, new TopBar.PauseListener() {
            @Override
            public void onPauseRequested() {
                dialogManager.push(pauseMenuDialog);
            }
        });
        this.shoppingHud = new ShoppingHud(commandManager, assets);
        this.battleHud = new BattleHud(commandManager, assets, new BattleHud.SpeedListener() {
            @Override
            public void onSpeedChanged(float factor) {
                speedFactor = factor;
            }
        });
        this.resultBanner = new ResultBanner(runFlowSystem, contextSupplier(), assets);
        this.runEndPanel = new RunEndPanel(assets, new RunEndPanel.RestartListener() {
            @Override
            public void onRestart() {
                restartRun();
            }
        }, contextSupplier());
        this.shopBar = new ShopBar(commandManager, assets, contextSupplier());
        this.inventoryPanel = new InventoryPanel(assets, contextSupplier(), equipPending);
        this.synergyPanel = new SynergyPanel(assets, contextSupplier());
        this.notificationPanel = new NotificationPanel(assets, contextSupplier(), commandManager);
        this.chestDialog = new ChestDialog(commandManager, assets, data);
        this.unitDetailDialog = new UnitDetailDialog(commandManager, assets, contextSupplier(),
                new UnitDetailDialog.CloseListener() {
                    @Override
                    public void onCloseRequested() {
                        closeDialog(unitDetailDialog);
                    }
                });
        this.pauseMenuDialog = new PauseMenuDialog(commandManager, assets, dialogManager);
        this.keyProcessor = new GlobalKeyProcessor(new GlobalKeyProcessor.Listener() {
            @Override
            public boolean onEscapeOrBack() {
                if (dialogManager.isShowing()) {
                    dialogManager.closeTop(); // input §3：有弹窗关顶层
                    return true;
                }
                dialogManager.push(pauseMenuDialog); // 无弹窗开暂停
                return true;
            }

            @Override
            public boolean onNotificationToggle() {
                notificationPanel.toggleLargeMode();
                return true;
            }
        });
        uiStage.addActor(topBar);
        uiStage.addActor(shoppingHud);
        uiStage.addActor(battleHud);
        uiStage.addActor(resultBanner);
        uiStage.addActor(runEndPanel);
        uiStage.addActor(shopBar);
        uiStage.addActor(inventoryPanel);
        uiStage.addActor(synergyPanel);
        uiStage.addActor(notificationPanel);
    }

    /** 上下文供应者（面板/横幅共用——值随 restartRun 换新） */
    private java.util.function.Supplier<RunContext> contextSupplier() {
        return new java.util.function.Supplier<RunContext>() {
            @Override
            public RunContext get() {
                return runContext;
            }
        };
    }

    // —— 生命周期 ——

    @Override
    public void show() {
        this.runContext = newContext(seed);
        runFlowSystem.registerHandlers(commandManager);   // 流程五命令（StartRun/StartBattle/Surrender/PickChest/AbandonRun）
        shopSystem.registerHandlers(commandManager);      // BuyUnit/RefreshShop/BuyExp
        rosterSystem.registerHandlers(commandManager);    // MoveUnit/SellUnit
        equipmentSystem.registerHandlers(commandManager); // EquipItem/UnequipItem
        commandManager.addCommand(new StartRunCommand(
                seed, runContext.getRunState().getSceneId(), null)); // 回放第 0 条记录（Q3 裁决）
        accumulator = 0f;
        renderClock = 0f;
        paused = false;
        speedFactor = 1f;
        battleHud.resetSpeed();
        this.boardProcessor = new BoardInputProcessor(worldViewport, commandManager,
                contextSupplier(),
                new java.util.function.BooleanSupplier() { // 模态阻断位（Phase 4 预留位兑现）
                    @Override
                    public boolean getAsBoolean() {
                        return dialogManager.isShowing();
                    }
                },
                new java.util.function.IntConsumer() { // 死区点击：装备待定落点 / 详情弹窗
                    @Override
                    public void accept(int unitId) {
                        if (equipPending.hasPending()) {
                            commandManager.addCommand(new EquipItemCommand(
                                    equipPending.pendingItemId(), unitId));
                            equipPending.clear();
                        } else {
                            unitDetailDialog.showUnit(unitId);
                            dialogManager.push(unitDetailDialog);
                        }
                    }
                });
        // 四层 multiplexer（input §2.2）：dialogStage > uiStage > boardProcessor > keyProcessor
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(dialogManager.getStage());
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(boardProcessor);
        multiplexer.addProcessor(keyProcessor);
        Gdx.input.setInputProcessor(multiplexer);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.08f, 1f); // 清屏（与菜单/装载屏同底色）：否则棋盘外区域无重绘，拖拽 ghost 与已隐藏 HUD 留余像
        boolean frozen = paused || dialogManager.isShowing(); // 模拟冻结（口径 #14：暂停或任一弹窗模态）
        if (!frozen) {
            stepSimulation(delta);
            renderClock += delta;
        }
        float alpha = frozen ? 0f : accumulator / GameBalance.LOGIC_STEP;
        worldViewport.apply();
        batch.setProjectionMatrix(worldCamera.combined);
        battleRenderer.draw(batch, runContext, alpha, renderClock, frozen ? 0f : delta, boardProcessor);
        GamePhase phase = runContext.getRunState().getPhase();
        topBar.refresh(runContext);
        shoppingHud.setVisible(phase == GamePhase.SHOPPING);
        battleHud.setVisible(phase == GamePhase.BATTLE);
        shopBar.setVisible(phase == GamePhase.SHOPPING);
        if (phase == GamePhase.SHOPPING) {
            shopBar.refresh(runContext);
        }
        inventoryPanel.refresh(); // ③⑤⑨ 全程可见（BATTLE 置灰在各自 draw 内，差异声明 #8）
        synergyPanel.refresh(runContext);
        notificationPanel.refresh(runContext);
        notificationPanel.syncBattle(runContext.getBattleState());
        if (phase == GamePhase.BATTLE) {
            battleHud.refresh(runContext.getBattleState());
        }
        resultBanner.setVisible(phase == GamePhase.RESULT);
        if (phase == GamePhase.RESULT && runContext.getBattleState() != null) {
            resultBanner.refresh(runContext.getBattleState().getOutcome(), mercyLine()); // 横幅读 outcome + 怜悯行（口径 #10）
        }
        syncChestDialog(phase);
        if (unitDetailDialog.getParent() != null) { // 在栈才检查（isExpired 对未打开态恒 true——CP25/T7 口径）
            unitDetailDialog.refresh(); // 详情弹窗每帧刷新（名单/装备可能经命令变化）
            if (unitDetailDialog.isExpired()) {
                closeDialog(unitDetailDialog); // 名单已无该单位（卖出/合并）→ 自动收起（CP25）
            }
        }
        runEndPanel.setVisible(phase == GamePhase.RUN_END);
        uiStage.act(delta);
        uiStage.getViewport().apply();
        uiStage.draw();
        dialogManager.act(delta);
        dialogManager.draw();
    }

    /** 败局怜悯提示行（刚发的怜悯金 → 横幅行；否则 null） */
    private String mercyLine() {
        RunState runState = runContext.getRunState();
        return runState.getMercyGoldThisRound() > 0 && runState.getMercyLossCount() >= GameBalance.MERCY_START_LOSS
                ? "怜悯 +1（连败 " + runState.getMercyLossCount() + "）" : null;
    }

    /** 胜局 RESULT：宝箱弹窗 push/pop（Screen 观察——领取后 pendingChest==null 自动收起） */
    private void syncChestDialog(GamePhase phase) {
        boolean shouldShow = phase == GamePhase.RESULT && runContext.getRunState().getPendingChest() != null;
        if (shouldShow && !chestShown) {
            chestDialog.refresh(runContext.getRunState().getPendingChest());
            dialogManager.push(chestDialog);
            chestShown = true;
        } else if (!shouldShow && chestShown) {
            closeDialog(chestDialog);
        }
    }

    private void closeDialog(Actor dialog) {
        dialogManager.closeTop();
        if (dialog == chestDialog) {
            chestShown = false;
        }
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
        dialogManager.resize(width, height); // 三 viewport 同参数（dialogStage）
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
        dialogManager.dispose();
        // Assets 归 Main 持有，不在此弃
    }

    /** 组装新鲜上下文（seed 来自 UI 域边界事件——Q3 裁决；首场景 MVP 仅森林） */
    private RunContext newContext(long runSeed) {
        String sceneId = data.getScenes().keySet().iterator().next(); // 首场景（种子仅森林）
        return new RunContext(new Player(GameBalance.START_GOLD),
                new RunState(runSeed, sceneId, new SequentialIdIssuer()),
                data, new RandomGenerator(runSeed), shopSystem);
    }

    /** RUN_END 重开：新 seed + 清弹窗/残留命令 + 复入 startRun（RunFlowSystem.restart 契约） */
    private void restartRun() {
        this.seed = System.nanoTime(); // UI 边界新 seed（口径 #12）
        this.runContext = newContext(seed);
        commandManager.discardPending();       // 跨局残留命令防泄漏（口径 #12）
        dialogManager.clearAll();
        chestShown = false;
        runFlowSystem.restart(runContext);
        accumulator = 0f;
        battleHud.resetSpeed();
    }
}
