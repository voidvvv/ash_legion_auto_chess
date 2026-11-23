package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.EquipItemCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.StartRunCommand;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.ChestOrigin;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.RunMode;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.input.BoardInputProcessor;
import com.voidvvv.kz_auto_chess_n.input.DrawerOutsideTouchProcessor;
import com.voidvvv.kz_auto_chess_n.input.GlobalKeyProcessor;
import com.voidvvv.kz_auto_chess_n.input.HoverCandidate;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.board.BattleRenderer;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.render.ui.BattleHud;
import com.voidvvv.kz_auto_chess_n.render.ui.BattleTransitionController;
import com.voidvvv.kz_auto_chess_n.render.ui.ChestDialog;
import com.voidvvv.kz_auto_chess_n.render.ui.DrawerController;
import com.voidvvv.kz_auto_chess_n.render.ui.DrawerGeometry;
import com.voidvvv.kz_auto_chess_n.render.ui.DrawerPanel;
import com.voidvvv.kz_auto_chess_n.render.ui.EquipPendingState;
import com.voidvvv.kz_auto_chess_n.render.ui.HoverPreviewCard;
import com.voidvvv.kz_auto_chess_n.render.ui.NotificationPanel;
import com.voidvvv.kz_auto_chess_n.render.ui.PauseMenuDialog;
import com.voidvvv.kz_auto_chess_n.render.ui.ResultBanner;
import com.voidvvv.kz_auto_chess_n.render.ui.RunEndPanel;
import com.voidvvv.kz_auto_chess_n.render.ui.ShopBar;
import com.voidvvv.kz_auto_chess_n.render.ui.ShoppingHud;
import com.voidvvv.kz_auto_chess_n.render.ui.SynergyPanel;
import com.voidvvv.kz_auto_chess_n.render.ui.TopBar;
import com.voidvvv.kz_auto_chess_n.render.ui.UIDialogManager;
import com.voidvvv.kz_auto_chess_n.render.ui.UnscaledLayer;
import com.voidvvv.kz_auto_chess_n.save.MetaService;
import com.voidvvv.kz_auto_chess_n.save.ProfileService;
import com.voidvvv.kz_auto_chess_n.save.RunSettlementText;
import com.voidvvv.kz_auto_chess_n.save.RunSnapshot;
import com.voidvvv.kz_auto_chess_n.save.SnapshotCodec;
import com.voidvvv.kz_auto_chess_n.systems.BattleSystem;
import com.voidvvv.kz_auto_chess_n.systems.EquipmentSystem;
import com.voidvvv.kz_auto_chess_n.systems.PaiTestScript;
import com.voidvvv.kz_auto_chess_n.systems.RosterSystem;
import com.voidvvv.kz_auto_chess_n.systems.RunFlowSystem;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;

