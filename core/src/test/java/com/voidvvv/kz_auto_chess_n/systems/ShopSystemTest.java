package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.BuyExpCommand;
import com.voidvvv.kz_auto_chess_n.command.BuyUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.GameCommand;
import com.voidvvv.kz_auto_chess_n.command.RefreshShopCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商店系统测试（CP10；Q6 裁决：起始 10 金商店自购）：reroll 确定性与 RNG 恰 10、
 * 费阶/Boss 过滤、buy 全校验链、席满例外（购买即合成不占席）、3 合 1 级联与 spend/装备折叠、
 * 经营命令门控矩阵（BATTLE 拒收）。
 */
class ShopSystemTest {

    // —— 夹具：可购池（1/2/3 费 + 一费 Boss 干扰项）——

    private static UnitData unit(String id, int cost, boolean boss) {
        return new UnitData(id, "兵" + id, "兽人", "战士", cost,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, boss);
    }

    private static GameData shopData() {
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        units.put("u_cost1", unit("u_cost1", 1, false));
        units.put("u_cost1b", unit("u_cost1b", 1, false));
        units.put("u_cost2", unit("u_cost2", 2, false));
        units.put("u_cost3", unit("u_cost3", 3, false));
        units.put("u_boss", unit("u_boss", 1, true)); // Boss 模板不入商店池
        return new GameData(units,
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                new LinkedHashMap<String, EquipmentData>(), new ArrayList<String>());
    }

    private static RunContext context(GameData data, int gold) {
        return new RunContext(new Player(gold), new RunState(42L, "scene_forest", new SequentialIdIssuer()),
                data, new RandomGenerator(42L));
    }

    private static int slotOf(ShopSystem shop, String templateId) {
        for (int i = 0; i < shop.getSlots().size(); i++) {
            UnitData slot = shop.getSlots().get(i);
            if (slot != null && slot.getId().equals(templateId)) {
                return i;
            }
        }
        return -1;
    }

    /** 找到包含指定模板的商店（双模板一费池 × 5 槽，多 seed 兜底保证确定性可复现） */
    private static ShopSystem shopContaining(GameData data, String templateId) {
        ShopSystem shop = new ShopSystem();
        for (long seed = 1; seed <= 50; seed++) {
            shop.reroll(1, data, new RandomGenerator(seed));
            if (slotOf(shop, templateId) >= 0) {
                return shop;
            }
        }
        throw new AssertionError("50 个 seed 内未刷出模板 " + templateId);
    }

    private static CommandManager armed(ShopSystem shop) {
        CommandManager manager = new CommandManager();
        shop.registerHandlers(manager);
        return manager;
    }

    private static List<GameCommand> executedTracker(CommandManager manager) {
        final List<GameCommand> executed = new ArrayList<GameCommand>();
        manager.addListener((cmd, success) -> executed.add(cmd));
        return executed;
    }

    // —— reroll ——

    @Test
    @DisplayName("reroll 同 seed 同结果、恰好消耗 10 RNG（费阶 1 + 池内抽取 1，每槽 2）")
    void rerollDeterministicAndExactRng() {
        GameData data = shopData();
        ShopSystem shopA = new ShopSystem();
        ShopSystem shopB = new ShopSystem();
        RandomGenerator rngA = new RandomGenerator(7L);
        RandomGenerator rngB = new RandomGenerator(7L);
        shopA.reroll(5, data, rngA);
        shopB.reroll(5, data, rngB);
        assertThat(slotIds(shopB)).isEqualTo(slotIds(shopA));
        assertThat(rngA.getConsumedCount()).isEqualTo(10);
    }

    private static List<String> slotIds(ShopSystem shop) {
        List<String> ids = new ArrayList<String>();
        for (UnitData slot : shop.getSlots()) {
            ids.add(slot == null ? null : slot.getId());
        }
        return ids;
    }

