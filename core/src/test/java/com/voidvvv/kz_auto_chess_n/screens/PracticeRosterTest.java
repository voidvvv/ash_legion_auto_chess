package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.StartBattleCommand;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.save.Profile;
import com.voidvvv.kz_auto_chess_n.save.ProfileService;
import com.voidvvv.kz_auto_chess_n.save.SnapshotCodec;
import com.voidvvv.kz_auto_chess_n.systems.RosterSystem;
import com.voidvvv.kz_auto_chess_n.systems.RosterSystem.Edit;
import com.voidvvv.kz_auto_chess_n.systems.RunFlowSystem;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeRosterTest {
    private static final String WARRIOR = "unit_warrior_01";
    private static final String RANGER = "unit_ranger_01";
    private static final PlacementTarget.Cell FIRST_CELL = new PlacementTarget.Cell(0, 4);
    private static GameData data;
    private final RunFlowSystem flow = new RunFlowSystem();
    private final RosterSystem roster = new RosterSystem();

    @BeforeAll
    static void load() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    @Test
    void benchEditsUseCompactSlotsAndKeepEquipmentOwnershipConsistent() {
        RunContext ctx = start();
        Player player = ctx.getPlayer();
        PlacementTarget.Bench first = new PlacementTarget.Bench(0);
        PlacementTarget.Bench second = new PlacementTarget.Bench(1);
        int next = ctx.getRunState().getIdIssuer().peekNext();
        assertThat(roster.editPractice(ctx, second, 0, 0, Edit.ADD, WARRIOR, 1)).contains("第一个空位");
        assertThat(roster.editPractice(ctx, first, 0, 0, Edit.ADD, "boss_thorn_mother", 1)).contains("模板");
        assertThat(roster.editPractice(ctx, first, 0, 0, Edit.ADD, "unit_skeleton_soldier", 1)).contains("模板");
        assertThat(roster.editPractice(ctx, first, 0, 0, Edit.ADD, WARRIOR, 4)).contains("星级");
        assertThat(ctx.getRunState().getIdIssuer().peekNext()).isEqualTo(next);
        assertThat(ctx.getRunState().isPractice()).isFalse();
        assertThat(player.getBench()).isEmpty();

        assertThat(roster.editPractice(ctx, first, 0, 0, Edit.ADD, WARRIOR, 1)).isNull();
        Unit original = player.getBench().get(0);
        original.addSpend(9);
        Equipment item = equip(ctx, original);
        int gold = player.getGold();
        assertThat(roster.editPractice(ctx, first, original.getId(), 1, Edit.STAR, null, 3)).isNull();
        assertThat(roster.editPractice(ctx, first, original.getId(), 1, Edit.REMOVE, null, 1)).contains("已变化");
        assertThat(original.getEquipped()).containsExactly(item);
        assertThat(original.getSpend()).isEqualTo(9);

        assertThat(roster.editPractice(ctx, second, 0, 0, Edit.ADD, WARRIOR, 1)).isNull();
        Unit neighbor = player.getBench().get(1);
        assertThat(roster.editPractice(ctx, first, original.getId(), 3, Edit.REPLACE, RANGER, 2)).isNull();
        Unit replacement = player.getBench().get(0);
        assertThat(player.getBench()).containsExactly(replacement, neighbor);
        assertThat(replacement.getId()).isGreaterThan(original.getId());
        assertThat(replacement.getStar()).isEqualTo(2);
        assertThat(replacement.getSpend()).isZero();
        assertThat(player.getInventory()).containsExactly(item);

        assertThat(roster.editPractice(ctx, first, replacement.getId(), 2, Edit.REMOVE, null, 1)).isNull();
        assertThat(player.getBench()).containsExactly(neighbor);
        assertThat(player.getGold()).isEqualTo(gold);
        assertThat(ctx.getRunState().isPractice()).isTrue();
    }

    @Test
    void boardEditsWorkWithFullBenchAndAtPopulationCapThenSpawnInRealBattle() {
        RunContext ctx = start();
        Player player = ctx.getPlayer();
        for (int i = 0; i < GameBalance.BENCH_SIZE; i++) {
            assertThat(roster.editPractice(ctx, new PlacementTarget.Bench(i), 0, 0, Edit.ADD, WARRIOR, 1)).isNull();
        }
        int cap = player.getPopulationCap();
        for (int i = 0; i < cap; i++) {
            assertThat(roster.editPractice(ctx, new PlacementTarget.Cell(i, 4), 0, 0, Edit.ADD, WARRIOR, 1)).isNull();
        }
        int next = ctx.getRunState().getIdIssuer().peekNext();
        assertThat(roster.editPractice(ctx, new PlacementTarget.Cell(0, 3), 0, 0, Edit.ADD, WARRIOR, 1)).contains("己方格");
        assertThat(roster.editPractice(ctx, new PlacementTarget.Cell(cap, 4), 0, 0, Edit.ADD, WARRIOR, 1)).contains("人口");
        assertThat(roster.editPractice(ctx, new PlacementTarget.Bench(9), 0, 0, Edit.ADD, WARRIOR, 1)).contains("位置");
        assertThat(ctx.getRunState().getIdIssuer().peekNext()).isEqualTo(next);

        Unit original = player.deployedAt(0, 4);
        Equipment item = equip(ctx, original);
        assertThat(roster.editPractice(ctx, FIRST_CELL, original.getId(), 1, Edit.REPLACE, RANGER, 3)).isNull();
        Unit replacement = player.deployedAt(0, 4);
        assertThat(player.getInventory()).containsExactly(item);
        assertThat(roster.editPractice(ctx, FIRST_CELL, replacement.getId(), 3, Edit.STAR, null, 2)).isNull();
        Unit second = player.deployedAt(1, 4);
        Equipment removedItem = equip(ctx, second);
        assertThat(roster.editPractice(ctx, new PlacementTarget.Cell(1, 4), second.getId(), 1, Edit.REMOVE, null, 1)).isNull();
        assertThat(player.getInventory()).containsExactly(item, removedItem);
        assertThat(player.getBench()).hasSize(GameBalance.BENCH_SIZE);
        assertThat(player.getDeployedUnits()).hasSize(cap - 1);

        RunContext resumed = SnapshotCodec.restore(
                SnapshotCodec.read(SnapshotCodec.write(SnapshotCodec.capture(ctx))), data, Profile.fresh(), new ShopSystem());
        assertThat(resumed.getPlayer().deployedAt(0, 4).getId()).isEqualTo(replacement.getId());
        assertThat(resumed.getPlayer().deployedAt(0, 4).getStar()).isEqualTo(2);
        assertThat(resumed.getPlayer().deployedAt(1, 4)).isNull();
        assertThat(resumed.getPlayer().getInventory()).extracting(Equipment::getId)
                .containsExactly(item.getId(), removedItem.getId());
        assertThat(resumed.getRunState().getIdIssuer().peekNext()).isEqualTo(ctx.getRunState().getIdIssuer().peekNext());
        assertThat(resumed.getRunState().isPractice()).isTrue();

        CommandManager manager = new CommandManager();
        flow.registerHandlers(manager);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(resumed);
        assertThat(resumed.getRunState().getPhase()).isEqualTo(GamePhase.BATTLE);
        assertThat(resumed.getBattleState().getUnits()).anyMatch(
                unit -> unit.getSide() == Side.PLAYER && unit.getTemplate().getId().equals(RANGER) && unit.getStar() == 2);
        assertThat(roster.editPractice(resumed, FIRST_CELL, replacement.getId(), 2, Edit.REMOVE, null, 1))
                .contains("备战");
    }

    private RunContext start() {
        RunContext ctx = RunAssembler.assembleNormal(data, new ShopSystem(), 45, "scene_forest", null,
                ProfileService.runModifiers(null, Profile.fresh(), data));
        flow.startRun(ctx);
        return ctx;
    }

    private Equipment equip(RunContext ctx, Unit unit) {
        Equipment item = new Equipment(ctx.getRunState().getIdIssuer().nextId(),
                data.getEquipments().values().iterator().next());
        unit.equip(item);
        return item;
    }
}
