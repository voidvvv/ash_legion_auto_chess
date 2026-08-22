package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.GameCommand;
import com.voidvvv.kz_auto_chess_n.command.MoveUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.SellUnitCommand;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 名单系统测试（CP12）：MoveUnit 自 RunFlowSystem 迁入后的行为一致性（席↔板/交换/门控）+
 * SellUnit 板/席两路径（返还 spend 100%、装备自动卸下、板上卖出释放人口）。
 */
class RosterSystemTest {

    // —— 夹具 ——

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, false);
    }

    private static GameData emptyData() {
        return new GameData(new LinkedHashMap<String, UnitData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                new LinkedHashMap<String, EquipmentData>(), new ArrayList<String>());
    }

    private static RunContext context(Player player) {
        return new RunContext(player, new RunState(42L, "scene_forest", new SequentialIdIssuer()),
                emptyData(), new RandomGenerator(42L));
    }

    private static CommandManager armed() {
        CommandManager manager = new CommandManager();
        new RosterSystem().registerHandlers(manager);
        return manager;
    }

    private static List<GameCommand> executedTracker(CommandManager manager) {
        final List<GameCommand> executed = new ArrayList<GameCommand>();
        manager.addListener((cmd, success) -> executed.add(cmd));
        return executed;
    }

    // —— MoveUnit 迁移一致性（口径与 MoveUnitExecutorTest 对齐） ——

    @Test
    @DisplayName("MoveUnit 席→板：经命令链上阵指定格，备战席同步清空")
    void moveBenchToBoard() {
        Player player = new Player(0);
        Unit unit = new Unit(1, tpl("u1"), 1);
        player.addToBench(unit);
        RunContext ctx = context(player);
        CommandManager manager = armed();
        List<GameCommand> executed = executedTracker(manager);
        manager.addCommand(new MoveUnitCommand(1, new PlacementTarget.Cell(2, 4)));
        manager.executeAll(ctx);
        assertThat(executed).hasSize(1);
        assertThat(player.deployedAt(2, 4)).isSameAs(unit);
        assertThat(player.getBench()).isEmpty();
    }

    @Test
    @DisplayName("MoveUnit 板→板交换：目标格有己方单位时互易位")
    void moveSwapsDeployedUnits() {
        Player player = new Player(0);
        Unit a = new Unit(1, tpl("u1"), 1);
        Unit b = new Unit(2, tpl("u2"), 1);
        player.addToBench(a);
        player.addToBench(b);
        player.deploy(a, 1, 4);
        player.deploy(b, 2, 4);
        RunContext ctx = context(player);
        CommandManager manager = armed();
        manager.addCommand(new MoveUnitCommand(1, new PlacementTarget.Cell(2, 4)));
        manager.executeAll(ctx);
        assertThat(player.deployedAt(2, 4)).isSameAs(a);
        assertThat(player.deployedAt(1, 4)).isSameAs(b);
    }

    @Test
    @DisplayName("门控矩阵：BATTLE 期 MoveUnit 拒绝（位置零变化）")
    void battlePhaseRejectsMove() {
        Player player = new Player(0);
        Unit unit = new Unit(1, tpl("u1"), 1);
        player.addToBench(unit);
        RunContext ctx = context(player);
        ctx.getRunState().setPhase(GamePhase.BATTLE);
        CommandManager manager = armed();
        List<GameCommand> executed = executedTracker(manager);
        manager.addCommand(new MoveUnitCommand(1, new PlacementTarget.Cell(2, 4)));
        manager.executeAll(ctx);
        assertThat(executed).isEmpty();
        assertThat(player.getBench()).containsExactly(unit);
        assertThat(player.getDeployedUnits()).isEmpty();
    }

    // —— SellUnit ——

    @Test
    @DisplayName("SellUnit 席上卖出：金币 +spend、名单移除、产生卖出通知")
    void sellFromBench() {
        Player player = new Player(5);
        Unit unit = new Unit(1, tpl("u1"), 1, 7);
        player.addToBench(unit);
        RunContext ctx = context(player);
        CommandManager manager = armed();
        List<GameCommand> executed = executedTracker(manager);
        manager.addCommand(new SellUnitCommand(1));
        manager.executeAll(ctx);
        assertThat(executed).hasSize(1);
        assertThat(player.getGold()).isEqualTo(12); // 5 + spend 7（100% 返还）
        assertThat(player.getBench()).isEmpty();
        List<String> notices = ctx.getRunState().drainNotices();
        assertThat(notices).anyMatch(line -> line.contains("卖出") && line.contains("+7"));
    }

    @Test
    @DisplayName("SellUnit 板上卖出：部署格清空（人口释放）、装备自动卸下回背包、金币 +spend")
    void sellFromBoardWithEquipment() {
        Player player = new Player(0);
        Unit unit = new Unit(1, tpl("u1"), 1, 3);
        player.addToBench(unit);
        player.deploy(unit, 3, 5);
        Equipment sword = new Equipment(11, new EquipmentData("eq_sword", "装eq_sword",
                EquipmentSlot.WEAPON, EquipmentRarity.WHITE,
                Arrays.asList(new EquipmentEffect(StatKey.ATTACK, EffectOp.ADD, 1f)), null));
        unit.equip(sword); // 测试充当 systems 层直接穿戴
        RunContext ctx = context(player);
        CommandManager manager = armed();
        manager.addCommand(new SellUnitCommand(1));
        manager.executeAll(ctx);
        assertThat(player.getGold()).isEqualTo(3);
        assertThat(player.deployedAt(3, 5)).isNull(); // 板上格清空
        assertThat(player.getDeployedUnits()).isEmpty();
        assertThat(player.getInventory()).containsExactly(sword); // 装备不随棋子消失
        assertThat(player.getUnitById(1)).isNull();
    }

    @Test
    @DisplayName("SellUnit 拒绝：unitId 不在名单")
    void sellRejectedWhenUnitMissing() {
        Player player = new Player(0);
        player.addToBench(new Unit(1, tpl("u1"), 1, 1));
        RunContext ctx = context(player);
        CommandManager manager = armed();
        List<GameCommand> executed = executedTracker(manager);
        manager.addCommand(new SellUnitCommand(999));
        manager.executeAll(ctx);
        assertThat(executed).isEmpty();
        assertThat(player.getGold()).isZero();
        assertThat(player.getBench()).hasSize(1);
    }

    @Test
    @DisplayName("门控矩阵：BATTLE 期 SellUnit 拒绝（棋子与金币零变化）")
    void battlePhaseRejectsSell() {
        Player player = new Player(0);
        Unit unit = new Unit(1, tpl("u1"), 1, 5);
        player.addToBench(unit);
        RunContext ctx = context(player);
        ctx.getRunState().setPhase(GamePhase.BATTLE);
        CommandManager manager = armed();
        List<GameCommand> executed = executedTracker(manager);
        manager.addCommand(new SellUnitCommand(1));
        manager.executeAll(ctx);
        assertThat(executed).isEmpty();
        assertThat(player.getGold()).isZero();
        assertThat(player.getBench()).containsExactly(unit);
    }
}
