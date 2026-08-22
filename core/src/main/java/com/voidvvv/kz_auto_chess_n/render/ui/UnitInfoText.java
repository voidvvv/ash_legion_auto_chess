package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.SynergySource;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * 棋子文案格式化纯函数（Phase 5.1 R1/R2）：悬停预览卡（棋盘域 + 商店卡）与详情弹窗
 * 共用的行集拼装——零 Gdx 依赖，headless 可测（TDD 先行）。
 *
 * <p>术语表见计划 §2.1（属性短名与 GDD 全称一一对应）。档位数值行由
 * thresholds/effects 结构化数据生成（R2 混合口径：synergy 级 desc 手写 + 档位行生成）。
 * 折行列宽口径：全角（含 CJK）= 1 列、半角 = 0.5 列（Fusion Pixel 12px 实测）。
 */
public final class UnitInfoText {

    private UnitInfoText() {
    }

    // —— 词表（§2.1 属性行短形；档位行共用） ——

    /** 属性短名（悬停卡/详情弹窗属性行与档位行共用） */
    public static String statLabel(StatKey key) {
        switch (key) {
            case HP: return "生命";
            case ATTACK: return "攻击";
            case ARMOR: return "护甲";
            case ATTACK_SPEED: return "攻速";
            case MOVE_SPEED: return "移速";
            case RANGE: return "射程";
            case LIFESTEAL: return "吸血";
            case ENERGY_GAIN_RATE: return "回能";
            case SKILL_POWER:
            default: return "技能强度";
        }
    }

    /** 单条档位效果 → 数值文案：ADD → +v（百分比刻度键附 %）；PCT → +v%；SHIELD → 护盾NN% */
    static String effectText(EffectData effect) {
        if (!effect.isStatChannel()) {
            if (effect.getEffect() == StatusType.SHIELD) {
                return "护盾" + Math.round(effect.getValue() * 100f) + "%";
            }
            return effect.getEffect().jsonName(); // 未知特殊效果：保守回退词表名（不炸）
        }
        String label = statLabel(effect.getStat());
        if (effect.getOp() == EffectOp.PCT) {
            return label + "+" + numberText(effect.getValue()) + "%";
        }
        String suffix = effect.getStat().isPercentScale() ? "%" : "";
        return label + "+" + numberText(effect.getValue()) + suffix;
    }

