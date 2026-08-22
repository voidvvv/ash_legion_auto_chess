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
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知文案纯函数测试（CP28；口径 #13）：命令行四态（RefreshShop/BuyExp/SellUnit/其余 null）
 * 与战斗事件行带主体名（feedback06：UNIT_DIED/CAST 行显施放者/亡者中文名，敌方附（敌方）
 * 标记、超 16 列截断；主体查 BattleState，与名单 id 不同源；CP15 起 CAST 显中文技能名）。
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

    // —— feedback06 夹具：微型战斗（BattleTestFixtures 公开夹具；名字 = "夹具" + 模板 id） ——

    private static BattleState battleWith(BattleUnit... units) {
        return BattleTestFixtures.state(units);
    }

    @Test
    @DisplayName("战斗事件行：UNIT_DIED 带主体名（玩家侧）")
    void unitDiedLineWithSubject() {
        BattleState state = battleWith(BattleTestFixtures.unit(11, Side.PLAYER,
                BattleTestFixtures.tpl("u_a"), 0, 4));
        assertThat(NotificationPanel.formatEvent(CombatEvent.unitDied(3, 11), emptyData(), state))
                .isEqualTo("夹具u_a 倒下");
    }

    @Test
    @DisplayName("战斗事件行：UNIT_DIED 敌方主体带（敌方）标记（feedback04-2 同款字面）")
    void unitDiedLineMarksEnemy() {
        BattleState state = battleWith(BattleTestFixtures.unit(21, Side.ENEMY,
                BattleTestFixtures.tpl("u_e"), 0, 0));
        assertThat(NotificationPanel.formatEvent(CombatEvent.unitDied(3, 21), emptyData(), state))
                .isEqualTo("夹具u_e（敌方） 倒下");
    }

    @Test
    @DisplayName("战斗事件行：CAST 带主体名 + 中文技能名（GameData 查表）")
    void castLineWithSubjectAndSkillName() {
        GameData data = new GameData(new LinkedHashMap<String, UnitData>(),
                skillsOf("skill_fireball", "火球术"),
                new LinkedHashMap<String, SynergyData>(),
                new LinkedHashMap<String, SceneData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                new ArrayList<String>());
        BattleState state = battleWith(BattleTestFixtures.unit(1, Side.PLAYER,
                BattleTestFixtures.tpl("u_a"), 0, 4));
        assertThat(NotificationPanel.formatEvent(
                CombatEvent.cast(3, 1, 2, "skill_fireball"), data, state))
                .isEqualTo("夹具u_a 施放 火球术");
    }

    @Test
    @DisplayName("战斗事件行：CAST 敌方主体带（敌方）标记")
    void castLineMarksEnemy() {
        GameData data = new GameData(new LinkedHashMap<String, UnitData>(),
                skillsOf("skill_fireball", "火球术"),
                new LinkedHashMap<String, SynergyData>(),
                new LinkedHashMap<String, SceneData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                new ArrayList<String>());
        BattleState state = battleWith(BattleTestFixtures.unit(1, Side.ENEMY,
                BattleTestFixtures.tpl("u_e"), 0, 0));
        assertThat(NotificationPanel.formatEvent(
                CombatEvent.cast(3, 1, 2, "skill_fireball"), data, state))
                .isEqualTo("夹具u_e（敌方） 施放 火球术");
    }

    @Test
    @DisplayName("战斗事件行：技能查表失败回退原始 id（防御路径，主体仍解析）")
    void castLineFallsBackToSkillId() {
        BattleState state = battleWith(BattleTestFixtures.unit(1, Side.PLAYER,
                BattleTestFixtures.tpl("u_a"), 0, 4));
        assertThat(NotificationPanel.formatEvent(
                CombatEvent.cast(3, 1, 2, "skill_ghost"), emptyData(), state))
                .isEqualTo("夹具u_a 施放 skill_ghost");
    }

    @Test
    @DisplayName("战斗事件行：主体查不到回退 #id（id 不在战斗 / state 为 null——防御路径）")
    void subjectFallsBackToHashId() {
        BattleState state = battleWith(BattleTestFixtures.unit(1, Side.PLAYER,
                BattleTestFixtures.tpl("u_a"), 0, 4));
        assertThat(NotificationPanel.formatEvent(CombatEvent.unitDied(3, 99), emptyData(), state))
                .isEqualTo("#99 倒下");
        assertThat(NotificationPanel.formatEvent(CombatEvent.unitDied(3, 99), emptyData(), null))
                .isEqualTo("#99 倒下");
    }

    @Test
    @DisplayName("战斗事件行：极端长主体截断 ≤16 列且以 … 收尾（口径 A2-3）")
    void longSubjectLineTruncated() {
        BattleState state = battleWith(BattleTestFixtures.unit(1, Side.ENEMY,
                BattleTestFixtures.tpl("名字特别长的测试单位"), 0, 0));
        String line = NotificationPanel.formatEvent(
                CombatEvent.cast(3, 1, 2, "skill_fireball"), emptyData(), state);
        assertThat(UnitInfoText.columns(line)).isLessThanOrEqualTo(NotificationPanel.NOTIFY_MAX_COLUMNS);
        assertThat(line).endsWith("…");
    }

    @Test
    @DisplayName("战斗事件行：HIT 过噪跳过（返回 null；口径 #13 维持不变）")
    void hitFilteredOut() {
        assertThat(NotificationPanel.formatEvent(
                CombatEvent.hit(3, 1, 2, 12.5f, false, null), emptyData(), null)).isNull();
    }

    // —— feedback06：BattleState 生命周期（syncBattle 与 inbox 对齐） ——

    @Test
    @DisplayName("syncBattle 保留/释放 BattleState 引用（attach 赋值、detach 置 null）")
    void syncBattleRetainsAndReleasesState() {
        NotificationPanel panel = new NotificationPanel(null, () -> null,
                new com.voidvvv.kz_auto_chess_n.command.CommandManager()); // refresh 零 GL：assets 可 null
        BattleState state = battleWith(BattleTestFixtures.unit(1, Side.PLAYER,
                BattleTestFixtures.tpl("u_a"), 0, 4));

        panel.syncBattle(state);
        assertThat(panel.currentBattle()).isSameAs(state);

        panel.syncBattle(null);
        assertThat(panel.currentBattle()).isNull();
    }
}
