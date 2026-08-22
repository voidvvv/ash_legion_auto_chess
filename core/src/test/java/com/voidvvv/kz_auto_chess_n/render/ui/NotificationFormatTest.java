package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.command.BuyExpCommand;
import com.voidvvv.kz_auto_chess_n.command.RefreshShopCommand;
import com.voidvvv.kz_auto_chess_n.command.SellUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.StartBattleCommand;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffect;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知文案纯函数测试（CP28；口径 #13）：命令行四态（RefreshShop/BuyExp/SellUnit/其余 null）
 * 与战斗事件行三态（UNIT_DIED/CAST/过噪跳过；CP15 起 CAST 显中文技能名，GameData 查表）。
 * SellUnit 返回静态行、动态数额由 notices 富行承担（WARNING-15 去重口径）。
 */
class NotificationFormatTest {

    // —— 夹具（GameData 六参构造，沿 UnitDetailDialogTest 先例） ——

    private static LinkedHashMap<String, SkillData> skillsOf(String id, String name) {
        LinkedHashMap<String, SkillData> skills = new LinkedHashMap<String, SkillData>();
        skills.put(id, new SkillData(id, name, "夹具", SkillShape.SELF, Delivery.MELEE_INSTANT,
                Collections.<SkillEffect>emptyList()));
        return skills;
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
    @DisplayName("RefreshShop 命令行：刷新商店（-2 金）")
    void refreshShopLine() {
        assertThat(NotificationFormat.formatCommand(RefreshShopCommand.INSTANCE))
                .isEqualTo("刷新商店（-2 金）");
    }

    @Test
    @DisplayName("BuyExp 命令行：购买经验（-4 金 +4 经验）")
    void buyExpLine() {
        assertThat(NotificationFormat.formatCommand(BuyExpCommand.INSTANCE))
                .isEqualTo("购买经验（-4 金 +4 经验）");
    }

    @Test
    @DisplayName("SellUnit 静态行：卖出棋子（数额动态行走 notices，不重复）")
    void sellUnitStaticLine() {
        assertThat(NotificationFormat.formatCommand(new SellUnitCommand(7)))
                .isEqualTo("卖出棋子");
    }

    @Test
    @DisplayName("其余命令返回 null 不入面板（买/穿/脱/领箱动态行均走 notices）")
    void otherCommandsReturnNull() {
        assertThat(NotificationFormat.formatCommand(StartBattleCommand.INSTANCE)).isNull();
    }

    @Test
    @DisplayName("战斗事件行：UNIT_DIED → 单位倒下")
    void unitDiedLine() {
        assertThat(NotificationPanel.formatEvent(CombatEvent.unitDied(3, 11), emptyData()))
                .isEqualTo("单位倒下");
    }

    @Test
    @DisplayName("战斗事件行：CAST 显中文技能名（GameData 查表）")
    void castLineWithSkillName() {
        GameData data = new GameData(new LinkedHashMap<String, UnitData>(),
                skillsOf("skill_fireball", "火球术"),
                new LinkedHashMap<String, SynergyData>(),
                new LinkedHashMap<String, SceneData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                new ArrayList<String>());
        assertThat(NotificationPanel.formatEvent(
                CombatEvent.cast(3, 1, 2, "skill_fireball"), data))
                .isEqualTo("技能施放：火球术");
    }

    @Test
    @DisplayName("战斗事件行：CAST 查表失败回退原始 id（防御路径）")
    void castLineFallsBackToId() {
        assertThat(NotificationPanel.formatEvent(
                CombatEvent.cast(3, 1, 2, "skill_ghost"), emptyData()))
                .isEqualTo("技能施放：skill_ghost");
    }

    @Test
    @DisplayName("战斗事件行：HIT 过噪跳过（返回 null）")
    void hitFilteredOut() {
        assertThat(NotificationPanel.formatEvent(CombatEvent.hit(3, 1, 2, 12.5f, false, null), emptyData()))
                .isNull();
    }
}
