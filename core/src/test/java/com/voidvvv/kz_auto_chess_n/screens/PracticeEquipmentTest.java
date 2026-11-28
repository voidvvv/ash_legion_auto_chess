package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.StartBattleCommand;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.save.Profile;
import com.voidvvv.kz_auto_chess_n.save.SnapshotCodec;
import com.voidvvv.kz_auto_chess_n.systems.EquipmentSystem;
import com.voidvvv.kz_auto_chess_n.systems.EquipmentSystem.InventoryEdit;
import com.voidvvv.kz_auto_chess_n.systems.RunFlowSystem;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeEquipmentTest {
    private static final PlacementTarget.Cell CELL = new PlacementTarget.Cell(0, 4);
    private static final PlacementTarget.Bench BENCH = new PlacementTarget.Bench(0);
    private static final String SWORD = "eq_iron_sword";
    private static final String BLADE = "eq_bronze_blade";
    private static final String ARMOR = "eq_mithril_armor";
    private static GameData data;
    private final EquipmentSystem equipment = new EquipmentSystem();
    private final RunFlowSystem flow = new RunFlowSystem();

    @BeforeAll
    static void load() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    @Test
    void compactBagAllowsDuplicatesAndRejectsStaleAndOutOfRangeActions() {
        RunContext ctx = start();
        Player player = ctx.getPlayer();
        long emptyRevision = player.getInventoryRevision();
        Equipment first = add(ctx, SWORD);
        assertThat(equipment.editPracticeInventory(ctx, emptyRevision, InventoryEdit.ADD, -1, 0, SWORD))
                .contains("背包已变化");
        for (int i = 0; i < 32; i++) add(ctx, SWORD);
        assertThat(player.getInventory()).hasSize(33).doesNotHaveDuplicates();
        Equipment neighbor = player.getInventory().get(1);
        int next = ctx.getRunState().getIdIssuer().peekNext();
        long revision = player.getInventoryRevision();
        assertThat(equipment.editPracticeInventory(ctx, revision, InventoryEdit.REPLACE, 33, first.getId(), ARMOR))
                .contains("已变化");
        assertThat(equipment.editPracticeInventory(ctx, revision, InventoryEdit.REPLACE, 0, first.getId(), "missing"))
                .contains("模板");
        assertThat(ctx.getRunState().getIdIssuer().peekNext()).isEqualTo(next);
        assertThat(equipment.editPracticeInventory(ctx, revision, InventoryEdit.REPLACE, 0, first.getId(), ARMOR)).isNull();
        assertThat(player.getInventory().get(1)).isSameAs(neighbor);
        assertThat(player.getInventory().get(0).getId()).isEqualTo(next);
        assertThat(equipment.editPracticeInventory(ctx, player.getInventoryRevision(),
                InventoryEdit.REMOVE, 0, first.getId(), null)).contains("已变化");
        assertThat(equipment.editPracticeInventory(ctx, player.getInventoryRevision(),
                InventoryEdit.REMOVE, 0, next, null)).isNull();
        assertThat(player.getInventory().get(0)).isSameAs(neighbor);
    }

    @Test
    void bothPositionsUseExactIdentityAndEquipmentChangesSurviveIntoBattle() {
        RunContext ctx = start();
        Player player = ctx.getPlayer();
        Unit board = createUnit(ctx);
        player.deploy(board, 0, 4);
        Unit bench = createUnit(ctx);
        Equipment sword = add(ctx, SWORD), blade = add(ctx, BLADE), armor = add(ctx, ARMOR);
        long before = player.getInventoryRevision();
        assertThat(equipment.editPracticeSlot(ctx, before, CELL, board.getId(), EquipmentSlot.WEAPON, 0, armor.getId()))
                .contains("不兼容");
        assertThat(player.getInventory()).containsExactly(sword, blade, armor);
        assertThat(player.getInventoryRevision()).isEqualTo(before);

        for (PlacementTarget position : new PlacementTarget[]{CELL, BENCH}) {
            Unit unit = position == CELL ? board : bench;
            assertThat(equip(ctx, position, unit, EquipmentSlot.WEAPON, 0, sword.getId())).isNull();
            assertThat(equip(ctx, position, unit, EquipmentSlot.WEAPON, 0, blade.getId())).contains("该槽装备已变化");
            assertThat(equip(ctx, position, unit, EquipmentSlot.WEAPON, sword.getId(), blade.getId())).isNull();
            assertThat(equip(ctx, position, unit, EquipmentSlot.WEAPON, blade.getId(), 0)).isNull();
            assertThat(player.getInventory()).containsExactlyInAnyOrder(sword, blade, armor);
            assertThat(unit.getEquipped()).isEmpty();
        }
        player.deploy(bench, 1, 4);
        assertThat(equip(ctx, BENCH, bench, EquipmentSlot.ARMOR, 0, armor.getId())).contains("棋子已变化");
        assertThat(equip(ctx, CELL, board, EquipmentSlot.ARMOR, 0, armor.getId())).isNull();
        assertThat(equipment.editPracticeSlot(ctx, before, CELL, board.getId(), EquipmentSlot.ARMOR, 0, armor.getId()))
                .contains("背包已变化");
        player.undeploy(1, 4);

        RunContext resumed = SnapshotCodec.restore(
                SnapshotCodec.read(SnapshotCodec.write(SnapshotCodec.capture(ctx))), data, Profile.fresh(), new ShopSystem());
        assertThat(resumed.getPlayer().deployedAt(0, 4).equippedIn(EquipmentSlot.ARMOR).getId()).isEqualTo(armor.getId());
        assertThat(resumed.getPlayer().getInventory()).containsExactlyInAnyOrder(sword, blade);
        assertThat(resumed.getRunState().isPractice()).isTrue();
        float baseArmor = board.getTemplate().getBaseStats().getArmor();
        CommandManager manager = new CommandManager();
        flow.registerHandlers(manager);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(resumed);
        assertThat(resumed.getBattleState().getUnits()).anySatisfy(unit -> {
            assertThat(unit.getSide()).isEqualTo(Side.PLAYER);
            assertThat(unit.getEffective(StatKey.ARMOR)).isEqualTo(baseArmor + 20);
        });
        assertThat(equip(resumed, CELL, board, EquipmentSlot.ARMOR, armor.getId(), 0)).contains("备战");
    }

    private Equipment add(RunContext ctx, String template) {
        Player player = ctx.getPlayer();
        assertThat(equipment.editPracticeInventory(ctx, player.getInventoryRevision(),
                InventoryEdit.ADD, -1, 0, template)).isNull();
        return player.getInventory().get(player.getInventory().size() - 1);
    }

    private String equip(RunContext ctx, PlacementTarget slot, Unit unit, EquipmentSlot equipmentSlot, int old, int next) {
        return equipment.editPracticeSlot(ctx, ctx.getPlayer().getInventoryRevision(), slot,
                unit.getId(), equipmentSlot, old, next);
    }

    private RunContext start() {
        RunContext ctx = RunAssembler.assembleNormal(data, new ShopSystem(), 125, "scene_forest", null, RunModifiers.EMPTY);
        flow.startRun(ctx);
        return ctx;
    }

    private Unit createUnit(RunContext ctx) {
        Unit unit = new Unit(ctx.getRunState().getIdIssuer().nextId(), data.getUnit("unit_warrior_01"), 1);
        ctx.getPlayer().addToBench(unit);
        return unit;
    }
}
