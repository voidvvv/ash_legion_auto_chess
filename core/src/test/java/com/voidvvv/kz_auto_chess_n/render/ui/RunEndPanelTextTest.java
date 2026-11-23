package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import com.voidvvv.kz_auto_chess_n.entities.RunMode;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RunEndPanel 静态文案测试：endTitleText（机会制 CP13）三成因三文案 + null 防御；
 * fallbackMasteryLine（品质改造 §0.1）测试档终局隐藏熟练度行。
 */
class RunEndPanelTextTest {

    @Test
    @DisplayName("endTitleText：COMPLETED 通关 / ABANDONED 已放弃 / DEFEATED 失败 / null 防御回通关")
    void endTitleTextThreeCauses() {
        assertThat(RunEndPanel.endTitleText(RunEndCause.COMPLETED)).isEqualTo("远征通关");
        assertThat(RunEndPanel.endTitleText(RunEndCause.ABANDONED)).isEqualTo("远征已放弃");
        assertThat(RunEndPanel.endTitleText(RunEndCause.DEFEATED)).isEqualTo("远征失败");
        assertThat(RunEndPanel.endTitleText(null)).isEqualTo("远征通关"); // 防御分支
    }

    // —— 品质改造 §0.1：测试档终局隐藏熟练度行 ——

    @Test
    @DisplayName("fallbackMasteryLine：正式模式回退熟练度行（存量口径不变）")
    void normalModeKeepsMasteryLine() {
        assertThat(RunEndPanel.fallbackMasteryLine(runStateWithMastery(9), RunMode.NORMAL))
                .isEqualTo("熟练度 +9");
        assertThat(RunEndPanel.fallbackMasteryLine(runStateWithMastery(0), RunMode.NORMAL))
                .isEqualTo("熟练度 +0");
    }

    @Test
    @DisplayName("fallbackMasteryLine：测试档隐藏熟练度行（null = 不画——不结算档案故无假信号）")
    void testPeriodHidesMasteryLine() {
        assertThat(RunEndPanel.fallbackMasteryLine(runStateWithMastery(9), RunMode.TEST_PERIOD_999))
                .isNull();
    }

    private static RunState runStateWithMastery(int awarded) {
        RunState runState = new RunState(42L, "scene_forest", new SequentialIdIssuer());
        runState.setMasteryAwarded(awarded);
        return runState;
    }
}
