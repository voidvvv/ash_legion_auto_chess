package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.UiKeys;

import java.util.List;

/**
 * 抽屉绘制皮肤与小件（UI 抽屉化批3 批前重构：自 DrawerPanel 抽出，纯搬迁、行为不变）。
 *
 * <p>承载三类东西：① 字号/透明度常量与文字摆放、透明度合成的纯函数；② 着色纪律入口
 * （{@link #drawPatch}/{@link #drawRegion}/{@link #drawText}）；③ 无业务状态的通用小件
 * （九宫格件、面板底、关闭钮、把手）。带业务状态的件各自成类：
 * {@link DrawerBadge}/{@link DrawerScrollKnob}/{@link DrawerInfoSection}/{@link DrawerStripSection}/{@link DrawerEquipSection}。
 */
final class DrawerSkin {

    /**
     * 描述类文字行高（正文楷体 8.5px 的实测行高 10px；抽屉滚动块 / 悬停卡 / tooltip 共用）。
     * 字号本身由 {@link Assets#bodyFont()} 决定，绘制时不再二次 setScale。
     */
    static final float SMALL_LINE_HEIGHT = Assets.BODY_LINE_HEIGHT;
    /** UI 标签/数值/按钮/页签字号（宋体逻辑 10px；标题黑体 20px 不在此列） */
    static final float TEXT_HEIGHT = Assets.UI_FONT_SIZE;
    /** Q2 让位：从抽屉往外拖棋子时面板淡到三成 */
    static final float DIM_ALPHA = 0.3f;

    /** 九宫格着色复用（NinePatch.setColor 只收 Color；渲染段零分配） */
    private static final Color TINT = new Color(1f, 1f, 1f, 1f);

    private DrawerSkin() {
    }

    // —— 纯函数 ——

    static float alphaOf(Actor actor, float parentAlpha) {
        return parentAlpha * actor.getColor().a;
    }

    /**
     * 盒内垂直居中的文字行顶（libGDX {@code BitmapFont.draw} 的 y 是<b>行顶</b>、文字向下延伸——
     * 不是基线；与 HoverPreviewCard 的用法同口径）。
     *
     * @param textH 行高（12px 字 = 12；0.75× 小字 = 9）
     */
    static float centeredTextTop(float boxY, float boxH, float textH) {
        return boxY + boxH - (boxH - textH) / 2f;
    }

    /**
     * 主体透明度合成规则：转场控制器每帧先写姿态透明度（稳态归 1、转场窗内 1−p），抽屉落位随后执行——
     * <b>让位期间逐帧压到三成</b>（否则被稳态归 1 覆盖）；退出让位的那一帧还原 1；其余帧不碰（保留转场淡出）。
     *
     * @param currentAlpha 转场姿态写下的当前透明度
     * @param dimmed       当前是否让位
     * @param dimApplied   上一帧是否处于让位
     */
    static float resolveBodyAlpha(float currentAlpha, boolean dimmed, boolean dimApplied) {
        if (dimmed) {
            return DIM_ALPHA;
        }
        return dimApplied ? 1f : currentAlpha;
    }

    // —— 着色纪律与绘制入口 ——

    /**
     * 抽屉内一切绘制（整图 / 九宫格 / 文字）的着色纪律——统一入口：<b>先显式自设色</b>、画毕还原白。
     *
     * <p><b>必须自设色</b>：{@code Batch.getColor()} 返回的是 SpriteBatch 内部同一实例（1.14.0 字节码核实），
     * 既有面板的 {@code Color old = batch.getColor() … setColor(old)} 实为空操作，tint 会泄漏给同批次内后续
     * 未自设色的绘制；且 <b>NinePatch.draw 同样被批次颜色相乘</b>（像素采样实证：备战期整块面板被背包槽底色
     * 染到 1/4 亮度，RESULT 期前方面板隐藏后恢复素材原色）。文字走字体自身颜色。
     *
     * @param alpha 本件的合成透明度（parentAlpha × 自身 color.a：转场淡出 / 让位三成由此透传）
     */
    static void beginUntinted(Batch batch, float alpha) {
        batch.setColor(1f, 1f, 1f, alpha);
    }

    /** 还原白（离开一次抽屉绘制后——不把任何状态留给同批次的后续 Actor） */
    static void endUntinted(Batch batch) {
        batch.setColor(Color.WHITE);
    }

