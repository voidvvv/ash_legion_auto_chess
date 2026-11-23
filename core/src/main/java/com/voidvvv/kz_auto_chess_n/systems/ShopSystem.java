package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.BuyExpCommand;
import com.voidvvv.kz_auto_chess_n.command.BuyUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.RefreshShopCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 商店系统（GDD §3.4；Q6 裁决：起始 10 金商店自购，演示名单移除）。
 *
 * <p>槽位状态：买走即置 null（刷新前不回填）。整批重掷 = 轮首免费（系统行为，RunFlowSystem 调）
 * 与 RefreshShop（2 金）共用 {@link #reroll}——每槽固定消耗 2 RNG（费阶 1 + 池内抽取 1），
 * 池为空也照常消耗（实现口径 #4：消耗序与内容无关，保确定性）。
 * 3 合 1（GDD §4.3）是 BuyUnit 的系统后果：合成产物保留首位参与者位置与装备，
 * 被吞并者装备回背包、spend 折叠加总，级联直到无可合（实现口径 #5）。
 *
 * <p>席满例外（GDD §3.4"席满禁买，例外 = 购买即完成 3 合 1"）：备战席满 9 且已有两位同名一星时
 * 放行购买，但买到的棋子<b>不占席位</b>、直接并入合成——{@code Player.addToBench} 的满席防御
 * （CP6）不放行绕路入席，故此路径在 {@link #mergeIntoFullBench} 内完成折叠。
 */
public final class ShopSystem {

    private final UnitData[] slots = new UnitData[GameBalance.SHOP_SLOTS];
    /** 对拍付费序号（品质改造 §0.2，0-based：0 = ①）：新轮免费补货归零、成功付费 +1；
     *  正式分支不消费。随上下文生命周期重建（重开即新商店——同轮重试自然保留）。 */
    private int paidRefreshIndex;
    /** 本帧升星星级（品质改造 §1.2：音频消费者；级联合成同帧只播一次、保留最高星级——drain 后清空） */
    private final java.util.List<Integer> frameUpgrades = new java.util.ArrayList<Integer>();

    /** 注册经营命令 handler（input §6.1：handler 由所属 system 注册） */
    public void registerHandlers(CommandManager manager) {
        manager.registerHandler(BuyUnitCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING) {
                return false;
            }
            return buy(ctx, ((BuyUnitCommand) cmd).getSlotIndex());
        });
        manager.registerHandler(RefreshShopCommand.class, (cmd, ctx) -> {
            int cost = refreshCost(ctx.getRunState().getModifiers());
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING
                    || !ctx.getPlayer().canAfford(cost)) {
                return false;
            }
            ctx.getPlayer().addGold(-cost);
            replenish(ctx, true); // 仅成功支付才推进对拍付费序号（§0.2）
            return true;
        });
        manager.registerHandler(BuyExpCommand.class, (cmd, ctx) -> {
            Player player = ctx.getPlayer();
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING
                    || player.getLevel() >= GameBalance.MAX_PLAYER_LEVEL
                    || !player.canAfford(GameBalance.BUY_EXP_COST)) {
                return false;
            }
            player.addGold(-GameBalance.BUY_EXP_COST);
            player.addExp(GameBalance.BUY_EXP_GAIN);
            return true;
        });
    }

    /**
     * 补货分派单点（品质改造 §0.2）：备战期商店补货的唯一入口，免费（新轮进入）与付费
     * （RefreshShop）共用——正式分支仍调原 {@link #reroll}（费阶概率 + 池门控，每槽 2 RNG）；
     * 对拍分支走 {@link PaiTestScript} 固定名单复用 {@link #restoreSlots}（0 RNG）：
     * 免费写新轮固定五槽并把付费序号归零，付费按共用序 ①②③ 循环（第四次回 ①）。
     *
     * <p>序号纪律（§0.2）：付费序号仅在扣款成功后推进（handler 守卫在前）；同轮重试不调用本方法，
     * 已买空槽与序号原样保留。
     *
     * @param paidRefresh false = 新轮免费补货；true = 付费刷新（已扣款）
     */
    public void replenish(RunContext ctx, boolean paidRefresh) {
        if (!ctx.getMode().isSampleDuel()) {
            reroll(ctx.getRunState().getRound(), ctx.getGameData(), ctx.getRng(),
                    ctx.getRunState().getModifiers()); // 正式口径不变（含启动参数起始金外的随机页）
            return;
        }
        if (paidRefresh) {
            restoreSlots(PaiTestScript.paidSlots(paidRefreshIndex, ctx.getGameData())); // 第 1 次付费 = ①
            paidRefreshIndex = (paidRefreshIndex + 1) % PaiTestScript.PAID_CYCLE_LENGTH; // ④ 回 ①（§0.2）
            return;
        }
        paidRefreshIndex = 0; // 每次进入新轮：先写免费五槽，并把付费序号归零（§0.2）
        restoreSlots(PaiTestScript.freeSlots(ctx.getRunState().getRound(), ctx.getGameData()));
    }

    /** 对拍付费序号（0-based，0 = ①；品质改造 §0.2 验收件直读） */
    public int getPaidRefreshIndex() {
        return paidRefreshIndex;
    }

    /** 槽位模板；空槽/越界返回 null */
    public UnitData slotAt(int index) {
        if (index < 0 || index >= slots.length) {
            return null;
        }
        return slots[index];
    }

    /** 槽位快照（不可变；空槽为 null 元素） */
    public List<UnitData> getSlots() {
        return Collections.unmodifiableList(Arrays.asList(slots));
    }

    /** 槽位复原（快照轨恢复唯一写入口；长度必须 = SHOP_SLOTS，null 槽原样保留） */
    public void restoreSlots(List<UnitData> templates) {
        Objects.requireNonNull(templates, "templates 不能为 null");
        if (templates.size() != slots.length) {
            throw new IllegalArgumentException("商店槽位数必须 = " + slots.length + "，实际=" + templates.size());
        }
        for (int i = 0; i < slots.length; i++) {
            slots[i] = templates.get(i);
        }
    }

    /** 整批重掷 5 槽（存量签名：无局外修正——测试路径） */
    public void reroll(int round, GameData data, RandomGenerator rng) {
        reroll(round, data, rng, RunModifiers.EMPTY);
    }

    /**
     * 整批重掷（带局外修正，Phase 6）：Lv.2 起 3 费概率 +5pp——仅当该轮基础 3 费概率 > 0
     * （防 1~9 轮提前出 3 费打破新手期节奏，GDD §3.4——裁决 D5），自 1 费扣减；
     * 池内抽取按 RunModifiers 商店池门控（场景 shopUnlocks + 本英雄传奇，裁决 D8）。
     * RNG 消耗序与点数不变（权重是数值调整非新掷——architecture §六）。
     */
    public void reroll(int round, GameData data, RandomGenerator rng, RunModifiers modifiers) {
        float[] probabilities = GameBalance.shopTierProbabilities(round);
        int bonusPp = Math.max(0, modifiers.getRareShopBonusPp());
        if (bonusPp > 0 && probabilities[2] > 0f) {
            probabilities[2] = Math.min(100f, probabilities[2] + bonusPp);
            probabilities[0] = Math.max(0f, probabilities[0] - bonusPp);
        }
        int[] tierWeights = {
                Math.round(probabilities[0] * GameBalance.PROBABILITY_WEIGHT_SCALE),
                Math.round(probabilities[1] * GameBalance.PROBABILITY_WEIGHT_SCALE),
                Math.round(probabilities[2] * GameBalance.PROBABILITY_WEIGHT_SCALE)};
        for (int i = 0; i < slots.length; i++) {
            int tier = rng.weightedPick(tierWeights);                          // RNG #1（费阶 0/1/2 → cost 1/2/3）
            List<UnitData> pool = allowedPool(tierPool(data, tier + 1), modifiers);
            int pick = rng.weightedPick(uniform(pool.size()));                 // RNG #2（池空也消耗）
            slots[i] = pool.isEmpty() ? null : pool.get(pick);
        }
    }

    /** 刷新实付价（GDD §3.4 基价 2 - Lv.5 折扣，下限 1 金——裁决 D4） */
    static int refreshCost(RunModifiers modifiers) {
        return Math.max(1, GameBalance.SHOP_REFRESH_COST - modifiers.getRefreshCostDiscount());
    }

    /** 商店池门控过滤（EMPTY 不门控——兼容路径全量非 Boss 池） */
    private static List<UnitData> allowedPool(List<UnitData> pool, RunModifiers modifiers) {
        if (!modifiers.isShopPoolRestricted()) {
            return pool;
        }
        List<UnitData> allowed = new ArrayList<UnitData>(pool.size());
        for (UnitData template : pool) {
            if (modifiers.isShopAllowed(template.getId())) {
                allowed.add(template);
            }
        }
        return allowed;
    }

    /** 购买（architecture §5.2 校验要点）：查价不信任载荷；席满禁买，例外 = 购买即完成 3 合 1 */
    boolean buy(RunContext ctx, int slotIndex) {
        Player player = ctx.getPlayer();
        UnitData template = slotAt(slotIndex);
        if (template == null || !player.canAfford(template.getCost())) {
            return false;
        }
        boolean benchFull = player.getBench().size() >= GameBalance.BENCH_SIZE;
        boolean mergeReady = countSameTemplateStar(player, template.getId(), 1) >= 2;
        if (benchFull && !mergeReady) {
            return false; // 备战席已满且不会立即合成（UI 预校验灰置 + 提示，input §4.3）
        }
        player.addGold(-template.getCost());
        slots[slotIndex] = null;
        Unit bought = new Unit(ctx.getRunState().getIdIssuer().nextId(), template, 1, template.getCost());
        ctx.getRunState().addNotice("购入 " + template.getName() + "（-" + template.getCost() + " 金）");
        if (benchFull) {
            mergeIntoFullBench(ctx, bought); // 席满例外：不占席，直接并入合成
        } else {
            player.addToBench(bought);
            mergeCascade(ctx);
        }
        return true;
    }

    /**
     * 席满例外路径：既有两位同名一星 + 本次购买直接三合一（买到的棋子不入席，
     * 首位保留者升星并折叠 spend，第二位被吞并、装备回背包），随后照常级联重扫。
     */
    private void mergeIntoFullBench(RunContext ctx, Unit bought) {
        Player player = ctx.getPlayer();
        Unit survivor = null;
        Unit consumed = null;
        for (Unit unit : rosterInRemovalOrder(player)) {
            if (unit.getTemplate().getId().equals(bought.getTemplate().getId()) && unit.getStar() == 1) {
                if (survivor == null) {
                    survivor = unit;
                } else {
                    consumed = unit;
                    break;
                }
            }
        }
        if (survivor == null || consumed == null) {
            // buy 的 mergeReady 前置校验保证两位同名一星存在（不变量被破坏 = 调用方装配错误，fail-fast）
            throw new IllegalStateException(
                    "席满合成例外要求恰有两位同名一星在名单，实际缺失: " + bought.getTemplate().getId());
        }
        EquipmentSystem.unequipAll(consumed, player);
        player.removeUnit(consumed);
        survivor.addSpend(consumed.getSpend() + bought.getSpend());
        registerUpgrade(ctx, survivor); // 升星收口单点（§1.2）
        mergeCascade(ctx);
    }

    /** 3 合 1 级联合成（GDD §4.3：买到第三个立即触发；可级联 2→3 星） */
    private void mergeCascade(RunContext ctx) {
        boolean mergedAny = true;
        while (mergedAny) {
            mergedAny = false;
            for (Unit survivor : rosterInRemovalOrder(ctx.getPlayer())) {
                List<Unit> group = new ArrayList<Unit>();
                for (Unit candidate : rosterInRemovalOrder(ctx.getPlayer())) {
                    if (candidate.getTemplate().getId().equals(survivor.getTemplate().getId())
                            && candidate.getStar() == survivor.getStar()) {
                        group.add(candidate);
                    }
                }
                if (group.size() >= 3 && survivor.getStar() < 3) {
                    int foldedSpend = 0;
                    for (int i = 1; i < 3; i++) {
                        Unit consumed = group.get(i);
                        foldedSpend += consumed.getSpend();
                        EquipmentSystem.unequipAll(consumed, ctx.getPlayer());
                        ctx.getPlayer().removeUnit(consumed);
                    }
                    survivor.addSpend(foldedSpend); // 首位保留位置与装备（实现口径 #5）
                    registerUpgrade(ctx, survivor); // 升星收口单点（§1.2）
                    mergedAny = true;
                    break; // 重扫（级联 + 名单已变）
                }
            }
        }
    }

    /** 名单移除序（确定性）：备战席入席序优先，其次部署扫描序 y↑x↑ */
    private static List<Unit> rosterInRemovalOrder(Player player) {
        List<Unit> roster = new ArrayList<Unit>(player.getBench());
        roster.addAll(player.getDeployedUnits());
        return roster;
    }

    private static int countSameTemplateStar(Player player, String templateId, int star) {
        int count = 0;
        for (Unit unit : rosterInRemovalOrder(player)) {
            if (unit.getTemplate().getId().equals(templateId) && unit.getStar() == star) {
                count++;
            }
        }
        return count;
    }

    /**
     * 升星收口单点（品质改造 §1.2）：mergeIntoFullBench / mergeCascade 两路共用——写一次带单位
     * 与新星级的文本通知 + 记录本帧升星（音频消费者与文本通知各有消费者）。级联合成同帧只播一次、
     * 保留最高星级（音频侧按帧归并）。
     */
    private void registerUpgrade(RunContext ctx, Unit survivor) {
        survivor.upgradeStar();
        ctx.getRunState().addNotice(
                survivor.getTemplate().getName() + " 升至 " + survivor.getStar() + " 星");
        frameUpgrades.add(survivor.getStar());
    }

    /** 本帧升星星级（品质改造 §1.2；取最高——级联合成同帧只播一次；drain 后清空） */
    public int drainFrameUpgradeStar() {
        if (frameUpgrades.isEmpty()) {
            return -1;
        }
        int highest = 0;
        for (Integer star : frameUpgrades) {
            highest = Math.max(highest, star);
        }
        frameUpgrades.clear();
        return highest;
    }

    /** 同费池（非 Boss、cost 匹配；GameData 声明序——LinkedHashMap 确定性） */
    private static List<UnitData> tierPool(GameData data, int cost) {
        List<UnitData> pool = new ArrayList<UnitData>();
        for (UnitData template : data.getUnits().values()) {
            if (!template.isBoss() && template.getCost() == cost) {
                pool.add(template);
            }
        }
        return pool;
    }

    private static int[] uniform(int size) {
        int[] weights = new int[Math.max(1, size)];
        Arrays.fill(weights, 1);
        return weights;
    }
}
