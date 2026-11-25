package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;

/**
 * 运行模式（品质改造阶段一 §0.1/§0.2）：正式远征 / 999 金测试档 / 对拍（测试样片）。
 *
 * <p>不可变模式参数——由界面（LoadingScreen / 测试样片菜单 → BattleScreen）传入并冻结进
 * {@link com.voidvvv.kz_auto_chess_n.command.RunContext}；既有构造器一律默认 {@link #NORMAL}
 * （正式模式），存量调用方零改动。
 *
 * <p>测试档口径（§0.1）：固定森林、无英雄修正（{@link RunModifiers#EMPTY}）、恰好 999 金、
 * 跳过全部档案写入，只作用于启动参数指定的第一个 Screen。
 * 对拍口径（§0.2）：主菜单「测试样片」进入，固定森林 / 无英雄 / EMPTY 修正 / 10 金 / 空名册，
 * 固定敌阵与商店表（{@code systems/PaiTestScript}）+ 种子公式，同样关闭档案轨。
 */
public enum RunMode {
    /** 正式远征：{@link GameBalance#START_GOLD} 基线 + 局外加成（裁决 D2），档案轨全开 */
    NORMAL,
    /** 测试档（999 金）：LWJGL3 启动参数 {@code --test-gold=999}（或无值形式）指定的首屏测试局 */
    TEST_PERIOD_999,
    /** 对拍局：主菜单「测试样片」选题与种子后进入的真实 BattleScreen（§0.2） */
    SAMPLE_DUEL;

    /** 是否测试档（999 金标识 / 起始金口径的判定入口） */
    public boolean isTestPeriod() {
        return this == TEST_PERIOD_999;
    }

    /** 是否对拍局（固定题表 / 种子公式 / 轮次上限的判定入口） */
    public boolean isSampleDuel() {
        return this == SAMPLE_DUEL;
    }

    /**
     * 档案轨是否关闭（品质改造 §0.1/§0.2）：测试档与对拍局一律不写快照、不补写、不结算、不删档——
     * 含正式快照的档案进出两类测试局前后字节一致；正式「继续远征」仍按快照原值恢复。
     */
    public boolean closesArchiveTrack() {
        return this != NORMAL;
    }

    /**
     * 启动参数解析结果 → 本次加载结束后的首屏运行模式（品质改造 §0.1「无参及其它数值使用正常入口」）。
     * 仅 999 档形态（{@code --test-gold} 无值 / {@code --test-gold=999}）进入测试档；
     * 无参及其它数值一律正式入口（对拍不经启动参数，只经主菜单）。入参显式注入
     * （{@link GameBalance#testStartGoldOverride} 的只读快照），本类不读全局态。
     */
    public static RunMode fromLaunchOverride(int testStartGoldOverride) {
        return testStartGoldOverride == GameBalance.TEST_PERIOD_START_GOLD
                ? TEST_PERIOD_999 : NORMAL;
    }
}
