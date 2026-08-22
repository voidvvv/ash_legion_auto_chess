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
 * 悬停预览卡行集拼装测试（feedback04）：boardCardLines 静态纯函数——玩家候选不加标记、
 * 敌方候选（虚影/敌侧战斗单位）首行加"（敌方）"、容量截断在标记后施加（总行数不超卡高）。
 * 卡绘制本体走 lwjgl3 手验（Assets 构造需 GL）。
 */
class HoverPreviewCardTest {

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
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
    @DisplayName("玩家候选行集：与 previewLines 原样一致（无标记行）")
    void playerLinesUnmarked() {
        List<String> plain = UnitInfoText.previewLines(tpl("u1"), emptyData(), 7);
        List<String> card = HoverPreviewCard.boardCardLines(tpl("u1"), false, emptyData());

        assertThat(card).isEqualTo(plain);
        assertThat(card.get(0)).doesNotContain("敌方");
    }

    @Test
    @DisplayName("敌方候选行集：首行（敌方）标记，正文自 previewLines 原样跟随")
    void enemyLinesMarked() {
        List<String> plain = UnitInfoText.previewLines(tpl("foe"), emptyData(), 7);
        List<String> card = HoverPreviewCard.boardCardLines(tpl("foe"), true, emptyData());

        assertThat(card.get(0)).isEqualTo("（敌方）");
        assertThat(card.subList(1, card.size())).isEqualTo(plain);
    }
}