    /** 羁绊档位行（结构化生成，R2 混合口径）：如 2:生命+150；4:生命+400·攻击+20% */
    public static String synergyTierLine(SynergyData synergy) {
        StringBuilder sb = new StringBuilder();
        for (SynergyData.Threshold tier : synergy.getThresholds()) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(tier.getCount()).append(":");
            for (int i = 0; i < tier.getEffects().size(); i++) {
                if (i > 0) {
                    sb.append("·");
                }
                sb.append(effectText(tier.getEffects().get(i)));
            }
        }
        return sb.toString();
    }

    /** 按 source+key 找羁绊（unit race/class → 登记表）；未登记返回 null（风味标签，§六 V1.3） */
    public static SynergyData findSynergy(GameData data, SynergySource source, String key) {
        for (SynergyData synergy : data.getSynergies().values()) {
            if (synergy.getSource() == source && synergy.getKey().equals(key)) {
                return synergy;
            }
        }
        return null;
    }

    // —— 悬停预览行集（棋盘域 + 商店卡共用；模板级，不含 spend/已穿装备——R1） ——

    /**
     * 预览行集（按信息优先序）：名/费阶 → 属性 3 行 → 技能名 → 技能 desc →
     * 羁绊块（名 + desc + 档位行）×≤2（未登记的种族/职业不显示）。
     *
     * @param maxColumns 行宽上限（列；≤0 = 不折行）
     */
    public static List<String> previewLines(UnitData template, GameData data, int maxColumns) {
        List<String> lines = new ArrayList<String>();
        BaseStats stats = template.getBaseStats();
        addWrapped(lines, template.getName() + " " + template.getCost() + "费", maxColumns);
        addWrapped(lines, statPair("生命", stats.getHp(), "攻击", stats.getAttack()), maxColumns);
        addWrapped(lines, statPair("护甲", stats.getArmor(), "攻速", stats.getAttackSpeed()), maxColumns);
        addWrapped(lines, statPair("射程", stats.getRange(), "移速", stats.getMoveSpeed()), maxColumns);
        SkillData skill = data.getSkill(template.getSkillId());
        if (skill != null) {
            addWrapped(lines, "技能 " + skill.getName(), maxColumns);
            addWrapped(lines, skill.getDesc(), maxColumns);
        }
        appendSynergyPreview(lines, findSynergy(data, SynergySource.RACE, template.getRace()), maxColumns);
        appendSynergyPreview(lines, findSynergy(data, SynergySource.CLASS, template.getUnitClass()), maxColumns);
        return lines;
    }

    private static void appendSynergyPreview(List<String> lines, SynergyData synergy, int maxColumns) {
        if (synergy == null) {
            return;
        }
        addWrapped(lines, "羁绊 " + synergy.getName(), maxColumns);
        addWrapped(lines, descOrEmpty(synergy), maxColumns);
        addWrapped(lines, synergyTierLine(synergy), maxColumns);
    }

    // —— 详情弹窗行集（实例级：星/spend + 属性 + 技能 + 两条羁绊；装备区由弹窗按钮行承担） ——

    public static List<String> detailLines(Unit unit, GameData data) {
        List<String> lines = new ArrayList<String>();
        UnitData template = unit.getTemplate();
        BaseStats stats = template.getBaseStats();
        lines.add(template.getName() + " " + unit.getStar() + "星（" + template.getCost()
                + "费）· 累计花费 " + unit.getSpend());
        lines.add("生命 " + numberText(stats.getHp()) + "   攻击 " + numberText(stats.getAttack())
                + "   护甲 " + numberText(stats.getArmor()));
        lines.add("攻速 " + numberText(stats.getAttackSpeed()) + "   射程 " + numberText(stats.getRange())
                + "   移速 " + numberText(stats.getMoveSpeed()));
        SkillData skill = data.getSkill(template.getSkillId());
        if (skill != null) {
            lines.add("技能 " + skill.getName() + "：" + skill.getDesc());
        }
        appendSynergyDetail(lines, findSynergy(data, SynergySource.RACE, template.getRace()));
        appendSynergyDetail(lines, findSynergy(data, SynergySource.CLASS, template.getUnitClass()));
        return lines;
    }

    private static void appendSynergyDetail(List<String> lines, SynergyData synergy) {
        if (synergy == null) {
            return;
        }
        String desc = descOrEmpty(synergy);
        lines.add("羁绊 " + synergy.getName() + (desc.isEmpty() ? "" : "：" + desc));
        lines.add(synergyTierLine(synergy));
    }

    // —— 数值与折行（纯函数，headless 可测） ——

    /** 数值显示：整数去小数尾（1.0 → 1、20 → 20），非整数保留（0.3 → 0.3、1.5 → 1.5） */
    static String numberText(float value) {
        float rounded = Math.round(value * 100f) / 100f; // 浮点尾差收口（0.3f*100 → 30.000001 → 0.3）
        if (rounded == Math.floor(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.valueOf(rounded);
    }

    private static String statPair(String labelA, float valueA, String labelB, float valueB) {
        return labelA + numberText(valueA) + " " + labelB + numberText(valueB);
    }

    private static String descOrEmpty(SynergyData synergy) {
        String desc = synergy.getDesc();
        return desc == null ? "" : desc;
    }

    private static void addWrapped(List<String> lines, String text, int maxColumns) {
        if (text != null && !text.isEmpty()) {
            lines.addAll(wrap(text, maxColumns));
        }
    }

    /** 空格断点贪心折行；单 token 超行宽按字符硬断；maxColumns ≤ 0 或整行已容纳 = 原样单行 */
    public static List<String> wrap(String text, int maxColumns) {
        List<String> result = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }
        if (maxColumns <= 0 || columns(text) <= maxColumns) {
            result.add(text);
            return result;
        }
        StringBuilder current = new StringBuilder();
        float used = 0f;
        for (String token : text.split(" ")) {
            if (token.isEmpty()) {
                continue;
            }
            float width = columns(token);
            if (current.length() > 0 && used + 0.5f + width > maxColumns) {
                result.add(current.toString());
                current.setLength(0);
                used = 0f;
            }
            if (width > maxColumns) { // 单 token 超行宽：字符硬断
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                    used = 0f;
                }
                hardSplit(token, maxColumns, result);
                continue;
            }
            if (current.length() > 0) {
                current.append(' ');
                used += 0.5f;
            }
            current.append(token);
            used += width;
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private static void hardSplit(String token, int maxColumns, List<String> out) {
        int start = 0;
        while (start < token.length()) {
            int end = start;
            float width = 0f;
            while (end < token.length() && width + charColumns(token.charAt(end)) <= maxColumns) {
                width += charColumns(token.charAt(end));
                end++;
            }
            if (end == start) { // 防零推进（调用方保证 maxColumns ≥ 1）
                end = start + 1;
            }
            out.add(token.substring(start, end));
            start = end;
        }
    }

    /** 列宽估算：全角（含 CJK 与全角标点）= 1 列，半角 = 0.5 列（12px 字体实测口径） */
    public static float columns(String text) {
        float width = 0f;
        for (int i = 0; i < text.length(); i++) {
            width += charColumns(text.charAt(i));
        }
        return width;
    }

    private static float charColumns(char c) {
        return c > 0xFF ? 1f : 0.5f;
    }

    /** 卡高截断（§5.3-4）：超容量行丢弃、末行以 … 示意；capacity ≤ 0 或未超 = 原样返回 */
    public static List<String> clipLines(List<String> lines, int capacity) {
        if (capacity <= 0 || lines.size() <= capacity) {
            return lines;
        }
        List<String> clipped = new ArrayList<String>(lines.subList(0, Math.max(1, capacity - 1)));
        clipped.add("…");
        return clipped;
    }
}
