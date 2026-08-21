package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 玩家局内状态（Phase 3 版：金币/经验/等级 + 名单——备战席 9 格 + 上场部署表 18 格）。
 *
 * <p>无 health 字段——1C-R 重试制无生命值机制（GDD §10.2 注，决策 2026-08-19）。
 * 纯宝箱经济：金币唯一货币（GDD §三）。
 *
 * <p>名单只做存储完整性：业务校验（人口上限 vs 等级、3 合 1、买卖规则）归命令层 Phase 5。
 * 部署表为 18 格数组（位置即数据——战斗派生需要坐标），索引 (y-4)*BOARD_COLS + x。
 */
public class Player {
    private int gold;
    private int level = 1;
    private int currentExp;
    private final List<Unit> bench = new ArrayList<Unit>();      // 备战席 ≤ BENCH_SIZE(9)
    private final Unit[] deployment = new Unit[GameBalance.BOARD_COLS * 3]; // 玩家区 18 格

    public Player(int startGold) {
        this.gold = Math.max(0, startGold);
    }

    public int getGold() { return gold; }
    public int getLevel() { return level; }
    public int getCurrentExp() { return currentExp; }

    /** 人口上限由等级决定（GDD §3.5） */
    public int getPopulationCap() {
        return GameBalance.population(level);
    }

    /**
     * 金币变动：支出传负数。防御性钳制不为负（命令层已校验，此处兜底）。
     */
    public void addGold(int amount) {
        gold = Math.max(0, gold + amount);
    }

    public boolean canAfford(int cost) {
        return gold >= cost;
    }

    /**
     * 累加经验并按经验表连续升级（GDD §3.5）；Lv.7 封顶后余量作废、不再累积。
     */
    public void addExp(int amount) {
        if (level >= GameBalance.MAX_PLAYER_LEVEL) {
            return; // 封顶后经验无效
        }
        currentExp += amount;
        while (level < GameBalance.MAX_PLAYER_LEVEL) {
            int need = GameBalance.expToNextLevel(level);
            if (currentExp < need) {
                break;
            }
            currentExp -= need;
            level++;
        }
        if (level >= GameBalance.MAX_PLAYER_LEVEL) {
            currentExp = 0; // 封顶余量作废
        }
    }

    // —— Phase 3 名单：备战席与部署表（framework-internal 纪律不适用——对 UI/命令层公开的名单 API） ——

    /** 备战席（不可变视图，入席序） */
    public List<Unit> getBench() {
        return Collections.unmodifiableList(bench);
    }

    /** 入席：满 9 格抛 IllegalStateException（命令层已校验，此处防御兜底） */
    public void addToBench(Unit unit) {
        Objects.requireNonNull(unit, "unit 不能为 null");
        if (bench.size() >= GameBalance.BENCH_SIZE) {
            throw new IllegalStateException("备战席已满（" + GameBalance.BENCH_SIZE + " 格），无法入席: " + unit.getId());
        }
        bench.add(unit);
    }

    /**
     * 插席：移入指定槽位（GDD §4.1 备战席 9 格位置语义，口径 #8）——索引钳制 [0, size]
     * （负数归 0、超 size 落末位），后续单位后移；满 9 格抛 IllegalStateException（同 addToBench）。
     * 槽位 = bench List 索引（入席序即展示序）；换位语义（先 remove 后 insert）归命令层。
     */
    public void insertToBench(Unit unit, int slotIndex) {
        Objects.requireNonNull(unit, "unit 不能为 null");
        if (bench.size() >= GameBalance.BENCH_SIZE) {
            throw new IllegalStateException("备战席已满（" + GameBalance.BENCH_SIZE + " 格），无法入席: " + unit.getId());
        }
        int index = Math.max(0, Math.min(bench.size(), slotIndex));
        bench.add(index, unit);
    }

    /** 出席：不在席抛 IllegalArgumentException */
    public void removeFromBench(Unit unit) {
        if (!bench.remove(unit)) {
            throw new IllegalArgumentException("单位不在备战席: " + (unit == null ? "null" : unit.getId()));
        }
    }

    /**
     * 部署到玩家区格（y ∈ 4~6）：越界抛 IllegalArgumentException、占用抛 IllegalStateException；
     * 自动从备战席摘除（名单一致性：非席内单位拒绝）。
     */
    public void deploy(Unit unit, int gridX, int gridY) {
        Objects.requireNonNull(unit, "unit 不能为 null");
        int idx = playerZoneIndex(gridX, gridY);
        if (deployment[idx] != null) {
            throw new IllegalStateException("格已被占用: (" + gridX + "," + gridY + ")");
        }
        if (!bench.remove(unit)) {
            throw new IllegalArgumentException("部署要求单位在备战席: " + unit.getId());
        }
        deployment[idx] = unit;
    }

    /** 撤下：清格并回备战席（单位本占名单一席，回席必有余位）；空格抛 IllegalStateException */
    public void undeploy(int gridX, int gridY) {
        int idx = playerZoneIndex(gridX, gridY);
        Unit unit = deployment[idx];
        if (unit == null) {
            throw new IllegalStateException("该格无部署单位: (" + gridX + "," + gridY + ")");
        }
        deployment[idx] = null;
        bench.add(unit);
    }

    /** 查部署格；空格返回 null（越界抛 IllegalArgumentException，与 deploy 同域校验） */
    public Unit deployedAt(int gridX, int gridY) {
        return deployment[playerZoneIndex(gridX, gridY)];
    }

    /** 上场单位列表，扫描序 y↑x↑——确定性序 = 开战发号序（口径 #16） */
    public List<Unit> getDeployedUnits() {
        List<Unit> result = new ArrayList<Unit>();
        for (int y = 4; y <= 6; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                Unit unit = deployment[(y - 4) * GameBalance.BOARD_COLS + x];
                if (unit != null) {
                    result.add(unit);
                }
            }
        }
        return result;
    }

    /** 名单总数 = 备战席 + 已部署 */
    public int getRosterSize() {
        return bench.size() + getDeployedUnits().size();
    }

    /** 玩家区索引与域校验：x ∈ [0,BOARD_COLS)、y ∈ [4,6]（第 3 行为缓冲带不可部署） */
    private static int playerZoneIndex(int gridX, int gridY) {
        if (gridX < 0 || gridX >= GameBalance.BOARD_COLS || gridY < 4 || gridY > 6) {
            throw new IllegalArgumentException(
                    "部署坐标必须在玩家区（x 0~" + (GameBalance.BOARD_COLS - 1) + "，y 4~6），实际=(" + gridX + "," + gridY + ")");
        }
        return (gridY - 4) * GameBalance.BOARD_COLS + gridX;
    }
}
