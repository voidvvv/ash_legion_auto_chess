package com.voidvvv.kz_auto_chess_n.render.board;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;

/**
 * 布局常量与换算（render §九坐标表；纯 Java 可测，零 Gdx）。
 *
 * <p>统一虚拟坐标（render §2.2，640×360）：棋盘 ④ (224,50,192,224)、备战席 ② (20,48,108,120)。
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
    /** ② 备战席 3×3（槽 36×40） */
    public static final int BENCH_X = 20;
    public static final int BENCH_Y = 48;
    public static final int BENCH_W = 108;
    public static final int BENCH_H = 120;

    /** ③ 装备背包 3×2（UI 域 InventoryPanel 定位同源；槽 36×36）。
     *  render §九原表值 (20,140,108,100) 与 ② 备战席底边 168 压叠 28px——feedback01 修正：
     *  下移至 172 起、槽高 36（2×36=72），底边 244 与 ⑨ 顶边相接。 */
    public static final int INVENTORY_X = 20;
    public static final int INVENTORY_Y = 172;
    public static final int INVENTORY_W = 108;
    public static final int INVENTORY_H = 72;
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

    /** feedback07 背包格装备悬停卡：③ 背包（右缘 128）与 ④ 棋盘（左缘 224）之间空带内、③ 顶 172 之上。
     *  与棋盘域卡（128,48,94,192）同带不同源——单指针命中域互斥（背包 x≤128 y172~244，棋盘/备战
     *  候选均在界外）；底边 240 避开 ⑨ 通知（y 244 起）、顶边 140 避开 ⑥ 开战按钮（y 88~128）。 */
    public static final int INVENTORY_HOVER_X = 132;
    public static final int INVENTORY_HOVER_Y = 140;
    public static final int INVENTORY_HOVER_W = 90;
    public static final int INVENTORY_HOVER_H = 100;

    public static final int CELL = 32;
    public static final int BENCH_SLOT_W = 36;
    public static final int BENCH_SLOT_H = 40;
    public static final int INVENTORY_SLOT_W = 36;
    public static final int INVENTORY_SLOT_H = 36;
    /** 备战期引导文案基线（feedback01 修正：原 y=24 落在 ⑧ 商店栏带内被半透明卡牌遮挡——移到 ④ 棋盘上边 274 之上） */
    public static final int SHOP_HINT_Y = 280;

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

    // —— 备战席槽 ↔ 像素（列主序：slot = col×3 + row） ——

    public static int[] benchSlotCenter(int slotIndex) {
        int col = slotIndex / 3;
        int row = slotIndex % 3;
        return new int[]{BENCH_X + col * BENCH_SLOT_W + BENCH_SLOT_W / 2,
                BENCH_Y + row * BENCH_SLOT_H + BENCH_SLOT_H / 2};
    }

    /** 像素 → 槽索引；界外返回 -1 */
    public static int pixelToBenchSlot(int px, int py) {
        if (px < BENCH_X || px >= BENCH_X + BENCH_W || py < BENCH_Y || py >= BENCH_Y + BENCH_H) {
            return -1;
        }
        int col = (px - BENCH_X) / BENCH_SLOT_W;
        int row = (py - BENCH_Y) / BENCH_SLOT_H;
        return col * 3 + row;
    }

    // —— Phase 5 CP17：⑦ 出售区命中 / ③ 背包槽定位 ——

    /** 像素点是否在 ⑦ 出售区内（boardProcessor 拖拽终点判定） */
    public static boolean isInSellZone(int px, int py) {
        return px >= SELL_ZONE_X && px < SELL_ZONE_X + SELL_ZONE_W
                && py >= SELL_ZONE_Y && py < SELL_ZONE_Y + SELL_ZONE_H;
    }

    /** ③ 背包槽中心（3 列 × 2 行；row 0 在下——scene2d y 向上） */
    public static int[] inventorySlotCenter(int slotIndex) {
        int col = slotIndex / 2;
        int row = slotIndex % 2;
        return new int[]{INVENTORY_X + col * INVENTORY_SLOT_W + INVENTORY_SLOT_W / 2,
                INVENTORY_Y + INVENTORY_H - row * INVENTORY_SLOT_H - INVENTORY_SLOT_H / 2};
    }
}
