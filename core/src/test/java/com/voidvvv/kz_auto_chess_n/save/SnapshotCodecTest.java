package com.voidvvv.kz_auto_chess_n.save;

import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.DataValidationException;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.HeroData;
import com.voidvvv.kz_auto_chess_n.data.HeroPassiveType;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.systems.RunFlowSystem;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SnapshotCodec 快照轨核心验收件（Phase 6 CP16）：富态 round-trip（capture → write → read
 * → restore 逐项等价）+ 续战等价（restore 后推进一轮与未挂起直接推进逐位相同——RNG 流对齐
 * 的直接证明）+ 非备战捕获拒绝 + 引用悬空四路。夹具手搓 GameData（沿 BattleTestFixtures 先例）。
 */
class SnapshotCodecTest {

    // —— 夹具 ——

    private static UnitData unit(String id, int cost, boolean boss) {
        return new UnitData(id, "夹具" + id, "人类", "战士", cost,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, boss);
    }

    private static GameData data() {
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        units.put("u_a", unit("u_a", 1, false));
        units.put("u_b", unit("u_b", 2, false));
        units.put("u_leg", unit("u_leg", 3, false));
        units.put("u_boss", unit("u_boss", 0, true));

        Map<Integer, String> bosses = new LinkedHashMap<Integer, String>();
        bosses.put(7, "u_boss");
        bosses.put(15, "u_boss");
        bosses.put(25, "u_boss");
        Map<String, SceneData> scenes = new LinkedHashMap<String, SceneData>();
        scenes.put("scene_forest", new SceneData("scene_forest", "翡翠林地", null,
                new ArrayList<SceneData.EnemyPoolEntry>(Arrays.asList(
                        new SceneData.EnemyPoolEntry("u_a", 3, 1),
                        new SceneData.EnemyPoolEntry("u_b", 2, 2))), bosses));

        Map<String, EquipmentData> equipments = new LinkedHashMap<String, EquipmentData>();
        equipments.put("eq_w", new EquipmentData("eq_w", "夹具武", EquipmentSlot.WEAPON,
                EquipmentRarity.WHITE, Arrays.asList(
                        new EquipmentEffect(StatKey.ATTACK, EffectOp.ADD, 1f)), null));
        equipments.put("eq_a", new EquipmentData("eq_a", "夹具甲", EquipmentSlot.ARMOR,
                EquipmentRarity.WHITE, Arrays.asList(
                        new EquipmentEffect(StatKey.HP, EffectOp.ADD, 10f)), null));
        equipments.put("eq_t", new EquipmentData("eq_t", "夹具饰", EquipmentSlot.TRINKET,
                EquipmentRarity.WHITE, Arrays.asList(
                        new EquipmentEffect(StatKey.ARMOR, EffectOp.ADD, 1f)), null));

        Map<String, HeroData> heroes = new LinkedHashMap<String, HeroData>();
        heroes.put("hero_x", new HeroData("hero_x", "夹具英雄", "desc",
                HeroPassiveType.START_GOLD, 2f, new ArrayList<String>(), "u_leg"));

        return new GameData(units,
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                scenes, equipments, heroes, new ArrayList<String>());
    }

    /** 富态备战期上下文：金/等级/经验/装备席（含穿着）/部署（含空格）/背包/商店（含空槽）/敌阵/怜悯/RNG/发号器 */
    private static RunContext richContext(GameData data) {
        Player player = new Player(37, 3, 5);
        Equipment weapon = new Equipment(21, data.getEquipment("eq_w"));
        Equipment armor = new Equipment(22, data.getEquipment("eq_a"));
        Equipment trinket = new Equipment(23, data.getEquipment("eq_t"));
        Unit bench1 = new Unit(101, data.getUnit("u_a"), 1, 1);
        bench1.equip(weapon);
        Unit bench2 = new Unit(102, data.getUnit("u_b"), 2, 6);
        Unit deployed = new Unit(103, data.getUnit("u_a"), 1, 1);
        deployed.equip(armor);
        player.addToBench(bench1);
        player.addToBench(bench2);
        player.addToBench(deployed);
        player.deploy(deployed, 1, 5);
        player.addToInventory(trinket);

        ShopSystem shop = new ShopSystem();
        shop.restoreSlots(Arrays.asList(null, data.getUnit("u_a"), data.getUnit("u_b"),
                data.getUnit("u_leg"), null)); // 受控槽位（含空槽）

        RunState runState = new RunState(99L, "scene_forest", "hero_x",
                RunModifiers.EMPTY, new SequentialIdIssuer(200));
        runState.setRound(4);
        runState.setMercyLossCount(2);
        runState.setMercyGoldThisRound(1);
        runState.markRunStarted();
        runState.setPhase(GamePhase.SHOPPING);
        runState.setEnemyWave(Arrays.asList(
                new WaveSpec(data.getUnit("u_boss"), 1, 1.0f, 2, 1),
                new WaveSpec(data.getUnit("u_a"), 1, 1.3f, 0, 2)));

        RandomGenerator rng = new RandomGenerator(99L);
        rng.weightedPick(new int[]{1, 1});
        rng.nextFloat(); // 已消耗 2（模拟局内随机流转）
        return new RunContext(player, runState, data, rng, shop);
    }

