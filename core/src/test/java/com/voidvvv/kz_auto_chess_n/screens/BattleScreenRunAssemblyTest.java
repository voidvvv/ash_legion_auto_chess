package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.RunMode;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.save.Profile;
import com.voidvvv.kz_auto_chess_n.save.ProfileService;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 品质改造批1（§0.1）验收件：起始金按模式结算 + 上下文装配口径 + 存档隔离闸。
 * 纯静态函数直测（沿 BattleScreenStatusLineTest 先例——UI Actor 本体不 headless 测，
 * GL 路径走 lwjgl3 实机手验）；GameData 用真实 data/ 目录（沿 BattleConsoleMain 的
 * FileHandle 纯 JVM 构造先例）。
 */
@DisplayName("BattleScreen 装配：起始金口径 / 测试档上下文 / 存档隔离闸（品质改造 §0.1）")
class BattleScreenRunAssemblyTest {

    private static GameData data;

    @BeforeAll
    static void loadRealData() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    /** 英雄局外修正（新档 = Profile.fresh，熟练度 Lv.1 起步——验收口径的 12/14 事实源） */
    private static RunModifiers modifiersOf(String heroId) {
        return ProfileService.runModifiers(data.getHero(heroId), Profile.fresh(), data);
    }

    // —— 起始金口径（验收：普通新档 12/14、空修正 10、999 档恒 999） ——

    @Test
    @DisplayName("正式口径：空修正 = START_GOLD 10（白板）")
    void normalEmptyModifiersStartsAtTen() {
        assertThat(BattleScreen.startGold(RunMode.NORMAL, RunModifiers.EMPTY)).isEqualTo(10);
    }

    @Test
    @DisplayName("正式口径：薇拉/奥兰多新档 = 12（10 + Lv.1 熟练度基础权益 2）")
    void veraAndOrlandoStartAtTwelve() {
        assertThat(modifiersOf("hero_vera").getStartGoldBonus()).isEqualTo(2);
        assertThat(modifiersOf("hero_orlando").getStartGoldBonus()).isEqualTo(2);
        assertThat(BattleScreen.startGold(RunMode.NORMAL, modifiersOf("hero_vera"))).isEqualTo(12);
        assertThat(BattleScreen.startGold(RunMode.NORMAL, modifiersOf("hero_orlando"))).isEqualTo(12);
    }

    @Test
    @DisplayName("正式口径：格雷克新档 = 14（10 + 熟练度 2 + START_GOLD 被动 2）")
    void gregStartsAtFourteen() {
        assertThat(modifiersOf("hero_greg").getStartGoldBonus()).isEqualTo(4);
        assertThat(BattleScreen.startGold(RunMode.NORMAL, modifiersOf("hero_greg"))).isEqualTo(14);
    }

    @Test
    @DisplayName("测试档口径：恒 999，不叠加熟练度/英雄金币加成（品质改造 §0.1）")
    void testPeriodAlwaysExactly999() {
        assertThat(BattleScreen.startGold(RunMode.TEST_PERIOD_999, RunModifiers.EMPTY)).isEqualTo(999);
        assertThat(BattleScreen.startGold(RunMode.TEST_PERIOD_999, modifiersOf("hero_greg")))
                .isEqualTo(999); // 带加成的修正也不叠加
    }

    // —— 上下文装配 ——

    @Test
    @DisplayName("测试档上下文：999 金 + 固定森林 + 无英雄 + EMPTY 修正 + 模式冻结进上下文")
    void testPeriodContextAssembly() {
        RunContext ctx = BattleScreen.assembleContext(data, new ShopSystem(), 42L,
                BattleScreen.TEST_PERIOD_SCENE_ID, "hero_greg", RunMode.TEST_PERIOD_999,
                modifiersOf("hero_greg")); // 传入英雄修正也不生效——测试档口径内强制 EMPTY
        assertThat(ctx.getMode()).isEqualTo(RunMode.TEST_PERIOD_999);
        assertThat(ctx.getPlayer().getGold()).isEqualTo(999); // 实际余额（前台同源见 TopBarTextTest）
        assertThat(ctx.getRunState().getSceneId()).isEqualTo("scene_forest");
        assertThat(ctx.getRunState().getHeroId()).isNull();
        assertThat(ctx.getRunState().getModifiers()).isSameAs(RunModifiers.EMPTY);
    }

    @Test
    @DisplayName("正式上下文：场景/英雄透传，起始金含局外加成，模式 NORMAL")
    void normalContextAssemblyPassesThrough() {
        RunContext ctx = BattleScreen.assembleContext(data, new ShopSystem(), 42L,
                "scene_crypt", "hero_greg", RunMode.NORMAL, modifiersOf("hero_greg"));
        assertThat(ctx.getMode()).isEqualTo(RunMode.NORMAL);
        assertThat(ctx.getPlayer().getGold()).isEqualTo(14);
        assertThat(ctx.getRunState().getSceneId()).isEqualTo("scene_crypt");
        assertThat(ctx.getRunState().getHeroId()).isEqualTo("hero_greg");
    }

    @Test
    @DisplayName("场景防御回退：null / 未知场景 → 首场景（data 声明序，既有口径保持）")
    void sceneFallbackToFirst() {
        RunContext nullScene = BattleScreen.assembleContext(data, new ShopSystem(), 42L,
                null, null, RunMode.NORMAL, RunModifiers.EMPTY);
        RunContext unknownScene = BattleScreen.assembleContext(data, new ShopSystem(), 42L,
                "scene_missing", null, RunMode.TEST_PERIOD_999, RunModifiers.EMPTY);
        String firstScene = data.getScenes().keySet().iterator().next();
        assertThat(nullScene.getRunState().getSceneId()).isEqualTo(firstScene);
        assertThat(unknownScene.getRunState().getSceneId()).isEqualTo(firstScene);
    }

    // —— 存档隔离闸 ——

    @Test
    @DisplayName("存档隔离闸：测试局旁路全部档案写入（快照/补写/结算/删档），正式局全开")
    void archiveGateBranchesByMode() {
        assertThat(BattleScreen.archiveWritesAllowed(RunMode.NORMAL)).isTrue();
        assertThat(BattleScreen.archiveWritesAllowed(RunMode.TEST_PERIOD_999)).isFalse();
    }
}
