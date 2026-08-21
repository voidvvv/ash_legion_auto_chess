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
    @DisplayName("enumerateFor 覆盖全部 unitId×5 动画×帧数 + skillId×2 + StatusType×1 + 通用件")
    void enumerateForCoversEverything() {
        SkillData skill = BattleTestFixtures.skill("sk_melee", SkillShape.SINGLE_TARGET,
                Delivery.MELEE_INSTANT, BattleTestFixtures.effect(SkillEffectType.DAMAGE, 2f, null, null));
        GameData data = BattleTestFixtures.microData(skill);
        List<String> keys = PlaceholderKeys.enumerateFor(data);
        // 单位帧：microData 无单位 → 0；技能 2（起手+落点）；状态 9；数字 10；通用件 4
        assertThat(keys).contains("fx_sk_melee", "fx_sk_melee_burst");
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
        assertThat(full).hasSize(12 + 2 + 9 + 10 + 4);
    }

    @Test
    @DisplayName("enumerateFor 无重复 key")
    void enumerateForNoDuplicates() {
        SkillData skill = BattleTestFixtures.skill("sk_melee", SkillShape.SINGLE_TARGET,
                Delivery.MELEE_INSTANT, BattleTestFixtures.effect(SkillEffectType.DAMAGE, 2f, null, null));
        List<String> keys = PlaceholderKeys.enumerateFor(BattleTestFixtures.microData(skill));
        assertThat(keys).hasSize((int) keys.stream().distinct().count());
    }
}
