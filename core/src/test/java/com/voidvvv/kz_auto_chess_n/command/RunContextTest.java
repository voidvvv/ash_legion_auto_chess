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

    // —— CP14：shop 字段（经济态随上下文生命周期重建——重开即新商店） ——

    @Test
    @DisplayName("4 参兼容构造：自建默认商店非空且 5 槽全空")
    void compatCtorBuildsEmptyShop() {
        RunContext ctx = newContext();
        assertThat(ctx.getShop()).isNotNull();
        assertThat(ctx.getShop().getSlots()).hasSize(5);
        assertThat(ctx.getShop().getSlots()).containsOnlyNulls();
    }

    @Test
    @DisplayName("5 参构造：注入同一商店实例 getShop() 同引用")
    void fullCtorHoldsSameShopInstance() {
        Player player = new Player(10);
        RunState runState = new RunState(42L, "scene_forest", new SequentialIdIssuer());
        GameData data = BattleTestFixtures.microData(BattleTestFixtures.meleeSkill());
        RandomGenerator rng = new RandomGenerator(42L);
        com.voidvvv.kz_auto_chess_n.systems.ShopSystem shop =
                new com.voidvvv.kz_auto_chess_n.systems.ShopSystem();
        RunContext ctx = new RunContext(player, runState, data, rng, shop);
        assertThat(ctx.getShop()).isSameAs(shop);
    }

    @Test
    @DisplayName("5 参构造拒绝 null shop")
    void fullCtorRejectsNullShop() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RunContext(
                new Player(10), new RunState(42L, "scene_forest", new SequentialIdIssuer()),
                BattleTestFixtures.microData(BattleTestFixtures.meleeSkill()),
                new RandomGenerator(42L), null))
                .isInstanceOf(NullPointerException.class);
    }
}
