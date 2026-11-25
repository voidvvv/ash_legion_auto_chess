package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

/** 开战转场驱动器状态机测试（battle §二 / render §5.6；headless——Actor/OrthographicCamera 零 GL）。
 *  zoom 断言全部除以 RENDER_GLOBAL_SCALE（libGDX zoom = 可见世界 ÷ 画布：缩小 0.8 倍 ⇔ zoom ÷0.8，
 *  SHOPPING 稳态 1.06/0.8 = 1.325 / BATTLE 稳态 1/0.8 = 1.25——相对拉远差 6% 语义不变；
 *  回归修复 2026-11-21：初版误乘致世界放大 1.25、备战席/出售区出屏）。
 *  UI 抽屉化批2：原「抽屉主体」参数位换成抽屉主体（DrawerPanel.body），退场距离参数化且必须 ≥ 抽屉宽——
 *  ③ 时代的断言按此迁移（测试数只增不减）。 */
class BattleTransitionControllerTest {

    private OrthographicCamera camera;
    private Actor shopBar;
    private Actor shoppingHud;
    private Actor battleHud;
    private Actor drawerBody;
    private Actor catcher;
    private BattleTransitionController controller;

    @BeforeEach
    void setUp() {
        camera = new OrthographicCamera();
        shopBar = new Actor();
        shoppingHud = new Actor();
        battleHud = new Actor();
        drawerBody = new Actor();
        catcher = new Actor();
        controller = new BattleTransitionController(
                camera, shopBar, shoppingHud, battleHud, drawerBody, catcher, DrawerGeometry.SLIDE_PX);
    }

    /** 按剩余秒数推进正向转场（每步 dt = LOGIC_STEP，模拟 step 门控递减） */
    private void runForward(float remaining, int steps) {
        for (int i = 0; i < steps; i++) {
            remaining = Math.max(0f, remaining - GameBalance.LOGIC_STEP);
            controller.update(GamePhase.BATTLE, remaining, GameBalance.LOGIC_STEP);
        }
        controller.applyUiPose();
    }

