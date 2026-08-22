package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.ChestOffer;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.IdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 宝箱系统（GDD §3.2/§5.2；Q2 裁决 A 最小可玩规则）。
 *
 * <p>roll 固定消耗 2 RNG：稀有度 1 + 池内均匀抽取 1；金币/经验选项零 RNG（公式确定）。
 * 装备池 = equipments.json 全集按稀有度过滤（GameData 声明序）；池空逐级向低稀有度降级、
 * 全空退化为金币选项（内容缺失防御，加载期软告警预警——CP3）。
 */
public final class ChestSystem {

    /** 胜局进入 RESULT 时 roll 三选项（槽1 金币常驻 / 槽2 经验书 / 槽3 装备） */
    public ChestOffer roll(int round, GameData data, RandomGenerator rng) {
        boolean boss = GameBalance.isBossRound(round);
        int gold = GameBalance.chestGold(round, boss);
        ChestOption equipment = rollEquipment(data, rng,
                boss ? GameBalance.BOSS_CHEST_RARITY_WEIGHTS : GameBalance.CHEST_RARITY_WEIGHTS, gold);
        return new ChestOffer(round, boss, Arrays.asList(
                ChestOption.gold(gold),
                ChestOption.expBook(GameBalance.CHEST_EXP_BOOK_GAIN),
                equipment));
    }

    /** 领取（PickChest handler 调）：装备发号入包，金币/经验入账；返回通知行文案 */
    public String apply(ChestOption option, Player player, IdIssuer idIssuer, GameData data) {
        switch (option.getKind()) {
            case GOLD:
                player.addGold(option.getAmount());
                return "宝箱：金币 +" + option.getAmount();
            case EXP_BOOK:
                player.addExp(option.getAmount());
                return "宝箱：经验 +" + option.getAmount();
            case EQUIPMENT:
            default:
                EquipmentData template = data.getEquipment(option.getEquipmentId());
                player.addToInventory(new Equipment(idIssuer.nextId(), template));
                return "宝箱：获得 " + template.getName();
        }
    }

    private ChestOption rollEquipment(GameData data, RandomGenerator rng,
                                      int[] rarityWeights, int fallbackGold) {
        int rarityIndex = rng.weightedPick(rarityWeights);                                   // RNG #1
        List<EquipmentData> pool = rarityPool(data, EquipmentRarity.values()[rarityIndex]);
        while (pool.isEmpty() && rarityIndex > 0) {
            rarityIndex--;                                                                   // 内容缺失防御：向低稀有度降级
            pool = rarityPool(data, EquipmentRarity.values()[rarityIndex]);
        }
        int pick = rng.weightedPick(uniform(pool.size()));                                   // RNG #2（池空也消耗，保确定性）
        return pool.isEmpty() ? ChestOption.gold(fallbackGold)                               // 全空兜底：退化为金币
                : ChestOption.equipment(pool.get(pick).getId());
    }

    private static List<EquipmentData> rarityPool(GameData data, EquipmentRarity rarity) {
        List<EquipmentData> pool = new ArrayList<EquipmentData>();
        for (EquipmentData template : data.getEquipments().values()) {
            if (template.getRarity() == rarity) {
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
