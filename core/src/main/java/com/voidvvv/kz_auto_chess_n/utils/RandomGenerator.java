package com.voidvvv.kz_auto_chess_n.utils;

import java.util.Random;

/**
 * 确定性随机（architecture §六 RNG 消耗点清单的唯一入口）。
 *
 * <p>封装 java.util.Random（实现层口径 #1）：JVM 规范保证跨平台位级确定、
 * 零 {@code Gdx.*} 依赖、JUnit 直测（分层约束，project_structure §四）。
 * 每次底层随机消耗 +1 计数（口径 #7），供测试断言与回放审计。
 */
public final class RandomGenerator {
    private final Random random;
    private int consumedCount;

    public RandomGenerator(long seed) {
        this.random = new Random(seed);
    }

    public int nextInt(int bound) {
        consumedCount++;
        return random.nextInt(bound);
    }

    public float nextFloat() {
        consumedCount++;
        return random.nextFloat();
    }

    /**
     * 按权重抽取，返回命中索引；每次消耗恰好 1 个随机数。
     *
     * <p>实现：r = nextFloat() × 正权重之和，线性累积扫描；
     * 浮点边界钳制到最后一个有效索引；weight ≤ 0 的条目永不命中；
     * 全 ≤ 0 抛 IllegalArgumentException。weights 数组顺序即抽取序
     * （调用方传 enemyPool 声明序，保证同 seed 同序列）。
     */
    public int weightedPick(int[] weights) {
        long sum = 0;
        for (int w : weights) {
            if (w > 0) {
                sum += w;
            }
        }
        if (sum <= 0) {
            throw new IllegalArgumentException("weightedPick 无有效权重（全 ≤ 0）: " + java.util.Arrays.toString(weights));
        }
        float r = nextFloat() * sum;
        float cumulative = 0f;
        int lastIndex = -1;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] <= 0) {
                continue;
            }
            lastIndex = i;
            cumulative += weights[i];
            if (r < cumulative) {
                return i;
            }
        }
        return lastIndex; // 浮点累积边界钳制：r ≥ 总和时归最后一个有效索引
    }

    /** 底层随机消耗计数（回放审计与测试断言用） */
    public int getConsumedCount() {
        return consumedCount;
    }
}
