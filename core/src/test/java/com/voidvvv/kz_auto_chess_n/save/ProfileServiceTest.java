package com.voidvvv.kz_auto_chess_n.save;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.HeroData;
import com.voidvvv.kz_auto_chess_n.data.HeroPassiveType;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ProfileService 纯函数测试（Phase 6 CP6）：熟练度结算升级链/封顶/通关登记/新解锁派生；
 * runModifiers 六档三被动与门控池（GDD §8.1；裁决 D2/D3/D8）。夹具手搓 GameData（沿
 * BattleTestFixtures 先例）。
 */
class ProfileServiceTest {

    // —— 夹具：三场景链 + 三英雄（GDD 草案同构）+ 门控单位 + 传奇 ——

    private static UnitData unit(String id, int cost, boolean boss) {
        return new UnitData(id, "夹具" + id, "人类", "战士", cost,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, boss);
    }

    private static GameData data() {
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        units.put("u_basic", unit("u_basic", 1, false));
        units.put("u_crypt1", unit("u_crypt1", 1, false));
        units.put("u_crypt2", unit("u_crypt2", 2, false));
        units.put("u_snow1", unit("u_snow1", 1, false));
        units.put("u_snow2", unit("u_snow2", 2, false));
        units.put("u_leg_g", unit("u_leg_g", 3, false));
        units.put("u_leg_v", unit("u_leg_v", 3, false));
        units.put("u_leg_o", unit("u_leg_o", 3, false));
        units.put("u_boss", unit("u_boss", 0, true));

        Map<Integer, String> bosses = new LinkedHashMap<Integer, String>();
        bosses.put(7, "u_boss");
        bosses.put(15, "u_boss");
        bosses.put(25, "u_boss");

        Map<String, SceneData> scenes = new LinkedHashMap<String, SceneData>();
        scenes.put("scene_forest", new SceneData("scene_forest", "翡翠林地", null,
                new ArrayList<SceneData.EnemyPoolEntry>(), bosses, new ArrayList<String>()));
        scenes.put("scene_crypt", new SceneData("scene_crypt", "亡者墓穴", "scene_forest",
                new ArrayList<SceneData.EnemyPoolEntry>(), bosses,
                new ArrayList<String>(Arrays.asList("u_crypt1", "u_crypt2"))));
        scenes.put("scene_snow", new SceneData("scene_snow", "寒峰雪山", "scene_crypt",
                new ArrayList<SceneData.EnemyPoolEntry>(), bosses,
                new ArrayList<String>(Arrays.asList("u_snow1", "u_snow2"))));

        Map<String, HeroData> heroes = new LinkedHashMap<String, HeroData>();
        heroes.put("hero_greg", new HeroData("hero_greg", "老兵格雷克", "desc",
                HeroPassiveType.START_GOLD, 2f, new ArrayList<String>(), "u_leg_g"));
        heroes.put("hero_vera", new HeroData("hero_vera", "荆语者薇拉", "desc",
                HeroPassiveType.SYNERGY_AMP, 25f,
                new ArrayList<String>(Arrays.asList("syn_beast", "syn_ranger")), "u_leg_v"));
        heroes.put("hero_orlando", new HeroData("hero_orlando", "灰烬诗人奥兰多", "desc",
                HeroPassiveType.ENERGY_GAIN, 15f, new ArrayList<String>(), "u_leg_o"));

        return new GameData(units,
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                scenes, new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                heroes, new ArrayList<String>());
    }

    private static Profile profileWith(String heroId, int level, int exp) {
        return Profile.fresh().withHeroProgress(heroId, new HeroProgress(level, exp));
    }

    // —— settle：升级链与封顶 ——

