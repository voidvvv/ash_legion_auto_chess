package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.command.BuyExpCommand;
import com.voidvvv.kz_auto_chess_n.command.RefreshShopCommand;
import com.voidvvv.kz_auto_chess_n.command.SellUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.StartBattleCommand;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知文案纯函数测试（CP28；口径 #13）：命令行四态（RefreshShop/BuyExp/SellUnit/其余 null）
 * 与战斗事件行三态（UNIT_DIED/CAST/过噪跳过）。SellUnit 返回静态行、动态数额由 notices
 * 富行承担（WARNING-15 去重口径）。
 */
class NotificationFormatTest {

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
        assertThat(NotificationPanel.formatEvent(CombatEvent.unitDied(3, 11)))
                .isEqualTo("单位倒下");
    }

    @Test
    @DisplayName("战斗事件行：CAST 带技能 id")
    void castLineWithSkillId() {
        assertThat(NotificationPanel.formatEvent(CombatEvent.cast(3, 1, 2, "skill_fireball")))
                .isEqualTo("技能施放: skill_fireball");
    }

    @Test
    @DisplayName("战斗事件行：HIT 过噪跳过（返回 null）")
    void hitFilteredOut() {
        assertThat(NotificationPanel.formatEvent(CombatEvent.hit(3, 1, 2, 12.5f, false, null)))
                .isNull();
    }
}
