package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 死亡清算（C2 两遍式级联，CP14）：pass1 标记 + 快照（快照先于 markCleaned 清状态——
 * 攻击与尸体格在状态销毁前定格）→ pass2 按 id 序结算（亡语 → 名册 → 点算 → 兽潮 → 清账）。
 * 结算造成的 HP≤0 并入下一轮循环，级联收敛后统一 retarget；循环上界 = 单位数
 * （每轮至少清 1 人，确定性收敛）。undying 首倒在此分流为倒地（C1/C7，非真死）。
 */
public final class DeathSystem {
    private final TargetingSystem targeting;
    private final StatusSystem statusSystem;
    private final DeathrattleExecutor deathrattleExecutor;

    DeathSystem(TargetingSystem targeting, DamagePipeline damagePipeline, StatusSystem statusSystem) {
        this.targeting = targeting;
        this.statusSystem = statusSystem;
        this.deathrattleExecutor = new DeathrattleExecutor(damagePipeline, statusSystem);
    }

    /** 单条死亡记录（pass1 产出，pass2 消费——快照语义：statuses 未清时的攻击与坐标） */
    static final class DeathRecord {
        final int unitId;
        final int corpseX;
        final int corpseY;
        final float attackSnapshot;
        final int killerId;

        DeathRecord(int unitId, int corpseX, int corpseY, float attackSnapshot, int killerId) {
            this.unitId = unitId;
            this.corpseX = corpseX;
            this.corpseY = corpseY;
            this.attackSnapshot = attackSnapshot;
            this.killerId = killerId;
        }
    }

    /** 阶段④：两遍式级联清扫——收敛后才返回（battle §二 H 语义不变） */
    public void sweep(BattleState state) {
        List<Integer> allDeadIds = new ArrayList<Integer>();
        int guard = state.getUnits().size(); // 每轮至少清 1 人，循环上界 = 单位数
        while (guard-- > 0) {
            List<DeathRecord> batch = markBatch(state);      // —— 第一遍：标记 + 快照 ——
            if (batch.isEmpty()) {
                break;
            }
            for (DeathRecord record : batch) {               // —— 第二遍：id 序结算 ——
                settle(state, record);
                allDeadIds.add(record.unitId);
            }
        }
        for (int deadId : allDeadIds) {
            targeting.retargetOnDeath(state, deadId);        // 收敛后统一重选（battle §三）
        }
    }

    /** 第一遍：本 tick 新达 HP≤0 且未清扫未倒地者——快照 → undying 分流 → 清扫标记 + UNIT_DIED(killer) */
    private List<DeathRecord> markBatch(BattleState state) {
        List<DeathRecord> batch = new ArrayList<DeathRecord>();
        for (BattleUnit unit : state.getUnits()) {           // id 升序 = 构造序
            if (unit.isCleaned() || unit.isDowned() || unit.getCurrentHp() > 0f) {
                continue;
            }
            if (UndyingRule.tryEnterDowned(state, unit)) {   // undying 首倒分流（C1，CP17）
                continue;
            }
            DeathRecord record = new DeathRecord(unit.getId(), unit.getGridX(), unit.getGridY(),
                    unit.getEffective(StatKey.ATTACK), unit.getLastDamagerId()); // 快照先于清状态
            unit.markCleaned();
            state.removeFromGrid(unit);
            state.record(CombatEvent.unitDied(state.getTick(), unit.getId(), record.killerId));
            batch.add(record);
        }
        return batch;
    }

    /** 第二遍：单死者结算序（亡语 → 名册 → 点算 → 兽潮 → 清账；C5/C8/C9/C11/C12/C13） */
    private void settle(BattleState state, DeathRecord record) {
        BattleUnit dead = state.getUnitById(record.unitId);
        deathrattleExecutor.execute(state, dead, record);            // CP15：个人亡语（含双响 C5）
        DeathBenefitRule.settleDeathTax(state, dead, record);        // CP16：阵亡名册（C8/C9）
        DeathBenefitRule.settleDeathAudit(state, dead, record);      // CP16：大公点算（C10/C11）
        DeathBenefitRule.settleEnemyDeathSurge(state, dead, statusSystem); // CP19：兽潮（C13）
        DeathBenefitRule.settleKillEnergyRefund(state, record);      // CP19：连环清账（C12）
    }
}
