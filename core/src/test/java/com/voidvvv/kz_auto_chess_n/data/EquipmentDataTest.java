package com.voidvvv.kz_auto_chess_n.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 装备数据层测试（data_schema §八 结构锁定；Phase 5 CP2）：
 * 模板不可变（effects 防御性拷贝 + 不可变视图）、passive 可 null、词表 jsonName 往返。
 */
class EquipmentDataTest {

    private static EquipmentEffect effect(StatKey stat, EffectOp op, float value) {
        return new EquipmentEffect(stat, op, value);
    }

    @Test
    @DisplayName("字段透传：id/name/slot/rarity/effects")
    void exposesTemplateFields() {
        List<EquipmentEffect> effects = new ArrayList<EquipmentEffect>();
        effects.add(effect(StatKey.ATTACK, EffectOp.PCT, 20f));
        effects.add(effect(StatKey.ARMOR, EffectOp.ADD, 5f));
        EquipmentData data = new EquipmentData("eq_x", "试作装备", EquipmentSlot.WEAPON,
                EquipmentRarity.WHITE, effects, null);

        assertThat(data.getId()).isEqualTo("eq_x");
        assertThat(data.getName()).isEqualTo("试作装备");
        assertThat(data.getSlot()).isEqualTo(EquipmentSlot.WEAPON);
        assertThat(data.getRarity()).isEqualTo(EquipmentRarity.WHITE);
        assertThat(data.getEffects()).hasSize(2);
        assertThat(data.getEffects().get(0).getStat()).isEqualTo(StatKey.ATTACK);
        assertThat(data.getEffects().get(0).getOp()).isEqualTo(EffectOp.PCT);
        assertThat(data.getEffects().get(0).getValue()).isEqualTo(20f);
        assertThat(data.getEffects().get(1).getValue()).isEqualTo(5f);
    }

    @Test
    @DisplayName("effects 视图不可变：外部写入抛 UnsupportedOperationException")
    void effectsViewUnmodifiable() {
        List<EquipmentEffect> effects = new ArrayList<EquipmentEffect>();
        effects.add(effect(StatKey.HP, EffectOp.ADD, 100f));
        EquipmentData data = new EquipmentData("eq_x", "试作装备", EquipmentSlot.ARMOR,
                EquipmentRarity.RARE, effects, null);
        assertThatThrownBy(() -> data.getEffects().add(effect(StatKey.HP, EffectOp.ADD, 1f)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("防御性拷贝：构造后改源列表不影响模板")
    void defensiveCopyOfEffects() {
        List<EquipmentEffect> source = new ArrayList<EquipmentEffect>();
        source.add(effect(StatKey.HP, EffectOp.ADD, 100f));
        EquipmentData data = new EquipmentData("eq_x", "试作装备", EquipmentSlot.ARMOR,
                EquipmentRarity.WHITE, source, null);
        source.add(effect(StatKey.ATTACK, EffectOp.ADD, 1f));
        assertThat(data.getEffects()).hasSize(1);
    }

    @Test
    @DisplayName("passive 可 null：无被动装备 getPassive() 为 null")
    void passiveMayBeNull() {
        EquipmentData data = new EquipmentData("eq_x", "试作装备", EquipmentSlot.TRINKET,
                EquipmentRarity.WHITE,
                Collections.singletonList(effect(StatKey.LIFESTEAL, EffectOp.ADD, 10f)), null);
        assertThat(data.getPassive()).isNull();
    }

    @Test
    @DisplayName("passive 字段透传：type/power/tickInterval")
    void exposesPassiveFields() {
        EquipmentPassive passive = new EquipmentPassive(StatusType.REGEN, 0.02f, 5f);
        EquipmentData data = new EquipmentData("eq_dragon", "龙心", EquipmentSlot.ARMOR,
                EquipmentRarity.LEGENDARY,
                Collections.singletonList(effect(StatKey.HP, EffectOp.ADD, 400f)), passive);
        assertThat(data.getPassive()).isSameAs(passive);
        assertThat(data.getPassive().getType()).isEqualTo(StatusType.REGEN);
        assertThat(data.getPassive().getPower()).isEqualTo(0.02f);
        assertThat(data.getPassive().getTickInterval()).isEqualTo(5f);
    }

    @Test
    @DisplayName("null 参数防御：id/name/slot/rarity/effects/stat/op/type 抛 NullPointerException")
    void rejectsNullArguments() {
        List<EquipmentEffect> effects = Collections.singletonList(effect(StatKey.ATTACK, EffectOp.ADD, 1f));
        assertThatThrownBy(() -> new EquipmentData(null, "名", EquipmentSlot.WEAPON,
                EquipmentRarity.WHITE, effects, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EquipmentData("eq_x", null, EquipmentSlot.WEAPON,
                EquipmentRarity.WHITE, effects, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EquipmentData("eq_x", "名", null,
                EquipmentRarity.WHITE, effects, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EquipmentData("eq_x", "名", EquipmentSlot.WEAPON,
                null, effects, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EquipmentData("eq_x", "名", EquipmentSlot.WEAPON,
                EquipmentRarity.WHITE, null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EquipmentEffect(null, EffectOp.ADD, 1f))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EquipmentEffect(StatKey.ATTACK, null, 1f))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EquipmentPassive(null, 0.02f, 5f))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("词表 jsonName 往返：槽位/稀有度 JSON 字面值与枚举一一对应")
    void vocabJsonNamesRoundTrip() {
        assertThat(EquipmentSlot.WEAPON.jsonName()).isEqualTo("WEAPON");
        assertThat(EquipmentSlot.ARMOR.jsonName()).isEqualTo("ARMOR");
        assertThat(EquipmentSlot.TRINKET.jsonName()).isEqualTo("TRINKET");
        assertThat(EquipmentRarity.WHITE.jsonName()).isEqualTo("WHITE");
        assertThat(EquipmentRarity.RARE.jsonName()).isEqualTo("RARE");
        assertThat(EquipmentRarity.LEGENDARY.jsonName()).isEqualTo("LEGENDARY");
    }
}
