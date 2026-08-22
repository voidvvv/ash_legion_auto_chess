package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.BattleStats;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 装备属性修正源测试（CP11；Phase 3 Q4 修正源列表的第二个实现）：
 * ΣADD/ΣPCT 合并、EMPTY 单例复用、与 StatPipeline.deriveBaseline 的先加后乘联动。
 */
class EquipmentStatsTest {

    // —— 夹具：铁剑（attack PCT 20）/ 玄铁板甲（hp ADD 200）口径 ——

    private static EquipmentData eq(String id, EquipmentSlot slot, StatKey stat, EffectOp op, float value) {
        return new EquipmentData(id, "装" + id, slot, EquipmentRarity.WHITE,
                Arrays.asList(new EquipmentEffect(stat, op, value)), null);
    }

    private static Equipment item(int id, EquipmentData template) {
        return new Equipment(id, template);
    }

    @Test
    @DisplayName("of(空列表) 与零修正装备均返回 EMPTY 单例")
    void emptyEquippedYieldsSingleton() {
        assertThat(EquipmentStats.of(Collections.<Equipment>emptyList())).isSameAs(EquipmentStats.EMPTY);
        assertThat(EquipmentStats.EMPTY.modifiers().isEmpty()).isTrue();
        EquipmentData noEffects = new EquipmentData("eq_blank", "空白", EquipmentSlot.WEAPON,
                EquipmentRarity.WHITE, Collections.<EquipmentEffect>emptyList(), null);
        assertThat(EquipmentStats.of(Arrays.asList(item(1, noEffects)))).isSameAs(EquipmentStats.EMPTY);
    }

    @Test
    @DisplayName("of：铁剑（attack PCT 20）+ 玄铁板甲（hp ADD 200）——addOf(HP)=200、pctOf(ATTACK)=20")
    void mergesAddAndPctChannels() {
        Equipment sword = item(1, eq("eq_sword", EquipmentSlot.WEAPON, StatKey.ATTACK, EffectOp.PCT, 20f));
        Equipment plate = item(2, eq("eq_plate", EquipmentSlot.ARMOR, StatKey.HP, EffectOp.ADD, 200f));
        EquipmentStats stats = EquipmentStats.of(Arrays.asList(sword, plate));
        assertThat(stats.modifiers().addOf(StatKey.HP)).isEqualTo(200f);
        assertThat(stats.modifiers().pctOf(StatKey.ATTACK)).isEqualTo(20f);
        assertThat(stats.modifiers().addOf(StatKey.ATTACK)).isZero();
        assertThat(stats.modifiers().pctOf(StatKey.HP)).isZero();
    }

    @Test
    @DisplayName("of：同 stat 同 op 多件装备累加（attack ADD 1 + 2 = 3）")
    void accumulatesSameChannel() {
        Equipment first = item(1, eq("eq_a", EquipmentSlot.WEAPON, StatKey.ATTACK, EffectOp.ADD, 1f));
        Equipment second = item(2, eq("eq_b", EquipmentSlot.WEAPON, StatKey.ATTACK, EffectOp.ADD, 2f));
        EquipmentStats stats = EquipmentStats.of(Arrays.asList(first, second));
        assertThat(stats.modifiers().addOf(StatKey.ATTACK)).isEqualTo(3f);
    }

    @Test
    @DisplayName("与 StatPipeline.deriveBaseline 联动：模板 × 星级后先加后乘（hp (100+200)、attack 10×1.2）")
    void integratesWithStatPipeline() {
        UnitData template = new UnitData("u1", "夹具u1", "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_u1", false);
        Equipment sword = item(1, eq("eq_sword", EquipmentSlot.WEAPON, StatKey.ATTACK, EffectOp.PCT, 20f));
        Equipment plate = item(2, eq("eq_plate", EquipmentSlot.ARMOR, StatKey.HP, EffectOp.ADD, 200f));
        List<StatModifierSource> sources = new ArrayList<StatModifierSource>();
        sources.add(EquipmentStats.of(Arrays.asList(sword, plate)));
        BattleStats baseline = StatPipeline.deriveBaseline(template, 1, 1f, sources);
        assertThat(baseline.get(StatKey.HP)).isCloseTo(300f, within(0.0001f));
        assertThat(baseline.get(StatKey.ATTACK)).isCloseTo(12f, within(0.0001f));
    }
}