    static void drawPatch(Batch batch, NinePatch patch, float x, float y, float w, float h, float alpha) {
        TINT.set(1f, 1f, 1f, alpha);
        patch.setColor(TINT); // 九宫格吃自身颜色：每次绘制前显式写，不依赖上一件
        beginUntinted(batch, alpha);
        patch.draw(batch, x, y, w, h);
        endUntinted(batch);
    }

    /** 整图绘制（同 {@link #drawPatch} 的着色纪律） */
    static void drawRegion(Batch batch, TextureRegion region, float x, float y, float w, float h, float alpha) {
        beginUntinted(batch, alpha);
        batch.draw(region, x, y, w, h);
        endUntinted(batch);
    }

    /**
     * 滚动框换内容：文字块按实测换行撑高后<b>必须作废 ScrollPane 的布局缓存</b>——ScrollPane 只在自身失效时
     * 重算内容高度，否则沿用首次布局的旧高度（占位 1 行 = 17px），滚轮被消费却滚不动
     * （批3 探针实证：scrollY 恒 0；回归用例见 DrawerTextCapacityTest）。
     * 换内容后立即重排，并从新内容顶部开始。
     */
    static void setContent(ScrollPane pane, DrawerTextBlock block, List<String> paragraphs, float width) {
        block.setParagraphs(paragraphs, width);
        pane.invalidate();
        pane.validate();
        pane.setScrollY(0f);
        pane.updateVisualScroll();
        if (pane.getMaxY() <= 0f) {
            releaseWheelFocus(pane);
        }
    }

    static void drawText(Batch batch, BitmapFont font, String text, float x, float top, float alpha) {
        Color color = font.getColor();
        float r = color.r, g = color.g, b = color.b, a = color.a;
        font.setColor(1f, 1f, 1f, alpha);
        try {
            font.draw(batch, text, x, top);
        } finally {
            font.setColor(r, g, b, a);
        }
    }

    /**
     * 抽屉内文字滚动框（scene2d ScrollPane，程序化拼 Style——项目无 Skin JSON；品质改造批7 修正）：
     * <ul>
     *   <li><b>纵向 pan/拖动开启</b>：flickScroll 手势 + flingTime=0（可拖动但无惯性；旧 flickScroll(false) 关掉了
     *       全部手势滚动，触屏/鼠标拖拽均不动）；</li>
     *   <li><b>track/knob 开启</b>：纵向滚动条用现有原创素材（M07 轨 / M08 滑块 九宫格）自绘 + 可交互
     *       （scrollBarTouch：拖滑块、点轨道翻页）；</li>
     *   <li><b>关闭</b>：横滚、过滚、惯性、平滑位移（逐像素对齐）、滚动条淡出。</li>
     * </ul>
     * 滚轮经 {@link #manageWheelFocus} 分配 Stage scroll focus 后由本框消费（不进 boardProcessor）。
     *
     * @param track 纵向滚动条轨（M07 九宫格；null = 不画轨）
     * @param knob  纵向滚动条滑块（M08 九宫格；null = 不画滑块）
     */
    static ScrollPane newTextScrollPane(Actor content, NinePatch track, NinePatch knob) {
        ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle();
        if (track != null) {
            NinePatchDrawable drawable = new NinePatchDrawable(track);
            drawable.setMinWidth(DrawerTextBlock.SCROLLBAR_WIDTH);
            style.vScroll = drawable;
        }
        if (knob != null) {
            NinePatchDrawable drawable = new NinePatchDrawable(knob);
            drawable.setMinWidth(DrawerTextBlock.SCROLLBAR_WIDTH);
            style.vScrollKnob = drawable;
        }
        ScrollPane pane = new ScrollPane(content, style);
        pane.setSmoothScrolling(false);
        pane.setFlickScroll(true); // 纵向 pan/拖动
        pane.setFlingTime(0f); // 关惯性（拖到哪停到哪）
        pane.setOverscroll(false, false);
        pane.setFadeScrollBars(false);
        pane.setScrollBarTouch(true); // track/knob 可交互
        pane.setScrollingDisabled(true, false); // 只纵向滚
        return pane;
    }

