package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.entities.ChestOffer;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 宝箱系统测试（CP9；Q2 裁决 A 最小可玩规则）：roll 确定性与 RNG 恰 2 消耗、
 * 槽序固定（金币/经验书/装备）、Boss 箱必含 ≥成装、稀有度降级与空表兜底、apply 三分支。
 */
class ChestSystemTest {

    // —— 夹具：装备模板与装备表数据集 ——

    private static EquipmentData eq(String id, EquipmentRarity rarity, EquipmentSlot slot) {
        return new EquipmentData(id, "装" + id, slot, rarity,
                Arrays.asList(new EquipmentEffect(StatKey.ATTACK, EffectOp.ADD, 1f)), null);
    }

    private static GameData dataWith(EquipmentData... items) {
        Map<String, EquipmentData> equipments = new LinkedHashMap<String, EquipmentData>();
        for (EquipmentData item : items) {
            equipments.put(item.getId(), item);
        }
        return new GameData(new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.UnitData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                equipments, new ArrayList<String>());
    }

    private static GameData fullRarityData() {
        return dataWith(eq("eq_white", EquipmentRarity.WHITE, EquipmentSlot.WEAPON),
                eq("eq_rare", EquipmentRarity.RARE, EquipmentSlot.ARMOR),
                eq("eq_legendary", EquipmentRarity.LEGENDARY, EquipmentSlot.TRINKET));
    }

    // —— roll：确定性与 RNG 消耗 ——

    @Test
    @DisplayName("同 seed 两次 roll 结果完全一致（ChestOffer 值语义 equals）")
    void rollDeterministic() {
        ChestSystem system = new ChestSystem();
        ChestOffer first = system.roll(4, fullRarityData(), new RandomGenerator(42L));
        ChestOffer second = system.roll(4, fullRarityData(), new RandomGenerator(42L));
        assertThat(second).isEqualTo(first);
        assertThat(second).hasSameHashCodeAs(first);
    }

    @Test
    @DisplayName("roll 恰好消耗 2 RNG（稀有度 1 + 池内抽取 1；金币/经验选项零 RNG）")
    void rollConsumesExactlyTwoRng() {
        RandomGenerator rng = new RandomGenerator(42L);
        rng.nextInt(100); // 前置消耗：验证差值口径而非绝对计数
        int before = rng.getConsumedCount();
        new ChestSystem().roll(4, fullRarityData(), rng);
        assertThat(rng.getConsumedCount() - before).isEqualTo(2);
    }

    // —— roll：槽序与 Boss 规则 ——

    @Test
    @DisplayName("普通箱（第 4 轮非 Boss）：槽1 金币=chestGold(4,false)=4、槽2 经验书 4、槽3 装备")
    void normalChestSlotOrder() {
        ChestOffer offer = new ChestSystem().roll(4, fullRarityData(), new RandomGenerator(42L));
        assertThat(offer.getRound()).isEqualTo(4);
        assertThat(offer.isBoss()).isFalse();
        assertThat(offer.getOptions()).hasSize(3);
        assertThat(offer.optionAt(0)).isEqualTo(ChestOption.gold(GameBalance.chestGold(4, false)));
        assertThat(offer.optionAt(0).getAmount()).isEqualTo(4);
        assertThat(offer.optionAt(1)).isEqualTo(ChestOption.expBook(GameBalance.CHEST_EXP_BOOK_GAIN));
        assertThat(offer.optionAt(2).getKind()).isEqualTo(ChestOption.Kind.EQUIPMENT);
    }

    @Test
    @DisplayName("Boss 箱（第 7 轮）：槽1 金币翻倍、装备槽必为成/传（白位权重 0，多 seed 全量断言）")
    void bossChestNeverWhite() {
        GameData data = fullRarityData();
        for (long seed = 1; seed <= 200; seed++) {
            ChestOffer offer = new ChestSystem().roll(7, data, new RandomGenerator(seed));
            assertThat(offer.isBoss()).as("seed=%s", seed).isTrue();
            assertThat(offer.optionAt(0)).as("seed=%s", seed)
                    .isEqualTo(ChestOption.gold(GameBalance.chestGold(7, true)));
            ChestOption equipment = offer.optionAt(2);
            assertThat(equipment.getKind()).as("seed=%s", seed).isEqualTo(ChestOption.Kind.EQUIPMENT);
            assertThat(data.getEquipment(equipment.getEquipmentId()).getRarity()).as("seed=%s", seed)
                    .isIn(EquipmentRarity.RARE, EquipmentRarity.LEGENDARY);
        }
    }

