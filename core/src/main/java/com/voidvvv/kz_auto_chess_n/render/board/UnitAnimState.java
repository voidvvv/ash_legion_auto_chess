package com.voidvvv.kz_auto_chess_n.render.board;

import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;

/**
 * 单位动画 FSM（render §5.1；纯 Java 可测，零 Gdx）。
 *
 * <p>优先级：Death 锁定 &gt; Attack/Cast &gt; Walk &gt; Idle；HitFlash 为叠加层独立计时，
 * 不占状态位。攻击摆动与受击抖动（attack_feedback FP1/FP2）同为叠加层：独立计时、
 * 重复触发刷新满、施法不抑制、死亡立即清零且死后忽略触发。
 * 事件 → 状态的"归属哪个单位"路由（sourceId/targetId）由渲染层完成，
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

    // —— 攻击摆动 / 受击抖动（attack_feedback FP1/FP2；缺省值待试玩评审定稿，GDD §8） ——
    /** 摆动模式（裁决 C）：ROTATE = 底部中心轴小幅旋转（主模式，像素规则第三例外）；TRANSLATE = 沿朝向整数像素平移备选 */
    public enum AttackSwingMode { ROTATE, TRANSLATE }
    /** 模式切换常量（试玩评审对比后定稿，届时删常量定死单模式） */
    public static final AttackSwingMode ATTACK_SWING_MODE = AttackSwingMode.ROTATE;
    /** 旋转摆幅（度；上限 8°——render §八#3 第三例外） */
    public static final float ATTACK_SWING_DEGREES = 6f;
    /** 平移备选摆幅（像素，整数吸附） */
    public static final float ATTACK_SWING_PIXELS = 2f;
    /** 摆动全程时长（秒；恰好 1 个来回、幅值线性衰减） */
    public static final float ATTACK_SWING_SECONDS = 0.25f;
    /** 受击抖动幅度（像素，整数吸附） */
    public static final float HIT_SHAKE_PIXELS = 2f;
    /** 受击抖动时长（秒） */
    public static final float HIT_SHAKE_SECONDS = 0.15f;
    /** 受击抖动往复周期（秒；0.15/0.07 ≈ 2 个来回） */
    public static final float HIT_SHAKE_PERIOD_SECONDS = 0.07f;

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
    private float swingTimer;   // 攻击摆动剩余秒数（叠加层，attack_feedback FP1）
    private float shakeTimer;   // 受击抖动剩余秒数（叠加层，attack_feedback FP2）
    private int shakeAxis = 1;  // 受击抖动水平轴 ±1（渲染层定，含确定性回退；0 归一 +1）

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
                swingTimer = 0f; // 死亡立即清零叠加反馈（attack_feedback FP3：不与缩放淡出叠加）
                shakeTimer = 0f;
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
        if (swingTimer > 0f) {
            swingTimer = Math.max(0f, swingTimer - dt);
        }
        if (shakeTimer > 0f) {
            shakeTimer = Math.max(0f, shakeTimer - dt);
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

    // —— 攻击摆动 / 受击抖动叠加层（attack_feedback FP1/FP2/FP3） ——

    /** 攻击摆动触发（叠加层，重复触发刷新满；死亡态忽略——出手同拍被反杀不残留） */
    public void triggerAttackSwing() {
        if (current == Anim.DEATH) {
            return;
        }
        swingTimer = ATTACK_SWING_SECONDS;
    }

    /** 受击抖动触发（叠加层，重复触发刷新满；axis = 水平轴 ±1，0 归一 +1；死亡态忽略） */
    public void triggerHitShake(int axis) {
        if (current == Anim.DEATH) {
            return;
        }
        shakeAxis = axis < 0 ? -1 : 1;
        shakeTimer = HIT_SHAKE_SECONDS;
    }

    /** 旋转模式摆角（度）：A·sin(2πt/T)·(1−t/T)，t ∈ [0,T]——1 个来回、幅值线性衰减
     *  （t=T/4 前倾峰值 +0.75A、t=3T/4 回摆 −0.25A、终了归零）。正 = 前倾，
     *  屏幕角符号由 UnitView 按敌我朝向翻转（y 向上坐标系正角 = 逆时针）。未触发为 0。 */
    public float attackSwingDegrees() {
        if (swingTimer <= 0f) {
            return 0f;
        }
        float phase = (ATTACK_SWING_SECONDS - swingTimer) / ATTACK_SWING_SECONDS;
        return ATTACK_SWING_DEGREES * (float) Math.sin(Math.PI * 2.0 * phase) * (1f - phase);
    }

    /** 平移备选模式位移（整数像素阶梯）：round(A'·sin(2πt/T)) → +2→0→−2→0（1 来回）。
     *  正 = 前倾（沿自身朝向），敌方由 UnitView 取反。未触发为 0。 */
    public int attackSwingDx() {
        if (swingTimer <= 0f) {
            return 0;
        }
        float phase = (ATTACK_SWING_SECONDS - swingTimer) / ATTACK_SWING_SECONDS;
        return Math.round(ATTACK_SWING_PIXELS * (float) Math.sin(Math.PI * 2.0 * phase));
    }

    /** 受击抖动水平位移（整数像素）：axis·round(A·sin(2πt/P)·(1−t/D))——约 2 个来回线性衰减。
     *  未触发为 0。 */
    public int hitShakeDx() {
        if (shakeTimer <= 0f) {
            return 0;
        }
        float t = HIT_SHAKE_SECONDS - shakeTimer;
        float envelope = 1f - t / HIT_SHAKE_SECONDS;
        return shakeAxis * Math.round(HIT_SHAKE_PIXELS
                * (float) Math.sin(Math.PI * 2.0 * t / HIT_SHAKE_PERIOD_SECONDS) * envelope);
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
