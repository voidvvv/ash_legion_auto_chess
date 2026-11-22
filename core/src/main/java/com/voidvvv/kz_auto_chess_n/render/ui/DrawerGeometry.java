package com.voidvvv.kz_auto_chess_n.render.ui;

/**
 * 抽屉几何（UI 抽屉化批1·Q7 甲案 / Q4 / Q1 / Q10；纯 Java 可测，零 Gdx）。
 *
 * <p>坐标系：抽屉挂在「不缩放层」（{@link UnscaledLayer}），故<b>抽屉局部坐标 = 舞台坐标</b>
 * （640×360，y 向上），与全局缩放 0.8 无关。两种原点：
 * <ul>
 *   <li><b>抽屉局部</b>：原点 = 抽屉主体左下角——分区/部件矩形用此（{@link #infoSection} 等族）；</li>
 *   <li><b>舞台</b>：{@link #drawerRect(float)} 随开合进度左移；把手 {@link #handleRect} 常驻左缘不随进度动。</li>
 * </ul>
 *
 * <p><b>全部常量待主人实机核对后可校准</b>（Q10：界面坐标镜像漂移另案处理，本次一律按实机落点 y-up 写）。
 * 内部预算沿 UI_PLAN §3.2 甲案示意（内边距 6，自下而上 装备区 / 头像条 / 信息区），并按实机核验做过两处修订：
 * <ol>
 *   <li><b>内容列让开把手</b>（批2）：把手是常驻左缘的竖木牌（舞台 x 0~14），展开时压在面板左缘上；
 *       头像槽 / 立绘 / 滚动条等会放内容的件一律自 {@link #CONTENT_X} 起（把手右缘 + 2px 间隙），
 *       否则首槽棋子左侧 4px 被把手盖住。标题板与分隔条仍通栏（它们不承载可点内容，从把手下方穿过）。</li>
 *   <li><b>标签页与把手走九宫格拉伸</b>（批2·素材返修后撤掉原生尺寸对策）：标签 32×30（横向原生宽、纵向 18→30，
 *       两枚竖排正好填满 64 高信息行）；把手纵向取 {@link #HANDLE_H}（与头像条分区等高对齐，见常量注释）。
 *       方案示意的「描述 124 + 标签 36」因内容列让位（232→224）改为「描述 120 + 标签 32」——9px 小字下列数不变（12 列）。</li>
 * </ol>
 *
 * <p>开合进度 progress ∈ [0,1]：0 = 收起（主体完全出屏）、1 = 展开。
 */
public final class DrawerGeometry {

    // —— 主体与把手（舞台坐标） ——

    /** 展开态主体左缘（贴屏幕左缘留 2px） */
    public static final int DRAWER_X = 2;
    /** 展开态主体底缘（舞台 88 > ⑧ 商店栏顶 87.2；顶缘 306 < ① 顶栏文字底 308） */
    public static final int DRAWER_Y = 88;
    /** Q7 甲案：244 宽（右缘 246 < ④ 棋盘备战期舞台左缘 247.5） */
    public static final int DRAWER_W = 244;
    /** Q7 甲案：218 高 */
    public static final int DRAWER_H = 218;
    /** 滑动距离 = 左缘 + 宽：收起态主体右缘恰为 0（完全出屏不露边） */
    public static final int SLIDE_PX = DRAWER_X + DRAWER_W;
    /** 左缘竖把手宽 = 素材源宽（M09 14；横向切分 0/0 不拉伸），x = 0 贴屏幕左边 */
    public static final int HANDLE_W = 14;

    // —— 内部预算（抽屉局部坐标；y 向上） ——

