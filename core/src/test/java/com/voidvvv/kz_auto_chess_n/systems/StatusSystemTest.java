package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.state;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.tpl;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 状态系统测试（battle §七统一框架 + 口径 #9/#10/#11）：
 * 同 type 不叠加（duration 取长、power 取大）；DOT/REGEN 1s 心跳；SHIELD 吸收条目。
 */
class StatusSystemTest {

    private static final StatusSystem SYSTEM = new StatusSystem(
            new DamagePipeline(new CastTrigger() {
                @Override
                public void tryCast(BattleState state, BattleUnit caster) {
                    // 测试用空触发器
                }
            }));

    private static BattleUnit target() {
        return unit(2, Side.ENEMY, tpl("t"), 2, 2);
    }

    /** 以传入单位直接建状态（全部经夹具落格） */
    private static BattleState withTarget(BattleUnit... units) {
        return state(units);
    }

    @Test
    @DisplayName("同 type 刷新：duration 取更长、power 取更大（口径 #11）")
    void sameTypeRefreshTakesLongerAndLarger() {
        BattleUnit target = target();
        BattleState state = withTarget(target);
        SYSTEM.apply(state, target, StatusType.ATK_UP, 30f, 5f, 1);
        SYSTEM.apply(state, target, StatusType.ATK_UP, 20f, 8f, 1);
        assertThat(target.getStatuses()).hasSize(1);
        ActiveStatus merged = target.getStatuses().get(0);
        assertThat(merged.getPower()).isEqualTo(30f);
        assertThat(merged.getRemainingTime()).isEqualTo(8f);
    }

    @Test
    @DisplayName("不同 type 独立共存")
    void differentTypesCoexist() {
        BattleUnit target = target();
        BattleState state = withTarget(target);
        SYSTEM.apply(state, target, StatusType.ATK_UP, 30f, 5f, 1);
        SYSTEM.apply(state, target, StatusType.SLOW, 40f, 3f, 1);
        assertThat(target.getStatuses()).hasSize(2);
    }

    @Test
    @DisplayName("属性类挂载即生效：ATK_UP 30 → 攻击 10×1.3")
    void attributeStatusTakesEffectImmediately() {
        BattleUnit target = target();
        BattleState state = withTarget(target);
        SYSTEM.apply(state, target, StatusType.ATK_UP, 30f, 5f, 1);
        assertThat(target.getEffective(StatKey.ATTACK)).isCloseTo(13f, within(1e-6f));
    }

    @Test
    @DisplayName("到期移除恢复属性：时长走完后 ATK_UP 失效")
    void expiryRemovesAndRestoresStats() {
        BattleUnit target = target();
        BattleState state = withTarget(target);
        SYSTEM.apply(state, target, StatusType.ATK_UP, 30f, 2f, 1);
        assertThat(target.getStatuses()).hasSize(1);

        for (int i = 0; i < Math.round(2f / GameBalance.LOGIC_STEP); i++) {
            SYSTEM.tickStatuses(state, GameBalance.LOGIC_STEP);
        }
        assertThat(target.getStatuses()).isEmpty();
        assertThat(target.getEffective(StatKey.ATTACK)).isEqualTo(10f);
    }

    @Test
    @DisplayName("DOT 首跳在满 1s（非立即）：0~59 tick 无伤害，第 60 tick 一跳")
    void dotFirstTickAfterOneSecond() {
        BattleUnit target = target();
        BattleState state = withTarget(target);
        SYSTEM.apply(state, target, StatusType.POISON, 8f, 6f, 1);
        for (int i = 0; i < 59; i++) {
            SYSTEM.tickStatuses(state, GameBalance.LOGIC_STEP);
        }
        assertThat(target.getCurrentHp()).isEqualTo(100f); // 未满 1s 不跳
        SYSTEM.tickStatuses(state, GameBalance.LOGIC_STEP); // 第 60 tick
        assertThat(target.getCurrentHp()).isCloseTo(92f, within(1e-6f));
    }

    @Test
    @DisplayName("DOT 每秒一跳、无回能；duration=6s 共 6 跳")
    void dotTicksEverySecondWithoutEnergy() {
        BattleUnit target = target();
        BattleState state = withTarget(target);
        SYSTEM.apply(state, target, StatusType.BLEED, 10f, 6f, 1);
        int ticks = Math.round(6f / GameBalance.LOGIC_STEP);
        for (int i = 0; i < ticks; i++) {
            SYSTEM.tickStatuses(state, GameBalance.LOGIC_STEP);
        }
        assertThat(target.getCurrentHp()).isCloseTo(40f, within(1e-4f)); // 100 − 6×10
        assertThat(target.getEnergy()).isEqualTo(0f); // DOT 不触发回能（口径 #10）
        assertThat(target.getStatuses()).isEmpty();   // 到期移除
    }

