package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.render.board.BattleEntryCells;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BattleEntryCells 测试（P1c 开战滑入）：玩家部署表 / 敌阵 WaveSpec 建表，
 * 同模板按序消耗、耗尽与未知模板返回 null（调用方落战斗锚点）。
 */
class BattleEntryCellsTest {

    private static UnitData unitData(String id) {
        return new UnitData(id, "测试" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 0, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, false);
    }

    @Test
    @DisplayName("玩家侧：部署表扫描序（y↑x↑）建表，同模板按序消耗、耗尽返回 null")
    void ofDeployedConsumesInScanOrder() {
        UnitData warrior = unitData("unit_warrior_01");
        UnitData assassin = unitData("unit_assassin_01");
        Player player = new Player(10);
        player.addToBench(new Unit(1, warrior, 1));
        player.addToBench(new Unit(2, assassin, 1));
        player.addToBench(new Unit(3, warrior, 1));
        player.deploy(player.getBench().get(0), 2, 5); // warrior id=1 → (2,5)
        player.deploy(player.getBench().get(0), 3, 5); // assassin  → (3,5)
        player.deploy(player.getBench().get(0), 2, 6); // warrior id=3 → (2,6)
        BattleEntryCells entries = BattleEntryCells.ofDeployed(player);
        assertThat(entries.consume("unit_warrior_01")).containsExactly(2, 5); // y=5 行先于 y=6
        assertThat(entries.consume("unit_warrior_01")).containsExactly(2, 6);
        assertThat(entries.consume("unit_warrior_01")).isNull();              // 耗尽
        assertThat(entries.consume("unit_assassin_01")).containsExactly(3, 5);
        assertThat(entries.consume("unit_boss_01")).isNull();                 // 未知模板
    }

    @Test
    @DisplayName("敌方侧：WaveSpec 列表序建表，同模板按序消耗")
    void ofEnemyWaveConsumesInListOrder() {
        UnitData goblin = unitData("unit_goblin_01");
        List<WaveSpec> wave = Arrays.asList(
                new WaveSpec(goblin, 1, 1f, 1, 0),
                new WaveSpec(goblin, 1, 1f, 4, 1));
        BattleEntryCells entries = BattleEntryCells.ofEnemyWave(wave);
        assertThat(entries.consume("unit_goblin_01")).containsExactly(1, 0);
        assertThat(entries.consume("unit_goblin_01")).containsExactly(4, 1);
        assertThat(entries.consume("unit_goblin_01")).isNull();
    }

    @Test
    @DisplayName("纯备战席名单：建表为空，任意模板消耗返回 null（调用方落锚点）")
    void benchOnlyPlayerYieldsNull() {
        Player player = new Player(10);
        player.addToBench(new Unit(1, unitData("unit_warrior_01"), 1));
        BattleEntryCells entries = BattleEntryCells.ofDeployed(player);
        assertThat(entries.consume("unit_warrior_01")).isNull();
    }
}