    @Test
    @DisplayName("第 1~3 轮：全部槽位为一费非 Boss（P1=100%，Boss 模板永不入池）")
    void earlyRoundsAllTierOneWithoutBoss() {
        GameData data = shopData();
        for (int round = 1; round <= 3; round++) {
            for (long seed = 1; seed <= 10; seed++) {
                ShopSystem shop = new ShopSystem();
                shop.reroll(round, data, new RandomGenerator(seed));
                assertThat(shop.getSlots()).as("round=%s seed=%s", round, seed).hasSize(5);
                for (UnitData slot : shop.getSlots()) {
                    assertThat(slot).as("round=%s seed=%s", round, seed).isNotNull();
                    assertThat(slot.getCost()).as("round=%s seed=%s", round, seed).isEqualTo(1);
                    assertThat(slot.isBoss()).as("round=%s seed=%s", round, seed).isFalse();
                }
            }
        }
    }

    @Test
    @DisplayName("slotAt 越界返回 null；getSlots 为不可变快照（空槽为 null 元素）")
    void slotAccessors() {
        ShopSystem shop = new ShopSystem(); // 未 reroll：全空槽
        assertThat(shop.slotAt(-1)).isNull();
        assertThat(shop.slotAt(5)).isNull();
        assertThat(shop.getSlots()).hasSize(GameBalance.SHOP_SLOTS);
        assertThat(shop.getSlots().get(0)).isNull();
    }

    // —— buy：校验链 ——

    @Test
    @DisplayName("buy：扣金币、槽位置 null、新 Unit 入席（spend=售价）并产生购入通知")
    void buyDeductsClearsSlotAndBenches() {
        GameData data = shopData();
        RunContext ctx = context(data, 10);
        ShopSystem shop = new ShopSystem();
        shop.reroll(1, data, new RandomGenerator(42L));
        UnitData template = shop.slotAt(0);
        assertThat(shop.buy(ctx, 0)).isTrue();
        assertThat(ctx.getPlayer().getGold()).isEqualTo(10 - template.getCost());
        assertThat(shop.slotAt(0)).isNull();
        assertThat(ctx.getPlayer().getBench()).hasSize(1);
        Unit bought = ctx.getPlayer().getBench().get(0);
        assertThat(bought.getTemplate()).isSameAs(template);
        assertThat(bought.getStar()).isEqualTo(1);
        assertThat(bought.getSpend()).isEqualTo(template.getCost());
        assertThat(ctx.getRunState().drainNotices()).anyMatch(line -> line.startsWith("购入 "));
    }

    @Test
    @DisplayName("buy 拒绝：金币不足 / 槽位为空，零状态残留")
    void buyRejectedWhenUnaffordableOrEmptySlot() {
        GameData data = shopData();
        RunContext poor = context(data, 0);
        ShopSystem shop = new ShopSystem();
        shop.reroll(1, data, new RandomGenerator(42L));
        assertThat(shop.buy(poor, 0)).isFalse();
        assertThat(shop.slotAt(0)).isNotNull();
        assertThat(poor.getPlayer().getBench()).isEmpty();

        RunContext fresh = context(data, 10);
        ShopSystem empty = new ShopSystem(); // 未 reroll 全空槽
        assertThat(empty.buy(fresh, 0)).isFalse();
        assertThat(fresh.getPlayer().getBench()).isEmpty();
        assertThat(fresh.getPlayer().getGold()).isEqualTo(10);
    }

    @Test
    @DisplayName("buy 拒绝：席满且不会立即合成（异名填充，无同名一星）")
    void buyRejectedWhenBenchFullWithoutMerge() {
        GameData data = shopData();
        RunContext ctx = context(data, 10);
        ShopSystem shop = shopContaining(data, "u_cost1");
        Player player = ctx.getPlayer();
        for (int i = 0; i < GameBalance.BENCH_SIZE; i++) {
            player.addToBench(new Unit(100 + i, data.getUnit("u_cost2"), 1, 2)); // 异名填充
        }
        assertThat(shop.buy(ctx, slotOf(shop, "u_cost1"))).isFalse();
        assertThat(player.getBench()).hasSize(GameBalance.BENCH_SIZE);
        assertThat(player.getGold()).isEqualTo(10);
        assertThat(shop.slotAt(slotOf(shop, "u_cost1"))).isNotNull();
    }

    // —— buy：席满例外与 3 合 1 ——

