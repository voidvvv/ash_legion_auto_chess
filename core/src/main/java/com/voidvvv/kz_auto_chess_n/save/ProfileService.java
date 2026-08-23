package com.voidvvv.kz_auto_chess_n.save;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.HeroData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 档案域纯函数服务（architecture §三：解锁判定/熟练度结算必须纯函数，禁止写进 ClickListener）。
 * 零 Gdx、零副作用；入参 Profile 不可变，产出新 Profile / 值对象。
 */
public final class ProfileService {

    private ProfileService() {
    }

    /** 当前熟练度等级（未记录英雄 = Lv.1 起步） */
    public static int masteryLevel(Profile profile, String heroId) {
        Objects.requireNonNull(profile, "profile 不能为 null");
        HeroProgress progress = profile.getHeroProgress().get(heroId);
        return progress == null ? 1 : progress.getLevel();
    }

    /** 已解锁场景 id 集：unlockAfter == null 或前置场景已通关（GDD §8.2；派生不落档——裁决 D7） */
    public static Set<String> unlockedSceneIds(Profile profile, GameData data) {
        Objects.requireNonNull(profile, "profile 不能为 null");
        Objects.requireNonNull(data, "data 不能为 null");
        Set<String> unlocked = new LinkedHashSet<String>();
        for (SceneData scene : data.getScenes().values()) {
            if (scene.getUnlockAfter() == null
                    || profile.getCompletedScenes().contains(scene.getUnlockAfter())) {
                unlocked.add(scene.getId());
            }
        }
        return Collections.unmodifiableSet(unlocked);
    }

    /**
     * 局末结算（GDD §8.1/§8.2）：熟练度经验入账（连续升级，Lv.5 封顶余量作废——沿
     * Player.addExp 先例）+ 通关场景登记 + 新解锁场景对比产出。
     *
     * @param awardedExp MasteryCalculator 产出的本局熟练度（负值防御钳 0）
     */
    public static Settlement settle(Profile profile, GameData data, String heroId, String sceneId,
                                    RunEndCause cause, int roundsReached, int awardedExp) {
        Objects.requireNonNull(profile, "profile 不能为 null");
        Objects.requireNonNull(data, "data 不能为 null");
        Objects.requireNonNull(cause, "cause 不能为 null");
        int gained = Math.max(0, awardedExp);
        int levelFrom = 1;
        int exp = gained;
        HeroProgress old = heroId == null ? null : profile.getHeroProgress().get(heroId);
        if (old != null) {
            levelFrom = old.getLevel();
            exp = old.getExp() + gained;
        }
        int level = levelFrom;
        while (level < GameBalance.MASTERY_MAX_LEVEL
                && exp >= GameBalance.masteryExpToNext(level)) {
            exp -= GameBalance.masteryExpToNext(level);
            level++;
        }
        if (level >= GameBalance.MASTERY_MAX_LEVEL) {
            exp = 0; // 封顶余量作废（沿 Player.addExp 先例）
        }
        Profile next = profile;
        if (heroId != null) {
            next = next.withHeroProgress(heroId, new HeroProgress(level, exp));
        }
        if (cause == RunEndCause.COMPLETED && sceneId != null) {
            next = next.withCompletedScene(sceneId);
        }
        Set<String> unlockedBefore = unlockedSceneIds(profile, data);
        Set<String> unlockedAfter = unlockedSceneIds(next, data);
        List<String> newlyUnlocked = new ArrayList<String>(unlockedAfter);
        newlyUnlocked.removeAll(unlockedBefore);
        int expToNext = level >= GameBalance.MASTERY_MAX_LEVEL
                ? 0 : GameBalance.masteryExpToNext(level);
        return new Settlement(next, gained, levelFrom, level, exp, expToNext,
                Collections.unmodifiableList(newlyUnlocked));
    }

