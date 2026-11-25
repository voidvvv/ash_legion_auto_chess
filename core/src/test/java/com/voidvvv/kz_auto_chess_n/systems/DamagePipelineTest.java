package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.BattleStats;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.EffectTarget;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
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
        DamagePipeline pipeline = new DamagePipeline(trigger);
        pipeline.bindStatusSystem(new StatusSystem(pipeline)); // CP25：普攻附带状态经 StatusSystem 挂载
        return pipeline;
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

        pipeline.applyDirectHit(state, atk, noArmor, 10f, 1f, false, 0f, true, null, 0f);
        assertThat(noArmor.getCurrentHp()).isCloseTo(90f, within(1e-6f));

        pipeline.applyDirectHit(state, atk, heavyArmor, 10f, 1f, false, 0f, true, null, 0f);
        assertThat(heavyArmor.getCurrentHp()).isCloseTo(95f, within(1e-6f));
    }

    @Test
    @DisplayName("暴击 ×1.5 与倍率乘序：atk10 × 倍率2 × 暴击1.5（0 甲）= 30")
    void critAndMultiplierOrder() {
        BattleUnit atk = attacker();
        BattleUnit zeroArmor = new BattleUnit(5, tpl("z"), 1, Side.ENEMY, melee(), stats(100, 10, 0f, 1f, 1f, 1f));
        zeroArmor.setPosition(2, 2);
        BattleState state = state(atk, zeroArmor);
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, zeroArmor, 10f, 2f, true, 0f, true, null, 0f);
        assertThat(zeroArmor.getCurrentHp()).isCloseTo(70f, within(1e-6f));
    }

    @Test
    @DisplayName("暴伤属性化（§五.5）：攻击者 critDamage 200 → 暴击 ×2.0；attacker=null 走缺省 ×1.5")
    void critMultiplierFromEffectiveStat() {
        BattleUnit atk = new BattleUnit(1, tpl("a"), 1, Side.PLAYER, melee(),
                new BattleStats(100f, 10f, 0f, 1f, 1f, 1f, 0f, 100f, 0f, 20f, 200f)); // critDamage 200
        atk.setPosition(2, 4);
        BattleUnit zeroArmor = new BattleUnit(5, tpl("z"), 1, Side.ENEMY, melee(), stats(100, 10, 0f, 1f, 1f, 1f));
        zeroArmor.setPosition(2, 2);
        BattleState state = state(atk, zeroArmor);
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, zeroArmor, 10f, 1f, true, 0f, true, null, 0f);
        assertThat(zeroArmor.getCurrentHp()).isCloseTo(80f, within(1e-6f)); // 10 × 2.0

        BattleUnit anonymous = new BattleUnit(6, tpl("n"), 1, Side.ENEMY, melee(), stats(100, 10, 0f, 1f, 1f, 1f));
        anonymous.setPosition(4, 2);
        pipeline(new RecordingTrigger()).applyDirectHit(state, null, anonymous, 10f, 1f, true, 0f, true, null, 0f);
        assertThat(anonymous.getCurrentHp()).isCloseTo(85f, within(1e-6f)); // 缺省 ×1.5
    }

    @Test
    @DisplayName("先扣盾 → 盾尽透血：盾 30 吃 50 伤（armor5 折减 47.62）→ HP 只扣 17.62 且盾移除")
    void shieldAbsorbsBeforeHp() {
        BattleUnit atk = attacker();
        BattleUnit target = target();
        target.addStatus(new ActiveStatus(StatusType.SHIELD, -1, 30f, Float.POSITIVE_INFINITY));
        BattleState state = state(atk, target);
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 50f, 1f, false, 0f, true, null, 0f);
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
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 20f, 1f, false, 0f, true, null, 0f);
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
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
        assertThat(atk.getEnergy()).isCloseTo(10f, within(1e-6f));
        assertThat(target.getEnergy()).isCloseTo(5f, within(1e-6f));
    }

    @Test
    @DisplayName("回能乘获得者自身 egr：egr 115 → 攻击者 +11.5、受击者 +5.75")
    void energyScaledByOwnGainRate() {
        BattleUnit atk = new BattleUnit(1, tpl("a"), 1, Side.PLAYER, melee(),
                new BattleStats(100f, 10f, 5f, 1f, 1f, 1f, 0f, 115f, 0f, 20f, 150f));
        atk.setPosition(2, 4);
        BattleUnit target = new BattleUnit(2, tpl("t"), 1, Side.ENEMY, melee(),
                new BattleStats(100f, 10f, 5f, 1f, 1f, 1f, 0f, 115f, 0f, 20f, 150f));
        target.setPosition(2, 2);
        BattleState state = state(atk, target);
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
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
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
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
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
        assertThat(atk.getEnergy()).isEqualTo(100f);
    }

    @Test
    @DisplayName("吸血（Q3）：普攻命中回复 = 护甲后实际扣血 × lifesteal/100，不溢出 maxHp")
    void lifestealOnBasicAttack() {
        BattleUnit atk = new BattleUnit(1, tpl("a"), 1, Side.PLAYER, melee(),
                new BattleStats(100f, 10f, 5f, 1f, 1f, 1f, 20f, 100f, 0f, 20f, 150f)); // lifesteal 20
        atk.modifyHp(-50f); // 50/100
        BattleUnit target = target(); // armor 5 → 10×100/105
        BattleState state = state(atk, target);
        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
        float damage = 10f * 100f / 105f;
        assertThat(atk.getCurrentHp()).isCloseTo(50f + damage * 0.2f, within(1e-4f));

        // 不溢出：满血攻击者吸满也不再涨
        BattleUnit fullAtk = new BattleUnit(3, tpl("a2"), 1, Side.PLAYER, melee(),
                new BattleStats(100f, 10f, 5f, 1f, 1f, 1f, 20f, 100f, 0f, 20f, 150f));
        BattleUnit target2 = target();
        BattleState state2 = state(fullAtk, target2);
        pipeline(new RecordingTrigger()).applyDirectHit(state2, fullAtk, target2, 10f, 1f, false, 0f, true, null, 0f);
        assertThat(fullAtk.getCurrentHp()).isEqualTo(100f);
    }

    @Test
    @DisplayName("吸血仅普攻触发（Q3）：技能直伤（basicAttack=false）与 DOT 真伤不吸血")
    void lifestealOnlyForBasicAttack() {
        BattleUnit atk = new BattleUnit(1, tpl("a"), 1, Side.PLAYER, melee(),
                new BattleStats(100f, 10f, 5f, 1f, 1f, 1f, 50f, 100f, 0f, 20f, 150f)); // lifesteal 50
        atk.modifyHp(-50f);
        BattleUnit target = target();
        BattleState state = state(atk, target);
        DamagePipeline pipeline = pipeline(new RecordingTrigger());

        pipeline.applyDirectHit(state, atk, target, 10f, 2.5f, false, 0f, false, "skill_x", 0f); // 技能直伤
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

        pipeline.applyDirectHit(state, atk, target, 10f, 1f, true, 0f, true, null, 0f);
        CombatEvent basic = state.getEvents().get(0);
        assertThat(basic.getType()).isEqualTo(CombatEvent.Type.HIT);
        assertThat(basic.isCrit()).isTrue();
        assertThat(basic.getSkillId()).isNull();
        assertThat(basic.getAmount()).isCloseTo(15f * 100f / 105f, within(1e-4f));

        pipeline.applyDirectHit(state, atk, target, 10f, 2f, false, 0f, false, "skill_exec", 0f);
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
        pipeline.applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
        assertThat(trigger.casted).containsExactly(atk); // 95+10 跨百 → 就地施放

        trigger.casted.clear();
        pipeline.applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
        assertThat(trigger.casted).isEmpty(); // 已在 100（延后施放中）不再回调，重试归行动链
    }

    private static com.voidvvv.kz_auto_chess_n.data.SkillData melee() {
        return com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.meleeSkill();
    }

    // —— v5 击杀归因（CP13，C5/C12 前提）——

    @Test
    @DisplayName("归因记录：直伤与真伤 hpDamage>0 记来源 id；匿名来源（null）不记")
    void lastDamagerIdRecordedOnDamage() {
        BattleUnit atk = attacker();
        BattleUnit target = target();
        BattleUnit anonymous = unit(9, Side.ENEMY, tpl("n"), 4, 2);
        BattleState state = state(atk, target, anonymous);

        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
        assertThat(target.getLastDamagerId()).isEqualTo(atk.getId());

        target.setLastDamagerId(-1); // 手动复位观察真伤路径
        pipeline(new RecordingTrigger()).applyTrueDamage(state, atk, target, 10f);
        assertThat(target.getLastDamagerId()).isEqualTo(atk.getId());

        target.setLastDamagerId(-1);
        pipeline(new RecordingTrigger()).applyDirectHit(state, null, target, 10f, 1f, false, 0f, true, null, 0f);
        assertThat(target.getLastDamagerId()).isEqualTo(-1); // 匿名（回环反弹等）不记
    }

    @Test
    @DisplayName("盾全额吸收（hpDamage=0）不记归因：有效伤害才更新 lastDamagerId")
    void shieldFullAbsorbSkipsAttribution() {
        BattleUnit atk = attacker();
        BattleUnit target = target();
        target.addStatus(new ActiveStatus(StatusType.SHIELD, -1, 999f, Float.POSITIVE_INFINITY));
        BattleState state = state(atk, target);

        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
        assertThat(target.getCurrentHp()).isEqualTo(100f);
        assertThat(target.getLastDamagerId()).isEqualTo(-1); // 未穿透到 HP → 无归因
    }

    @Test
    @DisplayName("真伤带 causeId：发 HIT 事件（紫飘字载荷口径）；DOT 三参重载维持无事件（口径 #10）")
    void trueDamageWithCauseIdRecordsHitEvent() {
        BattleUnit atk = attacker();
        BattleUnit target = target();
        BattleState state = state(atk, target);
        DamagePipeline pipeline = pipeline(new RecordingTrigger());

        int before = state.getEvents().size();
        pipeline.applyTrueDamage(state, atk, target, 12f, "deathrattle_unit_x");
        assertThat(state.getEvents()).hasSize(before + 1);
        CombatEvent event = state.getEvents().get(state.getEvents().size() - 1);
        assertThat(event.getType()).isEqualTo(CombatEvent.Type.HIT);
        assertThat(event.getSkillId()).isEqualTo("deathrattle_unit_x");
        assertThat(event.getAmount()).isCloseTo(12f, within(1e-6f)); // 真伤全额（无护甲折减）
        assertThat(event.isCrit()).isFalse();

        pipeline.applyTrueDamage(state, atk, target, 5f); // DOT 路径：无事件、但归因仍记录
        assertThat(state.getEvents()).hasSize(before + 1);
        assertThat(target.getLastDamagerId()).isEqualTo(atk.getId());
    }

    @Test
    @DisplayName("真伤带 causeId 且盾全吸收：hpDamage=0 → 无事件、无归因")
    void trueDamageCauseIdShieldedNoEvent() {
        BattleUnit atk = attacker();
        BattleUnit target = target();
        target.addStatus(new ActiveStatus(StatusType.SHIELD, -1, 999f, Float.POSITIVE_INFINITY));
        BattleState state = state(atk, target);

        int before = state.getEvents().size();
        pipeline(new RecordingTrigger()).applyTrueDamage(state, atk, target, 10f, "deathrattle_unit_x");
        assertThat(state.getEvents()).hasSize(before);
        assertThat(target.getLastDamagerId()).isEqualTo(-1);
    }

    // —— v5 批3 受击窗口（CP20/CP21，K9）——

    /** 机制词直授（测试充当 systems 层下发；与 startBattle 谓词过滤等效的单条形态） */
    private static void grant(BattleUnit unit, SynergyMechanic kind, Float value, Float value2) {
        unit.setMechanics(Collections.singletonList(new EffectData(null, null, kind, null,
                value, value2, null, null, EffectTarget.ALLIES, null)));
    }

    @Test
    @DisplayName("免伤（巨人6 DAMAGE_REDUCTION 15%）：直伤乘算 ×0.85；DOT/真伤全额豁免（K9）")
    void damageReductionAppliesToDirectOnly() {
        BattleUnit atk = attacker();
        BattleUnit target = target(); // armor 5
        grant(target, SynergyMechanic.DAMAGE_REDUCTION, 15f, null);
        BattleState state = state(atk, target);
        DamagePipeline pipeline = pipeline(new RecordingTrigger());

        pipeline.applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
        float expected = 10f * 0.85f * 100f / 105f;
        assertThat(target.getCurrentHp()).isCloseTo(100f - expected, within(1e-4f));

        pipeline.applyTrueDamage(state, atk, target, 10f); // 真伤路径：免伤豁免、全额
        assertThat(target.getCurrentHp()).isCloseTo(100f - expected - 10f, within(1e-4f));
    }

    @Test
    @DisplayName("余震反弹（巨人4 REFLECT 18%）：甲后实际扣血 ×18% 真伤回攻击者，无视攻击者护甲")
    void reflectDealsTrueDamageToAttacker() {
        BattleUnit atk = new BattleUnit(1, tpl("a"), 1, Side.PLAYER, melee(),
                stats(100, 10, 100f, 1f, 1f, 1f)); // 攻击者重甲 100——真伤无效
        atk.setPosition(2, 4);
        BattleUnit target = target(); // armor 5
        grant(target, SynergyMechanic.REFLECT, 18f, null);
        BattleState state = state(atk, target);

        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
        float direct = 10f * 100f / 105f;
        assertThat(target.getCurrentHp()).isCloseTo(100f - direct, within(1e-4f));
        assertThat(atk.getCurrentHp()).isCloseTo(100f - direct * 0.18f, within(1e-4f));
        assertThat(atk.getLastDamagerId()).isEqualTo(target.getId()); // K4：归因=反弹者
    }

    @Test
    @DisplayName("余震可致死入级联：低血攻击者被弹死 → DeathSystem 清扫 UNIT_DIED killer=反弹者")
    void reflectKillSweptWithReflectorAsKiller() {
        BattleUnit atk = new BattleUnit(1, tpl("a"), 1, Side.PLAYER, melee(), stats(2, 10, 0f, 1f, 1f, 1f));
        atk.setPosition(2, 4);
        BattleUnit target = target();
        grant(target, SynergyMechanic.REFLECT, 100f, null); // 全额反弹必杀
        BattleState state = state(atk, target);
        DamagePipeline pipeline = pipeline(new RecordingTrigger());

        pipeline.applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
        assertThat(atk.getCurrentHp()).isEqualTo(0f);

        new DeathSystem(new TargetingSystem(), pipeline, new StatusSystem(pipeline)).sweep(state);
        CombatEvent died = null;
        for (CombatEvent e : state.getEvents()) {
            if (e.getType() == CombatEvent.Type.UNIT_DIED && e.getSourceId() == atk.getId()) {
                died = e;
            }
        }
        assertThat(died).isNotNull();
        assertThat(died.getKillerId()).isEqualTo(target.getId());
    }

    @Test
    @DisplayName("余震对 DOT/真伤零触发：目标 REFLECT 100 但 DOT 命中不反弹")
    void reflectNeverTriggersOnTrueDamage() {
        BattleUnit atk = attacker();
        BattleUnit target = target();
        grant(target, SynergyMechanic.REFLECT, 100f, null);
        BattleState state = state(atk, target);

        pipeline(new RecordingTrigger()).applyTrueDamage(state, atk, target, 10f);
        assertThat(atk.getCurrentHp()).isEqualTo(100f); // 攻击者无损
    }

    @Test
    @DisplayName("收割（刺客4 EXECUTE 40/35）：目标 hpRatio<35% → 直伤 ×1.4；≥35% 不增伤")
    void executeAmplifiesBelowThreshold() {
        BattleUnit atk = attacker();
        BattleUnit low = target(); // armor 5
        grant(atk, SynergyMechanic.EXECUTE, 40f, 35f);
        low.modifyHp(-66f); // 34/100 = 34% < 35%
        BattleUnit healthy = unit(7, Side.ENEMY, tpl("h"), 4, 2);
        healthy.modifyHp(-64f); // 36/100 = 36% ≥ 35%
        BattleState state = state(atk, low, healthy);

        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, low, 10f, 1f, false, 0f, false, null, 0f);
        assertThat(low.getCurrentHp()).isCloseTo(34f - 10f * 1.4f * 100f / 105f, within(1e-4f));

        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, healthy, 10f, 1f, false, 0f, false, null, 0f);
        assertThat(healthy.getCurrentHp()).isCloseTo(36f - 10f * 100f / 105f, within(1e-4f));
    }

    @Test
    @DisplayName("穿甲参数（K9 窗口）：armorPenRatio 0.5 → 有效护甲减半（甲 100 → 50）")
    void armorPenRatioHalvesEffectiveArmor() {
        BattleUnit atk = attacker();
        BattleUnit heavy = new BattleUnit(8, tpl("hv"), 1, Side.ENEMY, melee(), stats(100, 10, 100f, 1f, 1f, 1f));
        heavy.setPosition(4, 2);
        BattleState state = state(atk, heavy);

        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, heavy, 10f, 1f, false, 0f, true, null, 0.5f);
        assertThat(heavy.getCurrentHp()).isCloseTo(100f - 10f * 100f / 150f, within(1e-4f));
    }

    @Test
    @DisplayName("暴伤加成参数（K10 前置）：critBonusPp 50 → 该刀暴击 ×2.0（150+50）")
    void critBonusPpAddsToCritMultiplier() {
        BattleUnit atk = attacker();
        BattleUnit zeroArmor = new BattleUnit(5, tpl("z"), 1, Side.ENEMY, melee(), stats(100, 10, 0f, 1f, 1f, 1f));
        zeroArmor.setPosition(2, 2);
        BattleState state = state(atk, zeroArmor);

        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, zeroArmor, 10f, 1f, true, 50f, true, null, 0f);
        assertThat(zeroArmor.getCurrentHp()).isCloseTo(100f - 10f * 2f, within(1e-4f));
    }

    @Test
    @DisplayName("旧疤（兽人6 SCAR 3/6）：每 3 次被普攻命中进一层，层数并入有效攻/甲（+5%×层 / +3×层）")
    void scarStacksEveryThreeBasicHits() {
        BattleUnit atk = attacker(); // atk 10, 命中远小于目标血量
        BattleUnit target = new BattleUnit(2, tpl("t"), 1, Side.ENEMY, melee(),
                stats(1000, 10, 0f, 1f, 1f, 1f)); // 0 甲：伤害断言不受层数甲加成干扰
        target.setPosition(2, 2);
        grant(target, SynergyMechanic.SCAR, 3f, 6f);
        BattleState state = state(atk, target);
        DamagePipeline pipeline = pipeline(new RecordingTrigger());

        pipeline.applyDirectHit(state, atk, target, 1f, 1f, false, 0f, true, null, 0f); // 第 1 刀
        pipeline.applyDirectHit(state, atk, target, 1f, 1f, false, 0f, true, null, 0f); // 第 2 刀
        assertThat(target.getCombatCounters().getScarStacks()).isZero(); // 不足 3 次无层

        pipeline.applyDirectHit(state, atk, target, 1f, 1f, false, 0f, true, null, 0f); // 第 3 刀 → 1 层
        assertThat(target.getCombatCounters().getScarStacks()).isEqualTo(1);
        assertThat(target.getEffective(StatKey.ATTACK)).isCloseTo(10f * 1.05f, within(1e-6f));
        assertThat(target.getEffective(StatKey.ARMOR)).isCloseTo(3f, within(1e-6f)); // 0 甲 + 3×1

        for (int i = 0; i < 15; i++) { // 累计 18 次被击 → 6 层（封顶）
            pipeline.applyDirectHit(state, atk, target, 1f, 1f, false, 0f, true, null, 0f);
        }
        assertThat(target.getCombatCounters().getScarStacks()).isEqualTo(6);
        assertThat(target.getEffective(StatKey.ATTACK)).isCloseTo(10f * 1.3f, within(1e-6f)); // +5%×6
        assertThat(target.getEffective(StatKey.ARMOR)).isCloseTo(18f, within(1e-6f));        // +3×6

        pipeline.applyDirectHit(state, atk, target, 1f, 1f, false, 0f, true, null, 0f); // 第 19 刀不再进层
        assertThat(target.getCombatCounters().getScarStacks()).isEqualTo(6);
    }

    @Test
    @DisplayName("旧疤只认普攻：DOT/真伤命中不喂层；无 SCAR 机制的受击零副作用")
    void scarIgnoresTrueDamageAndNonScarTargets() {
        BattleUnit atk = attacker();
        BattleUnit target = target();
        grant(target, SynergyMechanic.SCAR, 3f, 6f);
        BattleUnit plain = unit(9, Side.ENEMY, tpl("p"), 5, 2);
        BattleState state = state(atk, target, plain);
        DamagePipeline pipeline = pipeline(new RecordingTrigger());

        pipeline.applyTrueDamage(state, atk, target, 5f);
        pipeline.applyTrueDamage(state, atk, target, 5f);
        pipeline.applyTrueDamage(state, atk, target, 5f); // 3 次真伤 ≥ value 次但不喂层
        assertThat(target.getCombatCounters().getScarStacks()).isZero();

        pipeline.applyDirectHit(state, atk, target, 10f, 1f, false, 0f, false, null, 0f); // 技能直伤也不喂层
        assertThat(target.getCombatCounters().getScarStacks()).isZero();

        pipeline.applyDirectHit(state, atk, plain, 10f, 1f, false, 0f, true, null, 0f); // 无机制者空操作
        assertThat(plain.getCombatCounters().getScarStacks()).isZero();
    }

    // —— v5 批4：普攻附带状态（CP25）——

    /** BASIC_ATTACK_STATUS 机制直授（status+value+duration） */
    private static void grantStatus(BattleUnit unit, StatusType status, Float value, Float duration) {
        unit.setMechanics(Collections.singletonList(new EffectData(null, null,
                SynergyMechanic.BASIC_ATTACK_STATUS, null, value, null, status, duration,
                EffectTarget.ALLIES, null)));
    }

    @Test
    @DisplayName("普攻附带 BURN（兽人4，CP25）：power = 本发攻击 ×30%、时长 3s、同 type 刷新取大")
    void basicAttackStatusBurnUsesAttackPower() {
        BattleUnit atk = attacker();
        grantStatus(atk, StatusType.BURN, 30f, 3f);
        BattleUnit target = target();
        BattleState state = state(atk, target);
        DamagePipeline pipeline = pipeline(new RecordingTrigger());

        pipeline.applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
        ActiveStatus burn = target.getStatuses().get(0);
        assertThat(burn.getType()).isEqualTo(StatusType.BURN);
        assertThat(burn.getPower()).isCloseTo(3f, within(1e-6f));        // 10 × 30%
        assertThat(burn.getRemainingTime()).isCloseTo(3f, within(1e-6f));

        pipeline.applyDirectHit(state, atk, target, 20f, 1f, false, 0f, true, null, 0f);
        assertThat(burn.getPower()).isCloseTo(6f, within(1e-6f));        // 20 × 30%——刷新取大
    }

    @Test
    @DisplayName("普攻附带 SLOW（野兽4，CP25）：非 DOT power = value 百分点（30 / 2s）")
    void basicAttackStatusSlowUsesValue() {
        BattleUnit atk = attacker();
        grantStatus(atk, StatusType.SLOW, 30f, 2f);
        BattleUnit target = target();
        BattleState state = state(atk, target);

        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
        ActiveStatus slow = target.getStatuses().get(0);
        assertThat(slow.getType()).isEqualTo(StatusType.SLOW);
        assertThat(slow.getPower()).isCloseTo(30f, within(1e-6f)); // 非 DOT：power = value
        assertThat(slow.getRemainingTime()).isCloseTo(2f, within(1e-6f));
    }

    @Test
    @DisplayName("弹道附带取发射快照（CP25）：attackPower=快照 10，命中时攻已涨 60 → BURN 仍 3 非 18")
    void basicAttackStatusUsesSnapshotPower() {
        BattleUnit atk = attacker();
        grantStatus(atk, StatusType.BURN, 30f, 3f);
        BattleUnit target = target();
        BattleState state = state(atk, target);
        atk.addStatus(new ActiveStatus(StatusType.ATK_UP, -1, 500f, 10f)); // 有效攻 ×6 = 60
        assertThat(atk.getEffective(StatKey.ATTACK)).isCloseTo(60f, within(1e-4f));

        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
        assertThat(target.getStatuses().get(0).getPower()).isCloseTo(3f, within(1e-6f)); // 快照 10 × 30%
    }

    @Test
    @DisplayName("DOT/真伤不触发附带（CP25）：applyTrueDamage 命中无 BURN（非普攻路径）")
    void basicAttackStatusNotTriggeredByTrueDamage() {
        BattleUnit atk = attacker();
        grantStatus(atk, StatusType.BURN, 30f, 3f);
        BattleUnit target = target();
        BattleState state = state(atk, target);

        pipeline(new RecordingTrigger()).applyTrueDamage(state, atk, target, 10f);
        assertThat(target.getStatuses()).isEmpty();
    }

    // —— v5 批4：收割专项（CP26——接线在 CP20，本组为验证收口）——

    @Test
    @DisplayName("收割边界与技能路径（CP26）：恰 35% 不增伤（严格小于）；技能直伤同吃收割")
    void executeStrictBoundaryAndSkillPath() {
        BattleUnit atk = attacker();
        grant(atk, SynergyMechanic.EXECUTE, 40f, 35f);
        BattleUnit edge = target();
        edge.modifyHp(-65f); // 35/100 = 35% —— 恰等阈值，不增伤
        BattleState state = state(atk, edge);

        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, edge, 10f, 1f, false, 0f, false, null, 0f);
        assertThat(edge.getCurrentHp()).isCloseTo(35f - 10f * 100f / 105f, within(1e-4f)); // 无 ×1.4

        BattleUnit low = unit(7, Side.ENEMY, tpl("l"), 4, 2);
        low.modifyHp(-70f); // 30% < 35%
        BattleState state2 = state(atk, low);
        pipeline(new RecordingTrigger()).applyDirectHit(state2, atk, low, 10f, 2f, false, 0f, false,
                "skill_execute", 0f); // 技能直伤（处决）
        assertThat(low.getCurrentHp()).isCloseTo(30f - 20f * 1.4f * 100f / 105f, within(1e-4f)); // ×1.4 生效
    }

    @Test
    @DisplayName("收割 × 免伤同乘法链（CP26）：×1.4 × ×0.85 互不影响")
    void executeStacksWithReduction() {
        BattleUnit atk = attacker();
        grant(atk, SynergyMechanic.EXECUTE, 40f, 35f);
        BattleUnit target = target(); // armor 5
        grant(target, SynergyMechanic.DAMAGE_REDUCTION, 15f, null);
        target.modifyHp(-70f); // 30% < 35%
        BattleState state = state(atk, target);

        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, target, 10f, 1f, false, 0f, true, null, 0f);
        assertThat(target.getCurrentHp()).isCloseTo(30f - 10f * 1.4f * 0.85f * 100f / 105f, within(1e-4f));
    }

    @Test
    @DisplayName("收割对称（CP26）：敌方攻击者对玩家残血单位同样生效")
    void executeSymmetricOnSides() {
        BattleUnit atk = unit(1, Side.ENEMY, tpl("ea"), 2, 2);
        grant(atk, SynergyMechanic.EXECUTE, 40f, 35f);
        BattleUnit victim = unit(2, Side.PLAYER, tpl("pv"), 2, 4); // armor 5
        victim.modifyHp(-80f); // 20% < 35%
        BattleState state = state(atk, victim);

        pipeline(new RecordingTrigger()).applyDirectHit(state, atk, victim, 10f, 1f, false, 0f, true, null, 0f);
        assertThat(victim.getCurrentHp()).isCloseTo(20f - 10f * 1.4f * 100f / 105f, within(1e-4f));
    }
}
