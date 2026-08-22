package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ShopBar 预校验测试（CP22；input §4.3 表现层灰置，只读不改状态）：
 * canBuy = 空槽假 / 金币不足假 / 席满假 / 席满+同名一星×2 真（购买即 3 合 1 例外，
 * 备战席与上场名单都计数）、星级不匹配不计数。绘制走 lwjgl3 手验。
 */
class ShopBarLogicTest {

    private static UnitData unit(String id, int cost) {
        return new UnitData(id, "兵" + id, "兽人", "战士", cost,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, false);
    }

    /** 可购池仅一个一费模板：第 1 轮 100% 一费 → reroll 后 5 槽全为该模板（确定性） */
    private static GameData data() {
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        units.put("u_orc", unit("u_orc", 1));
        return new GameData(units,
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                new java.util.ArrayList<String>());
    }

    private static final UnitData FILLER = unit("u_filler", 1);

    private static RunContext context(int gold) {
        RunContext ctx = new RunContext(new Player(gold),
                new RunState(42L, "scene_forest", new SequentialIdIssuer()),
                data(), new RandomGenerator(42L));
        ctx.getShop().reroll(1, ctx.getGameData(), ctx.getRng());
        return ctx;
    }

    private static void fillBench(RunContext ctx, int count) {
        for (int i = 0; i < count; i++) {
            ctx.getPlayer().addToBench(new Unit(100 + i, FILLER, 1));
        }
    }

    @Test
    @DisplayName("金币充足席未满：5 槽全部可买")
    void affordableSlotIsBuyable() {
        RunContext ctx = context(10);
        for (int slot = 0; slot < GameBalance.SHOP_SLOTS; slot++) {
            assertThat(ShopBar.canBuy(ctx, slot)).as("slot " + slot).isTrue();
        }
    }

    @Test
    @DisplayName("金币不足：灰置（一费模板 vs 0 金）")
    void insufficientGoldGreysOut() {
        RunContext ctx = context(0);
        assertThat(ShopBar.canBuy(ctx, 0)).isFalse();
    }

    @Test
    @DisplayName("空槽（已购/未刷新）：不可买；越界槽同假")
    void emptyAndOutOfBoundsSlotsAreNotBuyable() {
        RunContext neverRerolled = new RunContext(new Player(10),
                new RunState(42L, "scene_forest", new SequentialIdIssuer()),
                data(), new RandomGenerator(42L)); // 商店槽位全空
        assertThat(ShopBar.canBuy(neverRerolled, 0)).isFalse();
        assertThat(ShopBar.canBuy(neverRerolled, GameBalance.SHOP_SLOTS)).isFalse();
        assertThat(ShopBar.canBuy(neverRerolled, -1)).isFalse();
    }

    @Test
    @DisplayName("备战席满且无同名一星×2：灰置（席满禁买）")
    void fullBenchWithoutMergePairGreysOut() {
        RunContext ctx = context(10);
        fillBench(ctx, GameBalance.BENCH_SIZE);
        assertThat(ShopBar.canBuy(ctx, 0)).isFalse();
    }

    @Test
    @DisplayName("席满例外：席上恰有同名一星×2 → 购买即 3 合 1，亮置")
    void fullBenchWithBenchPairAllowsBuy() {
        RunContext ctx = context(10);
        fillBench(ctx, GameBalance.BENCH_SIZE - 2);
        UnitData tpl = ctx.getShop().slotAt(0);
        ctx.getPlayer().addToBench(new Unit(201, tpl, 1));
        ctx.getPlayer().addToBench(new Unit(202, tpl, 1));
        assertThat(ctx.getPlayer().getBench().size()).isEqualTo(GameBalance.BENCH_SIZE);
        assertThat(ShopBar.canBuy(ctx, 0)).isTrue();
    }

    @Test
    @DisplayName("席上同名×2 但星级为 2：不构成 3 合 1，仍灰置")
    void twoStarPairDoesNotCount() {
        RunContext ctx = context(10);
        fillBench(ctx, GameBalance.BENCH_SIZE - 2);
        UnitData tpl = ctx.getShop().slotAt(0);
        ctx.getPlayer().addToBench(new Unit(201, tpl, 2));
        ctx.getPlayer().addToBench(new Unit(202, tpl, 2));
        assertThat(ShopBar.canBuy(ctx, 0)).isFalse();
    }

    @Test
    @DisplayName("上场名单的同名一星也计数：席满（无同名）+ 板上同名×2 → 亮置")
    void deployedPairCounts() {
        RunContext ctx = context(10);
        UnitData tpl = ctx.getShop().slotAt(0);
        Unit first = new Unit(201, tpl, 1);
        Unit second = new Unit(202, tpl, 1);
        ctx.getPlayer().addToBench(first);
        ctx.getPlayer().addToBench(second);
        ctx.getPlayer().deploy(first, 0, 4);
        ctx.getPlayer().deploy(second, 1, 4);
        fillBench(ctx, GameBalance.BENCH_SIZE); // 撤走两名后补满 9 席
        assertThat(ctx.getPlayer().getBench().size()).isEqualTo(GameBalance.BENCH_SIZE);
        assertThat(ShopBar.canBuy(ctx, 0)).isTrue();
    }

    @Test
    @DisplayName("预校验只读：canBuy 不改金币/席/槽位（无副作用断言）")
    void canBuyIsReadOnly() {
        RunContext ctx = context(10);
        fillBench(ctx, 2);
        ShopBar.canBuy(ctx, 0);
        ShopBar.canBuy(ctx, 1);
        assertThat(ctx.getPlayer().getGold()).isEqualTo(10);
        assertThat(ctx.getPlayer().getBench()).hasSize(2);
        assertThat(ctx.getShop().slotAt(0)).isNotNull();
    }

    // —— Phase 6：动态价签（CP9——Lv.5 折扣实付下限 1 金，裁决 D4） ——

    @Test
    @DisplayName("刷新钮价签：EMPTY → 2 金、Lv.5 折扣 1 → 1 金、折扣越界钳下限")
    void refreshPriceTextReflectsDiscount() {
        assertThat(ShopBar.refreshPriceText(RunModifiers.EMPTY)).isEqualTo("刷新 2金");
        assertThat(ShopBar.refreshPriceText(new RunModifiers(0, 1, 0, 0,
                new java.util.LinkedHashMap<String, Float>(), null,
                new java.util.LinkedHashSet<String>(), false))).isEqualTo("刷新 1金");
        assertThat(ShopBar.refreshPriceText(new RunModifiers(0, 9, 0, 0,
                new java.util.LinkedHashMap<String, Float>(), null,
                new java.util.LinkedHashSet<String>(), false))).isEqualTo("刷新 1金");
    }
}
