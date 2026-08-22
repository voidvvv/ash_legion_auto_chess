package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 装备实体测试（Phase 5 CP5；architecture §2.2 单一 id 空间的第二类实体）：
 * 完全不可变，id 即身份。
 */
class EquipmentTest {

    private static EquipmentData tpl(String id, EquipmentSlot slot) {
        return new EquipmentData(id, "夹具" + id, slot, EquipmentRarity.WHITE,
                Collections.singletonList(new EquipmentEffect(StatKey.ATTACK, EffectOp.ADD, 1f)), null);
    }

    @Test
    @DisplayName("字段只读：id / 模板引用")
    void exposesIdentityFields() {
        EquipmentData template = tpl("eq_a", EquipmentSlot.WEAPON);
        Equipment item = new Equipment(7, template);
        assertThat(item.getId()).isEqualTo(7);
        assertThat(item.getTemplate()).isSameAs(template);
    }

    @Test
    @DisplayName("equals/hashCode 按 id（模板不同不影响判等）")
    void equalityByIdOnly() {
        Equipment a = new Equipment(7, tpl("eq_a", EquipmentSlot.WEAPON));
        Equipment b = new Equipment(7, tpl("eq_b", EquipmentSlot.ARMOR));
        Equipment c = new Equipment(8, tpl("eq_a", EquipmentSlot.WEAPON));
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("模板为 null 抛 NullPointerException（防御）")
    void rejectsNullTemplate() {
        assertThatThrownBy(() -> new Equipment(1, null))
                .isInstanceOf(NullPointerException.class);
    }
}
