package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;

/**
 * 运行模式（品质改造阶段一 §0.1）：正式远征 / 999 金测试档。
 *
 * <p>不可变模式参数——由界面（LoadingScreen → BattleScreen）传入并冻结进
 * {@link com.voidvvv.kz_auto_chess_n.command.RunContext}；既有构造器一律默认 {@link #NORMAL}
 * （正式模式），存量调用方零改动。
 *
 * <p>测试档口径（设计书 §0.1）：固定森林、无英雄修正（{@link RunModifiers#EMPTY}）、
 * 恰好 999 金（不叠加熟练度/英雄加成）、跳过全部档案写入（快照/结算/删档），
 * 只作用于启动参数指定的第一个 Screen——离开该局返回主菜单后，普通开始与继续走正式入口。
 */
public enum RunMode {
    /** 正式远征：{@link GameBalance#START_GOLD} 基线 + 局外加成（裁决 D2），档案轨全开 */
    NORMAL,
    /** 测试档（999 金）：LWJGL3 启动参数 {@code --test-gold=999}（或无值形式）指定的首屏测试局 */
    TEST_PERIOD_999;

    /** 是否测试档（存档隔离 / 顶栏标识 / 终局熟练度行等分支的唯一判定入口） */
    public boolean isTestPeriod() {
        return this == TEST_PERIOD_999;
    }

    /**
     * 启动参数解析结果 → 本次加载结束后的首屏运行模式（品质改造 §0.1「无参及其它数值使用正常入口」）。
     * 仅 999 档形态（{@code --test-gold} 无值 / {@code --test-gold=999}）进入测试档；
     * 无参及其它数值一律正式入口。入参显式注入（{@link GameBalance#testStartGoldOverride} 的只读快照），
     * 本类不读全局态。
     */
    public static RunMode fromLaunchOverride(int testStartGoldOverride) {
        return testStartGoldOverride == GameBalance.TEST_PERIOD_START_GOLD
                ? TEST_PERIOD_999 : NORMAL;
    }
}
