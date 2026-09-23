package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 开战转场驱动器状态机测试（battle §二 / render §5.6；headless——Actor/OrthographicCamera 零 GL） */
class BattleTransitionControllerTest {

    private OrthographicCamera camera;
    private Actor shopBar;
    private Actor shoppingHud;
    private Actor battleHud;
    private Actor inventoryPanel;
    private Actor catcher;
    private BattleTransitionController controller;

    @BeforeEach
    void setUp() {
        camera = new OrthographicCamera();
        shopBar = new Actor();
        shoppingHud = new Actor();
        battleHud = new Actor();
        inventoryPanel = new Actor();
        catcher = new Actor();
        controller = new BattleTransitionController(
                camera, shopBar, shoppingHud, battleHud, inventoryPanel, catcher);
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
    @DisplayName("SHOPPING 稳态：zoom 1.06、chrome 全显、③ 可见、输入放行、Catcher 隐")
    void steadyShopping() {
        controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP);
        controller.applyUiPose();
        assertThat(camera.zoom).isCloseTo(GameBalance.SHOPPING_CAMERA_ZOOM, within(1e-6f));
        assertThat(controller.benchOffsetX()).isZero();
        assertThat(controller.chromeFadeAlpha()).isEqualTo(1f);
        assertThat(controller.isInputBlocked()).isFalse();
        assertThat(catcher.isVisible()).isFalse();
        assertThat(inventoryPanel.isVisible()).isTrue();
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
        assertThat(inventoryPanel.getX()).isLessThan(0f);          // ③ 左滑中

        runForward(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, 18); // 半程
        assertThat(camera.zoom)
                .isCloseTo((1f + GameBalance.SHOPPING_CAMERA_ZOOM) / 2f, within(1e-4f)); // 1.03
        assertThat(controller.chromeFadeAlpha()).isCloseTo(0.5f, within(0.05f));

        runForward(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS / 2f, 18); // 后半程：remaining 0.3 → 0
        controller.update(GamePhase.BATTLE, 0f, GameBalance.LOGIC_STEP); // 解冻帧（remaining==0 显式兜底）
        controller.applyUiPose();
        assertThat(camera.zoom).isCloseTo(1f, within(1e-6f));
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
                .isCloseTo((1f + GameBalance.SHOPPING_CAMERA_ZOOM) / 2f, within(1e-4f));
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
        assertThat(camera.zoom).isCloseTo(GameBalance.SHOPPING_CAMERA_ZOOM, within(1e-6f));
        assertThat(controller.isInputBlocked()).isFalse();
        assertThat(controller.chromeFadeAlpha()).isEqualTo(1f);
        assertThat(inventoryPanel.isVisible()).isTrue();
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
        assertThat(camera.zoom).isCloseTo(1f, within(1e-6f));
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
        assertThat(camera.zoom).isCloseTo(GameBalance.SHOPPING_CAMERA_ZOOM, within(1e-6f));
    }

    @Test
    @DisplayName("③ 背包稳态可见性归驱动器（K7）：仅 SHOPPING 可见——BATTLE/RESULT 稳态隐藏")
    void inventoryVisibleOnlyInSteadyShopping() {
        runForward(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, 36); // 正向播完 → BATTLE 稳态
        assertThat(inventoryPanel.isVisible()).isFalse();
        controller.update(GamePhase.RESULT, 0f, GameBalance.LOGIC_STEP);
        controller.applyUiPose();
        assertThat(inventoryPanel.isVisible()).isFalse();
        controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP);
        controller.applyUiPose();
        assertThat(inventoryPanel.isVisible()).isTrue();
    }
}
