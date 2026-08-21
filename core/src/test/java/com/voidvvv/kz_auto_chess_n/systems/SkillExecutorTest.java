package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.BattleStats;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.effect;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.skill;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.state;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.tpl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 技能执行器测试（battle §六三步执行模型 + Q2/Q4 裁决）：
 * shape 解析 → 载体投送（LINE 抛错）→ 逐效果应用；载荷冻结（口径 #13）；
 * 延后施放保留能量（口径 #18）。夹具零 LINE 构造（Q2）。
 */
class SkillExecutorTest {

    private static final SkillExecutor EXECUTOR = executor();

    private static SkillExecutor executor() {
        DamagePipeline pipeline = new DamagePipeline(new CastTrigger() {
            @Override
            public void tryCast(BattleState state, BattleUnit caster) {
            }
        });
        return new SkillExecutor(pipeline, new StatusSystem(pipeline));
    }

    // —— 夹具：0 甲直构单位（数值可精确断言）——

    private static BattleUnit u(int id, Side side, int x, int y, SkillData skill, float atk, int star) {
        UnitData template = tpl("u" + id);
        BattleUnit unit = new BattleUnit(id, template, star, side, skill,
                new BattleStats(100f, atk, 0f, 1f, 1f, 1f, 0f, 100f, 0f));
        unit.setPosition(x, y);
        return unit;
    }

    private static BattleUnit caster(SkillData skill) {
        return u(1, Side.PLAYER, 2, 4, skill, 10f, 1);
    }