/**
 * 战斗屏（render §三/§4.1；screens 是唯一装配点）。
 *
 * <p>帧循环（input §5.3 + 口径 #5/#7）：accumulator 累积（钳 MAX_DELTA、乘 speedFactor）
 * → 固定 LOGIC_STEP 步进：executeAll(runContext) → BATTLE 门控 step + isOver 观察-委托 →
 * RESULT 计时 → 超限丢弃。渲染段：棋盘域自绘 + UI 域 Stage（⑨），可见性按 phase 联动。
 * 阶段推进全部委托 RunFlowSystem——本类只做点火器/观察者（architecture §七）。
 *
 * <p>Phase 5 装配（CP29）：四 system 注册 handler；五层输入 multiplexer
 * （dialogStage &gt; drawerOutsideTouch &gt; uiStage &gt; boardProcessor &gt; keyProcessor，input §2.2）；
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
    /** 开战转场驱动器（battle §二 / render §5.6；裁决 A）：UI 姿态 + worldCamera zoom + 输入封禁位 */
    private final BattleTransitionController transitionController;
    /** 全屏透明收点（仅转场窗口 touchable）：吞 UI 点击——render §5.6 转场期输入禁用的 UI 域封禁面（K6） */
    private final Actor inputCatcher;
    private final RunEndPanel runEndPanel;
    private final ShopBar shopBar;
    private final SynergyPanel synergyPanel;
    private final NotificationPanel notificationPanel;
    private final HoverPreviewCard hoverPreview; // Phase 5.1 R1：悬停预览卡（uiStage 最上层瞬态）
    private final ChestDialog chestDialog;
    private final PauseMenuDialog pauseMenuDialog;
    private BoardInputProcessor boardProcessor;
    /** UI 抽屉（UI 抽屉化：备战席 / 背包 / 棋子详情的承载面，②③ 与详情弹窗已退役——分区分工见 {@link DrawerPanel}）：
     *  不缩放层（Q8）+ 面板 + 开合状态机（Q2 默认收起） */
    private final UnscaledLayer unscaledLayer;
    private final DrawerPanel drawerPanel;
    private final DrawerController drawerController = new DrawerController();
    /** 「点抽屉外收起」透传观察处理器（Q2；multiplexer 位置 = dialogStage 之后、uiStage 之前） */
    private final DrawerOutsideTouchProcessor drawerOutsideTouch;

    /** 本局 seed（UI 域边界给定——RunSetup 传入；RESTART 换新（对拍同种子重开——§0.2），Q3 裁决口径） */
    private long seed;
    /** 运行模式（品质改造 §0.1/§0.2：不可变模式参数，构造期冻结——测试档/对拍口径的分支唯一判定源） */
    private final RunMode mode;
    /** 对拍题号（品质改造 §0.2：仅对拍局非 null——上下文与重开同源） */
    private final PaiTestScript.Topic sampleTopic;
    /** 本局场景/英雄（RunSetup 选定；RESTART 沿用） */
    private final String selectedSceneId;
    private final String selectedHeroId;
    /** 档案域门面（Phase 6：结算观察 + 快照触发 + 局外修正装配） */
    private final MetaService metaService;
    /** 续玩快照（null = 新局；非 null 时 show() 走恢复路径，不发 StartRun） */
    private final RunSnapshot resumedSnapshot;
    /** resumedSnapshot != null 的派生缓存（恢复失败回退路径可置 false 保证 StartRun 仍发） */
    private boolean resumed;
    /** RUN_END 结算观察旗标（每局一次——裁决 D11） */
    private boolean runEndSettled;
    /** 本轮快照已写标记（进入 SHOPPING 首帧写、离开 SHOPPING 复位——裁决 D10） */
    private boolean snapshotCurrent;
    /** 宝箱弹窗在栈标记（防重复 push；领取/离开 RESULT 后收起，CP29） */
    private boolean chestShown;

    private float accumulator;
    private float renderClock;
    private boolean paused;
    /** ×1/×2 变速（口径 #5：只乘 accumulator 消费速率，不进模拟路径） */
    private float speedFactor = 1f;

    /** 新局（RunSetup 域边界事件三参 + 档案门面）——正式模式（品质改造 §0.1 既有构造器默认正式） */
    public BattleScreen(Game game, Assets assets, GameData data, MetaService metaService,
                        long seed, String sceneId, String heroId) {
        this(game, assets, data, metaService, seed, sceneId, heroId, null, RunMode.NORMAL, null);
    }

    /** 续玩（主菜单「继续远征」：快照在 show() 复原，跳过 StartRun）——恒正式模式（品质改造 §0.1：
     *  正式「继续远征」按快照原值恢复，档案轨全开；历史 999 金局无标志，续玩保留存量余额） */
    public BattleScreen(Game game, Assets assets, GameData data, MetaService metaService,
                        RunSnapshot snapshot) {
        this(game, assets, data, metaService, snapshot.getSeed(), snapshot.getSceneId(),
                snapshot.getHeroId(), snapshot, RunMode.NORMAL, null);
    }

    /**
     * 测试档局装配（品质改造 §0.1）：启动参数 {@code --test-gold=999}（或无值形式）指定的
     * 首屏测试局——固定森林、无英雄、999 金、存档隔离。seed 取装配时刻时钟（沿 RunSetup 口径）。
     * 唯一非常规模式入口之一：模式不可变，RESTART 沿用（测试档重开仍在测试档）。
     */
    public static BattleScreen newTestPeriodRun(Game game, Assets assets, GameData data,
                                                MetaService metaService) {
        return new BattleScreen(game, assets, data, metaService, System.nanoTime(),
                RunAssembler.FIXED_SCENE_ID, null, null, RunMode.TEST_PERIOD_999, null);
    }

    /**
     * 对拍局装配（品质改造 §0.2）：主菜单「测试样片」选题与种子后进入的真实 BattleScreen——
     * 固定森林、无英雄、10 金、空名册、固定敌阵/商店（{@code PaiTestScript}）、存档隔离。
     * 终局「按同种子重开」以原题原种子重建整个上下文（restartRun）。
     */
    public static BattleScreen newSampleDuelRun(Game game, Assets assets, GameData data,
                                                MetaService metaService,
                                                PaiTestScript.Topic topic, long seed) {
        return new BattleScreen(game, assets, data, metaService, seed,
                RunAssembler.FIXED_SCENE_ID, null, null, RunMode.SAMPLE_DUEL, topic);
    }

    private BattleScreen(Game game, Assets assets, GameData data, MetaService metaService,
                         long seed, String sceneId, String heroId, RunSnapshot resumedSnapshot,
                         RunMode mode, PaiTestScript.Topic sampleTopic) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.metaService = metaService;
        this.mode = java.util.Objects.requireNonNull(mode, "mode 不能为 null");
        this.sampleTopic = sampleTopic;
        this.seed = seed;
        this.selectedSceneId = sceneId;
        this.selectedHeroId = heroId;
        this.resumedSnapshot = resumedSnapshot;
        this.resumed = resumedSnapshot != null;
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
        }, new RunEndPanel.MenuListener() {
            @Override
            public void onMenuRequested() {
                game.setScreen(new MainMenuScreen(game, assets, data, metaService));
            }
        }, contextSupplier(), mode);
        this.shopBar = new ShopBar(commandManager, assets, contextSupplier());
        this.synergyPanel = new SynergyPanel(assets, contextSupplier());
        this.notificationPanel = new NotificationPanel(assets, contextSupplier(), commandManager);
        this.hoverPreview = new HoverPreviewCard(assets, contextSupplier());
        // 开战转场装配（battle §二 / render §5.6）：收点先于控制器构造（控制器持有其引用）
        this.inputCatcher = new Actor(); // 无绘制；空 ClickListener 即消费点击（ResultBanner.ClickCatcher 先例）
        this.inputCatcher.setSize(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H);
        this.inputCatcher.addListener(new ClickListener());
        // UI 抽屉装配（批1 骨架 / 批2 备战席迁入）：面板挂不缩放层（Q8）；其主体接替 ③ 参数位随转场淡出，
        // 故面板先于转场控制器构造。开/合请求一律经控制器，转场封禁位在回调处合流（§3.8）
        this.unscaledLayer = new UnscaledLayer(GameBalance.RENDER_GLOBAL_SCALE);
        this.drawerPanel = new DrawerPanel(assets, contextSupplier(), uiStage.getViewport(), commandManager,
                equipPending, new DrawerPanel.Listener() {
            @Override
            public void onToggleRequested() {
                drawerController.requestToggle(transitionController.isInputBlocked());
            }

            @Override
            public void onCloseRequested() {
                drawerController.requestClose(transitionController.isInputBlocked());
            }
        });
        this.unscaledLayer.addActor(drawerPanel);
        // 退场距离 = 抽屉滑动距离（246 ≥ 抽屉宽 244；③ 时代写死 140 会滑不干净）
        this.transitionController = new BattleTransitionController(
                worldCamera, shopBar, shoppingHud, battleHud, drawerPanel.bodyActor(), inputCatcher,
                DrawerGeometry.SLIDE_PX);
        this.chestDialog = new ChestDialog(commandManager, assets, data);
        this.pauseMenuDialog = new PauseMenuDialog(commandManager, assets, dialogManager);
        this.drawerOutsideTouch = new DrawerOutsideTouchProcessor(uiStage.getViewport(),
                new DrawerOutsideTouchProcessor.Host() {
                    @Override
                    public boolean isDrawerOpen() {
                        return drawerController.isOpen();
                    }

                    @Override
                    public boolean isDrawerTouchBlocked() {
                        return dialogManager.isShowing() || transitionController.isInputBlocked();
                    }

                    @Override
                    public boolean isInsideDrawer(float stageX, float stageY) {
                        return DrawerGeometry.isInside(stageX, stageY, drawerController.progress());
                    }

                    @Override
                    public boolean isOutsideClickSuppressed() {
                        // 批4·主人裁决：装备待定态期间「点抽屉外」不收起（两段式穿戴中途不被打断）。
                        // 显式收起手段（把手 / 关闭钮 / Esc）直连 DrawerController，不经本判定——待定态下照常可收起；
                        // 待定态的退出路径（穿上 / 再点同格或空格取消 / 物品失配自清）均在抽屉内或命令层，不受本位影响。
                        return equipPending.hasPending();
                    }

                    @Override
                    public void onOutsideClick() {
                        drawerController.requestClose(transitionController.isInputBlocked());
                    }
                });
        this.keyProcessor = new GlobalKeyProcessor(new GlobalKeyProcessor.Listener() {
            @Override
            public boolean onEscapeOrBack() {
                if (dialogManager.isShowing()) {
                    dialogManager.closeTop(); // input §3：有弹窗关顶层
                    return true;
                }
                if (drawerController.isOpen()) { // Q2：抽屉开着时 Esc 先收抽屉——本分支恒 return、不开暂停（返回值 = 收起请求是否受理）；封禁位拒绝仅防御性：转场窗口始于相位跳变，抽屉同帧已被强制收起、封禁期内也开不了，实际走不到
                    return drawerController.requestClose(transitionController.isInputBlocked());
                }
                dialogManager.push(pauseMenuDialog); // 无弹窗且抽屉未开 → 开暂停
                return true;
            }

            @Override
            public boolean onNotificationToggle() {
                notificationPanel.toggleLargeMode();
                return true;
            }
        });
        // 绘制/命中层级（自下而上，见 uiLayerOrder）：③ InventoryPanel 已退役（批3）；抽屉层按主人裁决
        // （批4·⑨ 层级 A 案）移到 ⑤⑨ 之上——展开期间通知小窗（含 L 键大窗左半）被整块盖住，属已接受代价
        for (Actor actor : uiLayerOrder(topBar, shoppingHud, battleHud, resultBanner, runEndPanel, shopBar,
                synergyPanel, notificationPanel, unscaledLayer, hoverPreview, inputCatcher)) {
            uiStage.addActor(actor);
        }
        applyGlobalUiScale(uiStage); // UI 整体缩小（2026-11-21 主人反馈）：与世界域 zoom 同系数同中心
        applyGlobalUiScale(dialogManager.getStage()); // 弹窗域（chest/暂停）同缩放
    }

    /** UI 域整体缩放（2026-11-21 主人反馈「所有 UI 偏大」→ 整体调小一档）：根节点统一缩放居中，
     *  与 {@link BattleTransitionController} 的 worldCamera.zoom 基准（RENDER_GLOBAL_SCALE）同系数——
     *  世界域（棋盘，zoom 以屏心收缩）与 UI 域（根缩放居中）足迹恒重合，相邻不错位；
     *  Scene2d 命中测试穿过根变换（parentToLocalCoords），指针交互自动正确。恢复 = 常量回 1.0f。 */
    private static void applyGlobalUiScale(com.badlogic.gdx.scenes.scene2d.Stage stage) {
        float scale = GameBalance.RENDER_GLOBAL_SCALE;
        stage.getRoot().setScale(scale);
        stage.getRoot().setPosition(
                BoardGeometry.VIRTUAL_W * (1f - scale) / 2f,
                BoardGeometry.VIRTUAL_H * (1f - scale) / 2f);
    }

    /**
     * uiStage 根下的 Actor 添加序 = 绘制序 = 命中优先级（后加者在上；headless 可测——装配唯一入口）。
     * 品质改造 §1.5b 自下而上：常驻 HUD（顶栏 ① / ⑥ / 战斗 HUD / ⑧ 商店栏）→ ⑤ 羁绊 → ⑨ 通知 →
     * <b>抽屉层</b> → 悬停卡（瞬态）→ <b>结果遮罩与终局</b> → 转场收点（模态弹窗在 dialogStage，恒最上）。
     * 抽屉展开 / 结果终局 / 弹窗期通知只收集不画（drawSuppressed）——层序与遮挡规则解耦。
     */
    static Actor[] uiLayerOrder(Actor topBar, Actor shoppingHud, Actor battleHud, Actor resultBanner,
                                Actor runEndPanel, Actor shopBar, Actor synergyPanel, Actor notificationPanel,
                                Actor drawerLayer, Actor hoverPreview, Actor inputCatcher) {
        return new Actor[]{topBar, shoppingHud, battleHud, shopBar, synergyPanel, notificationPanel,
                drawerLayer, hoverPreview, resultBanner, runEndPanel, inputCatcher};
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
        if (resumedSnapshot != null && resumed) {
            try {
                this.runContext = SnapshotCodec.restore(resumedSnapshot, data,
                        metaService.getProfile(), shopSystem);
            } catch (RuntimeException ex) {
                System.err.println("[BattleScreen] 快照恢复失败，回退新局: " + ex.getMessage());
                this.resumed = false; // 回退路径仍发 StartRun（正常已被 Store 删档前置拦截）
                this.runContext = newContext(seed);
            }
        } else {
            this.runContext = newContext(seed);
        }
        runFlowSystem.registerHandlers(commandManager);   // 流程五命令（StartRun/StartBattle/Surrender/PickChest/AbandonRun）
        shopSystem.registerHandlers(commandManager);      // BuyUnit/RefreshShop/BuyExp
        rosterSystem.registerHandlers(commandManager);    // MoveUnit/SellUnit
        equipmentSystem.registerHandlers(commandManager); // EquipItem/UnequipItem
        if (!resumed && runContext.getRunState().getRound() == 1
                && !runContext.getRunState().isRunStarted()) {
            commandManager.addCommand(new StartRunCommand(
                    seed, runContext.getRunState().getSceneId(), selectedHeroId)); // 回放第 0 条记录（heroId 启用）
        }
        accumulator = 0f;
        renderClock = 0f;
        paused = false;
        speedFactor = 1f;
        battleHud.resetSpeed();
        this.boardProcessor = new BoardInputProcessor(worldViewport, commandManager,
                contextSupplier(),
                new java.util.function.BooleanSupplier() { // 模态/转场阻断位（input §3）：弹窗或转场窗口期吞棋盘输入
                    @Override
                    public boolean getAsBoolean() {
                        return dialogManager.isShowing() || transitionController.isInputBlocked();
                    }
                },
                new java.util.function.IntConsumer() { // 死区点击：装备待定落点 / 抽屉选中
                    @Override
                    public void accept(int unitId) {
                        if (equipPending.hasPending()) {
                            commandManager.addCommand(new EquipItemCommand(
                                    equipPending.pendingItemId(), unitId));
                            equipPending.clear();
                            drawerPanel.select(unitId); // 批3：穿上即选中——「装备」标签页随即显示该棋子三件（用户刚点了它，合「点选谁就显示谁」）
                        } else { // Q5（UI 抽屉化批2）：点棋子 = 抽屉选中并展开——详情弹窗由抽屉「介绍」页取代
                            drawerPanel.select(unitId);
                            drawerController.requestOpen(transitionController.isInputBlocked());
                        }
                    }
                }, drawerPanel); // 备战席命中源（UI 抽屉化批2：抽屉头像条替代 ② 世界域矩形；滚轮路由同此）
        // 五层 multiplexer（input §2.2）：dialogStage > drawerOutsideTouch > uiStage > boardProcessor > keyProcessor；
        // 抽屉外点击观察者夹在 dialogStage 与 uiStage 之间——永不吃事件（透传），只多「看见」一遍
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(dialogManager.getStage());
        multiplexer.addProcessor(drawerOutsideTouch);
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(boardProcessor);
        multiplexer.addProcessor(keyProcessor);
        Gdx.input.setInputProcessor(multiplexer);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.08f, 1f); // 清屏（与菜单/装载屏同底色）：否则棋盘外区域无重绘，拖拽 ghost 与已隐藏 HUD 留余像
        boolean frozen = paused || dialogManager.isShowing(); // 模拟冻结（口径 #14：暂停或任一弹窗模态）
        GamePhase phase = runContext.getRunState().getPhase(); // 前提：转场驱动器与绘制同帧同相位
        // 开战转场驱动（battle §二 / render §5.6）：先于 worldViewport.apply() 写 zoom；dt 冻结感知——反向自计时随暂停停走
        transitionController.update(phase,
                phase == GamePhase.BATTLE && runContext.getBattleState() != null
                        && runContext.getBattleState().isIntroCountdownActive()
                        ? runContext.getBattleState().getIntroRemaining() : 0f,
                frozen ? 0f : delta);
        // UI 抽屉：相位跳变强制收起（Q6 开战随转场退场）+ 从抽屉往外拖棋子时让位（Q2：强制收起、松手后保持收起）
        // → 冻结感知推进（dt 传 0 不走）；落位在 applyUiPose 之后（见下）
        drawerController.syncPhase(phase);
        boolean dragFromBench = boardProcessor != null && boardProcessor.isDragFromBench();
        if (dragFromBench) {
            drawerController.collapse();
        }
        drawerController.update(frozen ? 0f : delta);
        if (!frozen) {
            stepSimulation(delta);
            renderClock += delta;
        } else {
            commandManager.executeAll(runContext); // 冻结的只是模拟推进（战斗步进/RESULT 计时/动画），命令照常结算——弹窗按钮链路（PickChest/AbandonRun/UnequipItem）否则死锁（feedback02 修复）
        }
        float alpha = frozen ? 0f : accumulator / GameBalance.LOGIC_STEP;
        worldViewport.apply();
        batch.setProjectionMatrix(worldCamera.combined);
        // 引导文案让位（批4）：抽屉展开时按主体当前舞台右缘右移；worldScale = 1 / zoom（备战期 0.8/1.06）
        battleRenderer.draw(batch, runContext, alpha, renderClock, frozen ? 0f : delta, boardProcessor,
                transitionController.chromeFadeAlpha(), 1f / worldCamera.zoom,
                DrawerGeometry.drawerRect(drawerController.progress()).right());
        if (phase == GamePhase.RUN_END && !runEndSettled) { // 档案结算每局一次（裁决 D11）
            runEndSettled = true;
            if (archiveWritesAllowed(mode)) {
                ProfileService.Settlement settlement = metaService.settleRun(data, runContext);
                runEndPanel.setSettlementLines(RunSettlementText.lines(settlement, data));
                metaService.clearRunSnapshot(); // RUN_END 自动删档（快照轨终点）
            }
            // 测试档旁路（品质改造 §0.1 存档隔离）：不结算档案、不删档——含正式快照的档案原样保留；
            // 终局熟练度行由 RunEndPanel 按模式隐藏
        }
        if (phase != GamePhase.SHOPPING) { // 快照轨：进入备战首帧写、离开复位（裁决 D10）
            snapshotCurrent = false;
        } else if (archiveWritesAllowed(mode) && runContext.getRunState().isRunStarted()
                && !snapshotCurrent) {
            metaService.saveRunSnapshot(runContext);
            snapshotCurrent = true;
        }
        topBar.refresh(runContext);
        shoppingHud.setVisible(phase == GamePhase.SHOPPING);
        battleHud.setVisible(phase == GamePhase.BATTLE);
        shopBar.setVisible(phase == GamePhase.SHOPPING);
        if (phase == GamePhase.SHOPPING) {
            shopBar.refresh(runContext);
        }
        transitionController.applyUiPose(); // 转场姿态覆写（⑥⑧HUD + 抽屉主体 + Catcher；稳态归零不越权——K7）
        // 抽屉落位须在转场姿态之后：applyUiPose 先写淡出透明度/位移，此处按开合进度定最终位姿与可见性；
        // 让位淡到三成、拖拽落点高亮头像格、绑定备战席（只读）
        drawerPanel.setDimmed(dragFromBench);
        drawerPanel.setDropTarget(boardProcessor != null && boardProcessor.isDragging()
                ? boardProcessor.getDropPreview() : null);
        drawerPanel.refresh(boardProcessor != null && boardProcessor.isDragging()); // 拖拽中抑制装备格悬停预览（口径 B4-2）
        drawerPanel.apply(drawerController.progress());
        synergyPanel.refresh(runContext);
        notificationPanel.syncBattle(runContext.getBattleState()); // §1.5b：先同步战斗引用再刷事件（附着守卫）
        notificationPanel.refresh(runContext);
        hoverPreview.refresh(drawerController.progress() > 0f ? HoverCandidate.NONE // Q9：抽屉展开时压制备战期棋盘悬停卡
                        : boardProcessor == null ? HoverCandidate.NONE : boardProcessor.getHoverCandidate(),
                shopBar.getHoveredSlot(),
                frozen, frozen ? 0f : delta); // R1+feedback04：候选/槽位/冻结位（§5.3-8；背包源随 ③ 退役——批3）
        if (phase == GamePhase.BATTLE) {
            battleHud.refresh(runContext.getBattleState());
        }
        // §1.5b 通知绘制闸：隐藏期只收集不画；展开通知与可见悬停卡重叠 → 仅暂停绘制（移开恢复，L 键状态保留）。
        // 相位以步进后重读值为准（§1.5b）——stepSimulation 本帧可能已推进相位（如开战转场）
        notificationPanel.setDrawSuppressed(NotificationPanel.suppressedByLayout(
                drawerController.progress() > 0f,
                NotificationPanel.isResultOrEndPhase(runContext.getRunState().getPhase()),
                dialogManager.isShowing())
                || NotificationPanel.overlapsVisibleHoverCard(notificationPanel.isLargeMode(),
                        hoverPreview.hasVisibleCard() ? hoverPreview.visibleCardRect() : null));
        resultBanner.setVisible(phase == GamePhase.RESULT);
        if (phase == GamePhase.RESULT && runContext.getBattleState() != null) {
            resultBanner.refresh(runContext.getBattleState().getOutcome(),
                    resultStatusLine(runContext.getRunState())); // 横幅读 outcome + 机会制状态行（GDD §2.2）
        }
        syncChestDialog(phase);
        runEndPanel.setVisible(phase == GamePhase.RUN_END);
        uiStage.act(delta);
        uiStage.getViewport().apply();
        uiStage.draw();
        dialogManager.act(delta);
        dialogManager.draw();
    }

    /**
     * RESULT 期横幅状态行（机会制，GDD §2.2；工作值待调）：
     * 胜局 null（hint 由 ResultBanner 内置）；败箱期「选择战败补给」；
     * 败 1~2 无箱「点击任意处重试 · 剩余机会 N」；败 3「机会耗尽 · 远征失败」。
     */
    static String resultStatusLine(RunState runState) {
        if (runState.getPendingChest() != null) {
            return runState.getPendingChest().getOrigin() == ChestOrigin.DEFEAT
                    ? "选择战败补给" : null; // 胜局 pendingChest → Banner 内置行
        }
        int left = GameBalance.DEFEAT_LIMIT_PER_ROUND - runState.getDefeatCountPerRound();
        if (left <= 0) {
            return "机会耗尽 · 远征失败"; // 第 3 败：点击/3s 后终局（continueAfterDefeat 路由）
        }
        return "点击任意处重试 · 剩余机会 " + left; // N = 2/1（工作值待调）
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
        saveSnapshotIfShopping(); // 快照补写（仅备战期——存档点决策）
    }

    @Override
    public void resume() {
        paused = false;
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null); // 防僵尸监听（input §2.3）
        saveSnapshotIfShopping();
    }

    /** 快照补写（pause/hide；战斗/结算期不写——存档点仅备战）；测试档旁路（品质改造 §0.1 存档隔离） */
    private void saveSnapshotIfShopping() {
        if (!archiveWritesAllowed(mode)) {
            return; // 测试局不补写：暂停/离屏不动正式存档
        }
        if (runContext != null && runContext.getRunState().isRunStarted()
                && runContext.getRunState().getPhase() == GamePhase.SHOPPING) {
            metaService.saveRunSnapshot(runContext);
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        uiStage.dispose();
        dialogManager.dispose();
        // Assets 归 Main 持有，不在此弃
    }

    /**
     * 档案域写入口闸（品质改造 §0.1/§0.2 存档隔离）：测试档与对拍局一律旁路——首次备战写快照、
     * 暂停/离屏补写、终局删档与档案结算全部跳过。含正式快照的档案进出两类测试局前后字节一致
     * （纯 JVM 验收件）；正式「继续远征」仍按快照原值恢复（本闸不拦读路径）。
     */
    static boolean archiveWritesAllowed(RunMode mode) {
        return !mode.closesArchiveTrack();
    }

    /** 组装新鲜上下文（口径归 {@link RunAssembler} 装配类；正式局起始金含英雄加成——裁决 D2） */
    private RunContext newContext(long runSeed) {
        if (mode.isSampleDuel()) {
            return RunAssembler.assembleSampleDuel(data, shopSystem, runSeed, sampleTopic);
        }
        if (mode.isTestPeriod()) {
            return RunAssembler.assembleTestPeriod(data, shopSystem, runSeed);
        }
        return RunAssembler.assembleNormal(data, shopSystem, runSeed, selectedSceneId, selectedHeroId,
                metaService.resolveRunModifiers(selectedHeroId, data));
    }

    /** RUN_END 重开：同英雄同场景新 seed + 清弹窗/残留命令/结算旗标 + 复入 startRun；
     *  对拍局（品质改造 §0.2）「按同种子重开」——原题原种子重建整个上下文。 */
    private void restartRun() {
        if (!mode.isSampleDuel()) {
            this.seed = System.nanoTime(); // UI 边界新 seed（口径 #12）
        }
        this.runContext = newContext(seed);
        commandManager.discardPending();       // 跨局残留命令防泄漏（口径 #12）
        dialogManager.clearAll();
        chestShown = false;
        runEndSettled = false; // 新局重新观察结算（裁决 D11）
        snapshotCurrent = false;
        runFlowSystem.restart(runContext);
        accumulator = 0f;
        battleHud.resetSpeed();
    }
}
