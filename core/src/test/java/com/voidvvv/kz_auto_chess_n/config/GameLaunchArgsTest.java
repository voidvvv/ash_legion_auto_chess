package com.voidvvv.kz_auto_chess_n.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 品质改造批0：测试档起始金启动参数（{@code --test-gold=<n>}）的解析与生效口径。 */
@DisplayName("启动参数 --test-gold：测试档起始金（品质改造批0）")
class GameLaunchArgsTest {

    @AfterEach
    void resetOverride() {
        GameBalance.applyLaunchArgs(new String[0]); // 无参 = 关闭态，避免静态字段污染其他测试
    }

    @Test
    @DisplayName("缺省：无启动参数时测试档关闭，effectiveStartGold = 正式口径 10")
    void defaultIsOff() {
        GameBalance.applyLaunchArgs(new String[0]);
        assertThat(GameBalance.testStartGoldOverride).isEqualTo(-1);
        assertThat(GameBalance.effectiveStartGold())
                .isEqualTo(GameBalance.START_GOLD).isEqualTo(10);
    }

    @Test
    @DisplayName("--test-gold=999：测试档启用，effectiveStartGold = 999")
    void enablesOverride() {
        GameBalance.applyLaunchArgs(new String[] {"--test-gold=999"});
        assertThat(GameBalance.testStartGoldOverride).isEqualTo(999);
        assertThat(GameBalance.effectiveStartGold()).isEqualTo(999);
    }

    @Test
    @DisplayName("非法值：--test-gold=abc 按未启用处理，回正式口径")
    void invalidValueStaysOff() {
        GameBalance.applyLaunchArgs(new String[] {"--test-gold=abc"});
        assertThat(GameBalance.testStartGoldOverride).isEqualTo(-1);
        assertThat(GameBalance.effectiveStartGold()).isEqualTo(10);
    }

    @Test
    @DisplayName("空参重置：启用后再传无参回到关闭态")
    void emptyArgsReset() {
        GameBalance.applyLaunchArgs(new String[] {"--test-gold=777"});
        assertThat(GameBalance.effectiveStartGold()).isEqualTo(777);
        GameBalance.applyLaunchArgs(new String[0]);
        assertThat(GameBalance.testStartGoldOverride).isEqualTo(-1);
        assertThat(GameBalance.effectiveStartGold()).isEqualTo(10);
    }

    @Test
    @DisplayName("--test-gold（无值）：启用测试档典型值 999")
    void bareFlagUsesTypicalValue() {
        GameBalance.applyLaunchArgs(new String[] {"--test-gold"});
        assertThat(GameBalance.testStartGoldOverride).isEqualTo(999);
        assertThat(GameBalance.effectiveStartGold()).isEqualTo(999);
    }
}
