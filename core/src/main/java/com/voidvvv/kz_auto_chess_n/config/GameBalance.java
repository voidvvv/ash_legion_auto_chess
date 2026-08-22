package com.voidvvv.kz_auto_chess_n.config;

import com.voidvvv.kz_auto_chess_n.utils.AnchorTable;

/**
 * 全局平衡常量与数值公式（data_schema §十；拒绝魔法数字——所有跨系统数值以此为准）。
 *
 * <p>锚点表类数值（敌方人口 / 商店费阶概率）用 {@link AnchorTable} 分段线性插值；
 * 表格类数值（人口 / 经验）用数组直查。全部数值待调（GDD §十一），改这里不改调用方。
 */
public final class GameBalance {
    // —— 回合 ——
    public static final int TOTAL_ROUNDS = 25;
    public static final int[] BOSS_ROUNDS = {7, 15, 25};

    // —— 战斗 ——
    public static final float LOGIC_STEP = 1f / 60f;
    public static final float BATTLE_TIMEOUT = 60f;
    public static final float CRIT_CHANCE = 0.20f;
    public static final float CRIT_MULTIPLIER = 1.5f;

    // —— 能量 ——
    public static final int ENERGY_MAX = 100;
    public static final int ENERGY_PER_HIT = 10;
    public static final int ENERGY_PER_HIT_TAKEN = 5;

    // —— 弹道 / 索敌 / DOT ——
    public static final float PROJECTILE_SPEED = 6f;
    public static final float RETARGET_INTERVAL = 2f;
    public static final float DOT_TICK_INTERVAL = 1f;

    // —— 技能 ——
    public static final int MAX_EFFECTS_PER_SKILL = 3;
    /** 就地施放重入深度上限（口径 #19）：能量跨百回调的嵌套施放链防御性保险，超限推迟到下一行动 tick */
    public static final int MAX_INLINE_CAST_DEPTH = 16;

    // —— 帧循环 / 输入（Phase 4；input §5.3 / §3 死区 / Q2/Q3）——
    /** 单帧最大 delta（秒）：accumulator 累积前的死亡螺旋防御钳制 */
    public static final float MAX_DELTA = 0.1f;
    /** 单帧最大逻辑步数：超限丢弃剩余 accumulator（与 MAX_DELTA 双保险） */
    public static final int MAX_TICKS_PER_FRAME = 5;
    /** 拖拽死区（虚拟像素）：unproject 后位移小于此值未进入拖拽，视为点击 */
    public static final int DRAG_DEAD_ZONE_PX = 20;
    /** 战毕横幅停留秒数（到时自动回 SHOPPING，Q3） */
    public static final float RESULT_BANNER_SECONDS = 3f;
    /** 战斗快进倍率（×2 变速档，只乘 accumulator 消费速率，Q2） */
    public static final float BATTLE_SPEED_FACTOR_FAST = 2f;

    // —— 经济 ——
    public static final int START_GOLD = 10;
    public static final int SHOP_REFRESH_COST = 2;
    public static final int BUY_EXP_COST = 4;
    public static final int BUY_EXP_GAIN = 4;
    public static final int CHEST_GOLD_CAP = 10;
    public static final int MERCY_START_LOSS = 3;
    public static final int MERCY_CAP_PER_ROUND = 3;

    // —— 宝箱三选一（Q2 裁决 A：最小可玩规则，数值待调）——
    /** 槽2 经验书固定经验值（对齐"4 金 = 4 经验"购买价比，待调） */
    public static final int CHEST_EXP_BOOK_GAIN = 4;
    /** 普通箱装备槽稀有度权重 [白, 成, 传]（GDD §5.2：70/25/5，待调） */
    public static final int[] CHEST_RARITY_WEIGHTS = {70, 25, 5};
    /** Boss 箱装备槽稀有度权重 [白, 成, 传]——白位 0 = 必含 ≥1 成装及以上；传说 20% = 大幅提升（待调） */
    public static final int[] BOSS_CHEST_RARITY_WEIGHTS = {0, 80, 20};
    /** 费阶概率 float → weightedPick int 权重的放大刻度（锚点概率和恒 100 → 权重和恒 100000） */
    public static final int PROBABILITY_WEIGHT_SCALE = 1000;

    // —— 装备（GDD §5.2 B2）——
    /** 每棋子装备槽数：武器 + 盔甲 + 饰品各一 */
    public static final int EQUIP_SLOTS_PER_UNIT = 3;

    // —— 商店 / 棋盘 ——
    public static final int SHOP_SLOTS = 5;
    public static final int BOARD_COLS = 6;
    public static final int BOARD_ROWS = 7;
    public static final int BENCH_SIZE = 9;

    // —— 棋手等级 ——
    public static final int MAX_PLAYER_LEVEL = 7;

