package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

/**
 * 不缩放层（UI 抽屉化批1·Q8 对策一）：挂在 uiStage 根下做<b>反向缩放</b>，恰好抵消根缩放
 * （{@code BattleScreen.applyGlobalUiScale}：根 scale = RENDER_GLOBAL_SCALE、位置居中）。
 * 效果：层内 1 虚拟像素 = 1 舞台像素 = 整数屏幕像素（默认窗口 2×）——位图逐像素清晰、滑动步进整像素。
 *
 * <p>数学（纯函数可测）：{@code layerScale = 1 / uiScale}；{@code layerPos = −rootPos / uiScale}
 * （rootPos = {@link BoardGeometry#uiContentRect} 的 x/y = (64,36) → 层位置 (−80,−45)）。
 * libGDX Actor 变换序 = 子坐标 × scale + position，两层复合：
 * {@code (局部 × k + layerPos) × uiScale + rootPos}，其中 k×uiScale = 1、layerPos×uiScale = −rootPos ⇒ 恒等，
 * 即<b>层内局部坐标 = 舞台坐标</b>（与 {@link BoardGeometry#screenX} 同式的中心保持缩放，合成系数为 1）。
 *
 * <p>实例自身<b>不可命中</b>（{@link Touchable#childrenOnly}）：本层铺满舞台，若自身可命中会遮蔽
 * 下层全部既有面板（商店卡 / 顶栏 / 背包槽……的点击全部被截）；只有子件（抽屉）参与命中。
 * 代价（计划 §3.6）：层内文字比其余 UI（等效 9.6px）大 25%——描述类用 0.75× 小字抹平。
 */
public final class UnscaledLayer extends Group {

    public UnscaledLayer(float uiScale) {
        requireValid(uiScale);
        setScale(layerScale(uiScale));
        setPosition(layerX(uiScale), layerY(uiScale));
        setSize(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H);
        setTouchable(Touchable.childrenOnly);
    }

    /** 反向缩放系数 = 1 / uiScale（uiScale = RENDER_GLOBAL_SCALE） */
    public static float layerScale(float uiScale) {
        requireValid(uiScale);
        return 1f / uiScale;
    }

    /** 层位置 x（根子坐标）= −根位置 x / uiScale */
    public static float layerX(float uiScale) {
        requireValid(uiScale);
        return -BoardGeometry.uiContentRect(uiScale)[0] / uiScale;
    }

    /** 层位置 y（根子坐标）= −根位置 y / uiScale */
    public static float layerY(float uiScale) {
        requireValid(uiScale);
        return -BoardGeometry.uiContentRect(uiScale)[1] / uiScale;
    }

    /** 层内局部 x → 舞台 x（与 {@link BoardGeometry#screenX} 同式：合成缩放 = uiScale × layerScale = 1） */
    public static float toStageX(float localX, float uiScale) {
        return BoardGeometry.screenX(localX, uiScale * layerScale(uiScale));
    }

    /** 层内局部 y → 舞台 y（同 {@link #toStageX}） */
    public static float toStageY(float localY, float uiScale) {
        return BoardGeometry.screenY(localY, uiScale * layerScale(uiScale));
    }

    /**
     * 舞台像素 → 屏幕像素倍率（FitViewport：取宽比/高比的较小者，另一向留黑边）。
     * 默认窗口 1280×720 → 2（整数：抽屉位图逐像素清晰的充要条件）；非整数倍窗口下清晰保证失效
     * ——这是全游戏既有条件，不是抽屉带来的（计划 §3.6 对策一 代价③）。
     */
    public static float screenPixelsPerStagePixel(int windowWidth, int windowHeight) {
        if (windowWidth <= 0 || windowHeight <= 0) {
            throw new IllegalArgumentException("窗口尺寸必须为正，实际=" + windowWidth + "x" + windowHeight);
        }
        return Math.min(windowWidth / (float) BoardGeometry.VIRTUAL_W,
                windowHeight / (float) BoardGeometry.VIRTUAL_H);
    }

    private static void requireValid(float uiScale) {
        if (uiScale <= 0f || Float.isNaN(uiScale)) {
            throw new IllegalArgumentException("uiScale 必须为正（反向缩放 = 1/uiScale），实际=" + uiScale);
        }
    }
}
