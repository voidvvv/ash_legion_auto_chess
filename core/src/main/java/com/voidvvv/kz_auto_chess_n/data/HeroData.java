package com.voidvvv.kz_auto_chess_n.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 英雄（棋手）模板（heroes.json，GDD §8.1）。完全不可变，加载一次终身只读；
 * 被动 = 类型 × 强度 ×（SYNERGY_AMP 时）作用羁绊集，效果装配归 ProfileService.runModifiers。
 */
public final class HeroData {
    private final String id;
    private final String name;
    private final String desc;
    private final HeroPassiveType passiveType;
    /** 被动强度：START_GOLD=金币 / SYNERGY_AMP=百分比 / ENERGY_GAIN=百分点（HeroPassiveType javadoc） */
    private final float passiveValue;
    /** SYNERGY_AMP 作用的羁绊 id（其余类型恒空表） */
    private final List<String> passiveSynergyIds;
    /** 熟练度 Lv.3 解锁的专属传奇棋子 id（加载期校验 ∈ units 且非 Boss、cost=3）；可空 */
    private final String legendaryUnitId;

    public HeroData(String id, String name, String desc, HeroPassiveType passiveType,
                    float passiveValue, List<String> passiveSynergyIds, String legendaryUnitId) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.passiveType = passiveType;
        this.passiveValue = passiveValue;
        this.passiveSynergyIds = Collections.unmodifiableList(
                new ArrayList<String>(passiveSynergyIds));
        this.legendaryUnitId = legendaryUnitId;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDesc() { return desc; }
    public HeroPassiveType getPassiveType() { return passiveType; }
    public float getPassiveValue() { return passiveValue; }
    public List<String> getPassiveSynergyIds() { return passiveSynergyIds; }
    public String getLegendaryUnitId() { return legendaryUnitId; }
}
