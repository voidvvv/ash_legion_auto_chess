package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.Unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 抽屉信息区文案（UI 抽屉化批2：选中棋子 → 标题板写名、描述框写介绍；行集复用 {@link UnitInfoText}）。
 * 纯函数、零 Gdx。
 *
 * <p>容量口径：描述框 {@link DrawerGeometry#DESC_W} 宽 × 64 高，9px 小字（0.75×12）→ 全角
 * {@link #DESC_MAX_COLUMNS} 列 × {@link #DESC_MAX_LINES} 行；超出以 … 收尾（完整滚动见批3 的 ScrollPane）。
 * 标题板 232 宽 / 12px 字 → 全角 {@link #TITLE_MAX_COLUMNS} 列。
 */
public final class DrawerInfoText {

    /** 描述框净宽 112px（120 − 左右各 4）÷ 9px = 全角 12 列 */
    public static final int DESC_MAX_COLUMNS = 12;
    /** 描述框净高 58px（64 − 上留白 4）÷ 9px 行高 → 6 行 */
    public static final int DESC_MAX_LINES = 6;
    /** 标题板净宽 220px（232 − 左右各 6）÷ 12px → 全角 18 列 */
    public static final int TITLE_MAX_COLUMNS = 18;

    static final String NO_SELECTION_TITLE = "未选中棋子";
    static final String NO_SELECTION_INTRO = "（选中棋子后显示介绍）";

    private DrawerInfoText() {
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
     * 「介绍」标签页行集：{@link UnitInfoText#detailLines}（名/星/费/累计花费 → 属性 → 技能 → 双羁绊）
     * 逐行折到 {@link #DESC_MAX_COLUMNS} 列，再截到 {@link #DESC_MAX_LINES} 行（超出 … 收尾）。
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
        return UnitInfoText.clipLines(wrapped, DESC_MAX_LINES);
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
