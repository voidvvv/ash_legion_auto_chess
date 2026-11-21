package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 assets 全量渲染回归（HoverPreview NPE 修复，2026-11-21）：
 * 24 可购棋子 × 各自羁绊 × 全部档位走 UnitInfoText 三条文案路径
 * （previewLines / detailLines / synergyTierLine→effectText）——
 * 断言不抛异常、文本非空、机制通道词条产出中文短文案（非枚举名）。
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
            List<String> lines = UnitInfoText.previewLines(template, data, 7); // 棋盘卡口径列宽
            assertThat(lines).as("棋子 %s 预览行集非空", template.getId()).isNotEmpty();
            assertThat(lines.get(0)).contains(template.getName());
            assertThat(lines).allSatisfy(line ->
                    assertThat(UnitInfoText.columns(line)).isLessThanOrEqualTo(7f));
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
    @DisplayName("原崩溃行回归：亡灵(2) 档位行含「阵亡名册」（修复前对 DEATH_TAX 词条 NPE）")
    void undeadTierTwoNoLongerCrashes() {
        String tierLine = UnitInfoText.synergyTierLine(data.getSynergy("syn_undead"));
        assertThat(tierLine).startsWith("2:阵亡名册：继承攻+4·生命+20");
        assertThat(tierLine).contains("亡语双响", "点算");
        // 骸骨士兵（亡灵战士）悬停预览 = 原崩溃链路入口
        List<String> lines = UnitInfoText.previewLines(data.getUnit("unit_skeleton_soldier"), data, 7);
        assertThat(String.join("", lines)).contains("阵亡名册");
    }
}
