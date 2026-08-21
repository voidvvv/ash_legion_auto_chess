package com.voidvvv.kz_auto_chess_n.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 羁绊门槛判定测试（档位替换制，data_schema §六 V1.3：高档全量生效、不与低档叠加）。
 */
class SynergyDataTest {

    private static SynergyData synergyWith246Tiers() {
        List<SynergyData.Threshold> tiers = new ArrayList<SynergyData.Threshold>();
        tiers.add(new SynergyData.Threshold(2, Arrays.<EffectData>asList(
                new EffectData(StatKey.ARMOR, null, EffectOp.ADD, 20f, EffectTarget.ALLIES))));
        tiers.add(new SynergyData.Threshold(4, Arrays.<EffectData>asList(
                new EffectData(StatKey.ARMOR, null, EffectOp.ADD, 50f, EffectTarget.ALLIES),
                new EffectData(StatKey.ATTACK, null, EffectOp.PCT, 15f, EffectTarget.ALLIES))));
        tiers.add(new SynergyData.Threshold(6, Arrays.<EffectData>asList(
                new EffectData(StatKey.ARMOR, null, EffectOp.ADD, 100f, EffectTarget.ALLIES),
                new EffectData(StatKey.ATTACK, null, EffectOp.PCT, 30f, EffectTarget.ALLIES),
                new EffectData(null, StatusType.SHIELD, null, 0.3f, EffectTarget.ALLIES))));
        return new SynergyData("syn_warrior", "战士", SynergySource.CLASS, "战士", tiers);
    }

    @Test
    @DisplayName("未达最低门槛时无生效档")
    void noActiveThresholdBelowMinimum() {
        SynergyData synergy = synergyWith246Tiers();
        assertThat(synergy.activeThreshold(0)).isNull();
        assertThat(synergy.activeThreshold(1)).isNull();
    }

    @Test
    @DisplayName("达到门槛时替换制生效：高档全量替换低档，非叠加")
    void higherTierReplacesLowerTier() {
        SynergyData synergy = synergyWith246Tiers();
        // 2~3 个：生效 (2) 档
        assertThat(synergy.activeThreshold(2).getCount()).isEqualTo(2);
        assertThat(synergy.activeThreshold(3).getCount()).isEqualTo(2);
        // 4~5 个：生效 (4) 档（全量替换，非叠加）
        assertThat(synergy.activeThreshold(4).getCount()).isEqualTo(4);
        assertThat(synergy.activeThreshold(5).getCount()).isEqualTo(4);
        // 6 个及以上：生效 (6) 档
        assertThat(synergy.activeThreshold(6).getCount()).isEqualTo(6);
        assertThat(synergy.activeThreshold(9)).isSameAs(synergy.getThresholds().get(2));
    }

    @Test
    @DisplayName("高档效果为该档全量条目（stat 通道与 effect 通道并存）")
    void activeTierContainsAllEffects() {
        SynergyData synergy = synergyWith246Tiers();
        SynergyData.Threshold tier6 = synergy.activeThreshold(6);
        assertThat(tier6.getEffects()).hasSize(3);
        // 第三条走 effect 通道（SHIELD 30% maxHp），前两条走 stat 通道
        assertThat(tier6.getEffects().get(0).getStat()).isEqualTo(StatKey.ARMOR);
        assertThat(tier6.getEffects().get(2).getEffect()).isEqualTo(StatusType.SHIELD);
    }

    @Test
    @DisplayName("3/5/7 门槛风格同样适用")
    void supports357ThresholdStyle() {
        List<SynergyData.Threshold> tiers = Arrays.asList(
                new SynergyData.Threshold(3, Arrays.<EffectData>asList(
                        new EffectData(StatKey.ATTACK, null, EffectOp.PCT, 10f, EffectTarget.ALLIES))),
                new SynergyData.Threshold(5, Arrays.<EffectData>asList(
                        new EffectData(StatKey.ATTACK, null, EffectOp.PCT, 25f, EffectTarget.ALLIES))),
                new SynergyData.Threshold(7, Arrays.<EffectData>asList(
                        new EffectData(StatKey.ATTACK, null, EffectOp.PCT, 50f, EffectTarget.ALLIES))));
        SynergyData synergy = new SynergyData("syn_x", "试作", SynergySource.RACE, "试作", tiers);
        assertThat(synergy.activeThreshold(2)).isNull();
        assertThat(synergy.activeThreshold(3).getCount()).isEqualTo(3);
        assertThat(synergy.activeThreshold(4).getCount()).isEqualTo(3);
        assertThat(synergy.activeThreshold(5).getCount()).isEqualTo(5);
        assertThat(synergy.activeThreshold(7).getCount()).isEqualTo(7);
        assertThat(synergy.activeThreshold(12).getCount()).isEqualTo(7);
    }
}
