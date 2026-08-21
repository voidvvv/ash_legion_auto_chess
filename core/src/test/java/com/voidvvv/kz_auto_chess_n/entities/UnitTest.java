package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 名单实体 Unit 测试：模板引用 + 星级 + id 身份（Phase 3 §7.1）。
 * 装备槽（Q4）与升星替换（3 合 1）推迟 Phase 5，本期完全不可变。
 */
class UnitTest {

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "skill_warcry", false);
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
}
