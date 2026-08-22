package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.EquipItemCommand;
import com.voidvvv.kz_auto_chess_n.command.GameCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.UnequipItemCommand;
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
 * 装备系统测试（CP11；Q1 裁决 A）：穿脱命令 handler 门控与三态拒绝、
 * unequipAll 卖出/合成共用助手的清空与保序入包。
 */
class EquipmentSystemTest {

    // —— 夹具 ——

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, false);
    }

    private static EquipmentData eq(String id, EquipmentSlot slot) {
        return new EquipmentData(id, "装" + id, slot, EquipmentRarity.WHITE,
                Arrays.asList(new EquipmentEffect(StatKey.ATTACK, EffectOp.ADD, 1f)), null);
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

    /** 成功执行的命令记录（CommandManager 仅成功才通知 onExecuted——空列表 ⇔ handler 全拒） */
    private static List<GameCommand> executedTracker(CommandManager manager) {
        final List<GameCommand> executed = new ArrayList<GameCommand>();
        manager.addListener((cmd, success) -> executed.add(cmd));
        return executed;
    }

    private static CommandManager armed(Player player) {
        CommandManager manager = new CommandManager();
        new EquipmentSystem().registerHandlers(manager);
        return manager;
    }

    // —— EquipItem ——

    @Test
    @DisplayName("EquipItem 成功：背包→棋子，槽位由装备类型推导，产生穿戴通知")
    void equipSuccess() {
        Player player = new Player(0);
        Unit unit = new Unit(1, tpl("u1"), 1);
        player.addToBench(unit);
        Equipment sword = new Equipment(11, eq("eq_sword", EquipmentSlot.WEAPON));
        player.addToInventory(sword);
        RunContext ctx = context(player);
        CommandManager manager = armed(player);
        List<GameCommand> executed = executedTracker(manager);
        manager.addCommand(new EquipItemCommand(11, 1));
        manager.executeAll(ctx);
        assertThat(executed).hasSize(1);
        assertThat(unit.equippedIn(EquipmentSlot.WEAPON)).isSameAs(sword);
        assertThat(player.getInventory()).isEmpty();
        assertThat(ctx.getRunState().drainNotices()).anyMatch(line -> line.contains("穿戴"));
    }

    @Test
    @DisplayName("EquipItem 三态拒绝：物品不在包 / 棋子不在名单 / 槽位被占（先卸下，实现口径 #6）")
    void equipRejectedThreeWays() {
        Player player = new Player(0);
        Unit unit = new Unit(1, tpl("u1"), 1);
        player.addToBench(unit);
        Equipment sword = new Equipment(11, eq("eq_sword", EquipmentSlot.WEAPON));
        Equipment anotherSword = new Equipment(12, eq("eq_sword2", EquipmentSlot.WEAPON));
        player.addToInventory(anotherSword);
        RunContext ctx = context(player);

        CommandManager first = armed(player);
        List<GameCommand> firstExecuted = executedTracker(first);
        first.addCommand(new EquipItemCommand(999, 1)); // 物品不在包
        first.executeAll(ctx);
        assertThat(firstExecuted).isEmpty();

        CommandManager second = armed(player);
        List<GameCommand> secondExecuted = executedTracker(second);
        second.addCommand(new EquipItemCommand(12, 999)); // 棋子不在名单
        second.executeAll(ctx);
        assertThat(secondExecuted).isEmpty();
        assertThat(player.getInventory()).containsExactly(anotherSword);

        unit.equip(sword); // 占住武器槽（测试充当 systems 层）
        CommandManager third = armed(player);
        List<GameCommand> thirdExecuted = executedTracker(third);
        third.addCommand(new EquipItemCommand(12, 1)); // 槽位被占
        third.executeAll(ctx);
        assertThat(thirdExecuted).isEmpty();
        assertThat(player.getInventory()).containsExactly(anotherSword);
        assertThat(unit.equippedIn(EquipmentSlot.WEAPON)).isSameAs(sword);
    }

    @Test
    @DisplayName("门控矩阵：BATTLE 期 EquipItem/UnequipItem 均拒绝且零状态变化")
    void battlePhaseRejectsEquipAndUnequip() {
        Player player = new Player(0);
        Unit unit = new Unit(1, tpl("u1"), 1);
        player.addToBench(unit);
        Equipment sword = new Equipment(11, eq("eq_sword", EquipmentSlot.WEAPON));
        player.addToInventory(sword);
        RunContext ctx = context(player);
        ctx.getRunState().setPhase(GamePhase.BATTLE);

        CommandManager manager = armed(player);
        List<GameCommand> executed = executedTracker(manager);
        manager.addCommand(new EquipItemCommand(11, 1));
        manager.addCommand(new UnequipItemCommand(11));
        manager.executeAll(ctx);
        assertThat(executed).isEmpty();
        assertThat(unit.getEquipped()).isEmpty();
        assertThat(player.getInventory()).containsExactly(sword);
    }

    // —— UnequipItem ——

    @Test
    @DisplayName("UnequipItem 成功：穿戴者由名单扫描，装备回背包，产生卸下通知")
    void unequipSuccess() {
        Player player = new Player(0);
        Unit unit = new Unit(1, tpl("u1"), 1);
        player.addToBench(unit);
        Equipment sword = new Equipment(11, eq("eq_sword", EquipmentSlot.WEAPON));
        unit.equip(sword); // 测试充当 systems 层直接穿戴
        RunContext ctx = context(player);
        CommandManager manager = armed(player);
        List<GameCommand> executed = executedTracker(manager);
        manager.addCommand(new UnequipItemCommand(11));
        manager.executeAll(ctx);
        assertThat(executed).hasSize(1);
        assertThat(unit.getEquipped()).isEmpty();
        assertThat(player.getInventory()).containsExactly(sword);
        assertThat(ctx.getRunState().drainNotices()).anyMatch(line -> line.contains("卸下"));
    }

    @Test
    @DisplayName("UnequipItem 拒绝：itemId 无穿戴者")
    void unequipRejectedWhenNoOwner() {
        Player player = new Player(0);
        player.addToBench(new Unit(1, tpl("u1"), 1));
        RunContext ctx = context(player);
        CommandManager manager = armed(player);
        List<GameCommand> executed = executedTracker(manager);
        manager.addCommand(new UnequipItemCommand(999));
        manager.executeAll(ctx);
        assertThat(executed).isEmpty();
        assertThat(player.getInventory()).isEmpty();
    }

    // —— unequipAll（卖出/合成共用） ——

    @Test
    @DisplayName("unequipAll：清空三槽并按穿着序保序入包")
    void unequipAllClearsAndKeepsOrder() {
        Player player = new Player(0);
        Unit unit = new Unit(1, tpl("u1"), 1);
        player.addToBench(unit);
        Equipment sword = new Equipment(11, eq("eq_sword", EquipmentSlot.WEAPON));
        Equipment plate = new Equipment(12, eq("eq_plate", EquipmentSlot.ARMOR));
        Equipment charm = new Equipment(13, eq("eq_charm", EquipmentSlot.TRINKET));
        unit.equip(sword);
        unit.equip(plate);
        unit.equip(charm);
        EquipmentSystem.unequipAll(unit, player);
        assertThat(unit.getEquipped()).isEmpty();
        assertThat(player.getInventory()).containsExactly(sword, plate, charm);
    }
}
