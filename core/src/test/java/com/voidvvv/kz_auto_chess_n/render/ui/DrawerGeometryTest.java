package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.render.UiKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 抽屉几何断言（UI 抽屉化批1；Q7 甲案 244×218 / Q9 / Q10）。
 *
 * <p>坐标系：抽屉与把手在「不缩放层」——局部坐标 = 舞台坐标（640×360，y 向上），与全局缩放无关；
 * ④ 棋盘在世界域（备战期 scale = RENDER_GLOBAL_SCALE / SHOPPING_CAMERA_ZOOM）、⑧① 在 UI 域（0.8）——
 * 「不压 ④⑧①」必须按两域各自的真实缩放换算到舞台坐标再比（UI_PLAN §2.6：备战期两域缩放并不相等）。
 * 常量待主人实机核对后可校准（Q10：界面坐标镜像漂移另案处理，本次一律按实机落点 y-up 写）。
 */
class DrawerGeometryTest {

    private static final float UI_SCALE = GameBalance.RENDER_GLOBAL_SCALE;
    /** 备战期世界域内容缩放（zoom = 1.06/0.8 → 内容缩 0.8/1.06 倍） */
    private static final float WORLD_SCALE_SHOPPING =
            GameBalance.RENDER_GLOBAL_SCALE / GameBalance.SHOPPING_CAMERA_ZOOM;

    /** ⑧ 商店栏顶（实机：底部 0..64 条带——render §九 Q6 勘误）的舞台坐标 */
    private static float shopBarTopStage() {
        return BoardGeometry.screenY(BoardGeometry.VIRTUAL_H - BoardGeometry.SHOP_BAR_Y, UI_SCALE);
    }

    /** ① 顶栏文字底的舞台坐标（TopBar 定位 y = VIRTUAL_H − 18 = 基线；Fusion Pixel 12px 降部约 2px） */
    private static float topBarTextBottomStage() {
        return BoardGeometry.screenY(BoardGeometry.VIRTUAL_H - 18 - 2, UI_SCALE);
    }

    private static boolean inside(DrawerGeometry.Rect outer, DrawerGeometry.Rect inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.x() + inner.width() <= outer.x() + outer.width()
                && inner.y() + inner.height() <= outer.y() + outer.height();
    }

    private static boolean overlaps(DrawerGeometry.Rect a, DrawerGeometry.Rect b) {
        return a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
                && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
    }

    private static DrawerGeometry.Rect shifted(DrawerGeometry.Rect r, int dx, int dy) {
        return new DrawerGeometry.Rect(r.x() + dx, r.y() + dy, r.width(), r.height());
    }

    // —— 尺寸与三分区 ——

    @Test
    @DisplayName("Q7 甲案：抽屉 244×218，展开态舞台矩形 (2,88)~(246,306)；把手 14×72 取自素材源尺寸")
    void drawerSizeMatchesPlan() {
        assertThat(DrawerGeometry.DRAWER_W).isEqualTo(244);
        assertThat(DrawerGeometry.DRAWER_H).isEqualTo(218);
        DrawerGeometry.Rect expanded = DrawerGeometry.drawerRect(1f);
        assertThat(expanded.x()).isEqualTo(DrawerGeometry.DRAWER_X);
        assertThat(expanded.y()).isEqualTo(DrawerGeometry.DRAWER_Y);
        assertThat(expanded.right()).isEqualTo(246);
        assertThat(expanded.top()).isEqualTo(306);
        assertThat(DrawerGeometry.HANDLE_W).isEqualTo(UiKeys.HANDLE.width());
        assertThat(DrawerGeometry.HANDLE_H).isEqualTo(UiKeys.HANDLE.height());
    }