    @Test
    @DisplayName("放弃 r1：+3 → Lv.1 exp3；不登记通关")
    void settleAbandonRoundOne() {
        ProfileService.Settlement s = ProfileService.settle(Profile.fresh(), data(),
                "hero_greg", "scene_forest", RunEndCause.ABANDONED, 1, 3);
        assertThat(s.getExpGained()).isEqualTo(3);
        assertThat(s.getLevelFrom()).isEqualTo(1);
        assertThat(s.getLevelTo()).isEqualTo(1);
        assertThat(s.getExpIntoLevel()).isEqualTo(3);
        assertThat(s.getExpToNextLevel()).isEqualTo(50);
        assertThat(s.getNewProfile().getCompletedScenes()).isEmpty();
        assertThat(s.getNewProfile().getHeroProgress().get("hero_greg"))
                .isEqualTo(new HeroProgress(1, 3));
    }

    @Test
    @DisplayName("通关 25 轮：+135 → 连续升级至 Lv.2 余 85/100，登记森林，解锁墓穴")
    void settleCompletionCrossesFirstLevel() {
        GameData data = data();
        ProfileService.Settlement s = ProfileService.settle(Profile.fresh(), data,
                "hero_greg", "scene_forest", RunEndCause.COMPLETED,
                GameBalance.TOTAL_ROUNDS,
                GameBalance.MASTERY_COMPLETE_BONUS + GameBalance.TOTAL_ROUNDS * 3);
        assertThat(s.getLevelFrom()).isEqualTo(1);
        assertThat(s.getLevelTo()).isEqualTo(2);
        assertThat(s.getExpIntoLevel()).isEqualTo(85); // 135 - 50
        assertThat(s.getExpToNextLevel()).isEqualTo(100);
        assertThat(s.getNewProfile().getCompletedScenes()).containsExactly("scene_forest");
        assertThat(s.getNewlyUnlockedSceneIds()).containsExactly("scene_crypt"); // 雪山未入
    }

    @Test
    @DisplayName("大额跨级：+500 → 直达 Lv.5 余 0")
    void settleHugeExpReachesCap() {
        ProfileService.Settlement s = ProfileService.settle(Profile.fresh(), data(),
                "hero_greg", null, RunEndCause.ABANDONED, 1, 500);
        assertThat(s.getLevelTo()).isEqualTo(GameBalance.MASTERY_MAX_LEVEL);
        assertThat(s.getExpIntoLevel()).isZero();
        assertThat(s.getExpToNextLevel()).isZero();
    }

    @Test
    @DisplayName("Lv.5 封顶：既有余量与新入账全部作废")
    void settleAtCapDiscardsRemainder() {
        ProfileService.Settlement s = ProfileService.settle(
                profileWith("hero_greg", 5, 40), data(),
                "hero_greg", null, RunEndCause.ABANDONED, 1, 100);
        assertThat(s.getLevelTo()).isEqualTo(5);
        assertThat(s.getExpIntoLevel()).isZero();
        assertThat(s.getNewProfile().getHeroProgress().get("hero_greg"))
                .isEqualTo(new HeroProgress(5, 0));
    }

    @Test
    @DisplayName("COMPLETED 登记幂等：二次通关同场景 completedScenes 仍 1 项")
    void completedSceneRegistrationIdempotent() {
        GameData data = data();
        Profile first = ProfileService.settle(Profile.fresh(), data, "hero_greg",
                "scene_forest", RunEndCause.COMPLETED, 25, 135).getNewProfile();
        Profile second = ProfileService.settle(first, data, "hero_greg",
                "scene_forest", RunEndCause.COMPLETED, 25, 135).getNewProfile();
        assertThat(second.getCompletedScenes()).containsExactly("scene_forest");
    }

    @Test
    @DisplayName("heroId null 容忍：不写英雄进度，通关登记照常")
    void settleToleratesNullHero() {
        ProfileService.Settlement s = ProfileService.settle(Profile.fresh(), data(),
                null, "scene_forest", RunEndCause.COMPLETED, 25, 135);
        assertThat(s.getNewProfile().getHeroProgress()).isEmpty();
        assertThat(s.getNewProfile().getCompletedScenes()).containsExactly("scene_forest");
        assertThat(s.getLevelFrom()).isEqualTo(1);
    }

