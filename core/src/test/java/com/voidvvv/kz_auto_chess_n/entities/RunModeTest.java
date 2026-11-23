package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 品质改造批1：运行模式（正式 / 999 金测试档）的判定与启动参数映射口径（§0.1）。 */
@DisplayName("RunMode：运行模式判定与启动参数映射（品质改造 §0.1）")
class RunModeTest {

    @Test
    @DisplayName("isTestPeriod：仅 TEST_PERIOD_999 为测试档，NORMAL 恒正式")
    void isTestPeriodOnlyForTestMode() {
        assertThat(RunMode.NORMAL.isTestPeriod()).isFalse();
        assertThat(RunMode.TEST_PERIOD_999.isTestPeriod()).isTrue();
    }

    @Test
    @DisplayName("启动参数映射：--test-gold=999 / 无值形式（override = 999）→ 测试档首屏")
    void launchOverride999MapsToTestPeriod() {
        assertThat(RunMode.fromLaunchOverride(GameBalance.TEST_PERIOD_START_GOLD))
                .isEqualTo(RunMode.TEST_PERIOD_999);
        assertThat(RunMode.fromLaunchOverride(999)).isEqualTo(RunMode.TEST_PERIOD_999);
    }

    @Test
    @DisplayName("启动参数映射：无参（-1）/其它数值/正式典型值 → 正常入口（设计书 §0.1）")
    void otherOverridesMapToNormal() {
        assertThat(RunMode.fromLaunchOverride(-1)).isEqualTo(RunMode.NORMAL); // 无参 = 关闭态
        assertThat(RunMode.fromLaunchOverride(0)).isEqualTo(RunMode.NORMAL);
        assertThat(RunMode.fromLaunchOverride(GameBalance.START_GOLD)).isEqualTo(RunMode.NORMAL); // 10
        assertThat(RunMode.fromLaunchOverride(777)).isEqualTo(RunMode.NORMAL); // 其它数值 → 正常入口
    }
}