    @Test
    @DisplayName("三分区落在抽屉内、互不压叠、自下而上 = 装备区 / 头像条 / 信息区（比例近草图 40/20/40）")
    void threeSectionsFitInsideDrawerWithoutOverlap() {
        DrawerGeometry.Rect body = new DrawerGeometry.Rect(0, 0, DrawerGeometry.DRAWER_W, DrawerGeometry.DRAWER_H);
        DrawerGeometry.Rect info = DrawerGeometry.infoSection();
        DrawerGeometry.Rect strip = DrawerGeometry.stripSection();
        DrawerGeometry.Rect equip = DrawerGeometry.equipSection();
        for (DrawerGeometry.Rect section : new DrawerGeometry.Rect[]{info, strip, equip}) {
            assertThat(inside(body, section)).as("分区 %s 落在抽屉内", section).isTrue();
            assertThat(section.width()).as("分区 %s 通栏内宽", section).isEqualTo(DrawerGeometry.DRAWER_W - 2 * DrawerGeometry.PADDING);
        }
        assertThat(overlaps(info, strip)).isFalse();
        assertThat(overlaps(strip, equip)).isFalse();
        assertThat(overlaps(info, equip)).isFalse();
        assertThat(equip.y()).isEqualTo(DrawerGeometry.PADDING);
        assertThat(strip.y()).isEqualTo(equip.top());
        assertThat(info.y()).isEqualTo(strip.top());
        assertThat(info.top()).isEqualTo(DrawerGeometry.DRAWER_H - DrawerGeometry.PADDING);
        // 草图比例（约 40/20/40）：信息区 ≥ 头像条、装备区 ≥ 头像条
        assertThat(info.height()).isGreaterThan(strip.height());
        assertThat(equip.height()).isGreaterThan(strip.height());
    }

    @Test
    @DisplayName("信息区内部件：立绘 64×64（Q11 = 单位帧 2 倍）、描述框、两标签竖排，全部落在信息区内且互不压叠")
    void infoSectionPartsFit() {
        DrawerGeometry.Rect info = DrawerGeometry.infoSection();
        DrawerGeometry.Rect title = DrawerGeometry.titlePlate();
        DrawerGeometry.Rect portrait = DrawerGeometry.portrait();
        DrawerGeometry.Rect desc = DrawerGeometry.descriptionPane();
        DrawerGeometry.Rect tabInfo = DrawerGeometry.tabInfo();
        DrawerGeometry.Rect tabEquip = DrawerGeometry.tabEquip();
        for (DrawerGeometry.Rect part : new DrawerGeometry.Rect[]{title, portrait, desc, tabInfo, tabEquip}) {
            assertThat(inside(info, part)).as("部件 %s 落在信息区内", part).isTrue();
        }
        assertThat(portrait.width()).isEqualTo(64);
        assertThat(portrait.height()).isEqualTo(64);
        assertThat(portrait.width()).isEqualTo(2 * BoardGeometry.CELL);
        for (DrawerGeometry.Rect a : new DrawerGeometry.Rect[]{portrait, desc, tabInfo, tabEquip}) {
            assertThat(overlaps(title, a)).isFalse();
        }
        assertThat(overlaps(portrait, desc)).isFalse();
        assertThat(overlaps(desc, tabInfo)).isFalse();
        assertThat(overlaps(desc, tabEquip)).isFalse();
        assertThat(overlaps(tabInfo, tabEquip)).isFalse();
        // 标签竖排：「介绍」在上、「装备」在下，同列等宽
        assertThat(tabInfo.x()).isEqualTo(tabEquip.x());
        assertThat(tabInfo.width()).isEqualTo(tabEquip.width());
        assertThat(tabInfo.y()).isGreaterThan(tabEquip.top());
        // 标签贴描述框右侧、右缘齐内边
        assertThat(tabInfo.x()).isGreaterThanOrEqualTo(desc.right());
        assertThat(tabInfo.right()).isEqualTo(info.right());
    }

    // —— 头像条 / 装备槽（含滚动偏移） ——

