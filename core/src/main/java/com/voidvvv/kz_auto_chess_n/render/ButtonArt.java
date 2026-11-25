package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

/**
 * 通用文本按钮壳（品质改造第二阶段：generated_v2 四态按钮接线）。
 *
 * <p>按禁用 &gt; 按下 &gt; 悬停 &gt; 正常的优先级选 {@link UiKeys} 按钮态九宫格
 * （48×24 切分 8/8/8/8，拉伸到按钮实际矩形）；文案仍由 BitmapFont 绘制（动态文字不进贴图）。
 * 供各屏自绘按钮 Actor 共用（MainMenu / SampleMenu / RunSetup / PauseMenu / ShopBar /
 * BattleHud / ShoppingHud / RunEnd / Chest / TopBar）——取态需要按钮自己的
 * {@link ClickListener}（{@code isPressed}/{@code isOver}），调用方须把 listener 存字段。
 *
 * <p>缺素材期由占位层兜底（UiPlaceholderPainter 已为四态生成平色占位），链路与 uiPatch 一致。
 */
public final class ButtonArt {

    /** 九宫格着色复用（渲染段零分配——沿 DrawerSkin.TINT 先例；每次绘制前整体覆写） */
    private static final Color TINT = new Color(1f, 1f, 1f, 1f);

    private ButtonArt() {
    }

    /**
     * 画按钮壳（左下角 x/y、宽高）。
     *
     * @param listener 按钮自己的 ClickListener（isPressed/isOver 取态；无悬停跟踪的按钮传 null = 恒正常态）
     * @param disabled 禁用态（最高优先）
     */
    public static void drawShell(Batch batch, Assets assets, ClickListener listener,
                                 float x, float y, float width, float height, float alpha, boolean disabled) {
        UiKeys.Entry state = disabled ? UiKeys.BUTTON_DISABLED
                : listener != null && listener.isPressed() ? UiKeys.BUTTON_PRESSED
                : listener != null && listener.isOver() ? UiKeys.BUTTON_HOVER
                : UiKeys.BUTTON_NORMAL;
        NinePatch patch = assets.uiPatch(state.id());
        TINT.set(1f, 1f, 1f, alpha);
        patch.setColor(TINT);
        patch.draw(batch, x, y, width, height);
    }
}
