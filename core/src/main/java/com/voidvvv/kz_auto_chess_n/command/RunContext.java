package com.voidvvv.kz_auto_chess_n.command;

import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
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
 */
public final class RunContext {
    private final Player player;
    private final RunState runState;
    private final GameData gameData;
    private final RandomGenerator rng;
    private final ShopSystem shop;
    private BattleState battleState;

    /** 兼容构造（存量测试）：自建默认商店（槽位全空） */
    public RunContext(Player player, RunState runState, GameData gameData, RandomGenerator rng) {
        this(player, runState, gameData, rng, new ShopSystem());
    }

    /** 生产路径：装配点注入随上下文生命周期重建的商店 */
    public RunContext(Player player, RunState runState, GameData gameData, RandomGenerator rng,
                      ShopSystem shop) {
        this.player = Objects.requireNonNull(player, "player 不能为 null");
        this.runState = Objects.requireNonNull(runState, "runState 不能为 null");
        this.gameData = Objects.requireNonNull(gameData, "gameData 不能为 null");
        this.rng = Objects.requireNonNull(rng, "rng 不能为 null");
        this.shop = Objects.requireNonNull(shop, "shop 不能为 null");
    }

    public Player getPlayer() { return player; }
    public RunState getRunState() { return runState; }
    public GameData getGameData() { return gameData; }
    public RandomGenerator getRng() { return rng; }
    public ShopSystem getShop() { return shop; }

    /** 仅 BATTLE/RESULT 期非 null，其余期为 null */
    public BattleState getBattleState() { return battleState; }

    /** StartBattle handler / RunFlowSystem 调用（回 SHOPPING 时置 null） */
    public void setBattleState(BattleState battleState) {
        this.battleState = battleState;
    }
}
