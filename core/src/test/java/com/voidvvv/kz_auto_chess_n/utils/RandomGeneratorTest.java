package com.voidvvv.kz_auto_chess_n.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RandomGenerator 确定性与加权抽取用例（Phase 2 实现层口径 #1/#7）：
 * 封装 java.util.Random（JVM 规范保证跨平台位级确定），
 * 每次底层随机消耗 +1，weightedPick 恰好消耗 1 个随机数。
 */
class RandomGeneratorTest {

    @Test
    @DisplayName("同 seed 两次实例序列逐位一致（nextInt/nextFloat/weightedPick 交错）")
    void sameSeedProducesIdenticalSequence() {
        RandomGenerator a = new RandomGenerator(42L);
        RandomGenerator b = new RandomGenerator(42L);
        int[] weights = {3, 1, 2};
        for (int i = 0; i < 100; i++) {
            assertThat(a.nextInt(6)).isEqualTo(b.nextInt(6));
            assertThat(a.nextFloat()).isEqualTo(b.nextFloat());
            assertThat(a.weightedPick(weights)).isEqualTo(b.weightedPick(weights));
        }
    }

    @Test
    @DisplayName("委托对拍：与裸 java.util.Random 同 seed 交错取值完全一致")
    void delegatesToJavaUtilRandom() {
        RandomGenerator g = new RandomGenerator(7L);
        Random raw = new Random(7L);
        for (int i = 0; i < 50; i++) {
            assertThat(g.nextInt(10)).isEqualTo(raw.nextInt(10));
            assertThat(g.nextFloat()).isEqualTo(raw.nextFloat());
        }
    }

    @Test
    @DisplayName("nextInt 值域：bound=1 恒 0，bound=6 恒落 [0,6) 整数")
    void nextIntBounds() {
        RandomGenerator g = new RandomGenerator(1L);
        for (int i = 0; i < 100; i++) {
            assertThat(g.nextInt(1)).isZero();
        }
        for (int i = 0; i < 1000; i++) {
            assertThat(g.nextInt(6)).isBetween(0, 5);
        }
    }

    @Test
    @DisplayName("nextFloat 值域：恒落 [0,1)")
    void nextFloatBounds() {
        RandomGenerator g = new RandomGenerator(2L);
        for (int i = 0; i < 1000; i++) {
            assertThat(g.nextFloat()).isGreaterThanOrEqualTo(0f).isLessThan(1f);
        }
    }

    @Test
    @DisplayName("weightedPick：单权必中自身，零权/负权永不命中")
    void weightedPickZeroWeightNeverHits() {
        RandomGenerator g = new RandomGenerator(3L);
        for (int i = 0; i < 100; i++) {
            assertThat(g.weightedPick(new int[]{5})).isZero();
            assertThat(g.weightedPick(new int[]{0, 3})).isEqualTo(1);
            assertThat(g.weightedPick(new int[]{-2, 4})).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("weightedPick：固定 seed 权重 3:1 抽 4000 次，命中比例对拍 3000±150")
    void weightedPickProportion() {
        RandomGenerator g = new RandomGenerator(42L);
        int hits0 = 0;
        for (int i = 0; i < 4000; i++) {
            if (g.weightedPick(new int[]{3, 1}) == 0) {
                hits0++;
            }
        }
        assertThat(hits0).isBetween(2850, 3150);
    }

    @Test
    @DisplayName("weightedPick：全非正权重（全零/全负/空数组）抛 IllegalArgumentException")
    void weightedPickAllNonPositiveThrows() {
        RandomGenerator g = new RandomGenerator(4L);
        assertThatThrownBy(() -> g.weightedPick(new int[]{0, 0}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> g.weightedPick(new int[]{-1, -2}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> g.weightedPick(new int[]{}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("消耗计数：nextInt/nextFloat 各 +1，weightedPick 恰好 +1")
    void consumedCountAccurate() {
        RandomGenerator g = new RandomGenerator(9L);
        assertThat(g.getConsumedCount()).isZero();
        g.nextInt(10);
        g.nextInt(10);
        g.nextFloat();
        assertThat(g.getConsumedCount()).isEqualTo(3);

        int before = g.getConsumedCount();
        g.weightedPick(new int[]{1, 2, 3});
        assertThat(g.getConsumedCount()).isEqualTo(before + 1);

        g.weightedPick(new int[]{1});
        g.weightedPick(new int[]{2, 0});
        assertThat(g.getConsumedCount()).isEqualTo(6);
    }

    // —— 复原构造（快照轨，Phase 6 CP16）：流重放对齐 ——

    @Test
    @DisplayName("复原构造流重放：(seed, n) 与原生消耗 n 次的实例后续 1000 个 nextFloat 逐位相同")
    void replayConstructorAlignsStream() {
        RandomGenerator nativeFlow = new RandomGenerator(42L);
        for (int i = 0; i < 137; i++) {
            nativeFlow.nextFloat(); // 模拟局内已消耗
        }
        RandomGenerator restored = new RandomGenerator(42L, 137);
        assertThat(restored.getConsumedCount()).isEqualTo(137);
        for (int i = 0; i < 1000; i++) {
            assertThat(restored.nextFloat()).as("第 %d 个 nextFloat", i).isEqualTo(nativeFlow.nextFloat());
        }
        assertThat(restored.getConsumedCount()).isEqualTo(nativeFlow.getConsumedCount());
    }

    @Test
    @DisplayName("复原构造：weightedPick 混合消耗后的重放对齐；负计数即死")
    void replayAfterMixedConsumption() {
        RandomGenerator nativeFlow = new RandomGenerator(7L);
        nativeFlow.weightedPick(new int[]{3, 1});
        nativeFlow.nextFloat();
        nativeFlow.weightedPick(new int[]{1, 0, 2});
        RandomGenerator restored = new RandomGenerator(7L, nativeFlow.getConsumedCount());
        for (int i = 0; i < 100; i++) {
            assertThat(restored.weightedPick(new int[]{2, 2})).isEqualTo(nativeFlow.weightedPick(new int[]{2, 2}));
        }
        assertThat(restored.getConsumedCount()).isEqualTo(nativeFlow.getConsumedCount());

        assertThatThrownBy(() -> new RandomGenerator(7L, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("≥ 0");
    }
}
