package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.EquipmentData;

import java.util.Objects;

/**
 * 装备实体（architecture §2.2 单一 int id 空间的第二类实体；Q1 裁决 A）：
 * id 由 IdIssuer 发号（与棋子共用）、模板直接引用。完全不可变。
 */
public final class Equipment {
    private final int id;
    private final EquipmentData template;

    public Equipment(int id, EquipmentData template) {
        this.id = id;
        this.template = Objects.requireNonNull(template, "template 不能为 null");
    }

    public int getId() { return id; }
    public EquipmentData getTemplate() { return template; }

    /** id 空间全局唯一，身份即 id */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Equipment)) {
            return false;
        }
        return id == ((Equipment) o).id;
    }

    @Override
    public int hashCode() {
        return 31 * 17 + id;
    }
}