    @Test
    @DisplayName("席满例外：同名一星 ×2 时放行购买，合成后备战席净 -1（3 入 1 出，买到的棋子不占席）")
    void benchFullMergeExceptionNetMinusOne() {
        GameData data = shopData();
        RunContext ctx = context(data, 10);
        ShopSystem shop = shopContaining(data, "u_cost1");
        Player player = ctx.getPlayer();
        Unit first = new Unit(101, data.getUnit("u_cost1"), 1, 1);
        Unit second = new Unit(102, data.getUnit("u_cost1"), 1, 1);
        player.addToBench(first);
        player.addToBench(second);
        // 异名填充至 9 满：同 (模板, 星) 至多 2 个——夹具自身不得构成可合并组（级联会重扫全部组）
        player.addToBench(new Unit(110, data.getUnit("u_cost1b"), 1, 1));
        player.addToBench(new Unit(111, data.getUnit("u_cost1b"), 1, 1));
        player.addToBench(new Unit(112, data.getUnit("u_cost2"), 1, 2));
        player.addToBench(new Unit(113, data.getUnit("u_cost2"), 1, 2));
        player.addToBench(new Unit(114, data.getUnit("u_cost3"), 1, 3));
        player.addToBench(new Unit(115, data.getUnit("u_cost3"), 1, 3));
        player.addToBench(new Unit(116, data.getUnit("u_boss"), 1, 1));
        assertThat(shop.buy(ctx, slotOf(shop, "u_cost1"))).isTrue();
        assertThat(player.getBench()).hasSize(8); // 9 → 8：净 -1
        assertThat(player.getGold()).isEqualTo(9);
        assertThat(first.getStar()).isEqualTo(2); // 首位保留者升星
        assertThat(first.getSpend()).isEqualTo(3); // 1 + 1 + 1 折叠
        assertThat(player.getBench()).contains(first).doesNotContain(second);
    }

    @Test
    @DisplayName("席满例外级联：8 同名一星 + 席满买第 9 个 → 1 个三星（spend=9）+ 1 个异名")
    void benchFullExceptionCascadesToThreeStar() {
        GameData data = shopData();
        RunContext ctx = context(data, 10);
        ShopSystem shop = shopContaining(data, "u_cost1");
        Player player = ctx.getPlayer();
        for (int i = 0; i < 8; i++) {
            player.addToBench(new Unit(200 + i, data.getUnit("u_cost1"), 1, 1));
        }
        player.addToBench(new Unit(300, data.getUnit("u_cost2"), 1, 2)); // 第 9 席异名
        assertThat(shop.buy(ctx, slotOf(shop, "u_cost1"))).isTrue();
        assertThat(player.getBench()).hasSize(2);
        Unit survivor = player.getBench().get(0);
        assertThat(survivor.getTemplate().getId()).isEqualTo("u_cost1");
        assertThat(survivor.getStar()).isEqualTo(3);
        assertThat(survivor.getSpend()).isEqualTo(9);
        assertThat(player.getBench().get(1).getTemplate().getId()).isEqualTo("u_cost2");
    }

    @Test
    @DisplayName("3 合 1 级联：席上 8 个 + 买第 9 个同名一星 → 1 个三星（2 星 ×3 → 3 星，spend=9）")
    void mergeCascadeNineToOneThreeStar() {
        GameData data = shopData();
        RunContext ctx = context(data, 10);
        ShopSystem shop = shopContaining(data, "u_cost1");
        Player player = ctx.getPlayer();
        for (int i = 0; i < 8; i++) {
            player.addToBench(new Unit(400 + i, data.getUnit("u_cost1"), 1, 1));
        }
        assertThat(shop.buy(ctx, slotOf(shop, "u_cost1"))).isTrue(); // 席 8 → 9 → 级联
        assertThat(player.getBench()).hasSize(1);
        Unit merged = player.getBench().get(0);
        assertThat(merged.getStar()).isEqualTo(3);
        assertThat(merged.getSpend()).isEqualTo(9);
        assertThat(merged.getTemplate().getId()).isEqualTo("u_cost1");
    }

