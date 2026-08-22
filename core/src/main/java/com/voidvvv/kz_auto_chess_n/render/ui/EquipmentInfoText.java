package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentPassive;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.StatusType;

import java.util.ArrayList;
import java.util.List;

/**
 * 装备文案格式化纯函数（feedback07）：详情弹窗装备行 / 背包格悬停卡 / 宝箱选项行
 * 三展示点共用同一文案源（用户裁决：禁止三处各写一份格式化）。
 * 零 Gdx 依赖，headless 可测（TDD 先行）。
 *
 * <p>词汇表与 {@link UnitInfoText} 同源：statLabel/numberText 复用、百分比刻度键附 %，
 * 装备 effects 与 synergies 共用 {stat, op, value} 词汇（data_schema §八）。
 * 被动文案对齐 GDD §5.2 龙心行原文（「每 5 秒回复 2% 最大生命」），加「被动：」前缀
 * 区分属性条目；空格为 {@link UnitInfoText#wrap} 的断点服务。
 */
public final class EquipmentInfoText {

    private EquipmentInfoText() {
    }

    /** 稀有度文案（GDD §5.2 白/成/传；词表名 WHITE/RARE/LEGENDARY 见 WARNING-2） */
    public static String rarityLabel(EquipmentRarity rarity) {
        switch (rarity) {
            case LEGENDARY:
                return "传说";
            case RARE:
                return "成装";
            case WHITE:
            default:
                return "白装";
        }
    }

    /** 槽位文案（GDD §5.2 武器/盔甲/饰品；背包格内短形武/甲/饰为 InventoryPanel.slotMark 既有现状） */
    public static String slotLabel(EquipmentSlot slot) {
        switch (slot) {
            case WEAPON:
                return "武器";
            case ARMOR:
                return "盔甲";
            case TRINKET:
            default:
                return "饰品";
        }
    }

    /** 单条效果 → 数值文案（与 UnitInfoText.effectText 同词汇）：PCT → 标签+v%；ADD → 标签+v（百分比刻度键附 %） */
    public static String effectText(EquipmentEffect effect) {
        String label = UnitInfoText.statLabel(effect.getStat());
        if (effect.getOp() == EffectOp.PCT) {
            return label + "+" + UnitInfoText.numberText(effect.getValue()) + "%";
        }
        String suffix = effect.getStat().isPercentScale() ? "%" : "";
        return label + "+" + UnitInfoText.numberText(effect.getValue()) + suffix;
    }

    /** 被动文案（本期仅 REGEN——JsonLoader 加载期已限制；power = maxHp 比例/跳、tickInterval = 秒/跳，
     *  StatusSystem 心跳落地语义）。非 REGEN 回退词表名（防御不炸，正常路径不可达） */
    public static String passiveText(EquipmentPassive passive) {
        if (passive.getType() == StatusType.REGEN) {
            return "被动：每 " + UnitInfoText.numberText(passive.getTickInterval())
                    + " 秒 回复 " + Math.round(passive.getPower() * 100f) + "% 最大生命";
        }
        return "被动：" + passive.getType().jsonName();
    }

    /** 效果条目行集（悬停卡/宝箱行形态）：各效果一行 + 被动一行；无任何条目 = 空列表 */
    public static List<String> effectEntries(EquipmentData template) {
        List<String> entries = new ArrayList<String>();
        for (EquipmentEffect effect : template.getEffects()) {
            entries.add(effectText(effect));
        }
        if (template.getPassive() != null) {
            entries.add(passiveText(template.getPassive()));
        }
        return entries;
    }

    /** 效果摘要单串（详情弹窗形态）：条目 " · " 连接；无任何条目 = 空串 */
    public static String effectSummary(EquipmentData template) {
        StringBuilder sb = new StringBuilder();
        for (String entry : effectEntries(template)) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(entry);
        }
        return sb.toString();
    }

    /** 悬停卡行集：名 → 稀有度·槽位 → 效果条目（模板级，调用方折行/截断） */
    public static List<String> lines(EquipmentData template) {
        List<String> lines = new ArrayList<String>();
        lines.add(template.getName());
        lines.add(rarityLabel(template.getRarity()) + "·" + slotLabel(template.getSlot()));
        lines.addAll(effectEntries(template));
        return lines;
    }
}
