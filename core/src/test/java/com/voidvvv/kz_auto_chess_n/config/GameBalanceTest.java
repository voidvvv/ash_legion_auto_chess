package com.voidvvv.kz_auto_chess_n.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * GameBalance 全局常量与数值公式测试（data_schema §十；GDD §3.2/3.4/3.5/4.3/7.3）。
 */
class GameBalanceTest {

    // —— 升星（GDD §4.3：属性 = 基础 × m^(星−1)，2星 ×1.8、3星 ×3.24）——

    @Test
    @DisplayName("星级属性倍率：1/2/3 星 → ×1 / ×1.8 / ×3.24")
    void starStatMultiplierAtEachStarLevel() {
        assertThat(GameBalance.starStatMultiplier(1.8f, 1)).isEqualTo(1.0f);
        assertThat(GameBalance.starStatMultiplier(1.8f, 2)).isCloseTo(1.8f, within(1e-6f));
        assertThat(GameBalance.starStatMultiplier(1.8f, 3)).isCloseTo(3.24f, within(1e-4f));
    }

    @Test
    @DisplayName("技能星级缩放：×(1+0.5×(星−1)) → 1 / 1.5 / 2.0")
    void skillStarScaleAtEachStarLevel() {
        assertThat(GameBalance.skillStarScale(1)).isEqualTo(1.0f);
        assertThat(GameBalance.skillStarScale(2)).isEqualTo(1.5f);
        assertThat(GameBalance.skillStarScale(3)).isEqualTo(2.0f);
    }

    // —— 敌方强度与人口（GDD §7.3）——

    @Test
    @DisplayName("敌方强度系数锚点：第1轮1.0、第5轮1.4、第25轮3.4")
    void enemyScaleAnchors() {
        assertThat(GameBalance.enemyScale(1)).isEqualTo(1.0f);
        assertThat(GameBalance.enemyScale(5)).isCloseTo(1.4f, within(1e-6f));
        assertThat(GameBalance.enemyScale(25)).isCloseTo(3.4f, within(1e-6f));
    }

    @Test
    @DisplayName("敌方人口锚点轮命中：1/3/5/8/12/16/20/25 → 1~8 人")
    void enemyCountAtAnchorRounds() {
        assertThat(GameBalance.enemyCount(1)).isEqualTo(1);
        assertThat(GameBalance.enemyCount(3)).isEqualTo(2);
        assertThat(GameBalance.enemyCount(5)).isEqualTo(3);
        assertThat(GameBalance.enemyCount(8)).isEqualTo(4);
        assertThat(GameBalance.enemyCount(12)).isEqualTo(5);
        assertThat(GameBalance.enemyCount(16)).isEqualTo(6);
        assertThat(GameBalance.enemyCount(20)).isEqualTo(7);
        assertThat(GameBalance.enemyCount(25)).isEqualTo(8);
    }

    @Test
    @DisplayName("敌方人口全程单调不降且在 1~8 界内")
    void enemyCountMonotonicWithinBounds() {
        int prev = 0;
        for (int round = 1; round <= GameBalance.TOTAL_ROUNDS; round++) {
            int count = GameBalance.enemyCount(round);
            assertThat(count).isBetween(1, 8);
            assertThat(count).isGreaterThanOrEqualTo(prev);
            prev = count;
        }
    }

    // —— 宝箱经济（GDD §3.2：3 + floor(轮/3)，第21轮起10金封顶；Boss 箱 ×2）——

    @Test
    @DisplayName("宝箱金币锚点与封顶")
    void chestGoldAnchorsAndCap() {
        assertThat(GameBalance.chestGold(1, false)).isEqualTo(3);
        assertThat(GameBalance.chestGold(3, false)).isEqualTo(4);
        assertThat(GameBalance.chestGold(6, false)).isEqualTo(5);
        assertThat(GameBalance.chestGold(12, false)).isEqualTo(7);
        assertThat(GameBalance.chestGold(21, false)).isEqualTo(10);
        assertThat(GameBalance.chestGold(25, false)).isEqualTo(10); // 封顶
    }

    @Test
    @DisplayName("Boss 宝箱双倍")
    void bossChestGoldDoubled() {
        // 第7轮 Boss：3+floor(7/3)=5 → ×2 = 10；第21轮 Boss：10×2 = 20
        assertThat(GameBalance.chestGold(7, true)).isEqualTo(10);
        assertThat(GameBalance.chestGold(21, true)).isEqualTo(20);
    }

    // —— 商店费阶概率（GDD §3.4 锚点，逐轮线性插值）——

    @Test
    @DisplayName("费阶概率锚点轮命中")
    void shopTierProbabilitiesAtAnchorRounds() {
        assertThat(GameBalance.shopTierProbabilities(1))
                .containsExactly(100f, 0f, 0f);   // 1~3 轮 100% 一费
        assertThat(GameBalance.shopTierProbabilities(3))
                .containsExactly(100f, 0f, 0f);
        assertThat(GameBalance.shopTierProbabilities(5))
                .containsExactly(70f, 30f, 0f);
        assertThat(GameBalance.shopTierProbabilities(10))
                .containsExactly(50f, 40f, 10f);
        assertThat(GameBalance.shopTierProbabilities(15))
                .containsExactly(40f, 45f, 15f);
        assertThat(GameBalance.shopTierProbabilities(21))
                .containsExactly(35f, 45f, 20f);
        assertThat(GameBalance.shopTierProbabilities(25))
                .containsExactly(35f, 45f, 20f);  // 21+ 持平
    }