    // —— 富态 round-trip：capture 字段 ——

    @Test
    @DisplayName("capture：全字段落位（RNG 计数/发号续号/装备池下标/部署 18 格/商店空槽）")
    void captureCapturesEverything() {
        GameData data = data();
        RunSnapshot s = SnapshotCodec.capture(richContext(data));
        assertThat(s.getVersion()).isEqualTo(1);
        assertThat(s.getSeed()).isEqualTo(99L);
        assertThat(s.getRngConsumedCount()).isEqualTo(2);
        assertThat(s.getSceneId()).isEqualTo("scene_forest");
        assertThat(s.getHeroId()).isEqualTo("hero_x");
        assertThat(s.getRound()).isEqualTo(4);
        assertThat(s.getMercyLossCount()).isEqualTo(2);
        assertThat(s.getMercyGoldThisRound()).isEqualTo(1);
        assertThat(s.getIdIssuerNext()).isEqualTo(200);
        assertThat(s.getPlayerGold()).isEqualTo(37);
        assertThat(s.getPlayerLevel()).isEqualTo(3);
        assertThat(s.getPlayerExp()).isEqualTo(5);

        assertThat(s.getUnits()).hasSize(3); // bench1 + bench2 + deployed
        assertThat(s.getUnits().get(0).getUnitId()).isEqualTo("u_a");
        assertThat(s.getUnits().get(0).getEquippedItemIndex()).containsExactly(0); // eq_w 池位
        assertThat(s.getUnits().get(1).getUnitId()).isEqualTo("u_b");
        assertThat(s.getUnits().get(1).getEquippedItemIndex()).isEmpty();
        assertThat(s.getUnits().get(2).getUnitId()).isEqualTo("u_a");
        assertThat(s.getUnits().get(2).getEquippedItemIndex()).containsExactly(1); // eq_a 池位
        assertThat(s.getUnits().get(1).getStar()).isEqualTo(2);
        assertThat(s.getUnits().get(1).getSpend()).isEqualTo(6);

        assertThat(s.getBenchUnitIndex()).containsExactly(0, 1);
        assertThat(s.getDeploymentUnitIndex()).hasSize(18);
        assertThat(s.getDeploymentUnitIndex().get(7)).isEqualTo(2); // (x=1, y=5) → (5-4)*6+1
        assertThat(s.getDeploymentUnitIndex().stream().filter(i -> i < 0).count()).isEqualTo(17);

        assertThat(s.getEquipments()).hasSize(3); // 已穿 2 + 背包 1
        assertThat(s.getInventory()).hasSize(1);
        assertThat(s.getInventory().get(0).getId()).isEqualTo(23);

        assertThat(s.getShopSlotUnitIds())
                .containsExactly(null, "u_a", "u_b", "u_leg", null);
        assertThat(s.getEnemyWave()).hasSize(2);
        assertThat(s.getEnemyWave().get(0).getUnitId()).isEqualTo("u_boss");
    }

    @Test
    @DisplayName("write → read：序列化对称（全字段等价回读）")
    void writeReadRoundTrip() {
        GameData data = data();
        RunSnapshot captured = SnapshotCodec.capture(richContext(data));
        RunSnapshot read = SnapshotCodec.read(SnapshotCodec.write(captured));
        assertThat(read.getVersion()).isEqualTo(captured.getVersion());
        assertThat(read.getSeed()).isEqualTo(captured.getSeed());
        assertThat(read.getRngConsumedCount()).isEqualTo(captured.getRngConsumedCount());
        assertThat(read.getSceneId()).isEqualTo(captured.getSceneId());
        assertThat(read.getHeroId()).isEqualTo(captured.getHeroId());
        assertThat(read.getRound()).isEqualTo(captured.getRound());
        assertThat(read.getMercyLossCount()).isEqualTo(captured.getMercyLossCount());
        assertThat(read.getMercyGoldThisRound()).isEqualTo(captured.getMercyGoldThisRound());
        assertThat(read.getIdIssuerNext()).isEqualTo(captured.getIdIssuerNext());
        assertThat(read.getPlayerGold()).isEqualTo(captured.getPlayerGold());
        assertThat(read.getPlayerLevel()).isEqualTo(captured.getPlayerLevel());
        assertThat(read.getPlayerExp()).isEqualTo(captured.getPlayerExp());
        assertThat(read.getUnits()).hasSameSizeAs(captured.getUnits());
        assertThat(read.getUnits().get(0).getUnitId())
                .isEqualTo(captured.getUnits().get(0).getUnitId());
        assertThat(read.getUnits().get(0).getEquippedItemIndex())
                .isEqualTo(captured.getUnits().get(0).getEquippedItemIndex());
        assertThat(read.getBenchUnitIndex()).isEqualTo(captured.getBenchUnitIndex());
        assertThat(read.getDeploymentUnitIndex()).isEqualTo(captured.getDeploymentUnitIndex());
        assertThat(read.getInventory()).hasSameSizeAs(captured.getInventory());
        assertThat(read.getEquipments()).hasSameSizeAs(captured.getEquipments());
        assertThat(read.getShopSlotUnitIds()).containsExactlyElementsOf(captured.getShopSlotUnitIds());
        assertThat(read.getEnemyWave()).hasSameSizeAs(captured.getEnemyWave());
        assertThat(read.getEnemyWave().get(1).getScale()).isEqualTo(1.3f);
    }

