package com.voidvvv.kz_auto_chess_n.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 装备模板（不可变；加载一次终身只读，沿 UnitData 先例） */
public final class EquipmentData {
    private final String id;
    private final String name;
    private final EquipmentSlot slot;
    private final EquipmentRarity rarity;
    private final List<EquipmentEffect> effects;
    private final EquipmentPassive passive; // 可 null

    public EquipmentData(String id, String name, EquipmentSlot slot, EquipmentRarity rarity,
                         List<EquipmentEffect> effects, EquipmentPassive passive) {
        this.id = Objects.requireNonNull(id, "id 不能为 null");
        this.name = Objects.requireNonNull(name, "name 不能为 null");
        this.slot = Objects.requireNonNull(slot, "slot 不能为 null");
        this.rarity = Objects.requireNonNull(rarity, "rarity 不能为 null");
        this.effects = Collections.unmodifiableList(
                new ArrayList<EquipmentEffect>(Objects.requireNonNull(effects, "effects 不能为 null")));
        this.passive = passive;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public EquipmentSlot getSlot() { return slot; }
    public EquipmentRarity getRarity() { return rarity; }
    public List<EquipmentEffect> getEffects() { return effects; }
    /** 可 null：无被动 */
    public EquipmentPassive getPassive() { return passive; }
}
