package com.voidvvv.kz_auto_chess_n.save;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RunSettlementText 结算文案测试（CP14，裁决 D6 = RunResult 形态 MVP）：
 * 未升级 / 升级 / 满级 / 带新解锁 四形态行数与文案。
 */
class RunSettlementTextTest {

    private static com.voidvvv.kz_auto_chess_n.data.GameData dataWithScene(String sceneId, String name) {
        SceneData scene = new SceneData(sceneId, name, null,
                new ArrayList<SceneData.EnemyPoolEntry>(), new LinkedHashMap<Integer, String>(),
                new ArrayList<String>());
        java.util.Map<String, SceneData> scenes = new LinkedHashMap<String, SceneData>();
        scenes.put(sceneId, scene);
        return new com.voidvvv.kz_auto_chess_n.data.GameData(
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.UnitData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                scenes,
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.HeroData>(),
                new ArrayList<String>());
    }

    @Test
    @DisplayName("未升级形态：熟练度行 + 当前级行（无升级行/无解锁行）")
    void linesWithoutLevelUp() {
        Profile profile = Profile.fresh().withHeroProgress("h1", new HeroProgress(1, 10));
        ProfileService.Settlement s = ProfileService.settle(profile, dataWithScene("sc", "场景甲"),
                "h1", null, RunEndCause.ABANDONED, 1, 3);
        assertThat(RunSettlementText.lines(s, dataWithScene("sc", "场景甲"))).containsExactly(
                "熟练度 +3",
                "当前 Lv.1（经验 13/50）");
    }

    @Test
    @DisplayName("升级形态：熟练度 + 升级行 + 当前级行")
    void linesWithLevelUp() {
        ProfileService.Settlement s = ProfileService.settle(Profile.fresh(),
                dataWithScene("sc", "场景甲"), "h1", null, RunEndCause.ABANDONED, 1, 135);
        assertThat(RunSettlementText.lines(s, dataWithScene("sc", "场景甲"))).containsExactly(
                "熟练度 +135",
                "英雄等级 Lv.1 → Lv.2",
                "当前 Lv.2（经验 85/100）");
    }

    @Test
    @DisplayName("满级形态：升级行 + 已满行（无当前经验行）")
    void linesAtCap() {
        Profile profile = Profile.fresh().withHeroProgress("h1", new HeroProgress(5, 0));
        ProfileService.Settlement s = ProfileService.settle(profile, dataWithScene("sc", "场景甲"),
                "h1", null, RunEndCause.ABANDONED, 1, 100);
        assertThat(RunSettlementText.lines(s, dataWithScene("sc", "场景甲"))).containsExactly(
                "熟练度 +100",
                "英雄等级已满（Lv.5）");
    }

    @Test
    @DisplayName("带新解锁形态：通关森林 → 解锁场景行追加（场景名解析，悬空回退 id）")
    void linesWithNewlyUnlockedScene() {
        SceneData forest = new SceneData("scene_forest", "翡翠林地", null,
                new ArrayList<SceneData.EnemyPoolEntry>(), new LinkedHashMap<Integer, String>(),
                new ArrayList<String>());
        SceneData crypt = new SceneData("scene_crypt", "亡者墓穴", "scene_forest",
                new ArrayList<SceneData.EnemyPoolEntry>(), new LinkedHashMap<Integer, String>(),
                new ArrayList<String>());
        java.util.Map<String, SceneData> scenes = new LinkedHashMap<String, SceneData>();
        scenes.put("scene_forest", forest);
        scenes.put("scene_crypt", crypt);
        com.voidvvv.kz_auto_chess_n.data.GameData data =
                new com.voidvvv.kz_auto_chess_n.data.GameData(
                        new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.UnitData>(),
                        new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                        new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                        scenes,
                        new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                        new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.HeroData>(),
                        new ArrayList<String>());
        ProfileService.Settlement s = ProfileService.settle(Profile.fresh(), data,
                "h1", "scene_forest", RunEndCause.COMPLETED,
                GameBalance.TOTAL_ROUNDS,
                GameBalance.MASTERY_COMPLETE_BONUS + GameBalance.TOTAL_ROUNDS * 3);
        assertThat(RunSettlementText.lines(s, data)).containsExactly(
                "熟练度 +135",
                "英雄等级 Lv.1 → Lv.2",
                "当前 Lv.2（经验 85/100）",
                "解锁场景：亡者墓穴");
    }
}
