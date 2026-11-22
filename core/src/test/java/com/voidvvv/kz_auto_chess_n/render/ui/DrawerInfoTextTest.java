package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 抽屉信息区文案（UI 抽屉化批2：选中棋子 → 标题板写名、描述框写介绍；复用 UnitInfoText）。
 * 容量口径：描述框 120 宽 × 64 高，9px 小字 → 全角 12 列 × 6 行；超出以 … 收尾（完整滚动见批3 ScrollPane）。
 * 真实 assets 全量回归（仿 UnitInfoTextSeedDataTest）：24 可购棋子 × 1~3 星的标题与介绍都必须装得下。
 */
class DrawerInfoTextTest {

    private static GameData data;

    @BeforeAll
    static void loadSeedData() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, false);
    }

    private static GameData emptyData() {
        return new GameData(new LinkedHashMap<String, UnitData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                new ArrayList<String>());
    }

    @Test
    @DisplayName("容量常量：描述 12 列 × 6 行（120 宽 / 64 高 / 9px 小字）、标题 18 列（232 宽 / 12px）")
    void capacityConstantsMatchPaneSizes() {
        assertThat(DrawerInfoText.DESC_MAX_COLUMNS).isEqualTo(12);
        assertThat(DrawerInfoText.DESC_MAX_LINES).isEqualTo(6);
        assertThat(DrawerInfoText.TITLE_MAX_COLUMNS).isEqualTo(18);
        assertThat(DrawerGeometry.DESC_W - 2 * 4).as("描述框净宽 ≥ 列数 × 9px（112 ≥ 108，余 4px）")
                .isGreaterThanOrEqualTo(DrawerInfoText.DESC_MAX_COLUMNS * 9);
        assertThat((DrawerGeometry.DESC_W - 8) / 9).as("净宽可容的全角列数 = 12").isEqualTo(DrawerInfoText.DESC_MAX_COLUMNS);
        assertThat(DrawerGeometry.descriptionPane().height()).isGreaterThanOrEqualTo(DrawerInfoText.DESC_MAX_LINES * 9);
        assertThat(DrawerGeometry.titlePlate().width() - 2 * DrawerGeometry.PADDING)
                .as("标题板净宽 ≥ 18 列 × 12px").isGreaterThanOrEqualTo(DrawerInfoText.TITLE_MAX_COLUMNS * 12);
    }

    @Test
    @DisplayName("未选中：标题「未选中棋子」、介绍 = 单行占位；选中：标题写「名 N星」、介绍 = detailLines 折到 12 列")
    void titleAndIntroForSelection() {
        assertThat(DrawerInfoText.title(null)).isEqualTo("未选中棋子");
        assertThat(DrawerInfoText.introLines(null, emptyData())).isEmpty();
        assertThat(DrawerInfoText.noSelectionIntro()).containsExactly("（选中棋子后显示介绍）");

        Unit unit = new Unit(5, tpl("u1"), 2);
        assertThat(DrawerInfoText.title(unit)).isEqualTo("夹具u1 2星");
        List<String> lines = DrawerInfoText.introLines(unit, emptyData());
        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0)).contains("夹具u1").contains("2星");
        for (String line : lines) {
            assertThat(UnitInfoText.columns(line)).as("行宽 ≤ 12 列: %s", line).isLessThanOrEqualTo(DrawerInfoText.DESC_MAX_COLUMNS);
        }
        assertThat(lines.size()).isLessThanOrEqualTo(DrawerInfoText.DESC_MAX_LINES);
    }

    @Test
    @DisplayName("角标文案：后备 n/9（n = 备战席条目数，越界钳制）")
    void badgeText() {
        assertThat(DrawerInfoText.badgeText(0)).isEqualTo("后备 0/9");
        assertThat(DrawerInfoText.badgeText(7)).isEqualTo("后备 7/9");
        assertThat(DrawerInfoText.badgeText(9)).isEqualTo("后备 9/9");
        assertThat(DrawerInfoText.badgeText(12)).isEqualTo("后备 9/9");
        assertThat(DrawerInfoText.badgeText(-1)).isEqualTo("后备 0/9");
    }

    @Test
    @DisplayName("真实 assets 全量：24 可购棋子 × 1~3 星——标题不溢出、介绍折行后 ≤ 6 行（超出 … 收尾，登记溢出棋子数）")
    void seedDataFitsTitleAndIntro() {
        int checked = 0;
        int overflow = 0;
        int maxRawLines = 0;
        for (UnitData template : data.getUnits().values()) {
            if (template.isBoss()) {
                continue;
            }
            for (int star = 1; star <= 3; star++) {
                Unit unit = new Unit(11, template, star);
                String title = DrawerInfoText.title(unit);
                assertThat(UnitInfoText.columns(title)).as("%s 标题 ≤ 18 列: %s", template.getId(), title)
                        .isLessThanOrEqualTo(DrawerInfoText.TITLE_MAX_COLUMNS);
                List<String> raw = new ArrayList<String>();
                for (String line : UnitInfoText.detailLines(unit, data)) {
                    raw.addAll(UnitInfoText.wrap(line, DrawerInfoText.DESC_MAX_COLUMNS));
                }
                maxRawLines = Math.max(maxRawLines, raw.size());
                if (raw.size() > DrawerInfoText.DESC_MAX_LINES) {
                    overflow++;
                }
                List<String> lines = DrawerInfoText.introLines(unit, data);
                assertThat(lines).as("%s %d星 介绍非空", template.getId(), star).isNotEmpty();
                assertThat(lines.size()).isLessThanOrEqualTo(DrawerInfoText.DESC_MAX_LINES);
                assertThat(lines).allSatisfy(line ->
                        assertThat(UnitInfoText.columns(line)).isLessThanOrEqualTo(DrawerInfoText.DESC_MAX_COLUMNS));
                if (raw.size() > DrawerInfoText.DESC_MAX_LINES) {
                    assertThat(lines.get(lines.size() - 1)).as("%s %d星 溢出以 … 收尾", template.getId(), star).isEqualTo("…");
                }
            }
            checked++;
        }
        assertThat(checked).isEqualTo(24);
        System.out.println("[DrawerInfoText] overflow(star-combinations)=" + overflow + "/72 maxRawWrappedLines=" + maxRawLines
                + " capacity=" + DrawerInfoText.DESC_MAX_LINES);
    }
}