    public static final int PADDING = 6;
    /** 把手与内容的间隙 */
    public static final int HANDLE_GAP = 2;
    /** 分隔条（M11 24×4，横向拉伸到内宽） */
    public static final int DIVIDER_H = 4;
    /** 信息区：标题板 14 高 + 立绘/描述行 64 高 */
    public static final int TITLE_H = 14;
    public static final int INFO_ROW_H = 64;
    /** Q11：立绘 = 单位 idle 帧放大 2 倍（64px） */
    public static final int PORTRAIT_W = 64;
    public static final int PORTRAIT_H = 64;
    /** 标签页：源 32×18 九宫格——横向取原生宽、纵向拉伸到 30（两枚 + 4px 间隙 = 64 高信息行） */
    public static final int TAB_W = 32;
    public static final int TAB_H = 30;
    public static final int TAB_GAP = 4;
    /** Q4：头像条可见 6 槽 × 34（槽内放 32px 单位帧原尺寸），间距 4 */
    public static final int STRIP_VISIBLE_SLOTS = 6;
    public static final int STRIP_SLOT = 34;
    public static final int STRIP_GAP = 4;
    /** 头像条横向滚动条（M07 轨：高取切分线和 8 = 不压扁四角） */
    public static final int STRIP_SCROLL_H = 8;
    /** Q1：装备网格 3 列 × 2 行可见（背包库存，可滚动），槽 34、间距 4 */
    public static final int EQUIP_GRID_COLS = 3;
    public static final int EQUIP_GRID_ROWS = 2;
    public static final int EQUIP_SLOT = 34;
    public static final int EQUIP_GAP = 4;
    /** 关闭钮：采购单规格「不拉伸」，源 14×14 */
    public static final int CLOSE_W = 14;
    public static final int CLOSE_H = 14;
    /** 收起态「后备 n/9」小角标（Q2）：把手右上；54 容 12px 字「后备 -/9」= 48px + 左右各 3px */
    public static final int BADGE_W = 54;
    public static final int BADGE_H = 16;

    // —— 纵向标尺（自下而上；局部 y） ——
    private static final int EQUIP_Y = PADDING;                                   // 6
    private static final int EQUIP_H = EQUIP_GRID_ROWS * EQUIP_SLOT + EQUIP_GAP;   // 72
    private static final int DIVIDER_LOWER_Y = EQUIP_Y + EQUIP_H;                  // 78
    private static final int SCROLL_Y = DIVIDER_LOWER_Y + DIVIDER_H + 1;           // 83
    private static final int STRIP_Y = SCROLL_Y + STRIP_SCROLL_H + 2;              // 93
    private static final int DIVIDER_UPPER_Y = STRIP_Y + STRIP_SLOT;               // 127
    private static final int INFO_ROW_Y = DIVIDER_UPPER_Y + DIVIDER_H + 1;         // 132
    private static final int TITLE_Y = DRAWER_H - PADDING - TITLE_H;               // 198

    /** 头像条分区（下分隔条底 ~ 上分隔条顶）的局部起止——把手与之等高对齐 */
    private static final int STRIP_SECTION_Y = DIVIDER_LOWER_Y;                    // 78
    private static final int STRIP_SECTION_TOP = DIVIDER_UPPER_Y + DIVIDER_H;      // 131

    /** 内容列左缘 = 把手占到的局部右缘（HANDLE_W − DRAWER_X）+ 间隙：把手竖向范围内的内容件一律自此起 */
    public static final int CONTENT_X = (HANDLE_W - DRAWER_X) + HANDLE_GAP;        // 14
    /** 内容列宽（到底内边） */
    public static final int CONTENT_W = DRAWER_W - PADDING - CONTENT_X;            // 224
    /** 描述框宽 = 内容列 − 立绘 64 − 两道间距 − 标签 32 */
    public static final int DESC_W = CONTENT_W - PORTRAIT_W - 2 * STRIP_GAP - TAB_W; // 120

    /**
     * 把手高（九宫格纵向拉伸/收缩，M09 源 72，切分 0/0/12/12）。
     * 取值依据：<b>与它所展开的头像条分区等高并对齐</b>（局部 y 78~131）——把手在语义上「拉出的是备战席」，
     * 收起态与头像条落在同一竖带，展开后也恰好夹在两条分隔条之间；高度 53 ≥ 上下帽之和 24，中段木纹收缩不撕裂。
     */
    public static final int HANDLE_H = STRIP_SECTION_TOP - STRIP_SECTION_Y;        // 53

