package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.UiKeys;

import java.util.Objects;

/**
 * 抽屉面板 Actor 树（UI 抽屉化批1·UI_PLAN §4.1；挂 {@link UnscaledLayer} 下，局部坐标 = 舞台坐标）。
 *
 * <p><b>批1 = 骨架：只画外观和开合，不绑数据</b>——头像条 / 装备网格是空槽，信息区与描述是占位文字
 * （数据绑定与拖拽 / 穿透 / 滚动见批2、批3）。布局常量全部取自 {@link DrawerGeometry}；
 * 九宫格件经 {@link Assets#uiPatch}（真素材 → 占位），不拉伸件经 {@link Assets#uiRegion}。
 *
 * <p>结构：{@code body}（主体，随开合进度左滑）+ {@code handle}（左缘把手，常驻不滑）+ {@code badge}
 * （收起态「后备 n/9」角标）。把手<b>不随主体滑</b>：收起时主体完全出屏而把手仍须留在左缘，二者不可能刚性相连。
 *
 * <p>命中口径（计划 §3.5「触摸穿透」）：本组与全屏层同为 {@link Touchable#childrenOnly}（自身不可命中，
 * 收起态不遮左列既有面板）；{@code body} 可命中但无监听——展开态挡住并存期下层的 ③ 背包槽防误点，
 * 事件不被吞、经 multiplexer 下传 boardProcessor；装饰件一律 {@link Touchable#disabled}；
 * 可触摸件 = 把手 / 关闭钮 / 两标签。只读实体、零命令；Assets 注入式（render 铁律 4）。
 */
public final class DrawerPanel extends Group {

    /** 抽屉请求回调（BattleScreen 实现：转交 DrawerController，转场封禁位在装配点合流） */
    public interface Listener {
        /** 把手点击：开 ⇄ 合 */
        void onToggleRequested();

        /** 关闭钮：收起 */
        void onCloseRequested();
    }

    /** 描述类文字字号（Q8：层内 12px 原生字比其余 UI 大 25%——描述用 0.75×=9px 与悬停卡持平） */
    private static final float SMALL_TEXT_SCALE = HoverPreviewCard.CARD_TEXT_SCALE;
    /** 文字基线距件底（像素）：标题板 14 高 / 标签 18 高 / 角标 16 高 */
    private static final float TITLE_BASELINE = 2f;
    private static final float TAB_BASELINE = 4f;
    private static final float BADGE_BASELINE = 2f;
    private static final float BADGE_TEXT_X = 3f;
    /** 装备大图预览图标位：占位图标放大 3 倍（16 → 48px） */
    private static final float EQUIP_ICON_SCALE = 3f;
    private static final float EQUIP_ICON_X = 6f;
    private static final float EQUIP_ICON_Y = 12f;
    private static final float EQUIP_DESC_X = 60f;
    private static final float EQUIP_DESC_BASELINE = 40f;

    // —— 批1 占位文案（批2/3 数据绑定后替换；中文仅出现在字面量与注释） ——
    private static final String TITLE_PLACEHOLDER = "未选中棋子";
    private static final String DESC_INFO_PLACEHOLDER = "（选中棋子后显示介绍）";
    private static final String DESC_EQUIP_PLACEHOLDER = "（选中棋子后显示装备）";
    private static final String EQUIP_DESC_PLACEHOLDER = "（装备描述）";
    private static final String BADGE_PLACEHOLDER = "后备 -/9";
    private static final String TAB_INFO_LABEL = "介绍";
    private static final String TAB_EQUIP_LABEL = "装备";

    private final Assets assets;
    /** 主体：随进度左滑 */
    private final Group body = new Group();
    /** 收起态角标（随展开淡出） */
    private final Actor badge;
    /** 当前标签页（true = 介绍 / false = 装备）——批1 纯视觉切换，不含数据 */
    private boolean infoTabActive = true;

