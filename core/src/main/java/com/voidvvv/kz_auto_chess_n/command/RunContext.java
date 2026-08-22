package com.voidvvv.kz_auto_chess_n.command;

import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.util.Objects;

/**
 * 命令工具箱（input §6.1 六件套的 Phase 4 子集 + GameData，口径 #4）。
 *
 * <p>{@code battleState} 可空：仅 BATTLE/RESULT 期非空，回 SHOPPING 即置 null
 * （BattleState 双实体语义——战毕整体丢弃）。静态模板 {@code gameData} 只读安全，
 * StartBattle handler 解析技能/模板所需（差异声明 #8）。
 *
 * <p>Phase 5 预留字段位（本期仅注释声明，不建字段）：{@code ShopSystem shop} / {@code UnitRegistry registry}。
 */
public final class RunContext {
    private final Player player;
    private final RunState runState;
    private final GameData gameData;
    private final RandomGenerator rng;
    private BattleState battleState;

    public RunContext(Player player, RunState runState, GameData gameData, RandomGenerator rng) {
        this.player = Objects.requireNonNull(player, "player 不能为 null");
        this.runState = Objects.requireNonNull(runState, "runState 不能为 null");
        this.gameData = Objects.requireNonNull(gameData, "gameData 不能为 null");
        this.rng = Objects.requireNonNull(rng, "rng 不能为 null");
    }

    public Player getPlayer() { return player; }
    public RunState getRunState() { return runState; }
    public GameData getGameData() { return gameData; }
    public RandomGenerator getRng() { return rng; }

    /** 仅 BATTLE/RESULT 期非 null，其余期为 null */
    public BattleState getBattleState() { return battleState; }

    /** StartBattle handler / RunFlowSystem 调用（回 SHOPPING 时置 null） */
    public void setBattleState(BattleState battleState) {
        this.battleState = battleState;
    }
}
