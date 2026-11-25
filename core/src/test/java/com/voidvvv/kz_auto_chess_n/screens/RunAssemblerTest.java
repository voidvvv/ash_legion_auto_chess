package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.RunMode;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.save.Profile;
import com.voidvvv.kz_auto_chess_n.save.ProfileService;
import com.voidvvv.kz_auto_chess_n.systems.PaiTestScript;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 运行上下文装配验收件（品质改造 §0.1/§0.2；批 1 的 BattleScreenRunAssemblyTest 随装配类拆出
 * 改名吸收）：起始金按模式结算 + 三种装配口径 + 存档隔离闸 + 模式不变量。纯静态函数直测
 * （UI Actor 本体不 headless 测，GL 路径走 lwjgl3 实机手验）；GameData 用真实 data/ 目录。
 */
@DisplayName("RunAssembler 装配：起始金口径 / 三种装配 / 存档隔离闸（品质改造 §0.1/§0.2）")
class RunAssemblerTest {

    private static GameData data;

    @BeforeAll
    static void loadRealData() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    /** 英雄局外修正（新档 = Profile.fresh，熟练度 Lv.1 起步——验收口径的 12/14 事实源） */
    private static RunModifiers modifiersOf(String heroId) {
        return ProfileService.runModifiers(data.getHero(heroId), Profile.fresh(), data);
    }

    // —— 起始金口径（验收：普通新档 12/14、空修正 10、999 档恒 999、对拍 10） ——

    @Test
    @DisplayName("正式口径：空修正 = START_GOLD 10（白板）")
    void normalEmptyModifiersStartsAtTen() {
        assertThat(RunAssembler.startGold(RunMode.NORMAL, RunModifiers.EMPTY)).isEqualTo(10);
    }

    @Test
    @DisplayName("正式口径：薇拉/奥兰多新档 = 12（10 + Lv.1 熟练度基础权益 2）")
    void veraAndOrlandoStartAtTwelve() {
        assertThat(modifiersOf("hero_vera").getStartGoldBonus()).isEqualTo(2);
        assertThat(modifiersOf("hero_orlando").getStartGoldBonus()).isEqualTo(2);
        assertThat(RunAssembler.startGold(RunMode.NORMAL, modifiersOf("hero_vera"))).isEqualTo(12);
        assertThat(RunAssembler.startGold(RunMode.NORMAL, modifiersOf("hero_orlando"))).isEqualTo(12);
    }

    @Test
    @DisplayName("正式口径：格雷克新档 = 14（10 + 熟练度 2 + START_GOLD 被动 2）")
    void gregStartsAtFourteen() {
        assertThat(modifiersOf("hero_greg").getStartGoldBonus()).isEqualTo(4);
        assertThat(RunAssembler.startGold(RunMode.NORMAL, modifiersOf("hero_greg"))).isEqualTo(14);
    }

    @Test
    @DisplayName("测试档口径：恒 999，不叠加熟练度/英雄金币加成（品质改造 §0.1）")
    void testPeriodAlwaysExactly999() {
        assertThat(RunAssembler.startGold(RunMode.TEST_PERIOD_999, RunModifiers.EMPTY)).isEqualTo(999);
        assertThat(RunAssembler.startGold(RunMode.TEST_PERIOD_999, modifiersOf("hero_greg")))
                .isEqualTo(999); // 带加成的修正也不叠加
    }

    // —— 装配口径 ——

    @Test
    @DisplayName("测试档装配：999 金 + 固定森林 + 无英雄 + EMPTY 修正 + 标题「测试档 999」")
    void testPeriodAssembly() {
        RunContext ctx = RunAssembler.assembleTestPeriod(data, new ShopSystem(), 42L);
        assertThat(ctx.getMode()).isEqualTo(RunMode.TEST_PERIOD_999);
        assertThat(ctx.getPlayer().getGold()).isEqualTo(999);
        assertThat(ctx.getRunState().getSceneId()).isEqualTo(RunAssembler.FIXED_SCENE_ID);
        assertThat(ctx.getRunState().getHeroId()).isNull();
        assertThat(ctx.getRunState().getModifiers()).isSameAs(RunModifiers.EMPTY);
        assertThat(ctx.getHudTitle()).isEqualTo("测试档 999");
        assertThat(ctx.totalRounds()).isEqualTo(25); // 测试档仍是 25 轮远征
        assertThat(ctx.getMode().closesArchiveTrack()).isTrue();
    }

