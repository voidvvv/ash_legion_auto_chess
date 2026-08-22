package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 名单实体 Unit 测试：模板引用 + 星级 + id 身份（Phase 3 §7.1）。
 * Phase 5 CP5 起受控可变：spend / 升星 / 装备三槽（framework-internal 写方法）。
 */
class UnitTest {

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "skill_warcry", false);
    }

    private static EquipmentData eqTpl(String id, EquipmentSlot slot) {
        return new EquipmentData(id, "夹具" + id, slot, EquipmentRarity.WHITE,
                Collections.singletonList(new EquipmentEffect(StatKey.ATTACK, EffectOp.ADD, 1f)), null);
    }

    private static Equipment item(int id, EquipmentSlot slot) {
        return new Equipment(id, eqTpl("eq" + id, slot));
    }

    @Test
    @DisplayName("字段只读：id / 模板引用 / 星级")
    void exposesIdentityFields() {
        UnitData template = tpl("u1");
        Unit unit = new Unit(7, template, 2);
        assertThat(unit.getId()).isEqualTo(7);
        assertThat(unit.getTemplate()).isSameAs(template);
        assertThat(unit.getStar()).isEqualTo(2);
    }

    @Test
    @DisplayName("星级边界：0 与 4 抛 IllegalArgumentException（合法域 1~3）")
    void rejectsStarOutOfRange() {
        assertThatThrownBy(() -> new Unit(1, tpl("u1"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1~3");
        assertThatThrownBy(() -> new Unit(1, tpl("u1"), 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1~3");
    }

    @Test
    @DisplayName("模板为 null 抛 NullPointerException（防御）")
    void rejectsNullTemplate() {
        assertThatThrownBy(() -> new Unit(1, null, 1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("equals/hashCode 按 id（id 空间全局唯一，模板不同不影响）")
    void equalityByIdOnly() {
        Unit a = new Unit(7, tpl("ua"), 1);
        Unit b = new Unit(7, tpl("ub"), 3);
        Unit c = new Unit(8, tpl("ua"), 1);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    // —— Phase 5 CP5：spend / 升星 / 装备三槽 ——

    @Test
    @DisplayName("spend：3 参构造缺省 0，4 参构造透传；负值抛 IllegalArgumentException")
    void spendDefaultsAndPassthrough() {
        assertThat(new Unit(1, tpl("u1"), 1).getSpend()).isZero();
        assertThat(new Unit(1, tpl("u1"), 1, 42).getSpend()).isEqualTo(42);
        assertThatThrownBy(() -> new Unit(1, tpl("u1"), 1, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("累计花费");
    }

    @Test
    @DisplayName("addSpend 累加；负增量抛 IllegalArgumentException")
    void addSpendAccumulatesWithValidation() {
        Unit unit = new Unit(1, tpl("u1"), 1);
        unit.addSpend(3);
        unit.addSpend(4);
        assertThat(unit.getSpend()).isEqualTo(7);
        assertThatThrownBy(() -> unit.addSpend(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("upgradeStar 星级 +1；3 星再合抛 IllegalStateException；升星后 equals 不变（同 id）")
    void upgradeStarIncrementsAndKeepsIdentity() {
        Unit unit = new Unit(1, tpl("u1"), 1);
        unit.upgradeStar();
        assertThat(unit.getStar()).isEqualTo(2);
        Unit same = new Unit(1, tpl("u1"), 2);
        assertThat(unit).isEqualTo(same); // 升星不变身份

        Unit threeStar = new Unit(2, tpl("u2"), 3);
        assertThatThrownBy(threeStar::upgradeStar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 星上限");
    }

    @Test
    @DisplayName("equippedIn 空槽返回 null")
    void equippedInEmptySlotReturnsNull() {
        Unit unit = new Unit(1, tpl("u1"), 1);
        assertThat(unit.equippedIn(EquipmentSlot.WEAPON)).isNull();
        assertThat(unit.equippedIn(EquipmentSlot.ARMOR)).isNull();
        assertThat(unit.equippedIn(EquipmentSlot.TRINKET)).isNull();
    }

    @Test
    @DisplayName("equip 三槽各一（穿着序）；满 3 件抛 IllegalStateException")
    void equipFillsThreeSlotsWithUniqueness() {
        Unit unit = new Unit(1, tpl("u1"), 1);
        Equipment weapon = item(11, EquipmentSlot.WEAPON);
        Equipment armor = item(12, EquipmentSlot.ARMOR);
        Equipment trinket = item(13, EquipmentSlot.TRINKET);
        unit.equip(weapon);
        unit.equip(armor);
        unit.equip(trinket);
        assertThat(unit.getEquipped()).containsExactly(weapon, armor, trinket); // 穿着序
        assertThat(unit.equippedIn(EquipmentSlot.WEAPON)).isSameAs(weapon);
        assertThat(unit.equippedIn(EquipmentSlot.TRINKET)).isSameAs(trinket);

        // 三槽已满：第 4 件无论什么槽位都拒绝（满检查在槽位检查之前）
        assertThatThrownBy(() -> unit.equip(item(14, EquipmentSlot.ARMOR)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("装备槽已满");
    }

    @Test
    @DisplayName("equip 同槽二次抛 IllegalStateException（未满状态下的槽位唯一）")
    void equipRejectsDuplicateSlot() {
        Unit unit = new Unit(1, tpl("u1"), 1);
        unit.equip(item(11, EquipmentSlot.WEAPON));
        unit.equip(item(12, EquipmentSlot.ARMOR)); // 未满（2/3）
        assertThatThrownBy(() -> unit.equip(item(13, EquipmentSlot.WEAPON)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("槽位已占用");
        assertThat(unit.getEquipped()).hasSize(2); // 拒绝后状态不变
    }

    @Test
    @DisplayName("unequip 卸下指定装备；未穿戴在此棋子抛 IllegalArgumentException")
    void unequipValidatesMembership() {
        Unit unit = new Unit(1, tpl("u1"), 1);
        Equipment weapon = item(11, EquipmentSlot.WEAPON);
        unit.equip(weapon);
        unit.unequip(weapon);
        assertThat(unit.getEquipped()).isEmpty();
        assertThat(unit.equippedIn(EquipmentSlot.WEAPON)).isNull();

        Unit other = new Unit(2, tpl("u2"), 1);
        Equipment otherWeapon = item(21, EquipmentSlot.WEAPON);
        other.equip(otherWeapon);
        assertThatThrownBy(() -> unit.unequip(otherWeapon)) // 穿在别的棋子身上
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getEquipped 返回不可变视图（穿着序）")
    void equippedViewUnmodifiable() {
        Unit unit = new Unit(1, tpl("u1"), 1);
        unit.equip(item(11, EquipmentSlot.WEAPON));
        assertThatThrownBy(() -> unit.getEquipped().add(item(12, EquipmentSlot.ARMOR)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

