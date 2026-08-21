package com.voidvvv.kz_auto_chess_n.config;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffect;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JsonLoader 正向用例：加载 assets/data 种子文件（data_schema V1.4 示例），断言字段、缺省与软告警。
 * 测试工作目录 = core/，种子经相对路径 ../assets/data 注入（project_structure §五）。
 */
class JsonLoaderTest {

    private static GameData data;

    @BeforeAll
    static void loadSeedData() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    @Test
    @DisplayName("种子规模：6 棋子 / 11 技能 / 6 羁绊 / 1 场景")
    void seedCounts() {
        assertThat(data.getUnits()).hasSize(6);
        assertThat(data.getSkills()).hasSize(11);
        assertThat(data.getSynergies()).hasSize(6);
        assertThat(data.getScenes()).hasSize(1);
    }

    @Test
    @DisplayName("棋子字段全量落位：兽人战士")
    void unitFieldsFullyMapped() {
        UnitData warrior = data.getUnit("unit_warrior_01");
        assertThat(warrior).isNotNull();
        assertThat(warrior.getName()).isEqualTo("兽人战士");
        assertThat(warrior.getRace()).isEqualTo("兽人");
        assertThat(warrior.getUnitClass()).isEqualTo("战士");
        assertThat(warrior.getCost()).isEqualTo(1);
        assertThat(warrior.getUpgradeMultiplier()).isEqualTo(1.8f);
        assertThat(warrior.getDefaultPriority().jsonName()).isEqualTo("NEAREST");
        assertThat(warrior.getSpecialPriority()).isNull(); // 显式 null
        assertThat(warrior.getSkillId()).isEqualTo("skill_warcry");
        assertThat(warrior.isBoss()).isFalse();

        BaseStats bs = warrior.getBaseStats();
        assertThat(bs.getHp()).isEqualTo(100);
        assertThat(bs.getAttack()).isEqualTo(15);
        assertThat(bs.getArmor()).isEqualTo(10);
        assertThat(bs.getAttackSpeed()).isEqualTo(1.0f);
        assertThat(bs.getRange()).isEqualTo(1);
        assertThat(bs.getMoveSpeed()).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("可选字段缺省：priority 缺省 NEAREST、百分比三键缺省 0/100/0")
    void optionalFieldDefaults() {
        // 丛林游侠：未写 defaultPriority/specialPriority/upgradeMultiplier 与百分比三键
        UnitData ranger = data.getUnit("unit_ranger_01");
        assertThat(ranger.getDefaultPriority().jsonName()).isEqualTo("NEAREST");
        assertThat(ranger.getSpecialPriority()).isNull();
        assertThat(ranger.getUpgradeMultiplier()).isEqualTo(1.8f);
        assertThat(ranger.getBaseStats().getLifesteal()).isEqualTo(0);
        assertThat(ranger.getBaseStats().getEnergyGainRate()).isEqualTo(100);
        assertThat(ranger.getBaseStats().getSkillPower()).isEqualTo(0);

        // 暗夜刺客：specialPriority 覆盖为 BACKLINE
        assertThat(data.getUnit("unit_assassin_01").getSpecialPriority().jsonName()).isEqualTo("BACKLINE");
    }

    @Test
    @DisplayName("Boss 模板：cost=0、boss=true、烘焙数值、倍率 1.0")
    void bossTemplateBakedValues() {
        UnitData boss = data.getUnit("boss_thorn_mother");
        assertThat(boss.isBoss()).isTrue();
        assertThat(boss.getCost()).isEqualTo(0);
        assertThat(boss.getUpgradeMultiplier()).isEqualTo(1.0f);
        assertThat(boss.getBaseStats().getHp()).isEqualTo(1250); // 500 × 2.5 烘焙
        assertThat(boss.getBaseStats().getAttack()).isEqualTo(42); // 21 × 2.0 烘焙
    }

    @Test
    @DisplayName("技能字段：载体缺省 MELEE_INSTANT、多效果依次保序")
    void skillFieldsAndDefaults() {
        SkillData warcry = data.getSkill("skill_warcry");
        assertThat(warcry.getShape().jsonName()).isEqualTo("ALL_ALLIES");
        assertThat(warcry.getDelivery()).isEqualTo(Delivery.MELEE_INSTANT);
        assertThat(warcry.getEffects()).hasSize(1);
        assertThat(warcry.getEffects().get(0).getStatus().jsonName()).isEqualTo("ATK_UP");
        assertThat(warcry.getEffects().get(0).getValue()).isEqualTo(15f);
        assertThat(warcry.getEffects().get(0).getDuration()).isEqualTo(5f);

        // 暴走：双状态依次保序（ATK_UP → ASPD_UP）
        SkillData rampage = data.getSkill("skill_rampage");
        assertThat(rampage.getShape().jsonName()).isEqualTo("SELF");
        assertThat(rampage.getDelivery()).isEqualTo(Delivery.MELEE_INSTANT); // 显式写了缺省值
        assertThat(rampage.getEffects()).extracting(SkillEffect::getStatus)
                .extracting(s -> s.jsonName())
                .containsExactly("ATK_UP", "ASPD_UP");

        // 毒雾弹：AOE_2 + POISON，value=0.1（每跳攻击力倍率）
        SkillData poison = data.getSkill("skill_poison_cloud");
        assertThat(poison.getShape().jsonName()).isEqualTo("AOE_2");
        assertThat(poison.getDelivery()).isEqualTo(Delivery.HOMING);
        assertThat(poison.getEffects().get(0).getValue()).isEqualTo(0.1f);
        assertThat(poison.getEffects().get(0).getDuration()).isEqualTo(6f);

        // 星陨：ALL_ENEMIES（Boss 演出形状）
        assertThat(data.getSkill("skill_starfall").getShape().jsonName()).isEqualTo("ALL_ENEMIES");
        // 贯穿箭：SINGLE_TARGET + DAMAGE 2.0，value 非 null
        SkillEffect pierce = data.getSkill("skill_pierce").getEffects().get(0);
        assertThat(pierce.getEffect().jsonName()).isEqualTo("DAMAGE");
        assertThat(pierce.getValue()).isEqualTo(2.0f);
        assertThat(pierce.getStatus()).isNull();
        assertThat(pierce.getDuration()).isNull();
    }

    @Test
    @DisplayName("羁绊档位：6 羁绊 count 升序、(6) 档含 lifesteal stat 通道")
    void synergyTiersLoaded() {
        assertThat(data.getSynergies().keySet())
                .containsExactly("syn_orc", "syn_warrior", "syn_mage", "syn_assassin", "syn_beast", "syn_ranger");

        SynergyData warrior = data.getSynergy("syn_warrior");
        assertThat(warrior.getSource().jsonName()).isEqualTo("CLASS");
        assertThat(warrior.getKey()).isEqualTo("战士");
        assertThat(warrior.getThresholds()).extracting(SynergyData.Threshold::getCount)
                .containsExactly(2, 4, 6);

        List<EffectData> tier6 = warrior.getThresholds().get(2).getEffects();
        assertThat(tier6).hasSize(3);
        // 第三条为 stat:lifesteal（V1.3 起 effect 通道 LIFESTEAL 写法废弃）
        assertThat(tier6.get(2).getStat().jsonName()).isEqualTo("lifesteal");
        assertThat(tier6.get(2).getOp().jsonName()).isEqualTo("ADD");
        assertThat(tier6.get(2).getValue()).isEqualTo(20f);

        // 兽人 (6) 档：effect 通道 SHIELD 0.3
        EffectData orcShield = data.getSynergy("syn_orc").getThresholds().get(2).getEffects().get(0);
        assertThat(orcShield.getEffect().jsonName()).isEqualTo("SHIELD");
        assertThat(orcShield.getValue()).isEqualTo(0.3f);
        assertThat(orcShield.getStat()).isNull();
    }

    @Test
    @DisplayName("加载后的羁绊门槛判定（数据与逻辑集成）")
    void loadedSynergyThresholdResolution() {
        SynergyData warrior = data.getSynergy("syn_warrior");
        assertThat(warrior.activeThreshold(1)).isNull();
        assertThat(warrior.activeThreshold(2).getCount()).isEqualTo(2);
        assertThat(warrior.activeThreshold(3).getCount()).isEqualTo(2);
        assertThat(warrior.activeThreshold(4).getCount()).isEqualTo(4);
        assertThat(warrior.activeThreshold(6).getCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("软告警：5 孤儿技能 + 2 孤儿羁绊（野兽/法师无种子单位）+ 1 行风味聚合")
    void softWarningsOnSeed() {
        List<String> warnings = data.getWarnings();
        // 孤儿技能：rampage/mass_heal/long_snipe/starfall/poison_cloud 无单位引用
        assertThat(warnings).anyMatch(w -> w.contains("孤儿技能") && w.contains("skill_rampage"));
        assertThat(warnings).anyMatch(w -> w.contains("孤儿技能") && w.contains("skill_starfall"));
        // 孤儿羁绊：种子单位无野兽种族、无法师职业
        assertThat(warnings).anyMatch(w -> w.contains("孤儿羁绊") && w.contains("syn_beast"));
        assertThat(warnings).anyMatch(w -> w.contains("孤儿羁绊") && w.contains("syn_mage"));
        // 风味聚合：暗夜/精灵/植物/Boss 一行列出
        assertThat(warnings).anyMatch(w -> w.contains("风味") && w.contains("暗夜")
                && w.contains("精灵") && w.contains("植物") && w.contains("Boss"));
        // 无 ALL_ENEMIES 非 Boss 引用告警（星陨未被任何单位引用，只报孤儿技能）
        assertThat(warnings).noneMatch(w -> w.contains("ALL_ENEMIES 被非 Boss"));
    }

    @Test
    @DisplayName("聚合容器不可变：外部不可写入查找表")
    void gameDataMapsUnmodifiable() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> data.getUnits().put("x", null))
                .isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> data.getWarnings().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
