package com.voidvvv.kz_auto_chess_n.screens;

import com.voidvvv.kz_auto_chess_n.entities.ChestOffer;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.entities.ChestOrigin;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BattleScreen.resultStatusLine 静态纯函数测试（机会制 CP11，GDD §2.2；工作值待调）：
 * 胜箱期 null / 败箱期「选择战败补给」/ 败 1~2 无箱「剩余机会 N」/ 败 3「机会耗尽 · 远征失败」。
 */
class BattleScreenStatusLineTest {

    private static RunState newState() {
        RunState state = new RunState(42L, "scene_forest", new SequentialIdIssuer());
        state.setPhase(GamePhase.RESULT);
        return state;
    }

    @Test
    @DisplayName("胜箱期（pendingChest origin=VICTORY）→ null（hint 由 ResultBanner 内置）")
    void victoryChestReturnsNull() {
        RunState state = newState();
        state.setPendingChest(new ChestOffer(1, false, ChestOrigin.VICTORY, Arrays.asList(
                ChestOption.gold(3), ChestOption.expBook(4), ChestOption.gold(3))));
        assertThat(BattleScreen.resultStatusLine(state)).isNull();
    }

    @Test
    @DisplayName("败箱期（pendingChest origin=DEFEAT）→ 「选择战败补给」")
    void defeatChestShowsPrompt() {
        RunState state = newState();
        state.setPendingChest(new ChestOffer(1, false, ChestOrigin.DEFEAT, Arrays.asList(
                ChestOption.gold(1), ChestOption.expBook(4))));
        assertThat(BattleScreen.resultStatusLine(state)).isEqualTo("选择战败补给");
    }

    @Test
    @DisplayName("败 1 / 败 2 无箱 → 「点击任意处重试 · 剩余机会 2 / 1」")
    void remainingChancesShown() {
        RunState one = newState();
        one.setDefeatCountPerRound(1);
        assertThat(BattleScreen.resultStatusLine(one)).isEqualTo("点击任意处重试 · 剩余机会 2");
        RunState two = newState();
        two.setDefeatCountPerRound(2);
        assertThat(BattleScreen.resultStatusLine(two)).isEqualTo("点击任意处重试 · 剩余机会 1");
    }

    @Test
    @DisplayName("败 3 → 「机会耗尽 · 远征失败」")
    void exhaustedShowsFailure() {
        RunState state = newState();
        state.setDefeatCountPerRound(3);
        assertThat(BattleScreen.resultStatusLine(state)).isEqualTo("机会耗尽 · 远征失败");
    }
}
