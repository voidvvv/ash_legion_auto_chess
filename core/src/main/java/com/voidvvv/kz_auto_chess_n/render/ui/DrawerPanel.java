package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.input.DrawerHitTest;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.UiKeys;
import com.voidvvv.kz_auto_chess_n.render.StarColors;
import com.voidvvv.kz_auto_chess_n.render.board.UnitFramePainter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 抽屉面板 Actor 树（UI 抽屉化批1 骨架 / 批2 备战席迁入；UI_PLAN §4.1；挂 {@link UnscaledLayer} 下，局部坐标 = 舞台坐标）。
 *
 * <p><b>头像条 = 备战席 9 格的横向形态</b>（Q4）：可见 6 槽、滚轮 / 拖滑块看余下，顺序即备战席槽序——
 * <b>槽号即 bench 下标</b>（合成 / 购买逻辑零改动）；条里只放备战席棋子。头像格本身不可触摸（事件穿过 uiStage
 * 落到 boardProcessor），命中经 {@link DrawerHitTest}（本类实现，屏幕坐标 → 可见槽号）。
 * 选中棋子（板上或头像格上点选）后信息区显示其介绍（{@link DrawerInfoText}，复用 UnitInfoText）。
 *
 * <p>结构：{@code body}（主体，随开合进度左滑；转场控制器持有它做淡出/淡入）+ {@code handle}（左缘把手，常驻不滑）
 * + {@code badge}（收起态「后备 n/9」角标）。把手<b>不随主体滑</b>：收起时主体完全出屏而把手仍须留在左缘。
 *
 * <p>命中口径（计划 §3.5「触摸穿透」）：本组与全屏层同为 {@link Touchable#childrenOnly}（自身不可命中，收起态不遮
 * 左列既有面板）；{@code body} 可命中但无监听——展开态挡住并存期下层的 ③ 背包槽防误点，事件不被吞、经 multiplexer
 * 下传 boardProcessor；头像格 / 底板等装饰件一律 {@link Touchable#disabled}；可触摸件 = 把手 / 关闭钮 / 两标签 / 滚动条滑块。
 *
 * <p>只读实体、零命令；Assets 注入式（render 铁律 4）。渲染段零分配：文案 / 角标按指纹缓存，几何在构造期取定。
 */
public final class DrawerPanel extends Group implements DrawerHitTest {

    /** 抽屉请求回调（BattleScreen 实现：转交 DrawerController，转场封禁位在装配点合流） */
    public interface Listener {
        /** 把手点击：开 ⇄ 合 */
        void onToggleRequested();

        /** 关闭钮：收起 */
        void onCloseRequested();
    }

    /** 描述类文字字号（Q8：层内 12px 原生字比其余 UI 大 25%——描述用 0.75×=9px 与悬停卡持平） */
    private static final float SMALL_TEXT_SCALE = HoverPreviewCard.CARD_TEXT_SCALE;
    private static final float SMALL_LINE_HEIGHT = 12f * SMALL_TEXT_SCALE;
    /** 12px 原生字的行高 */
    private static final float TEXT_HEIGHT = 12f;
    /** 标签文字水平居中：(32 − 2 字 × 12) / 2 */
    private static final float TAB_LABEL_X = 4f;
    private static final float BADGE_TEXT_X = 3f;
    private static final float DESC_PAD_X = 4f;
    private static final float DESC_PAD_TOP = 4f;
    /** 装备大图预览图标位（批3 前的占位画法）：占位图标放大 3 倍（16 → 48px），居上；描述占位行居下 */
    private static final float EQUIP_ICON_SCALE = 3f;
    private static final float EQUIP_DESC_BASELINE = 6f;
    private static final float KNOB_SIZE = 12f;
    private static final float UNIT_FRAME = 32f;
    /** Q2 让位：从抽屉往外拖棋子时面板淡到三成 */
    static final float DIM_ALPHA = 0.3f;

    // —— 占位文案（批3 数据绑定后替换；中文仅出现在字面量与注释） ——
    private static final String EQUIP_TAB_PLACEHOLDER = "（装备迁入见批3）";
    private static final String EQUIP_DESC_PLACEHOLDER = "（装备描述）";
    private static final String TAB_INFO_LABEL = "介绍";
    private static final String TAB_EQUIP_LABEL = "装备";
    private static final List<String> EQUIP_TAB_LINES = Collections.singletonList(EQUIP_TAB_PLACEHOLDER);

    /** 九宫格着色复用（NinePatch.setColor 只收 Color；渲染段零分配） */
    private static final Color TINT = new Color(1f, 1f, 1f, 1f);

    private final Assets assets;
    private final Supplier<RunContext> context;
    private final Viewport uiViewport;
    /** 主体：随进度左滑 */
    private final Group body = new Group();
    /** 收起态角标（随展开淡出；独占一份标题板九宫格——NinePatch 颜色是实例态） */
    private final Badge badge;
    private final TitlePlate title;
    private final Portrait portrait;
    private final DescriptionPane description;
    private final StripSlot[] stripSlots = new StripSlot[DrawerGeometry.STRIP_VISIBLE_SLOTS];
    private final ScrollKnob knob;
    /** 当前标签页（true = 介绍 / false = 装备）——批2 仅介绍页有数据 */
    private boolean infoTabActive = true;
    /** 最近一次 apply 的开合进度（命中判定用） */
    private float progress;
    /** 头像条首槽下标（= 第一个可见槽的 bench 下标） */
    private int scrollIndex;
    private int selectedUnitId = -1;
    private boolean dimmed;
    private boolean dimApplied;
    /** 拖拽落点预览（高亮目标头像格；null = 无拖拽） */
    private PlacementTarget dropTarget;
    /** 复用（unproject 输出，零分配） */
    private final Vector2 touch = new Vector2();
    // —— 文案缓存指纹（渲染段零分配） ——
    private int cachedUnitId = -1;
    private int cachedStar;
    private int cachedSpend;
    private int cachedBenchCount = -1;

    public DrawerPanel(Assets assets, Supplier<RunContext> context, Viewport uiViewport, Listener listener) {
        this.assets = Objects.requireNonNull(assets, "assets 不能为 null");
        this.context = Objects.requireNonNull(context, "context 不能为 null");
        this.uiViewport = Objects.requireNonNull(uiViewport, "uiViewport 不能为 null");
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
        NinePatch tabActivePatch = assets.uiPatch(UiKeys.TAB_ACTIVE.id());
        NinePatch tabInactivePatch = assets.uiPatch(UiKeys.TAB_INACTIVE.id());
        NinePatch handlePatch = assets.uiPatch(UiKeys.HANDLE.id());

        // 主体（自下而上：底板与分隔条 → 信息区 → 头像条 → 装备区 → 关闭钮）
        body.addActor(new PanelBackground(panelPatch, dividerPatch));
        title = new TitlePlate(titlePatch);
        portrait = new Portrait(insetPatch);
        description = new DescriptionPane(insetPatch);
        body.addActor(title);
        body.addActor(portrait); // 立绘位（Q11：选中棋子 idle 帧 ×2）
        body.addActor(description);
        body.addActor(new TabButton(true, tabActivePatch, tabInactivePatch));
        body.addActor(new TabButton(false, tabActivePatch, tabInactivePatch));
        for (int slot = 0; slot < stripSlots.length; slot++) {
            stripSlots[slot] = new StripSlot(slot, slotPatch,
                    assets.uiPatch(UiKeys.SLOT_SELECTED.id()));
            body.addActor(stripSlots[slot]);
        }
        body.addActor(new PatchActor(trackPatch, DrawerGeometry.stripScrollBar()));
        knob = new ScrollKnob(assets.uiRegion(UiKeys.SCROLL_KNOB.id()));
        body.addActor(knob);
        for (int index = 0; index < DrawerGeometry.EQUIP_GRID_COLS * DrawerGeometry.EQUIP_GRID_ROWS; index++) {
            body.addActor(new PatchActor(slotPatch, DrawerGeometry.equipSlot(index, 0)));
        }
        body.addActor(new EquipPreview(insetPatch, assets.uiRegion(UiKeys.ICON_EQUIP_WEAPON.id())));
        body.addActor(new CloseButton(assets.uiRegion(UiKeys.BTN_CLOSE.id()), listener));
        addActor(body);

        badge = new Badge();
        addActor(badge);
        Handle handle = new Handle(handlePatch, listener);
        addActor(handle);
        // 不在构造器里 refresh()：BattleScreen 的 runContext 到 show() 才就绪（与既有面板一致——绑定只在 render 期做）
        apply(0f);
    }

    // —— 每帧驱动（BattleScreen） ——

    /** 每帧刷新（只读实体）：头像条绑定备战席、滚动钳制、选中有效性、文案（指纹变化才重建） */
    public void refresh() {
        RunContext ctx = context.get();
        List<Unit> bench = ctx.getPlayer().getBench();
        scrollIndex = DrawerStripScroll.clamp(scrollIndex, bench.size());
        Unit selected = selectedUnitId >= 0 ? ctx.getPlayer().getUnitById(selectedUnitId) : null;
        if (selected == null) {
            selectedUnitId = -1;
        }
        for (StripSlot slot : stripSlots) {
            slot.bind(bench, scrollIndex, selected, dropTarget);
        }
        refreshInfo(selected, ctx);
        badge.setBenchCount(bench.size());
        DrawerGeometry.Rect track = DrawerGeometry.stripScrollBar();
        knob.setPosition(track.x() + DrawerStripScroll.knobOffsetX(track.width(), (int) KNOB_SIZE, scrollIndex, bench.size()),
                track.y() + (track.height() - KNOB_SIZE) / 2f);
    }

    private void refreshInfo(Unit selected, RunContext ctx) {
        if (selected == null) {
            if (cachedUnitId != -1) {
                cachedUnitId = -1;
                title.setText(DrawerInfoText.NO_SELECTION_TITLE);
                portrait.setUnit(null);
                description.setLines(DrawerInfoText.noSelectionIntro());
            }
            return;
        }
        if (selected.getId() == cachedUnitId && selected.getStar() == cachedStar
                && selected.getSpend() == cachedSpend) {
            return; // 指纹未变：沿用缓存
        }
        cachedUnitId = selected.getId();
        cachedStar = selected.getStar();
        cachedSpend = selected.getSpend();
        title.setText(DrawerInfoText.title(selected));
        portrait.setUnit(selected);
        description.setLines(DrawerInfoText.introLines(selected, ctx.getGameData()));
    }

    /**
     * 按开合进度落位（BattleScreen 每帧调用；须在 transitionController.applyUiPose 之后）：主体 x 取整左滑、
     * 全收起即隐藏；角标随展开淡出；把手不动。让位淡色只在状态切换帧写入，不逐帧覆盖转场姿态写的透明度。
     *
     * @param progress 0 = 收起、1 = 展开（越界钳制）
     */
    public void apply(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        this.progress = p;
        body.setPosition(DrawerGeometry.bodyX(p), DrawerGeometry.DRAWER_Y);
        body.setVisible(p > 0f);
        body.getColor().a = resolveBodyAlpha(body.getColor().a, dimmed, dimApplied);
        dimApplied = dimmed;
        badge.setColor(1f, 1f, 1f, 1f - p);
        badge.setVisible(p < 1f);
    }

    /**
     * 主体透明度合成规则（纯函数，headless 可测）：转场控制器每帧先写姿态透明度（稳态归 1、转场窗内 1−p），
     * 本方法随后落位——<b>让位期间逐帧压到三成</b>（否则被稳态归 1 覆盖；探针实测 bodyAlpha 卡在 1.00）；
     * 退出让位的那一帧还原 1；其余帧不碰（保留转场淡出）。
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

    /** 选中棋子（板上或头像格上点选；unitId &lt; 0 = 取消选中） */
    public void select(int unitId) {
        this.selectedUnitId = unitId;
    }

    public int selectedUnitId() {
        return selectedUnitId;
    }

    /** 让位（Q2：从抽屉往外拖棋子时淡到三成；松手后由调用方解除） */
    public void setDimmed(boolean dimmed) {
        this.dimmed = dimmed;
    }

    /** 拖拽落点预览（高亮目标头像格；null = 无拖拽） */
    public void setDropTarget(PlacementTarget target) {
        this.dropTarget = target;
    }

    /** 抽屉主体 Actor（转场控制器接替 ③ 参数位做淡出 / 淡入） */
    public Actor bodyActor() {
        return body;
    }

    /** 当前标签页是否为「介绍」 */
    public boolean isInfoTabActive() {
        return infoTabActive;
    }

    /** 头像条首槽下标（= 第一个可见槽的 bench 下标） */
    public int scrollIndex() {
        return scrollIndex;
    }

    // —— DrawerHitTest（屏幕坐标；头像格穿透后由 boardProcessor 回查） ——

    @Override
    public int stripSlotAt(int screenX, int screenY) {
        if (progress <= 0f) {
            return -1; // 收起态头像条不可命中
        }
        toLocal(screenX, screenY);
        for (int i = 0; i < stripSlots.length; i++) {
            if (DrawerGeometry.stripSlot(i, 0).contains(touch.x, touch.y)) {
                return scrollIndex + i;
            }
        }
        return -1;
    }

    @Override
    public boolean scrollStrip(int screenX, int screenY, int steps) {
        if (progress <= 0f || steps == 0) {
            return false;
        }
        toLocal(screenX, screenY);
        if (!DrawerGeometry.stripSection().contains(touch.x, touch.y)) {
            return false;
        }
        scrollIndex = DrawerStripScroll.scroll(scrollIndex, context.get().getPlayer().getBench().size(), steps);
        return true;
    }

    /** 屏幕坐标 → 抽屉局部（复用 Vector2，零分配） */
    private void toLocal(int screenX, int screenY) {
        touch.set(screenX, screenY);
        uiViewport.unproject(touch);
        touch.sub(body.getX(), DrawerGeometry.DRAWER_Y);
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

    private static void drawText(Batch batch, BitmapFont font, String text, float x, float y, float alpha) {
        font.setColor(1f, 1f, 1f, alpha);
        font.draw(batch, text, x, y);
        font.setColor(1f, 1f, 1f, 1f); // 用后即还（共用字体纪律）
    }

    private static float alphaOf(Actor actor, float parentAlpha) {
        return parentAlpha * actor.getColor().a;
    }

    /**
     * 盒内垂直居中的文字行顶（libGDX {@code BitmapFont.draw} 的 y 是<b>行顶</b>、文字向下延伸——
     * 不是基线；与 HoverPreviewCard 的用法同口径）。批1 误按基线摆放，标题板文字只露出上沿 2px。
     *
     * @param textH 行高（12px 字 = 12；0.75× 小字 = 9）
     */
    static float centeredTextTop(float boxY, float boxH, float textH) {
        return boxY + boxH - (boxH - textH) / 2f;
    }

    // —— 件 ——

    /** 九宫格件（局部定位；纯装饰不可触摸；带内容/文字的件继承它叠画） */
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
            drawPatch(batch, patch, getX(), getY(), getWidth(), getHeight(), alphaOf(this, parentAlpha));
        }
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
            float alpha = parentAlpha * getColor().a;
            drawPatch(batch, panelPatch, 0f, 0f, DrawerGeometry.DRAWER_W, DrawerGeometry.DRAWER_H, alpha);
            drawPatch(batch, dividerPatch, lower.x(), lower.y(), lower.width(), lower.height(), alpha);
            drawPatch(batch, dividerPatch, upper.x(), upper.y(), upper.width(), upper.height(), alpha);
        }
    }

    /** 标题板 + 棋子名 */
    private final class TitlePlate extends PatchActor {
        private final DrawerGeometry.Rect rect;
        private String text = DrawerInfoText.NO_SELECTION_TITLE;

        TitlePlate(NinePatch patch) {
            super(patch, DrawerGeometry.titlePlate());
            this.rect = DrawerGeometry.titlePlate();
        }

        void setText(String text) {
            this.text = text;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);
            drawText(batch, assets.font(), text, rect.x() + DrawerGeometry.PADDING,
                    centeredTextTop(rect.y(), rect.height(), TEXT_HEIGHT), alphaOf(this, parentAlpha));
        }
    }

    /** 立绘位：选中棋子 idle 帧 ×2（Q11） */
    private final class Portrait extends PatchActor {
        private final DrawerGeometry.Rect rect;
        private Unit unit;

        Portrait(NinePatch patch) {
            super(patch, DrawerGeometry.portrait());
            this.rect = DrawerGeometry.portrait();
        }

        void setUnit(Unit unit) {
            this.unit = unit;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);
            if (unit != null) {
                UnitFramePainter.draw(batch, assets, unit.getTemplate().getId(), PlaceholderKeys.ANIM_IDLE, 0,
                        rect.x(), rect.y(), rect.width(), false, alphaOf(this, parentAlpha), StarColors.of(unit.getStar()));
            }
        }
    }

    /** 描述框（介绍 / 装备两标签页的内容区；9px 小字；批2 介绍页绑数据，装备页占位至批3） */
    private final class DescriptionPane extends PatchActor {
        private final DrawerGeometry.Rect rect;
        private List<String> lines = DrawerInfoText.noSelectionIntro();

        DescriptionPane(NinePatch patch) {
            super(patch, DrawerGeometry.descriptionPane());
            this.rect = DrawerGeometry.descriptionPane();
        }

        void setLines(List<String> lines) {
            this.lines = lines;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);
            BitmapFont font = assets.font();
            font.getData().setScale(SMALL_TEXT_SCALE); // 用后即还（共用字体纪律）
            List<String> shown = infoTabActive ? lines : EQUIP_TAB_LINES;
            float y = rect.top() - DESC_PAD_TOP;
            for (int i = 0; i < shown.size() && y - SMALL_LINE_HEIGHT >= rect.y(); i++) {
                drawText(batch, font, shown.get(i), rect.x() + DESC_PAD_X, y, alphaOf(this, parentAlpha));
                y -= SMALL_LINE_HEIGHT;
            }
            font.getData().setScale(1f);
        }
    }

    /** 标签页按钮（九宫格 32×30；点击切视觉态） */
    private final class TabButton extends Actor {
        private final boolean infoTab;
        private final NinePatch activePatch;
        private final NinePatch inactivePatch;

        TabButton(boolean infoTab, NinePatch activePatch, NinePatch inactivePatch) {
            this.infoTab = infoTab;
            this.activePatch = activePatch;
            this.inactivePatch = inactivePatch;
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
            float alpha = alphaOf(this, parentAlpha);
            drawPatch(batch, infoTab == infoTabActive ? activePatch : inactivePatch,
                    getX(), getY(), getWidth(), getHeight(), alpha);
            drawText(batch, assets.font(), infoTab ? TAB_INFO_LABEL : TAB_EQUIP_LABEL,
                    getX() + TAB_LABEL_X, centeredTextTop(getY(), getHeight(), TEXT_HEIGHT), alpha);
        }
    }

    /** 头像格（不可触摸——穿透给 boardProcessor；显示 bench[scrollIndex + visibleIndex]；选中 / 落点叠选中框） */
    private final class StripSlot extends Actor {
        private final int visibleIndex;
        private final NinePatch slotPatch;
        private final NinePatch selectedPatch;
        private final DrawerGeometry.Rect rect;
        private Unit unit;
        private boolean highlight;

        StripSlot(int visibleIndex, NinePatch slotPatch, NinePatch selectedPatch) {
            this.visibleIndex = visibleIndex;
            this.slotPatch = slotPatch;
            this.selectedPatch = selectedPatch;
            this.rect = DrawerGeometry.stripSlot(visibleIndex, 0);
            setPosition(rect.x(), rect.y());
            setSize(rect.width(), rect.height());
            setTouchable(Touchable.disabled);
        }

        void bind(List<Unit> bench, int firstIndex, Unit selected, PlacementTarget target) {
            int index = firstIndex + visibleIndex;
            unit = index < bench.size() ? bench.get(index) : null;
            boolean dropHere = target instanceof PlacementTarget.Bench
                    && ((PlacementTarget.Bench) target).slotIndex == index;
            highlight = dropHere || (unit != null && unit == selected);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float alpha = alphaOf(this, parentAlpha);
            drawPatch(batch, slotPatch, rect.x(), rect.y(), rect.width(), rect.height(), alpha);
            if (unit != null) {
                UnitFramePainter.draw(batch, assets, unit.getTemplate().getId(), PlaceholderKeys.ANIM_IDLE, 0,
                        rect.x() + (rect.width() - UNIT_FRAME) / 2f, rect.y() + (rect.height() - UNIT_FRAME) / 2f,
                        UNIT_FRAME, false, alpha, StarColors.of(unit.getStar()));
            }
            if (highlight) {
                drawPatch(batch, selectedPatch, rect.x(), rect.y(), rect.width(), rect.height(), alpha);
            }
        }
    }

    /** 头像条滚动条滑块（可拖：头像格穿透了，条内只支持滚轮 + 拖滑块） */
    private final class ScrollKnob extends Actor {
        private final TextureRegion region;
        private final DrawerGeometry.Rect track;

        ScrollKnob(TextureRegion region) {
            this.region = region;
            this.track = DrawerGeometry.stripScrollBar();
            setSize(KNOB_SIZE, KNOB_SIZE);
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    return true; // 捕获：后续拖动事件送到本滑块
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    float offset = event.getStageX() - body.getX() - track.x() - KNOB_SIZE / 2f;
                    scrollIndex = DrawerStripScroll.indexForKnobOffset(track.width(), (int) KNOB_SIZE,
                            (int) offset, context.get().getPlayer().getBench().size());
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            drawRegion(batch, region, getX(), getY(), getWidth(), getHeight(), alphaOf(this, parentAlpha));
        }
    }

    /** 装备大图预览底 + 图标位（占位图标 ×3，居上）+ 装备描述占位行（居下）——批3 替换为真数据 */
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
            float alpha = alphaOf(this, parentAlpha);
            float icon = iconRegion.getRegionWidth() * EQUIP_ICON_SCALE;
            drawRegion(batch, iconRegion, rect.x() + (rect.width() - icon) / 2f, rect.y() + rect.height() - icon - 2f,
                    icon, icon, alpha);
            BitmapFont font = assets.font();
            font.getData().setScale(SMALL_TEXT_SCALE); // 用后即还（共用字体纪律）
            float textWidth = UnitInfoText.columns(EQUIP_DESC_PLACEHOLDER) * SMALL_LINE_HEIGHT;
            drawText(batch, font, EQUIP_DESC_PLACEHOLDER, rect.x() + (rect.width() - textWidth) / 2f,
                    rect.y() + EQUIP_DESC_BASELINE + SMALL_LINE_HEIGHT, alpha); // 行顶 = 底留白 + 行高
            font.getData().setScale(1f);
        }
    }

    /** 关闭钮（采购单规格不拉伸，14×14；点击请求收起） */
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
            drawRegion(batch, region, getX(), getY(), getWidth(), getHeight(), alphaOf(this, parentAlpha));
        }
    }

    /** 收起态角标「后备 n/9」（n = 备战席条目数）；随展开淡出——独占一份标题板九宫格（颜色是实例态） */
    private final class Badge extends Actor {
        private final NinePatch platePatch;
        private final DrawerGeometry.Rect rect;
        private final Color tint = new Color(1f, 1f, 1f, 1f);
        private String text = DrawerInfoText.badgeText(0);

        Badge() {
            UiKeys.Entry plate = UiKeys.TITLE_PLATE;
            this.platePatch = new NinePatch(assets.uiRegion(plate.id()),
                    plate.left(), plate.right(), plate.top(), plate.bottom());
            this.rect = DrawerGeometry.badgeRect();
            setPosition(rect.x(), rect.y());
            setSize(rect.width(), rect.height());
            setTouchable(Touchable.disabled);
        }

        void setBenchCount(int count) {
            if (cachedBenchCount != count) { // 指纹：条目数变化才重建文案（渲染段零分配）
                cachedBenchCount = count;
                text = DrawerInfoText.badgeText(count);
            }
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float alpha = alphaOf(this, parentAlpha);
            // 角标淡出/让位 alpha 写在独占九宫格的自身色上（NinePatch 吃自身颜色）
            tint.set(1f, 1f, 1f, alpha);
            platePatch.setColor(tint);
            beginUntinted(batch, alpha);
            platePatch.draw(batch, getX(), getY(), getWidth(), getHeight());
            endUntinted(batch);
            drawText(batch, assets.font(), text, getX() + BADGE_TEXT_X,
                    centeredTextTop(getY(), getHeight(), TEXT_HEIGHT), alpha);
        }
    }

    /** 左缘把手（常驻，不随主体滑；九宫格 14×53；点击 = 开 ⇄ 合） */
    private static final class Handle extends Actor {
        private final NinePatch patch;

        Handle(NinePatch patch, final Listener listener) {
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
