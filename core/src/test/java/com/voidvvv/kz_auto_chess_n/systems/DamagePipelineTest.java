package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.BattleStats;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.state;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.stats;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.tpl;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 唯一伤害管线测试（battle §5.2 + Q3 吸血裁决 + 口径 #5/#6/#7/#9）：
 * 护甲公式 → 先扣盾 → 扣 HP → Hit 事件 → 攻守回能 → 普攻吸血。
 */
class DamagePipelineTest {

    /** 记录型就地施放触发器 */
    private static final class RecordingTrigger implements CastTrigger {
        final List<BattleUnit> casted = new ArrayList<BattleUnit>();

        @Override
        public void tryCast(BattleState state, BattleUnit caster) {
            casted.add(caster);
        }
    }

    private static DamagePipeline pipeline(RecordingTrigger trigger) {
        return new DamagePipeline(trigger);
    }

    private static BattleUnit attacker() {
        return unit(1, Side.PLAYER, tpl("a"), 2, 4);
    }

    private static BattleUnit target() {
        return unit(2, Side.ENEMY, tpl("t"), 2, 2);
    }

    @Test
    @DisplayName("护甲公式：0 甲直通（10）；100 甲减半（10×100/200=5）")
    void armorFormula() {
        BattleUnit noArmor = new BattleUnit(3, tpl("n"), 1, Side.ENEMY, melee(), stats(100, 10, 0f, 1f, 1f, 1f));
        noArmor.setPosition(1, 2);
        BattleUnit heavyArmor = new BattleUnit(4, tpl("h"), 1, Side.ENEMY, melee(), stats(100, 10, 100f, 1f, 1f, 1f));
        heavyArmor.setPosition(3, 2);
        BattleUnit atk = attacker();
        BattleState state = state(atk, noArmor, heavyArmor);
        DamagePipeline pipeline = pipeline(new RecordingTrigger());

        pipeline.applyDirectHit(state, atk, noArmor, 10f, 1f, false, true, null);
        assertThat(noArmor.getCurrentHp()).isCloseTo(90f, within(1e-6f));

        pipeline.applyDirectHit(state, atk, heavyArmor, 10f, 1f, false, true, null);
        assertThat(heavyArmor.getCurrentHp()).isCloseTo(95f, within(1e-6f));
    }

