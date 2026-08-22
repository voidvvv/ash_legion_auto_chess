package com.voidvvv.kz_auto_chess_n.command;

import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RunContext（命令工具箱，Q1 减配 + GameData 口径 #4）测试：构造字段只读与 battleState 可空语义。
 */
class RunContextTest {

    private RunContext newContext() {
        return new RunContext(new Player(10),
                new RunState(42L, "scene_forest", new SequentialIdIssuer()),
                BattleTestFixtures.microData(BattleTestFixtures.meleeSkill()),
                new RandomGenerator(42L));
    }

    @Test
    @DisplayName("构造字段只读可查（player/runState/gameData/rng 均同实例）")
    void ctorFieldsReadOnly() {
        Player player = new Player(10);
        RunState runState = new RunState(42L, "scene_forest", new SequentialIdIssuer());
        GameData data = BattleTestFixtures.microData(BattleTestFixtures.meleeSkill());
        RandomGenerator rng = new RandomGenerator(42L);
        RunContext ctx = new RunContext(player, runState, data, rng);
        assertThat(ctx.getPlayer()).isSameAs(player);
        assertThat(ctx.getRunState()).isSameAs(runState);
        assertThat(ctx.getGameData()).isSameAs(data);
        assertThat(ctx.getRng()).isSameAs(rng);
    }

    @Test
    @DisplayName("battleState 初始为 null（仅 BATTLE/RESULT 期非空，回 SHOPPING 即弃）")
    void battleStateStartsNull() {
        assertThat(newContext().getBattleState()).isNull();
    }

    @Test
    @DisplayName("setBattleState 后可查同实例（StartBattle handler / RunFlowSystem 调用）")
    void battleStateSettable() {
        RunContext ctx = newContext();
        BattleState state = BattleTestFixtures.state();
        ctx.setBattleState(state);
        assertThat(ctx.getBattleState()).isSameAs(state);
        ctx.setBattleState(null); // 回 SHOPPING 时置 null
        assertThat(ctx.getBattleState()).isNull();
    }
}