    @Test
    @DisplayName("Q4 头像条 = 备战席横向形态：可见 6 槽 × 34（间距 4）、槽号即 bench 下标；滚动偏移使槽整体左移 1 槽位")
    void ownedStripSlotsAndScrollOffset() {
        assertThat(DrawerGeometry.STRIP_VISIBLE_SLOTS).isEqualTo(6);
        assertThat(DrawerGeometry.STRIP_VISIBLE_SLOTS).isLessThanOrEqualTo(GameBalance.BENCH_SIZE);
        assertThat(DrawerGeometry.STRIP_SLOT).isEqualTo(34);
        DrawerGeometry.Rect strip = DrawerGeometry.stripSection();
        List<DrawerGeometry.Rect> slots = new ArrayList<DrawerGeometry.Rect>();
        for (int slot = 0; slot < GameBalance.BENCH_SIZE; slot++) {
            DrawerGeometry.Rect r = DrawerGeometry.stripSlot(slot, 0);
            slots.add(r);
            assertThat(r.width()).isEqualTo(DrawerGeometry.STRIP_SLOT);
            assertThat(r.height()).isEqualTo(DrawerGeometry.STRIP_SLOT);
            if (slot < DrawerGeometry.STRIP_VISIBLE_SLOTS) {
                assertThat(inside(strip, r)).as("可见槽 %d 落在头像条分区内", slot).isTrue();
            }
        }
        for (int slot = 1; slot < DrawerGeometry.STRIP_VISIBLE_SLOTS; slot++) { // 等距、不压叠
            assertThat(slots.get(slot).x() - slots.get(slot - 1).x())
                    .isEqualTo(DrawerGeometry.STRIP_SLOT + DrawerGeometry.STRIP_GAP);
            assertThat(overlaps(slots.get(slot), slots.get(slot - 1))).isFalse();
        }
        // 槽内放得下 32px 单位帧原尺寸
        assertThat(DrawerGeometry.STRIP_SLOT).isGreaterThanOrEqualTo(BoardGeometry.CELL);
        // 滚动偏移：只平移 x、不动 y；偏移一个槽距后槽 1 落到槽 0 原位
        int pitch = DrawerGeometry.STRIP_SLOT + DrawerGeometry.STRIP_GAP;
        DrawerGeometry.Rect scrolled = DrawerGeometry.stripSlot(1, pitch);
        assertThat(scrolled.x()).isEqualTo(slots.get(0).x());
        assertThat(scrolled.y()).isEqualTo(slots.get(1).y());
        assertThat(DrawerGeometry.stripSlot(3, 10).x()).isEqualTo(slots.get(3).x() - 10);
        // 横滚动条紧贴槽行下方、落在头像条分区内
        DrawerGeometry.Rect scroll = DrawerGeometry.stripScrollBar();
        assertThat(inside(strip, scroll)).isTrue();
        assertThat(scroll.top()).isLessThanOrEqualTo(slots.get(0).y());
    }

    @Test
    @DisplayName("Q1 装备网格 = 3 列 × 2 行可见（背包库存，可滚动）：槽 34、行 0 在上；滚动偏移使内容上移")
    void equipmentGridSlotsAndScrollOffset() {
        assertThat(DrawerGeometry.EQUIP_GRID_COLS).isEqualTo(3);
        assertThat(DrawerGeometry.EQUIP_GRID_ROWS).isEqualTo(2);
        DrawerGeometry.Rect grid = DrawerGeometry.equipGrid();
        List<DrawerGeometry.Rect> slots = new ArrayList<DrawerGeometry.Rect>();
        for (int slot = 0; slot < DrawerGeometry.EQUIP_GRID_COLS * DrawerGeometry.EQUIP_GRID_ROWS; slot++) {
            DrawerGeometry.Rect r = DrawerGeometry.equipSlot(slot, 0);
            slots.add(r);
            assertThat(r.width()).isEqualTo(DrawerGeometry.EQUIP_SLOT);
            assertThat(r.height()).isEqualTo(DrawerGeometry.EQUIP_SLOT);
            assertThat(inside(grid, r)).as("装备槽 %d 落在网格内", slot).isTrue();
        }
        assertThat(slots.get(1).x() - slots.get(0).x()).isEqualTo(DrawerGeometry.EQUIP_SLOT + DrawerGeometry.EQUIP_GAP);
        assertThat(slots.get(0).y()).isGreaterThan(slots.get(3).top() - 1); // 行 0 在上（槽 0 高于槽 3）
        // 行距 = 槽 + 间距（向下为负）
        assertThat(slots.get(0).y() - slots.get(3).y()).isEqualTo(DrawerGeometry.EQUIP_SLOT + DrawerGeometry.EQUIP_GAP);
        // 滚动偏移：只平移 y；偏移一行后行 1 落到行 0 原位
        int rowPitch = DrawerGeometry.EQUIP_SLOT + DrawerGeometry.EQUIP_GAP;
        assertThat(DrawerGeometry.equipSlot(3, rowPitch).y()).isEqualTo(slots.get(0).y());
        assertThat(DrawerGeometry.equipSlot(3, rowPitch).x()).isEqualTo(slots.get(3).x());
        // 装备大图预览在网格右侧、落在装备分区内、与网格不压叠
        DrawerGeometry.Rect preview = DrawerGeometry.equipPreview();
        assertThat(inside(DrawerGeometry.equipSection(), preview)).isTrue();
        assertThat(overlaps(grid, preview)).isFalse();
        assertThat(preview.x()).isGreaterThanOrEqualTo(grid.right());
    }