    @Test
    @DisplayName("DOT 可致死（真伤路径）")
    void dotCanKill() {
        BattleUnit target = target();
        BattleState state = withTarget(target);
        SYSTEM.apply(state, target, StatusType.POISON, 30f, 10f, 1);
        for (int i = 0; i < Math.round(4.5f / GameBalance.LOGIC_STEP); i++) { // 4 跳 ×30 = 120 ≥ 100
            SYSTEM.tickStatuses(state, GameBalance.LOGIC_STEP);
        }
        assertThat(target.getCurrentHp()).isEqualTo(0f);
    }

    @Test
    @DisplayName("REGEN 按比例回：power 0.1 = 10%/跳，1s 一跳")
    void regenHealsByRatio() {
        BattleUnit target = target();
        target.modifyHp(-50f);
        BattleState state = withTarget(target);
        SYSTEM.apply(state, target, StatusType.REGEN, 0.1f, 3f, 1);
        for (int i = 0; i < Math.round(2f / GameBalance.LOGIC_STEP); i++) {
            SYSTEM.tickStatuses(state, GameBalance.LOGIC_STEP);
        }
        assertThat(target.getCurrentHp()).isCloseTo(70f, within(1e-4f)); // 50 + 2×10
    }

    @Test
    @DisplayName("SHIELD 吸收条目：同类刷新取大、被消耗至零移除并打脏标记")
    void shieldEntryMergeAndConsume() {
        BattleUnit target = target();
        BattleState state = withTarget(target);
        SYSTEM.apply(state, target, StatusType.SHIELD, 30f, Float.POSITIVE_INFINITY, 1);
        SYSTEM.apply(state, target, StatusType.SHIELD, 50f, Float.POSITIVE_INFINITY, 1);
        assertThat(target.getStatuses()).hasSize(1);
        assertThat(target.getStatuses().get(0).getPower()).isEqualTo(50f);

        // 被消耗至零 → 移除（走 DamagePipeline 扣盾路径；applyTrueDamage 不触碰 state 记账）
        DamagePipeline pipeline = new DamagePipeline(noOpTrigger());
        BattleUnit attacker = unit(1, Side.PLAYER, tpl("a"), 2, 4);
        pipeline.applyTrueDamage(state, attacker, target, 50f);
        assertThat(target.getStatuses()).isEmpty();
    }

    @Test
    @DisplayName("STUN 挂载即控制；到期解除")
    void stunControlsAndExpires() {
        BattleUnit target = target();
        BattleState state = withTarget(target);
        SYSTEM.apply(state, target, StatusType.STUN, 0f, 1f, 1);
        assertThat(target.hasControl()).isTrue();
        for (int i = 0; i < Math.round(1f / GameBalance.LOGIC_STEP); i++) {
            SYSTEM.tickStatuses(state, GameBalance.LOGIC_STEP);
        }
        assertThat(target.hasControl()).isFalse();
    }

    @Test
    @DisplayName("STATUS_APPLIED 事件：amount=power、amount2=duration；SHIELD 发 SHIELDED 不发 STATUS_APPLIED")
    void statusAppliedEventFields() {
        BattleUnit target = target();
        BattleState state = withTarget(target);
        SYSTEM.apply(state, target, StatusType.ATK_UP, 30f, 5f, 7);
        SYSTEM.apply(state, target, StatusType.SHIELD, 40f, Float.POSITIVE_INFINITY, 7);

        assertThat(state.getEvents()).hasSize(2);
        CombatEvent status = state.getEvents().get(0);
        assertThat(status.getType()).isEqualTo(CombatEvent.Type.STATUS_APPLIED);
        assertThat(status.getStatusType()).isEqualTo(StatusType.ATK_UP);
        assertThat(status.getAmount()).isEqualTo(30f);
        assertThat(status.getAmount2()).isEqualTo(5f);
        assertThat(status.getSourceId()).isEqualTo(7);
        assertThat(status.getTargetId()).isEqualTo(2);

        CombatEvent shield = state.getEvents().get(1);
        assertThat(shield.getType()).isEqualTo(CombatEvent.Type.SHIELDED);
        assertThat(shield.getAmount()).isEqualTo(40f);
    }

    private static CastTrigger noOpTrigger() {
        return new CastTrigger() {
            @Override
            public void tryCast(BattleState state, BattleUnit caster) {
            }
        };
    }
}
