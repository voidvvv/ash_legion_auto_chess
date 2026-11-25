package com.voidvvv.kz_auto_chess_n.config;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v5 §4.4 棋子矩阵全量校验（真实 assets）：费阶 10/9/5、四种族×四职业行列各 6、
 * 传奇三子不入商店池、亡灵六子亡语/不死载荷、改名 4 例保 id。
 */
class UnitsMatrixTest {

    private static GameData data;

    @BeforeAll
    static void loadSeedData() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    private static List<UnitData> purchasable() {
        List<UnitData> result = new ArrayList<UnitData>();
        for (UnitData unit : data.getUnits().values()) {
            if (!unit.isBoss()) {
                result.add(unit);
            }
        }
        return result;
    }

    @Test
    @DisplayName("费阶矩阵：非 Boss 24 子 = 1 费×10 / 2 费×9 / 3 费×5（v5 §4.4）")
    void costTierMatrix() {
        Map<Integer, Integer> byCost = new LinkedHashMap<Integer, Integer>();
        for (UnitData unit : purchasable()) {
            Integer current = byCost.get(unit.getCost());
            byCost.put(unit.getCost(), current == null ? 1 : current + 1);
        }
        assertThat(purchasable()).hasSize(24);
        assertThat(byCost).containsEntry(1, 10).containsEntry(2, 9).containsEntry(3, 5);
    }

    @Test
    @DisplayName("种族×职业行列矩阵：四种族各 6、四职业各 6（v5 §4.4 数数）")
    void raceClassMatrix() {
        Map<String, Integer> races = new LinkedHashMap<String, Integer>();
        Map<String, Integer> classes = new LinkedHashMap<String, Integer>();
        for (UnitData unit : purchasable()) {
            races.merge(unit.getRace(), 1, Integer::sum);
            classes.merge(unit.getUnitClass(), 1, Integer::sum);
        }
        assertThat(races).containsOnlyKeys("兽人", "野兽", "亡灵", "巨人")
                .containsEntry("兽人", 6).containsEntry("野兽", 6)
                .containsEntry("亡灵", 6).containsEntry("巨人", 6);
        assertThat(classes).containsOnlyKeys("战士", "法师", "刺客", "游侠")
                .containsEntry("战士", 6).containsEntry("法师", 6)
                .containsEntry("刺客", 6).containsEntry("游侠", 6);
    }

    @Test
    @DisplayName("传奇三子：cost=3 且不在任何场景 shopUnlocks（走英雄 Lv.3 门控，两机制互斥）")
    void legendariesExcludedFromShopUnlocks() {
        Set<String> legendaryIds = new HashSet<String>();
        for (com.voidvvv.kz_auto_chess_n.data.HeroData hero : data.getHeroes().values()) {
            if (hero.getLegendaryUnitId() != null) {
                legendaryIds.add(hero.getLegendaryUnitId());
            }
        }
        assertThat(legendaryIds).containsExactlyInAnyOrder(
                "unit_legend_quartermaster", "unit_legend_thornhart", "unit_legend_warsong_singer");
        for (String legendary : legendaryIds) {
            assertThat(data.getUnit(legendary).getCost()).isEqualTo(3);
            assertThat(data.getUnit(legendary).isBoss()).isFalse();
        }
        Set<String> shopUnlocked = new HashSet<String>();
        for (com.voidvvv.kz_auto_chess_n.data.SceneData scene : data.getScenes().values()) {
            shopUnlocked.addAll(scene.getShopUnlocks());
        }
        assertThat(shopUnlocked).doesNotContainAnyElementsOf(legendaryIds);
    }

    @Test
    @DisplayName("亡灵六子全部携带 deathrattle 或 undying（死骑），其余 18 子不携带")
    void undeadCarryDeathrattleOrUndying() {
        int undeadWithPayload = 0;
        for (UnitData unit : purchasable()) {
            boolean hasPayload = unit.getDeathrattle() != null || unit.getUndying() != null;
            if ("亡灵".equals(unit.getRace())) {
                assertThat(hasPayload)
                        .as("亡灵 %s 必须携带 deathrattle 或 undying", unit.getId()).isTrue();
                undeadWithPayload++;
            } else {
                assertThat(hasPayload)
                        .as("非亡灵 %s 不得携带 deathrattle/undying", unit.getId()).isFalse();
            }
        }
        assertThat(undeadWithPayload).isEqualTo(6);
        // 死骑：唯一 undying 承载（誓约未竟=单位属性非亡语，C1）
        assertThat(data.getUnit("unit_death_knight").getUndying()).isNotNull();
        assertThat(data.getUnit("unit_death_knight").getDeathrattle()).isNull();
        assertThat(data.getUnit("unit_death_knight").getUndying().getReviveDelaySeconds()).isEqualTo(1.0f);
        assertThat(data.getUnit("unit_death_knight").getUndying().getReviveHpRatio()).isEqualTo(0.4f);
    }

    @Test
    @DisplayName("改名 4 例保 id 不变（unit_mage_apprentice/unit_assassin_01/unit_fairy_druid/unit_legend_warsong_singer）")
    void renamedUnitsKeepIds() {
        assertThat(data.getUnit("unit_mage_apprentice").getName()).isEqualTo("焰痕祭司");
        assertThat(data.getUnit("unit_assassin_01").getName()).isEqualTo("霜峰弃子");
        assertThat(data.getUnit("unit_fairy_druid").getName()).isEqualTo("荒野德鲁伊");
        assertThat(data.getUnit("unit_legend_warsong_singer").getName()).isEqualTo("冰川颂者");
    }
}
