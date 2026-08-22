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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    @DisplayName("种子规模：12 棋子（9 可购 + 3 Boss，CP31 铺量）/ 11 技能 / 6 羁绊 / 1 场景")
    void seedCounts() {
        assertThat(data.getUnits()).hasSize(12);
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

        // Phase 5.1 CP4：synergy 级 desc 手写文案（裁决 2）——主题句落位且六条全非空
        assertThat(data.getSynergy("syn_orc").getDesc())
                .isEqualTo("焰痕部族的雇佣兵，越战越硬的正面铁壁");
        for (SynergyData synergy : data.getSynergies().values()) {
            assertThat(synergy.getDesc()).as("羁绊 %s desc 非空", synergy.getId()).isNotEmpty();
        }
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
    @DisplayName("软告警：2 孤儿技能（铺量后）+ 0 孤儿羁绊 + 1 行风味聚合")
    void softWarningsOnSeed() {
        List<String> warnings = data.getWarnings();
        // 孤儿技能：铺量后仅 long_snipe/starfall 无单位引用（rampage/mass_heal/poison_cloud 已被新模板引用）
        assertThat(warnings).anyMatch(w -> w.contains("孤儿技能") && w.contains("skill_long_snipe"));
        assertThat(warnings).anyMatch(w -> w.contains("孤儿技能") && w.contains("skill_starfall"));
        assertThat(warnings).noneMatch(w -> w.contains("孤儿技能") && w.contains("skill_rampage"));
        assertThat(warnings).noneMatch(w -> w.contains("孤儿技能") && w.contains("skill_mass_heal"));
        assertThat(warnings).noneMatch(w -> w.contains("孤儿技能") && w.contains("skill_poison_cloud"));
        // 孤儿羁绊：铺量后野兽（狼崽/兽猎手）、法师（暗夜学徒/精灵德鲁伊）均有可购模板，告警清零
        assertThat(warnings).noneMatch(w -> w.contains("孤儿羁绊"));
        // 风味聚合：暗夜/精灵/植物/Boss 一行列出（非新增羁绊键，既有告警口径不变）
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

    // —— CP31 铺量断言（units.json 可购模板 3 → 9）——

    @Test
    @DisplayName("铺量模板落位：6 新模板名称/种族/职业/费阶/技能全量映射")
    void bulkTemplatesMapped() {
        assertBulkTemplate("unit_boar_rider", "野猪骑士", "兽人", "战士", 1, "skill_rampage");
        assertBulkTemplate("unit_wolf_pup", "狼崽", "野兽", "刺客", 1, "skill_execute");
        assertBulkTemplate("unit_mage_apprentice", "暗夜学徒", "暗夜", "法师", 1, "skill_poison_cloud");
        assertBulkTemplate("unit_fairy_druid", "精灵德鲁伊", "精灵", "法师", 2, "skill_mass_heal");
        assertBulkTemplate("unit_beast_archer", "兽猎手", "野兽", "游侠", 2, "skill_pierce");
        assertBulkTemplate("unit_shadow_blade", "暗影之刃", "暗夜", "刺客", 3, "skill_execute");
        // 暗影之刃：3 费刺客切入后排（沿刺客 specialPriority 口径）
        assertThat(data.getUnit("unit_shadow_blade").getSpecialPriority().jsonName()).isEqualTo("BACKLINE");
    }

    /** 铺量模板共性断言：非 Boss、升星倍率缺省 1.8（防手误漏写/错写关键字段） */
    private static void assertBulkTemplate(String id, String name, String race, String unitClass,
                                           int cost, String skillId) {
        UnitData unit = data.getUnit(id);
        assertThat(unit).as("模板 %s 存在", id).isNotNull();
        assertThat(unit.getName()).isEqualTo(name);
        assertThat(unit.getRace()).isEqualTo(race);
        assertThat(unit.getUnitClass()).isEqualTo(unitClass);
        assertThat(unit.getCost()).isEqualTo(cost);
        assertThat(unit.getSkillId()).isEqualTo(skillId);
        assertThat(unit.isBoss()).isFalse();
        assertThat(unit.getUpgradeMultiplier()).isEqualTo(1.8f);
    }

    @Test
    @DisplayName("铺量后费阶池分布：非 Boss 可购 1 费 4 / 2 费 3 / 3 费 2（商店 tierPool 同口径）")
    void purchasableTierDistribution() {
        Map<Integer, List<String>> byCost = new LinkedHashMap<Integer, List<String>>();
        for (UnitData unit : data.getUnits().values()) {
            if (!unit.isBoss()) {
                byCost.computeIfAbsent(unit.getCost(), c -> new ArrayList<String>()).add(unit.getId());
            }
        }
        assertThat(byCost.get(1)).containsExactly("unit_warrior_01", "unit_boar_rider",
                "unit_wolf_pup", "unit_mage_apprentice");
        assertThat(byCost.get(2)).containsExactly("unit_ranger_01", "unit_fairy_druid", "unit_beast_archer");
        assertThat(byCost.get(3)).containsExactly("unit_assassin_01", "unit_shadow_blade");
    }

    @Test
    @DisplayName("铺量后羁绊覆盖：6 首发羁绊各有 ≥2 个可购模板（(2) 档预演可达）")
    void synergyCoverageAtLeastTwo() {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (UnitData unit : data.getUnits().values()) {
            if (!unit.isBoss()) { // 双通道各计 1（SynergySystem 同口径）
                counts.merge(unit.getRace(), 1, Integer::sum);
                counts.merge(unit.getUnitClass(), 1, Integer::sum);
            }
        }
        for (SynergyData synergy : data.getSynergies().values()) {
            assertThat(counts.getOrDefault(synergy.getKey(), 0))
                    .as("羁绊 %s（key=%s）可购模板数", synergy.getId(), synergy.getKey())
                    .isGreaterThanOrEqualTo(2);
        }
    }
}
