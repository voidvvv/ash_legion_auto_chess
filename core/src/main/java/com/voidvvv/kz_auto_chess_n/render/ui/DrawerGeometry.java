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
 * 内部预算沿 UI_PLAN §3.2 甲案示意（内边距 6，自下而上 装备区 / 头像条 / 信息区）；
 * 与示意的两处差异：① 标签页取素材原生 32×18（M05/M06 左端饰纹跨九宫格中段，拉伸会变形——描述框因此
 * 取 128 宽而非示意的 124）；② 把手 / 关闭钮 / 标签页不拉伸。
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
    /** 左缘竖把手：宽/高 = 素材源尺寸（M09 14×72，原生绘制不拉伸），x = 0 贴屏幕左边 */
    public static final int HANDLE_W = 14;
    public static final int HANDLE_H = 72;
    /** 收起态「后备 n/9」小角标（Q2）：把手右上；54 宽容 12px 字「后备 -/9」= 48px + 左右各 3px */
    public static final int BADGE_W = 54;
    public static final int BADGE_H = 16;

    // —— 内部预算（抽屉局部坐标；y 向上） ——

    public static final int PADDING = 6;
    /** 分隔条（M11 24×4，横向拉伸到内宽） */
    public static final int DIVIDER_H = 4;
    /** 信息区：标题板 14 高 + 立绘/描述行 64 高 */
    public static final int TITLE_H = 14;
    public static final int INFO_ROW_H = 64;
    /** Q11：立绘 = 单位 idle 帧放大 2 倍（64px） */
    public static final int PORTRAIT_W = 64;
    public static final int PORTRAIT_H = 64;
    public static final int DESC_W = 128;
    /** 标签页：素材原生 32×18（不拉伸），竖排两枚、间隙 2 */
    public static final int TAB_W = 32;
    public static final int TAB_H = 18;
    public static final int TAB_GAP = 2;
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
    /** 关闭钮：素材原生 14×14（不拉伸） */
    public static final int CLOSE_W = 14;
    public static final int CLOSE_H = 14;

    // —— 纵向标尺（自下而上；局部 y） ——
    private static final int EQUIP_Y = PADDING;                                   // 6
    private static final int EQUIP_H = EQUIP_GRID_ROWS * EQUIP_SLOT + EQUIP_GAP;   // 72
    private static final int DIVIDER_LOWER_Y = EQUIP_Y + EQUIP_H;                  // 78
    private static final int SCROLL_Y = DIVIDER_LOWER_Y + DIVIDER_H + 1;           // 83
    private static final int STRIP_Y = SCROLL_Y + STRIP_SCROLL_H + 2;              // 93
    private static final int DIVIDER_UPPER_Y = STRIP_Y + STRIP_SLOT;               // 127
    private static final int INFO_ROW_Y = DIVIDER_UPPER_Y + DIVIDER_H + 1;         // 132
    private static final int TITLE_Y = DRAWER_H - PADDING - TITLE_H;               // 198

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
        int y = DRAWER_Y + (DRAWER_H - HANDLE_H) / 2;
        return new Rect(0, y, HANDLE_W, HANDLE_H);
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

    // —— 三分区（抽屉局部坐标；自下而上 装备区 / 头像条 / 信息区，相邻共边、互不压叠） ——

    /** 上段·信息区：上分隔条之上到顶内边（标题板 + 立绘/描述/标签行） */
    public static Rect infoSection() {
        return new Rect(PADDING, DIVIDER_UPPER_Y + DIVIDER_H, DRAWER_W - 2 * PADDING,
                DRAWER_H - PADDING - (DIVIDER_UPPER_Y + DIVIDER_H));
    }

    /** 中段·头像条：下分隔条之上到上分隔条之上（两条分隔条归本段；含滚动条与槽行） */
    public static Rect stripSection() {
        return new Rect(PADDING, DIVIDER_LOWER_Y, DRAWER_W - 2 * PADDING,
                DIVIDER_UPPER_Y + DIVIDER_H - DIVIDER_LOWER_Y);
    }

    /** 下段·装备区：底内边到装备网格顶（装备网格 + 大图预览） */
    public static Rect equipSection() {
        return new Rect(PADDING, EQUIP_Y, DRAWER_W - 2 * PADDING, EQUIP_H);
    }

    // —— 信息区部件 ——

    /** 标题板（棋子名）：通栏内宽 × 14 */
    public static Rect titlePlate() {
        return new Rect(PADDING, TITLE_Y, DRAWER_W - 2 * PADDING, TITLE_H);
    }

    /** 关闭钮：贴标题板右上角（落在标题板内） */
    public static Rect closeButton() {
        return new Rect(DRAWER_W - PADDING - CLOSE_W, TITLE_Y + TITLE_H - CLOSE_H, CLOSE_W, CLOSE_H);
    }

    /** 立绘位：左下角起 64×64（Q11） */
    public static Rect portrait() {
        return new Rect(PADDING, INFO_ROW_Y, PORTRAIT_W, PORTRAIT_H);
    }

    /** 描述框：立绘右侧 128×64（介绍 / 装备两标签页的内容区） */
    public static Rect descriptionPane() {
        return new Rect(portrait().right() + STRIP_GAP, INFO_ROW_Y, DESC_W, INFO_ROW_H);
    }

    /** 「介绍」标签（上） */
    public static Rect tabInfo() {
        return new Rect(DRAWER_W - PADDING - TAB_W, tabEquip().top() + TAB_GAP, TAB_W, TAB_H);
    }

    /** 「装备」标签（下）：在信息行内垂直居中于两标签整体 */
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
        int blockW = STRIP_VISIBLE_SLOTS * STRIP_SLOT + (STRIP_VISIBLE_SLOTS - 1) * STRIP_GAP;
        int x0 = PADDING + (DRAWER_W - 2 * PADDING - blockW) / 2;
        return new Rect(x0 + slot * pitch - scrollPx, STRIP_Y, STRIP_SLOT, STRIP_SLOT);
    }

    /** 头像条横向滚动条轨（槽行正下方；通头像条宽） */
    public static Rect stripScrollBar() {
        Rect first = stripSlot(0, 0);
        int w = STRIP_VISIBLE_SLOTS * STRIP_SLOT + (STRIP_VISIBLE_SLOTS - 1) * STRIP_GAP;
        return new Rect(first.x(), SCROLL_Y, w, STRIP_SCROLL_H);
    }

    // —— 装备区 ——

    /** 装备网格外框（3 列 × 2 行可见） */
    public static Rect equipGrid() {
        return new Rect(PADDING, EQUIP_Y,
                EQUIP_GRID_COLS * EQUIP_SLOT + (EQUIP_GRID_COLS - 1) * EQUIP_GAP,
                EQUIP_GRID_ROWS * EQUIP_SLOT + (EQUIP_GAP));
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

    // —— 分隔条 ——

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
