package com.voidvvv.kz_auto_chess_n.data;

import java.util.Collections;
import java.util.List;

/**
 * 具名技能（data_schema §五 skills.json）。
 *
 * <p>定义 = 目标形状（shape）× 效果列表（effects ≤ 3）× 载体（delivery）三维组合；
 * 多单位可共享同一技能（units 以 skillId 引用）。执行模型见 battle_design §六。
 */
public final class SkillData {
    private final String id;
    private final String name;
    private final String desc;
    private final SkillShape shape;
    private final Delivery delivery;
    private final List<SkillEffect> effects;

    public SkillData(String id, String name, String desc, SkillShape shape,
                     Delivery delivery, List<SkillEffect> effects) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.shape = shape;
        this.delivery = delivery;
        this.effects = Collections.unmodifiableList(effects);
    }

    /** 技能 id，兼作图标/特效 key（fx_{skillId}，render §5.4） */
    public String getId() { return id; }
    public String getName() { return name; }
    /** 详情面板 / 图鉴用描述 */
    public String getDesc() { return desc; }
    public SkillShape getShape() { return shape; }
    /** 缺省 MELEE_INSTANT（data_schema §5.2） */
    public Delivery getDelivery() { return delivery; }
    /** 1~3 条，多条依次应用（如暴走 = 双状态） */
    public List<SkillEffect> getEffects() { return effects; }
}