    private static BattleUnit enemy(int id, int x, int y) {
        return u(id, Side.ENEMY, x, y, skill("sk_e", SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 1f, null, null)), 10f, 1);
    }

    // —— ① shape 解析 ——

    @Test
    @DisplayName("SINGLE_TARGET：命中锁定目标（MELEE_INSTANT 直伤 10×2=20，0 甲）")
    void singleTargetHitsLockedTarget() {
        BattleUnit caster = caster(skill("s1", SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 2f, null, null)));
        BattleUnit target = enemy(2, 2, 2);
        caster.setTargetId(2);
        BattleState state = state(caster, target);
        assertThat(EXECUTOR.cast(state, caster)).isTrue();
        assertThat(target.getCurrentHp()).isCloseTo(80f, within(1e-6f));
    }

    @Test
    @DisplayName("SELF：增益落在自己身上（ATK_UP 30 → 攻击 13）")
    void selfShapeBuffsCaster() {
        BattleUnit caster = caster(skill("s2", SkillShape.SELF, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.APPLY_STATUS, 30f, StatusType.ATK_UP, 8f)));
        BattleState state = state(caster);
        assertThat(EXECUTOR.cast(state, caster)).isTrue();
        assertThat(caster.getStatuses()).hasSize(1);
        assertThat(caster.getEffective(com.voidvvv.kz_auto_chess_n.data.StatKey.ATTACK))
                .isCloseTo(13f, within(1e-6f));
    }

    @Test
    @DisplayName("LOWEST_ALLY：治疗 HP% 最低友军（含自己）")
    void lowestAllyHealsWeakest() {
        BattleUnit caster = caster(skill("s3", SkillShape.LOWEST_ALLY, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.HEAL, 0.25f, null, null)));
        BattleUnit ally = u(3, Side.PLAYER, 3, 4, caster.getSkill(), 10f, 1);
        ally.modifyHp(-60f); // 0.4
        caster.modifyHp(-10f); // 0.9
        BattleState state = state(caster, ally);
        assertThat(EXECUTOR.cast(state, caster)).isTrue();
        assertThat(ally.getCurrentHp()).isCloseTo(65f, within(1e-4f)); // 40 + 25
        assertThat(caster.getCurrentHp()).isCloseTo(90f, within(1e-6f)); // 未被治疗
    }

    @Test
    @DisplayName("ALL_ALLIES：全体友军（含自己）各回 25")
    void allAlliesHealEveryone() {
        BattleUnit caster = caster(skill("s4", SkillShape.ALL_ALLIES, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.HEAL, 0.25f, null, null)));
        BattleUnit ally = u(3, Side.PLAYER, 3, 4, caster.getSkill(), 10f, 1);
        caster.modifyHp(-40f);
        ally.modifyHp(-20f);
        BattleState state = state(caster, ally);
        assertThat(EXECUTOR.cast(state, caster)).isTrue();
        assertThat(caster.getCurrentHp()).isCloseTo(85f, within(1e-4f));
        assertThat(ally.getCurrentHp()).isCloseTo(100f, within(1e-4f)); // -20+25 cap 100
    }

    @Test
    @DisplayName("ALL_ENEMIES：命中全部敌方")
    void allEnemiesHit() {
        BattleUnit caster = caster(skill("s5", SkillShape.ALL_ENEMIES, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 1f, null, null)));
        BattleUnit e1 = enemy(2, 0, 0);
        BattleUnit e2 = enemy(3, 5, 2);
        caster.setTargetId(2);
        BattleState state = state(caster, e1, e2);
        assertThat(EXECUTOR.cast(state, caster)).isTrue();
        assertThat(e1.getCurrentHp()).isCloseTo(90f, within(1e-6f));
        assertThat(e2.getCurrentHp()).isCloseTo(90f, within(1e-6f));
    }

    @Test
    @DisplayName("AOE_1 = 落点 + 4 邻（十字 5 格），只中区域内存活敌方")
    void aoe1HitsCrossCells() {
        BattleUnit caster = caster(skill("s6", SkillShape.AOE_1, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 1f, null, null)));
        BattleUnit mainTarget = enemy(2, 2, 2);
        BattleUnit adjacent = enemy(3, 2, 1);   // 上邻
        BattleUnit diagonal = enemy(4, 1, 1);   // 对角：不在 AOE_1
        BattleUnit outside = enemy(5, 5, 0);    // 远处
        caster.setTargetId(2);
        BattleState state = state(caster, mainTarget, adjacent, diagonal, outside);
        assertThat(EXECUTOR.cast(state, caster)).isTrue();
        assertThat(mainTarget.getCurrentHp()).isCloseTo(90f, within(1e-6f));
        assertThat(adjacent.getCurrentHp()).isCloseTo(90f, within(1e-6f));
        assertThat(diagonal.getCurrentHp()).isEqualTo(100f);
        assertThat(outside.getCurrentHp()).isEqualTo(100f);
    }

    @Test
    @DisplayName("AOE_2 = 13 格菱形（曼哈顿 ≤2，含边界格）")
    void aoe2HitsDiamond13() {
        BattleUnit caster = caster(skill("s7", SkillShape.AOE_2, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 1f, null, null)));
        BattleUnit mainTarget = enemy(2, 2, 2);
        BattleUnit boundary = enemy(3, 2, 0);   // |0|+|2| = 2 边界内
        BattleUnit edgeFar = enemy(4, 4, 2);    // |2|+|0| = 2 边界内
        BattleUnit outside = enemy(5, 0, 0);    // |2|+|2| = 4 界外
        caster.setTargetId(2);
        BattleState state = state(caster, mainTarget, boundary, edgeFar, outside);
        assertThat(EXECUTOR.cast(state, caster)).isTrue();
        assertThat(mainTarget.getCurrentHp()).isCloseTo(90f, within(1e-6f));
        assertThat(boundary.getCurrentHp()).isCloseTo(90f, within(1e-6f));
        assertThat(edgeFar.getCurrentHp()).isCloseTo(90f, within(1e-6f));
        assertThat(outside.getCurrentHp()).isEqualTo(100f);
    }

    @Test
    @DisplayName("LOWEST_ALLY 全满延后（口径 #18）：能量保留 100，友军掉血后重试成功")
    void lowestAllyDefersWhenAllFull() {
        BattleUnit caster = caster(skill("s8", SkillShape.LOWEST_ALLY, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.HEAL, 0.25f, null, null)));
        caster.setEnergy(100f);
        BattleUnit ally = u(3, Side.PLAYER, 3, 4, caster.getSkill(), 10f, 1);
        BattleState state = state(caster, ally);
        assertThat(EXECUTOR.cast(state, caster)).isFalse(); // 全满 → 延后
        assertThat(caster.getEnergy()).isEqualTo(100f);     // 能量保留

        ally.modifyHp(-30f);
        assertThat(EXECUTOR.cast(state, caster)).isTrue();  // 重试成功
        assertThat(caster.getEnergy()).isEqualTo(0f);       // 施放清零
        assertThat(ally.getCurrentHp()).isCloseTo(95f, within(1e-4f)); // 70 + 25
    }

    @Test
    @DisplayName("SINGLE_TARGET 锁定失效（目标已清扫）→ 延后返回 false")
    void singleTargetDefersWhenInvalid() {
        BattleUnit caster = caster(skill("s9", SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 2f, null, null)));
        BattleUnit dead = enemy(2, 2, 2);
        dead.markCleaned();
        caster.setTargetId(2);
        assertThat(EXECUTOR.cast(state(caster, dead), caster)).isFalse();
        assertThat(caster.getEnergy()).isEqualTo(0f); // cast 不清能量（未施放）
    }

    // —— ② 载体 ——

    @Test
    @DisplayName("LINE 弹道抛 UnsupportedOperationException（Q2 裁决：本期不做）")
    void lineDeliveryThrows() {
        BattleUnit caster = caster(skill("s10", SkillShape.SINGLE_TARGET, Delivery.LINE,
                effect(SkillEffectType.DAMAGE, 2f, null, null)));
        BattleUnit target = enemy(2, 2, 2);
        caster.setTargetId(2);
        assertThatThrownBy(() -> EXECUTOR.cast(state(caster, target), caster))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("LINE");
    }

    @Test
    @DisplayName("ally 形状遇 HOMING 抛 IllegalStateException（防御：增益弹无目标语义）")
    void allyShapeWithHomingThrows() {
        BattleUnit caster = caster(skill("s11", SkillShape.ALL_ALLIES, Delivery.HOMING,
                effect(SkillEffectType.HEAL, 0.25f, null, null)));
        assertThatThrownBy(() -> EXECUTOR.cast(state(caster), caster))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("HOMING 冻结载荷：cast 产弹不立即结算，快照与缩放因子入弹（口径 #13）")
    void homingFreezesPayload() {
        BattleUnit caster = caster(skill("s12", SkillShape.SINGLE_TARGET, Delivery.HOMING,
                effect(SkillEffectType.DAMAGE, 2.5f, null, null)));
        BattleUnit target = enemy(2, 2, 2);
        caster.setTargetId(2);
        BattleState state = state(caster, target);
        assertThat(EXECUTOR.cast(state, caster)).isTrue();
        assertThat(state.getProjectiles()).hasSize(1);
        assertThat(target.getCurrentHp()).isEqualTo(100f); // 未立即结算
        assertThat(state.getProjectiles().get(0).getAttackSnapshot()).isEqualTo(10f);
        assertThat(state.getProjectiles().get(0).getScaleFactor()).isEqualTo(1f); // 1 星 × sp0
        assertThat(caster.getEnergy()).isEqualTo(0f);
    }

    // —— ③ 效果应用 ——

    @Test
    @DisplayName("DAMAGE 走护甲公式且带 skillId（事件可追溯）")
    void damageGoesThroughPipelineWithSkillId() {
        BattleUnit caster = caster(skill("s13", SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 2f, null, null)));
        BattleUnit heavy = new BattleUnit(3, tpl("heavy"), 1, Side.ENEMY, caster.getSkill(),
                new BattleStats(100f, 10f, 100f, 1f, 1f, 1f, 0f, 100f, 0f));
        heavy.setPosition(3, 2);
        caster.setTargetId(3);
        BattleState state = state(caster, heavy);
        assertThat(EXECUTOR.cast(state, caster)).isTrue();
        assertThat(heavy.getCurrentHp()).isCloseTo(90f, within(1e-4f)); // 10×2×100/200 = 10
        assertThat(state.getEvents().get(0).getType()).isEqualTo(CombatEvent.Type.CAST);   // 施放先于效果
        assertThat(state.getEvents().get(1).getType()).isEqualTo(CombatEvent.Type.HIT);
        assertThat(state.getEvents().get(1).getSkillId()).isEqualTo("s13");
    }

    @Test
    @DisplayName("HEAL = maxHp × value × 星级 × (1+skillPower/100)：2 星 + sp20 → 100×0.25×1.5×1.2=45")
    void healFormulaWithStarAndSkillPower() {
        SkillData heal = skill("s14", SkillShape.LOWEST_ALLY, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.HEAL, 0.25f, null, null));
        BattleUnit casterSp = new BattleUnit(9, tpl("sp"), 2, Side.PLAYER, heal,
                new BattleStats(100f, 10f, 0f, 1f, 1f, 1f, 0f, 100f, 20f));
        casterSp.setPosition(2, 4);
        casterSp.modifyHp(-80f); // 20/100
        BattleState state = state(casterSp);
        assertThat(EXECUTOR.cast(state, casterSp)).isTrue();
        assertThat(casterSp.getCurrentHp()).isCloseTo(65f, within(1e-4f)); // 20+45
    }

    @Test
    @DisplayName("SHIELD = maxHp × value × 星级 × (1+skillPower/100)：2 星 0.3 → 45 吸收")
    void shieldFormulaWithStarScale() {
        SkillData shield = skill("s15", SkillShape.SELF, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.SHIELD, 0.3f, null, null));
        BattleUnit twoStar = u(1, Side.PLAYER, 2, 4, shield, 10f, 2);
        BattleState state = state(twoStar);
        assertThat(EXECUTOR.cast(state, twoStar)).isTrue();
        assertThat(twoStar.getStatuses()).hasSize(1);
        assertThat(twoStar.getStatuses().get(0).getType()).isEqualTo(StatusType.SHIELD);
        assertThat(twoStar.getStatuses().get(0).getPower()).isCloseTo(45f, within(1e-4f));
    }

    @Test
    @DisplayName("APPLY_STATUS 时长/强度不随星级（GDD §4.3）：2 星 ATK_UP 30/5s 仍为 30/5s")
    void applyStatusNotScaledByStar() {
        SkillData buff = skill("s16", SkillShape.SELF, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.APPLY_STATUS, 30f, StatusType.ATK_UP, 5f));
        BattleUnit twoStar = u(1, Side.PLAYER, 2, 4, buff, 10f, 2);
        BattleState state = state(twoStar);
        assertThat(EXECUTOR.cast(state, twoStar)).isTrue();
        ActiveStatus status = twoStar.getStatuses().get(0);
        assertThat(status.getPower()).isEqualTo(30f);
        assertThat(status.getRemainingTime()).isEqualTo(5f);
    }

    @Test
    @DisplayName("DOT power = 施放时攻击快照 × value（星级与 skillPower 不缩放，口径 #10）")
    void dotPowerFromAttackSnapshot() {
        SkillData dot = skill("s17", SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.APPLY_STATUS, 0.1f, StatusType.POISON, 6f));
        BattleUnit caster = caster(dot);
        BattleUnit target = enemy(2, 2, 2);
        caster.setTargetId(2);
        BattleState state = state(caster, target);
        assertThat(EXECUTOR.cast(state, caster)).isTrue();
        ActiveStatus poison = target.getStatuses().get(0);
        assertThat(poison.getType()).isEqualTo(StatusType.POISON);
        assertThat(poison.getPower()).isCloseTo(1f, within(1e-6f)); // 10 × 0.1
        assertThat(poison.getRemainingTime()).isEqualTo(6f);
    }

    @Test
    @DisplayName("Cast 事件：skillId + 主目标口径（SELF/增益类 = caster 自身）")
    void castEventFields() {
        BattleUnit caster = caster(skill("s18", SkillShape.SELF, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.APPLY_STATUS, 30f, StatusType.ATK_UP, 5f)));
        BattleState state = state(caster);
        EXECUTOR.cast(state, caster);
        CombatEvent cast = state.getEvents().get(0);
        assertThat(cast.getType()).isEqualTo(CombatEvent.Type.CAST);
        assertThat(cast.getSkillId()).isEqualTo("s18");
        assertThat(cast.getSourceId()).isEqualTo(1);
        assertThat(cast.getTargetId()).isEqualTo(1); // SELF 主目标 = caster
    }

    @Test
    @DisplayName("控制期不施放：STUN 在身 cast 返回 false")
    void stunnedCasterCannotCast() {
        BattleUnit caster = caster(skill("s19", SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 2f, null, null)));
        caster.addStatus(new ActiveStatus(StatusType.STUN, 9, 0f, 1f));
        assertThat(EXECUTOR.cast(state(caster), caster)).isFalse();
    }

    @Test
    @DisplayName("applyAtImpact：HOMING 技能弹到达后按冻结快照结算")
    void applyAtImpactUsesFrozenSnapshot() {
        SkillData homing = skill("s20", SkillShape.SINGLE_TARGET, Delivery.HOMING,
                effect(SkillEffectType.DAMAGE, 2f, null, null));
        BattleUnit caster = caster(homing);
        BattleUnit target = enemy(2, 2, 2);
        caster.setTargetId(2);
        BattleState state = state(caster, target);
        EXECUTOR.applyAtImpact(state, caster, target, homing, 10f, 1f);
        assertThat(target.getCurrentHp()).isCloseTo(80f, within(1e-6f)); // 快照 10 × 2
        assertThat(state.getEvents().get(0).getSkillId()).isEqualTo("s20");
    }
}
