package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BoardGeometry 测试：布局常量自洽、行 0 在顶的 y 翻转、格↔像素往返、整数吸附（render §九/§八.2）。
 */
class BoardGeometryTest {

    @Test
    @DisplayName("常量自洽：BOARD = 6×32 × 7×32、虚拟画布 640×360；② 备战席 9 格的容量语义由抽屉头像条承接（可见 6 + 滚动 = BENCH_SIZE）")
    void constantsSelfConsistent() {
        assertThat(BoardGeometry.BOARD_W).isEqualTo(GameBalance.BOARD_COLS * BoardGeometry.CELL);
        assertThat(BoardGeometry.BOARD_H).isEqualTo(GameBalance.BOARD_ROWS * BoardGeometry.CELL);
        assertThat(BoardGeometry.VIRTUAL_W).isEqualTo(640);
        assertThat(BoardGeometry.VIRTUAL_H).isEqualTo(360);
        // 批4 迁移：原「BENCH = 3×36 × 3×40」自洽断言 → 备战席容量由头像条承接（槽号即 bench 下标）
        assertThat(com.voidvvv.kz_auto_chess_n.render.ui.DrawerGeometry.STRIP_VISIBLE_SLOTS).isEqualTo(6);
        assertThat(GameBalance.BENCH_SIZE).isEqualTo(9);
        assertThat(BoardGeometry.BENCH_X).as("退役前左列矩形样本仍在（缩放公式锁引用）").isEqualTo(20);
        assertThat(BoardGeometry.BENCH_X + BoardGeometry.BENCH_W).isEqualTo(128);
    }

    @Test
    @DisplayName("cellCenter 行 0（敌区）在顶：gridY=0 的中心 py 大于 gridY=6 的中心 py")
    void rowZeroAtTop() {
        int[] enemy = BoardGeometry.cellCenter(0, 0);
        int[] player = BoardGeometry.cellCenter(0, 6);
        assertThat(enemy[1]).isGreaterThan(player[1]);
    }

