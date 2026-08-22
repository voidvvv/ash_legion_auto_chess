package com.voidvvv.kz_auto_chess_n.screens;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.HeroData;
import com.voidvvv.kz_auto_chess_n.data.HeroPassiveType;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.save.HeroProgress;
import com.voidvvv.kz_auto_chess_n.save.Profile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CodexScreen 纯函数文案测试（CP15）：英雄块四形态（无进度/进行中/满级/传奇解锁态）
 * 与场景块三态（已通关/已解锁/未解锁）。屏体为 Gdx 绑定，逻辑断言下沉包级静态。
 */
class CodexScreenTextTest {

    private static UnitData unit(String id, int cost) {
        return new UnitData(id, "夹具" + id, "人类", "战士", cost,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, false);
    }

    private static GameData dataWithLegendary() {
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        units.put("u_leg", unit("u_leg", 3));
        Map<String, HeroData> heroes = new LinkedHashMap<String, HeroData>();
        heroes.put("h1", new HeroData("h1", "英雄甲", "英雄描述", HeroPassiveType.START_GOLD,
                2f, new ArrayList<String>(), "u_leg"));
        return new GameData(units,
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                new LinkedHashMap<String, SceneData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                heroes, new ArrayList<String>());
    }

    private static HeroData hero() {
        return dataWithLegendary().getHero("h1");
    }

    @Test
    @DisplayName("英雄块无进度：Lv.1（经验 0/50）+ 传奇（Lv.3 解锁）")
    void heroLinesWithoutProgress() {
        GameData data = dataWithLegendary();
        assertThat(CodexScreen.codexHeroLines(hero(), Profile.fresh(), data)).containsExactly(
                "【英雄甲】被动：开局金币 +2 · Lv.1（经验 0/50）",
                "   英雄描述 · 专属传奇：夹具u_leg（Lv.3 解锁）");
    }

    @Test
    @DisplayName("英雄块进行中：Lv.2（经验 35/100）；满级：Lv.5（满级）+ 传奇（已解锁）")
    void heroLinesInProgressAndCapped() {
        GameData data = dataWithLegendary();
        Profile lv2 = Profile.fresh().withHeroProgress("h1", new HeroProgress(2, 35));
        assertThat(CodexScreen.codexHeroLines(hero(), lv2, data).get(0))
                .isEqualTo("【英雄甲】被动：开局金币 +2 · Lv.2（经验 35/100）");

        Profile lv3 = Profile.fresh().withHeroProgress("h1", new HeroProgress(3, 0));
        assertThat(CodexScreen.codexHeroLines(hero(), lv3, data).get(1))
                .isEqualTo("   英雄描述 · 专属传奇：夹具u_leg（已解锁）");

        Profile lv5 = Profile.fresh().withHeroProgress("h1", new HeroProgress(5, 0));
        assertThat(CodexScreen.codexHeroLines(hero(), lv5, data).get(0))
                .isEqualTo("【英雄甲】被动：开局金币 +2 · Lv.5（满级）");
    }

    @Test
    @DisplayName("英雄块无传奇：第二行仅 desc，无专属传奇尾注")
    void heroLinesWithoutLegendary() {
        HeroData plain = new HeroData("h2", "英雄乙", "乙描述", HeroPassiveType.ENERGY_GAIN,
                15f, new ArrayList<String>(), null);
        assertThat(CodexScreen.codexHeroLines(plain, Profile.fresh(), dataWithLegendary()))
                .containsExactly(
                        "【英雄乙】被动：全队能量获取 +15% · Lv.1（经验 0/50）",
                        "   乙描述");
    }

    @Test
    @DisplayName("场景块三态：已通关 / 已解锁未通关 / 未解锁")
    void sceneLinesThreeStates() {
        SceneData scene = new SceneData("scene_crypt", "亡者墓穴", "scene_forest",
                new ArrayList<SceneData.EnemyPoolEntry>(),
                new LinkedHashMap<Integer, String>(), new ArrayList<String>());
        Set<String> unlocked = new LinkedHashSet<String>(Arrays.asList("scene_forest", "scene_crypt"));

        Profile completed = Profile.fresh().withCompletedScene("scene_crypt");
        assertThat(CodexScreen.codexSceneLines(scene, unlocked, completed))
                .containsExactly("【亡者墓穴】已通关");

        Profile fresh = Profile.fresh();
        assertThat(CodexScreen.codexSceneLines(scene, unlocked, fresh))
                .containsExactly("【亡者墓穴】已解锁");

        Set<String> onlyForest = new LinkedHashSet<String>(Arrays.asList("scene_forest"));
        assertThat(CodexScreen.codexSceneLines(scene, onlyForest, fresh))
                .containsExactly("【亡者墓穴】未解锁");
    }
}