    @Test
    @DisplayName("合并折叠：被吞并者装备回背包、首位保留装备、spend 折叠（1+1+1 → 2 星 spend 3）")
    void mergeFoldsEquipmentAndSpend() {
        GameData data = shopData();
        RunContext ctx = context(data, 10);
        ShopSystem shop = shopContaining(data, "u_cost1");
        Player player = ctx.getPlayer();
        Unit first = new Unit(501, data.getUnit("u_cost1"), 1, 1);
        Unit second = new Unit(502, data.getUnit("u_cost1"), 1, 1);
        Unit third = new Unit(503, data.getUnit("u_cost1"), 1, 1);
        player.addToBench(first);
        player.addToBench(second);
        player.addToBench(third);
        Equipment charm = new Equipment(21, equip("eq_charm", EquipmentSlot.TRINKET));
        Equipment sword = new Equipment(22, equip("eq_sword", EquipmentSlot.WEAPON));
        Equipment plate = new Equipment(23, equip("eq_plate", EquipmentSlot.ARMOR));
        first.equip(charm);  // 首位保留
        second.equip(sword); // 被吞并 → 回背包
        third.equip(plate);  // 被吞并 → 回背包

        assertThat(shop.buy(ctx, slotOf(shop, "u_cost1"))).isTrue(); // 第 4 个同名单独留在席上
        assertThat(first.getStar()).isEqualTo(2);
        assertThat(first.getSpend()).isEqualTo(3);
        assertThat(first.getEquipped()).containsExactly(charm); // 首位装备保留
        assertThat(player.getInventory()).containsExactly(sword, plate); // 被吞并者装备回包
        assertThat(player.getBench()).hasSize(2); // 首位（2 星）+ 买到的（1 星）
        assertThat(player.getBench().get(1).getStar()).isEqualTo(1);
    }

    private static EquipmentData equip(String id, EquipmentSlot slot) {
        return new EquipmentData(id, "装" + id, slot, EquipmentRarity.WHITE,
                Arrays.asList(new EquipmentEffect(StatKey.ATTACK, EffectOp.ADD, 1f)), null);
    }

    @Test
    @DisplayName("部署位参与合并：首位（部署扫描序最先）保留部署格，其余被吞者格清空")
    void mergeKeepsFirstDeployedCell() {
        GameData data = shopData();
        RunContext ctx = context(data, 10);
        ShopSystem shop = shopContaining(data, "u_cost1b"); // 买异名触发重扫，不并入同名组
        Player player = ctx.getPlayer();
        Unit d1 = new Unit(601, data.getUnit("u_cost1"), 1, 1);
        Unit d2 = new Unit(602, data.getUnit("u_cost1"), 1, 1);
        Unit d3 = new Unit(603, data.getUnit("u_cost1"), 1, 1);
        player.addToBench(d1);
        player.addToBench(d2);
        player.addToBench(d3);
        player.deploy(d1, 1, 4); // Lv.1 人口上限 3
        player.deploy(d2, 2, 4);
        player.deploy(d3, 3, 4);

        assertThat(shop.buy(ctx, slotOf(shop, "u_cost1b"))).isTrue();
        assertThat(player.deployedAt(1, 4)).isSameAs(d1); // 首位保留部署格
        assertThat(d1.getStar()).isEqualTo(2);
        assertThat(player.deployedAt(2, 4)).isNull(); // 被吞并者格清空
        assertThat(player.deployedAt(3, 4)).isNull();
        assertThat(player.getBench()).hasSize(1); // 买到的 u_cost1b 留在席上
        assertThat(player.getBench().get(0).getTemplate().getId()).isEqualTo("u_cost1b");
    }

    // —— 命令接线与门控 ——

    @Test
    @DisplayName("BuyUnitCommand 接线：SHOPPING 期经命令管理器购买成功")
    void buyUnitCommandWiring() {
        GameData data = shopData();
        RunContext ctx = context(data, 10);
        ShopSystem shop = new ShopSystem();
        shop.reroll(1, data, new RandomGenerator(42L));
        CommandManager manager = armed(shop);
        List<GameCommand> executed = executedTracker(manager);
        manager.addCommand(new BuyUnitCommand(0));
        manager.executeAll(ctx);
        assertThat(executed).hasSize(1);
        assertThat(ctx.getPlayer().getBench()).hasSize(1);
        assertThat(ctx.getPlayer().getGold()).isEqualTo(9);
    }

