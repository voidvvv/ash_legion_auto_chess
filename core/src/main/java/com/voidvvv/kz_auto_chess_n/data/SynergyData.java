package com.voidvvv.kz_auto_chess_n.data;

import java.util.Collections;
import java.util.List;

/**
 * 羁绊定义（data_schema §六 synergies.json）。
 *
 * <p>双通道计数：种族与职业各自独立计数（source 决定 key 匹配 units 的 race 还是 class）。
 * 门槛数组支持 2/4/6 或 3/5/7（count 升序唯一，加载期校验）。
 *
 * <p><b>档位替换制</b>（data_schema §六 V1.3 明文）：达到更高 count 时生效该档<b>全量</b>效果
 * （数值已含低档等价物），不与低档叠加——由 {@link #activeThreshold(int)} 兑现。
 */
public final class SynergyData {
    private final String id;
    private final String name;
    private final SynergySource source;
    /** 与 units 的 race（source=RACE）或 class（source=CLASS）值精确匹配 */
    private final String key;
    private final List<Threshold> thresholds;

    public SynergyData(String id, String name, SynergySource source, String key,
                       List<Threshold> thresholds) {
        this.id = id;
        this.name = name;
        this.source = source;
        this.key = key;
        this.thresholds = Collections.unmodifiableList(thresholds);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public SynergySource getSource() { return source; }
    public String getKey() { return key; }
    /** count 升序（加载期校验保证） */
    public List<Threshold> getThresholds() { return thresholds; }

    /**
     * 门槛判定（替换制）：给定同名单位数，返回达到的最高档；未达最低档返回 null。
     *
     * @param unitCount 该羁绊 key 的同名单位计数
     */
    public Threshold activeThreshold(int unitCount) {
        Threshold active = null;
        for (Threshold t : thresholds) {
            if (unitCount >= t.getCount()) {
                active = t;
            } else {
                break;
            }
        }
        return active;
    }

    /** 单一门槛档：count 与该档全量效果列表 */
    public static final class Threshold {
        private final int count;
        private final List<EffectData> effects;

        public Threshold(int count, List<EffectData> effects) {
            this.count = count;
            this.effects = Collections.unmodifiableList(effects);
        }

        public int getCount() { return count; }
        public List<EffectData> getEffects() { return effects; }
    }
}
