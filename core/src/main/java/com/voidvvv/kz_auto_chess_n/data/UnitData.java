package com.voidvvv.kz_auto_chess_n.data;

/**
 * 棋子静态模板（data_schema §四 units.json 字段终版）。
 *
 * <p>完全不可变：加载一次终身只读（architecture §2.4 第一层）。
 * Java 侧字段名 {@code unitClass} 对应 JSON 的 {@code "class"}（Java 关键字冲突，JsonLoader 显式映射）。
 *
 * <p>Boss 模板的 baseStats 已烘焙最终值（普通 Boss ×2.5HP/×2.0攻、最终 Boss ×3.0/×2.5，data_schema §4.2）。
 */
public final class UnitData {
    private final String id;
    private final String name;
    private final String race;
    private final String unitClass;
    private final int cost;
    private final BaseStats baseStats;
    private final float upgradeMultiplier;
    private final TargetPriority defaultPriority;
    /** 索敌覆盖，null = 无（用默认） */
    private final TargetPriority specialPriority;
    private final String skillId;
    private final boolean boss;

    public UnitData(String id, String name, String race, String unitClass, int cost,
                    BaseStats baseStats, float upgradeMultiplier,
                    TargetPriority defaultPriority, TargetPriority specialPriority,
                    String skillId, boolean boss) {
        this.id = id;
        this.name = name;
        this.race = race;
        this.unitClass = unitClass;
        this.cost = cost;
        this.baseStats = baseStats;
        this.upgradeMultiplier = upgradeMultiplier;
        this.defaultPriority = defaultPriority;
        this.specialPriority = specialPriority;
        this.skillId = skillId;
        this.boss = boss;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getRace() { return race; }
    /** 职业（JSON 字段 "class"） */
    public String getUnitClass() { return unitClass; }
    /** 费阶 ∈ {1,2,3}；Boss 为 0。商店查价的事实源（input §6.3） */
    public int getCost() { return cost; }
    public BaseStats getBaseStats() { return baseStats; }
    /** 星级倍率：属性 = 基础 × m^(星−1)，缺省 1.8 */
    public float getUpgradeMultiplier() { return upgradeMultiplier; }
    public TargetPriority getDefaultPriority() { return defaultPriority; }
    public TargetPriority getSpecialPriority() { return specialPriority; }
    /** 引用 skills.json 的技能 id（加载期校验必存在） */
    public String getSkillId() { return skillId; }
    public boolean isBoss() { return boss; }
}
