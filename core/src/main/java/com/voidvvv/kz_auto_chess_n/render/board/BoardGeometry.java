package com.voidvvv.kz_auto_chess_n.render.board;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;

/**
 * 布局常量与换算（render §九坐标表；纯 Java 可测，零 Gdx）。
 *
 * <p>统一虚拟坐标（render §2.2，640×360）：棋盘 ④ (224,50,192,224)；退役前左列 ② (20,48,108,120)
 * （备战席 / 背包已迁入 UI 抽屉——几何见 {@code render/ui/DrawerGeometry}，命中走 {@code input/DrawerHitTest}）。
 * 行 0（敌区）在顶——格坐标 y 向上、屏幕 y 向下，cellCenterY 做翻转。
 * 全部输出整数虚拟像素（int 运算天然吸附，render §八.2）。
 */
public final class BoardGeometry {
    public static final int VIRTUAL_W = 640;
    public static final int VIRTUAL_H = 360;

    /** ④ 棋盘 6×7×32 */
    public static final int BOARD_X = 224;
    public static final int BOARD_Y = 50;
    public static final int BOARD_W = 192;
    public static final int BOARD_H = 224;
    /** ② 备战席 3×3 的<b>退役前世界域矩形</b>（UI 抽屉化批2 起不再绘制、不可命中——备战席迁入抽屉头像条，
     *  见 {@code render/ui/DrawerGeometry}）。<b>保留用途</b>（批4 清理裁定）：左列元件的极端坐标样本——
     *  {@code RenderScaleFitTest} 的缩放边缘公式锁（左缘 20 在 s=1.25 放大档 → −55 出屏）与布局回归、
     *  悬停卡走廊左缘断言（右缘 128）仍以它为准；不再代表任何在屏元件。 */
    public static final int BENCH_X = 20;
    public static final int BENCH_Y = 48;
    public static final int BENCH_W = 108;
    public static final int BENCH_H = 120;

    /** ⑤ 羁绊面板（UI 域） */
    public static final int SYNERGY_X = 508;
    public static final int SYNERGY_Y = 48;
    public static final int SYNERGY_W = 112;
    public static final int SYNERGY_H = 144;
    /** ⑥ 开战按钮（UI 域；UI 抽屉化批1·Q9：自备战席右侧 (134,88) 迁至右侧、⑤ 羁绊面板上方——
     *  位置取 render §九设计表原值 (508,200) 按实机 y-up 落点（Q10：界面坐标镜像漂移另案处理，本次一律
     *  按实机落点写）；尺寸沿用既有 64×40（设计表的 112×40 未取）。待主人实机核对后可校准。 */
    public static final int FIGHT_BUTTON_X = 508;
    public static final int FIGHT_BUTTON_Y = 200;
    public static final int FIGHT_BUTTON_W = 64;
    public static final int FIGHT_BUTTON_H = 40;
    /** ⑦ 出售区（棋盘域拖拽终点，仅 SHOPPING） */
    public static final int SELL_ZONE_X = 564;
    public static final int SELL_ZONE_Y = 246;
    public static final int SELL_ZONE_W = 56;
    public static final int SELL_ZONE_H = 46;
    /** ⑧ 商店栏（UI 域 ShopBar，全宽 640） */
    public static final int SHOP_BAR_Y = 296;
    public static final int SHOP_BAR_H = 64;
    /** ⑨ 事件通知小窗（render §九原值 y=230 与 ③ 底边 240 重叠 10px——差异声明 #4，改 244 起） */
    public static final int NOTIFY_X = 20;
    public static final int NOTIFY_Y = 244;
    public static final int NOTIFY_W = 128;
    public static final int NOTIFY_H = 46;

    // —— Phase 5.1 R1：悬停预览卡固定锚点（裁决 A；防遮挡论证见计划 §5.3-1） ——

    /** 棋盘域悬停卡：②③（右缘 128）与 ④ 棋盘（左缘 224）之间的左侧空带。
     *  v5 全量文案后卡区加高（2026-11-21 主人整改：悬停卡必须完整容纳全部文本、不截断）——
     *  高度 = 最长真实组合（敌方标记 27 行 × 9px + 上下留白）；27 行最坏仅现于 BATTLE 敌方悬停
     *  （战时商店栏已下滑离场，底缘 296 与其顶缘相接不压）；SHOPPING 自有卡 ≤26 行底缘 ≤290，
     *  瞬态覆盖 ⑨ 通知（y244 起，移开即恢复——非模态瞬态，拖拽期悬停本就抑制）。 */
    public static final int BOARD_HOVER_X = 128;
    public static final int BOARD_HOVER_Y = 44;
    public static final int BOARD_HOVER_W = 94;
    public static final int BOARD_HOVER_H = 252;
    /** 商店卡悬停卡：锚 ⑤ 羁绊面板区；高度容纳最长组合 24 行（2026-11-21 整改，自适应加高、不截断）；
     *  底缘 272 瞬态覆盖 ⑦ 出售区（y246 起）——拖拽期悬停抑制，无操作冲突 */
    public static final int SHOP_HOVER_X = 508;
    public static final int SHOP_HOVER_Y = 48;
    public static final int SHOP_HOVER_W = 112;
    public static final int SHOP_HOVER_H = 224;

