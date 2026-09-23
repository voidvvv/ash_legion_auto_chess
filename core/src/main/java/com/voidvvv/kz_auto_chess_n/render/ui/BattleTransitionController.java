package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;

/**
 * 开战转场「清场入阵」驱动器（battle §二 / render §5.6；2026-09-09 用户裁决 A「就地适配」）。
 *
 * <p>正向（SHOPPING→BATTLE）进度 = 1 − introRemaining / 转场时长：消费逻辑冻结门的剩余值，
 * ×2 快进与暂停/弹窗冻结零特判自动正确；反向（RESULT→SHOPPING，Q3 裁决）无逻辑计时器，
 * 渲染侧自计时（调用方以 frozen ? 0 : delta 喂 dt，随冻结停走）。
 *
 * <p>纯表现层：只写 UI Actor 位姿/透明度与 worldCamera.zoom，零逻辑改动、零 CombatEvent、零 RNG，
 * 确定性回放不含转场（battle §二冻结清单）。像素位移一律取整吸附（render §八）；zoom 插值是唯一例外
 * （Q4 已批的镜头域）。命中换算：boardViewport.unproject 含 zoom 逆变换，备战期拖拽无需补偿。
 *
 * <p>调用序（BattleScreen.render）：update() 必须先于 worldViewport.apply()（zoom 同帧生效）；
 * applyUiPose() 必须在相位可见性翻转行之后、uiStage.draw 之前（转场窗内覆写可见性）。
 */
public final class BattleTransitionController {

    private final OrthographicCamera worldCamera;
    private final Actor shopBar;        // ⑧ 商店栏（底部条带、组原点）：下滑离场 / 自底缘上滑归位（裁决 A）
    private final Actor shoppingHud;    // ⑥ 开战按钮（组原点）：左移淡出 / 淡入归位
    private final Actor battleHud;      // 战斗 HUD（顶部、组原点）：自上缘外下落就位 / 上升退场
    private final Actor inventoryPanel; // ③ 背包（组原点）：左滑离场 / 滑回；稳态可见性归本类（K7「仅 SHOPPING」）
    private final Actor inputCatcher;   // 全屏透明收点：转场窗口吞 UI 点击（ResultBanner.ClickCatcher 先例）

    private GamePhase phase = GamePhase.SHOPPING;
    private float introRemaining;    // 最近一次 update 收到的转场剩余秒数（正向激活判定）
    private float reverseRemaining;  // 反向转场剩余秒数（渲染自计时；0 = 不在反向）

    public BattleTransitionController(OrthographicCamera worldCamera, Actor shopBar, Actor shoppingHud,
                                      Actor battleHud, Actor inventoryPanel, Actor inputCatcher) {
        this.worldCamera = worldCamera;
        this.shopBar = shopBar;
        this.shoppingHud = shoppingHud;
        this.battleHud = battleHud;
        this.inventoryPanel = inventoryPanel;
        this.inputCatcher = inputCatcher;
    }

    // —— 状态推进（render 早期：写 camera.zoom，先于 worldViewport.apply()） ——

    /**
     * 推进转场状态机并写入镜头 zoom。
     *
     * @param introRemaining BATTLE 且 intro 激活时的剩余秒数；否则传 0（Screen 侧合流）
     * @param dt             渲染帧时长（冻结期传 0——反向自计时随暂停停走）
     */
    public void update(GamePhase phase, float introRemaining, float dt) {
        if (this.phase == GamePhase.RESULT && phase == GamePhase.SHOPPING) {
            reverseRemaining = GameBalance.BATTLE_INTRO_TRANSITION_SECONDS; // Q3：战毕回备战反向播一次
        }
        this.phase = phase;
        this.introRemaining = introRemaining;
        if (reverseRemaining > 0f && phase != GamePhase.SHOPPING) {
            reverseRemaining = 0f; // 中断自愈（转场期 Esc→放弃 → RUN_END 等）：瞬回稳态
        }
        reverseRemaining = Math.max(0f, reverseRemaining - dt);
        worldCamera.zoom = zoom();
    }

    // —— 只读姿态（棋盘域 chrome 由 Screen 转交给 BattleRenderer） ——

    /** ② 备战席层水平位移（负值向左；③⑥ UI 域同参取值）：转场外恒 0 */
    public float benchOffsetX() {
        if (forwardPlaying()) {
            return -snap(GameBalance.BATTLE_TRANSITION_SLIDE_LEFT_PX * forwardProgress());
        }
        if (reverseRemaining > 0f) {
            return -snap(GameBalance.BATTLE_TRANSITION_SLIDE_LEFT_PX * (1f - reverseProgress()));
        }
        return 0f;
    }