    @Test
    @DisplayName("费阶概率锚点间线性插值")
    void shopTierProbabilitiesInterpolated() {
        // 第4轮：3轮(100/0/0) 与 5轮(70/30/0) 的中点 = 85/15/0
        assertThat(GameBalance.shopTierProbabilities(4))
                .usingComparatorWithPrecision(1e-4f)
                .containsExactly(85f, 15f, 0f);
        // 第18轮：15轮(40/45/15) 与 21轮(35/45/20) 中点 = 37.5/45/17.5
        assertThat(GameBalance.shopTierProbabilities(18))
                .usingComparatorWithPrecision(1e-4f)
                .containsExactly(37.5f, 45f, 17.5f);
    }

    @Test
    @DisplayName("费阶概率全程三档之和恒为 100")
    void shopTierProbabilitiesAlwaysSumTo100() {
        for (int round = 1; round <= GameBalance.TOTAL_ROUNDS; round++) {
            float[] p = GameBalance.shopTierProbabilities(round);
            assertThat(p).hasSize(3);
            assertThat(p[0] + p[1] + p[2]).isCloseTo(100f, within(1e-4f));
        }
    }

    // —— 棋手等级与人口（GDD §3.5 表）——

    @Test
    @DisplayName("人口上限表 Lv.1~7 → 3~9")
    void populationTable() {
        int[] expected = {3, 4, 5, 6, 7, 8, 9};
        for (int level = 1; level <= 7; level++) {
            assertThat(GameBalance.population(level)).isEqualTo(expected[level - 1]);
        }
    }

    @Test
    @DisplayName("升级经验需求表 Lv.1→2 起 4/8/16/24/40/56；Lv.7 封顶为 0")
    void expToNextLevelTable() {
        int[] expected = {4, 8, 16, 24, 40, 56, 0};
        for (int level = 1; level <= 7; level++) {
            assertThat(GameBalance.expToNextLevel(level)).isEqualTo(expected[level - 1]);
        }
    }

    // —— 常量快照（data_schema §十）——

    @Test
    @DisplayName("全局常量与设计文档一致")
    void constantsMatchDesignDocs() {
        assertThat(GameBalance.TOTAL_ROUNDS).isEqualTo(25);
        assertThat(GameBalance.BOSS_ROUNDS).containsExactly(7, 15, 25);
        assertThat(GameBalance.LOGIC_STEP).isCloseTo(1f / 60f, within(1e-8f));
        assertThat(GameBalance.BATTLE_TIMEOUT).isEqualTo(60f);
        assertThat(GameBalance.CRIT_CHANCE).isEqualTo(0.20f);
        assertThat(GameBalance.CRIT_MULTIPLIER).isEqualTo(1.5f);
        assertThat(GameBalance.ENERGY_MAX).isEqualTo(100);
        assertThat(GameBalance.ENERGY_PER_HIT).isEqualTo(10);
        assertThat(GameBalance.ENERGY_PER_HIT_TAKEN).isEqualTo(5);
        assertThat(GameBalance.PROJECTILE_SPEED).isEqualTo(6f);
        assertThat(GameBalance.RETARGET_INTERVAL).isEqualTo(2f);
        assertThat(GameBalance.DOT_TICK_INTERVAL).isEqualTo(1f);
        assertThat(GameBalance.MAX_EFFECTS_PER_SKILL).isEqualTo(3);
        assertThat(GameBalance.START_GOLD).isEqualTo(10);
        assertThat(GameBalance.SHOP_REFRESH_COST).isEqualTo(2);
        assertThat(GameBalance.BUY_EXP_COST).isEqualTo(4);
        assertThat(GameBalance.BUY_EXP_GAIN).isEqualTo(4);
        assertThat(GameBalance.MERCY_START_LOSS).isEqualTo(3);
        assertThat(GameBalance.MERCY_CAP_PER_ROUND).isEqualTo(3);
        assertThat(GameBalance.SHOP_SLOTS).isEqualTo(5);
        assertThat(GameBalance.BOARD_COLS).isEqualTo(6);
        assertThat(GameBalance.BOARD_ROWS).isEqualTo(7);
        assertThat(GameBalance.BENCH_SIZE).isEqualTo(9);
        assertThat(GameBalance.MAX_PLAYER_LEVEL).isEqualTo(7);
    }

    @Test
    @DisplayName("Boss 轮判定：仅 7/15/25 为 true")
    void bossRoundDetection() {
        for (int round = 1; round <= 25; round++) {
            boolean expected = Arrays.binarySearch(new int[]{7, 15, 25}, round) >= 0;
            assertThat(GameBalance.isBossRound(round)).isEqualTo(expected);
        }
    }
}