    @Test
    @DisplayName("门控矩阵：BATTLE 期 BuyUnit/RefreshShop/BuyExp 全部拒绝且零状态零 RNG 变化")
    void battlePhaseRejectsShopCommands() {
        GameData data = shopData();
        RunContext ctx = context(data, 10);
        ShopSystem shop = new ShopSystem();
        shop.reroll(1, data, new RandomGenerator(42L));
        CommandManager manager = armed(shop);
        List<GameCommand> executed = executedTracker(manager);
        ctx.getRunState().setPhase(GamePhase.BATTLE);
        int rngBefore = ctx.getRng().getConsumedCount();
        manager.addCommand(new BuyUnitCommand(0));
        manager.addCommand(RefreshShopCommand.INSTANCE);
        manager.addCommand(BuyExpCommand.INSTANCE);
        manager.executeAll(ctx);
        assertThat(executed).isEmpty();
        assertThat(ctx.getPlayer().getGold()).isEqualTo(10);
        assertThat(ctx.getPlayer().getBench()).isEmpty();
        assertThat(ctx.getPlayer().getLevel()).isEqualTo(1);
        assertThat(shop.slotAt(0)).isNotNull();
        assertThat(ctx.getRng().getConsumedCount()).isEqualTo(rngBefore);
    }

    @Test
    @DisplayName("RefreshShop：扣 2 金整批重掷（RNG 再 +10）")
    void refreshShopCommand() {
        GameData data = shopData();
        RunContext ctx = context(data, 2);
        ShopSystem shop = new ShopSystem();
        shop.reroll(1, data, ctx.getRng()); // 轮首免费口径：10 RNG
        CommandManager manager = armed(shop);
        List<GameCommand> executed = executedTracker(manager);
        manager.addCommand(RefreshShopCommand.INSTANCE);
        manager.executeAll(ctx);
        assertThat(executed).hasSize(1);
        assertThat(ctx.getPlayer().getGold()).isZero();
        assertThat(ctx.getRng().getConsumedCount()).isEqualTo(20);
    }

    @Test
    @DisplayName("RefreshShop 拒绝：金币不足（<2 金）时槽位与 RNG 均不变")
    void refreshShopRejectedWhenUnaffordable() {
        GameData data = shopData();
        RunContext ctx = context(data, 1);
        ShopSystem shop = new ShopSystem();
        shop.reroll(1, data, ctx.getRng());
        UnitData before = shop.slotAt(0);
        CommandManager manager = armed(shop);
        List<GameCommand> executed = executedTracker(manager);
        manager.addCommand(RefreshShopCommand.INSTANCE);
        manager.executeAll(ctx);
        assertThat(executed).isEmpty();
        assertThat(ctx.getPlayer().getGold()).isEqualTo(1);
        assertThat(shop.slotAt(0)).isSameAs(before);
        assertThat(ctx.getRng().getConsumedCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("BuyExp：4 金换 4 经验（Lv.1 恰升 Lv.2）；金币不足与 Lv.7 封顶拒绝")
    void buyExpCommand() {
        GameData data = shopData();

        RunContext ok = context(data, 4);
        CommandManager okManager = armed(new ShopSystem());
        List<GameCommand> okExecuted = executedTracker(okManager);
        okManager.addCommand(BuyExpCommand.INSTANCE);
        okManager.executeAll(ok);
        assertThat(okExecuted).hasSize(1);
        assertThat(ok.getPlayer().getGold()).isZero();
        assertThat(ok.getPlayer().getLevel()).isEqualTo(2);
        assertThat(ok.getPlayer().getCurrentExp()).isZero();

        RunContext poor = context(data, 3);
        CommandManager poorManager = armed(new ShopSystem());
        List<GameCommand> poorExecuted = executedTracker(poorManager);
        poorManager.addCommand(BuyExpCommand.INSTANCE);
        poorManager.executeAll(poor);
        assertThat(poorExecuted).isEmpty();
        assertThat(poor.getPlayer().getLevel()).isEqualTo(1);
        assertThat(poor.getPlayer().getGold()).isEqualTo(3);

        RunContext capped = context(data, 10);
        capped.getPlayer().addExp(148); // 4+8+16+24+40+56 → Lv.7 封顶
        assertThat(capped.getPlayer().getLevel()).isEqualTo(GameBalance.MAX_PLAYER_LEVEL);
        CommandManager cappedManager = armed(new ShopSystem());
        List<GameCommand> cappedExecuted = executedTracker(cappedManager);
        cappedManager.addCommand(BuyExpCommand.INSTANCE);
        cappedManager.executeAll(capped);
        assertThat(cappedExecuted).isEmpty();
        assertThat(capped.getPlayer().getGold()).isEqualTo(10);
    }
}
