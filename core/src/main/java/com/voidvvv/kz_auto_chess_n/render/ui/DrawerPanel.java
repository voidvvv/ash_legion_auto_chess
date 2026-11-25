package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.input.DrawerHitTest;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.UiKeys;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 抽屉面板——装配根（UI 抽屉化批1 骨架 / 批2 备战席迁入 / 批3 批前重构；UI_PLAN §4.1；挂 {@link UnscaledLayer} 下，
 * 局部坐标 = 舞台坐标）。本类只管装配、开合落位、命中接口与每帧驱动；分区各自成类：
 * <ul>
 *   <li>{@link DrawerInfoSection} 信息区（标题板 / 立绘 / 介绍·装备两标签内容区）</li>
 *   <li>{@link DrawerStripSection} 头像条 = 备战席 9 格横向形态（可见 6 + 滚轮/滑块；槽号即 bench 下标）</li>
 *   <li>{@link DrawerEquipSection} 装备区（网格 + 大图预览）</li>
 *   <li>{@link DrawerBadge} 收起态「后备 n/9」角标；{@link DrawerSkin} 绘制皮肤与通用小件（面板底/把手/关闭钮）</li>
 * </ul>
 *
 * <p>结构：{@code body}（主体，随开合进度左滑；转场控制器持有它做淡出/淡入）+ 把手（左缘常驻不滑）+ 角标。
 * 把手<b>不随主体滑</b>：收起时主体完全出屏而把手仍须留在左缘。
 *
 * <p>命中口径（计划 §3.5「触摸穿透」）：本组与全屏层同为 {@link Touchable#childrenOnly}（自身不可命中，收起态不遮
 * 左列既有面板）；{@code body} 可命中但无监听——展开态挡住下层面板防误点，事件不被吞、经 multiplexer 下传
 * boardProcessor；头像格等装饰件不可触摸，可触摸件 = 把手 / 关闭钮 / 两标签 / 滚动条滑块。
 *
 * <p><b>头像条</b>（Q4）：条里只放备战席棋子；选中棋子（板上或头像格上点选）后信息区显示其介绍。
 * 只读实体、零命令；Assets 注入式（render 铁律 4）。
 */
public final class DrawerPanel extends Group implements DrawerHitTest {

    /** 抽屉请求回调（BattleScreen 实现：转交 DrawerController，转场封禁位在装配点合流） */
    public interface Listener {
        /** 把手点击：开 ⇄ 合 */
        void onToggleRequested();

        /** 关闭钮：收起 */
        void onCloseRequested();
    }

    private final Supplier<RunContext> context;
    private final Viewport uiViewport;
    private final EquipPendingState equipPending;
    /** 主体：随进度左滑 */
    private final Group body = new Group();
    /** 收起态角标（随展开淡出） */
    private final DrawerBadge badge;
    private final DrawerInfoSection info;
    private final DrawerStripSection strip;
    private final DrawerEquipSection equip;
    /** 最近一次 apply 的开合进度（命中判定用） */
    private float progress;
    private int selectedUnitId = -1;
    private boolean dimmed;
    private boolean dimApplied;
    /** 拖拽落点预览（高亮目标头像格；null = 无拖拽） */
    private PlacementTarget dropTarget;
    /** 复用（unproject 输出，零分配） */
    private final Vector2 touch = new Vector2();

    public DrawerPanel(Assets assets, Supplier<RunContext> context, Viewport uiViewport,
                       CommandManager commandManager, EquipPendingState equipPending, Listener listener) {
        this.context = Objects.requireNonNull(context, "context 不能为 null");
        this.uiViewport = Objects.requireNonNull(uiViewport, "uiViewport 不能为 null");
        this.equipPending = Objects.requireNonNull(equipPending, "equipPending 不能为 null");
        Objects.requireNonNull(assets, "assets 不能为 null");
        Objects.requireNonNull(commandManager, "commandManager 不能为 null");
        Objects.requireNonNull(listener, "listener 不能为 null");
        setTouchable(Touchable.childrenOnly);
        body.setTouchable(Touchable.enabled);
        body.setSize(DrawerGeometry.DRAWER_W, DrawerGeometry.DRAWER_H);

        NinePatches patches = new NinePatches(assets);
        // 主体（自下而上：底板与分隔条 → 信息区 → 头像条 → 装备区 → 关闭钮）
        body.addActor(new DrawerSkin.PanelBackground(patches.panel, patches.divider));
        info = new DrawerInfoSection(assets, commandManager, patches.inset, patches.title,
                patches.tabActive, patches.tabInactive);
        strip = new DrawerStripSection(assets, patches.slot, patches.slotSelected, patches.track,
                assets.uiRegion(UiKeys.SCROLL_KNOB.id()));
        equip = new DrawerEquipSection(assets, patches.slot, patches.slotSelected, patches.inset, patches.track,
                equipPending);
        body.addActor(info);
        body.addActor(strip);
        body.addActor(equip);
        body.addActor(new DrawerSkin.CloseButton(assets, listener));
        addActor(body);

        badge = new DrawerBadge(assets);
        addActor(badge);
        addActor(new DrawerSkin.Handle(patches.handle, listener));
        // 不在构造器里 refresh()：BattleScreen 的 runContext 到 show() 才就绪（与既有面板一致——绑定只在 render 期做）
        apply(0f);
    }

    /** 九宫格集合（构造期一次取齐；Assets 按 id 缓存 NinePatch） */
    private static final class NinePatches {
        final NinePatch panel;
        final NinePatch divider;
        final NinePatch inset;
        final NinePatch title;
        final NinePatch tabActive;
        final NinePatch tabInactive;
        final NinePatch slot;
        final NinePatch slotSelected;
        final NinePatch track;
        final NinePatch handle;

        NinePatches(Assets assets) {
            panel = assets.uiPatch(UiKeys.PANEL_BODY.id());
            divider = assets.uiPatch(UiKeys.DIVIDER_H.id());
            inset = assets.uiPatch(UiKeys.INSET.id());
            title = assets.uiPatch(UiKeys.TITLE_PLATE.id());
            tabActive = assets.uiPatch(UiKeys.TAB_ACTIVE.id());
            tabInactive = assets.uiPatch(UiKeys.TAB_INACTIVE.id());
            slot = assets.uiPatch(UiKeys.SLOT.id());
            slotSelected = assets.uiPatch(UiKeys.SLOT_SELECTED.id());
            track = assets.uiPatch(UiKeys.SCROLL_TRACK.id());
            handle = assets.uiPatch(UiKeys.HANDLE.id());
        }
    }

    // —— 每帧驱动（BattleScreen） ——

    /**
     * 每帧刷新（只读实体）：待定失配自愈、头像条绑定备战席、选中有效性、信息区文案（指纹变化才重建）、
     * 装备网格绑定背包、角标计数。
     *
     * @param dragging 拖拽中（装备格悬停预览抑制，口径 B4-2）
     */
    public void refresh(boolean dragging) {
        RunContext ctx = context.get();
        Player player = ctx.getPlayer();
        GamePhase phase = ctx.getRunState().getPhase();
        DrawerEquipText.reconcilePending(equipPending, player);
        List<Unit> bench = player.getBench();
        selectedUnitId = DrawerSelection.resolveSelectedId(ctx, selectedUnitId); // 棋子已不在名单（卖出/合并）→ 清选中
        Unit selected = selectedUnitId >= 0 ? player.getUnitById(selectedUnitId) : null;
        info.setPhase(phase);
        equip.setPhase(phase);
        info.setUnit(selected, ctx.getGameData());
        badge.setBenchCount(bench.size());
        strip.bind(bench, selected, dropTarget);
        equip.bind(player.getInventory(), dragging);
    }

    /**
     * 按开合进度落位（BattleScreen 每帧调用；须在 transitionController.applyUiPose 之后）：主体 x 取整左滑、
     * 全收起即隐藏；角标随展开淡出；把手不动。让位淡色按 {@link DrawerSkin#resolveBodyAlpha} 合成。
     * 全收起帧释放抽屉内滚动框的滚轮焦点（批7：关抽屉后滚轮不得继续误控旧面板——
     * 隐藏的 Actor 收不到 exit 事件，须在此显式收口）。
     *
     * @param progress 0 = 收起、1 = 展开（越界钳制）
     */
    public void apply(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        this.progress = p;
        body.setPosition(DrawerGeometry.bodyX(p), DrawerGeometry.DRAWER_Y);
        body.setVisible(p > 0f);
        if (p <= 0f) {
            info.releaseWheelFocus();
            equip.releaseWheelFocus();
        }
        body.getColor().a = DrawerSkin.resolveBodyAlpha(body.getColor().a, dimmed, dimApplied);
        dimApplied = dimmed;
        badge.setColor(1f, 1f, 1f, 1f - p);
        badge.setVisible(p < 1f);
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
        return info.isInfoTabActive();
    }

    /** 头像条首槽下标（= 第一个可见槽的 bench 下标） */
    public int scrollIndex() {
        return strip.scrollIndex();
    }

    /** 装备网格首个可见行（库存下标 = 行 × 3） */
    public int equipFirstRow() {
        return equip.firstRow();
    }

    /** 滚轮事件可能先于 Stage 的 enter/exit 分派，按事件当时的实际命中校验焦点。 */
    public void syncWheelFocus(Stage stage, Actor hit) {
        if (hit instanceof ScrollPane && hit.isDescendantOf(body) && ((ScrollPane) hit).getMaxY() > 0f) {
            stage.setScrollFocus(hit);
        } else {
            Actor focus = stage.getScrollFocus();
            if (focus instanceof ScrollPane && focus.isDescendantOf(body)) {
                stage.setScrollFocus(null);
            }
        }
    }

    // —— DrawerHitTest（屏幕坐标；头像格穿透后由 boardProcessor 回查） ——

    @Override
    public int stripSlotAt(int screenX, int screenY) {
        if (progress <= 0f) {
            return -1; // 收起态头像条不可命中
        }
        toLocal(screenX, screenY);
        for (int i = 0; i < DrawerGeometry.STRIP_VISIBLE_SLOTS; i++) {
            if (DrawerGeometry.stripSlot(i, 0).contains(touch.x, touch.y)) {
                return strip.scrollIndex() + i;
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
        strip.scrollBy(steps, context.get().getPlayer().getBench().size());
        return true;
    }

    @Override
    public boolean scrollEquip(int screenX, int screenY, int steps) {
        if (progress <= 0f || steps == 0) {
            return false;
        }
        toLocal(screenX, screenY);
        if (!DrawerGeometry.equipGrid().contains(touch.x, touch.y)
                && !DrawerGeometry.equipScrollBar().contains(touch.x, touch.y)) {
            return false;
        }
        equip.scrollRows(steps, context.get().getPlayer().getInventory().size());
        return true;
    }

    /** 屏幕坐标 → 抽屉局部（复用 Vector2，零分配） */
    private void toLocal(int screenX, int screenY) {
        touch.set(screenX, screenY);
        uiViewport.unproject(touch);
        touch.sub(body.getX(), DrawerGeometry.DRAWER_Y);
    }
}
