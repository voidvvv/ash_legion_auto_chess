package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.PickChestCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.StartBattleCommand;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.save.MetaService;
import com.voidvvv.kz_auto_chess_n.save.Profile;
import com.voidvvv.kz_auto_chess_n.save.SnapshotCodec;
import com.voidvvv.kz_auto_chess_n.systems.RunFlowSystem;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PracticeRulesTest {
    private static GameData data;

    @BeforeAll
    static void loadGame() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    @Test
    void economyUsesPlayerRulesAndRoundTrips() {
        RunContext ctx = startedRun();
        assertThat(PracticeRules.setGold(ctx, -1)).isNotNull();
        assertThat(PracticeRules.setGold(ctx, 10)).isNull();
        assertThat(PracticeRules.changeExp(ctx, -1)).isNotNull();
        assertThat(ctx.getRunState().isPractice()).isFalse();
        assertThat(PracticeRules.setGold(ctx, Integer.MAX_VALUE)).isNull();
        ctx.getPlayer().addGold(15);
        assertThat(ctx.getPlayer().getGold()).isEqualTo(Integer.MAX_VALUE);

        int exp = GameBalance.expToNextLevel(1) + GameBalance.expToNextLevel(2) + 1;
        assertThat(PracticeRules.changeExp(ctx, exp)).isNull();
        assertThat(PracticeRules.changeExp(ctx, -2)).isNotNull();
        assertThat(PracticeRules.changeExp(ctx, -1)).isNull();
        assertThat(ctx.getPlayer().getLevel()).isEqualTo(3);
        assertThat(ctx.getPlayer().getPopulationCap()).isEqualTo(GameBalance.population(3));
        assertThat(ctx.getPlayer().getCurrentExp()).isZero();
        int toCap = PracticeRules.remainingExp(ctx.getPlayer());
        assertThat(PracticeRules.changeExp(ctx, toCap + 1)).isNotNull();
        assertThat(PracticeRules.changeExp(ctx, toCap)).isNull();
        assertThat(PracticeRules.changeExp(ctx, 1)).isNotNull();

        RunContext saved = roundTrip(ctx);
        assertThat(saved.getPlayer().getLevel()).isEqualTo(GameBalance.MAX_PLAYER_LEVEL);
        assertThat(saved.getPlayer().getCurrentExp()).isZero();
        assertThat(saved.getPlayer().getGold()).isEqualTo(Integer.MAX_VALUE);
        assertThat(saved.getRunState().isPractice()).isTrue();
    }

    @Test
    void travelSkipsSettlementAndFutureBattlePaysExactlyOnce(@TempDir Path directory) {
        RunContext ctx = startedRun();
        RunFlowSystem flow = new RunFlowSystem();
        CommandManager manager = new CommandManager();
        flow.registerHandlers(manager);
        Unit unit = new Unit(ctx.getRunState().getIdIssuer().nextId(), data.getUnit("unit_warrior_01"), 1);
        ctx.getPlayer().addToBench(unit);
        ctx.getPlayer().deploy(unit, 0, 4);
        ctx.getRunState().markRoundIncomePaid();
        ctx.getRunState().setDefeatCountPerRound(2);

        assertThatIllegalArgumentException().isThrownBy(() -> flow.preparePracticeWave(ctx, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> flow.preparePracticeWave(ctx, 26));
        assertThat(ctx.getRunState().isRoundIncomePaid()).isTrue();
        assertThat(ctx.getRunState().getDefeatCountPerRound()).isEqualTo(2);
        flow.preparePracticeWave(ctx, 7);
        assertThat(ctx.getPlayer().getGold()).isEqualTo(10);
        assertThat(ctx.getPlayer().getCurrentExp()).isZero();
        assertThat(ctx.getPlayer().deployedAt(0, 4)).isSameAs(unit);
        assertThat(ctx.getRunState().getPhase()).isEqualTo(GamePhase.SHOPPING);
        assertThat(ctx.getRunState().getPendingChest()).isNull();
        assertThat(ctx.getRunState().getEndCause()).isNull();
        assertThat(ctx.getRunState().getDefeatCountPerRound()).isZero();
        assertThat(ctx.getRunState().isRoundIncomePaid()).isFalse();

        RunContext resumed = roundTrip(ctx);
        assertThat(resumed.getRunState().getEnemyWave()).anyMatch(
                enemy -> enemy.getTemplate().getId().equals("boss_thorn_mother"));
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(resumed);
        assertThat(PracticeRules.mutationBlock(resumed)).contains("备战");
        assertThatIllegalArgumentException().isThrownBy(() -> flow.preparePracticeWave(resumed, 8));
        resumed.getBattleState().finish(BattleOutcome.PLAYER_WIN);
        flow.onBattleOver(resumed);
        assertThat(PracticeRules.mutationBlock(resumed)).contains("宝箱");
        assertThatIllegalArgumentException().isThrownBy(() -> flow.preparePracticeWave(resumed, 8));
        manager.addCommand(new PickChestCommand(0));
        manager.addCommand(new PickChestCommand(0));
        manager.executeAll(resumed);
        assertThat(resumed.getRunState().getRound()).isEqualTo(8);
        assertThat(resumed.getPlayer().getGold()).isEqualTo(
                10 + GameBalance.chestGold(7, true) + GameBalance.ROUND_INCOME_GOLD);

        flow.preparePracticeWave(resumed, GameBalance.TOTAL_ROUNDS);
        manager.addCommand(StartBattleCommand.INSTANCE);
        manager.executeAll(resumed);
        resumed.getBattleState().finish(BattleOutcome.PLAYER_WIN);
        flow.onBattleOver(resumed);
        manager.addCommand(new PickChestCommand(0));
        manager.executeAll(resumed);
        assertThat(resumed.getRunState().getEndCause()).isEqualTo(RunEndCause.COMPLETED);
        FileHandle profile = new FileHandle(directory.resolve("profile.json").toFile());
        MetaService meta = new MetaService(profile, null);
        assertThat(meta.settleRun(data, resumed)).isNull();
        assertThat(meta.getProfile().getCompletedScenes()).isEmpty();
        assertThat(meta.getProfile().getHeroProgress()).isEmpty();
        assertThat(profile.exists()).isFalse();
    }

    @Test
    void profileGrantIsReversibleAndDoesNotOpenSuccessors(@TempDir Path directory) {
        FileHandle profile = new FileHandle(directory.resolve("profile.json").toFile());
        MetaService meta = new MetaService(profile, null);
        PracticeAccess access = new PracticeAccess(data, meta, () -> null);
        assertThat(access.setSceneGrant("missing", true).applied).isFalse();
        assertThat(access.setSceneGrant("scene_forest", false).applied).isFalse();
        assertThat(access.jump(null, "scene_crypt", 7).applied).isFalse();
        assertThat(access.setSceneGrant("scene_crypt", true).saved).isTrue();
        MetaService reloaded = new MetaService(profile, null);
        reloaded.loadProfile();
        assertThat(reloaded.unlockedSceneIds(data)).containsExactly("scene_forest", "scene_crypt");
        assertThat(reloaded.getProfile().getCompletedScenes()).isEmpty();
        assertThat(access.setSceneGrant("scene_crypt", false).saved).isTrue();
        reloaded.loadProfile();
        assertThat(reloaded.unlockedSceneIds(data)).containsExactly("scene_forest");

        MetaService unwritable = new MetaService(null, null);
        PracticeAccess failed = new PracticeAccess(data, unwritable, () -> null);
        assertThat(failed.setSceneGrant("scene_crypt", true).applied).isFalse();
        assertThat(unwritable.getProfile().getPracticeSceneGrants()).isEmpty();
    }

    private static RunContext startedRun() {
        RunContext ctx = RunAssembler.assembleNormal(data, new ShopSystem(), 235L, "scene_forest",
                "hero_greg", RunModifiers.EMPTY);
        new RunFlowSystem().startRun(ctx);
        return ctx;
    }

    private static RunContext roundTrip(RunContext ctx) {
        return SnapshotCodec.restore(SnapshotCodec.read(SnapshotCodec.write(SnapshotCodec.capture(ctx))),
                data, Profile.fresh(), new ShopSystem());
    }
}