    @Test
    @DisplayName("负入账防御钳 0（expGained=0、档案不变量保留）")
    void settleClampsNegativeAward() {
        ProfileService.Settlement s = ProfileService.settle(
                profileWith("hero_greg", 2, 30), data(),
                "hero_greg", null, RunEndCause.ABANDONED, 1, -999);
        assertThat(s.getExpGained()).isZero();
        assertThat(s.getNewProfile().getHeroProgress().get("hero_greg"))
                .isEqualTo(new HeroProgress(2, 30));
    }

    // —— unlockedSceneIds：completedScenes 派生（裁决 D7） ——

    @Test
    @DisplayName("解锁链派生：fresh 仅森林 → 通关森林加墓穴 → 通关墓穴加雪山")
    void unlockedScenesDerivedFromCompletions() {
        GameData data = data();
        assertThat(ProfileService.unlockedSceneIds(Profile.fresh(), data))
                .containsExactly("scene_forest");

        Profile forestDone = Profile.fresh().withCompletedScene("scene_forest");
        assertThat(ProfileService.unlockedSceneIds(forestDone, data))
                .containsExactly("scene_forest", "scene_crypt");

        Profile cryptDone = forestDone.withCompletedScene("scene_crypt");
        assertThat(ProfileService.unlockedSceneIds(cryptDone, data))
                .containsExactly("scene_forest", "scene_crypt", "scene_snow");
    }

    @Test
    @DisplayName("masteryLevel：未记录英雄 = Lv.1；记录英雄取档值")
    void masteryLevelDefaultsToOne() {
        GameData data = data();
        assertThat(ProfileService.masteryLevel(Profile.fresh(), "hero_greg")).isEqualTo(1);
        assertThat(ProfileService.masteryLevel(
                profileWith("hero_vera", 3, 10), "hero_vera")).isEqualTo(3);
    }

    // —— runModifiers：等级解锁表 × 英雄被动 × 门控池 ——

    @Test
    @DisplayName("格雷克六档：Lv.1=+4（Lv.1 权益 2+被动 2，裁决 D2）/ Lv.4=+7 / 折扣与概率逐档")
    void gregModifiersAcrossLevels() {
        GameData data = data();
        HeroData greg = data.getHero("hero_greg");

        RunModifiers lv1 = ProfileService.runModifiers(greg, Profile.fresh(), data);
        assertThat(lv1.getStartGoldBonus()).isEqualTo(4); // 2 + 2（裁决 D2 叠加）
        assertThat(lv1.getRareShopBonusPp()).isZero();
        assertThat(lv1.getRefreshCostDiscount()).isZero();

        RunModifiers lv2 = ProfileService.runModifiers(greg, profileWith("hero_greg", 2, 0), data);
        assertThat(lv2.getRareShopBonusPp()).isEqualTo(5);
        assertThat(lv2.getStartGoldBonus()).isEqualTo(4);

        RunModifiers lv4 = ProfileService.runModifiers(greg, profileWith("hero_greg", 4, 0), data);
        assertThat(lv4.getStartGoldBonus()).isEqualTo(7); // 2 + 3 + 2
        assertThat(lv4.getRareShopBonusPp()).isEqualTo(5);

        RunModifiers lv5 = ProfileService.runModifiers(greg, profileWith("hero_greg", 5, 0), data);
        assertThat(lv5.getRefreshCostDiscount()).isEqualTo(1);
        assertThat(lv5.getStartGoldBonus()).isEqualTo(7);
    }