    public static final int CELL = 32;

    // —— 备战期引导文案（批4 收尾：方位按实机落点、抽屉展开时让位） ——

    /** 引导文案基线（feedback01 修正：原 y=24 落在 ⑧ 商店栏带内被半透明卡牌遮挡——移到 ④ 棋盘上边 274 之上） */
    public static final int SHOP_HINT_Y = 280;
    /** 抽屉展开时引导文案左缘与抽屉右缘的间隙（舞台像素） */
    public static final int SHOP_HINT_DRAWER_GAP_PX = 4;

    // —— 战前危险提示区（品质改造 §1.1：棋盘下缘、虚拟世界矩形 (224,14,324,32)、两行基线 42/28） ——

    public static final int THREAT_HINT_X = 224;
    public static final int THREAT_HINT_Y = 14;
    public static final int THREAT_HINT_W = 324;
    public static final int THREAT_HINT_H = 32;
    /** 两行文案基线（y-up；第一行在上） */
    public static final int THREAT_HINT_LINE1_Y = 42;
    public static final int THREAT_HINT_LINE2_Y = 28;
    /** 文案左内边距（相对提示区左缘） */
    public static final int THREAT_HINT_TEXT_INSET_PX = 8;

    /** 规则行文案（§1.1 原文） */
    public static final String THREAT_COPY_BREAK_STOMP = "破阵踏：目标及相邻四格，拉开站位";
    public static final String THREAT_COPY_EXECUTE = "处决：优先盯最靠后的棋子，让战士站最后";
    /** 单威胁第二行说明（§1.1 原文） */
    public static final String THREAT_NOTE_SINGLE = "开局预判，目标会随交战变化";
    /** 双威胁栏头（§1.1：「规则行统一以开局预判为栏头缩写，维持两条正文可读空间」） */
    public static final String THREAT_HEADER = "开局预判";

    /**
     * 战前提示两行文案（纯函数可测；§1.1 排版口径）：
     * 单威胁 = 规则行 + 「开局预判，目标会随交战变化」；双威胁 = 「开局预判」栏头缩写入首行 +
     * 规则行两条正文各占一行（A 在前，与预判输出序同源）。无威胁返回空数组（不绘制）。
     *
     * @param threatCount 命中威胁条数（0/1/2；仅两题，恒 ≤ 2）
     * @param firstCopy   第一条（或唯一一条）威胁的规则行
     * @param secondCopy  第二条威胁的规则行（单威胁传 null）
     */
    public static String[] threatHintLines(int threatCount, String firstCopy, String secondCopy) {
        if (threatCount <= 0) {
            return new String[0];
        }
        if (threatCount >= 2 && secondCopy != null) {
            return new String[]{THREAT_HEADER + " " + firstCopy, secondCopy};
        }
        return new String[]{firstCopy, THREAT_NOTE_SINGLE};
    }

    /**
     * 出售区方位词——<b>与 ⑦ 出售区常量同源</b>（UI 抽屉化批4；Q10 裁决：界面坐标镜像漂移另案处理，
     * 本次按实机落点 y-up 写）：x 过中线为「右」、y 过中线为「上」。设计表原值 (564,246) 被当 y-up 用 →
     * 实机右上（三张探针截图核实）。待主人实机核对后可校准——改 {@link #SELL_ZONE_X/Y} 即同步改文案。
     */
    public static String sellZoneDirectionText() {
        String horizontal = SELL_ZONE_X + SELL_ZONE_W / 2 >= VIRTUAL_W / 2 ? "右" : "左";
        String vertical = SELL_ZONE_Y + SELL_ZONE_H / 2 >= VIRTUAL_H / 2 ? "上" : "下";
        return horizontal + vertical;
    }

    /** 备战期引导文案（方位词由出售区常量派生，文字与几何不会漂移） */
    public static final String SHOP_HINT_TEXT = "拖拽棋子布阵 · 拖至" + sellZoneDirectionText() + "出售区卖出 · 点开战";

    /**
     * 引导文案左缘的世界坐标（批4：抽屉展开时让位——否则首字压在抽屉右边框上）。
     * 默认居中；若抽屉展开（右缘 &gt; 0，<b>舞台坐标</b>，不缩放层 1:1）且居中后的舞台左缘落在抽屉右缘 + 间隙之内，
     * 右移到间隙之外；再把舞台坐标反算回世界坐标。
     *
     * @param textWidth           文案实宽（像素）
     * @param worldScale          备战期世界域内容缩放（= 1 / worldCamera.zoom）
     * @param drawerStageRightEdge 抽屉主体当前舞台右缘；收起（progress ≤ 0）传 0 = 不让位
     */
    public static float shopHintX(float textWidth, float worldScale, float drawerStageRightEdge) {
        float centered = Math.round((VIRTUAL_W - textWidth) / 2f);
        if (drawerStageRightEdge <= 0f) {
            return centered;
        }
        float minStageLeft = drawerStageRightEdge + SHOP_HINT_DRAWER_GAP_PX;
        if (screenX(centered, worldScale) >= minStageLeft) {
            return centered; // 本就落在抽屉右侧：不动
        }
        return (minStageLeft - VIRTUAL_W / 2f) / worldScale + VIRTUAL_W / 2f;
    }

