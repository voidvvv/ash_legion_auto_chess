package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MoveUnitExecutor 测试：bench↔board / 交换 / 人口上限与全部拒绝路径（口径 #8/#9/#26）。
 * 失败路径断言零状态残留（操作原子性）。
 */
class MoveUnitExecutorTest {

    private static final MoveUnitExecutor EXECUTOR = new MoveUnitExecutor();

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "skill_warcry", false);
    }

    /** 入席并部署到 (x,y) 的辅助（沿 BattleConsoleMain.buyAndDeploy 先例） */
    private static Unit benchAndDeploy(Player player, int id, int x, int y) {
        Unit unit = new Unit(id, tpl("u" + id), 1);
        player.addToBench(unit);
        if (x >= 0) {
            player.deploy(unit, x, y);
        }
        return unit;
    }

    // —— 成功路径 ——

    @Test
    @DisplayName("bench→board 空格：部署成功并自动摘席")
    void benchToEmptyCellDeploys() {
        Player player = new Player(10);
        Unit a = benchAndDeploy(player, 1, -1, -1);
        assertThat(EXECUTOR.move(player, a.getId(), new PlacementTarget.Cell(2, 5))).isTrue();
        assertThat(player.deployedAt(2, 5)).isSameAs(a);
        assertThat(player.getBench()).isEmpty();
        assertThat(player.getRosterSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("bench→board 占用格：交换，原板员去 bench 源槽")
    void benchToOccupiedCellSwapsIntoSourceSlot() {
        Player player = new Player(10);
        Unit boardUnit = benchAndDeploy(player, 1, 2, 5);
        Unit filler = benchAndDeploy(player, 2, -1, -1);
        player.addToBench(new Unit(99, tpl("u99"), 1)); // bench: [filler(2), filler99]
        assertThat(EXECUTOR.move(player, filler.getId(), new PlacementTarget.Cell(2, 5))).isTrue();
        assertThat(player.deployedAt(2, 5)).isSameAs(filler);
        assertThat(player.getBench()).containsExactly(boardUnit, player.getBench().get(1)); // 原板员落源槽 0
        assertThat(player.getBench().get(0)).isSameAs(boardUnit);
    }

    @Test
    @DisplayName("board→board 空格：走位成功")
    void boardToEmptyCellMoves() {
        Player player = new Player(10);
        Unit a = benchAndDeploy(player, 1, 2, 5);
        assertThat(EXECUTOR.move(player, a.getId(), new PlacementTarget.Cell(4, 6))).isTrue();
        assertThat(player.deployedAt(2, 5)).isNull();
        assertThat(player.deployedAt(4, 6)).isSameAs(a);
        assertThat(player.getBench()).isEmpty();
    }

    @Test
    @DisplayName("board→board 占用格：互易位（交换）")
    void boardToOccupiedCellSwaps() {
        Player player = new Player(10);
        Unit a = benchAndDeploy(player, 1, 2, 5);
        Unit b = benchAndDeploy(player, 2, 4, 6);
        assertThat(EXECUTOR.move(player, a.getId(), new PlacementTarget.Cell(4, 6))).isTrue();
        assertThat(player.deployedAt(4, 6)).isSameAs(a);
        assertThat(player.deployedAt(2, 5)).isSameAs(b);
        assertThat(player.getBench()).isEmpty();
    }

    @Test
    @DisplayName("board→bench 指定槽：撤下并插入目标槽位")
    void boardToBenchSlotInserts() {
        Player player = new Player(10);
        Unit a = benchAndDeploy(player, 1, 2, 5);
        Unit benchUnit = benchAndDeploy(player, 2, -1, -1);
        assertThat(EXECUTOR.move(player, a.getId(), new PlacementTarget.Bench(0))).isTrue();
        assertThat(player.deployedAt(2, 5)).isNull();
        assertThat(player.getBench()).containsExactly(a, benchUnit);
    }

    @Test
    @DisplayName("bench→bench：remove + insert 换位（合法槽位超当前 size 时钳到末位）")
    void benchToBenchRepositions() {
        Player player = new Player(10);
        Unit a = benchAndDeploy(player, 1, -1, -1);
        Unit b = benchAndDeploy(player, 2, -1, -1);
        Unit c = benchAndDeploy(player, 3, -1, -1);
        assertThat(EXECUTOR.move(player, a.getId(), new PlacementTarget.Bench(2))).isTrue();
        assertThat(player.getBench()).containsExactly(b, c, a);
        assertThat(EXECUTOR.move(player, a.getId(), new PlacementTarget.Bench(8))).isTrue(); // 槽 8 合法，钳到末位（原地）
        assertThat(player.getBench()).containsExactly(b, c, a);
    }

    @Test
    @DisplayName("bench→board 占用格交换不占人口名额（人数不变）")
    void swapDoesNotConsumePopulation() {
        Player player = new Player(10);
        for (int i = 1; i <= 3; i++) { // Lv.1 人口上限 3，全部上场
            benchAndDeploy(player, i, i - 1, 4);
        }
        Unit benchUnit = benchAndDeploy(player, 4, -1, -1);
        assertThat(EXECUTOR.move(player, benchUnit.getId(), new PlacementTarget.Cell(0, 4))).isTrue();
        assertThat(player.getDeployedUnits()).hasSize(3);
        assertThat(player.deployedAt(0, 4)).isSameAs(benchUnit);
    }

    // —— 拒绝路径 ——

    @Test
    @DisplayName("人口上限拒绝：cap=3 已上 3 人，第 4 个 bench→board 空格 false")
    void populationCapRejectsFourthDeploy() {
        Player player = new Player(10);
        for (int i = 1; i <= 3; i++) {
            benchAndDeploy(player, i, i - 1, 4);
        }
        Unit fourth = benchAndDeploy(player, 4, -1, -1);
        assertThat(EXECUTOR.move(player, fourth.getId(), new PlacementTarget.Cell(5, 6))).isFalse();
    }

    @Test
    @DisplayName("bench 满 9 拒绝 board→bench（口径 #26 预校验）")
    void fullBenchRejectsBoardToBench() {
        Player player = new Player(10);
        Unit a = benchAndDeploy(player, 1, 2, 5);
        for (int i = 2; i <= 10; i++) { // 席内 9 个 + 场上 1 个 = 名单 10
            player.addToBench(new Unit(i, tpl("u" + i), 1));
        }
        assertThat(player.getBench()).hasSize(9);
        assertThat(EXECUTOR.move(player, a.getId(), new PlacementTarget.Bench(0))).isFalse();
        assertThat(player.deployedAt(2, 5)).isSameAs(a); // 零残留
    }

    @Test
    @DisplayName("Cell 越界拒绝：缓冲行 y=3、界外 y=7、界外 x=6")
    void cellOutOfPlayerZoneRejected() {
        Player player = new Player(10);
        Unit a = benchAndDeploy(player, 1, -1, -1);
        assertThat(EXECUTOR.move(player, a.getId(), new PlacementTarget.Cell(0, 3))).isFalse();
        assertThat(EXECUTOR.move(player, a.getId(), new PlacementTarget.Cell(0, 7))).isFalse();
        assertThat(EXECUTOR.move(player, a.getId(), new PlacementTarget.Cell(6, 4))).isFalse();
        assertThat(EXECUTOR.move(player, a.getId(), new PlacementTarget.Cell(-1, 4))).isFalse();
        assertThat(player.getBench()).hasSize(1); // 零残留
    }

    @Test
    @DisplayName("Bench 槽位越界拒绝：槽 9 与负数")
    void benchSlotOutOfRangeRejected() {
        Player player = new Player(10);
        Unit a = benchAndDeploy(player, 1, -1, -1);
        assertThat(EXECUTOR.move(player, a.getId(), new PlacementTarget.Bench(9))).isFalse();
        assertThat(EXECUTOR.move(player, a.getId(), new PlacementTarget.Bench(-1))).isFalse();
        assertThat(player.getBench()).containsExactly(a); // 零残留
    }

    @Test
    @DisplayName("unitId 不在名单：false")
    void unknownUnitRejected() {
        Player player = new Player(10);
        benchAndDeploy(player, 1, -1, -1);
        assertThat(EXECUTOR.move(player, 999, new PlacementTarget.Cell(0, 4))).isFalse();
        assertThat(EXECUTOR.move(player, 999, new PlacementTarget.Bench(0))).isFalse();
    }

    @Test
    @DisplayName("人口上限拒绝后零状态残留：bench 与部署表不变")
    void failedMoveLeavesNoPartialState() {
        Player player = new Player(10);
        for (int i = 1; i <= 3; i++) {
            benchAndDeploy(player, i, i - 1, 4);
        }
        Unit fourth = benchAndDeploy(player, 4, -1, -1);
        boolean moved = EXECUTOR.move(player, fourth.getId(), new PlacementTarget.Cell(5, 6));
        assertThat(moved).isFalse();
        assertThat(player.getBench()).containsExactly(fourth);
        assertThat(player.deployedAt(5, 6)).isNull();
        assertThat(player.getDeployedUnits()).hasSize(3);
        assertThat(player.getRosterSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("board→board 同格：无变化幂等成功")
    void boardToSameCellIsIdempotentNoOp() {
        Player player = new Player(10);
        Unit a = benchAndDeploy(player, 1, 2, 5);
        assertThat(EXECUTOR.move(player, a.getId(), new PlacementTarget.Cell(2, 5))).isTrue();
        assertThat(player.deployedAt(2, 5)).isSameAs(a);
        assertThat(player.getBench()).isEmpty();
    }
}
