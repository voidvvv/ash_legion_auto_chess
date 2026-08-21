package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.UnitData;

import java.util.Objects;

/**
 * 名单实体（architecture §2.1）：模板引用 + 星级。本期完全不可变——
 * 装备三槽（Q4 裁决：修正源列表化，Unit 不含装备字段）与升星替换
 * （3 合 1 为 Phase 5 BuyUnit 的系统后果）均推迟。
 *
 * <p>id 由注入的 {@link IdIssuer} 发号（单一 int id 空间，architecture §2.2）；
 * 模板直接引用（模板加载一次终身只读，沿 {@link WaveSpec} 先例）。
 */
public final class Unit {
    private final int id;
    private final UnitData template;
    /** 1~3（3 合 1 上限 3 星），构造校验 */
    private final int star;

    public Unit(int id, UnitData template, int star) {
        if (star < 1 || star > 3) {
            throw new IllegalArgumentException("星级必须在 1~3（3 合 1 上限 3 星），实际=" + star);
        }
        this.id = id;
        this.template = Objects.requireNonNull(template, "template 不能为 null");
        this.star = star;
    }

    public int getId() { return id; }
    public UnitData getTemplate() { return template; }
    public int getStar() { return star; }

    /** id 空间全局唯一，身份即 id（模板与星级不参与判等） */
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
