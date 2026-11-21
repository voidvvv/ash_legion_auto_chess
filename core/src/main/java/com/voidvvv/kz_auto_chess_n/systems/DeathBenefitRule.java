package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.Side;

/**
 * 死亡收益结算（DeathSystem 第二遍逐死者调用，CP16/CP19）：
 * 阵亡名册（C8/C9）、大公点算（C10/C11）、兽潮（C13）、连环清账（C12）。
 * 放逐不走本路径（Banish 无 DeathSystem 足迹）——「真死才触发」由结构保证。
 */
final class DeathBenefitRule {
    private DeathBenefitRule() {
    }

    /** 阵亡名册：距尸体格最近的可行动存活 trait 友方获遗物（攻 ADD + 血上限 ADD 并立即回复 value2） */
    static void settleDeathTax(BattleState state, BattleUnit dead, DeathSystem.DeathRecord record) {
        EffectData tax = mechanicOnSide(state, dead.getSide(), SynergyMechanic.DEATH_TAX);
        if (tax == null) {
            return;
        }
        BattleUnit heir = nearestTraitActableAlly(state, dead, tax.getTrait(),
                record.corpseX, record.corpseY);
        if (heir == null) {
            return; // 无可行动存活继承者 → 作废（C8，含死骑倒地窗口边界）
        }
        heir.getCombatCounters().addRelic(tax.getValue(), tax.getValue2());
        heir.invalidateStats();
        heir.modifyHp(tax.getValue2()); // 立即回复（钳制到新上限——先打脏后回血）
    }

    /** 大公点算：放逐敌方生命比例最低且 <阈值% 者（C10/C11；D3：Boss 亦可被移除；无合格不触发不缓存） */
    static void settleDeathAudit(BattleState state, BattleUnit dead, DeathSystem.DeathRecord record) {
        EffectData audit = mechanicOnSide(state, dead.getSide(), SynergyMechanic.DEATH_AUDIT);
        if (audit == null) {
            return;
        }
        BattleUnit best = null;
        for (BattleUnit enemy : state.getUnits()) {
            if (enemy.getSide() == dead.getSide() || !enemy.isActable()) {
                continue;
            }
            if (enemy.hpRatio() >= audit.getValue() / 100f) {
                continue; // 严格小于阈值才合格（=25% 不触发）
            }
            if (best == null || enemy.hpRatio() < best.hpRatio()
                    || (enemy.hpRatio() == best.hpRatio() && enemy.getId() < best.getId())) {
                best = enemy;
            }
        }
        if (best != null) {
            Banish.execute(state, best); // 独立移除路径（C10）：非死亡事件
        }
    }

    /** 兽潮（野兽6，C13）：敌方真死 → 对侧含机制单位 ASPD_UP（value% / duration s，同 type 取大 = 刷新制） */
    static void settleEnemyDeathSurge(BattleState state, BattleUnit dead, StatusSystem statusSystem) {
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() == dead.getSide() || !unit.isActable()) {
                continue;
            }
            EffectData surge = unit.getMechanic(SynergyMechanic.ENEMY_DEATH_SURGE);
            if (surge == null) {
                continue;
            }
            statusSystem.apply(state, unit, StatusType.ASPD_UP, surge.getValue(),
                    surge.getDuration() == null ? 0f : surge.getDuration(), unit.getId());
        }
    }

    /** 连环清账（刺客6，C12）：真死归因凶手含机制且可行动存活 → 能量回满（K11：下一行动 tick 施放） */
    static void settleKillEnergyRefund(BattleState state, DeathSystem.DeathRecord record) {
        if (record.killerId < 0) {
            return;
        }
        BattleUnit killer = state.getUnitById(record.killerId);
        if (killer == null || !killer.isActable()
                || killer.getMechanic(SynergyMechanic.KILL_ENERGY_REFUND) == null) {
            return;
        }
        killer.setEnergy(GameBalance.ENERGY_MAX);
    }

    // —— 助手 ——

    /** 同侧任一存活单位（含倒地）的机制快照取词——同侧同档一致（K1 下发保证） */
    private static EffectData mechanicOnSide(BattleState state, Side side, SynergyMechanic kind) {
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() == side && unit.isAlive()
                    && unit.getMechanic(kind) != null) {
                return unit.getMechanic(kind);
            }
        }
        return null;
    }

    /** 距尸体格最近的可行动存活 trait 友方（race 或 class 命中；死者已清扫天然出局，C9） */
    private static BattleUnit nearestTraitActableAlly(BattleState state, BattleUnit dead,
                                                      String trait, int cx, int cy) {
        if (trait == null) {
            return null;
        }
        BattleUnit best = null;
        int bestDistance = 0;
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() != dead.getSide() || !unit.isActable()) {
                continue;
            }
            String race = unit.getTemplate().getRace();
            String unitClass = unit.getTemplate().getUnitClass();
            if (!trait.equals(race) && !trait.equals(unitClass)) {
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
}