    @Test
    @DisplayName("SHOPPING 稳态：zoom = 基准×1.06、chrome 全显、抽屉主体可见、输入放行、Catcher 隐")
    void steadyShopping() {
        controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP);
        controller.applyUiPose();
        assertThat(camera.zoom).isCloseTo(
                GameBalance.SHOPPING_CAMERA_ZOOM / GameBalance.RENDER_GLOBAL_SCALE, within(1e-6f));
        assertThat(controller.chromeFadeAlpha()).isEqualTo(1f);
        assertThat(controller.isInputBlocked()).isFalse();
        assertThat(catcher.isVisible()).isFalse();
        assertThat(drawerBody.isVisible()).isTrue();
    }

    @Test
    @DisplayName("正向转场：起点姿态满格、中点 zoom 插值、终点解冻归稳态（zoom 1.0、输入恢复）")
    void forwardFullCycle() {
        controller.update(GamePhase.SHOPPING, 0f, 0f); // 预置 prevPhase
        // 计划核对留痕：p=0 时位移恰为 -0.0f（IEEE 中不小于 0），推进一步进入转场窗内再断言姿态
        controller.update(GamePhase.BATTLE,
                GameBalance.BATTLE_INTRO_TRANSITION_SECONDS - GameBalance.LOGIC_STEP, 0f);
        controller.applyUiPose();
        assertThat(controller.isInputBlocked()).isTrue();
        assertThat(catcher.isVisible()).isTrue();
        assertThat(shopBar.getY()).isLessThan(0f);                 // ⑧ 下滑离场中
        assertThat(battleHud.getY()).isGreaterThan(0f);            // HUD 上缘外下落中
        assertThat(drawerBody.getX()).isLessThan(0f);          // 抽屉主体左滑中

        runForward(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, 18); // 半程
        assertThat(camera.zoom)
                .isCloseTo((1f + GameBalance.SHOPPING_CAMERA_ZOOM) / 2f / GameBalance.RENDER_GLOBAL_SCALE,
                        within(1e-4f));
        assertThat(controller.chromeFadeAlpha()).isCloseTo(0.5f, within(0.05f));

        runForward(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS / 2f, 18); // 后半程：remaining 0.3 → 0
        controller.update(GamePhase.BATTLE, 0f, GameBalance.LOGIC_STEP); // 解冻帧（remaining==0 显式兜底）
        controller.applyUiPose();
        assertThat(camera.zoom).isCloseTo(1f / GameBalance.RENDER_GLOBAL_SCALE, within(1e-6f));
        assertThat(controller.isInputBlocked()).isFalse();
        assertThat(catcher.isVisible()).isFalse();
        assertThat(shopBar.getY()).isZero();                       // 稳态位姿归零
        assertThat(battleHud.getY()).isZero();
    }

    @Test
    @DisplayName("正向进度只随 introRemaining 走：冻结（dt=0）不动、跳变 remaining 即跳变（快进语义）")
    void forwardFollowsIntroRemainingOnly() {
        controller.update(GamePhase.BATTLE, GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, 0f);
        float zoomAtStart = camera.zoom;
        controller.update(GamePhase.BATTLE, GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, 0f); // 冻结帧
        assertThat(camera.zoom).isEqualTo(zoomAtStart);

        controller.update(GamePhase.BATTLE,
                GameBalance.BATTLE_INTRO_TRANSITION_SECONDS / 2f, GameBalance.LOGIC_STEP); // ×2 快进一瞬
        assertThat(camera.zoom)
                .isCloseTo((1f + GameBalance.SHOPPING_CAMERA_ZOOM) / 2f / GameBalance.RENDER_GLOBAL_SCALE,
                        within(1e-4f));
    }

    @Test
    @DisplayName("反向转场：RESULT→SHOPPING 翻转触发一次，随 dt 自计时，终点归 SHOPPING 稳态")
    void reverseFullCycle() {
        controller.update(GamePhase.RESULT, 0f, GameBalance.LOGIC_STEP); // prevPhase = RESULT
        controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP); // 触发
        controller.applyUiPose();
        assertThat(controller.isInputBlocked()).isTrue();
        assertThat(catcher.isVisible()).isTrue();
        assertThat(controller.chromeFadeAlpha()).isLessThan(1f);
        assertThat(shopBar.getY()).isLessThan(0f);                 // ⑧ 自底缘滑入中
        assertThat(battleHud.getY()).isGreaterThan(0f);            // HUD 上升退场中

        for (int i = 0; i < 36; i++) { // 0.6s = 36 步
            controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP);
        }
        controller.applyUiPose();
        assertThat(camera.zoom).isCloseTo(
                GameBalance.SHOPPING_CAMERA_ZOOM / GameBalance.RENDER_GLOBAL_SCALE, within(1e-6f));
        assertThat(controller.isInputBlocked()).isFalse();
        assertThat(controller.chromeFadeAlpha()).isEqualTo(1f);
        assertThat(drawerBody.isVisible()).isTrue();
    }

    @Test
    @DisplayName("反向中断自愈：SHOPPING 反向中相位跳 RUN_END → 立即 snap 稳态（zoom 1.0、输入恢复）")
    void reverseInterruptedByRunEndSnaps() {
        controller.update(GamePhase.RESULT, 0f, GameBalance.LOGIC_STEP);
        controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP);
        controller.update(GamePhase.RUN_END, 0f, GameBalance.LOGIC_STEP); // 转场期 Esc→放弃
        controller.applyUiPose();
        assertThat(controller.isInputBlocked()).isFalse();
        assertThat(catcher.isVisible()).isFalse();
        assertThat(camera.zoom).isCloseTo(1f / GameBalance.RENDER_GLOBAL_SCALE, within(1e-6f));
        assertThat(controller.chromeFadeAlpha()).isZero();
    }

    @Test
    @DisplayName("RUN_END→SHOPPING（重开新局）不误播反向：直接落地 SHOPPING 稳态（口径 K10）")
    void runEndToShoppingDoesNotReplayReverse() {
        controller.update(GamePhase.RUN_END, 0f, GameBalance.LOGIC_STEP);
        controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP);
        controller.applyUiPose();
        assertThat(controller.isInputBlocked()).isFalse();
        assertThat(catcher.isVisible()).isFalse();
        assertThat(controller.chromeFadeAlpha()).isEqualTo(1f);
        assertThat(camera.zoom).isCloseTo(
                GameBalance.SHOPPING_CAMERA_ZOOM / GameBalance.RENDER_GLOBAL_SCALE, within(1e-6f));
    }

    @Test
    @DisplayName("抽屉主体稳态可见性归驱动器（沿 ③ 时代 K7 口径）：仅 SHOPPING 可见——BATTLE/RESULT 稳态隐藏（装配点随后按开合进度覆写）")
    void drawerBodyVisibleOnlyInSteadyShopping() {
        runForward(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, 36); // 正向播完 → BATTLE 稳态
        assertThat(drawerBody.isVisible()).isFalse();
        controller.update(GamePhase.RESULT, 0f, GameBalance.LOGIC_STEP);
        controller.applyUiPose();
        assertThat(drawerBody.isVisible()).isFalse();
        controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP);
        controller.applyUiPose();
        assertThat(drawerBody.isVisible()).isTrue();
    }

    // —— UI 抽屉化批2：退场距离参数化（原写死 140 < 抽屉宽，会滑不干净） ——

    @Test
    @DisplayName("退役留痕：BattleTransitionController 不再暴露 ② 备战席左滑位移 benchOffsetX()（备战席已不在世界域绘制）")
    void benchOffsetXRetired() {
        java.util.Set<String> names = new java.util.HashSet<String>();
        for (java.lang.reflect.Method m : BattleTransitionController.class.getDeclaredMethods()) {
            names.add(m.getName());
        }
        assertThat(names).doesNotContain("benchOffsetX");
    }

    @Test
    @DisplayName("退场距离校验：必须 ≥ 抽屉宽 244（小于即抛 IllegalArgumentException；等于放行）")
    void drawerExitDistanceMustCoverDrawerWidth() {
        assertThat(DrawerGeometry.SLIDE_PX).isEqualTo(DrawerGeometry.DRAWER_X + DrawerGeometry.DRAWER_W);
        assertThat(controller.getDrawerSlidePx()).isEqualTo(DrawerGeometry.SLIDE_PX);
        assertThat((float) DrawerGeometry.SLIDE_PX).isGreaterThan(GameBalance.BATTLE_TRANSITION_SLIDE_LEFT_PX);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new BattleTransitionController(
                camera, shopBar, shoppingHud, battleHud, drawerBody, catcher, DrawerGeometry.DRAWER_W - 1));
        new BattleTransitionController(camera, shopBar, shoppingHud, battleHud, drawerBody, catcher,
                DrawerGeometry.DRAWER_W); // 等于抽屉宽：放行
    }

    @Test
    @DisplayName("正向转场：抽屉主体按自身退场距离左滑并淡出——同进度位移 ≥ ⑥ 开战按钮（140 档），滑完 ≥ 抽屉宽不留残边")
    void drawerBodyExitsByItsOwnDistance() {
        controller.update(GamePhase.SHOPPING, 0f, 0f); // 预置 prevPhase
        controller.update(GamePhase.BATTLE,
                GameBalance.BATTLE_INTRO_TRANSITION_SECONDS - GameBalance.LOGIC_STEP, 0f);
        controller.applyUiPose();
        float p = 1f - (GameBalance.BATTLE_INTRO_TRANSITION_SECONDS - GameBalance.LOGIC_STEP)
                / GameBalance.BATTLE_INTRO_TRANSITION_SECONDS;
        assertThat(drawerBody.isVisible()).isTrue();
        assertThat(drawerBody.getX()).isEqualTo(-Math.round(DrawerGeometry.SLIDE_PX * p));
        int hudShift = Math.round(GameBalance.BATTLE_TRANSITION_SLIDE_LEFT_PX * p);
        assertThat(-drawerBody.getX()).as("抽屉退场位移 ≥ ⑥ 位移").isGreaterThanOrEqualTo(hudShift);
        assertThat(shoppingHud.getX()).isEqualTo(-hudShift);
        assertThat(drawerBody.getColor().a).isLessThan(1f);

        runForward(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, 36); // 播完
        assertThat(drawerBody.getX()).isZero();   // 稳态归零
        assertThat(drawerBody.getColor().a).isEqualTo(1f);
    }

    @Test
    @DisplayName("反向转场：抽屉主体淡入归位（p: 0→1 透明度随之回升）")
    void drawerBodyEntersOnReverse() {
        controller.update(GamePhase.RESULT, 0f, GameBalance.LOGIC_STEP);
        controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP);
        controller.applyUiPose();
        float p = 1f - (GameBalance.BATTLE_INTRO_TRANSITION_SECONDS - GameBalance.LOGIC_STEP)
                / GameBalance.BATTLE_INTRO_TRANSITION_SECONDS;
        assertThat(drawerBody.isVisible()).isTrue();
        assertThat(drawerBody.getColor().a).isCloseTo(p, within(1e-4f));
        assertThat(drawerBody.getX()).isEqualTo(-Math.round(DrawerGeometry.SLIDE_PX * (1f - p)));
    }
}