    private DrawerGeometry() {
    }

    /** 不可变矩形（整数虚拟像素；右/上缘为开区间） */
    public static final class Rect {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        public Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int right() {
            return x + width;
        }

        public int top() {
            return y + height;
        }

        /** 半开区间 [x, x+w) × [y, y+h) */
        public boolean contains(float px, float py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Rect)) {
                return false;
            }
            Rect r = (Rect) other;
            return x == r.x && y == r.y && width == r.width && height == r.height;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * (31 * x + y) + width) + height;
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + " " + width + "x" + height + ")";
        }
    }

    // —— 开合（舞台坐标） ——

    /**
     * 主体左下角的舞台 x（整虚拟像素吸附：逐帧 Math.round，render §八）。
     * progress 0 = 收起（x = DRAWER_X − SLIDE_PX，主体完全出屏）、1 = 展开（x = DRAWER_X）。
     */
    public static int bodyX(float progress) {
        return DRAWER_X - Math.round(SLIDE_PX * (1f - clamp(progress)));
    }

    /** 主体舞台矩形（随开合进度左移） */
    public static Rect drawerRect(float progress) {
        return new Rect(bodyX(progress), DRAWER_Y, DRAWER_W, DRAWER_H);
    }

    /** 把手舞台矩形（常驻左缘，不随进度移动；重载仅为调用方语义清晰） */
    public static Rect handleRect() {
        return new Rect(0, DRAWER_Y + STRIP_SECTION_Y, HANDLE_W, HANDLE_H);
    }

    /** 同 {@link #handleRect()}（进度无关——签名对齐 {@link #drawerRect(float)} 便于调用方统一传进度） */
    public static Rect handleRect(float progress) {
        return handleRect();
    }

    /** 收起态角标舞台矩形（把手右上） */
    public static Rect badgeRect() {
        Rect handle = handleRect();
        return new Rect(handle.right() + 2, handle.top() - BADGE_H, BADGE_W, BADGE_H);
    }

    /**
     * 抽屉交互域判定（「点抽屉外收起」用，舞台坐标）：把手恒算内；主体/角标只在展开中算内。
     * 收起态主体位置（x ≤ 0）不算内——此时抽屉并未展开。
     */
    public static boolean isInside(float stageX, float stageY, float progress) {
        if (handleRect().contains(stageX, stageY)) {
            return true;
        }
        if (progress > 0f) {
            return drawerRect(progress).contains(stageX, stageY);
        }
        return false;
    }

    /** 抽屉局部矩形 → 舞台矩形（随进度平移） */
    public static Rect toStage(Rect local, float progress) {
        return new Rect(local.x() + bodyX(progress), local.y() + DRAWER_Y, local.width(), local.height());
    }

    // —— 三分区（抽屉局部坐标；自下而上 装备区 / 头像条 / 信息区，相邻共边、互不压叠；均自内容列起） ——

    /** 上段·信息区：上分隔条之上到顶内边（标题板 + 立绘/描述/标签行） */
    public static Rect infoSection() {
        return new Rect(CONTENT_X, STRIP_SECTION_TOP, CONTENT_W, DRAWER_H - PADDING - STRIP_SECTION_TOP);
    }

    /** 中段·头像条：下分隔条之上到上分隔条之上（两条分隔条归本段；含滚动条与槽行） */
    public static Rect stripSection() {
        return new Rect(CONTENT_X, STRIP_SECTION_Y, CONTENT_W, STRIP_SECTION_TOP - STRIP_SECTION_Y);
    }

    /** 下段·装备区：底内边到装备网格顶（装备网格 + 大图预览） */
    public static Rect equipSection() {
        return new Rect(CONTENT_X, EQUIP_Y, CONTENT_W, EQUIP_H);
    }

    // —— 信息区部件 ——

    /** 标题板（棋子名）：通栏内宽 × 14（在把手竖向范围之上，可通栏） */
    public static Rect titlePlate() {
        return new Rect(PADDING, TITLE_Y, DRAWER_W - 2 * PADDING, TITLE_H);
    }

    /** 关闭钮：贴标题板右上角（落在标题板内） */
    public static Rect closeButton() {
        return new Rect(DRAWER_W - PADDING - CLOSE_W, TITLE_Y + TITLE_H - CLOSE_H, CLOSE_W, CLOSE_H);
    }

    /** 立绘位：内容列左下角起 64×64（Q11） */
    public static Rect portrait() {
        return new Rect(CONTENT_X, INFO_ROW_Y, PORTRAIT_W, PORTRAIT_H);
    }

    /** 描述框：立绘右侧（介绍 / 装备两标签页的内容区） */
    public static Rect descriptionPane() {
        return new Rect(portrait().right() + STRIP_GAP, INFO_ROW_Y, DESC_W, INFO_ROW_H);
    }

    /** 「介绍」标签（上）：32×30（九宫格纵向拉伸），贴描述框右侧、右缘齐内边 */
    public static Rect tabInfo() {
        return new Rect(DRAWER_W - PADDING - TAB_W, tabEquip().top() + TAB_GAP, TAB_W, TAB_H);
    }

    /** 「装备」标签（下）：两标签整体在信息行内垂直居中（30 + 4 + 30 = 64） */
    public static Rect tabEquip() {
        int blockH = 2 * TAB_H + TAB_GAP;
        return new Rect(DRAWER_W - PADDING - TAB_W, INFO_ROW_Y + (INFO_ROW_H - blockH) / 2, TAB_W, TAB_H);
    }

    // —— 头像条 ——

    /**
     * 头像条槽（抽屉局部坐标）。槽号 = 备战席 bench 下标（Q4：顺序即备战席槽序，合成/购买逻辑零改动）。
     *
     * @param scrollPx 横向滚动偏移（≥ 0，像素）：内容左移以露出后面的槽
     */
    public static Rect stripSlot(int slot, int scrollPx) {
        int pitch = STRIP_SLOT + STRIP_GAP;
        return new Rect(CONTENT_X + slot * pitch - scrollPx, STRIP_Y, STRIP_SLOT, STRIP_SLOT);
    }

    /** 头像条横向滚动条轨（槽行正下方；与 6 槽等宽 = 内容列宽） */
    public static Rect stripScrollBar() {
        return new Rect(CONTENT_X, SCROLL_Y, CONTENT_W, STRIP_SCROLL_H);
    }

    // —— 装备区 ——

    /** 装备网格外框（3 列 × 2 行可见） */
    public static Rect equipGrid() {
        return new Rect(CONTENT_X, EQUIP_Y,
                EQUIP_GRID_COLS * EQUIP_SLOT + (EQUIP_GRID_COLS - 1) * EQUIP_GAP,
                EQUIP_GRID_ROWS * EQUIP_SLOT + (EQUIP_GAP));
    }

    /** 装备网格纵向滚动条（Q1 可滚动；M07 轨横竖共用）——落在网格与大图预览之间的 8px 间隙，宽取切分线和 8 */
    public static final int EQUIP_SCROLL_W = 8;
    /** 装备滚动条滑块边长（M08 滑块 12 缩到轨宽 8） */
    public static final int EQUIP_KNOB = 8;

    public static Rect equipScrollBar() {
        Rect grid = equipGrid();
        return new Rect(grid.right(), grid.y(), EQUIP_SCROLL_W, grid.height());
    }

    // —— 装备标签页（「装备」标签 = 选中棋子三件；行数 = 每棋子装备槽数） ——

    /** 装备标签页行数 = 每棋子装备槽数（GameBalance.EQUIP_SLOTS_PER_UNIT：武器 + 盔甲 + 饰品） */
    public static final int EQUIP_TAB_ROWS = 3;
    /** 三行（武器 / 盔甲 / 饰品）行高：3 × 18 = 54，落在 64 高的描述框内 */
    public static final int EQUIP_TAB_ROW_H = 18;
    public static final int EQUIP_TAB_ROW_BOX_H = 16;

    /** 装备标签页第 row 行（row 0 = 武器，在上）的行矩形：与描述框同列，左右内缩 2 */
    public static Rect equipTabRow(int row) {
        Rect pane = descriptionPane();
        int y = pane.top() - 4 - row * EQUIP_TAB_ROW_H - EQUIP_TAB_ROW_BOX_H;
        return new Rect(pane.x() + 2, y, pane.width() - 4, EQUIP_TAB_ROW_BOX_H);
    }

    /** 装备标签页行内「卸下」按钮（右对齐，42×14，落在行矩形内） */
    public static Rect equipTabUnequipButton(int row) {
        Rect r = equipTabRow(row);
        return new Rect(r.right() - 44, r.y() + 1, 42, EQUIP_TAB_ROW_BOX_H - 2);
    }

    // —— 滚动视口（scene2d ScrollPane；批3 硬验收：最长文案能滚到底） ——

    /** 介绍描述滚动视口：描述框内缩（左右 4、上下 2）；净宽 112 → 9px 小字全角 12 列 */
    public static Rect descriptionViewport() {
        Rect pane = descriptionPane();
        return new Rect(pane.x() + 4, pane.y() + 2, pane.width() - 8, pane.height() - 4);
    }

    /** 装备大图预览：图标位（上 40 高）+ 装备描述滚动框（下 32 高，左右/底下内缩 2） */
    public static final int EQUIP_DESC_ZONE_H = 32;

    public static Rect equipIconZone() {
        Rect preview = equipPreview();
        return new Rect(preview.x(), preview.y() + EQUIP_DESC_ZONE_H, preview.width(),
                preview.height() - EQUIP_DESC_ZONE_H);
    }

    /** 装备描述滚动视口：净宽 98 → 全角 10 列；净高 28 → 3 行 */
    public static Rect equipDescViewport() {
        Rect preview = equipPreview();
        return new Rect(preview.x() + 2, preview.y() + 2, preview.width() - 4, EQUIP_DESC_ZONE_H - 4);
    }

    /**
     * 装备槽（抽屉局部坐标）：下标 = 背包库存下标，行优先、行 0 在上。
     *
     * @param scrollPx 纵向滚动偏移（≥ 0，像素）：内容上移以露出后面的行
     */
    public static Rect equipSlot(int index, int scrollPx) {
        int pitch = EQUIP_SLOT + EQUIP_GAP;
        int col = index % EQUIP_GRID_COLS;
        int row = index / EQUIP_GRID_COLS;
        Rect grid = equipGrid();
        return new Rect(grid.x() + col * pitch, grid.top() - EQUIP_SLOT - row * pitch + scrollPx,
                EQUIP_SLOT, EQUIP_SLOT);
    }

    /** 装备大图预览（含描述滚动框）：网格右侧到底内边 */
    public static Rect equipPreview() {
        Rect grid = equipGrid();
        return new Rect(grid.right() + 2 * EQUIP_GAP, EQUIP_Y,
                DRAWER_W - PADDING - (grid.right() + 2 * EQUIP_GAP), EQUIP_H);
    }

    // —— 分隔条（通栏：不承载内容，从把手下方穿过） ——

    /** 下分隔条（装备区 ↔ 头像条） */
    public static Rect dividerLower() {
        return new Rect(PADDING, DIVIDER_LOWER_Y, DRAWER_W - 2 * PADDING, DIVIDER_H);
    }

    /** 上分隔条（头像条 ↔ 信息区） */
    public static Rect dividerUpper() {
        return new Rect(PADDING, DIVIDER_UPPER_Y, DRAWER_W - 2 * PADDING, DIVIDER_H);
    }

    private static float clamp(float progress) {
        return Math.max(0f, Math.min(1f, progress));
    }
}