    // —— roll：内容缺失防御 ——

    @Test
    @DisplayName("单稀有度数据集：roll 到高空档向低稀有度降级（结果恒为白装，降级路径必有覆盖）")
    void degradeToLowerRarityWhenPoolEmpty() {
        GameData full = fullRarityData();
        GameData whiteOnly = dataWith(eq("eq_white", EquipmentRarity.WHITE, EquipmentSlot.WEAPON));
        int degradedSeeds = 0;
        for (long seed = 1; seed <= 200; seed++) {
            ChestOption fullPick = new ChestSystem().roll(4, full, new RandomGenerator(seed)).optionAt(2);
            ChestOption whitePick = new ChestSystem().roll(4, whiteOnly, new RandomGenerator(seed)).optionAt(2);
            assertThat(whitePick.getKind()).as("seed=%s", seed).isEqualTo(ChestOption.Kind.EQUIPMENT);
            assertThat(whitePick.getEquipmentId()).as("seed=%s", seed).isEqualTo("eq_white");
            if (full.getEquipment(fullPick.getEquipmentId()).getRarity() != EquipmentRarity.WHITE) {
                degradedSeeds++; // 该 seed 稀有度 roll 落在成/传，白装集上走了降级路径
            }
        }
        assertThat(degradedSeeds).isPositive();
    }

    @Test
    @DisplayName("空装备表：槽3 退化为金币兜底且 RNG 仍恰好 2")
    void emptyTableFallsBackToGold() {
        RandomGenerator rng = new RandomGenerator(42L);
        ChestOffer offer = new ChestSystem().roll(4, dataWith(), rng);
        assertThat(offer.optionAt(2)).isEqualTo(ChestOption.gold(GameBalance.chestGold(4, false)));
        assertThat(rng.getConsumedCount()).isEqualTo(2);
    }

    // —— apply：三分支入账 ——

    @Test
    @DisplayName("apply 金币/经验分支：入账并返回通知文案（Lv.1 + 4 经验恰好连升一级）")
    void applyGoldAndExpBranches() {
        Player player = new Player(10);
        ChestSystem system = new ChestSystem();
        assertThat(system.apply(ChestOption.gold(4), player, new SequentialIdIssuer(), dataWith()))
                .isEqualTo("宝箱：金币 +4");
        assertThat(player.getGold()).isEqualTo(14);
        assertThat(system.apply(ChestOption.expBook(4), player, new SequentialIdIssuer(), dataWith()))
                .isEqualTo("宝箱：经验 +4");
        assertThat(player.getLevel()).isEqualTo(2);
        assertThat(player.getCurrentExp()).isZero();
    }

    @Test
    @DisplayName("apply 装备分支：发号入包、模板正确、返回装备名")
    void applyEquipmentBranch() {
        GameData data = dataWith(eq("eq_sword", EquipmentRarity.RARE, EquipmentSlot.WEAPON));
        Player player = new Player(0);
        SequentialIdIssuer issuer = new SequentialIdIssuer();
        issuer.nextId(); // 前置消耗一个号：断言装备拿到的是下一个 id
        String message = new ChestSystem().apply(ChestOption.equipment("eq_sword"), player, issuer, data);
        assertThat(message).isEqualTo("宝箱：获得 装eq_sword");
        assertThat(player.getInventory()).hasSize(1);
        assertThat(player.getInventory().get(0).getId()).isEqualTo(2);
        assertThat(player.getInventory().get(0).getTemplate().getId()).isEqualTo("eq_sword");
    }

    // —— ChestOffer / ChestOption 值语义与校验 ——

    @Test
    @DisplayName("ChestOffer 恰三选项约束；optionAt 越界返回 null")
    void chestOfferValidation() {
        List<ChestOption> two = Arrays.asList(ChestOption.gold(1), ChestOption.expBook(1));
        assertThatThrownBy(() -> new ChestOffer(1, false, two))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("三");
        ChestOffer offer = new ChestOffer(1, true, Arrays.asList(
                ChestOption.gold(1), ChestOption.expBook(1), ChestOption.equipment("eq_white")));
        assertThat(offer.getOptions()).hasSize(3);
        assertThat(offer.optionAt(-1)).isNull();
        assertThat(offer.optionAt(3)).isNull();
        assertThat(offer.getRound()).isEqualTo(1);
        assertThat(offer.isBoss()).isTrue();
    }
}