    /**
     * 局外修正聚合（装配期一次）：英雄被动 + 熟练度等级解锁表（GDD §8.1）+ 场景门控商店池。
     * hero 可空（防御路径：无英雄局——旧测试/异常装配）→ 仅场景门控 + Lv.1 基础权益。
     */
    public static RunModifiers runModifiers(HeroData hero, Profile profile, GameData data) {
        Objects.requireNonNull(profile, "profile 不能为 null");
        Objects.requireNonNull(data, "data 不能为 null");
        int level = hero == null ? 1 : masteryLevel(profile, hero.getId());

        int startGoldBonus = GameBalance.MASTERY_LV1_START_GOLD_BONUS; // Lv.1 全英雄基础权益（裁决 D2）
        int rarePp = 0;
        int refreshDiscount = 0;
        if (level >= 2) {
            rarePp = GameBalance.MASTERY_LV2_RARE_SHOP_BONUS_PP;
        }
        if (level >= 4) {
            startGoldBonus += GameBalance.MASTERY_LV4_START_GOLD_BONUS;
        }
        if (level >= 5) {
            refreshDiscount = GameBalance.MASTERY_LV5_REFRESH_DISCOUNT;
        }

        int energyPp = 0;
        Map<String, Float> amp = new LinkedHashMap<String, Float>();
        if (hero != null) {
            switch (hero.getPassiveType()) {
                case START_GOLD:
                    startGoldBonus += Math.round(hero.getPassiveValue());
                    break;
                case SYNERGY_AMP:
                    for (String synergyId : hero.getPassiveSynergyIds()) {
                        amp.put(synergyId, hero.getPassiveValue() / 100f);
                    }
                    break;
                case ENERGY_GAIN:
                    energyPp = Math.round(hero.getPassiveValue());
                    break;
                default:
                    throw new IllegalStateException("未知英雄被动类型: " + hero.getPassiveType());
            }
        }

        String legendary = level >= 3 && hero != null ? hero.getLegendaryUnitId() : null;
        return new RunModifiers(startGoldBonus, refreshDiscount, rarePp, energyPp, amp, legendary,
                shopPool(profile, data, legendary), true);
    }

    /** 可购单位池（裁决 D8）：非 Boss；场景门控单位需场景已解锁；他人传奇不可见、本英雄传奇 Lv.3 起可见 */
    private static Set<String> shopPool(Profile profile, GameData data, String ownLegendary) {
        Set<String> unlocked = unlockedSceneIds(profile, data);
        Set<String> legendaryAll = new HashSet<String>();
        for (HeroData heroEntry : data.getHeroes().values()) {
            if (heroEntry.getLegendaryUnitId() != null) {
                legendaryAll.add(heroEntry.getLegendaryUnitId());
            }
        }
        Set<String> pool = new LinkedHashSet<String>();
        for (UnitData unit : data.getUnits().values()) {
            if (unit.isBoss()) {
                continue;
            }
            String id = unit.getId();
            boolean sceneOk = true;
            for (SceneData scene : data.getScenes().values()) {
                if (scene.getShopUnlocks().contains(id) && !unlocked.contains(scene.getId())) {
                    sceneOk = false;
                    break;
                }
            }
            boolean legendaryOk = !legendaryAll.contains(id) || id.equals(ownLegendary);
            if (sceneOk && legendaryOk) {
                pool.add(id);
            }
        }
        return pool;
    }

    /** 局末结算产物（不可变；展示文案由 RunSettlementText 生成） */
    public static final class Settlement {
        private final Profile newProfile;
        private final int expGained;
        private final int levelFrom;
        private final int levelTo;
        private final int expIntoLevel;
        private final int expToNextLevel;
        private final List<String> newlyUnlockedSceneIds;

        Settlement(Profile newProfile, int expGained, int levelFrom, int levelTo,
                   int expIntoLevel, int expToNextLevel, List<String> newlyUnlockedSceneIds) {
            this.newProfile = newProfile;
            this.expGained = expGained;
            this.levelFrom = levelFrom;
            this.levelTo = levelTo;
            this.expIntoLevel = expIntoLevel;
            this.expToNextLevel = expToNextLevel;
            this.newlyUnlockedSceneIds = newlyUnlockedSceneIds;
        }

        public Profile getNewProfile() { return newProfile; }
        public int getExpGained() { return expGained; }
        public int getLevelFrom() { return levelFrom; }
        public int getLevelTo() { return levelTo; }
        public int getExpIntoLevel() { return expIntoLevel; }
        public int getExpToNextLevel() { return expToNextLevel; }
        public List<String> getNewlyUnlockedSceneIds() { return newlyUnlockedSceneIds; }
    }
}