    @Test
    @DisplayName("对拍装配：10 金 + 固定森林 + 无英雄 + EMPTY 修正 + 题号与标题「对拍 A · 42」+ 3 轮")
    void sampleDuelAssembly() {
        RunContext ctx = RunAssembler.assembleSampleDuel(data, new ShopSystem(), 42L,
                PaiTestScript.Topic.A);
        assertThat(ctx.getMode()).isEqualTo(RunMode.SAMPLE_DUEL);
        assertThat(ctx.getPlayer().getGold())
                .as("对拍 10 金 = START_GOLD（Q4 恰好口径）").isEqualTo(10);
        assertThat(ctx.getRunState().getSceneId()).isEqualTo(RunAssembler.FIXED_SCENE_ID);
        assertThat(ctx.getRunState().getHeroId()).isNull();
        assertThat(ctx.getRunState().getModifiers()).isSameAs(RunModifiers.EMPTY);
        assertThat(ctx.getSampleTopic()).isEqualTo(PaiTestScript.Topic.A);
        assertThat(ctx.getHudTitle()).isEqualTo("对拍 A · 42");
        assertThat(ctx.totalRounds()).isEqualTo(3);
        assertThat(ctx.getMode().closesArchiveTrack()).isTrue();
    }

    @Test
    @DisplayName("对拍装配缺题号 fail-fast：assembleSampleDuel(null) 抛 NPE")
    void sampleDuelRequiresTopic() {
        assertThatThrownBy(() -> RunAssembler.assembleSampleDuel(data, new ShopSystem(), 42L, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("正式装配：场景/英雄透传，起始金含局外加成，无标题，档案轨全开")
    void normalAssemblyPassesThrough() {
        RunContext ctx = RunAssembler.assembleNormal(data, new ShopSystem(), 42L,
                "scene_crypt", "hero_greg", modifiersOf("hero_greg"));
        assertThat(ctx.getMode()).isEqualTo(RunMode.NORMAL);
        assertThat(ctx.getPlayer().getGold()).isEqualTo(14);
        assertThat(ctx.getRunState().getSceneId()).isEqualTo("scene_crypt");
        assertThat(ctx.getRunState().getHeroId()).isEqualTo("hero_greg");
        assertThat(ctx.getHudTitle()).isNull();
        assertThat(ctx.totalRounds()).isEqualTo(25);
        assertThat(ctx.getMode().closesArchiveTrack()).isFalse();
        assertThat(ctx.getSampleTopic()).isNull();
    }

    @Test
    @DisplayName("场景防御回退：null / 未知场景 → 首场景（data 声明序，既有口径保持）")
    void sceneFallbackToFirst() {
        RunContext nullScene = RunAssembler.assembleNormal(data, new ShopSystem(), 42L,
                null, null, RunModifiers.EMPTY);
        RunContext unknownScene = RunAssembler.assembleNormal(data, new ShopSystem(), 42L,
                "scene_missing", null, RunModifiers.EMPTY);
        String firstScene = data.getScenes().keySet().iterator().next();
        assertThat(nullScene.getRunState().getSceneId()).isEqualTo(firstScene);
        assertThat(unknownScene.getRunState().getSceneId()).isEqualTo(firstScene);
    }

    // —— 存档隔离闸（批 1 闸 + §0.2 对拍并入） ——

    @Test
    @DisplayName("存档隔离闸：测试档与对拍局旁路全部档案写入，正式局全开（closesArchiveTrack）")
    void archiveGateBranchesByMode() {
        assertThat(RunMode.NORMAL.closesArchiveTrack()).isFalse();
        assertThat(RunMode.TEST_PERIOD_999.closesArchiveTrack()).isTrue();
        assertThat(RunMode.SAMPLE_DUEL.closesArchiveTrack()).isTrue();
        assertThat(BattleScreen.archiveWritesAllowed(RunMode.NORMAL)).isTrue();
        assertThat(BattleScreen.archiveWritesAllowed(RunMode.TEST_PERIOD_999)).isFalse();
        assertThat(BattleScreen.archiveWritesAllowed(RunMode.SAMPLE_DUEL)).isFalse();
    }

    @Test
    @DisplayName("启动参数映射不受影响：对拍不经启动参数（无参 / 其它数值 → 正常入口）")
    void launchOverrideUnaffected() {
        assertThat(RunMode.fromLaunchOverride(-1)).isEqualTo(RunMode.NORMAL);
        assertThat(RunMode.fromLaunchOverride(999)).isEqualTo(RunMode.TEST_PERIOD_999);
        assertThat(RunMode.fromLaunchOverride(777)).isEqualTo(RunMode.NORMAL);
    }
}
