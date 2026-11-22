package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 抽屉开合状态机测试（UI 抽屉化批1；仿 {@link BattleTransitionControllerTest}，headless 纯逻辑）。
 * Q2：默认收起（常量可切）、把把手开合；§3.3：0.18~0.25s 先快后慢、位移逐帧取整；
 * 冻结感知 dt 传 0；转场窗口内拒绝开合（§3.8）；Q6：战斗期默认收起、允许只读展开；战毕不自动恢复展开。
 */
class DrawerControllerTest {

    private static final float DT = GameBalance.LOGIC_STEP;

    /** 按 dt 步进 n 次 */
    private static void step(DrawerController c, int n, float dt) {
        for (int i = 0; i < n; i++) {
            c.update(dt);
        }
    }

    @Test
    @DisplayName("默认收起（Q2）：新实例进度 0、非开态；DEFAULT_OPEN 常量为 false；常量置 true 则默认展开")
    void defaultCollapsed() {
        assertThat(DrawerController.DEFAULT_OPEN).isFalse();
        DrawerController c = new DrawerController();
        assertThat(c.progress()).isZero();
        assertThat(c.isOpen()).isFalse();
        assertThat(c.isAnimating()).isFalse();
        DrawerController open = new DrawerController(true);
        assertThat(open.progress()).isEqualTo(1f);
        assertThat(open.isOpen()).isTrue();
    }

    @Test
    @DisplayName("开合进度 0→1：requestOpen 后随 dt 单调升至恰 1（时长 = OPEN_SECONDS 0.2s），终点停走不再动画")
    void openProgressReachesOne() {
        DrawerController c = new DrawerController();
        assertThat(c.requestOpen(false)).isTrue();
        assertThat(c.isOpen()).as("目标态即时为开").isTrue();
        assertThat(c.isAnimating()).isTrue();
        assertThat(DrawerController.OPEN_SECONDS).isBetween(0.18f, 0.25f);
        float prev = 0f;
        int steps = 0;
        while (c.progress() < 1f && steps < 1000) {
            c.update(DT);
            assertThat(c.progress()).isGreaterThanOrEqualTo(prev);
            assertThat(c.progress()).isLessThanOrEqualTo(1f);
            prev = c.progress();
            steps++;
        }
        assertThat(c.progress()).isEqualTo(1f);
        assertThat((float) steps * DT).as("实际耗时 ≈ OPEN_SECONDS").isCloseTo(DrawerController.OPEN_SECONDS, within(DT + 1e-4f));
        assertThat(c.isAnimating()).isFalse();
        c.update(DT);
        assertThat(c.progress()).isEqualTo(1f);
    }

    @Test
    @DisplayName("先快后慢（ease-out）：开/合两向前半程位移都大于后半程（半程点 ≈ 0.75）")
    void easeOutForBothDirections() {
        DrawerController c = new DrawerController();
        c.requestOpen(false);
        step(c, 6, DT);
        float openFirstHalf = c.progress();
        step(c, 6, DT);
        float openSecondHalf = c.progress() - openFirstHalf;
        assertThat(openFirstHalf).as("展开前半程").isGreaterThan(openSecondHalf);
        assertThat(openFirstHalf).isCloseTo(0.75f, within(0.01f));

        c.requestClose(false);
        step(c, 6, DT);
        float closeFirstHalf = 1f - c.progress();
        step(c, 6, DT);
        float closeSecondHalf = (1f - closeFirstHalf) - c.progress();
        assertThat(closeFirstHalf).as("收起前半程").isGreaterThan(closeSecondHalf);
        assertThat(closeFirstHalf).isCloseTo(0.75f, within(0.01f));
    }

    @Test
    @DisplayName("位移取整：任意进度下主体 x 为整虚拟像素；展开 = DRAWER_X、收起 = DRAWER_X − SLIDE_PX；收起过程单调左移")
    void offsetSnappedToWholePixels() {
        DrawerController c = new DrawerController();
        assertThat(DrawerGeometry.bodyX(c.progress())).isEqualTo(DrawerGeometry.DRAWER_X - DrawerGeometry.SLIDE_PX);
        c.requestOpen(false);
        int prev = Integer.MIN_VALUE;
        float prevP = -1f;
        for (int i = 0; i < 40; i++) {
            c.update(DT);
            int x = DrawerGeometry.bodyX(c.progress());
            assertThat(x).isEqualTo((int) x);
            assertThat(x).isGreaterThanOrEqualTo(prev);
            assertThat(c.progress()).isGreaterThanOrEqualTo(prevP);
            prev = x;
            prevP = c.progress();
        }
        assertThat(DrawerGeometry.bodyX(c.progress())).isEqualTo(DrawerGeometry.DRAWER_X);
        prev = Integer.MAX_VALUE;
        c.requestClose(false);
        for (int i = 0; i < 40; i++) {
            c.update(DT);
            int x = DrawerGeometry.bodyX(c.progress());
            assertThat(x).isLessThanOrEqualTo(prev);
            prev = x;
        }
        assertThat(DrawerGeometry.bodyX(c.progress())).isEqualTo(DrawerGeometry.DRAWER_X - DrawerGeometry.SLIDE_PX);
        assertThat(c.progress()).isZero();
        assertThat(c.isOpen()).isFalse();
    }