    public DrawerPanel(Assets assets, Listener listener) {
        this.assets = Objects.requireNonNull(assets, "assets 不能为 null");
        Objects.requireNonNull(listener, "listener 不能为 null");
        setTouchable(Touchable.childrenOnly);
        body.setTouchable(Touchable.enabled);
        body.setSize(DrawerGeometry.DRAWER_W, DrawerGeometry.DRAWER_H);

        NinePatch panelPatch = assets.uiPatch(UiKeys.PANEL_BODY.id());
        NinePatch insetPatch = assets.uiPatch(UiKeys.INSET.id());
        NinePatch slotPatch = assets.uiPatch(UiKeys.SLOT.id());
        NinePatch titlePatch = assets.uiPatch(UiKeys.TITLE_PLATE.id());
        NinePatch dividerPatch = assets.uiPatch(UiKeys.DIVIDER_H.id());
        NinePatch trackPatch = assets.uiPatch(UiKeys.SCROLL_TRACK.id());
        TextureRegion tabActiveRegion = assets.uiRegion(UiKeys.TAB_ACTIVE.id());
        TextureRegion tabInactiveRegion = assets.uiRegion(UiKeys.TAB_INACTIVE.id());
        TextureRegion knobRegion = assets.uiRegion(UiKeys.SCROLL_KNOB.id());

        // 主体（自下而上：底板与分隔条 → 信息区 → 头像条 → 装备区 → 关闭钮）
        body.addActor(new PanelBackground(panelPatch, dividerPatch));
        body.addActor(new TitlePlate(titlePatch));
        body.addActor(new PatchActor(insetPatch, DrawerGeometry.portrait())); // 立绘位（批2：选中棋子 idle 帧 ×2）
        body.addActor(new DescriptionPane(insetPatch));
        body.addActor(new TabButton(true, tabActiveRegion, tabInactiveRegion));
        body.addActor(new TabButton(false, tabActiveRegion, tabInactiveRegion));
        for (int slot = 0; slot < DrawerGeometry.STRIP_VISIBLE_SLOTS; slot++) {
            body.addActor(new PatchActor(slotPatch, DrawerGeometry.stripSlot(slot, 0)));
        }
        body.addActor(new StripScrollBar(trackPatch, knobRegion));
        for (int index = 0; index < DrawerGeometry.EQUIP_GRID_COLS * DrawerGeometry.EQUIP_GRID_ROWS; index++) {
            body.addActor(new PatchActor(slotPatch, DrawerGeometry.equipSlot(index, 0)));
        }
        body.addActor(new EquipPreview(insetPatch, assets.uiRegion(UiKeys.ICON_EQUIP_WEAPON.id())));
        body.addActor(new CloseButton(assets.uiRegion(UiKeys.BTN_CLOSE.id()), listener));
        addActor(body);

        badge = new Badge();
        addActor(badge);
        addActor(new Handle(assets.uiRegion(UiKeys.HANDLE.id()), listener));
        apply(0f);
    }

    /**
     * 按开合进度落位（BattleScreen 每帧调用）：主体 x 取整左滑、全收起即隐藏；角标随展开淡出；把手不动。
     *
     * @param progress 0 = 收起、1 = 展开（越界钳制）
     */
    public void apply(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        body.setPosition(DrawerGeometry.bodyX(p), DrawerGeometry.DRAWER_Y);
        body.setVisible(p > 0f);
        badge.setColor(1f, 1f, 1f, 1f - p);
        badge.setVisible(p < 1f);
    }

    /** 当前标签页是否为「介绍」（批1 视觉态） */
    public boolean isInfoTabActive() {
        return infoTabActive;
    }

    // —— 件 ——

    /** 九宫格件（局部定位；纯装饰不可触摸；带文字的件继承它叠画） */
    private static class PatchActor extends Actor {
        private final NinePatch patch;

        PatchActor(NinePatch patch, DrawerGeometry.Rect rect) {
            this.patch = patch;
            setPosition(rect.x(), rect.y());
            setSize(rect.width(), rect.height());
            setTouchable(Touchable.disabled);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            drawPatch(batch, patch, getX(), getY(), getWidth(), getHeight());
        }
    }

    /**
     * 抽屉内一切绘制（整图 / 九宫格）的着色纪律——统一入口：<b>先显式置白</b>、画毕还原白。
     *
     * <p><b>必须自设色</b>：{@code Batch.getColor()} 返回的是 SpriteBatch 内部同一实例（1.14.0 字节码核实），
     * 既有面板的 {@code Color old = batch.getColor() … setColor(old)} 实为空操作，tint 会泄漏给同批次内后续
     * 未自设色的绘制；且 <b>NinePatch.draw 同样被批次颜色相乘</b>（像素采样实证：备战期整块面板被背包槽底色
     * 染到 1/4 亮度，RESULT 期前方面板隐藏后恢复素材原色）。文字走字体自身颜色，不受影响。
     */
    /** 置白（进入一次抽屉绘制前） */
    static void beginUntinted(Batch batch) {
        batch.setColor(Color.WHITE);
    }

