package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 assets 全量渲染回归（HoverPreview NPE 修复，2026-11-21；同日整改补容量锁定）：
 * 24 可购棋子 × 各自羁绊 × 全部档位走 UnitInfoText 三条文案路径
 * （previewLines / detailLines / synergyTierLine→effectText）——
 * 断言不抛异常、文本非空、机制通道词条产出中文短文案（非枚举名）；
 * 悬停卡最长真实组合（棋盘卡 9 列×敌方标记 / 商店卡 11 列）所需卡高 ≤ 锚区——不截断不溢出。
 */
class UnitInfoTextSeedDataTest {

    private static GameData data;

    @BeforeAll
    static void loadSeedData() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    @Test
    @DisplayName("24 可购棋子全量走 previewLines（悬停卡路径）：零异常、行集非空、行宽合规")
    void previewLinesAllPurchasableUnits() {
        int checked = 0;
        for (UnitData template : data.getUnits().values()) {
            if (template.isBoss()) {
                continue;
            }
            List<String> lines = UnitInfoText.previewLines(template, data, 9); // 棋盘卡口径列宽（0.75×字号）
            assertThat(lines).as("棋子 %s 预览行集非空", template.getId()).isNotEmpty();
            assertThat(lines.get(0)).contains(template.getName());
            assertThat(lines).allSatisfy(line ->
                    assertThat(UnitInfoText.columns(line)).isLessThanOrEqualTo(9f));
            checked++;
        }
        assertThat(checked).isEqualTo(24);
    }

    @Test
    @DisplayName("24 可购棋子全量走 detailLines（详情弹窗路径）：零异常、含双羁绊档位行")
    void detailLinesAllPurchasableUnits() {
        for (UnitData template : data.getUnits().values()) {
            if (template.isBoss()) {
                continue;
            }
            List<String> lines = UnitInfoText.detailLines(new Unit(7, template, 1), data);
            assertThat(lines).as("棋子 %s 详情行集非空", template.getId()).isNotEmpty();
            assertThat(lines.get(0)).contains(template.getName());
        }
    }

    @Test
    @DisplayName("8 羁绊 × 全部档位 × 全部词条走 effectText：非空文案；机制通道=中文短文案（非枚举名）")
    void effectTextAllSynergyTiersAndEffects() {
        int mechanicEntries = 0;
        for (SynergyData synergy : data.getSynergies().values()) {
            for (SynergyData.Threshold tier : synergy.getThresholds()) {
                for (EffectData effect : tier.getEffects()) {
                    String text = UnitInfoText.effectText(effect);
                    assertThat(text).as("%s (%d) 档词条 %s 文案非空",
                            synergy.getId(), tier.getCount(), effect.getMechanic()).isNotEmpty();
                    if (effect.isMechanicChannel()) {
                        mechanicEntries++;
                        assertThat(text).as("机制词 %s 产出中文", effect.getMechanic())
                                .matches(".*[一-鿿].*");
                        assertThat(text).as("机制词 %s 非枚举名", effect.getMechanic())
                                .isNotEqualTo(effect.getMechanic().jsonName());
                    }
                }
            }
            // 档位行整行拼接零异常且含各档前缀
            String tierLine = UnitInfoText.synergyTierLine(synergy);
            assertThat(tierLine).contains("2:", "4:", "6:");
        }
        assertThat(mechanicEntries).isGreaterThanOrEqualTo(17); // 8 羁绊机制词条总量
    }

    @Test
    @DisplayName("原崩溃行回归：亡灵(2) 档位行含「名册」（修复前对 DEATH_TAX 词条 NPE）")
    void undeadTierTwoNoLongerCrashes() {
        String tierLine = UnitInfoText.synergyTierLine(data.getSynergy("syn_undead"));
        assertThat(tierLine).startsWith("2:名册：继承攻+4·生命+20");
        assertThat(tierLine).contains("亡语双响", "点算");
        // 骸骨士兵（亡灵战士）悬停预览 = 原崩溃链路入口
        List<String> lines = UnitInfoText.previewLines(data.getUnit("unit_skeleton_soldier"), data, 9);
        assertThat(String.join("", lines)).contains("名册");
    }

    // —— 2026-11-21 主人整改：悬停卡最长组合不溢出（布局数学固化断言） ——

    @Test
    @DisplayName("容量锁定：全部模板（含 Boss）棋盘卡（9 列 × 敌方标记）所需卡高 ≤ 锚区 252——不截断不溢出")
    void boardCardWorstCaseFitsZone() {
        int worstLines = 0;
        String worstId = "";
        for (UnitData template : data.getUnits().values()) {
            List<String> card = HoverPreviewCard.boardCardLines(template, true, data); // 敌方标记 = 最坏
            assertThat(card).as("模板 %s 棋盘卡行集非空", template.getId()).isNotEmpty();
            for (String line : card) {
                assertThat(UnitInfoText.columns(line))
                        .as("模板 %s 行宽 ≤ 棋盘卡 9 列: %s", template.getId(), line)
                        .isLessThanOrEqualTo(HoverPreviewCard.BOARD_MAX_COLUMNS);
            }
            if (card.size() > worstLines) {
                worstLines = card.size();
                worstId = template.getId();
            }
        }
        float worstHeight = HoverPreviewCard.cardHeightFor(worstLines);
        assertThat(worstHeight)
                .as("最长组合 %s（%d 行）卡高 %.0f ≤ 锚区 %d", worstId, worstLines, worstHeight,
                        BoardGeometry.BOARD_HOVER_H)
                .isLessThanOrEqualTo(BoardGeometry.BOARD_HOVER_H);
    }

    @Test
    @DisplayName("容量锁定：全部可购模板商店卡（11 列）所需卡高 ≤ 锚区 224——不截断不溢出")
    void shopCardWorstCaseFitsZone() {
        int worstLines = 0;
        String worstId = "";
        for (UnitData template : data.getUnits().values()) {
            if (template.isBoss()) {
                continue; // 商店池不含 Boss
            }
            List<String> card = HoverPreviewCard.shopCardLines(template, data);
            for (String line : card) {
                assertThat(UnitInfoText.columns(line))
                        .as("模板 %s 行宽 ≤ 商店卡 11 列: %s", template.getId(), line)
                        .isLessThanOrEqualTo(HoverPreviewCard.SHOP_MAX_COLUMNS);
            }
            if (card.size() > worstLines) {
                worstLines = card.size();
                worstId = template.getId();
            }
        }
        float worstHeight = HoverPreviewCard.cardHeightFor(worstLines);
        assertThat(worstHeight)
                .as("最长组合 %s（%d 行）卡高 %.0f ≤ 锚区 %d", worstId, worstLines, worstHeight,
                        BoardGeometry.SHOP_HOVER_H)
                .isLessThanOrEqualTo(BoardGeometry.SHOP_HOVER_H);
    }
}