    private BoardGeometry() {
    }

    // —— 整体渲染缩放布局数学（2026-11-21 整改 + 回归修复；纯 JVM 可测） ——

    /**
     * worldCamera 可见世界矩形（标准 FitViewport 相机位 = 画布中心 (320,180)，libGDX zoom 语义 =
     * 可见世界 ÷ 画布）：{x, y, w, h} = (320−320z, 180−180z, 640z, 360z)。
     * 内容缩小 s 倍 ⇔ zoom = 1/s（BattleTransitionController.zoom 除法基准）。
     */
    public static float[] visibleWorldRect(float zoom) {
        float halfW = VIRTUAL_W / 2f * zoom;
        float halfH = VIRTUAL_H / 2f * zoom;
        return new float[]{VIRTUAL_W / 2f - halfW, VIRTUAL_H / 2f - halfH, halfW * 2f, halfH * 2f};
    }

    /**
     * UI 域根缩放后的内容矩形（Stage 根 setScale(s) + 居中 setPosition）：
     * {x, y, w, h} = (640(1−s)/2, 360(1−s)/2, 640s, 360s)——与世界域同中心，
     * 两域统一屏幕映射 screen(v) = (v−C)·s + C。
     */
    public static float[] uiContentRect(float uiScale) {
        float w = VIRTUAL_W * uiScale;
        float h = VIRTUAL_H * uiScale;
        return new float[]{(VIRTUAL_W - w) / 2f, (VIRTUAL_H - h) / 2f, w, h};
    }

    /** 统一屏幕映射：世界坐标或 UI 子坐标 v → 屏幕坐标（C = 画布中心；两域同式） */
    public static float screenX(float v, float scale) {
        return (v - VIRTUAL_W / 2f) * scale + VIRTUAL_W / 2f;
    }

    /** 同 {@link #screenX}（y 轴） */
    public static float screenY(float v, float scale) {
        return (v - VIRTUAL_H / 2f) * scale + VIRTUAL_H / 2f;
    }

    // —— 棋盘格 ↔ 像素 ——

    public static int[] cellCenter(int gridX, int gridY) {
        return new int[]{cellCenterX(gridX), cellCenterY(gridY)};
    }

    /** 格中心 x（热路径零分配版） */
    public static int cellCenterX(int gridX) {
        return BOARD_X + gridX * CELL + CELL / 2;
    }

    /** 格中心 y：行 0（敌区）在顶（屏幕 y 向下翻转） */
    public static int cellCenterY(int gridY) {
        return BOARD_Y + BOARD_H - (gridY + 1) * CELL + CELL / 2;
    }

    // —— 连续格坐标 ↔ 像素（模拟层弹道口径：格中心 = gridX + 0.5；差异 #P0 修正） ——

    /** 连续格坐标 x → 像素中心 x（0.5×32=16 浮点精确，与 cellCenterX(gridX) 等价） */
    public static float continuousCenterX(float x) {
        return BOARD_X + x * CELL;
    }

    /** 连续格坐标 y → 像素中心 y（行 0 在顶，翻转口径同 cellCenterY） */
    public static float continuousCenterY(float y) {
        return BOARD_Y + BOARD_H - y * CELL;
    }

    /** 像素 → 格坐标；界外返回 null（boardProcessor 命中判定） */
    public static int[] pixelToCell(int px, int py) {
        if (px < BOARD_X || px >= BOARD_X + BOARD_W || py < BOARD_Y || py >= BOARD_Y + BOARD_H) {
            return null;
        }
        int gridX = (px - BOARD_X) / CELL;
        int gridY = GameBalance.BOARD_ROWS - 1 - (py - BOARD_Y) / CELL;
        return new int[]{gridX, gridY};
    }

    // —— 备战席槽 ↔ 像素：已退役（UI 抽屉化批2/批4）——备战席命中改走 input/DrawerHitTest（抽屉头像条实现，
    //    槽号即 bench 下标）；几何见 render/ui/DrawerGeometry.stripSlot ——

    // —— Phase 5 CP17：⑦ 出售区命中 ——

    /** 像素点是否在 ⑦ 出售区内（boardProcessor 拖拽终点判定） */
    public static boolean isInSellZone(int px, int py) {
        return px >= SELL_ZONE_X && px < SELL_ZONE_X + SELL_ZONE_W
                && py >= SELL_ZONE_Y && py < SELL_ZONE_Y + SELL_ZONE_H;
    }
}
