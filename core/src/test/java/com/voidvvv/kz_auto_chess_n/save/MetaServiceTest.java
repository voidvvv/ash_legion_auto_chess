package com.voidvvv.kz_auto_chess_n.save;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.HeroData;
import com.voidvvv.kz_auto_chess_n.data.HeroPassiveType;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetaService 档案域门面测试（Phase 6 CP7）：settleRun（纯结算 + 落盘，裁决 D11）、
 * 解锁派生透传、快照生命周期（save → has → load → clear，裁决 D20 坏档删档）。
 */
class MetaServiceTest {

    @TempDir
    Path tempDir;

    private static GameData data() {
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        units.put("u_a", new UnitData("u_a", "夹具a", "人类", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_a", false));
        Map<Integer, String> bosses = new LinkedHashMap<Integer, String>();
        bosses.put(7, "u_a");
        bosses.put(15, "u_a");
        bosses.put(25, "u_a");
        Map<String, SceneData> scenes = new LinkedHashMap<String, SceneData>();
        scenes.put("scene_forest", new SceneData("scene_forest", "翡翠林地", null,
                new ArrayList<SceneData.EnemyPoolEntry>(Arrays.asList(
                        new SceneData.EnemyPoolEntry("u_a", 1, 1))), bosses));
        Map<String, HeroData> heroes = new LinkedHashMap<String, HeroData>();
        heroes.put("hero_x", new HeroData("hero_x", "夹具英雄", "desc",
                HeroPassiveType.START_GOLD, 2f, new ArrayList<String>(), null));
        return new GameData(units,
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                scenes, new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                heroes, new ArrayList<String>());
    }

    private MetaService service() {
        MetaService service = new MetaService(
                new FileHandle(tempDir.resolve("profile.json").toString()),
                new FileHandle(tempDir.resolve("run_snapshot.json").toString()));
        service.loadProfile();
        return service;
    }

    private static RunContext endedContext(GameData data) {
        RunState runState = new RunState(42L, "scene_forest", "hero_x",
                RunModifiers.EMPTY, new SequentialIdIssuer());
        runState.markRunStarted();
        runState.setRound(25);
        runState.setEndCause(RunEndCause.COMPLETED);
        runState.setMasteryAwarded(135);
        runState.setPhase(GamePhase.RUN_END);
        return new RunContext(new Player(10), runState, data, new RandomGenerator(42L),
                new ShopSystem());
    }

    private static RunContext shoppingContext(GameData data) {
        RunState runState = new RunState(42L, "scene_forest", null,
                RunModifiers.EMPTY, new SequentialIdIssuer(30));
        runState.markRunStarted();
        runState.setRound(6);
        runState.setPhase(GamePhase.SHOPPING);
        RunContext ctx = new RunContext(new Player(21, 2, 3), runState, data,
                new RandomGenerator(42L), new ShopSystem());
        ctx.getShop().restoreSlots(Arrays.asList(
                data.getUnit("u_a"), null, null, null, null));
        return ctx;
    }

    @Test
    @DisplayName("loadProfile：无档案文件 → 初始档案（fresh）")
    void loadProfileMissingFileYieldsFresh() {
        assertThat(service().getProfile().getHeroProgress()).isEmpty();
    }

    @Test
    @DisplayName("settleRun：结算入账 + 落盘（重开服务回读到新进度）+ 解锁场景派生更新")
    void settleRunPersistsAndUnlocks() {
        GameData data = data();
        MetaService service = service();
        ProfileService.Settlement settlement = service.settleRun(data, endedContext(data));
        assertThat(settlement.getExpGained()).isEqualTo(135);
        assertThat(settlement.getLevelTo()).isEqualTo(2);
        assertThat(service.getProfile().getHeroProgress().get("hero_x"))
                .isEqualTo(new HeroProgress(2, 85));
        assertThat(service.getProfile().getCompletedScenes()).containsExactly("scene_forest");
        assertThat(service.unlockedSceneIds(data)).contains("scene_forest");

        MetaService reloaded = service(); // 模拟重启：同一文件句柄重装载
        assertThat(reloaded.getProfile().getHeroProgress().get("hero_x"))
                .isEqualTo(new HeroProgress(2, 85));
    }

    @Test
    @DisplayName("resolveRunModifiers：heroId → ProfileService.runModifiers 透传（Lv.1 +2+2）")
    void resolveRunModifiersDelegates() {
        GameData data = data();
        MetaService service = service();
        assertThat(service.resolveRunModifiers("hero_x", data).getStartGoldBonus()).isEqualTo(4);
        assertThat(service.resolveRunModifiers(null, data).getStartGoldBonus()).isEqualTo(2);
        assertThat(service.resolveRunModifiers("no_such_hero", data).getStartGoldBonus())
                .isEqualTo(2); // 未知英雄防御路径：仅基础权益
    }

    @Test
    @DisplayName("快照生命周期：save → hasRunSnapshot true → load 回读 → clear 后 false")
    void snapshotLifecycle() {
        GameData data = data();
        MetaService service = service();
        assertThat(service.hasRunSnapshot()).isFalse();

        service.saveRunSnapshot(shoppingContext(data));
        assertThat(service.hasRunSnapshot()).isTrue();
        RunSnapshot loaded = service.loadRunSnapshot(data);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getRound()).isEqualTo(6);
        assertThat(loaded.getPlayerGold()).isEqualTo(21);
        assertThat(loaded.getIdIssuerNext()).isEqualTo(30);

        service.clearRunSnapshot();
        assertThat(service.hasRunSnapshot()).isFalse();
        assertThat(service.loadRunSnapshot(data)).isNull();
    }

    @Test
    @DisplayName("坏档快照：loadRunSnapshot 返回 null 且删档不炸（裁决 D20）")
    void corruptedSnapshotReturnsNullAndDeletes() throws IOException {
        GameData data = data();
        MetaService service = service();
        Files.write(tempDir.resolve("run_snapshot.json"),
                "{ 坏档 ".getBytes(StandardCharsets.UTF_8));
        assertThat(service.hasRunSnapshot()).isTrue();
        assertThat(service.loadRunSnapshot(data)).isNull();
        assertThat(service.hasRunSnapshot()).isFalse(); // 已删档
    }
}
