package com.voidvvv.kz_auto_chess_n.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 品质改造批0：同种子对拍入口的确定性验证（stage1 §0.2——同 seed 双跑事件流逐位一致）。 */
@DisplayName("BattleConsoleMain 同种子对拍（品质改造批0）")
class BattleConsoleMainTest {

    private static final String DATA_DIR = "../assets/data";

    @Test
    @DisplayName("同 seed（42）双调 renderBattle：返回值逐位一致且含事件流与结局行")
    void sameSeedRendersIdenticalStream() {
        String first = BattleConsoleMain.renderBattle(42, 5, DATA_DIR);
        String second = BattleConsoleMain.renderBattle(42, 5, DATA_DIR);
        assertThat(first).isEqualTo(second).isNotEmpty();
        assertThat(first).contains("outcome=").contains("--- 事件流 ---").contains("--- 阵容表 ---");
    }

    @Test
    @DisplayName("不同 seed 产出不同流：对拍能区分局面")
    void differentSeedRendersDifferentStream() {
        assertThat(BattleConsoleMain.renderBattle(43, 5, DATA_DIR))
                .isNotEqualTo(BattleConsoleMain.renderBattle(42, 5, DATA_DIR));
    }
}