    /** 备战层淡出系数（⑦ 出售区 / 布阵提示 / 敌阵虚影 / 部署帧）：稳态 SHOPPING 恒 1、其余稳态恒 0 */
    public float chromeFadeAlpha() {
        if (forwardPlaying()) {
            return 1f - forwardProgress();
        }
        if (reverseRemaining > 0f) {
            return reverseProgress();
        }
        return phase == GamePhase.SHOPPING ? 1f : 0f;
    }

    /** 转场期输入封禁（boardProcessor modalBlocked 合流 + inputCatcher 显隐；转场结束帧即恢复——GDD） */
    public boolean isInputBlocked() {
        return forwardPlaying() || reverseRemaining > 0f;
    }

    // —— UI 姿态（render 后段：相位可见性行之后、uiStage.draw 之前） ——

    /**
     * 覆写 ⑥⑧HUD③ 的可见性与位姿：转场窗内接管（相位行可能已隐藏，转场期强制可见随位移动画）；
     * 稳态把位姿归零、可见性交还相位行（③ 例外——稳态可见性常驻归本类，K7）。
     */
    public void applyUiPose() {
        if (forwardPlaying()) {
            float p = forwardProgress();
            shopBar.setVisible(true); // 相位行已隐藏：转场期接管（render §5.6）
            shopBar.setPosition(0f, -snap(GameBalance.BATTLE_TRANSITION_SLIDE_EDGE_PX * p));
            battleHud.setVisible(true);
            battleHud.setPosition(0f, snap(GameBalance.BATTLE_TRANSITION_SLIDE_EDGE_PX * (1f - p)));
            poseExit(shoppingHud, p);
            poseExit(inventoryPanel, p);
        } else if (reverseRemaining > 0f) {
            float p = reverseProgress();
            shopBar.setVisible(true); // 相位行已显示：仅覆写位姿（自底缘上滑归位——裁决 A）
            shopBar.setPosition(0f, -snap(GameBalance.BATTLE_TRANSITION_SLIDE_EDGE_PX * (1f - p)));
            battleHud.setVisible(true); // 相位行已隐藏：上升退场
            battleHud.setPosition(0f, snap(GameBalance.BATTLE_TRANSITION_SLIDE_EDGE_PX * p));
            poseEnter(shoppingHud, p);
            poseEnter(inventoryPanel, p);
        } else {
            poseRest(shopBar, false);
            poseRest(battleHud, false);
            poseRest(shoppingHud, false);
            poseRest(inventoryPanel, true);
        }
        inputCatcher.setVisible(isInputBlocked()); // 仅转场窗口可命中（不可见 Actor 不参与 hit 测试）
    }

    /** ⑥/③ 退场姿态：左移 + 淡出（p: 0→1） */
    private void poseExit(Actor actor, float p) {
        actor.setVisible(true);
        actor.setColor(1f, 1f, 1f, 1f - p);
        actor.setPosition(-snap(GameBalance.BATTLE_TRANSITION_SLIDE_LEFT_PX * p), 0f);
    }

    /** ⑥/③ 归位姿态：左缘滑回 + 淡入（p: 0→1） */
    private void poseEnter(Actor actor, float p) {
        actor.setVisible(true);
        actor.setColor(1f, 1f, 1f, p);
        actor.setPosition(-snap(GameBalance.BATTLE_TRANSITION_SLIDE_LEFT_PX * (1f - p)), 0f);
    }

    /** 稳态归零（幂等，逐帧调用无害）；visibleByPhase=false 的 Actor 可见性交还 Screen 相位行 */
    private void poseRest(Actor actor, boolean visibleOwnedHere) {
        actor.setColor(1f, 1f, 1f, 1f);
        actor.setPosition(0f, 0f);
        if (visibleOwnedHere) {
            actor.setVisible(phase == GamePhase.SHOPPING);
        }
    }

    // —— 内部 ——

    private boolean forwardPlaying() {
        return phase == GamePhase.BATTLE && introRemaining > 0f;
    }

    /** 正向进度 0→1（introRemaining 归一化；×2 快进 = 同一 accumulator 通路自动加速） */
    private float forwardProgress() {
        return 1f - introRemaining / GameBalance.BATTLE_INTRO_TRANSITION_SECONDS;
    }

    private float reverseProgress() {
        return 1f - reverseRemaining / GameBalance.BATTLE_INTRO_TRANSITION_SECONDS;
    }

    /** 镜头 zoom（K4：SHOPPING 稳态 1.06、其余稳态 1.0、转场线性插值）：随备战层淡出系数同步回正——
     *  chrome 全显（转场起点/SHOPPING 稳态）= 1.06，chrome 全隐（BATTLE 稳态）= 1.0，同一转场窗单一真相源 */
    private float zoom() {
        return 1f + (GameBalance.SHOPPING_CAMERA_ZOOM - 1f) * chromeFadeAlpha();
    }

    /** 像素取整吸附（render §八；zoom 插值除外——Q4 镜头域） */
    private static float snap(float value) {
        return Math.round(value);
    }
}
