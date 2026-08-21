package com.voidvvv.kz_auto_chess_n.render.board;

import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;

/**
 * 单位动画 FSM（render §5.1；纯 Java 可测，零 Gdx）。
 *
 * <p>优先级：Death 锁定 &gt; Attack/Cast &gt; Walk &gt; Idle；HitFlash 为叠加层独立计时，
 * 不占状态位。事件 → 状态的"归属哪个单位"路由（sourceId/targetId）由渲染层完成，
 * 本类只按事件类型转移。死亡淡出 0.5s（口径 #13 占位表现）。
 */
public final class UnitAnimState {

    public enum Anim { IDLE, WALK, ATTACK, CAST, DEATH }

    // 帧时长常量（秒/帧，render §7.3）；cast 与 attack 同步长（计划未单列，实现口径）
    public static final float FRAME_SECONDS_IDLE = 0.4f;
    public static final float FRAME_SECONDS_WALK = 0.2f;
    public static final float FRAME_SECONDS_ATTACK = 0.1f;
    public static final float FRAME_SECONDS_DEATH = 0.15f;
    public static final float FRAME_SECONDS_CAST = 0.1f;
    /** 受击白闪时长（叠加层） */
    public static final float HIT_FLASH_SECONDS = 0.1f;
    /** 死亡缩放淡出时长（口径 #13） */
    public static final float DEATH_FADE_SECONDS = 0.5f;

    private static final int FRAMES_IDLE = 2;
    private static final int FRAMES_WALK = 2;
    private static final int FRAMES_ATTACK = 3;
    private static final int FRAMES_CAST = 2;
    private static final int FRAMES_DEATH = 3;

    private Anim current = Anim.IDLE;
    private float animElapsed;
    private boolean moving;
    private float hitFlashTimer;
    private float deathElapsed;

    public Anim current() {
        return current;
    }

    /** 当前动画已播秒数（测试与调试观察） */
    public float animElapsed() {
        return animElapsed;
    }

    /** 事件驱动状态转移（Death 锁定不可打断） */
    public void onEvent(CombatEvent.Type type) {
        if (current == Anim.DEATH) {
            return;
        }
        switch (type) {
            case ATTACK_LAUNCHED:
            case HIT: // 近战即时：伤害与出手同拍
                restart(Anim.ATTACK);
                break;
            case CAST:
                restart(Anim.CAST);
                break;
            case UNIT_DIED:
                current = Anim.DEATH; // 锁定
                animElapsed = 0f;
                deathElapsed = 0f;
                break;
            default:
                break; // 其余事件不转移状态
        }
    }

    /** 移动状态提示（Idle↔Walk 互切；不打断 Attack/Cast/Death） */
    public void setMoving(boolean moving) {
        this.moving = moving;
        if (current == Anim.IDLE && moving) {
            restart(Anim.WALK);
        } else if (current == Anim.WALK && !moving) {
            restart(Anim.IDLE);
        }
    }

    /** 帧推进：动画播完回落 Idle/Walk；HitFlash 与死亡淡出独立计时 */
    public void update(float dt) {
        if (hitFlashTimer > 0f) {
            hitFlashTimer = Math.max(0f, hitFlashTimer - dt);
        }
        if (current == Anim.DEATH) {
            deathElapsed = Math.min(DEATH_FADE_SECONDS, deathElapsed + dt);
            animElapsed += dt; // death 动画帧推进（播完保持末帧）
            return;
        }
        animElapsed += dt;
        if (animElapsed >= totalDuration(current)) {
            restart(moving ? Anim.WALK : Anim.IDLE);
        }
    }

    /** 受击白闪触发（叠加层，重复触发刷新满） */
    public void triggerHitFlash() {
        hitFlashTimer = HIT_FLASH_SECONDS;
    }

    /** 白闪强度 0~1（线性衰减；未触发为 0） */
    public float hitFlashRatio() {
        return hitFlashTimer / HIT_FLASH_SECONDS;
    }

    /** 死亡淡出进度 0~1（到顶保持；非死亡态为 0） */
    public float deathFadeRatio() {
        if (current != Anim.DEATH) {
            return 0f;
        }
        return deathElapsed / DEATH_FADE_SECONDS;
    }

    /** 当前动画帧索引（0 起，播完钉末帧） */
    public int frameIndex() {
        int frames = frameCount(current);
        float per = frameSeconds(current);
        int index = (int) (animElapsed / per);
        return Math.min(frames - 1, index);
    }

    private void restart(Anim anim) {
        current = anim;
        animElapsed = 0f;
    }

    private static float totalDuration(Anim anim) {
        return frameCount(anim) * frameSeconds(anim);
    }

    private static int frameCount(Anim anim) {
        switch (anim) {
            case IDLE: return FRAMES_IDLE;
            case WALK: return FRAMES_WALK;
            case ATTACK: return FRAMES_ATTACK;
            case CAST: return FRAMES_CAST;
            case DEATH: return FRAMES_DEATH;
            default: return FRAMES_IDLE;
        }
    }

    private static float frameSeconds(Anim anim) {
        switch (anim) {
            case IDLE: return FRAME_SECONDS_IDLE;
            case WALK: return FRAME_SECONDS_WALK;
            case ATTACK: return FRAME_SECONDS_ATTACK;
            case CAST: return FRAME_SECONDS_CAST;
            case DEATH: return FRAME_SECONDS_DEATH;
            default: return FRAME_SECONDS_IDLE;
        }
    }
}
