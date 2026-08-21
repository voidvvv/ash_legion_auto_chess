package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.render.board.UnitAnimState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UnitAnimState 测试：优先级（Death 锁定 > Attack/Cast > Walk > Idle）、HitFlash 叠加独立计时、
 * 帧时长常量（render §5.1/§7.3，口径 #13）。
 */
class UnitAnimStateTest {

    @Test
    @DisplayName("初始为 Idle")
    void startsIdle() {
        assertThat(new UnitAnimState().current()).isEqualTo(UnitAnimState.Anim.IDLE);
    }

    @Test
    @DisplayName("Death 锁定：任何事件不可打断")
    void deathLocksOutAllEvents() {
        UnitAnimState state = new UnitAnimState();
        state.onEvent(CombatEvent.Type.UNIT_DIED);
        state.onEvent(CombatEvent.Type.ATTACK_LAUNCHED);
        state.onEvent(CombatEvent.Type.CAST);
        state.onEvent(CombatEvent.Type.HIT);
        state.update(10f);
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.DEATH);
    }

    @Test
    @DisplayName("Attack 打断 Idle/Walk；Cast 打断 Idle/Walk")
    void attackAndCastInterruptIdleAndWalk() {
        UnitAnimState state = new UnitAnimState();
        state.onEvent(CombatEvent.Type.ATTACK_LAUNCHED);
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.ATTACK);
        state.update(1f); // 播完回落 Idle
        state.setMoving(true);
        state.update(0f);
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.WALK);
        state.onEvent(CombatEvent.Type.CAST);
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.CAST);
    }

    @Test
    @DisplayName("Idle↔Walk 随移动状态切换")
    void idleWalkToggleByMoving() {
        UnitAnimState state = new UnitAnimState();
        state.setMoving(true);
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.WALK);
        state.setMoving(false);
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.IDLE);
    }

    @Test
    @DisplayName("同类型动画重触发取最新（计时归零）")
    void sameAnimRetriggerRestarts() {
        UnitAnimState state = new UnitAnimState();
        state.onEvent(CombatEvent.Type.ATTACK_LAUNCHED);
        state.update(0.2f); // attack 3×0.1=0.3s，走到 0.2
        assertThat(state.animElapsed()).isCloseTo(0.2f, org.assertj.core.data.Offset.offset(1e-6f));
        state.onEvent(CombatEvent.Type.ATTACK_LAUNCHED); // 重触发
        assertThat(state.animElapsed()).isZero();
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.ATTACK);
    }

    @Test
    @DisplayName("动画播完回落：Attack 结束回 Idle（未在移动）")
    void fallsBackToIdleAfterAttack() {
        UnitAnimState state = new UnitAnimState();
        state.onEvent(CombatEvent.Type.ATTACK_LAUNCHED);
        state.update(0.29f);
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.ATTACK);
        state.update(0.02f); // 累计 0.31 ≥ 0.3
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.IDLE);
    }

    @Test
    @DisplayName("动画播完回落：Attack 结束仍在移动则回 Walk")
    void fallsBackToWalkAfterAttackWhileMoving() {
        UnitAnimState state = new UnitAnimState();
        state.setMoving(true);
        state.onEvent(CombatEvent.Type.ATTACK_LAUNCHED);
        state.update(0.4f);
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.WALK);
    }

    @Test
    @DisplayName("HitFlash 独立叠加：不占状态位，0.1s 线性衰减，可重复触发刷新")
    void hitFlashIndependentAndDecays() {
        UnitAnimState state = new UnitAnimState();
        assertThat(state.hitFlashRatio()).isZero(); // 未触发
        state.triggerHitFlash();
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.IDLE); // 不占状态位
        assertThat(state.hitFlashRatio()).isEqualTo(1f);
        state.update(0.05f);
        assertThat(state.hitFlashRatio()).isCloseTo(0.5f, org.assertj.core.data.Offset.offset(1e-6f));
        state.update(0.05f);
        assertThat(state.hitFlashRatio()).isZero(); // 衰减完毕
        state.triggerHitFlash(); // 再次触发刷新满
        assertThat(state.hitFlashRatio()).isEqualTo(1f);
    }

    @Test
    @DisplayName("Death 淡出计时：0.5s 线性走到 1 后保持（口径 #13 占位表现）")
    void deathFadeTimer() {
        UnitAnimState state = new UnitAnimState();
        state.onEvent(CombatEvent.Type.UNIT_DIED);
        assertThat(state.deathFadeRatio()).isZero();
        state.update(0.25f);
        assertThat(state.deathFadeRatio()).isCloseTo(0.5f, org.assertj.core.data.Offset.offset(1e-6f));
        state.update(0.5f);
        assertThat(state.deathFadeRatio()).isEqualTo(1f); // 到顶保持
    }

    @Test
    @DisplayName("帧时长常量与 render §7.3 一致：idle 0.4 / walk 0.2 / attack 0.1 / death 0.15（秒/帧）")
    void frameDurationConstants() {
        assertThat(UnitAnimState.FRAME_SECONDS_IDLE).isEqualTo(0.4f);
        assertThat(UnitAnimState.FRAME_SECONDS_WALK).isEqualTo(0.2f);
        assertThat(UnitAnimState.FRAME_SECONDS_ATTACK).isEqualTo(0.1f);
        assertThat(UnitAnimState.FRAME_SECONDS_DEATH).isEqualTo(0.15f);
    }
}
