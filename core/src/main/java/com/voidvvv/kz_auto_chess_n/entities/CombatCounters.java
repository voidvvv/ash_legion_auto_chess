package com.voidvvv.kz_auto_chess_n.entities;

/**
 * 战斗内计数器（v5 §4.1 计数通道，K2）：遗物（名册攻/血 ADD）与旧疤（层数）并入
 * StatPipeline 第二级有效值——先加后乘，与状态修正同序；不走 StatusType（同 type 取大）、
 * 不走 stat 修正源（开战快照）。旧疤每层幅度走 GameBalance 常量，次数/封顶在机制 value/value2。
 *
 * <p>framework-internal 写方法仅 systems 包在战斗作用域内调用；计数器变更后调用方需
 * {@link BattleUnit#invalidateStats()} 打脏。战斗实例级生命周期（新一场自动归零）。
 */
public final class CombatCounters {
    private float relicAtkAdd;
    private float relicHpAdd;
    private int scarStacks;
    private int basicAttackCount;   // 三连装填计数（CP27 消费）
    private int basicHitTakenCount; // 旧疤被击计数（CP21：每 SCAR.value 次进一层）

    public float getRelicAtkAdd() { return relicAtkAdd; }
    public float getRelicHpAdd() { return relicHpAdd; }
    public int getScarStacks() { return scarStacks; }
    public int getBasicAttackCount() { return basicAttackCount; }
    public int getBasicHitTakenCount() { return basicHitTakenCount; }

    /** framework-internal：名册遗物入账（攻 ADD / 血 ADD） */
    public void addRelic(float atkAdd, float hpAdd) {
        relicAtkAdd += atkAdd;
        relicHpAdd += hpAdd;
    }

    /** framework-internal：旧疤 +1 层（封顶由调用方按机制 value2 判定） */
    public void addScarStack() {
        scarStacks++;
    }

    /** framework-internal：普攻出手 +1（三连装填） */
    public void addBasicAttack() {
        basicAttackCount++;
    }

    /** framework-internal：被普攻直伤命中 +1（旧疤进层计数，CP21） */
    public void addBasicHitTaken() {
        basicHitTakenCount++;
    }

    /** framework-internal：进层后清零重新累计（CP21） */
    public void resetBasicHitTakenCount() {
        basicHitTakenCount = 0;
    }
}
