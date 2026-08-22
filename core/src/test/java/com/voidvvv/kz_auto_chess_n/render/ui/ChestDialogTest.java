package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 宝箱弹窗数据装配测试（CP27）：选项文案三分支（金币/经验书/装备名）与
 * 传说装备金棕着色。optionText/optionTint 为包级静态纯函数直测——
 * 弹窗本体绘制与点击走 lwjgl3 手验（Phase 4 先例）。
 */
class ChestDialogTest {

    private static GameData dataWith(EquipmentData... equipments) {
        Map<String, EquipmentData> eqMap = new LinkedHashMap<String, EquipmentData>();
        for (EquipmentData eq : equipments) {
            eqMap.put(eq.getId(), eq);
        }
        return new GameData(new LinkedHashMap<String, UnitData>(),
                new LinkedHashMap<String, SkillData>(),
                new LinkedHashMap<String, SynergyData>(),
                new LinkedHashMap<String, SceneData>(),
                eqMap, new ArrayList<String>());
    }

    private static EquipmentData eq(String id, EquipmentRarity rarity) {
        return new EquipmentData(id, "装备" + id, EquipmentSlot.WEAPON, rarity,
                Collections.<com.voidvvv.kz_auto_chess_n.data.EquipmentEffect>emptyList(), null);
    }

    @Test
    @DisplayName("金币选项文案：金币 +数额")
    void goldOptionText() {
        assertThat(ChestDialog.optionText(dataWith(), ChestOption.gold(15)))
                .isEqualTo("金币 +15");
    }

    @Test
    @DisplayName("经验书选项文案：经验 +数额")
    void expBookOptionText() {
        assertThat(ChestDialog.optionText(dataWith(), ChestOption.expBook(4)))
                .isEqualTo("经验 +4");
    }

    @Test
    @DisplayName("装备选项文案：取 equipments 表内模板名")
    void equipmentOptionTextUsesTemplateName() {
        GameData data = dataWith(eq("eq_blade", EquipmentRarity.WHITE));

        assertThat(ChestDialog.optionText(data, ChestOption.equipment("eq_blade")))
                .isEqualTo("装备eq_blade");
    }

    @Test
    @DisplayName("传说装备选项金棕着色（与 InventoryPanel 传说底色同源）")
    void legendaryEquipmentGetsGoldTint() {
        GameData data = dataWith(eq("eq_blade", EquipmentRarity.LEGENDARY));

        Color tint = ChestDialog.optionTint(data, ChestOption.equipment("eq_blade"));

        assertThat(tint).isEqualTo(new Color(0.55f, 0.42f, 0.12f, 1f));
    }

    @Test
    @DisplayName("非传说装备与金币/经验选项用默认底色")
    void nonLegendaryUsesDefaultTint() {
        GameData data = dataWith(eq("eq_blade", EquipmentRarity.RARE));

        assertThat(ChestDialog.optionTint(data, ChestOption.equipment("eq_blade")))
                .isEqualTo(new Color(0.3f, 0.32f, 0.4f, 1f));
        assertThat(ChestDialog.optionTint(data, ChestOption.gold(15)))
                .isEqualTo(new Color(0.3f, 0.32f, 0.4f, 1f));
        assertThat(ChestDialog.optionTint(data, ChestOption.expBook(4)))
                .isEqualTo(new Color(0.3f, 0.32f, 0.4f, 1f));
    }
}
