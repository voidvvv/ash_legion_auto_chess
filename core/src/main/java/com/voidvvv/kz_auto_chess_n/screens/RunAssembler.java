package com.voidvvv.kz_auto_chess_n.screens;

import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunMode;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.systems.PaiTestScript;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.util.Objects;

/**
 * 运行上下文装配（品质改造 §0.1/§0.2）：screens 唯一装配点的纯函数层（零 GL——JVM 直测），
 * 从 {@code BattleScreen} 按职责拆出。三种装配入口各自对应一个批次口径：
 * <ul>
 *   <li>正式（RunSetup 域边界）：{@code START_GOLD} 基线 + 局外加成（裁决 D2），场景/英雄透传；</li>
 *   <li>测试档（启动参数首屏，§0.1）：固定森林 / 无英雄 / EMPTY 修正 / 恒 999 金；</li>
 *   <li>对拍（测试样片菜单，§0.2）：固定森林 / 无英雄 / EMPTY 修正 / 10 金（START_GOLD，Q4 恰好口径）。</li>
 * </ul>
 *
 * <p>口径单点：非正式局强制无英雄 + EMPTY 修正（调用方误传在此丢弃）；起始金按模式结算；
 * 顶栏标题在此投影（测试档「测试档 999」/ 对拍「对拍 A · 42」）。
 */
public final class RunAssembler {

    /** 非正式局固定场景（§0.1「999 档固定森林」/ §0.2 对拍固定森林） */
    static final String FIXED_SCENE_ID = "scene_forest";
    /** 测试档顶栏标题（§0.1） */
    static final String TEST_PERIOD_TITLE = "测试档 999";

    private RunAssembler() {
    }

    /** 正式局：场景/英雄透传（RunSetup 域边界三参），起始金含局外加成 */
    public static RunContext assembleNormal(GameData data, ShopSystem shop, long runSeed, String sceneId,
                                     String heroId, RunModifiers modifiers) {
        return assemble(data, shop, runSeed, sceneId, heroId, RunMode.NORMAL, null, modifiers);
    }

    /** 测试档局（§0.1）：固定森林 / 无英雄 / EMPTY 修正 / 恰好 999 金，不读档案 */
    public static RunContext assembleTestPeriod(GameData data, ShopSystem shop, long runSeed) {
        return assemble(data, shop, runSeed, FIXED_SCENE_ID, null,
                RunMode.TEST_PERIOD_999, null, RunModifiers.EMPTY);
    }

    /** 对拍局（§0.2）：固定森林 / 无英雄 / EMPTY 修正 / 恰好 10 金 / 题号随上下文冻结 */
    public static RunContext assembleSampleDuel(GameData data, ShopSystem shop, long runSeed,
                                         PaiTestScript.Topic topic) {
        Objects.requireNonNull(topic, "对拍局缺少题号");
        return assemble(data, shop, runSeed, FIXED_SCENE_ID, null,
                RunMode.SAMPLE_DUEL, topic, RunModifiers.EMPTY);
    }

    /** 装配核心：场景防御回退首场景 + 非正式局强制口径 + 起始金按模式结算 */
    private static RunContext assemble(GameData data, ShopSystem shop, long runSeed, String sceneId,
                                       String heroId, RunMode mode, PaiTestScript.Topic topic,
                                       RunModifiers modifiers) {
        String resolvedScene = sceneId != null && data.getScene(sceneId) != null
                ? sceneId : data.getScenes().keySet().iterator().next(); // 防御回退首场景
        boolean formal = mode == RunMode.NORMAL;
        RunModifiers effectiveModifiers = formal ? modifiers : RunModifiers.EMPTY;
        String effectiveHero = formal ? heroId : null; // 测试档与对拍局无英雄（修正 EMPTY 同源）
        return new RunContext(new Player(startGold(mode, effectiveModifiers)),
                new RunState(runSeed, resolvedScene, effectiveHero, effectiveModifiers,
                        new SequentialIdIssuer()),
                data, new RandomGenerator(runSeed), shop, mode, topic);
    }

    /**
     * 新局起始金（品质改造 §0.1/§0.2）：测试档恒 {@link GameBalance#TEST_PERIOD_START_GOLD}=999
     * （不叠加熟练度/英雄加成）；对拍与正式 = {@link GameBalance#START_GOLD} 基线 + 局外加成
     * （对拍修正恒 EMPTY → 恰 10 金——Q4 恰好口径；正式白板 10 金、薇拉/奥兰多 12、格雷克 14）。
     * 测试档/对拍退出后的正式新局不受启动参数残留影响。
     */
    static int startGold(RunMode mode, RunModifiers modifiers) {
        return mode.isTestPeriod()
                ? GameBalance.TEST_PERIOD_START_GOLD
                : GameBalance.START_GOLD + modifiers.getStartGoldBonus();
    }
}
