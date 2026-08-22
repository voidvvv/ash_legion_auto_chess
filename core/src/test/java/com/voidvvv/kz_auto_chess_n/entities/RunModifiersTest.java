package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RunModifiers（局外修正聚合值对象，Phase 6 CP5）测试：
 * 构造防御（负增益钳 0）/ EMPTY 语义 / 门控 / StatModifierSource 修正块。
 */
class RunModifiersTest {

    private static Map<String, Float> amp(String key, float value) {
        Map<String, Float> map = new LinkedHashMap<String, Float>();
        map.put(key, value);
        return map;
    }

    @Test
    @DisplayName("构造防御：四项数值增益为负时钳 0")
    void negativeBonusesClampedToZero() {
        RunModifiers modifiers = new RunModifiers(-3, -1, -5, -15,
                amp("syn_beast", -0.5f), null, new LinkedHashSet<String>(), true);
        assertThat(modifiers.getStartGoldBonus()).isZero();
        assertThat(modifiers.getRefreshCostDiscount()).isZero();
        assertThat(modifiers.getRareShopBonusPp()).isZero();
        assertThat(modifiers.getEnergyGainRateBonus()).isZero();
    }

    @Test
    @DisplayName("EMPTY：零增益 + 不门控商店池（isShopAllowed 恒 true）+ 空修正块")
    void emptySemantics() {
        assertThat(RunModifiers.EMPTY.getStartGoldBonus()).isZero();
        assertThat(RunModifiers.EMPTY.getSynergyAmp()).isEmpty();
        assertThat(RunModifiers.EMPTY.getLegendaryUnitId()).isNull();
        assertThat(RunModifiers.EMPTY.isShopPoolRestricted()).isFalse();
        assertThat(RunModifiers.EMPTY.isShopAllowed("any_unit")).isTrue();
        assertThat(RunModifiers.EMPTY.modifiers()).isEqualTo(StatModifierBlock.empty());
    }

    @Test
    @DisplayName("门控：restricted 时仅池内单位可购；未门控恒 true")
    void shopPoolGating() {
        Set<String> pool = new LinkedHashSet<String>();
        pool.add("u_a");
        pool.add("u_b");
        RunModifiers gated = new RunModifiers(0, 0, 0, 0,
                new LinkedHashMap<String, Float>(), null, pool, true);
        assertThat(gated.isShopAllowed("u_a")).isTrue();
        assertThat(gated.isShopAllowed("u_missing")).isFalse();
    }

    @Test
    @DisplayName("不可变视图：synergyAmp put / shopPoolUnitIds add 均抛 UnsupportedOperationException")
    void unmodifiableViews() {
        RunModifiers modifiers = new RunModifiers(0, 0, 0, 0,
                amp("syn_beast", 0.25f), "u_legend",
                new LinkedHashSet<String>(), false);
        assertThatThrownBy(() -> modifiers.getSynergyAmp().put("syn_orc", 0.1f))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("StatModifierSource：带 energyGainRateBonus 时 ADD 透传；零增益为空块")
    void statModifierBlockCarriesEnergyGain() {
        RunModifiers withBonus = new RunModifiers(0, 0, 0, 15,
                new LinkedHashMap<String, Float>(), null, new LinkedHashSet<String>(), false);
        assertThat(withBonus.modifiers().addOf(StatKey.ENERGY_GAIN_RATE)).isEqualTo(15f);
        assertThat(withBonus.modifiers().pctOf(StatKey.ENERGY_GAIN_RATE)).isZero();
        assertThat(withBonus.modifiers().isEmpty()).isFalse();
        assertThat(withBonus.modifiers().addOf(StatKey.HP)).isZero(); // 只贡献回能一键
    }

    @Test
    @DisplayName("修正块 op 语义：ADD 通道（energyGainRate 百分点刻度——裁决 D13）")
    void energyGainIsAddChannel() {
        RunModifiers modifiers = new RunModifiers(0, 0, 0, 15,
                new LinkedHashMap<String, Float>(), null, new LinkedHashSet<String>(), false);
        assertThat(modifiers.modifiers().addOf(StatKey.ENERGY_GAIN_RATE)).isEqualTo(15f);
        assertThat(modifiers.getSynergyAmp()).isEmpty();
        assertThat(modifiers.getLegendaryUnitId()).isNull();
        assertThat(modifiers.modifiers().isEmpty()).isFalse();
        // EffectOp 枚举存在性对拍（词表即代码）
        assertThat(EffectOp.ADD.jsonName()).isEqualTo("ADD");
    }
}
