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
    @DisplayName("Q7 甲案：抽屉 244×218，展开态舞台矩形 (2,88)~(246,306)；把手宽/关闭钮取素材源尺寸，把手高为九宫格纵向取值 53（见 handleFlushWithLeftEdge）")
    void drawerSizeMatchesPlan() {
        assertThat(DrawerGeometry.DRAWER_W).isEqualTo(244);
        assertThat(DrawerGeometry.DRAWER_H).isEqualTo(218);
        DrawerGeometry.Rect expanded = DrawerGeometry.drawerRect(1f);
        assertThat(expanded.x()).isEqualTo(DrawerGeometry.DRAWER_X);
        assertThat(expanded.y()).isEqualTo(DrawerGeometry.DRAWER_Y);
        assertThat(expanded.right()).isEqualTo(246);
        assertThat(expanded.top()).isEqualTo(306);
        assertThat(DrawerGeometry.HANDLE_W).isEqualTo(UiKeys.HANDLE.width());
        assertThat(DrawerGeometry.CLOSE_W).isEqualTo(UiKeys.BTN_CLOSE.width());
        assertThat(DrawerGeometry.CLOSE_H).isEqualTo(UiKeys.BTN_CLOSE.height());
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
            assertThat(section.width()).as("分区 %s = 内容列宽（让开把手）", section).isEqualTo(DrawerGeometry.CONTENT_W);
            assertThat(section.x()).as("分区 %s 自内容列起", section).isEqualTo(DrawerGeometry.CONTENT_X);
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
        DrawerGeometry.Rect body = new DrawerGeometry.Rect(0, 0, DrawerGeometry.DRAWER_W, DrawerGeometry.DRAWER_H);
        for (DrawerGeometry.Rect part : new DrawerGeometry.Rect[]{portrait, desc, tabInfo, tabEquip}) {
            assertThat(inside(info, part)).as("部件 %s 落在信息区内", part).isTrue();
        }
        // 标题板通栏（在把手竖向范围之上，不被压）——落在抽屉体内而非自 CONTENT_X 起的信息区
        assertThat(inside(body, title)).as("标题板落在抽屉体内").isTrue();
        assertThat(title.x()).isEqualTo(DrawerGeometry.PADDING);
        assertThat(title.y()).isEqualTo(info.y() + info.height() - title.height());
        assertThat(portrait.width()).isEqualTo(64);
        assertThat(portrait.height()).isEqualTo(64);
        assertThat(portrait.width()).isEqualTo(2 * BoardGeometry.CELL);
        assertThat(portrait.x()).as("立绘自内容列起（让开把手）").isEqualTo(DrawerGeometry.CONTENT_X);
        // 批2·素材返修后：描述 120 + 标签 32（方案示意 124+36 因内容列让位 232→224 各让 4px；9px 字下同为 12 列）
        assertThat(desc.width()).isEqualTo(120);
        assertThat(desc.x()).isEqualTo(portrait.right() + DrawerGeometry.STRIP_GAP);
        assertThat(tabInfo.x()).isEqualTo(desc.right() + DrawerGeometry.STRIP_GAP);
        assertThat(DrawerGeometry.DESC_W + DrawerGeometry.PORTRAIT_W + 2 * DrawerGeometry.STRIP_GAP + DrawerGeometry.TAB_W)
                .as("立绘 + 两道间距 + 描述 + 标签 = 内容列宽").isEqualTo(DrawerGeometry.CONTENT_W);
        for (DrawerGeometry.Rect a : new DrawerGeometry.Rect[]{portrait, desc, tabInfo, tabEquip}) {
            assertThat(overlaps(title, a)).isFalse();
        }
        assertThat(overlaps(portrait, desc)).isFalse();
        assertThat(overlaps(desc, tabInfo)).isFalse();
        assertThat(overlaps(desc, tabEquip)).isFalse();
        assertThat(overlaps(tabInfo, tabEquip)).isFalse();
        // 标签竖排：「介绍」在上、「装备」在下，同列等宽；两枚（30+4+30）正好填满 64 高信息行
        assertThat(tabInfo.x()).isEqualTo(tabEquip.x());
        assertThat(tabInfo.width()).isEqualTo(tabEquip.width());
        assertThat(tabInfo.y()).isGreaterThan(tabEquip.top());
        assertThat(tabInfo.top()).isEqualTo(portrait.top());
        assertThat(tabEquip.y()).isEqualTo(portrait.y());
        assertThat(tabInfo.top() - tabEquip.y()).isEqualTo(DrawerGeometry.INFO_ROW_H);
        // 标签贴描述框右侧、右缘齐内边
        assertThat(tabInfo.x()).isGreaterThanOrEqualTo(desc.right());
        assertThat(tabInfo.right()).isEqualTo(info.right());
    }

    @Test
    @DisplayName("内容列让开把手（批2·骨架观察 a）：把手竖向范围内一切承载内容的件左缘 ≥ CONTENT_X（把手右缘 + 2px）且与把手不压叠；标题板/分隔条在把手之上/下方穿过属通栏例外")
    void contentClearOfHandle() {
        // 把手占到的抽屉局部矩形：舞台 x 0~14 → 局部 −2~12；y = 头像条分区
        DrawerGeometry.Rect handleLocal = new DrawerGeometry.Rect(
                -DrawerGeometry.DRAWER_X, DrawerGeometry.stripSection().y(),
                DrawerGeometry.HANDLE_W, DrawerGeometry.HANDLE_H);
        assertThat(DrawerGeometry.CONTENT_X)
                .as("内容列左缘 = 把手局部右缘 + 间隙").isEqualTo(handleLocal.right() + DrawerGeometry.HANDLE_GAP);
        List<DrawerGeometry.Rect> content = new ArrayList<DrawerGeometry.Rect>();
        content.add(DrawerGeometry.portrait());
        content.add(DrawerGeometry.descriptionPane());
        for (int slot = 0; slot < DrawerGeometry.STRIP_VISIBLE_SLOTS; slot++) {
            content.add(DrawerGeometry.stripSlot(slot, 0));
        }
        content.add(DrawerGeometry.stripScrollBar());
        for (int index = 0; index < DrawerGeometry.EQUIP_GRID_COLS * DrawerGeometry.EQUIP_GRID_ROWS; index++) {
            content.add(DrawerGeometry.equipSlot(index, 0));
        }
        content.add(DrawerGeometry.equipPreview());
        for (DrawerGeometry.Rect part : content) {
            assertThat(part.x()).as("内容件 %s 左缘 ≥ CONTENT_X", part).isGreaterThanOrEqualTo(DrawerGeometry.CONTENT_X);
            assertThat(overlaps(handleLocal, part)).as("内容件 %s 不被把手压住", part).isFalse();
        }
        // 通栏例外：标题板在把手竖向范围之上（不被压）；两条分隔条不承载内容，从把手下方穿过
        assertThat(DrawerGeometry.titlePlate().y()).isGreaterThanOrEqualTo(handleLocal.top());
        assertThat(overlaps(handleLocal, DrawerGeometry.titlePlate())).isFalse();
        assertThat(DrawerGeometry.dividerLower().x()).as("分隔条通栏（把手下穿过）").isEqualTo(DrawerGeometry.PADDING);
        assertThat(DrawerGeometry.dividerUpper().x()).isEqualTo(DrawerGeometry.PADDING);
        // 把手恰夹在两条分隔条之间（下分隔条底 ~ 上分隔条顶）
        assertThat(DrawerGeometry.stripSection().y()).isEqualTo(DrawerGeometry.dividerLower().y());
        assertThat(DrawerGeometry.stripSection().top()).isEqualTo(DrawerGeometry.dividerUpper().top());
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
    @DisplayName("把手矩形贴左缘：x = 0、宽 14（素材源宽）；高 53 = 头像条分区等高对齐（九宫格纵向 72→53 收缩，≥ 上下帽和 24）；位置不随开合进度移动")
    void handleFlushWithLeftEdge() {
        DrawerGeometry.Rect handle = DrawerGeometry.handleRect();
        assertThat(handle.x()).isZero();
        assertThat(handle.width()).isEqualTo(UiKeys.HANDLE.width());
        assertThat(handle.height()).isEqualTo(DrawerGeometry.HANDLE_H);
        // 取值依据：与头像条分区等高对齐（局部 78~131）
        assertThat(handle.height()).isEqualTo(DrawerGeometry.stripSection().height());
        assertThat(handle.y()).isEqualTo(DrawerGeometry.DRAWER_Y + DrawerGeometry.stripSection().y());
        assertThat(handle.top()).isEqualTo(DrawerGeometry.DRAWER_Y + DrawerGeometry.stripSection().top());
        // 九宫格纵向收缩不撕裂：目标高 ≥ 上下切分线和；且确为非原生高（拉伸/收缩生效）
        assertThat(handle.height()).isGreaterThanOrEqualTo(UiKeys.HANDLE.top() + UiKeys.HANDLE.bottom());
        assertThat(handle.height()).isNotEqualTo(UiKeys.HANDLE.height());
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
    @DisplayName("九宫格拉伸安全：目标尺寸 ≥ 切分线和（不压扁四角）；标签页 32×30 纵向拉伸（18→30）、把手 14×53 纵向收缩（72→53）均走九宫格；关闭钮按规格不拉伸")
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
        // 标签页（M05/M06 返修后中段可拉伸）：横向原生宽 32、纵向 18→30
        for (DrawerGeometry.Rect tab : new DrawerGeometry.Rect[]{DrawerGeometry.tabInfo(), DrawerGeometry.tabEquip()}) {
            assertThat(tab.width()).isEqualTo(UiKeys.TAB_ACTIVE.width());
            assertThat(tab.height()).isEqualTo(DrawerGeometry.TAB_H).isGreaterThan(UiKeys.TAB_ACTIVE.height());
            assertThat(tab.width()).isGreaterThanOrEqualTo(UiKeys.TAB_ACTIVE.left() + UiKeys.TAB_ACTIVE.right());
            assertThat(tab.height()).isGreaterThanOrEqualTo(UiKeys.TAB_ACTIVE.top() + UiKeys.TAB_ACTIVE.bottom());
        }
        // 把手（M09 返修后中段木纹可拉伸）：宽 14 原生（切分 0/0）、高 53 纵向收缩
        assertThat(DrawerGeometry.HANDLE_W).isEqualTo(UiKeys.HANDLE.width());
        assertThat(DrawerGeometry.HANDLE_H).isNotEqualTo(UiKeys.HANDLE.height());
        // 关闭钮：采购单规格「不拉伸」——原生 14×14
        assertThat(DrawerGeometry.CLOSE_W).isEqualTo(UiKeys.BTN_CLOSE.width());
        assertThat(DrawerGeometry.CLOSE_H).isEqualTo(UiKeys.BTN_CLOSE.height());
        assertThat(UiKeys.BTN_CLOSE.isNinePatch()).isFalse();
    }

    // —— UI 抽屉化批3：装备区滚动条 / 装备标签页三行 / 描述滚动视口 ——

    @Test
    @DisplayName("装备网格纵向滚动条：恰好落在网格与大图预览之间的 8px 间隙，与网格等高；宽 8 ≥ 轨切分线和 8")
    void equipScrollBarSitsBetweenGridAndPreview() {
        DrawerGeometry.Rect grid = DrawerGeometry.equipGrid();
        DrawerGeometry.Rect bar = DrawerGeometry.equipScrollBar();
        DrawerGeometry.Rect preview = DrawerGeometry.equipPreview();
        assertThat(bar.x()).as("轨左缘 = 网格右缘").isEqualTo(grid.right());
        assertThat(bar.right()).as("轨右缘 = 预览左缘").isEqualTo(preview.x());
        assertThat(bar.width()).isEqualTo(DrawerGeometry.EQUIP_SCROLL_W);
        assertThat(bar.y()).isEqualTo(grid.y());
        assertThat(bar.height()).isEqualTo(grid.height());
        assertThat(bar.width()).isGreaterThanOrEqualTo(UiKeys.SCROLL_TRACK.left() + UiKeys.SCROLL_TRACK.right());
        assertThat(DrawerGeometry.EQUIP_KNOB).isEqualTo(bar.width());
        assertThat(overlaps(grid, bar)).isFalse();
        assertThat(overlaps(preview, bar)).isFalse();
    }

    @Test
    @DisplayName("装备标签页三行（武器/盔甲/饰品）落在描述框内、互不压叠、行数 = 每棋子装备槽数；每行「卸下」钮落在行内")
    void equipTabRowsFitDescriptionPane() {
        DrawerGeometry.Rect pane = DrawerGeometry.descriptionPane();
        assertThat(DrawerGeometry.EQUIP_TAB_ROWS).isEqualTo(GameBalance.EQUIP_SLOTS_PER_UNIT);
        assertThat(DrawerGeometry.EQUIP_TAB_ROWS).isEqualTo(com.voidvvv.kz_auto_chess_n.data.EquipmentSlot.values().length);
        DrawerGeometry.Rect previous = null;
        for (int row = 0; row < DrawerGeometry.EQUIP_TAB_ROWS; row++) {
            DrawerGeometry.Rect r = DrawerGeometry.equipTabRow(row);
            assertThat(inside(pane, r)).as("行 %d 落在描述框内", row).isTrue();
            if (previous != null) {
                assertThat(r.top()).as("行 %d 在上一行之下且不压叠", row).isLessThanOrEqualTo(previous.y());
            }
            previous = r;
            DrawerGeometry.Rect button = DrawerGeometry.equipTabUnequipButton(row);
            assertThat(inside(r, button)).as("行 %d 的卸下钮落在行内", row).isTrue();
            assertThat(button.right()).as("钮右对齐").isEqualTo(r.right() - 2);
            assertThat(button.x()).as("钮左缘留出 槽名(24)+物品名(36) 的文字区").isGreaterThanOrEqualTo(r.x() + 28 + 36);
        }
    }

    @Test
    @DisplayName("描述滚动视口：介绍视口在描述框内（左右各缩 4、上下各缩 2）；装备预览 = 图标位(上 40) + 描述视口(下 32)，两者铺满预览且不压叠")
    void scrollableViewportsFit() {
        DrawerGeometry.Rect pane = DrawerGeometry.descriptionPane();
        DrawerGeometry.Rect viewport = DrawerGeometry.descriptionViewport();
        assertThat(inside(pane, viewport)).isTrue();
        assertThat(viewport.width()).isEqualTo(pane.width() - 8);
        assertThat(viewport.height()).isEqualTo(pane.height() - 4);
        DrawerGeometry.Rect preview = DrawerGeometry.equipPreview();
        DrawerGeometry.Rect icon = DrawerGeometry.equipIconZone();
        DrawerGeometry.Rect desc = DrawerGeometry.equipDescViewport();
        assertThat(inside(preview, icon)).isTrue();
        assertThat(inside(preview, desc)).isTrue();
        assertThat(overlaps(icon, desc)).isFalse();
        assertThat(icon.y()).as("图标位自描述带顶起（预览底 + 32）").isEqualTo(preview.y() + DrawerGeometry.EQUIP_DESC_ZONE_H);
        assertThat(desc.top()).as("描述视口在描述带内四边各缩 2——与图标位之间留 2px 呼吸间隙").isEqualTo(icon.y() - 2);
        assertThat(icon.height()).isEqualTo(preview.height() - DrawerGeometry.EQUIP_DESC_ZONE_H);
        assertThat(desc.width()).isEqualTo(preview.width() - 4);
        assertThat(desc.height()).isEqualTo(DrawerGeometry.EQUIP_DESC_ZONE_H - 4);
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