    // —— 把手 / 开合两态 ——

    @Test
    @DisplayName("把手矩形贴左缘：x = 0、宽 14、高 72，垂直居中于抽屉，完整在视口内；把手位置不随开合进度移动")
    void handleFlushWithLeftEdge() {
        DrawerGeometry.Rect handle = DrawerGeometry.handleRect();
        assertThat(handle.x()).isZero();
        assertThat(handle.width()).isEqualTo(14);
        assertThat(handle.height()).isEqualTo(72);
        assertThat((float) handle.y() + handle.height() / 2f)
                .as("把手中心 y = 抽屉中心 y").isCloseTo(DrawerGeometry.DRAWER_Y + DrawerGeometry.DRAWER_H / 2f, within(0.5f));
        assertThat(handle.y()).isPositive();
        assertThat(handle.top()).isLessThanOrEqualTo(BoardGeometry.VIRTUAL_H);
        assertThat(DrawerGeometry.handleRect(0.5f)).isEqualTo(handle); // 与进度无关
    }

    @Test
    @DisplayName("开合两态（舞台坐标）：展开态完整在视口内；收起态主体完全出屏（右缘 ≤ 0）、把手与角标仍完整在视口内")
    void expandedAndCollapsedStates() {
        DrawerGeometry.Rect expanded = DrawerGeometry.drawerRect(1f);
        assertThat(expanded.x()).isGreaterThanOrEqualTo(0);
        assertThat(expanded.y()).isGreaterThanOrEqualTo(0);
        assertThat(expanded.right()).isLessThanOrEqualTo(BoardGeometry.VIRTUAL_W);
        assertThat(expanded.top()).isLessThanOrEqualTo(BoardGeometry.VIRTUAL_H);

        DrawerGeometry.Rect collapsed = DrawerGeometry.drawerRect(0f);
        assertThat(collapsed.y()).isEqualTo(DrawerGeometry.DRAWER_Y);
        assertThat(collapsed.right()).as("收起态主体右缘 ≤ 0（不露边）").isLessThanOrEqualTo(0);
        DrawerGeometry.Rect badge = DrawerGeometry.badgeRect();
        assertThat(badge.x()).isGreaterThanOrEqualTo(DrawerGeometry.handleRect().right());
        assertThat(badge.right()).isLessThanOrEqualTo(BoardGeometry.VIRTUAL_W);
        assertThat(badge.top()).isLessThanOrEqualTo(BoardGeometry.VIRTUAL_H);
        // 角标在把手右上、与之不压叠
        assertThat(overlaps(DrawerGeometry.handleRect(), badge)).isFalse();
    }

