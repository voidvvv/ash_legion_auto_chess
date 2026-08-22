package com.voidvvv.kz_auto_chess_n.save;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 挂起存档（快照轨 MVP，仅在 SHOPPING 期捕获——裁决 D10）。完全不可变。 */
public final class RunSnapshot {
    public static final int CURRENT_VERSION = 1;

    private final int version;
    private final long seed;
    /** RNG 底层流消耗计数（恢复 = new RandomGenerator(seed, n) 重放对齐） */
    private final int rngConsumedCount;
    private final String sceneId;
    /** 可空（防御旧档/无英雄局） */
    private final String heroId;
    private final int round;
    private final int mercyLossCount;
    private final int mercyGoldThisRound;
    /** 发号器下一待发号（单一 id 空间续号） */
    private final int idIssuerNext;
    private final int playerGold;
    private final int playerLevel;
    private final int playerExp;
    /** 全部名单棋子（benchUnitIndex/deploymentUnitIndex 引用其下标） */
    private final List<UnitSnapshot> units;
    /** 备战席入席序（units 下标） */
    private final List<Integer> benchUnitIndex;
    /** 部署表 18 格（units 下标；-1 = 空格） */
    private final List<Integer> deploymentUnitIndex;
    /** 背包装备（未穿戴） */
    private final List<EquipmentSnapshot> inventory;
    /** 全部装备实例（含已穿；units.equippedItemIndex 引用其下标） */
    private final List<EquipmentSnapshot> equipments;
    /** 商店 5 槽模板 id（null 槽 = JSON null） */
    private final List<String> shopSlotUnitIds;
    /** 敌阵（轮内固定的重试不变量） */
    private final List<WaveEntrySnapshot> enemyWave;

    public RunSnapshot(int version, long seed, int rngConsumedCount, String sceneId, String heroId,
                       int round, int mercyLossCount, int mercyGoldThisRound, int idIssuerNext,
                       int playerGold, int playerLevel, int playerExp,
                       List<UnitSnapshot> units, List<Integer> benchUnitIndex,
                       List<Integer> deploymentUnitIndex, List<EquipmentSnapshot> inventory,
                       List<EquipmentSnapshot> equipments, List<String> shopSlotUnitIds,
                       List<WaveEntrySnapshot> enemyWave) {
        this.version = version;
        this.seed = seed;
        this.rngConsumedCount = rngConsumedCount;
        this.sceneId = sceneId;
        this.heroId = heroId;
        this.round = round;
        this.mercyLossCount = mercyLossCount;
        this.mercyGoldThisRound = mercyGoldThisRound;
        this.idIssuerNext = idIssuerNext;
        this.playerGold = playerGold;
        this.playerLevel = playerLevel;
        this.playerExp = playerExp;
        this.units = Collections.unmodifiableList(new ArrayList<UnitSnapshot>(units));
        this.benchUnitIndex = Collections.unmodifiableList(new ArrayList<Integer>(benchUnitIndex));
        this.deploymentUnitIndex = Collections.unmodifiableList(new ArrayList<Integer>(deploymentUnitIndex));
        this.inventory = Collections.unmodifiableList(new ArrayList<EquipmentSnapshot>(inventory));
        this.equipments = Collections.unmodifiableList(new ArrayList<EquipmentSnapshot>(equipments));
        this.shopSlotUnitIds = Collections.unmodifiableList(new ArrayList<String>(shopSlotUnitIds));
        this.enemyWave = Collections.unmodifiableList(new ArrayList<WaveEntrySnapshot>(enemyWave));
    }

    public int getVersion() { return version; }
    public long getSeed() { return seed; }
    public int getRngConsumedCount() { return rngConsumedCount; }
    public String getSceneId() { return sceneId; }
    public String getHeroId() { return heroId; }
    public int getRound() { return round; }
    public int getMercyLossCount() { return mercyLossCount; }
    public int getMercyGoldThisRound() { return mercyGoldThisRound; }
    public int getIdIssuerNext() { return idIssuerNext; }
    public int getPlayerGold() { return playerGold; }
    public int getPlayerLevel() { return playerLevel; }
    public int getPlayerExp() { return playerExp; }
    public List<UnitSnapshot> getUnits() { return units; }
    public List<Integer> getBenchUnitIndex() { return benchUnitIndex; }
    public List<Integer> getDeploymentUnitIndex() { return deploymentUnitIndex; }
    public List<EquipmentSnapshot> getInventory() { return inventory; }
    public List<EquipmentSnapshot> getEquipments() { return equipments; }
    public List<String> getShopSlotUnitIds() { return shopSlotUnitIds; }
    public List<WaveEntrySnapshot> getEnemyWave() { return enemyWave; }

    /** 名单棋子快照 */
    public static final class UnitSnapshot {
        private final int id;
        private final String unitId;
        private final int star;
        private final int spend;
        private final List<Integer> equippedItemIndex; // equipments 下标

        public UnitSnapshot(int id, String unitId, int star, int spend, List<Integer> equippedItemIndex) {
            this.id = id;
            this.unitId = unitId;
            this.star = star;
            this.spend = spend;
            this.equippedItemIndex = Collections.unmodifiableList(new ArrayList<Integer>(equippedItemIndex));
        }

        public int getId() { return id; }
        public String getUnitId() { return unitId; }
        public int getStar() { return star; }
        public int getSpend() { return spend; }
        public List<Integer> getEquippedItemIndex() { return equippedItemIndex; }
    }

    /** 装备快照（背包与已穿共池，按 id 幂等） */
    public static final class EquipmentSnapshot {
        private final int id;
        private final String templateId;

        public EquipmentSnapshot(int id, String templateId) {
            this.id = id;
            this.templateId = templateId;
        }

        public int getId() { return id; }
        public String getTemplateId() { return templateId; }
    }

    /** 敌阵条目快照（WaveSpec 平面化） */
    public static final class WaveEntrySnapshot {
        private final String unitId;
        private final int star;
        private final float scale;
        private final int gridX;
        private final int gridY;

        public WaveEntrySnapshot(String unitId, int star, float scale, int gridX, int gridY) {
            this.unitId = unitId;
            this.star = star;
            this.scale = scale;
            this.gridX = gridX;
            this.gridY = gridY;
        }

        public String getUnitId() { return unitId; }
        public int getStar() { return star; }
        public float getScale() { return scale; }
        public int getGridX() { return gridX; }
        public int getGridY() { return gridY; }
    }
}
