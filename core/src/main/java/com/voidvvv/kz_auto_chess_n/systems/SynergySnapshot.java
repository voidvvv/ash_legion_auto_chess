package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierBlock;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierSource;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 羁绊结算快照（{@link SynergySystem} 的不可变产物），实现 {@link StatModifierSource}
 * （Q4 修正源列表：本期属性管线唯一源）。
 *
 * <p>三部分：actives（达档羁绊与档位信息，供 UI 与测试断言）、statModifiers（stat 通道
 * ΣADD/ΣPCT，进第一级基准派生）、openingEffects（effect 通道如兽人 6 的开局盾，
 * startBattle 落地，口径 #17）。
 */
public final class SynergySnapshot implements StatModifierSource {
    /** 无任何达档羁绊的空快照（resolve 对未达最低档输入返回此单例） */
    public static final SynergySnapshot EMPTY = new SynergySnapshot(
            Collections.<ActiveSynergy>emptyList(), StatModifierBlock.empty(),
            Collections.<EffectData>emptyList());

    private final List<ActiveSynergy> actives;
    private final StatModifierBlock statModifiers;
    private final List<EffectData> openingEffects;

    public SynergySnapshot(List<ActiveSynergy> actives, StatModifierBlock statModifiers,
                           List<EffectData> openingEffects) {
        this.actives = Collections.unmodifiableList(new java.util.ArrayList<ActiveSynergy>(actives));
        this.statModifiers = Objects.requireNonNull(statModifiers, "statModifiers 不能为 null");
        this.openingEffects = Collections.unmodifiableList(new java.util.ArrayList<EffectData>(openingEffects));
    }

    /** 修正源接口：stat 通道修正块 */
    @Override
    public StatModifierBlock modifiers() {
        return statModifiers;
    }

    public StatModifierBlock getStatModifiers() { return statModifiers; }

    /** 达档羁绊列表（GameData 声明序） */
    public List<ActiveSynergy> getActives() { return actives; }

    /** 开局效果（effect 通道，ALLIES 目标语义：对该侧全部单位生效） */
    public List<EffectData> getOpeningEffects() { return openingEffects; }

    public boolean isEmpty() {
        return actives.isEmpty();
    }

    /** 达档羁绊条目：synergyId / 展示名 / 生效档位数 */
    public static final class ActiveSynergy {
        private final String synergyId;
        private final String name;
        private final int thresholdCount;

        public ActiveSynergy(String synergyId, String name, int thresholdCount) {
            this.synergyId = synergyId;
            this.name = name;
            this.thresholdCount = thresholdCount;
        }

        public String getSynergyId() { return synergyId; }
        public String getName() { return name; }
        public int getThresholdCount() { return thresholdCount; }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ActiveSynergy)) {
                return false;
            }
            ActiveSynergy that = (ActiveSynergy) o;
            return thresholdCount == that.thresholdCount
                    && Objects.equals(synergyId, that.synergyId)
                    && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(synergyId, name, thresholdCount);
        }
    }
}