    /**
     * 滚轮焦点纪律（品质改造批7）：鼠标<b>实际位于可见滚动框上</b>时接管 Stage scroll focus——
     * enter 分配、exit 归还（仅当焦点仍在本框）。移开即还 null，滚轮落回 multiplexer 下层
     * （boardProcessor 的头像条/装备格/棋盘路由），不再误控旧面板。
     * 隐藏的 Actor 不参与命中（enter 不会来）；因隐藏/遮挡而错过 exit 的场合由持有方显式
     * {@link #releaseWheelFocus} 收口（切页 / 收抽屉 / 模态）。
     */
    static void manageWheelFocus(final ScrollPane pane) {
        pane.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                Stage stage = pane.getStage();
                if (pointer == -1 && stage != null && pane.isVisible() && pane.getMaxY() > 0f) {
                    stage.setScrollFocus(pane);
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (pointer == -1) {
                    releaseWheelFocus(pane);
                }
            }
        });
    }

    /** 若 Stage 滚轮焦点在本框上则归还 null（移开/隐藏/切页/收起/模态时由持有方调用） */
    static void releaseWheelFocus(ScrollPane pane) {
        Stage stage = pane.getStage();
        if (stage != null && stage.getScrollFocus() == pane) {
            stage.setScrollFocus(null);
        }
    }

    // —— 通用小件（无业务状态） ——

    /** 九宫格件（局部定位；纯装饰不可触摸；带内容/文字的件继承它叠画） */
    static class PatchActor extends Actor {
        final NinePatch patch;
        final DrawerGeometry.Rect rect;

        PatchActor(NinePatch patch, DrawerGeometry.Rect rect) {
            this.patch = patch;
            this.rect = rect;
            setPosition(rect.x(), rect.y());
            setSize(rect.width(), rect.height());
            setTouchable(Touchable.disabled);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            drawPatch(batch, patch, getX(), getY(), getWidth(), getHeight(), alphaOf(this, parentAlpha));
        }
    }

    /** 面板底（自带外边框）+ 两条分隔条 */
    static final class PanelBackground extends Actor {
        private final NinePatch panelPatch;
        private final NinePatch dividerPatch;
        private final DrawerGeometry.Rect lower = DrawerGeometry.dividerLower();
        private final DrawerGeometry.Rect upper = DrawerGeometry.dividerUpper();

        PanelBackground(NinePatch panelPatch, NinePatch dividerPatch) {
            this.panelPatch = panelPatch;
            this.dividerPatch = dividerPatch;
            setSize(DrawerGeometry.DRAWER_W, DrawerGeometry.DRAWER_H);
            setTouchable(Touchable.disabled);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float alpha = parentAlpha * getColor().a;
            drawPatch(batch, panelPatch, 0f, 0f, DrawerGeometry.DRAWER_W, DrawerGeometry.DRAWER_H, alpha);
            drawPatch(batch, dividerPatch, lower.x(), lower.y(), lower.width(), lower.height(), alpha);
            drawPatch(batch, dividerPatch, upper.x(), upper.y(), upper.width(), upper.height(), alpha);
        }
    }

    /** 关闭钮（采购单规格不拉伸，14×14；点击请求收起） */
    static final class CloseButton extends Actor {
        private final com.voidvvv.kz_auto_chess_n.render.Assets assets;
        private final ClickListener listener;

        CloseButton(com.voidvvv.kz_auto_chess_n.render.Assets assets, final DrawerPanel.Listener listener) {
            this.assets = assets;
            DrawerGeometry.Rect rect = DrawerGeometry.closeButton();
            setPosition(rect.x(), rect.y());
            setSize(rect.width(), rect.height());
            this.listener = new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    listener.onCloseRequested();
                }
            };
            addListener(this.listener);
        }

        /** 四态取图：按下 &gt; 悬停 &gt; 正常（禁用态预留；14×14 不拉伸件，整图非九宫格） */
        private TextureRegion stateRegion() {
            String id = listener.isPressed() ? UiKeys.BTN_CLOSE_PRESSED.id()
                    : listener.isOver() ? UiKeys.BTN_CLOSE_HOVER.id()
                    : UiKeys.BTN_CLOSE.id();
            return assets.uiRegion(id);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            drawRegion(batch, stateRegion(), getX(), getY(), getWidth(), getHeight(), alphaOf(this, parentAlpha));
        }
    }

    /** 左缘把手（常驻，不随主体滑；九宫格 14×53；点击 = 开 ⇄ 合） */
    static final class Handle extends Actor {
        private final NinePatch patch;

        Handle(NinePatch patch, final DrawerPanel.Listener listener) {
            this.patch = patch;
            DrawerGeometry.Rect rect = DrawerGeometry.handleRect();
            setPosition(rect.x(), rect.y());
            setSize(rect.width(), rect.height());
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    listener.onToggleRequested();
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            drawPatch(batch, patch, getX(), getY(), getWidth(), getHeight(), 1f);
        }
    }
}
