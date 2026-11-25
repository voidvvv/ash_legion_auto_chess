package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.command.BuyUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.MoveUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.StartBattleCommand;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.screens.RunAssembler;
import com.voidvvv.kz_auto_chess_n.systems.RosterSystem;
import com.voidvvv.kz_auto_chess_n.systems.RunFlowSystem;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import com.voidvvv.kz_auto_chess_n.systems.SynergySnapshot;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;

class RosterPreviewTextTest {
    private final GameData data = PaiDuelDriver.realData();
    private final RunContext ctx = RunAssembler.assembleTestPeriod(data, new ShopSystem(), 42L);
    private final CommandManager commands = new CommandManager();

    RosterPreviewTextTest() {
        new RunFlowSystem().registerHandlers(commands);
        new RosterSystem().registerHandlers(commands);
        ctx.getShop().registerHandlers(commands);
        ctx.getRunState().setRound(8);
        ctx.getRunState().setEnemyWave(Arrays.asList(
                new WaveSpec(data.getUnit("unit_wolf_pup"), 1, 1f, 0, 1),
                new WaveSpec(data.getUnit("unit_wolf_pup"), 1, 1f, 1, 1),
                new WaveSpec(data.getUnit("unit_wolf_pup"), 1, 1f, 2, 1),
                new WaveSpec(data.getUnit("unit_wolf_pup"), 1, 1f, 3, 1)));
    }

    private Unit add(String template, int col) {
        Unit unit = new Unit(ctx.getRunState().getIdIssuer().nextId(), data.getUnit(template), 1);
        ctx.getPlayer().addToBench(unit);
        if (col >= 0) ctx.getPlayer().deploy(unit, col, 4);
        return unit;
    }

    @Test
    void fullBoardTradeMatchesCombatAndEnemyUsesItsRoundCap() {
        ctx.getPlayer().addExp(12);
        add("unit_wolf_pup", 0);
        add("unit_wolf_pup", 1);
        add("unit_ambush_panther", 2);
        add("unit_boar_rider", 3);
        add("unit_spear_headhunter", 4);
        ctx.getShop().restoreSlots(Arrays.asList(data.getUnit("unit_beast_archer"), null, null, null, null));
        assertThat(RosterPreviewText.shop(ctx, 0)).contains("人口已满 · 购入进后备",
                "野兽 3→4 升档", "普攻减速", "游侠 1→2 升档", "兽人 2→1 掉档");
        Unit archer = add("unit_beast_archer", -1);
        PlacementTarget.Cell target = new PlacementTarget.Cell(3, 4);
        assertThat(RosterPreviewText.drop(ctx, archer, target)).contains("野兽 3→4 升档", "游侠 1→2 升档",
                "兽人 2→1 掉档");
        assertThat(RosterPreviewText.drop(ctx, archer, new PlacementTarget.Cell(5, 4)))
                .containsExactly("人口已满 · 拖至棋子上");
        assertThat(RosterPreviewText.enemy(ctx, data.getUnit("unit_wolf_pup")))
                .contains("野兽 2档").doesNotContain("野兽 4档");
        commands.addCommand(new MoveUnitCommand(archer.getId(), target));
        commands.addCommand(StartBattleCommand.INSTANCE);
        commands.executeAll(ctx);
        SynergySnapshot player = ctx.getBattleState().getPlayerSynergies();
        assertThat(player.getActives()).extracting(a -> a.getName() + a.getThresholdCount())
                .contains("野兽4", "游侠2").doesNotContain("兽人2");
        assertThat(ctx.getBattleState().getEnemySynergies().getActives())
                .extracting(SynergySnapshot.ActiveSynergy::getThresholdCount).containsOnly(2);
    }

    @Test
    void automaticMergeCanRemoveBothBoardCopiesWhenBenchSurvives() {
        add("unit_wolf_pup", 0);
        add("unit_wolf_pup", 1);
        ctx.getShop().restoreSlots(Arrays.asList(data.getUnit("unit_wolf_pup"), null, null, null, null));
        assertThat(RosterPreviewText.shop(ctx, 0)).contains("购入即合成", "上阵同名减少 2", "野兽 2→0 掉档");
        commands.addCommand(new BuyUnitCommand(0));
        commands.executeAll(ctx);
        assertThat(ctx.getPlayer().getDeployedUnits()).isEmpty();
        assertThat(ctx.getPlayer().getBench()).extracting(Unit::getStar).containsExactly(2);
    }
}