    @Test
    @DisplayName("薇拉 amp map {syn_beast:0.25, syn_ranger:0.25}；奥兰多 energyPp 15")
    void veraAndOrlandoPassives() {
        GameData data = data();
        RunModifiers vera = ProfileService.runModifiers(
                data.getHero("hero_vera"), Profile.fresh(), data);
        assertThat(vera.getSynergyAmp())
                .containsEntry("syn_beast", 0.25f)
                .containsEntry("syn_ranger", 0.25f)
                .hasSize(2);
        assertThat(vera.getStartGoldBonus()).isEqualTo(2); // 仅 Lv.1 权益
        assertThat(vera.getEnergyGainRateBonus()).isZero();

        RunModifiers orlando = ProfileService.runModifiers(
                data.getHero("hero_orlando"), Profile.fresh(), data);
        assertThat(orlando.getEnergyGainRateBonus()).isEqualTo(15);
        assertThat(orlando.getSynergyAmp()).isEmpty();
        assertThat(orlando.modifiers().addOf(com.voidvvv.kz_auto_chess_n.data.StatKey.ENERGY_GAIN_RATE))
                .isEqualTo(15f);
    }

    @Test
    @DisplayName("门控池：fresh 档基础池仅 u_basic；Lv.3 本英雄传奇入池、他人传奇被排除；通关后场景单位入池")
    void shopPoolGating() {
        GameData data = data();
        HeroData greg = data.getHero("hero_greg");

        RunModifiers lv1 = ProfileService.runModifiers(greg, Profile.fresh(), data);
        assertThat(lv1.isShopAllowed("u_basic")).isTrue();
        assertThat(lv1.isShopAllowed("u_crypt1")).isFalse(); // 墓穴未解锁
        assertThat(lv1.isShopAllowed("u_snow1")).isFalse();  // 雪山未解锁
        assertThat(lv1.isShopAllowed("u_leg_g")).isFalse();  // 本英雄传奇需 Lv.3
        assertThat(lv1.isShopAllowed("u_leg_v")).isFalse();  // 他人传奇恒不可见
        assertThat(lv1.isShopAllowed("u_boss")).isFalse();   // Boss 不入池

        RunModifiers lv3 = ProfileService.runModifiers(greg, profileWith("hero_greg", 3, 0), data);
        assertThat(lv3.getLegendaryUnitId()).isEqualTo("u_leg_g");
        assertThat(lv3.isShopAllowed("u_leg_g")).isTrue();
        assertThat(lv3.isShopAllowed("u_leg_o")).isFalse();

        Profile forestDone = Profile.fresh().withCompletedScene("scene_forest");
        RunModifiers unlockedCrypt = ProfileService.runModifiers(greg, forestDone, data);
        assertThat(unlockedCrypt.isShopAllowed("u_crypt1")).isTrue();
        assertThat(unlockedCrypt.isShopAllowed("u_crypt2")).isTrue();
        assertThat(unlockedCrypt.isShopAllowed("u_snow2")).isFalse(); // 雪山仍锁
    }

    @Test
    @DisplayName("hero null：仅门控 + Lv.1 基础权益（防御路径）")
    void nullHeroGetsBaseEntitlementsOnly() {
        RunModifiers modifiers = ProfileService.runModifiers(null, Profile.fresh(), data());
        assertThat(modifiers.getStartGoldBonus()).isEqualTo(2);
        assertThat(modifiers.getLegendaryUnitId()).isNull();
        assertThat(modifiers.getSynergyAmp()).isEmpty();
        assertThat(modifiers.isShopPoolRestricted()).isTrue();
        assertThat(modifiers.isShopAllowed("u_basic")).isTrue();
        assertThat(modifiers.isShopAllowed("u_leg_g")).isFalse();
    }

    @Test
    @DisplayName("null 入参防御：profile/data/cause 任一为 null 即抛 NullPointerException")
    void rejectsNullArguments() {
        assertThatThrownBy(() -> ProfileService.masteryLevel(null, "h"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ProfileService.unlockedSceneIds(Profile.fresh(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ProfileService.settle(Profile.fresh(), data(),
                "h", null, null, 1, 3))
                .isInstanceOf(NullPointerException.class);
    }
}
