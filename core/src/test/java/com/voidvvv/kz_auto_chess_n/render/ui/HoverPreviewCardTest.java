package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentPassive;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 悬停预览卡行集拼装测试（feedback04 + feedback07）：boardCardLines 静态纯函数——玩家候选不加标记、
 * 敌方候选（虚影/敌侧战斗单位）首行加"（敌方）"、容量截断在标记后施加（总行数不超卡高）；
 * feedback07 背包格装备卡（inventoryCardLines 折 6 列截 7 行）与悬停归一（normalizeInventorySlot）。
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

    // —— feedback07：背包格装备卡（inventoryCardLines 折 6 列 × 截 7 行） ——

    /** 铁剑镜像（equipments.json eq_iron_sword：白武，攻击 PCT 20） */
    private static EquipmentData ironSword() {
        return new EquipmentData("eq_iron_sword", "铁剑", EquipmentSlot.WEAPON, EquipmentRarity.WHITE,
                Arrays.asList(new EquipmentEffect(StatKey.ATTACK, EffectOp.PCT, 20f)), null);
    }

    /** 龙心镜像（equipments.json eq_dragon_heart：传说盔甲，HP+400 + REGEN 0.02/5） */
    private static EquipmentData dragonHeart() {
        return new EquipmentData("eq_dragon_heart", "龙心", EquipmentSlot.ARMOR, EquipmentRarity.LEGENDARY,
                Arrays.asList(new EquipmentEffect(StatKey.HP, EffectOp.ADD, 400f)),
                new EquipmentPassive(StatusType.REGEN, 0.02f, 5f));
    }

    @Test
    @DisplayName("背包卡行集：铁剑三行原样（各条 ≤6 列不折）")
    void inventoryCardLinesIronSword() {
        assertThat(HoverPreviewCard.inventoryCardLines(ironSword()))
                .containsExactly("铁剑", "白装·武器", "攻击+20%");
    }

    @Test
    @DisplayName("背包卡行集：龙心被动行折 6 列贪心断点（每 5 / 秒 回复 2% / 最大生命）")
    void inventoryCardLinesDragonHeart() {
        assertThat(HoverPreviewCard.inventoryCardLines(dragonHeart()))
                .containsExactly("龙心", "传说·盔甲", "生命+400", "被动：每 5", "秒 回复 2%", "最大生命");
    }

    @Test
    @DisplayName("背包卡行集：多效果模板超 7 行 → 截断为 6 行 + 末行 …")
    void inventoryCardLinesClipsOverflow() {
        EquipmentData many = new EquipmentData("eq_many", "测试", EquipmentSlot.TRINKET, EquipmentRarity.LEGENDARY,
                Arrays.asList(new EquipmentEffect(StatKey.ATTACK, EffectOp.ADD, 5f),
                        new EquipmentEffect(StatKey.ARMOR, EffectOp.ADD, 5f),
                        new EquipmentEffect(StatKey.HP, EffectOp.ADD, 50f),
                        new EquipmentEffect(StatKey.LIFESTEAL, EffectOp.ADD, 10f)),
                new EquipmentPassive(StatusType.REGEN, 0.02f, 5f));
        assertThat(HoverPreviewCard.inventoryCardLines(many))
                .containsExactly("测试", "传说·饰品", "攻击+5", "护甲+5", "生命+50", "吸血+10%", "…");
    }

    // —— feedback07：背包悬停归一（BATTLE 置灰抑制 + 空槽/越界 → -1） ——

    @Test
    @DisplayName("悬停归一：BATTLE 置灰期与空槽/越界/负值 → -1；其余阶段照常放行")
    void normalizeInventorySlotCoversPhasesAndBounds() {
        assertThat(HoverPreviewCard.normalizeInventorySlot(GamePhase.SHOPPING, 2, 3)).isEqualTo(2);
        assertThat(HoverPreviewCard.normalizeInventorySlot(GamePhase.BATTLE, 2, 3)).isEqualTo(-1); // 置灰抑制
        assertThat(HoverPreviewCard.normalizeInventorySlot(GamePhase.SHOPPING, 3, 3)).isEqualTo(-1); // 空槽
        assertThat(HoverPreviewCard.normalizeInventorySlot(GamePhase.SHOPPING, -1, 3)).isEqualTo(-1);
        assertThat(HoverPreviewCard.normalizeInventorySlot(GamePhase.RESULT, 2, 3)).isEqualTo(2);
        assertThat(HoverPreviewCard.normalizeInventorySlot(GamePhase.RUN_END, 0, 1)).isEqualTo(0);
    }
}
