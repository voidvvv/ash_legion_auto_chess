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
    @DisplayName("种子规模：33 棋子（24 可购 + 9 Boss）/ 24 技能 / 8 羁绊 / 3 场景 / 3 英雄（v5 §2 全量）")
    void seedCounts() {
        assertThat(data.getUnits()).hasSize(33);
        assertThat(data.getSkills()).hasSize(24);
        assertThat(data.getSynergies()).hasSize(8);
        assertThat(data.getScenes()).hasSize(3);
        assertThat(data.getHeroes()).hasSize(3);
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
    @DisplayName("可选字段缺省：priority 缺省 NEAREST、百分比五键缺省 0/100/0/20/150（§五.5 暴击两键）")
    void optionalFieldDefaults() {
        // 兽猎手：未写 defaultPriority/specialPriority/upgradeMultiplier 与百分比五键
        UnitData archer = data.getUnit("unit_beast_archer");
        assertThat(archer.getDefaultPriority().jsonName()).isEqualTo("NEAREST");
        assertThat(archer.getSpecialPriority()).isNull();
        assertThat(archer.getUpgradeMultiplier()).isEqualTo(1.8f);
        assertThat(archer.getBaseStats().getLifesteal()).isEqualTo(0);
        assertThat(archer.getBaseStats().getEnergyGainRate()).isEqualTo(100);
        assertThat(archer.getBaseStats().getSkillPower()).isEqualTo(0);
        assertThat(archer.getBaseStats().getCritChance()).isEqualTo(20);
        assertThat(archer.getBaseStats().getCritDamage()).isEqualTo(150);

        // 霜峰弃子（v5 改名保 id）：specialPriority 显式 null
        assertThat(data.getUnit("unit_assassin_01").getSpecialPriority()).isNull();
        // 暗影之刃：specialPriority 覆盖为 BACKLINE
        assertThat(data.getUnit("unit_shadow_blade").getSpecialPriority().jsonName()).isEqualTo("BACKLINE");
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
    @DisplayName("羁绊档位：v5 全量 8 条 × 2/4/6 替换制，stat/effect/mechanic 三通道逐档落位")
    void synergyTiersLoaded() {
        assertThat(data.getSynergies().keySet())
                .containsExactly("syn_orc", "syn_beast", "syn_undead", "syn_giant",
                        "syn_warrior", "syn_mage", "syn_assassin", "syn_ranger");

        SynergyData warrior = data.getSynergy("syn_warrior");
        assertThat(warrior.getSource().jsonName()).isEqualTo("CLASS");
        assertThat(warrior.getKey()).isEqualTo("战士");
        assertThat(warrior.getThresholds()).extracting(SynergyData.Threshold::getCount)
                .containsExactly(2, 4, 6);

        // 战士 (6) 档：armor stat + WARCRY_TAUNT + SHIELD_WALL（三通道共存）
        List<EffectData> warriorTier6 = warrior.getThresholds().get(2).getEffects();
        assertThat(warriorTier6).hasSize(3);
        assertThat(warriorTier6.get(0).getStat().jsonName()).isEqualTo("armor");
        assertThat(warriorTier6.get(0).getOp().jsonName()).isEqualTo("ADD");
        assertThat(warriorTier6.get(0).getValue()).isEqualTo(60f);
        assertThat(warriorTier6.get(1).getMechanic().jsonName()).isEqualTo("WARCRY_TAUNT");
        assertThat(warriorTier6.get(1).getTarget().jsonName()).isEqualTo("TRAITS");
        assertThat(warriorTier6.get(1).getTrait()).isEqualTo("战士");
        assertThat(warriorTier6.get(2).getMechanic().jsonName()).isEqualTo("SHIELD_WALL");

        // 兽人 (6) 档：lifesteal stat + BURN 附带 + SCAR
        List<EffectData> orcTier6 = data.getSynergy("syn_orc").getThresholds().get(2).getEffects();
        assertThat(orcTier6).hasSize(3);
        assertThat(orcTier6.get(0).getStat().jsonName()).isEqualTo("lifesteal");
        assertThat(orcTier6.get(1).getMechanic().jsonName()).isEqualTo("BASIC_ATTACK_STATUS");
        assertThat(orcTier6.get(1).getStatus().jsonName()).isEqualTo("BURN");
        assertThat(orcTier6.get(1).getDuration()).isEqualTo(3f);
        assertThat(orcTier6.get(2).getMechanic().jsonName()).isEqualTo("SCAR");
        assertThat(orcTier6.get(2).getValue2()).isEqualTo(6f);

        // 巨人 (6) 档：effect 通道 SHIELD 0.2（开局盾沿用 effect 词汇）
        List<EffectData> giantTier6 = data.getSynergy("syn_giant").getThresholds().get(2).getEffects();
        EffectData giantShield = giantTier6.get(giantTier6.size() - 1);
        assertThat(giantShield.getEffect().jsonName()).isEqualTo("SHIELD");
        assertThat(giantShield.getValue()).isEqualTo(0.2f);
        assertThat(giantShield.getStat()).isNull();

        // 游侠 (4) 档：critChance stat 通道（§五.5 属性化兑现）
        List<EffectData> rangerTier4 = data.getSynergy("syn_ranger").getThresholds().get(1).getEffects();
        assertThat(rangerTier4).extracting(EffectData::getStat)
                .extracting(s -> s == null ? null : s.jsonName())
                .containsExactly("range", "attack", "critChance", "attackSpeed");

        // v5 CP4：synergy 级 desc 手写文案——主题句落位且八条全非空
        assertThat(data.getSynergy("syn_orc").getDesc())
                .isEqualTo("焰痕部族的雇佣兵——每一道疤都是一次结清的账");
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
    @DisplayName("软告警：孤儿技能仅 long_snipe + 0 孤儿羁绊 + 风味聚合仅 Boss 种族/职业")
    void softWarningsOnSeed() {
        List<String> warnings = data.getWarnings();
        // 孤儿技能：v5 铺量后仅 long_snipe 无单位引用
        assertThat(warnings).anyMatch(w -> w.contains("孤儿技能") && w.contains("skill_long_snipe"));
        assertThat(warnings).noneMatch(w -> w.contains("孤儿技能") && w.contains("skill_starfall"));
        assertThat(warnings).noneMatch(w -> w.contains("孤儿技能") && w.contains("skill_rampage"));
        assertThat(warnings).noneMatch(w -> w.contains("孤儿技能") && w.contains("skill_mass_heal"));
        assertThat(warnings).noneMatch(w -> w.contains("孤儿技能") && w.contains("skill_warcry"));
        assertThat(warnings).noneMatch(w -> w.contains("孤儿技能") && w.contains("skill_ignite"));
        assertThat(warnings).noneMatch(w -> w.contains("孤儿技能") && w.contains("skill_repossess"));
        assertThat(warnings).noneMatch(w -> w.contains("孤儿技能") && w.contains("skill_payday"));
        // 孤儿羁绊：8 条 key 全部有可购模板，告警清零
        assertThat(warnings).noneMatch(w -> w.contains("孤儿羁绊"));
        // 风味聚合：v5 后仅 Boss 风味（植物/构造/独眼/Boss）残留；四种族/四职业已全登记
        assertThat(warnings).anyMatch(w -> w.contains("风味") && w.contains("植物")
                && w.contains("构造") && w.contains("独眼") && w.contains("Boss"));
        assertThat(warnings).noneMatch(w -> w.contains("风味") && w.contains("兽人"));
        assertThat(warnings).noneMatch(w -> w.contains("风味") && w.contains("亡灵"));
        assertThat(warnings).noneMatch(w -> w.contains("风味") && w.contains("巨人"));
        assertThat(warnings).noneMatch(w -> w.contains("风味") && w.contains("野兽"));
        // 无 ALL_ENEMIES 非 Boss 引用告警（星陨只被 Boss 引用）
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
    @DisplayName("v5 重排模板落位：改名 4 例保 id + 新增 6 例全量映射")
    void bulkTemplatesMapped() {
        // v5 §2.2 改名保 id 4 例（unit_mage_apprentice/unit_assassin_01/unit_fairy_druid/unit_legend_warsong_singer）
        assertBulkTemplate("unit_mage_apprentice", "焰痕祭司", "兽人", "法师", 1, "skill_ignite");
        assertBulkTemplate("unit_fairy_druid", "荒野德鲁伊", "野兽", "法师", 2, "skill_mass_heal");
        assertBulkTemplate("unit_legend_warsong_singer", "冰川颂者", "巨人", "法师", 3, "skill_mass_heal");
        assertBulkTemplate("unit_legend_quartermaster", "王家军需官", "兽人", "法师", 3, "skill_payday");
        // v5 §4.3 新增 6 例
        assertBulkTemplate("unit_spear_headhunter", "掷矛猎头者", "兽人", "游侠", 1, "skill_headhunt");
        assertBulkTemplate("unit_ambush_panther", "伏击豹", "野兽", "刺客", 1, "skill_ambush_pounce");
        assertBulkTemplate("unit_debt_collector", "收账人", "亡灵", "刺客", 1, "skill_repossess");
        assertBulkTemplate("unit_lamplighter", "守灯人", "亡灵", "法师", 2, "skill_lamplight");
        assertBulkTemplate("unit_bowstring_archer", "空弦射手", "亡灵", "游侠", 2, "skill_audit_stamp");
        assertBulkTemplate("unit_rubble_slinger", "碎石仔", "巨人", "游侠", 1, "skill_rock_toss");
        // 既有模板重排抽查：暗影之刃改兽人降 2 费、暗夜刺客→霜峰弃子（巨人刺客 2 费）
        assertBulkTemplate("unit_shadow_blade", "暗影之刃", "兽人", "刺客", 2, "skill_execute");
        assertBulkTemplate("unit_assassin_01", "霜峰弃子", "巨人", "刺客", 2, "skill_ambush_snow");
        assertBulkTemplate("unit_ranger_01", "丛林游侠", "野兽", "游侠", 2, "skill_entangle");
        assertBulkTemplate("unit_death_knight", "死亡骑士", "亡灵", "战士", 3, "skill_banner_charge");
        // 暗影之刃：切入后排（沿刺客 specialPriority 口径）
        assertThat(data.getUnit("unit_shadow_blade").getSpecialPriority().jsonName()).isEqualTo("BACKLINE");
        // 收账人：LOWEST_HP 覆盖；空弦射手：HIGHEST_ATK 覆盖
        assertThat(data.getUnit("unit_debt_collector").getSpecialPriority().jsonName()).isEqualTo("LOWEST_HP");
        assertThat(data.getUnit("unit_bowstring_archer").getSpecialPriority().jsonName()).isEqualTo("HIGHEST_ATK");
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
    @DisplayName("v5 费阶池分布：非 Boss 可购 1 费 10 / 2 费 9 / 3 费 5（商店 tierPool 同口径，v5 §4.4）")
    void purchasableTierDistribution() {
        Map<Integer, List<String>> byCost = new LinkedHashMap<Integer, List<String>>();
        for (UnitData unit : data.getUnits().values()) {
            if (!unit.isBoss()) {
                byCost.computeIfAbsent(unit.getCost(), c -> new ArrayList<String>()).add(unit.getId());
            }
        }
        assertThat(byCost.get(1)).containsExactly("unit_warrior_01", "unit_boar_rider",
                "unit_mage_apprentice", "unit_spear_headhunter", "unit_wolf_pup", "unit_ambush_panther",
                "unit_skeleton_soldier", "unit_debt_collector", "unit_frost_imp", "unit_rubble_slinger");
        assertThat(byCost.get(2)).containsExactly("unit_shadow_blade", "unit_beast_archer",
                "unit_ranger_01", "unit_fairy_druid", "unit_wraith", "unit_lamplighter",
                "unit_bowstring_archer", "unit_frost_giant", "unit_assassin_01");
        assertThat(byCost.get(3)).containsExactly("unit_legend_quartermaster", "unit_legend_thornhart",
                "unit_death_knight", "unit_glacial_giant", "unit_legend_warsong_singer");
    }

    // —— Phase 6（CP3/CP11）：三英雄与场景链 / 门控互斥 ——

    @Test
    @DisplayName("三英雄全量落位：被动三形态 / 薇拉 synergyIds 含野兽与游侠 / 传奇三件不共用")
    void heroesFullyMapped() {
        assertThat(data.getHeroes().keySet())
                .containsExactly("hero_greg", "hero_vera", "hero_orlando");
        assertThat(data.getHero("hero_greg").getName()).isEqualTo("老兵格雷克");
        assertThat(data.getHero("hero_greg").getPassiveType().jsonName()).isEqualTo("START_GOLD");
        assertThat(data.getHero("hero_greg").getPassiveValue()).isEqualTo(2f);
        assertThat(data.getHero("hero_greg").getLegendaryUnitId()).isEqualTo("unit_legend_quartermaster");

        assertThat(data.getHero("hero_vera").getPassiveType().jsonName()).isEqualTo("SYNERGY_AMP");
        assertThat(data.getHero("hero_vera").getPassiveSynergyIds())
                .containsExactly("syn_beast", "syn_ranger");

        assertThat(data.getHero("hero_orlando").getPassiveType().jsonName()).isEqualTo("ENERGY_GAIN");
        assertThat(data.getHero("hero_orlando").getLegendaryUnitId()).isEqualTo("unit_legend_warsong_singer");
    }

    @Test
    @DisplayName("三场景解锁链 forest→crypt→snow：墓穴 6 亡灵/雪山 5 巨人 shopUnlocks 且与英雄传奇互斥（加载即证明）")
    void sceneChainAndShopUnlocks() {
        assertThat(data.getScenes().keySet())
                .containsExactly("scene_forest", "scene_crypt", "scene_snow");
        assertThat(data.getScene("scene_forest").getUnlockAfter()).isNull();
        assertThat(data.getScene("scene_crypt").getUnlockAfter()).isEqualTo("scene_forest");
        assertThat(data.getScene("scene_snow").getUnlockAfter()).isEqualTo("scene_crypt");
        assertThat(data.getScene("scene_forest").getShopUnlocks()).isEmpty();
        assertThat(data.getScene("scene_crypt").getShopUnlocks())
                .containsExactly("unit_skeleton_soldier", "unit_wraith", "unit_debt_collector",
                        "unit_bowstring_archer", "unit_lamplighter", "unit_death_knight");
        assertThat(data.getScene("scene_snow").getShopUnlocks())
                .containsExactly("unit_frost_imp", "unit_rubble_slinger", "unit_frost_giant",
                        "unit_assassin_01", "unit_glacial_giant");
        assertThat(data.getScene("scene_snow").getBosses()).containsEntry(25, "boss_star_warden");
        // CP6：森林敌池首条 minRound=1（S5 兑现——狼崽开荒）
        assertThat(data.getScene("scene_forest").getEnemyPool().get(0).getUnitId()).isEqualTo("unit_wolf_pup");
        assertThat(data.getScene("scene_forest").getEnemyPool().get(0).getMinRound()).isEqualTo(1);
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