    /** 还原白（离开一次抽屉绘制后——不把任何状态留给同批次的后续 Actor） */
    static void endUntinted(Batch batch) {
        batch.setColor(Color.WHITE);
    }

    static void drawPatch(Batch batch, NinePatch patch, float x, float y, float w, float h) {
        beginUntinted(batch);
        patch.draw(batch, x, y, w, h);
        endUntinted(batch);
    }

    /** 整图绘制（同 {@link #drawPatch} 的着色纪律） */
    static void drawRegion(Batch batch, TextureRegion region, float x, float y, float w, float h) {
        beginUntinted(batch);
        batch.draw(region, x, y, w, h);
        endUntinted(batch);
    }

    /** 面板底（自带外边框）+ 两条分隔条 */
    private static final class PanelBackground extends Actor {
        private final NinePatch panelPatch;
        private final NinePatch dividerPatch;
        private final DrawerGeometry.Rect lower;
        private final DrawerGeometry.Rect upper;

        PanelBackground(NinePatch panelPatch, NinePatch dividerPatch) {
            this.panelPatch = panelPatch;
            this.dividerPatch = dividerPatch;
            this.lower = DrawerGeometry.dividerLower();
            this.upper = DrawerGeometry.dividerUpper();
            setTouchable(Touchable.disabled);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            drawPatch(batch, panelPatch, 0f, 0f, DrawerGeometry.DRAWER_W, DrawerGeometry.DRAWER_H);
            drawPatch(batch, dividerPatch, lower.x(), lower.y(), lower.width(), lower.height());
            drawPatch(batch, dividerPatch, upper.x(), upper.y(), upper.width(), upper.height());
        }
    }

    /** 标题板 + 棋子名占位 */
    private final class TitlePlate extends PatchActor {
        private final DrawerGeometry.Rect rect;

