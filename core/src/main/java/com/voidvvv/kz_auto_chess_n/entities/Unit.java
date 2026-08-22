package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.UnitData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 名单实体（architecture §2.1）：模板引用 + 星级 + 累计花费 + 装备三槽。
 *
 * <p>Phase 5（CP5）起受控可变：升星 / spend / 穿脱为 framework-internal 写方法
 * （仅供 systems 包命令结算调用，渲染层只读——沿 {@link BattleUnit} 纪律）。
 *
 * <p>id 由注入的 {@link IdIssuer} 发号（单一 int id 空间，architecture §2.2）；
 * 模板直接引用（模板加载一次终身只读，沿 {@link WaveSpec} 先例）。
 */
public final class Unit {
    private final int id;
    private final UnitData template;
    /** 1~3（3 合 1 上限 3 星），构造校验；3 合 1 升星经 framework-internal upgradeStar */
    private int star;
    /** 累计花费（GDD §3.6 卖出 100% 返还；买入累加、合并折叠） */
    private int spend;
    /** 已穿装备 ≤ EQUIP_SLOTS_PER_UNIT(3)，槽位唯一（武器/盔甲/饰品各一） */
    private final List<Equipment> equipped = new ArrayList<Equipment>();

    public Unit(int id, UnitData template, int star) {
        this(id, template, star, 0);
    }

    public Unit(int id, UnitData template, int star, int spend) {
        if (star < 1 || star > 3) {
            throw new IllegalArgumentException("星级必须在 1~3（3 合 1 上限 3 星），实际=" + star);
        }
        if (spend < 0) {
            throw new IllegalArgumentException("累计花费必须 ≥ 0，实际=" + spend);
        }
        this.id = id;
        this.template = Objects.requireNonNull(template, "template 不能为 null");
        this.star = star;
        this.spend = spend;
    }

    public int getId() { return id; }
    public UnitData getTemplate() { return template; }
    public int getStar() { return star; }
    public int getSpend() { return spend; }

    /** 已穿装备（不可变视图，穿着序） */
    public List<Equipment> getEquipped() {
        return Collections.unmodifiableList(equipped);
    }

    /** 指定槽位已穿装备；空槽返回 null */
    public Equipment equippedIn(EquipmentSlot slot) {
        for (Equipment item : equipped) {
            if (item.getTemplate().getSlot() == slot) {
                return item;
            }
        }
        return null;
    }

    // —— framework-internal 写方法（仅供 systems 包命令结算调用，渲染层只读；沿 BattleUnit 纪律） ——

    /** 3 合 1 升星（star+1；已是 3 星抛错——调用方保证 ≤2 星才合） */
    public void upgradeStar() {
        if (star >= 3) {
            throw new IllegalStateException("3 星上限，不可再合: " + template.getId());
        }
        star++;
    }

    /** 累计花费累加（买入花费 / 合并折叠，增量 ≥ 0） */
    public void addSpend(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("花费增量必须 ≥ 0，实际=" + amount);
        }
        spend += amount;
    }

    /** 穿戴：满 3 件或同槽已穿抛 IllegalStateException */
    public void equip(Equipment item) {
        Objects.requireNonNull(item, "item 不能为 null");
        if (equipped.size() >= GameBalance.EQUIP_SLOTS_PER_UNIT) {
            throw new IllegalStateException("装备槽已满（" + GameBalance.EQUIP_SLOTS_PER_UNIT + " 件）: " + template.getId());
        }
        if (equippedIn(item.getTemplate().getSlot()) != null) {
            throw new IllegalStateException("槽位已占用: " + item.getTemplate().getSlot());
        }
        equipped.add(item);
    }

    /** 卸下（未穿戴在此棋子抛 IllegalArgumentException） */
    public void unequip(Equipment item) {
        if (!equipped.remove(item)) {
            throw new IllegalArgumentException("该装备未穿戴在此棋子: " + (item == null ? "null" : item.getId()));
        }
    }

    /** id 空间全局唯一，身份即 id（模板与星级不参与判等——升星不变身份） */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Unit)) {
            return false;
        }
        return id == ((Unit) o).id;
    }

    @Override
    public int hashCode() {
        return 31 * 17 + id;
    }
}
