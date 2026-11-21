package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.DeathrattleKind;
import com.voidvvv.kz_auto_chess_n.data.DeathrattleSpec;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.Projectile;

import java.util.ArrayList;
import java.util.List;

/**
 * 个人亡语执行（CP15，载荷全真伤——阵营轴：亡者的攻击无视生者的盔甲）。
 * 双份抚恤 = 同 tick 顺序执行两次（DOUBLE_DEATHRATTLE 机制在<b>死者自身</b>，C5）；
 * 目标候选一律「可行动存活」敌方（含倒地豁免，C7 ③）。
 *
 * <p>DEATH_VOLLEY 弹目标：第 1 发 HIGHEST_ATK；双响第 2 发 LOWEST_HP（发射时各自选取）。
 */
final class DeathrattleExecutor {
    private final DamagePipeline damagePipeline;
    private final StatusSystem statusSystem;

    DeathrattleExecutor(DamagePipeline damagePipeline, StatusSystem statusSystem) {
        this.damagePipeline = damagePipeline;
        this.statusSystem = statusSystem;
    }

    void execute(BattleState state, BattleUnit dead, DeathSystem.DeathRecord record) {
        DeathrattleSpec spec = dead.getTemplate().getDeathrattle();
        if (spec == null) {
            return;
        }
        int times = dead.getMechanic(SynergyMechanic.DOUBLE_DEATHRATTLE) != null ? 2 : 1;
        for (int i = 0; i < times; i++) {
            executeOnce(state, dead, spec, record, i);
        }
    }

    private void executeOnce(BattleState state, BattleUnit dead, DeathrattleSpec spec,
                             DeathSystem.DeathRecord record, int shotIndex) {
        switch (spec.getKind()) {
            case AREA_TRUE_DAMAGE: {
                for (BattleUnit target : enemiesWithin(state, dead, record.corpseX, record.corpseY,
                        spec.getRadius())) {
                    damagePipeline.applyTrueDamage(state, dead, target,
                            record.attackSnapshot * spec.getRatio(), causeId(dead));
                    if (spec.getStatusType() != null) { // SLOW 附带：同 type 取大 = 双响只刷新（已知特性）
                        statusSystem.apply(state, target, spec.getStatusType(),
                                spec.getStatusPower(), spec.getStatusDuration(), dead.getId());
                    }
                }
                break;
            }
            case KILLER_TRUE_DAMAGE: {
                BattleUnit victim = validKiller(state, dead, record);
                if (victim != null) { // 两发均对有效凶手；无有效凶手则每发各自转移（C5）
                    damagePipeline.applyTrueDamage(state, dead, victim,
                            record.attackSnapshot * spec.getRatio(), causeId(dead));
                }
                break;
            }
            case DEATH_VOLLEY: {
                BattleUnit target = shotIndex == 1
                        ? pickLowestHpActableEnemy(state, dead)   // 双响第 2 发按 LOWEST_HP
                        : pickHighestAtkActableEnemy(state, dead); // 第 1 发 HIGHEST_ATK
                spawnVolleyShot(state, dead, record, target, spec);
                break;
            }
            case ALLY_REGEN: {
                for (BattleUnit ally : state.getUnits()) {
                    if (ally.getSide() == dead.getSide() && ally.isActable()) {
                        statusSystem.apply(state, ally, com.voidvvv.kz_auto_chess_n.data.StatusType.REGEN,
                                spec.getRegenRatio(), spec.getDuration(), dead.getId());
                    }
                }
                break;
            }
            default:
                throw new IllegalArgumentException("未知亡语 kind: " + spec.getKind());
        }
    }

    private static String causeId(BattleUnit dead) {
        return "deathrattle_" + dead.getTemplate().getId();
    }

    private void spawnVolleyShot(BattleState state, BattleUnit dead, DeathSystem.DeathRecord record,
                                 BattleUnit target, DeathrattleSpec spec) {
        if (target == null) {
            return;
        }
        state.spawnProjectile(new Projectile(dead.getId(), target.getId(),
                record.corpseX + 0.5f, record.corpseY + 0.5f,
                record.attackSnapshot * spec.getRatio(), false, null, 1f, true)); // deathrattleBorn（CP18 路由）
    }

    /** 坏账目标：凶手可行动存活且敌对 → 凶手；否则尸体格曼哈顿最近可行动存活敌（平局 id 小） */
    private static BattleUnit validKiller(BattleState state, BattleUnit dead,
                                          DeathSystem.DeathRecord record) {
        if (record.killerId >= 0) {
            BattleUnit killer = state.getUnitById(record.killerId);
            if (killer != null && killer.isActable() && killer.getSide() != dead.getSide()) {
                return killer;
            }
        }
        return nearestActableEnemy(state, dead, record.corpseX, record.corpseY);
    }

    // —— 目标选取助手（曼哈顿 + id 升序平局；谓词 = isActable，C7 ③）——

    /** 尸体格为心、曼哈顿 ≤radius 的可行动存活敌方 */
    static List<BattleUnit> enemiesWithin(BattleState state, BattleUnit dead, int cx, int cy, int radius) {
        List<BattleUnit> hits = new ArrayList<BattleUnit>();
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() == dead.getSide() || !unit.isActable()) {
                continue;
            }
            int distance = Math.abs(unit.getGridX() - cx) + Math.abs(unit.getGridY() - cy);
            if (distance <= radius) {
                hits.add(unit);
            }
        }
        return hits;
    }

    /** 参照格曼哈顿最近的可行动存活敌（平局 id 小） */
    static BattleUnit nearestActableEnemy(BattleState state, BattleUnit dead, int cx, int cy) {
        BattleUnit best = null;
        int bestDistance = 0;
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() == dead.getSide() || !unit.isActable()) {
                continue;
            }
            int distance = Math.abs(unit.getGridX() - cx) + Math.abs(unit.getGridY() - cy);
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && unit.getId() < best.getId())) {
                best = unit;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** HIGHEST_ATK：最大有效攻的可行动存活敌（平局 id 小） */
    static BattleUnit pickHighestAtkActableEnemy(BattleState state, BattleUnit dead) {
        BattleUnit best = null;
        float bestAtk = 0f;
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() == dead.getSide() || !unit.isActable()) {
                continue;
            }
            float atk = unit.getEffective(StatKey.ATTACK);
            if (best == null || atk > bestAtk || (atk == bestAtk && unit.getId() < best.getId())) {
                best = unit;
                bestAtk = atk;
            }
        }
        return best;
    }

    /** LOWEST_HP：最小 hpRatio 的可行动存活敌（平局 id 小）——亡语源弹道消散重选共用（CP18） */
    static BattleUnit pickLowestHpActableEnemy(BattleState state, BattleUnit dead) {
        BattleUnit best = null;
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() == dead.getSide() || !unit.isActable()) {
                continue;
            }
            if (best == null || unit.hpRatio() < best.hpRatio()
                    || (unit.hpRatio() == best.hpRatio() && unit.getId() < best.getId())) {
                best = unit;
            }
        }
        return best;
    }
}
