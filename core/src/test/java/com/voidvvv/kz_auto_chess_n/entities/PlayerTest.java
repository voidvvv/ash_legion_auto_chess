package com.voidvvv.kz_auto_chess_n.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Player（Phase 1 版：金币/经验/等级）测试——GDD §3.5 经验表驱动升级。
 * 名单（备战席/上场部署）随 Unit 实体在 Phase 3 增补（project_structure §六出生时间表）。
 */
class PlayerTest {

    @Test
    @DisplayName("初始状态：起始金币10、1级、0经验、人口上限3")
    void initialState() {
        Player player = new Player(10);
        assertThat(player.getGold()).isEqualTo(10);
        assertThat(player.getLevel()).isEqualTo(1);
        assertThat(player.getCurrentExp()).isEqualTo(0);
        assertThat(player.getPopulationCap()).isEqualTo(3); // Lv.1 人口 3
    }

    @Test
    @DisplayName("加经验恰好升级，余数归零")
    void levelsUpWithExactExp() {
        Player player = new Player(10);
        player.addExp(4); // Lv.1→2 需 4
        assertThat(player.getLevel()).isEqualTo(2);
        assertThat(player.getCurrentExp()).isEqualTo(0);
        assertThat(player.getPopulationCap()).isEqualTo(4);
    }

    @Test
    @DisplayName("加经验跨级保留余数")
    void keepsRemainderExpAcrossLevels() {
        Player player = new Player(10);
        player.addExp(8); // 4 升 Lv.2，余 4 存入 Lv.2（Lv.2→3 需 8）
        assertThat(player.getLevel()).isEqualTo(2);
        assertThat(player.getCurrentExp()).isEqualTo(4);
    }

    @Test
    @DisplayName("大额经验连跳至封顶 Lv.7，封顶后经验不再累积")
    void capsAtMaxLevelAndDiscardsExtraExp() {
        Player player = new Player(10);
        player.addExp(1000);
        assertThat(player.getLevel()).isEqualTo(7);
        assertThat(player.getPopulationCap()).isEqualTo(9); // Lv.7 人口上限 9
        assertThat(player.getCurrentExp()).isEqualTo(0);    // 封顶后余量作废

        player.addExp(4); // 封顶后再买经验无效
        assertThat(player.getLevel()).isEqualTo(7);
        assertThat(player.getCurrentExp()).isEqualTo(0);
    }

    @Test
    @DisplayName("金币增减不透支为负")
    void goldNeverGoesNegative() {
        Player player = new Player(10);
        player.addGold(-5);
        assertThat(player.getGold()).isEqualTo(5);
        player.addGold(-99); // 防御性钳制（命令层已校验，此处兜底）
        assertThat(player.getGold()).isEqualTo(0);
        player.addGold(3);
        assertThat(player.getGold()).isEqualTo(3);
    }

    @Test
    @DisplayName("金币充足判定")
    void canAffordChecksGold() {
        Player player = new Player(4);
        assertThat(player.canAfford(4)).isTrue();
        assertThat(player.canAfford(5)).isFalse();
    }
}