    @Test
    @DisplayName("滑动位移：进度 0→1 主体 x 单调右移且逐帧取整（整虚拟像素），展开/收起端点精确")
    void slideOffsetIsSnappedAndMonotonic() {
        assertThat(DrawerGeometry.bodyX(0f)).isEqualTo(DrawerGeometry.DRAWER_X - DrawerGeometry.SLIDE_PX);
        assertThat(DrawerGeometry.bodyX(1f)).isEqualTo(DrawerGeometry.DRAWER_X);
        assertThat(DrawerGeometry.SLIDE_PX).isEqualTo(DrawerGeometry.DRAWER_X + DrawerGeometry.DRAWER_W);
        int prev = Integer.MIN_VALUE;
        for (int i = 0; i <= 200; i++) {
            float p = i / 200f;
            int x = DrawerGeometry.bodyX(p);
            assertThat(x).as("进度 %.3f 位移取整", p).isEqualTo((int) x);
            assertThat(x).isGreaterThanOrEqualTo(prev);
            prev = x;
        }
        // 中途进度主体仍与把手相接（右缘 ≥ 把手左缘），不会出现「主体悬空」
        DrawerGeometry.Rect mid = DrawerGeometry.drawerRect(0.5f);
        assertThat(mid.right()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("交互域判定 isInside：把手任何进度都算「抽屉内」；主体只在展开时算；其余为外")
    void insideTestForOutsideClick() {
        float hx = DrawerGeometry.handleRect().x() + DrawerGeometry.handleRect().width() / 2f;
        float hy = DrawerGeometry.handleRect().y() + DrawerGeometry.handleRect().height() / 2f;
        float bx = DrawerGeometry.DRAWER_X + DrawerGeometry.DRAWER_W / 2f;
        float by = DrawerGeometry.DRAWER_Y + DrawerGeometry.DRAWER_H / 2f;
        assertThat(DrawerGeometry.isInside(hx, hy, 0f)).isTrue();
        assertThat(DrawerGeometry.isInside(hx, hy, 1f)).isTrue();
        assertThat(DrawerGeometry.isInside(bx, by, 1f)).isTrue();
        assertThat(DrawerGeometry.isInside(bx, by, 0f)).as("收起态主体位置不在交互域").isFalse();
        assertThat(DrawerGeometry.isInside(400f, 200f, 1f)).isFalse();
        assertThat(DrawerGeometry.isInside(400f, 200f, 0f)).isFalse();
    }

    // —— 不压 ④⑧①（两域真实缩放换算到舞台坐标） ——

    @Test
    @DisplayName("不压 ④ 棋盘（备战期真实缩放）：抽屉展开态舞台右缘 246 ≤ 棋盘舞台左缘 247.5；把手右缘更靠左")
    void expandedDrawerClearsBoard() {
        float boardLeft = BoardGeometry.screenX(BoardGeometry.BOARD_X, WORLD_SCALE_SHOPPING);
        assertThat(WORLD_SCALE_SHOPPING).as("备战期世界域内容缩放 ≈ 0.755").isCloseTo(0.7547f, within(1e-4f));
        assertThat(boardLeft).isCloseTo(247.55f, within(0.01f));
        assertThat((float) DrawerGeometry.drawerRect(1f).right()).isLessThanOrEqualTo(boardLeft);
        assertThat((float) DrawerGeometry.handleRect().right()).isLessThan(boardLeft);
        // 战斗稳态（zoom = 1/0.8 → 内容 0.8 倍）棋盘更宽：左缘 243.2 —— 仅战斗期只读展开时左缘会压 2.8px（边界登记）
        float battleBoardLeft = BoardGeometry.screenX(BoardGeometry.BOARD_X, GameBalance.RENDER_GLOBAL_SCALE);
        assertThat(battleBoardLeft).isCloseTo(243.2f, within(0.01f));
    }

    @Test
    @DisplayName("不压 ⑧ 商店栏：抽屉底缘 88 ≥ 商店栏顶舞台坐标 87.2；不压 ① 顶栏：抽屉顶缘 306 ≤ 顶栏文字底 308")
    void expandedDrawerClearsShopBarAndTopBar() {
        assertThat(shopBarTopStage()).isCloseTo(87.2f, within(0.01f));
        assertThat(topBarTextBottomStage()).isCloseTo(308f, within(0.01f));
        DrawerGeometry.Rect expanded = DrawerGeometry.drawerRect(1f);
        assertThat((float) expanded.y()).isGreaterThanOrEqualTo(shopBarTopStage());
        assertThat((float) expanded.top()).isLessThanOrEqualTo(topBarTextBottomStage());
        // 把手竖向范围也在同一条带内
        assertThat((float) DrawerGeometry.handleRect().y()).isGreaterThanOrEqualTo(shopBarTopStage());
        assertThat((float) DrawerGeometry.handleRect().top()).isLessThanOrEqualTo(topBarTextBottomStage());
    }

    // —— 九宫格拉伸安全（对照真素材像素核验结论） ——

    @Test
    @DisplayName("九宫格目标尺寸 ≥ 切分线和（不压扁四角）；标签页/把手/关闭钮按素材原生尺寸绘制（真图饰纹跨中段，拉伸会变形）")
    void ninePatchTargetsNeverShrinkBelowSplits() {
        assertThat(UiKeys.PANEL_BODY.left() + UiKeys.PANEL_BODY.right()).isLessThanOrEqualTo(DrawerGeometry.DRAWER_W);
        assertThat(UiKeys.PANEL_BODY.top() + UiKeys.PANEL_BODY.bottom()).isLessThanOrEqualTo(DrawerGeometry.DRAWER_H);
        assertThat(UiKeys.SLOT.left() + UiKeys.SLOT.right()).isLessThanOrEqualTo(DrawerGeometry.STRIP_SLOT);
        assertThat(UiKeys.SLOT.top() + UiKeys.SLOT.bottom()).isLessThanOrEqualTo(DrawerGeometry.STRIP_SLOT);
        DrawerGeometry.Rect title = DrawerGeometry.titlePlate();
        assertThat(title.width()).isGreaterThanOrEqualTo(UiKeys.TITLE_PLATE.left() + UiKeys.TITLE_PLATE.right());
        assertThat(title.height()).isGreaterThanOrEqualTo(UiKeys.TITLE_PLATE.top() + UiKeys.TITLE_PLATE.bottom());
        DrawerGeometry.Rect desc = DrawerGeometry.descriptionPane();
        assertThat(desc.width()).isGreaterThanOrEqualTo(UiKeys.INSET.left() + UiKeys.INSET.right());
        assertThat(desc.height()).isGreaterThanOrEqualTo(UiKeys.INSET.top() + UiKeys.INSET.bottom());
        DrawerGeometry.Rect scroll = DrawerGeometry.stripScrollBar();
        assertThat(scroll.height()).isGreaterThanOrEqualTo(UiKeys.SCROLL_TRACK.top() + UiKeys.SCROLL_TRACK.bottom());
        assertThat(scroll.width()).isGreaterThanOrEqualTo(UiKeys.SCROLL_TRACK.left() + UiKeys.SCROLL_TRACK.right());
        DrawerGeometry.Rect divider = DrawerGeometry.dividerLower();
        assertThat(divider.width()).isGreaterThanOrEqualTo(UiKeys.DIVIDER_H.left() + UiKeys.DIVIDER_H.right());
        assertThat(divider.height()).isEqualTo(UiKeys.DIVIDER_H.height());
        // 原生尺寸件：标签页 32×18（M05/M06 左端饰纹跨切分中段——不拉伸）、把手 14×72（M09 拉槽跨中段）
        assertThat(DrawerGeometry.tabInfo().width()).isEqualTo(UiKeys.TAB_ACTIVE.width());
        assertThat(DrawerGeometry.tabInfo().height()).isEqualTo(UiKeys.TAB_ACTIVE.height());
        assertThat(DrawerGeometry.tabEquip().width()).isEqualTo(UiKeys.TAB_INACTIVE.width());
        assertThat(DrawerGeometry.tabEquip().height()).isEqualTo(UiKeys.TAB_INACTIVE.height());
        assertThat(DrawerGeometry.HANDLE_W).isEqualTo(UiKeys.HANDLE.width());
        assertThat(DrawerGeometry.HANDLE_H).isEqualTo(UiKeys.HANDLE.height());
        assertThat(DrawerGeometry.CLOSE_W).isEqualTo(UiKeys.BTN_CLOSE.width());
        assertThat(DrawerGeometry.CLOSE_H).isEqualTo(UiKeys.BTN_CLOSE.height());
    }

    @Test
    @DisplayName("关闭钮落在标题板内、贴抽屉右上角，不越出抽屉")
    void closeButtonInsideTitlePlate() {
        DrawerGeometry.Rect close = DrawerGeometry.closeButton();
        DrawerGeometry.Rect title = DrawerGeometry.titlePlate();
        assertThat(inside(title, close)).isTrue();
        assertThat(close.right()).isEqualTo(DrawerGeometry.DRAWER_W - DrawerGeometry.PADDING);
        assertThat(close.top()).isEqualTo(title.top());
    }

    @Test
    @DisplayName("Rect 值对象：右/上缘、contains 半开区间；局部矩形 → 舞台矩形随进度平移")
    void rectHelpers() {
        DrawerGeometry.Rect local = new DrawerGeometry.Rect(6, 6, 110, 72);
        assertThat(local.right()).isEqualTo(116);
        assertThat(local.top()).isEqualTo(78);
        assertThat(local.contains(6f, 6f)).isTrue();
        assertThat(local.contains(116f, 78f)).as("右/上缘为开区间").isFalse();
        DrawerGeometry.Rect stage = DrawerGeometry.toStage(local, 1f);
        assertThat(shifted(stage, -DrawerGeometry.DRAWER_X, -DrawerGeometry.DRAWER_Y)).isEqualTo(local);
        DrawerGeometry.Rect half = DrawerGeometry.toStage(local, 0.5f);
        assertThat(half.x()).isEqualTo(local.x() + DrawerGeometry.bodyX(0.5f));
        assertThat(half.y()).isEqualTo(local.y() + DrawerGeometry.DRAWER_Y);
    }
}
