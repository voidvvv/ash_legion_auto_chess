package com.voidvvv.kz_auto_chess_n.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景模板（data_schema §七 scenes.json，Phase 2 结构锁定版）。
 *
 * <p>场景 = 敌人池 + Boss 轮映射：enemyPool 按权重有放回抽取杂兵（允许同名重复，实现层口径 #3），
 * bosses 固定三键 {7, 15, 25}（加载期校验齐全，实现层口径 #5）。
 * 完全不可变，列表/映射保持 JSON 声明序（声明序即权重数组序，确定性抽取的前提）。
 */
public final class SceneData {
    private final String id;
    private final String name;
    /** 解锁前置场景 id；null = 初始开放（data_schema §七）。解锁判定 = ProfileService 派生（Phase 6，裁决 D7） */
    private final String unlockAfter;
    private final List<EnemyPoolEntry> enemyPool;
    /** {7, 15, 25} → Boss 模板 id（加载期保证三键齐全且被引用模板 isBoss） */
    private final Map<Integer, String> bosses;
    /** 该场景解锁后进入商店池的单位 id（Phase 6 裁决 D8；空表 = 无场景门控单位） */
    private final List<String> shopUnlocks;

    /** 兼容构造（Phase 5 存量测试先例）：无 shopUnlocks */
    public SceneData(String id, String name, String unlockAfter,
                     List<EnemyPoolEntry> enemyPool, Map<Integer, String> bosses) {
        this(id, name, unlockAfter, enemyPool, bosses, new ArrayList<String>());
    }

    public SceneData(String id, String name, String unlockAfter,
                     List<EnemyPoolEntry> enemyPool, Map<Integer, String> bosses,
                     List<String> shopUnlocks) {
        this.id = id;
        this.name = name;
        this.unlockAfter = unlockAfter;
        this.enemyPool = Collections.unmodifiableList(new ArrayList<EnemyPoolEntry>(enemyPool));
        this.bosses = Collections.unmodifiableMap(new LinkedHashMap<Integer, String>(bosses));
        this.shopUnlocks = Collections.unmodifiableList(new ArrayList<String>(shopUnlocks));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    /** 解锁前置场景 id；null = 初始开放 */
    public String getUnlockAfter() { return unlockAfter; }
    /** 该场景解锁后进入商店池的单位 id（不可变视图，声明序） */
    public List<String> getShopUnlocks() { return shopUnlocks; }
    public List<EnemyPoolEntry> getEnemyPool() { return enemyPool; }
    public Map<Integer, String> getBosses() { return bosses; }

    /** 查 Boss 轮映射；非 Boss 轮返回 null */
    public String getBossUnitId(int round) { return bosses.get(round); }

    /** 敌池条目：unitId（加载期校验必 ∈ units 且非 Boss 模板）+ 抽取权重 + 最早出现轮次 */
    public static final class EnemyPoolEntry {
        private final String unitId;
        /** 正整数 ≥ 1（实现层口径 #4，沿"JSON 不出现易错小数"精神） */
        private final int weight;
        /** 1~25（加载期校验 S6） */
        private final int minRound;

        public EnemyPoolEntry(String unitId, int weight, int minRound) {
            this.unitId = unitId;
            this.weight = weight;
            this.minRound = minRound;
        }

        public String getUnitId() { return unitId; }
        public int getWeight() { return weight; }
        public int getMinRound() { return minRound; }
    }
}
