package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RunEndPanel.endTitleText 静态函数测试（机会制 CP13）：三成因三文案 + null 防御。
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
}
