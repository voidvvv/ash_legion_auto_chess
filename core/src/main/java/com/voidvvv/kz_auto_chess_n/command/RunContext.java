package com.voidvvv.kz_auto_chess_n.command;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunMode;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.systems.PaiTestScript;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.util.Objects;

/**
 * 命令工具箱（input §6.1 六件套的 Phase 4 子集 + GameData，口径 #4）。
 *
 * <p>{@code battleState} 可空：仅 BATTLE/RESULT 期非空，回 SHOPPING 即置 null
 * （BattleState 双实体语义——战毕整体丢弃）。静态模板 {@code gameData} 只读安全，
 * StartBattle handler 解析技能/模板所需（差异声明 #8）。
 *
 * <p>Phase 5：{@code ShopSystem shop} 已落地（Phase 4 预留字段位兑现——经济态随
 * 上下文生命周期重建，重开即新商店）；{@code UnitRegistry} 继续推迟——GDD 无全局池/
 * 池耗尽机制可承载（见实施文档 §8 开放问题-2）。
 *
 * <p>品质改造 §0.1/§0.2：{@code RunMode} 以不可变模式参数随上下文冻结（既有构造器默认
 * {@link RunMode#NORMAL} 正式模式）；测试档与对拍局另带顶栏标题投影（{@link #getHudTitle()}）
 * 与对拍题号（{@link #getSampleTopic()}）——正式局两者恒 null。
 */
public final class RunContext {
    /** 测试档顶栏标题（品质改造 §0.1——原 TopBar 模式前缀的载体化落点） */
    private static final String TEST_PERIOD_TITLE = "测试档 999";

    private final Player player;
    private final RunState runState;
    private final GameData gameData;
    private final RandomGenerator rng;
    private final ShopSystem shop;
    private final RunMode mode;
    private final PaiTestScript.Topic sampleTopic;
    private BattleState battleState;

    /** 兼容构造（存量测试）：自建默认商店（槽位全空），正式模式（品质改造 §0.1 既有构造器默认正式） */
    public RunContext(Player player, RunState runState, GameData gameData, RandomGenerator rng) {
        this(player, runState, gameData, rng, new ShopSystem(), RunMode.NORMAL);
    }

    /** 兼容构造（存量调用方）：注入商店 + 正式模式 */
    public RunContext(Player player, RunState runState, GameData gameData, RandomGenerator rng,
                      ShopSystem shop) {
        this(player, runState, gameData, rng, shop, RunMode.NORMAL);
    }

    /** 生产路径（品质改造 §0.1）：装配点注入商店与运行模式（均随上下文生命周期重建/冻结） */
    public RunContext(Player player, RunState runState, GameData gameData, RandomGenerator rng,
                      ShopSystem shop, RunMode mode) {
        this(player, runState, gameData, rng, shop, mode, null);
    }

    /**
     * 全参生产路径（品质改造 §0.1/§0.2）：{@code sampleTopic} 仅对拍局非 null（其余模式传 null）。
     */
    public RunContext(Player player, RunState runState, GameData gameData, RandomGenerator rng,
                      ShopSystem shop, RunMode mode, PaiTestScript.Topic sampleTopic) {
        this.player = Objects.requireNonNull(player, "player 不能为 null");
        this.runState = Objects.requireNonNull(runState, "runState 不能为 null");
        this.gameData = Objects.requireNonNull(gameData, "gameData 不能为 null");
        this.rng = Objects.requireNonNull(rng, "rng 不能为 null");
        this.shop = Objects.requireNonNull(shop, "shop 不能为 null");
        this.mode = Objects.requireNonNull(mode, "mode 不能为 null");
        if (mode.isSampleDuel()) {
            Objects.requireNonNull(sampleTopic, "对拍局缺少题号"); // fail-fast：装配错误不在局内静默
        }
        this.sampleTopic = sampleTopic;
    }

    public Player getPlayer() { return player; }
    public RunState getRunState() { return runState; }
    public GameData getGameData() { return gameData; }
    public RandomGenerator getRng() { return rng; }
    public ShopSystem getShop() { return shop; }
    public RunMode getMode() { return mode; }

    /** 对拍题号（品质改造 §0.2）：仅对拍局非 null——RunFlowSystem.beginRound 按题取固定敌阵 */
    public PaiTestScript.Topic getSampleTopic() { return sampleTopic; }

    /**
     * 顶栏标题（品质改造 §0.1/§0.2）：测试档「测试档 999」/ 对拍「对拍 A · 42」/ 正式 null。
     * 装配期口径单点（派生自 mode + 题号 + seed），TopBar 只做展示。
     */
    public String getHudTitle() {
        if (mode.isTestPeriod()) {
            return TEST_PERIOD_TITLE;
        }
        if (mode.isSampleDuel()) {
            return PaiTestScript.hudTitle(sampleTopic, runState.getSeed());
        }
        return null;
    }

    /** 本局轮次上限（品质改造 §0.2）：对拍 3 轮，其余 25——TopBar 与 RunEndPanel 同源 */
    public int totalRounds() {
        return mode.isSampleDuel() ? PaiTestScript.TOTAL_ROUNDS : GameBalance.TOTAL_ROUNDS;
    }

    /** 仅 BATTLE/RESULT 期非 null，其余期为 null */
    public BattleState getBattleState() { return battleState; }

    /** StartBattle handler / RunFlowSystem 调用（回 SHOPPING 时置 null） */
    public void setBattleState(BattleState battleState) {
        this.battleState = battleState;
    }
}