    // —— restore：上下文逐项等价 ——

    @Test
    @DisplayName("restore：RunContext 完整复原（名单/装备归属/商店/敌阵/阶段/modifiers 重算）")
    void restoreRebuildsFullContext() {
        GameData data = data();
        RunSnapshot captured = SnapshotCodec.capture(richContext(data));
        RunContext restored = SnapshotCodec.restore(captured, data, Profile.fresh(),
                new ShopSystem());

        RunState runState = restored.getRunState();
        assertThat(runState.isRunStarted()).isTrue();
        assertThat(runState.getPhase()).isEqualTo(GamePhase.SHOPPING);
        assertThat(runState.getRound()).isEqualTo(4);
        assertThat(runState.getMercyLossCount()).isEqualTo(2);
        assertThat(runState.getMercyGoldThisRound()).isEqualTo(1);
        assertThat(runState.getHeroId()).isEqualTo("hero_x");
        assertThat(runState.getEnemyWave()).containsExactly(
                new WaveSpec(data.getUnit("u_boss"), 1, 1.0f, 2, 1),
                new WaveSpec(data.getUnit("u_a"), 1, 1.3f, 0, 2)); // WaveSpec.equals 对拍
        assertThat(runState.getIdIssuer().peekNext()).isEqualTo(200);
        assertThat(restored.getRng().getConsumedCount()).isEqualTo(2);
        assertThat(runState.getModifiers().getStartGoldBonus())
                .isEqualTo(4); // 按当前档案重算：Lv.1 权益 2 + 被动 2

        Player player = restored.getPlayer();
        assertThat(player.getGold()).isEqualTo(37);
        assertThat(player.getLevel()).isEqualTo(3);
        assertThat(player.getCurrentExp()).isEqualTo(5);
        assertThat(player.getBench()).extracting(Unit::getId).containsExactly(101, 102);
        assertThat(player.getBench().get(0).getStar()).isEqualTo(1);
        assertThat(player.getBench().get(1).getStar()).isEqualTo(2);
        assertThat(player.getBench().get(1).getSpend()).isEqualTo(6);
        assertThat(player.getBench().get(0).getEquipped())
                .extracting(Equipment::getId)
                .containsExactly(21);
        assertThat(player.deployedAt(1, 5).getId()).isEqualTo(103);
        assertThat(player.deployedAt(1, 5).getEquipped())
                .extracting(Equipment::getId)
                .containsExactly(22);
        assertThat(player.deployedAt(0, 4)).isNull();
        assertThat(player.getInventory()).extracting(Equipment::getId).containsExactly(23);

        assertThat(restored.getShop().slotAt(0)).isNull();
        assertThat(restored.getShop().slotAt(1).getId()).isEqualTo("u_a");
        assertThat(restored.getShop().slotAt(3).getId()).isEqualTo("u_leg");
        assertThat(restored.getShop().slotAt(4)).isNull();
    }

    @Test
    @DisplayName("续战等价：restore 后推进一轮，与未挂起的同 seed 上下文直接推进逐位相同")
    void resumeEquivalenceAfterOneRound() {
        GameData data = data();
        RunContext baseline = richContext(data); // 未挂起参照系
        RunContext restored = SnapshotCodec.restore(
                SnapshotCodec.capture(richContext(data)), data, Profile.fresh(), new ShopSystem());

        RunFlowSystem flow = new RunFlowSystem();
        flow.advanceAfterVictory(baseline);
        flow.advanceAfterVictory(restored);

        assertThat(restored.getRunState().getRound()).isEqualTo(baseline.getRunState().getRound());
        assertThat(restored.getRunState().getEnemyWave())
                .containsExactlyElementsOf(baseline.getRunState().getEnemyWave()); // RNG 流对齐
        assertThat(slotIds(restored.getShop())).isEqualTo(slotIds(baseline.getShop()));
        assertThat(restored.getRng().getConsumedCount())
                .isEqualTo(baseline.getRng().getConsumedCount());
        assertThat(restored.getPlayer().getGold()).isEqualTo(baseline.getPlayer().getGold());
    }

