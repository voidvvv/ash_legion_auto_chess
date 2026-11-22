package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.entities.GamePhase;

/**
 * 抽屉开合状态机与动画进度（UI 抽屉化批1·Q2 / Q6；纯 Java 可测，零 Scene2d、零 GL）。
 *
 * <p>进度 progress ∈ [0,1]：0 = 收起、1 = 展开。动效（计划 §3.3）：单程 {@link #OPEN_SECONDS} 秒、
 * 先快后慢（ease-out，开/合两向皆然——缓动作用于动画自身的时间参数而非开合度）；
 * 位移取整由 {@link DrawerGeometry#bodyX(float)} 承担（渲染侧逐帧 Math.round）。
 *
 * <p>冻结感知：调用方以 {@code frozen ? 0 : delta} 喂 {@link #update}（dt ≤ 0 不推进——暂停/弹窗期动画停走）。
 * 转场窗口内拒绝开合（§3.8）：{@code requestOpen/requestClose/requestToggle} 的 transitionBlocked 为真时
 * 返回 false 且状态不变；{@link #collapse} 例外——它是「随开战转场左滑退场」的强制收起，无视封禁位。
 *
 * <p>相位联动（Q6）：{@link #syncPhase} 只在相位<b>跳变</b>时强制收起（开战随转场退场）；战斗期内允许只读展开、
 * 同相位重复同步不会再次收起；战毕回备战不自动恢复展开（Q2）。
 */
public final class DrawerController {

    /** Q2：默认收起（常量可切——置 true 即默认展开） */
    public static final boolean DEFAULT_OPEN = false;
    /** 单程开合时长（秒；计划 §3.3 区间 0.18~0.25，待调） */
    public static final float OPEN_SECONDS = 0.2f;

    /** 目标态（true = 开）。请求被接受的瞬间即翻转——动画只是追赶 */
    private boolean open;
    private float progress;
    private float animFrom;
    private float animTarget;
    private float animElapsed;
    private boolean animating;
    private GamePhase lastPhase;

    public DrawerController() {
        this(DEFAULT_OPEN);
    }

    /** @param defaultOpen 初始态（Q2 默认收起；{@link #DEFAULT_OPEN} 为常量开关） */
    public DrawerController(boolean defaultOpen) {
        this.open = defaultOpen;
        this.progress = defaultOpen ? 1f : 0f;
    }

    // —— 请求（transitionBlocked = 转场窗口封禁位） ——

    /** 请求展开；@return 是否接受（封禁期拒绝；已开幂等接受） */
    public boolean requestOpen(boolean transitionBlocked) {
        return request(true, transitionBlocked);
    }

    /** 请求收起；@return 是否接受（封禁期拒绝；已收起幂等接受） */
    public boolean requestClose(boolean transitionBlocked) {
        return request(false, transitionBlocked);
    }

    /** 把手点击：开 ⇄ 合 */
    public boolean requestToggle(boolean transitionBlocked) {
        return request(!open, transitionBlocked);
    }

    /** 强制收起（开战转场退场用）：无视封禁位；幂等（已在收起 / 收起中 = 无操作） */
    public void collapse() {
        if (open) {
            startAnim(false);
        }
    }

    private boolean request(boolean target, boolean transitionBlocked) {
        if (transitionBlocked) {
            return false;
        }
        if (open != target) {
            startAnim(target);
        }
        return true;
    }

    private void startAnim(boolean target) {
        open = target;
        animFrom = progress;
        animTarget = target ? 1f : 0f;
        animElapsed = 0f;
        animating = true;
    }

    // —— 推进 ——

    /**
     * 推进动画。
     *
     * @param dt 帧间隔秒；冻结帧传 0（不推进），负值忽略，过大一步钳到端点
     */
    public void update(float dt) {
        if (!animating || dt <= 0f) {
            return;
        }
        animElapsed += dt;
        float t = Math.min(1f, animElapsed / OPEN_SECONDS);
        progress = animFrom + (animTarget - animFrom) * easeOutQuad(t);
        if (t >= 1f) {
            progress = animTarget;
            animating = false;
        }
    }

    /** 相位联动（Q6 / §3.8）：相位跳变即强制收起；同相位重复调用无操作 */
    public void syncPhase(GamePhase phase) {
        if (lastPhase != null && phase != lastPhase) {
            collapse();
        }
        lastPhase = phase;
    }

    // —— 只读 ——

    /** 开合进度：0 = 收起、1 = 展开 */
    public float progress() {
        return progress;
    }

    /** 目标态是否为开 */
    public boolean isOpen() {
        return open;
    }

    /** 是否在开合动画中 */
    public boolean isAnimating() {
        return animating;
    }

    /** 先快后慢（ease-out quad）：半程点 ≈ 0.75 */
    static float easeOutQuad(float t) {
        return t * (2f - t);
    }
}
