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
    // 暴击两键已属性化（v5 §五.5）：缺省值以 StatKey.CRIT_CHANCE/CRIT_DAMAGE.baseStatsDefault() 为唯一事实源

    /** 全局攻速系数（battle §5.1：敌我对称、只在消耗点乘算——BattleUnit.attackInterval() 一处；1 = 关闭） */
    public static final float ATTACK_SPEED_GLOBAL_FACTOR = 0.6f;

    /** BGM 开关（品质改造 §1.2：Music 接口已接线，默认关闭——播放方式待 codex 下批裁定） */
    public static boolean BGM_ENABLED = false;

    /** UI/世界整体渲染缩放（2026-11-21 主人反馈「所有 UI 偏大」：整体调小一档 = 0.8×；
     *  消费点两处——BattleTransitionController.zoom() 的 worldCamera 基准（<b>除以</b> s：libGDX zoom
     *  = 可见世界 ÷ 画布，缩小 s 倍 ⇔ zoom = 基准/s）与 BattleScreen 的 uiStage/dialogStage 根缩放
     *  （<b>乘</b> s、居中）。两域统一屏幕映射 screen(v)=(v−C)·s+C（C=屏心），s ≤ 1 时全部布局恒在视口；
     *  1.0 = 恢复原大小 */
    public static final float RENDER_GLOBAL_SCALE = 0.8f;

    // —— 开战转场「清场入阵」（battle §二 / render §5.6；2026-09-09 修订取代 2026-09-02 倒计时案）——
    /** 开战转场时长秒数（区间 0.4~0.8 待调；0 = 关闭转场——直接解冻且输入禁用窗口消失，软回滚杠杆 K11） */
    public static final float BATTLE_INTRO_TRANSITION_SECONDS = 0.6f;
    /** 备战期镜头基线 zoom（Q4：备战微拉远，开战回正 1.0「聚焦战场」；非整数缩放有轻微像素闪烁风险——计划 §8 W2） */
    public static final float SHOPPING_CAMERA_ZOOM = 1.06f;
    /** ② 备战席 / ③ 背包 / ⑥ 开战按钮 左滑（左移）距离，虚拟像素（≥ 备战席宽 108 + 留白；⑥ 同时淡出完成离场；待调） */
    public static final float BATTLE_TRANSITION_SLIDE_LEFT_PX = 140f;
    /** ⑧ 商店栏下滑 / 战斗 HUD 下落的单侧屏缘出场距离，虚拟像素（≥ 商店栏高 64 + 留白；待调） */
    public static final float BATTLE_TRANSITION_SLIDE_EDGE_PX = 72f;

    // —— 能量 ——
    public static final int ENERGY_MAX = 100;
    public static final int ENERGY_PER_HIT = 10;
    public static final int ENERGY_PER_HIT_TAKEN = 5;

    // —— 弹道 / 索敌 / DOT ——
    public static final float PROJECTILE_SPEED = 6f;
    public static final float RETARGET_INTERVAL = 2f;
    public static final float DOT_TICK_INTERVAL = 1f;

    // —— 旧疤（兽人(6) SCAR，v5 §4.1 计数通道；每层幅度全局常量，次数/封顶在 synergies.json value/value2）——
    /** 每层攻击加成（百分点，有效攻 ×(1+0.05×层数)） */
    public static final float SCAR_ATK_PCT_PER_STACK = 5f;
    /** 每层护甲加成（ADD） */
    public static final float SCAR_ARMOR_ADD_PER_STACK = 3f;

    // —— 卫队战吼（战士(4)/(6) WARCRY_TAUNT，CP23 K7；延迟/持续在 synergies.json value/value2）——
    /** 战吼震慑半径（曼哈顿，格） */
    public static final int WARCRY_TAUNT_RADIUS = 1;

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
    /** 胜利战毕节奏：最后敌人死亡后先定格战场展示死亡反馈，再进 RESULT 弹奖励（胜局专属延迟——
     *  消费点 RunFlowSystem.tickBattleEnd；败局 / 超时保持即时流转）。×2 变速按逻辑时长同倍缩短 */
    public static final float VICTORY_RESULT_DELAY_SECONDS = 1.5f;
    /** 战斗快进倍率（×2 变速档，只乘 accumulator 消费速率，Q2） */
    public static final float BATTLE_SPEED_FACTOR_FAST = 2f;

    // —— 经济 ——
    public static final int START_GOLD = 10; // 正式口径（GDD §3.2：开局 10 金）
    /** 测试档起始金币值：仅经启动参数 {@code --test-gold=<n>} 启用（见 {@link #applyLaunchArgs}）；
     *  未启用时生产路径走 {@link #START_GOLD}=10 正式口径，单元测试/控制台不受影响。 */
    public static final int TEST_PERIOD_START_GOLD = 999;
    /** 运行时测试档覆盖（-1 = 关闭，默认）。仅 {@link #applyLaunchArgs(String[])} 写入、
     *  经 {@code RunMode.fromLaunchOverride} 解析为首屏运行模式（品质改造 §0.1）；
     *  起始金由 {@code BattleScreen.startGold(RunMode, RunModifiers)} 按模式结算
     *  （测试档恒 {@link #TEST_PERIOD_START_GOLD}、正式恒 {@link #START_GOLD} + 局外加成），
     *  无其他写点。 */
    public static int testStartGoldOverride = -1;

    /** 解析启动参数（Lwjgl3Launcher 入口调用）：{@code --test-gold=<n>} 启用测试档起始金；
     *  无参 / 空参 / 非法值一律回到关闭态。 */
    public static void applyLaunchArgs(String[] args) {
        testStartGoldOverride = -1;
        if (args == null) {
            return;
        }
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--test-gold")) {
                continue;
            }
            if (arg.startsWith("--test-gold=")) {
                try {
                    testStartGoldOverride = Math.max(0, Integer.parseInt(arg.substring("--test-gold=".length())));
                } catch (NumberFormatException ignored) {
                    testStartGoldOverride = -1; // 非法值按未启用处理
                }
            } else if (arg.equals("--test-gold")) {
                testStartGoldOverride = TEST_PERIOD_START_GOLD; // 无值形式 = 测试档典型值 999
            }
        }
    }

    /** 启动参数解析结果只读视图（{@code --test-gold=<n>} 的 n；无值形式 = 测试档典型值；未启用 =
     *  正式口径 {@link #START_GOLD}）。品质改造 §0.1 起：新局起始金改由
     *  {@code BattleScreen.startGold(RunMode, RunModifiers)} 按模式结算，本函数不再参与起始金，
     *  保留为解析口径断言面（GameLaunchArgsTest）。 */
    public static int effectiveStartGold() {
        return testStartGoldOverride >= 0 ? testStartGoldOverride : START_GOLD;
    }
    public static final int SHOP_REFRESH_COST = 2;
    public static final int BUY_EXP_COST = 4;
    public static final int BUY_EXP_GAIN = 4;
    public static final int ROUND_INCOME_GOLD = 3;
    public static final int CHEST_GOLD_CAP = 12; // 修订二：10→12（现公式最大基础值 11，≈解除封顶；待调）
    /** 每轮战败机会上限（3 次机会制，GDD §2.2；第 3 败直接终局无败箱；工作值待调） */
    public static final int DEFEAT_LIMIT_PER_ROUND = 3;

    // —— 局外成长（GDD §8.1；Lv.1 解锁 = 全英雄基础权益，与英雄被动同通道叠加——裁决 D2）——
    /** 熟练度等级上限（GDD §8.1「等级上限 Lv.5」） */
    public static final int MASTERY_MAX_LEVEL = 5;
    /** Lv.1 解锁：初始金币 +2（全英雄，随开局即生效） */
    public static final int MASTERY_LV1_START_GOLD_BONUS = 2;
    /** Lv.2 解锁：商店 3 费概率加成（百分点；仅基础 3 费概率 > 0 的轮次生效——裁决 D5） */
    public static final int MASTERY_LV2_RARE_SHOP_BONUS_PP = 5;
    /** Lv.4 解锁：开局金币额外加成（工作值待调——GDD §8.1「更多待设计」裁决 D4） */
    public static final int MASTERY_LV4_START_GOLD_BONUS = 3;
    /** Lv.5 解锁：商店刷新费减免（工作值待调；实付下限 1 金——裁决 D4） */
    public static final int MASTERY_LV5_REFRESH_DISCOUNT = 1;
    /** 通关一次性熟练度经验（GDD §8.1「通关 +60」——裁决 D3） */
    public static final int MASTERY_COMPLETE_BONUS = 60;
    /** 每已达 1 轮熟练度经验（GDD §8.1「每通过 1 轮 +3」；AbandonRun 同口径 GDD §2.1） */
    public static final int MASTERY_EXP_PER_ROUND = 3;
    /** 熟练度升级经验表：Lv.1→2 起 50/100/150/200；Lv.5 封顶 0（GDD §8.1） */
    private static final int[] MASTERY_EXP_TO_NEXT = {50, 100, 150, 200, 0};

    // —— 宝箱三选一（Q2 裁决 A：最小可玩规则，数值待调）——
    /** 槽2 经验书基底（修订二：4 + floor(轮/5)——GDD §3.2；Boss 不加倍；待调） */
    public static final int CHEST_EXP_BOOK_BASE = 4;
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
    /** 棋手等级 → 升到下一级所需经验（修订二压平：4/8/12/20/28/36，总需求 148→108；Lv.7 封顶为 0；待调） */
    private static final int[] EXP_TO_NEXT_LEVEL = {4, 8, 12, 20, 28, 36, 0};

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

    /** D10 敌方羁绊档位上限（v5 §4.1 临时口径）：r1~5=0（无）、r6~12=2、r13~19=4、r20+=6 */
    public static int enemySynergyCountCap(int round) {
        checkRound(round);
        if (round <= 5) { return 0; }
        if (round <= 12) { return 2; }
        if (round <= 19) { return 4; }
        return 6;
    }

    /** 基础战后经验：前半程逐步开放人口，后半程保持升级节奏。 */
    public static int roundIncomeExp(int round) {
        checkRound(round);
        if (round < 8) {
            return 2;
        }
        return round < 15 ? 3 : 4;
    }

    /** 宝箱金币：3 + floor(轮/3)，封顶 12（修订二）；Boss 箱 ×2（GDD §3.2） */
    public static int chestGold(int round, boolean boss) {
        checkRound(round);
        int base = Math.min(CHEST_GOLD_CAP, 3 + round / 3);
        return boss ? base * 2 : base;
    }

    /** 经验书：4 + floor(轮/5)，胜箱败箱同公式同值，Boss 不加倍（修订二，GDD §3.2；待调） */
    public static int chestExpBook(int round) {
        checkRound(round);
        return CHEST_EXP_BOOK_BASE + round / 5;
    }

    /** 败箱金币 = 加倍后胜箱金币 ×50% 向下取整（GDD §3.2 锚点：7 轮 5 / 15 轮 8 / 25 轮 11；待调） */
    public static int defeatChestGold(int round) {
        checkRound(round);
        return chestGold(round, isBossRound(round)) / 2;
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

    /** 熟练度等级 → 升到下一级所需经验；Lv.5 封顶返回 0（GDD §8.1） */
    public static int masteryExpToNext(int level) {
        checkMasteryLevel(level);
        return MASTERY_EXP_TO_NEXT[level - 1];
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

    private static void checkMasteryLevel(int level) {
        if (level < 1 || level > MASTERY_MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "熟练度等级必须在 1~" + MASTERY_MAX_LEVEL + "，实际=" + level);
        }
    }
}