        TitlePlate(NinePatch patch) {
            super(patch, DrawerGeometry.titlePlate());
            this.rect = DrawerGeometry.titlePlate();
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);
            BitmapFont font = assets.font();
            font.setColor(1f, 1f, 1f, 1f);
            font.draw(batch, TITLE_PLACEHOLDER, rect.x() + DrawerGeometry.PADDING, rect.y() + TITLE_BASELINE);
        }
    }

    /** 描述框（介绍 / 装备两标签页的内容区；批1 占位文字，9px 小字） */
    private final class DescriptionPane extends PatchActor {
        private final DrawerGeometry.Rect rect;

        DescriptionPane(NinePatch patch) {
            super(patch, DrawerGeometry.descriptionPane());
            this.rect = DrawerGeometry.descriptionPane();
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);
            BitmapFont font = assets.font();
            font.getData().setScale(SMALL_TEXT_SCALE); // 用后即还（共用字体纪律）
            font.setColor(1f, 1f, 1f, 1f);
            font.draw(batch, infoTabActive ? DESC_INFO_PLACEHOLDER : DESC_EQUIP_PLACEHOLDER,
                    rect.x() + DrawerGeometry.PADDING, rect.top() - SMALL_TEXT_SCALE * 12f);
            font.getData().setScale(1f);
        }
    }

    /** 标签页按钮（素材原生 32×18 不拉伸；点击切视觉态） */
    private final class TabButton extends Actor {
        private final boolean infoTab;
        private final TextureRegion activeRegion;
        private final TextureRegion inactiveRegion;

        TabButton(boolean infoTab, TextureRegion activeRegion, TextureRegion inactiveRegion) {
            this.infoTab = infoTab;
            this.activeRegion = activeRegion;
            this.inactiveRegion = inactiveRegion;
            DrawerGeometry.Rect rect = infoTab ? DrawerGeometry.tabInfo() : DrawerGeometry.tabEquip();
            setPosition(rect.x(), rect.y());
            setSize(rect.width(), rect.height());
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    infoTabActive = infoTab;
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            drawRegion(batch, infoTab == infoTabActive ? activeRegion : inactiveRegion,
                    getX(), getY(), getWidth(), getHeight());
            BitmapFont font = assets.font();
            font.setColor(1f, 1f, 1f, 1f);
            font.draw(batch, infoTab ? TAB_INFO_LABEL : TAB_EQUIP_LABEL,
                    getX() + (DrawerGeometry.TAB_W - 24f) / 2f, getY() + TAB_BASELINE);
        }
    }

    /** 头像条横向滚动条（轨 + 滑块；批1 仅外观，不可拖——滚动见批2） */
    private static final class StripScrollBar extends Actor {
        private final NinePatch trackPatch;
        private final TextureRegion knobRegion;
        private final DrawerGeometry.Rect track;

        StripScrollBar(NinePatch trackPatch, TextureRegion knobRegion) {
            this.trackPatch = trackPatch;
            this.knobRegion = knobRegion;
            this.track = DrawerGeometry.stripScrollBar();
            setTouchable(Touchable.disabled);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            drawPatch(batch, trackPatch, track.x(), track.y(), track.width(), track.height());
            float knobSize = knobRegion.getRegionWidth();
            drawRegion(batch, knobRegion, track.x(), track.y() + (track.height() - knobSize) / 2f, knobSize, knobSize);
        }
    }

    /** 装备大图预览底 + 图标位（占位图标 ×3）+ 装备描述占位 */
    private final class EquipPreview extends PatchActor {
        private final TextureRegion iconRegion;
        private final DrawerGeometry.Rect rect;

        EquipPreview(NinePatch patch, TextureRegion iconRegion) {
            super(patch, DrawerGeometry.equipPreview());
            this.iconRegion = iconRegion;
            this.rect = DrawerGeometry.equipPreview();
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);
            float iconSize = iconRegion.getRegionWidth() * EQUIP_ICON_SCALE;
            drawRegion(batch, iconRegion, rect.x() + EQUIP_ICON_X, rect.y() + EQUIP_ICON_Y, iconSize, iconSize);
            BitmapFont font = assets.font();
            font.getData().setScale(SMALL_TEXT_SCALE); // 用后即还（共用字体纪律）
            font.setColor(1f, 1f, 1f, 1f);
            font.draw(batch, EQUIP_DESC_PLACEHOLDER, rect.x() + EQUIP_DESC_X, rect.y() + EQUIP_DESC_BASELINE);
            font.getData().setScale(1f);
        }
    }

    /** 关闭钮（素材原生 14×14；点击请求收起） */
    private static final class CloseButton extends Actor {
        private final TextureRegion region;

        CloseButton(TextureRegion region, final Listener listener) {
            this.region = region;
            DrawerGeometry.Rect rect = DrawerGeometry.closeButton();
            setPosition(rect.x(), rect.y());
            setSize(rect.width(), rect.height());
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    listener.onCloseRequested();
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            drawRegion(batch, region, getX(), getY(), getWidth(), getHeight());
        }
    }

    /**
     * 收起态角标「后备 n/9」（批1 占位文案，批2 绑备战席计数）。随展开淡出（{@link #apply} 写 alpha）——
     * 独占一份标题板九宫格（NinePatch 颜色是实例态，不能污染与标题板共享的那份）。
     */
    private final class Badge extends Actor {
        private final NinePatch platePatch;
        /** 淡出色（NinePatch.setColor 只收 Color；渲染段零分配——复用实例） */
        private final Color tint = new Color(1f, 1f, 1f, 1f);

        Badge() {
            UiKeys.Entry plate = UiKeys.TITLE_PLATE;
            this.platePatch = new NinePatch(assets.uiRegion(plate.id()),
                    plate.left(), plate.right(), plate.top(), plate.bottom());
            DrawerGeometry.Rect rect = DrawerGeometry.badgeRect();
            setPosition(rect.x(), rect.y());
            setSize(rect.width(), rect.height());
            setTouchable(Touchable.disabled);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float alpha = getColor().a * parentAlpha;
            tint.set(1f, 1f, 1f, alpha);
            platePatch.setColor(tint);
            // 角标淡出：独占九宫格的自身色即最终色——只置白批次色（淡出 alpha 在九宫格自身色上，不被抹掉）
            beginUntinted(batch);
            platePatch.draw(batch, getX(), getY(), getWidth(), getHeight());
            endUntinted(batch);
            BitmapFont font = assets.font();
            font.setColor(1f, 1f, 1f, alpha);
            font.draw(batch, BADGE_PLACEHOLDER, getX() + BADGE_TEXT_X, getY() + BADGE_BASELINE);
            font.setColor(1f, 1f, 1f, 1f); // 用后即还（共用字体纪律）
        }
    }

    /** 左缘把手（常驻，不随主体滑；点击 = 开 ⇄ 合） */
    private static final class Handle extends Actor {
        private final TextureRegion region;

        Handle(TextureRegion region, final Listener listener) {
            this.region = region;
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
            drawRegion(batch, region, getX(), getY(), getWidth(), getHeight());
        }
    }
}