    @Test
    @DisplayName("cellCenter 落在各自 32px 格内（中心 = 格矩形中点）")
    void cellCenterInsideOwnCellRect() {
        for (int y = 0; y < GameBalance.BOARD_ROWS; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                int[] c = BoardGeometry.cellCenter(x, y);
                int top = BoardGeometry.BOARD_Y + BoardGeometry.BOARD_H - (y + 1) * BoardGeometry.CELL;
                assertThat(c[0]).isEqualTo(BoardGeometry.BOARD_X + x * BoardGeometry.CELL + BoardGeometry.CELL / 2);
                assertThat(c[1]).isEqualTo(top + BoardGeometry.CELL / 2);
            }
        }
    }

    @Test
    @DisplayName("cellCenter ↔ pixelToCell 往返一致（全 42 格中心点）")
    void cellCenterRoundTrip() {
        for (int y = 0; y < GameBalance.BOARD_ROWS; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                int[] center = BoardGeometry.cellCenter(x, y);
                int[] cell = BoardGeometry.pixelToCell(center[0], center[1]);
                assertThat(cell).containsExactly(x, y);
            }
        }
    }

    @Test
    @DisplayName("pixelToCell 界外返回 null：左/右/上/下边界外")
    void pixelToCellRejectsOutside() {
        assertThat(BoardGeometry.pixelToCell(BoardGeometry.BOARD_X - 1, 100)).isNull();
        assertThat(BoardGeometry.pixelToCell(BoardGeometry.BOARD_X + BoardGeometry.BOARD_W, 100)).isNull();
        assertThat(BoardGeometry.pixelToCell(300, BoardGeometry.BOARD_Y - 1)).isNull();
        assertThat(BoardGeometry.pixelToCell(300, BoardGeometry.BOARD_Y + BoardGeometry.BOARD_H)).isNull();
    }

    @Test
    @DisplayName("pixelToCell 半格像素命中正确格（格内任意点归属该格）")
    void pixelToCellHalfCellResolution() {
        // 格 (3,4) 的矩形：x ∈ [224+96, 224+128)，y ∈ [114, 146)（top = 274-160）
        assertThat(BoardGeometry.pixelToCell(224 + 96, 114)).containsExactly(3, 4);
        assertThat(BoardGeometry.pixelToCell(224 + 127, 145)).containsExactly(3, 4);
        assertThat(BoardGeometry.pixelToCell(224 + 128, 118)).containsExactly(4, 4); // 下一列
        assertThat(BoardGeometry.pixelToCell(224 + 96, 146)).containsExactly(3, 3);  // 屏幕上方一行（更靠敌区，gridY 更小）
    }

    @Test
    @DisplayName("cellCenter 输出整数吸附（render §八.2 Math.round）")
    void cellCenterSnapsToInteger() {
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 6; x++) {
                int[] c = BoardGeometry.cellCenter(x, y);
                assertThat(c[0]).isEqualTo((int) c[0]);
                assertThat(c[1]).isEqualTo((int) c[1]);
            }
        }
    }

    @Test
    @DisplayName("continuousCenter 与 cellCenter 等价：全 6×7 格 (grid+0.5) 逐位相等（0.5×32=16 浮点精确）")
    void continuousCenterEquivalentToCellCenter() {
        for (int y = 0; y < GameBalance.BOARD_ROWS; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                assertThat(BoardGeometry.continuousCenterX(x + 0.5f))
                        .isEqualTo((float) BoardGeometry.cellCenterX(x));
                assertThat(BoardGeometry.continuousCenterY(y + 0.5f))
                        .isEqualTo((float) BoardGeometry.cellCenterY(y));
            }
        }
        // 抽查：格 (2,5) 中心 (304, 98)；连续坐标 y 大 → 像素 y 小（行 0 在顶）
        assertThat(BoardGeometry.continuousCenterX(2.5f)).isEqualTo(304f);
        assertThat(BoardGeometry.continuousCenterY(5.5f)).isEqualTo(98f);
        assertThat(BoardGeometry.continuousCenterY(5.5f)).isGreaterThan(BoardGeometry.continuousCenterY(6.5f));
    }

    // —— ② 备战席槽 ↔ 像素四用例（benchSlotCenter / pixelToBenchSlot）已随 ② 世界域几何退役（UI 抽屉化批4）——
    //    断言迁至 render/ui/DrawerGeometryTest（stripSlotsMapBenchIndexInOrder / stripBeyondVisibleSlotsLieOutsideContentColumn） ——

    // —— Phase 5 CP17：⑤⑦⑧⑨ 区常量与命中判定（render §九 Phase 4 遗留区；③ 背包常量已随 InventoryPanel 退役） ——

    @Test
    @DisplayName("⑤⑦⑧ 区常量与 render §九坐标表逐项对照（±4px 容差；⑨ 差异声明 #4 例外单独断言）")
    void phase5ZonesMatchRenderLayoutTable() {
        // render_design.md §九：⑤ (508,48,112,144)、⑦ (564,246,56,46)、⑧ (0,296,640,64)
        assertThat(BoardGeometry.SYNERGY_X).isBetween(508 - 4, 508 + 4);
        assertThat(BoardGeometry.SYNERGY_Y).isBetween(48 - 4, 48 + 4);
        assertThat(BoardGeometry.SYNERGY_W).isBetween(112 - 4, 112 + 4);
        assertThat(BoardGeometry.SYNERGY_H).isBetween(144 - 4, 144 + 4);
        assertThat(BoardGeometry.SELL_ZONE_X).isBetween(564 - 4, 564 + 4);
        assertThat(BoardGeometry.SELL_ZONE_Y).isBetween(246 - 4, 246 + 4);
        assertThat(BoardGeometry.SELL_ZONE_W).isBetween(56 - 4, 56 + 4);
        assertThat(BoardGeometry.SELL_ZONE_H).isBetween(46 - 4, 46 + 4);
        assertThat(BoardGeometry.SHOP_BAR_Y).isBetween(296 - 4, 296 + 4);
        assertThat(BoardGeometry.SHOP_BAR_H).isBetween(64 - 4, 64 + 4);
        // ⑨ render §九原值 (20,230,128,60) 与 ③ 底边 240 重叠 10px——差异声明 #4 改 (20,244,128,46)
        assertThat(BoardGeometry.NOTIFY_SMALL_X).isEqualTo(20);
        assertThat(BoardGeometry.NOTIFY_SMALL_Y).isEqualTo(232);
        assertThat(BoardGeometry.NOTIFY_SMALL_W).isEqualTo(104);
        assertThat(BoardGeometry.NOTIFY_SMALL_H).isEqualTo(58);
        assertThat(BoardGeometry.NOTIFY_LARGE_X).isEqualTo(20);
        assertThat(BoardGeometry.NOTIFY_LARGE_Y).isEqualTo(92);
        assertThat(BoardGeometry.NOTIFY_LARGE_W).isEqualTo(196);
        assertThat(BoardGeometry.NOTIFY_LARGE_H).isEqualTo(188);
    }

    @Test
    @DisplayName("布局冲突回归：⑤⑦、⑨⑧ 相邻区互不压叠（②③ 左列对已迁入抽屉——其与 ⑧① 的不压叠断言见 DrawerGeometryTest）")
    void adjacentZonesDoNotOverlap() {
        // ⑤ 底边不侵入 ⑦、⑨ 底边不侵入 ⑧
        assertThat(BoardGeometry.SELL_ZONE_Y).isGreaterThanOrEqualTo(BoardGeometry.SYNERGY_Y + BoardGeometry.SYNERGY_H);
        assertThat(BoardGeometry.SHOP_BAR_Y).isGreaterThanOrEqualTo(BoardGeometry.NOTIFY_SMALL_Y + BoardGeometry.NOTIFY_SMALL_H);
    }

    @Test
    @DisplayName("引导文案基线在 ⑧ 商店栏带与 ④ 棋盘之上、顶栏之下（feedback01 回归：原 y=24 落在 ⑧ 内被卡牌遮挡）")
    void shopHintBaselineClearOfShopBarAndBoard() {
        int shopBandTop = BoardGeometry.VIRTUAL_H - BoardGeometry.SHOP_BAR_Y;          // ⑧ 换算 y 向上 = 64
        int boardBandTop = BoardGeometry.BOARD_Y + BoardGeometry.BOARD_H;              // ④ 上边 = 274
        assertThat(BoardGeometry.SHOP_HINT_Y).isGreaterThanOrEqualTo(shopBandTop + 4);
        assertThat(BoardGeometry.SHOP_HINT_Y).isGreaterThanOrEqualTo(boardBandTop + 4);
        assertThat(BoardGeometry.SHOP_HINT_Y + 12).isLessThanOrEqualTo(BoardGeometry.VIRTUAL_H - 40); // 不侵入顶栏带
    }

    // —— 批4 收尾：引导文案方位与出售区常量同源；抽屉展开时让位 ——

    @Test
    @DisplayName("出售区方位词由常量派生：现值 (564,246) 过中线 →「右上」（实机落点；待主人实机核对后可校准）；文案含该方位词")
    void sellZoneDirectionTextDerivedFromConstants() {
        assertThat(BoardGeometry.SELL_ZONE_X + BoardGeometry.SELL_ZONE_W / 2).isGreaterThan(BoardGeometry.VIRTUAL_W / 2);
        assertThat(BoardGeometry.SELL_ZONE_Y + BoardGeometry.SELL_ZONE_H / 2).isGreaterThan(BoardGeometry.VIRTUAL_H / 2);
        assertThat(BoardGeometry.sellZoneDirectionText()).isEqualTo("右上");
        assertThat(BoardGeometry.SHOP_HINT_TEXT).contains("拖至右上出售区卖出").doesNotContain("右下");
    }

    @Test
    @DisplayName("引导文案左缘：抽屉收起（右缘 ≤ 0）→ 居中不动")
    void shopHintCenteredWhenDrawerCollapsed() {
        float worldScale = GameBalance.RENDER_GLOBAL_SCALE / GameBalance.SHOPPING_CAMERA_ZOOM;
        for (float w = 180f; w <= 300f; w += 20f) {
            assertThat(BoardGeometry.shopHintX(w, worldScale, 0f)).isEqualTo(Math.round((640f - w) / 2f));
            assertThat(BoardGeometry.shopHintX(w, worldScale, -244f)).as("收起态右缘为负 = 不让位")
                    .isEqualTo(Math.round((640f - w) / 2f));
        }
    }

    @Test
    @DisplayName("引导文案让位：抽屉展开（舞台右缘 246）时，文案舞台左缘 ≥ 246 + 4；原本就在抽屉右侧的超短文案不动；右缘不出虚拟宽")
    void shopHintYieldsRightOfExpandedDrawer() {
        float worldScale = GameBalance.RENDER_GLOBAL_SCALE / GameBalance.SHOPPING_CAMERA_ZOOM;
        int drawerRight = com.voidvvv.kz_auto_chess_n.render.ui.DrawerGeometry.DRAWER_X
                + com.voidvvv.kz_auto_chess_n.render.ui.DrawerGeometry.DRAWER_W;
        for (float w = 180f; w <= 300f; w += 20f) {
            float x = BoardGeometry.shopHintX(w, worldScale, drawerRight);
            assertThat(BoardGeometry.screenX(x, worldScale))
                    .as("文案宽 %.0f：舞台左缘 ≥ 抽屉右缘 + 间隙", w)
                    .isGreaterThanOrEqualTo(drawerRight + BoardGeometry.SHOP_HINT_DRAWER_GAP_PX);
            float centeredStageLeft = BoardGeometry.screenX(Math.round((640f - w) / 2f), worldScale);
            if (centeredStageLeft < drawerRight) {
                assertThat(x).as("文案宽 %.0f：居中会压抽屉 → 已右移", w).isGreaterThan(Math.round((640f - w) / 2f));
            } else {
                assertThat(x).as("文案宽 %.0f：居中本就不压 → 不动", w).isEqualTo(Math.round((640f - w) / 2f));
            }
            // 让位后仍在虚拟画布内，且不侵入 ⑦ 出售区（世界 x 564 起）
            assertThat(x + w).isLessThan(BoardGeometry.SELL_ZONE_X);
        }
        // 真实文案（约 24 字 ≈ 270px）：居中落点舞台左缘 ≈ 225 < 246 → 修复前首字压抽屉右边框（协调方实机截图所见）
        assertThat(BoardGeometry.screenX(Math.round((640f - 270f) / 2f), worldScale)).isLessThan(drawerRight);
        // 超短文案（40px）居中后舞台左缘 = 305 ≥ 250：不动
        float centered = Math.round((640f - 40f) / 2f);
        assertThat(BoardGeometry.shopHintX(40f, worldScale, drawerRight)).isEqualTo(centered);
    }

    // —— Phase 5.1 CP8：悬停预览卡固定锚点（裁决 A；防遮挡论证见计划 §5.3-1） ——

    @Test
    @DisplayName("棋盘域悬停卡锚点：不压 ④ 棋盘/②③ 左列/⑧ 商店栏（v5 全量文案整改后加高、全量容纳）")
    void boardHoverAnchorClearOfZones() {
        // 右缘不压 ④ 棋盘（224 起，留 ≥2px 间隙）
        assertThat(BoardGeometry.BOARD_HOVER_X + BoardGeometry.BOARD_HOVER_W)
                .isLessThanOrEqualTo(BoardGeometry.BOARD_X);
        // 左缘不压退役前左列（② 右缘 128 = ③ 右缘 128，同一右缘——③ 常量已随 InventoryPanel 退役，以 ② 样本为准）
        assertThat(BoardGeometry.BOARD_HOVER_X)
                .isGreaterThanOrEqualTo(BoardGeometry.BENCH_X + BoardGeometry.BENCH_W);
        // 下缘不压 ⑧ 商店栏（296 起；⑨ 通知 244~290 为瞬态覆盖——非模态卡移开即恢复，2026-11-21 整改口径）
        assertThat(BoardGeometry.BOARD_HOVER_Y + BoardGeometry.BOARD_HOVER_H)
                .isLessThanOrEqualTo(BoardGeometry.SHOP_BAR_Y);
        // 上沿抬至 44（② 顶 48 之上的空隙带——v5 最坏组合 27 行加高所需）
        assertThat(BoardGeometry.BOARD_HOVER_Y).isGreaterThanOrEqualTo(44);
    }

    @Test
    @DisplayName("商店悬停卡锚点：与 ⑤ 羁绊面板同位、右缘不出虚拟宽、下缘不压 ⑧ 商店栏（⑦ 瞬态覆盖）")
    void shopHoverAnchorMatchesSynergyZone() {
        assertThat(BoardGeometry.SHOP_HOVER_X).isEqualTo(BoardGeometry.SYNERGY_X);
        assertThat(BoardGeometry.SHOP_HOVER_Y).isEqualTo(BoardGeometry.SYNERGY_Y);
        assertThat(BoardGeometry.SHOP_HOVER_W).isEqualTo(BoardGeometry.SYNERGY_W);
        assertThat(BoardGeometry.SHOP_HOVER_X + BoardGeometry.SHOP_HOVER_W)
                .isLessThanOrEqualTo(BoardGeometry.VIRTUAL_W);
        // 下缘不压 ⑧ 商店栏（296 起）；⑦ 出售区 246~292 为瞬态覆盖（拖拽期悬停抑制，2026-11-21 整改口径）
        assertThat(BoardGeometry.SHOP_HOVER_Y + BoardGeometry.SHOP_HOVER_H)
                .isLessThanOrEqualTo(BoardGeometry.SHOP_BAR_Y);
    }

    @Test
    @DisplayName("isInSellZone 四角命中、界外四向不命中（半开区间）")
    void isInSellZoneCornersAndOutside() {
        int x0 = BoardGeometry.SELL_ZONE_X;
        int y0 = BoardGeometry.SELL_ZONE_Y;
        int x1 = BoardGeometry.SELL_ZONE_X + BoardGeometry.SELL_ZONE_W;
        int y1 = BoardGeometry.SELL_ZONE_Y + BoardGeometry.SELL_ZONE_H;
        // 左上/右下/右上/左下四角（右/下边界为开区间，取 -1）
        assertThat(BoardGeometry.isInSellZone(x0, y0)).isTrue();
        assertThat(BoardGeometry.isInSellZone(x1 - 1, y1 - 1)).isTrue();
        assertThat(BoardGeometry.isInSellZone(x1 - 1, y0)).isTrue();
        assertThat(BoardGeometry.isInSellZone(x0, y1 - 1)).isTrue();
        // 中心
        assertThat(BoardGeometry.isInSellZone((x0 + x1) / 2, (y0 + y1) / 2)).isTrue();
        // 界外：左/右/上/下 + 恰在右/下边界（开区间不含）
        assertThat(BoardGeometry.isInSellZone(x0 - 1, y0)).isFalse();
        assertThat(BoardGeometry.isInSellZone(x1, y0)).isFalse();
        assertThat(BoardGeometry.isInSellZone(x0, y0 - 1)).isFalse();
        assertThat(BoardGeometry.isInSellZone(x0, y1)).isFalse();
    }

    // —— UI 抽屉化批1：⑥ 开战按钮迁位（Q9：右侧、⑤ 羁绊面板上方；Q10：按实机 y-up 落点，常量待主人实机核对） ——

    @Test
    @DisplayName("⑥ 开战按钮新址（Q9）：右侧 ⑤ 羁绊面板正上方、⑦ 出售区正下方（两域真实缩放换算到舞台坐标互不压叠），不再占用抽屉范围")
    void fightButtonRelocatedAboveSynergyPanel() {
        // 位置取设计表原位 (508,200) 按实机 y-up；尺寸沿用既有 64×40
        assertThat(BoardGeometry.FIGHT_BUTTON_X).isEqualTo(BoardGeometry.SYNERGY_X);
        assertThat(BoardGeometry.FIGHT_BUTTON_Y).isEqualTo(200);
        assertThat(BoardGeometry.FIGHT_BUTTON_W).isEqualTo(64);
        assertThat(BoardGeometry.FIGHT_BUTTON_H).isEqualTo(40);
        // UI 域内（同为 0.8 缩放）：⑥ 底 200 ≥ ⑤ 顶 192；右缘 ≤ ⑤ 右缘 620；不出虚拟宽
        assertThat(BoardGeometry.FIGHT_BUTTON_Y).isGreaterThanOrEqualTo(
                BoardGeometry.SYNERGY_Y + BoardGeometry.SYNERGY_H);
        assertThat(BoardGeometry.FIGHT_BUTTON_X + BoardGeometry.FIGHT_BUTTON_W)
                .isLessThanOrEqualTo(BoardGeometry.SYNERGY_X + BoardGeometry.SYNERGY_W);
        // 跨域（⑥ UI 域 0.8 / ⑦ 世界域备战期 0.8/1.06）：⑥ 舞台顶 ≤ ⑦ 舞台底；⑥ 舞台底 ≥ ⑤ 舞台顶
        float uiScale = GameBalance.RENDER_GLOBAL_SCALE;
        float worldScale = GameBalance.RENDER_GLOBAL_SCALE / GameBalance.SHOPPING_CAMERA_ZOOM;
        float buttonTop = BoardGeometry.screenY(BoardGeometry.FIGHT_BUTTON_Y + BoardGeometry.FIGHT_BUTTON_H, uiScale);
        float buttonBottom = BoardGeometry.screenY(BoardGeometry.FIGHT_BUTTON_Y, uiScale);
        float sellBottom = BoardGeometry.screenY(BoardGeometry.SELL_ZONE_Y, worldScale);
        float synergyTop = BoardGeometry.screenY(BoardGeometry.SYNERGY_Y + BoardGeometry.SYNERGY_H, uiScale);
        assertThat(buttonTop).isLessThanOrEqualTo(sellBottom);
        assertThat(buttonBottom).isGreaterThanOrEqualTo(synergyTop);
        // 不再落在抽屉展开态舞台范围 (2,88)~(246,306) 内（x 方向整个在抽屉右缘之外）
        assertThat(BoardGeometry.screenX(BoardGeometry.FIGHT_BUTTON_X, uiScale))
                .isGreaterThanOrEqualTo(246f);
        // 旧址 (134,88) 已弃用：按钮不再压备战席右侧的悬停卡走廊
        assertThat(BoardGeometry.FIGHT_BUTTON_X).isGreaterThan(
                BoardGeometry.BENCH_X + BoardGeometry.BENCH_W + 6);
    }
}
