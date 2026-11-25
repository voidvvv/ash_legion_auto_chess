package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PlaceholderKeys 测试：命名约定格式与 enumerateFor 全 key 覆盖（render §7.1/§7.5，防漏生成）。
 */
class PlaceholderKeysTest {

    private static SkillData meleeSkill() {
        return BattleTestFixtures.skill("sk_melee", SkillShape.SINGLE_TARGET,
                Delivery.MELEE_INSTANT, BattleTestFixtures.effect(SkillEffectType.DAMAGE, 2f, null, null));
    }

    @Test
    @DisplayName("单位帧命名：{unitId}_{anim}_{frame}")
    void unitFrameNaming() {
        assertThat(PlaceholderKeys.unitFrame("unit_warrior_01", "idle", 0))
                .isEqualTo("unit_warrior_01_idle_0");
        assertThat(PlaceholderKeys.unitFrame("unit_ranger_01", "attack", 2))
                .isEqualTo("unit_ranger_01_attack_2");
    }

    @Test
    @DisplayName("技能/状态/数字命名：fx_{skillId} / fx_{skillId}_burst / fx_status_{type} / fx_digit_{d}")
    void fxNaming() {
        assertThat(PlaceholderKeys.skillFx("sk_fireball")).isEqualTo("fx_sk_fireball");
        assertThat(PlaceholderKeys.skillFxBurst("sk_fireball")).isEqualTo("fx_sk_fireball_burst");
        assertThat(PlaceholderKeys.statusFx(com.voidvvv.kz_auto_chess_n.data.StatusType.ATK_UP))
                .isEqualTo("fx_status_atk_up");
        assertThat(PlaceholderKeys.digitFx(0)).isEqualTo("fx_digit_0");
        assertThat(PlaceholderKeys.digitFx(9)).isEqualTo("fx_digit_9");
    }

    @Test
    @DisplayName("帧数表：idle 2 / walk 2 / attack 3 / cast 2 / death 3（render §7.1）")
    void frameCounts() {
        assertThat(PlaceholderKeys.frameCount("idle")).isEqualTo(2);
        assertThat(PlaceholderKeys.frameCount("walk")).isEqualTo(2);
        assertThat(PlaceholderKeys.frameCount("attack")).isEqualTo(3);
        assertThat(PlaceholderKeys.frameCount("cast")).isEqualTo(2);
        assertThat(PlaceholderKeys.frameCount("death")).isEqualTo(3);
    }

    @Test
    @DisplayName("enumerateFor 覆盖全部 unitId×5 动画×帧数 + skillId×2 + StatusType×1 + 通用件 + UI 框架件")
    void enumerateForCoversEverything() {
        GameData data = BattleTestFixtures.microData(meleeSkill());
        List<String> keys = PlaceholderKeys.enumerateFor(data);
        // 单位帧：microData 无单位 → 0；技能 2（起手+落点）；亡语弹通用 1；状态 10；数字 10；通用件 4；UI 框架件 26（含按钮态 6）
        assertThat(keys).contains("fx_sk_melee", "fx_sk_melee_burst");
        assertThat(keys).contains("fx_deathrattle_projectile_burst"); // 亡语合成 id（2026-11-21 回归补）
        assertThat(keys).contains("fx_status_shield", "fx_status_stun");
        assertThat(keys).contains("fx_digit_0", "fx_digit_9");
        assertThat(keys).contains("fx_cast_default", "fx_hit_default", "ui_panel_9slice", "fx_white");
        assertThat(keys).doesNotContain("unit_warrior_01_idle_0");

        // 完整覆盖：构造含一个单位的 GameData 再验单位帧全 12 键
        java.util.Map<String, com.voidvvv.kz_auto_chess_n.data.UnitData> units =
                new java.util.LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.UnitData>();
        units.put("unit_x", BattleTestFixtures.tpl("unit_x"));
        GameData withUnit = new GameData(units,
                data.getSkills(), data.getSynergies(), data.getScenes(), data.getWarnings());
        List<String> full = PlaceholderKeys.enumerateFor(withUnit);
        assertThat(full).contains("unit_x_idle_0", "unit_x_idle_1",
                "unit_x_walk_0", "unit_x_walk_1",
                "unit_x_attack_0", "unit_x_attack_1", "unit_x_attack_2",
                "unit_x_cast_0", "unit_x_cast_1",
                "unit_x_death_0", "unit_x_death_1", "unit_x_death_2");
        // 状态 fx 10（v5 增 BURN）+ 亡语弹通用 1（回归补）+ UI 框架件 26（必需 12 + 加分 8 + 按钮态 6——第二阶段）
        assertThat(full).hasSize(12 + 2 + 1 + 10 + 10 + 4 + 26);
    }

    @Test
    @DisplayName("enumerateFor 覆盖全部 UI 框架键（UI 抽屉化批0）：必需 12 + 加分 8 逐键在列——占位必存在、与旧通用件键不碰撞")
    void enumerateForCoversAllUiKeys() {
        List<String> keys = PlaceholderKeys.enumerateFor(BattleTestFixtures.microData(meleeSkill()));
        for (UiKeys.Entry entry : UiKeys.ALL) {
            assertThat(keys)
                    .as("UI 键 %s 必须在枚举内（PlaceholderArt.verifyCoverage 逐键强制生成占位）", entry.id())
                    .contains(entry.id());
        }
        assertThat(UiKeys.find(PlaceholderKeys.PANEL_9SLICE))
                .as("旧通用件 ui_panel_9slice 仍是独立键（既有面板在用），不属于新 UI 素材键集").isNull();
        assertThat(keys).contains(PlaceholderKeys.PANEL_9SLICE); // 旧通用件 4 键照旧在列
    }

    @Test
    @DisplayName("enumerateFor 无重复 key（含 UI 框架件：与单位帧/技能/通用件命名空间互不碰撞）")
    void enumerateForNoDuplicates() {
        List<String> keys = PlaceholderKeys.enumerateFor(BattleTestFixtures.microData(meleeSkill()));
        assertThat(keys).hasSize((int) keys.stream().distinct().count());
    }
}