    @Test
    @DisplayName("冻结不走：update(0) 不推进；负 dt 忽略；大 dt 一步钳到端点不越界")
    void freezeAndClamp() {
        DrawerController c = new DrawerController();
        c.requestOpen(false);
        step(c, 3, DT);
        float p = c.progress();
        for (int i = 0; i < 50; i++) {
            c.update(0f); // 冻结帧
        }
        assertThat(c.progress()).isEqualTo(p);
        c.update(-1f);
        assertThat(c.progress()).isEqualTo(p);
        c.update(10f);
        assertThat(c.progress()).isEqualTo(1f);
        c.update(10f);
        assertThat(c.progress()).isEqualTo(1f);
        c.requestClose(false);
        c.update(10f);
        assertThat(c.progress()).isZero();
    }

    @Test
    @DisplayName("转场窗口内拒绝开合（§3.8）：blocked 时开/关/切换均返回 false 且状态不变；解禁后照常")
    void requestsRejectedWhileTransitionBlocked() {
        DrawerController c = new DrawerController();
        assertThat(c.requestOpen(true)).isFalse();
        assertThat(c.requestToggle(true)).isFalse();
        assertThat(c.progress()).isZero();
        step(c, 20, DT);
        assertThat(c.progress()).as("被拒请求不留待播动画").isZero();

        assertThat(c.requestOpen(false)).isTrue();
        step(c, 20, DT);
        assertThat(c.requestClose(true)).isFalse();
        assertThat(c.requestToggle(true)).isFalse();
        assertThat(c.isOpen()).isTrue();
        c.update(DT);
        assertThat(c.progress()).isEqualTo(1f);

        assertThat(c.requestToggle(false)).isTrue();
        assertThat(c.isOpen()).isFalse();
    }

    @Test
    @DisplayName("幂等与中途反向：已开再开 = 无动画；开合中途反向从当前进度续走（不跳变）")
    void idempotentRequestsAndMidwayReversal() {
        DrawerController c = new DrawerController();
        assertThat(c.requestClose(false)).as("已收起再收起：接受且无动画").isTrue();
        assertThat(c.isAnimating()).isFalse();
        c.requestOpen(false);
        step(c, 4, DT);
        float mid = c.progress();
        assertThat(mid).isGreaterThan(0f).isLessThan(1f);
        assertThat(c.requestClose(false)).isTrue(); // 中途反向
        c.update(1e-4f);
        assertThat(c.progress()).as("反向瞬间从当前进度续走").isCloseTo(mid, within(1e-3f));
        c.requestOpen(false); // 再反向
        c.update(1e-4f);
        assertThat(c.progress()).isGreaterThanOrEqualTo(mid - 1e-3f);
        step(c, 30, DT);
        assertThat(c.requestOpen(false)).isTrue();
        assertThat(c.isAnimating()).as("已全开再开：无动画").isFalse();
    }

    @Test
    @DisplayName("强制收起 collapse：无视转场位、幂等——开战转场「随转场左滑退场」用")
    void collapseIsUnconditionalAndIdempotent() {
        DrawerController c = new DrawerController(true);
        c.collapse();
        assertThat(c.isOpen()).isFalse();
        float p = c.progress();
        assertThat(p).isGreaterThan(0f); // 走动画而非瞬跳
        c.update(DT);
        c.collapse();
        c.update(DT);
        assertThat(c.progress()).isLessThanOrEqualTo(p);
        step(c, 30, DT);
        assertThat(c.progress()).isZero();
        c.collapse();
        assertThat(c.isAnimating()).isFalse();
    }

    @Test
    @DisplayName("相位联动（Q6）：离开备战的相位跳变强制收起；战斗期内只读展开后不被反复收起；战毕回备战不自动恢复展开")
    void phaseSyncCollapsesOnPhaseChangeOnly() {
        DrawerController c = new DrawerController();
        c.syncPhase(GamePhase.SHOPPING);
        c.requestOpen(false);
        step(c, 20, DT);
        c.syncPhase(GamePhase.SHOPPING); // 同相位：不动
        assertThat(c.isOpen()).isTrue();

        c.syncPhase(GamePhase.BATTLE); // 开战：随转场退场
        assertThat(c.isOpen()).isFalse();
        step(c, 20, DT);
        assertThat(c.progress()).isZero();

        assertThat(c.requestOpen(false)).as("战斗期允许只读展开").isTrue();
        step(c, 20, DT);
        for (int i = 0; i < 10; i++) {
            c.syncPhase(GamePhase.BATTLE); // 同相位重复同步：不得再次强制收起
        }
        assertThat(c.isOpen()).as("战斗期展开态保持").isTrue();
        assertThat(c.progress()).isEqualTo(1f);

        c.syncPhase(GamePhase.RESULT); // 任一相位跳变都强制收起
        assertThat(c.isOpen()).isFalse();
        step(c, 20, DT);
        c.syncPhase(GamePhase.SHOPPING); // 战毕回备战：保持收起，不自动恢复
        assertThat(c.isOpen()).isFalse();
        assertThat(c.progress()).isZero();
    }
}