    private static List<String> slotIds(ShopSystem shop) {
        List<String> ids = new ArrayList<String>();
        for (UnitData slot : shop.getSlots()) {
            ids.add(slot == null ? null : slot.getId());
        }
        return ids;
    }

    // —— 捕获门控与读校验 ——

    @Test
    @DisplayName("非备战期捕获（BATTLE）→ IllegalStateException；null ctx → NPE")
    void captureOnlyInShoppingPhase() {
        GameData data = data();
        RunContext ctx = richContext(data);
        ctx.getRunState().setPhase(GamePhase.BATTLE);
        assertThatThrownBy(() -> SnapshotCodec.capture(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("备战期");
        assertThatThrownBy(() -> SnapshotCodec.capture(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("read 校验：版本不符 / 未知字段 / 部署表长度错 / 商店槽数错")
    void readValidatesStructure() {
        GameData data = data();
        String json = SnapshotCodec.write(SnapshotCodec.capture(richContext(data)));
        assertThatThrownBy(() -> SnapshotCodec.read(json.replace("\"version\":1", "\"version\":9")))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("不支持的快照版本");
        assertThatThrownBy(() -> SnapshotCodec.read(json.replace("\"round\":4", "\"round\":4,\"zzz\":1")))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("未知字段 zzz");
        assertThatThrownBy(() -> SnapshotCodec.read(json.replace(
                "\"deploymentUnitIndex\":[", "\"deploymentUnitIndexExtra\":[")
                .replace("],\"inventory\"", "],\"deploymentUnitIndex\":[],\"inventory\"")))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("deploymentUnitIndex");
    }

    @Test
    @DisplayName("restore 引用悬空四路：sceneId / heroId / units / equipments / enemyWave / shopSlots")
    void restoreRejectsDanglingReferences() {
        GameData data = data();
        String json = SnapshotCodec.write(SnapshotCodec.capture(richContext(data)));

        assertThatThrownBy(() -> restoreFrom(json.replace(
                "\"sceneId\":\"scene_forest\"", "\"sceneId\":\"scene_missing\""), data))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("sceneId");
        assertThatThrownBy(() -> restoreFrom(json.replace(
                "\"heroId\":\"hero_x\"", "\"heroId\":\"hero_missing\""), data))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("heroId");
        assertThatThrownBy(() -> restoreFrom(json.replace("\"u_leg\"", "\"u_missing\""), data))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("u_missing");
        assertThatThrownBy(() -> restoreFrom(json.replace("\"eq_t\"", "\"eq_missing\""), data))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("eq_missing");
        assertThatThrownBy(() -> restoreFrom(json.replace("\"u_boss\"", "\"u_missing\""), data))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("u_missing");
        assertThatThrownBy(() -> restoreFrom(json.replace("\"unitId\":\"u_b\"", "\"unitId\":\"u_missing\""), data))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("u_missing");
    }

    private static RunContext restoreFrom(String json, GameData data) {
        return SnapshotCodec.restore(SnapshotCodec.read(json), data, Profile.fresh(),
                new ShopSystem());
    }

    @Test
    @DisplayName("装备池下标越限防御：手搓快照 equippedItemIndex 越界 → DataValidationException")
    void restoreRejectsOutOfRangeEquipmentIndex() {
        GameData data = data();
        RunSnapshot captured = SnapshotCodec.capture(richContext(data));
        RunSnapshot broken = new RunSnapshot(captured.getVersion(), captured.getSeed(),
                captured.getRngConsumedCount(), captured.getSceneId(), captured.getHeroId(),
                captured.getRound(), captured.getMercyLossCount(), captured.getMercyGoldThisRound(),
                captured.getIdIssuerNext(), captured.getPlayerGold(), captured.getPlayerLevel(),
                captured.getPlayerExp(),
                Collections.singletonList(new RunSnapshot.UnitSnapshot(
                        101, "u_a", 1, 1, Collections.singletonList(99))), // 池只有 3 件
                captured.getBenchUnitIndex(), captured.getDeploymentUnitIndex(),
                captured.getInventory(), captured.getEquipments(), captured.getShopSlotUnitIds(),
                captured.getEnemyWave());
        assertThatThrownBy(() -> SnapshotCodec.restore(broken, data, Profile.fresh(),
                new ShopSystem()))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("装备池下标越限");
    }
}
