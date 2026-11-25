package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.EffectTarget;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.SynergySource;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 抽屉信息区文案（UI 抽屉化批2：选中棋子 → 标题板写名、描述框写介绍；行集复用 {@link UnitInfoText}）。
 * 纯函数、零 Gdx。
 *
 * <p>容量口径：描述滚动视口 {@link DrawerGeometry#descriptionViewport()} 宽 × 高，9px 小字（0.75×12）→ 全角
 * {@link #DESC_MAX_COLUMNS} 列；不滚动可见 {@link #DESC_VISIBLE_LINES} 行，<b>超出由 ScrollPane 滚到底、不截断</b>
 * （批2 过渡态的「前 5 行 + …」已退役）。标题板 232 宽 / 12px 字 → 全角 {@link #TITLE_MAX_COLUMNS} 列。
 */
public final class DrawerInfoText {

    /** 描述框净宽 112px（120 − 左右各 4）÷ 9px = 全角 12 列 */
    public static final int DESC_MAX_COLUMNS = 12;
    /** 描述滚动视口净高 60px 不滚动时可见的行数（超出靠 ScrollPane 滚动——批3 硬验收，不再截断） */
    public static final int DESC_VISIBLE_LINES = 6;
    /** 标题板净宽 220px（232 − 左右各 6）÷ 12px → 全角 18 列 */
    public static final int TITLE_MAX_COLUMNS = 18;

    static final String NO_SELECTION_TITLE = "未选中棋子";
    static final String NO_SELECTION_INTRO = "（选中棋子后显示介绍）";
    /** 羁绊页未选中占位（批7 三页签） */
    static final String NO_SELECTION_SYNERGY = "（选中棋子后查看羁绊）";
    /** 羁绊页：种族/职业均未登记羁绊的空态（缺资源） */
    static final String NO_SYNERGY_REGISTERED = "（该棋子种族/职业暂无登记羁绊）";

    private DrawerInfoText() {
    }

    /**
     * 「自身」页行集（批7 三页签：<b>原始逻辑行、不折行</b>——换行由 DrawerTextBlock 以 GlyphLayout 实测宽度执行）：
     * 棋子身份（名/星/费/累计花费）→ 基础属性 → 自身技能。<b>不含</b>种族/职业羁绊说明（迁往羁绊页）。
     * 未选中 → 空列表（调用方画占位行）。
     */
    public static List<String> selfLines(Unit unit, GameData data) {
        if (unit == null) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        UnitData template = unit.getTemplate();
        BaseStats stats = template.getBaseStats();
        lines.add(template.getName() + " " + unit.getStar() + "星（" + template.getCost()
                + "费）· 累计花费 " + unit.getSpend());
        lines.add("生命 " + UnitInfoText.numberText(stats.getHp()) + "   攻击 " + UnitInfoText.numberText(stats.getAttack())
                + "   护甲 " + UnitInfoText.numberText(stats.getArmor()));
        lines.add("攻速 " + UnitInfoText.numberText(stats.getAttackSpeed()) + "   射程 " + UnitInfoText.numberText(stats.getRange())
                + "   移速 " + UnitInfoText.numberText(stats.getMoveSpeed()));
        SkillData skill = data.getSkill(template.getSkillId());
        if (skill != null) {
            lines.add("技能 " + skill.getName() + "：" + skill.getDesc());
        }
        return lines;
    }

    /**
     * 「羁绊」页行集（批7 三页签：<b>原始逻辑行、不折行</b>）：该棋子所属的种族/职业羁绊——
     * 每条羁绊 = 标题行（来源标记 + 名）→ 主题描述 → <b>全部档位</b>效果详情行（N只：效果·效果）。
     * 未登记的种族/职业不显示；两条都未登记 → 空列表（调用方画空态行）。
     */
    public static List<String> synergyLines(Unit unit, GameData data) {
        if (unit == null) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        UnitData template = unit.getTemplate();
        appendSynergyPage(lines, data, SynergySource.RACE, template.getRace());
        appendSynergyPage(lines, data, SynergySource.CLASS, template.getUnitClass());
        return lines;
    }

    private static void appendSynergyPage(List<String> lines, GameData data, SynergySource source, String key) {
        SynergyData synergy = UnitInfoText.findSynergy(data, source, key);
        String kind = source == SynergySource.RACE ? "种族" : "职业";
        if (synergy == null) {
            lines.add(kind + "·" + key + "（暂无羁绊）");
            return;
        }
        lines.add(kind + "羁绊·" + synergy.getName());
        String desc = synergy.getDesc();
        if (desc != null && !desc.isEmpty()) {
            lines.add(synergyDescription(desc));
        }
        for (SynergyData.Threshold tier : synergy.getThresholds()) {
            lines.add(tierLine(tier));
        }
    }

    /** 档位详情行：N只：效果·效果（效果文案复用 {@link UnitInfoText#effectText} 三通道词表） */
    private static String tierLine(SynergyData.Threshold tier) {
        StringBuilder sb = new StringBuilder();
        sb.append(tier.getCount()).append("只：");
        for (int i = 0; i < tier.getEffects().size(); i++) {
            if (i > 0) {
                sb.append("·");
            }
            sb.append(synergyEffectText(tier.getEffects().get(i)));
        }
        return sb.toString();
    }

    static String synergyDescription(String desc) {
        return desc.replace("——", "：");
    }

    static String synergyEffectText(EffectData effect) {
        String label = UnitInfoText.effectText(effect);
        return effect.getTarget() == EffectTarget.TRAITS && effect.getTrait() != null
                ? label + "（仅" + effect.getTrait() + "）" : label;
    }

    /** 未选中时的羁绊页占位行 */
    public static List<String> noSelectionSynergy() {
        return Collections.singletonList(NO_SELECTION_SYNERGY);
    }

    /** 两条羁绊都未登记时的羁绊页空态行（缺资源） */
    public static List<String> noSynergyRegistered() {
        return Collections.singletonList(NO_SYNERGY_REGISTERED);
    }

    /** 标题板文案：「名 N星」；未选中 → 占位标题（超宽按列截断） */
    public static String title(Unit unit) {
        if (unit == null) {
            return NO_SELECTION_TITLE;
        }
        return UnitInfoText.truncateColumns(
                unit.getTemplate().getName() + " " + unit.getStar() + "星", TITLE_MAX_COLUMNS);
    }

    /**
     * 「介绍」标签页行集（<b>全量、不截断</b>——批3：描述框改 ScrollPane，最长真实文案滚到底）：
     * {@link UnitInfoText#detailLines}（名/星/费/累计花费 → 属性 → 技能 → 双羁绊）逐行折到 {@link #DESC_MAX_COLUMNS} 列。
     * 未选中 → 空列表（调用方改画占位行 {@link #noSelectionIntro()}）。
     */
    public static List<String> introLines(Unit unit, GameData data) {
        if (unit == null) {
            return Collections.emptyList();
        }
        List<String> wrapped = new ArrayList<String>();
        for (String line : UnitInfoText.detailLines(unit, data)) {
            wrapped.addAll(UnitInfoText.wrap(line, DESC_MAX_COLUMNS));
        }
        return wrapped;
    }

    /** 未选中时的介绍占位行 */
    public static List<String> noSelectionIntro() {
        return Collections.singletonList(NO_SELECTION_INTRO);
    }

    /** 收起态角标文案「后备 n/9」（n = 备战席条目数，钳到 [0, BENCH_SIZE]） */
    public static String badgeText(int benchCount) {
        int count = Math.max(0, Math.min(benchCount, GameBalance.BENCH_SIZE));
        return "后备 " + count + "/" + GameBalance.BENCH_SIZE;
    }
}
