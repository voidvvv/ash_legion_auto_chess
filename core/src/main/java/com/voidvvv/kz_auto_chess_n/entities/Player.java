package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;

/**
 * 玩家局内状态（Phase 1 版：金币/经验/等级；名单字段随 Unit 实体 Phase 3 增补）。
 *
 * <p>无 health 字段——1C-R 重试制无生命值机制（GDD §10.2 注，决策 2026-08-19）。
 * 纯宝箱经济：金币唯一货币（GDD §三）。
 */
public class Player {
    private int gold;
    private int level = 1;
    private int currentExp;

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
}