    /** 棋手等级 → 人口上限（GDD §3.5：Lv.1→3 ... Lv.7→9） */
    private static final int[] POPULATION_BY_LEVEL = {3, 4, 5, 6, 7, 8, 9};
    /** 棋手等级 → 升到下一级所需经验（GDD §3.5：Lv.1→2 起 4/8/16/24/40/56；Lv.7 封顶为 0） */
    private static final int[] EXP_TO_NEXT_LEVEL = {4, 8, 16, 24, 40, 56, 0};

    /**
     * 敌方人口锚点（GDD §7.3）：第1轮1人、第3轮2、第5轮3、第8轮4、第12轮5、第16轮6、第20轮7、第25轮8。
     * 插值后取整（四舍五入）——文档未定取整规则，此处为实现口径。
     */
    private static final AnchorTable ENEMY_COUNT = new AnchorTable(
            new float[]{1, 3, 5, 8, 12, 16, 20, 25},
            new float[]{1, 2, 3, 4, 5, 6, 7, 8});

    /** 商店费阶概率锚点（GDD §3.4）：轮次 3/5/10/15/21 → [1费, 2费, 3费]%；1~3 轮 100% 一费、21+ 持平 */
    private static final AnchorTable SHOP_TIER_P1 = new AnchorTable(
            new float[]{3, 5, 10, 15, 21}, new float[]{100, 70, 50, 40, 35});
    private static final AnchorTable SHOP_TIER_P2 = new AnchorTable(
            new float[]{3, 5, 10, 15, 21}, new float[]{0, 30, 40, 45, 45});
    private static final AnchorTable SHOP_TIER_P3 = new AnchorTable(
            new float[]{3, 5, 10, 15, 21}, new float[]{0, 0, 10, 15, 20});

    private GameBalance() {
    }

    /** 星级属性倍率：基础 × m^(星−1)（GDD §4.3，m 缺省 1.8 → 2星 ×1.8、3星 ×3.24） */
    public static float starStatMultiplier(float upgradeMultiplier, int star) {
        checkStar(star);
        return (float) Math.pow(upgradeMultiplier, star - 1);
    }

    /** 技能星级缩放：×(1 + 0.5×(星−1))，仅作用于数值幅度，状态时长与强度不变（GDD §4.3/§6.5） */
    public static float skillStarScale(int star) {
        checkStar(star);
        return 1f + 0.5f * (star - 1);
    }

    /** 敌方强度系数：k = 1 + 0.1×(轮−1)（GDD §7.3：第5轮1.4、第25轮3.4） */
    public static float enemyScale(int round) {
        checkRound(round);
        return 1f + 0.1f * (round - 1);
    }

    /** 敌方上场人数：锚点间线性插值后四舍五入（GDD §7.3 锚点表） */
    public static int enemyCount(int round) {
        checkRound(round);
        return Math.round(ENEMY_COUNT.valueAt(round));
    }

    /** 宝箱金币：3 + floor(轮/3)，第21轮起封顶10；Boss 箱 ×2（GDD §3.2） */
    public static int chestGold(int round, boolean boss) {
        checkRound(round);
        int base = Math.min(CHEST_GOLD_CAP, 3 + round / 3);
        return boss ? base * 2 : base;
    }

    /** 商店费阶概率 [1费, 2费, 3费]%：锚点间逐轮线性插值，三档之和恒为 100（GDD §3.4） */
    public static float[] shopTierProbabilities(int round) {
        checkRound(round);
        return new float[]{
                SHOP_TIER_P1.valueAt(round),
                SHOP_TIER_P2.valueAt(round),
                SHOP_TIER_P3.valueAt(round)};
    }

    /** 棋手等级 → 人口上限（GDD §3.5 表） */
    public static int population(int level) {
        checkLevel(level);
        return POPULATION_BY_LEVEL[level - 1];
    }

    /** 棋手等级 → 升到下一级所需经验；Lv.7 封顶返回 0（GDD §3.5 表） */
    public static int expToNextLevel(int level) {
        checkLevel(level);
        return EXP_TO_NEXT_LEVEL[level - 1];
    }

    /** Boss 轮判定（固定第 7/15/25 轮） */
    public static boolean isBossRound(int round) {
        checkRound(round);
        for (int bossRound : BOSS_ROUNDS) {
            if (bossRound == round) {
                return true;
            }
        }
        return false;
    }

    private static void checkStar(int star) {
        if (star < 1 || star > 3) {
            throw new IllegalArgumentException("星级必须在 1~3（3 合 1 上限 3 星），实际=" + star);
        }
    }

    private static void checkRound(int round) {
        if (round < 1 || round > TOTAL_ROUNDS) {
            throw new IllegalArgumentException("轮次必须在 1~" + TOTAL_ROUNDS + "，实际=" + round);
        }
    }

    private static void checkLevel(int level) {
        if (level < 1 || level > MAX_PLAYER_LEVEL) {
            throw new IllegalArgumentException("棋手等级必须在 1~" + MAX_PLAYER_LEVEL + "，实际=" + level);
        }
    }
}
