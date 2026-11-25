package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.BuyExpCommand;
import com.voidvvv.kz_auto_chess_n.command.BuyUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.EquipItemCommand;
import com.voidvvv.kz_auto_chess_n.command.GameCommand;
import com.voidvvv.kz_auto_chess_n.command.MoveUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.PickChestCommand;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RefreshShopCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.StartBattleCommand;
import com.voidvvv.kz_auto_chess_n.command.StartRunCommand;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.ChestOffer;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.save.Profile;
import com.voidvvv.kz_auto_chess_n.save.ProfileService;
import com.voidvvv.kz_auto_chess_n.screens.RunAssembler;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ExpeditionBalanceProbeTest {
    @Test
    void forestThroughSecondBoss() {
        GameData data = PaiDuelDriver.realData();
        List<Integer> recruitWins = new ArrayList<>();
        List<Integer> levelWins = new ArrayList<>();
        for (long seed : new long[]{42, 73, 2026}) {
            recruitWins.add(play(data, seed, false));
            levelWins.add(play(data, seed, true));
        }
        assertThat(recruitWins).containsOnly(15);
        assertThat(levelWins).allMatch(wins -> wins >= 7);
        assertThat(levelWins).filteredOn(wins -> wins == 15).hasSizeGreaterThanOrEqualTo(2);
    }

    private static int play(GameData data, long seed, boolean levelFirst) {
        String scene = "scene_forest";
        String hero = "hero_greg";
        ShopSystem shop = new ShopSystem();
        RunContext ctx = RunAssembler.assembleNormal(data, shop, seed, scene, hero,
                ProfileService.runModifiers(data.getHero(hero), Profile.fresh(), data));
        CommandManager commands = new CommandManager();
        RunFlowSystem flow = new RunFlowSystem();
        flow.registerHandlers(commands);
        shop.registerHandlers(commands);
        new RosterSystem().registerHandlers(commands);
        new EquipmentSystem().registerHandlers(commands);
        BattleSystem battle = new BattleSystem();
        execute(commands, ctx, new StartRunCommand(seed, scene, hero));
        Player player = ctx.getPlayer();
        int wins = 0;
        while (ctx.getRunState().getPhase() == GamePhase.SHOPPING && ctx.getRunState().getRound() <= 15) {
            int round = ctx.getRunState().getRound();
            int attempt = ctx.getRunState().getDefeatCountPerRound() + 1;
            String start = economy(player);
            if (levelFirst && round >= 2 && player.getLevel() < GameBalance.MAX_PLAYER_LEVEL
                    && GameBalance.expToNextLevel(player.getLevel()) - player.getCurrentExp()
                    <= GameBalance.BUY_EXP_GAIN && player.getGold() >= 6) {
                execute(commands, ctx, BuyExpCommand.INSTANCE);
            }
            buy(commands, ctx, levelFirst);
            if (!levelFirst && (round >= 4 || attempt > 1) && player.getGold() >= 4) {
                execute(commands, ctx, RefreshShopCommand.INSTANCE);
                buy(commands, ctx, false);
            }
            deploy(commands, ctx);
            for (Equipment item : new ArrayList<>(player.getInventory())) {
                for (Unit unit : player.getDeployedUnits()) {
                    if (unit.equippedIn(item.getTemplate().getSlot()) == null) {
                        execute(commands, ctx, new EquipItemCommand(item.getId(), unit.getId()));
                        break;
                    }
                }
            }
            String before = economy(player);
            String roster = player.getDeployedUnits().stream()
                    .map(unit -> unit.getTemplate().getId() + "*" + unit.getStar()).collect(Collectors.joining("+"));
            List<WaveSpec> wave = ctx.getRunState().getEnemyWave();
            execute(commands, ctx, StartBattleCommand.INSTANCE);
            BattleState state = ctx.getBattleState();
            double enemyHp = state.getUnits().stream().filter(unit -> unit.getSide() == Side.ENEMY)
                    .mapToDouble(BattleUnit::maxHp).sum();
            battle.runToEnd(state, 4000);
            assertThat(state.isOver()).isTrue();
            flow.tickBattleEnd(ctx, GameBalance.VICTORY_RESULT_DELAY_SECONDS);
            ChestOffer offer = ctx.getRunState().getPendingChest();
            String choice = "-";
            if (offer != null) {
                int slot = state.getOutcome() == BattleOutcome.PLAYER_WIN ? 2 : 0;
                ChestOption option = offer.optionAt(slot);
                choice = option.getKind() == ChestOption.Kind.EQUIPMENT
                        ? option.getEquipmentId() : option.getKind() + "+" + option.getAmount();
                execute(commands, ctx, new PickChestCommand(slot));
            } else {
                flow.tickResult(ctx, GameBalance.RESULT_BANNER_SECONDS);
            }
            if (state.getOutcome() == BattleOutcome.PLAYER_WIN) {
                wins++;
            }
            System.out.printf(Locale.ROOT,
                    "%d,%s,R%d.%d,%s,%s,%s,%s,foes=%d scale=%.2f hp=%.0f,%s %.1fs,%s%n",
                    seed, levelFirst ? "level" : "recruit", round, attempt, start, before, economy(player),
                    roster, wave.size(), wave.get(0).getScale(), enemyHp, state.getOutcome(), state.getElapsed(), choice);
        }
        System.out.printf("SUMMARY %d %s wins=%d%n", seed, levelFirst ? "level" : "recruit", wins);
        return wins;
    }

    private static void buy(CommandManager commands, RunContext ctx, boolean levelFirst) {
        Player player = ctx.getPlayer();
        for (int i = 0; i < GameBalance.SHOP_SLOTS; i++) {
            UnitData unit = ctx.getShop().getSlots().get(i);
            if (unit == null) {
                continue;
            }
            int copies = player.getDeployedUnits().stream().filter(u -> u.getTemplate() == unit).mapToInt(u -> 1).sum()
                    + player.getBench().stream().filter(u -> u.getTemplate() == unit).mapToInt(u -> 1).sum();
            if (player.getRosterSize() < player.getPopulationCap() || copies > 0
                    || (levelFirst && player.getBench().size() < 2)) {
                execute(commands, ctx, new BuyUnitCommand(i));
            }
        }
    }

    private static void deploy(CommandManager commands, RunContext ctx) {
        Player player = ctx.getPlayer();
        int[] columns = {2, 3, 1, 4, 0, 5};
        for (Unit unit : new ArrayList<>(player.getBench())) {
            if (player.getDeployedUnits().size() >= player.getPopulationCap()) {
                return;
            }
            int firstRow = unit.getTemplate().getBaseStats().getRange() == 1 ? 4 : 5;
            placement:
            for (int y = firstRow; y <= 6; y++) {
                for (int x : columns) {
                    if (player.deployedAt(x, y) == null) {
                        execute(commands, ctx, new MoveUnitCommand(unit.getId(), new PlacementTarget.Cell(x, y)));
                        break placement;
                    }
                }
            }
        }
    }

    private static String economy(Player player) {
        return String.format(Locale.ROOT, "%dg/L%d:%dxp/%d:%dpop", player.getGold(), player.getLevel(),
                player.getCurrentExp(), player.getDeployedUnits().size(), player.getPopulationCap());
    }

    private static void execute(CommandManager commands, RunContext ctx, GameCommand command) {
        commands.addCommand(command);
        commands.executeAll(ctx);
    }
}
