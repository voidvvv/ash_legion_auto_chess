package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 悬停预览卡行集拼装测试（feedback04 + feedback07 + 2026-11-21 v5 全量文案整改）：
 * boardCardLines/shopCardLines 静态纯函数——玩家候选不加标记、敌方候选首行加"（敌方）"、
 * 棋子卡全量不截断（容量由 UnitInfoTextSeedDataTest 按真实 assets 锁定）；卡高纯函数 cardHeightFor；
 * UI 抽屉化批3：背包格装备卡（feedback07 第三源）退役——inventoryCardLines / normalizeInventorySlot 及其 4 个用例
 * 迁至 DrawerEquipTextTest（折行列宽 6 → 10、改全量滚动；RESULT 期口径收紧）——测试数只增不减。
 * 卡绘制本体走 lwjgl3 手验（Assets 构造需 GL）。
 */
class HoverPreviewCardTest {

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, false);
    }

    private static GameData emptyData() {
        return new GameData(new LinkedHashMap<String, UnitData>(),
                new LinkedHashMap<String, SkillData>(),
                new LinkedHashMap<String, SynergyData>(),
                new LinkedHashMap<String, SceneData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                new ArrayList<String>());
    }

    @Test
    @DisplayName("玩家候选行集：与 previewLines 原样一致（无标记行、不截断）")
    void playerLinesUnmarked() {
        List<String> plain = UnitInfoText.previewLines(tpl("u1"), emptyData(), 9);
        List<String> card = HoverPreviewCard.boardCardLines(tpl("u1"), false, emptyData());

        assertThat(card).isEqualTo(plain);
        assertThat(card.get(0)).doesNotContain("敌方");
    }

    @Test
    @DisplayName("敌方候选行集：首行（敌方）标记，正文自 previewLines 原样跟随（全量）")
    void enemyLinesMarked() {
        List<String> plain = UnitInfoText.previewLines(tpl("foe"), emptyData(), 9);
        List<String> card = HoverPreviewCard.boardCardLines(tpl("foe"), true, emptyData());

        assertThat(card.get(0)).isEqualTo("（敌方）");
        assertThat(card.subList(1, card.size())).isEqualTo(plain);
    }

    @Test
    @DisplayName("商店卡行集：previewLines 11 列原样（不截断）；卡高纯函数 = 顶留白+行数×9+底留白")
    void shopLinesAndCardHeight() {
        List<String> shop = HoverPreviewCard.shopCardLines(tpl("u1"), emptyData());
        assertThat(shop).isEqualTo(UnitInfoText.previewLines(tpl("u1"), emptyData(), 11));

        // 卡内正文楷体 8.5px；cardHeightFor = 4 + n×9 + 4（9px 行距保住容量锚点）
        assertThat(HoverPreviewCard.LINE_HEIGHT).isEqualTo(9f);
        assertThat(HoverPreviewCard.cardHeightFor(1)).isEqualTo(17f);
        assertThat(HoverPreviewCard.cardHeightFor(27)).isEqualTo(251f); // 真实最坏组合（伏击豹 敌方标记）
    }
}