    @Test
    @DisplayName("暴击 ×1.5 与倍率乘序：atk10 × 倍率2 × 暴击1.5（0 甲）= 30")
    void critAndMultiplierOrder() {
        BattleUnit atk = attacker();
        BattleUnit zeroArmor = new BattleUnit(5, tpl("z"), 1, Side.ENEMY, melee(), stats(100, 10, 0f, 1f, 1f, 1f));
        zeroArmor.setPosition(2, 2);
        BattleState state = state(atk, zeroArmor);
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, zeroArmor, 10f, 2f, true, true, null);
        assertThat(zeroArmor.getCurrentHp()).isCloseTo(70f, within(1e-6f));
    }

    @Test
    @DisplayName("先扣盾 → 盾尽透血：盾 30 吃 50 伤（armor5 折减 47.62）→ HP 只扣 17.62 且盾移除")
    void shieldAbsorbsBeforeHp() {
        BattleUnit atk = attacker();
        BattleUnit target = target();
        target.addStatus(new ActiveStatus(StatusType.SHIELD, -1, 30f, Float.POSITIVE_INFINITY));
        BattleState state = state(atk, target);
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 50f, 1f, false, true, null);
        assertThat(target.getCurrentHp()).isCloseTo(100f - (50f * 100f / 105f - 30f), within(1e-4f));
        assertThat(target.getStatuses()).isEmpty(); // 盾耗尽移除
    }

    @Test
    @DisplayName("盾未耗尽：盾 30 吃 20 伤（armor5 折减后 19.05）→ HP 不动、盾余差额")
    void shieldPartialAbsorb() {
        BattleUnit atk = attacker();
        BattleUnit target = target();
        target.addStatus(new ActiveStatus(StatusType.SHIELD, -1, 30f, Float.POSITIVE_INFINITY));
        BattleState state = state(atk, target);
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 20f, 1f, false, true, null);
        assertThat(target.getCurrentHp()).isEqualTo(100f);
        assertThat(target.getStatuses()).hasSize(1);
        assertThat(target.getStatuses().get(0).getPower()).isCloseTo(30f - 20f * 100f / 105f, within(1e-4f));
    }

    @Test
    @DisplayName("攻守回能：攻击者 +10、受击者 +5（egr=100 基准）")
    void energyOnHitAndTaken() {
        BattleUnit atk = attacker();
        BattleUnit target = target();
        BattleState state = state(atk, target);
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, true, null);
        assertThat(atk.getEnergy()).isCloseTo(10f, within(1e-6f));
        assertThat(target.getEnergy()).isCloseTo(5f, within(1e-6f));
    }

    @Test
    @DisplayName("回能乘获得者自身 egr：egr 115 → 攻击者 +11.5、受击者 +5.75")
    void energyScaledByOwnGainRate() {
        BattleUnit atk = new BattleUnit(1, tpl("a"), 1, Side.PLAYER, melee(),
                new BattleStats(100f, 10f, 5f, 1f, 1f, 1f, 0f, 115f, 0f));
        atk.setPosition(2, 4);
        BattleUnit target = new BattleUnit(2, tpl("t"), 1, Side.ENEMY, melee(),
                new BattleStats(100f, 10f, 5f, 1f, 1f, 1f, 0f, 115f, 0f));
        target.setPosition(2, 2);
        BattleState state = state(atk, target);
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, true, null);
        assertThat(atk.getEnergy()).isCloseTo(11.5f, within(1e-6f));
        assertThat(target.getEnergy()).isCloseTo(5.75f, within(1e-6f));
    }

    @Test
    @DisplayName("控制期回能完全冻结（口径 #5）：被 STUN 的受击者 +0，攻击者不受目标控制影响")
    void energyFrozenWhileStunned() {
        BattleUnit atk = attacker();
        BattleUnit target = target();
        target.addStatus(new ActiveStatus(StatusType.STUN, -1, 0f, 2f));
        BattleState state = state(atk, target);
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, true, null);
        assertThat(atk.getEnergy()).isCloseTo(10f, within(1e-6f));
        assertThat(target.getEnergy()).isEqualTo(0f);
    }

    @Test
    @DisplayName("能量封顶 100（口径 #5）：95+10 → 100 不上溢")
    void energyClampedAtHundred() {
        BattleUnit atk = attacker();
        atk.setEnergy(95f);
        BattleUnit target = target();
        BattleState state = state(atk, target);
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, true, null);
        assertThat(atk.getEnergy()).isEqualTo(100f);
    }

    @Test
    @DisplayName("吸血（Q3）：普攻命中回复 = 护甲后实际扣血 × lifesteal/100，不溢出 maxHp")
    void lifestealOnBasicAttack() {
        BattleUnit atk = new BattleUnit(1, tpl("a"), 1, Side.PLAYER, melee(),
                new BattleStats(100f, 10f, 5f, 1f, 1f, 1f, 20f, 100f, 0f)); // lifesteal 20
        atk.modifyHp(-50f); // 50/100
        BattleUnit target = target(); // armor 5 → 10×100/105
        BattleState state = state(atk, target);
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, true, null);
        float damage = 10f * 100f / 105f;
        assertThat(atk.getCurrentHp()).isCloseTo(50f + damage * 0.2f, within(1e-4f));

        // 不溢出：满血攻击者吸满也不再涨
        BattleUnit fullAtk = new BattleUnit(3, tpl("a2"), 1, Side.PLAYER, melee(),
                new BattleStats(100f, 10f, 5f, 1f, 1f, 1f, 20f, 100f, 0f));
        BattleUnit target2 = target();
        BattleState state2 = state(fullAtk, target2);
        pipeline(new RecordingTrigger()).applyDirectHit(state2, fullAtk, target2, 10f, 1f, false, true, null);
        assertThat(fullAtk.getCurrentHp()).isEqualTo(100f);
    }

    @Test
    @DisplayName("吸血仅普攻触发（Q3）：技能直伤（basicAttack=false）与 DOT 真伤不吸血")
    void lifestealOnlyForBasicAttack() {
        BattleUnit atk = new BattleUnit(1, tpl("a"), 1, Side.PLAYER, melee(),
                new BattleStats(100f, 10f, 5f, 1f, 1f, 1f, 50f, 100f, 0f)); // lifesteal 50
        atk.modifyHp(-50f);
        BattleUnit target = target();
        BattleState state = state(atk, target);
        DamagePipeline pipeline = pipeline(new RecordingTrigger());

        pipeline.applyDirectHit(state, atk, target, 10f, 2.5f, false, false, "skill_x"); // 技能直伤
        assertThat(atk.getCurrentHp()).isEqualTo(50f); // 无回复

        pipeline.applyTrueDamage(state, atk, target, 10f); // DOT 真伤路径
        assertThat(atk.getCurrentHp()).isEqualTo(50f);
    }

    @Test
    @DisplayName("applyTrueDamage：无视护甲、先扣盾、可致死、无事件无回能")
    void trueDamageBypassesArmor() {
        BattleUnit atk = attacker();
        BattleUnit target = new BattleUnit(2, tpl("t"), 1, Side.ENEMY, melee(), stats(100, 10, 100f, 1f, 1f, 1f));
        target.addStatus(new ActiveStatus(StatusType.SHIELD, -1, 20f, Float.POSITIVE_INFINITY));
        BattleState state = state(atk, target);
        int eventsBefore = state.getEvents().size();
        DamagePipeline pipeline = pipeline(new RecordingTrigger());
        pipeline.applyTrueDamage(state, atk, target, 50f);
        assertThat(target.getCurrentHp()).isCloseTo(70f, within(1e-6f)); // 50−20 盾
        assertThat(state.getEvents()).hasSize(eventsBefore); // 无事件
        assertThat(target.getEnergy()).isEqualTo(0f);        // 无回能

        pipeline.applyTrueDamage(state, atk, target, 999f);
        assertThat(target.getCurrentHp()).isEqualTo(0f); // 可致死
    }

    @Test
    @DisplayName("applyHeal：cap maxHp、溢出作废、发 Healed 事件（实际回复量）")
    void healCapsAndRecords() {
        BattleUnit target = target();
        target.modifyHp(-30f);
        BattleUnit atk = attacker();
        BattleState state = state(atk, target);
        pipeline(new RecordingTrigger()).applyHeal(state, target, 50f);
        assertThat(target.getCurrentHp()).isEqualTo(100f); // 只回 30
        assertThat(state.getEvents()).hasSize(1);
        CombatEvent event = state.getEvents().get(0);
        assertThat(event.getType()).isEqualTo(CombatEvent.Type.HEALED);
        assertThat(event.getAmount()).isCloseTo(30f, within(1e-6f));
    }

    @Test
    @DisplayName("Hit 事件字段：amount = 扣盾后实际伤害、crit、skillId（普攻 null / 技能带 id）")
    void hitEventFields() {
        BattleUnit atk = attacker();
        BattleUnit target = target();
        BattleState state = state(atk, target);
        DamagePipeline pipeline = pipeline(new RecordingTrigger());

        pipeline.applyDirectHit(state, atk, target, 10f, 1f, true, true, null);
        CombatEvent basic = state.getEvents().get(0);
        assertThat(basic.getType()).isEqualTo(CombatEvent.Type.HIT);
        assertThat(basic.isCrit()).isTrue();
        assertThat(basic.getSkillId()).isNull();
        assertThat(basic.getAmount()).isCloseTo(15f * 100f / 105f, within(1e-4f));

        pipeline.applyDirectHit(state, atk, target, 10f, 2f, false, false, "skill_exec");
        CombatEvent skill = state.getEvents().get(1);
        assertThat(skill.getSkillId()).isEqualTo("skill_exec");
        assertThat(skill.getAmount()).isCloseTo(20f * 100f / 105f, within(1e-4f));
    }

    @Test
    @DisplayName("CastTrigger 跨百回调：能量 95 → +10 跨百即触发一次；已在 100 不重复触发")
    void castTriggerFiresOnCrossingHundred() {
        RecordingTrigger trigger = new RecordingTrigger();
        DamagePipeline pipeline = pipeline(trigger);
        BattleUnit atk = attacker();
        BattleUnit target = target();
        BattleState state = state(atk, target);

        atk.setEnergy(95f);
        pipeline.applyDirectHit(state, atk, target, 10f, 1f, false, true, null);
        assertThat(trigger.casted).containsExactly(atk); // 95+10 跨百 → 就地施放

        trigger.casted.clear();
        pipeline.applyDirectHit(state, atk, target, 10f, 1f, false, true, null);
        assertThat(trigger.casted).isEmpty(); // 已在 100（延后施放中）不再回调，重试归行动链
    }

    private static com.voidvvv.kz_auto_chess_n.data.SkillData melee() {
        return com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.meleeSkill();
    }
}
