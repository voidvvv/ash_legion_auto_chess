package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierBlock;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierSource;

import java.util.List;
import java.util.Objects;

/**
 * 装备属性修正源（Phase 3 Q4 修正源列表的第二个实现）：该单位所穿装备 effects 的 ΣADD/ΣPCT。
 * 作用域 = 单体（羁绊快照为侧全体）；StatPipeline.deriveBaseline 结算器零改动。
 */
public final class EquipmentStats implements StatModifierSource {

    public static final EquipmentStats EMPTY = new EquipmentStats(StatModifierBlock.empty());

    private final StatModifierBlock block;

    private EquipmentStats(StatModifierBlock block) {
        this.block = Objects.requireNonNull(block, "block 不能为 null");
    }

    /** 装备列表 → 合并修正块（空列表/零修正返回 EMPTY 单例） */
    public static EquipmentStats of(List<Equipment> equipped) {
        Objects.requireNonNull(equipped, "equipped 不能为 null");
        StatModifierBlock merged = StatModifierBlock.empty();
        for (Equipment item : equipped) {
            for (EquipmentEffect effect : item.getTemplate().getEffects()) {
                merged = merged.plus(StatModifierBlock.of(effect.getStat(), effect.getOp(), effect.getValue()));
            }
        }
        return merged.isEmpty() ? EMPTY : new EquipmentStats(merged);
    }

    @Override
    public StatModifierBlock modifiers() {
        return block;
    }
}
