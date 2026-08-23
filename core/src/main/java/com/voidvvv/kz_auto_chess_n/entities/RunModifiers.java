package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.StatKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 局外修正聚合（Phase 6，GDD §8.1）：英雄被动 + 熟练度等级解锁 + 场景解锁门控的不可变快照。
 * 由 ProfileService.runModifiers 纯函数在装配期产出、冻结进 RunState——局内任何系统只读，
 * 同 seed + 同 heroId + 同命令流 ⇒ 同结果（确定性口径不变）。
 *
 * <p>实现 {@link StatModifierSource}（裁决 D13）：当前唯一修正 = 全队回能 ADD 百分点
 * （「战歌」energyGainRate，结算 ÷100 乘数——data_schema §三刻度约定）；敌方侧不注入。
 */
public final class RunModifiers implements StatModifierSource {

    /** 空修正（无英雄/存量测试路径）：全部零增益、不门控商店池 */
    public static final RunModifiers EMPTY = new RunModifiers(0, 0, 0, 0,
            Collections.<String, Float>emptyMap(), null, Collections.<String>emptySet(), false);

    private final int startGoldBonus;
    private final int refreshCostDiscount;
    /** 商店 3 费概率加成（百分点；仅基础 3 费概率 > 0 的轮次生效——裁决 D5） */
    private final int rareShopBonusPp;
    /** 全队回能加成（百分点，energyGainRate ADD） */
    private final int energyGainRateBonus;
    /** 羁绊增幅：synergyId → 比例（0.25 = +25%，「荆语」——裁决 D12） */
    private final Map<String, Float> synergyAmp;
    /** 本英雄专属传奇棋子 id（熟练度 Lv.3 起；可空） */
    private final String legendaryUnitId;
    /** 受门控时的可购单位 id 集 */
    private final Set<String> shopPoolUnitIds;
    /** 是否门控商店池（false = 全量非 Boss 池，兼容路径） */
    private final boolean shopPoolRestricted;

    public RunModifiers(int startGoldBonus, int refreshCostDiscount, int rareShopBonusPp,
                        int energyGainRateBonus, Map<String, Float> synergyAmp,
                        String legendaryUnitId, Set<String> shopPoolUnitIds,
                        boolean shopPoolRestricted) {
        this.startGoldBonus = Math.max(0, startGoldBonus);
        this.refreshCostDiscount = Math.max(0, refreshCostDiscount);
        this.rareShopBonusPp = Math.max(0, rareShopBonusPp);
        this.energyGainRateBonus = Math.max(0, energyGainRateBonus);
        this.synergyAmp = Collections.unmodifiableMap(
                new LinkedHashMap<String, Float>(synergyAmp));
        this.legendaryUnitId = legendaryUnitId;
        this.shopPoolUnitIds = Collections.unmodifiableSet(
                new LinkedHashSet<String>(shopPoolUnitIds));
        this.shopPoolRestricted = shopPoolRestricted;
    }

    public int getStartGoldBonus() { return startGoldBonus; }
    public int getRefreshCostDiscount() { return refreshCostDiscount; }
    public int getRareShopBonusPp() { return rareShopBonusPp; }
    public int getEnergyGainRateBonus() { return energyGainRateBonus; }
    public Map<String, Float> getSynergyAmp() { return synergyAmp; }
    public String getLegendaryUnitId() { return legendaryUnitId; }
    public boolean isShopPoolRestricted() { return shopPoolRestricted; }

    /** 单位是否可购（门控 = 场景 shopUnlocks 已解锁 + 本英雄传奇 Lv.3；未门控恒 true） */
    public boolean isShopAllowed(String unitId) {
        return !shopPoolRestricted || shopPoolUnitIds.contains(unitId);
    }

    @Override
    public StatModifierBlock modifiers() {
        return energyGainRateBonus == 0
                ? StatModifierBlock.empty()
                : StatModifierBlock.of(StatKey.ENERGY_GAIN_RATE, EffectOp.ADD, energyGainRateBonus);
    }
}
