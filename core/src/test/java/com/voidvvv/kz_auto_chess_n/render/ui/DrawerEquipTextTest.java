package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentPassive;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 抽屉装备区文案与两段式待定逻辑（UI 抽屉化批3）。断言迁移口径：
 * <ul>
 *   <li>背包悬停卡行集（HoverPreviewCardTest.inventoryCardLines*）→ 装备描述滚动框行集：折行列宽 6 → 10
 *       （预览描述框更宽），「超 7 行截断 …」退役改为全量 + ScrollPane 滚到底（溢出用例迁为容量断言）；</li>
 *   <li>悬停归一（normalizeInventorySlot）→ {@link DrawerEquipText#normalizeHover}：RESULT 期由放行收紧为 -1
 *       （③ 自 K7 起仅 SHOPPING 可见，原口径已无载体），另加拖拽抑制位；</li>
 *   <li>{@code InventoryPanel.reconcilePending} 四用例 → {@link DrawerEquipText#reconcilePending} 原样迁入。</li>
 * </ul>
 */
class DrawerEquipTextTest {

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

    private static Equipment sword(int id) {
        EquipmentData template = new EquipmentData("eq_sword_" + id, "铁剑", EquipmentSlot.WEAPON,
                EquipmentRarity.WHITE, Collections.<EquipmentEffect>emptyList(), null);
        return new Equipment(id, template);
    }

    // —— 装备描述行集（自背包悬停卡行集迁入；列宽 6 → 10，不再截断） ——

    @Test
    @DisplayName("装备描述行集：铁剑三行原样（各条 ≤10 列不折）")
    void descriptionLinesIronSword() {
        assertThat(DrawerEquipText.descriptionLines(ironSword(), DrawerEquipText.DESC_MAX_COLUMNS))
                .containsExactly("铁剑", "白装·武器", "攻击+20%");
    }

    @Test
    @DisplayName("装备描述行集：龙心被动行折 10 列贪心断点（被动：每 5 秒 回复 / 2% 最大生命）")
    void descriptionLinesDragonHeart() {
        assertThat(DrawerEquipText.descriptionLines(dragonHeart(), DrawerEquipText.DESC_MAX_COLUMNS))
                .containsExactly("龙心", "传说·盔甲", "生命+400", "被动：每 5 秒 回复", "2% 最大生命");
    }

    @Test
    @DisplayName("装备描述行集：多效果模板不再截断——8 行全量保留（滚到底由 ScrollPane 负责，容量见 DrawerTextCapacityTest）")
    void descriptionLinesKeepOverflowForScrolling() {
        EquipmentData many = new EquipmentData("eq_many", "测试", EquipmentSlot.TRINKET, EquipmentRarity.LEGENDARY,
                Arrays.asList(new EquipmentEffect(StatKey.ATTACK, EffectOp.ADD, 5f),
                        new EquipmentEffect(StatKey.ARMOR, EffectOp.ADD, 5f),
                        new EquipmentEffect(StatKey.HP, EffectOp.ADD, 50f),
                        new EquipmentEffect(StatKey.LIFESTEAL, EffectOp.ADD, 10f)),
                new EquipmentPassive(StatusType.REGEN, 0.02f, 5f));
        List<String> lines = DrawerEquipText.descriptionLines(many, DrawerEquipText.DESC_MAX_COLUMNS);
        assertThat(lines).containsExactly("测试", "传说·饰品", "攻击+5", "护甲+5", "生命+50", "吸血+10%",
                "被动：每 5 秒 回复", "2% 最大生命");
        assertThat(lines).allSatisfy(line ->
                assertThat(UnitInfoText.columns(line)).isLessThanOrEqualTo(DrawerEquipText.DESC_MAX_COLUMNS));
    }

    @Test
    @DisplayName("槽内短名 / 槽位短标 / 稀有度底色（沿 InventoryPanel 既有口径）")
    void slotLabelsAndRarityTints() {
        assertThat(DrawerEquipText.shortName("铁剑")).isEqualTo("铁剑");
        assertThat(DrawerEquipText.shortName("龙心")).isEqualTo("龙心");
        assertThat(DrawerEquipText.shortName("很长的装备名")).as("超宽截断：列宽−1 + …（UnitInfoText 既有省略号策略）").isEqualTo("很长…");
        assertThat(DrawerEquipText.slotMark(EquipmentSlot.WEAPON)).isEqualTo("武");
        assertThat(DrawerEquipText.slotMark(EquipmentSlot.ARMOR)).isEqualTo("甲");
        assertThat(DrawerEquipText.slotMark(EquipmentSlot.TRINKET)).isEqualTo("饰");
        assertThat(DrawerEquipText.rarityTint(null)).isSameAs(DrawerEquipText.TINT_EMPTY);
        assertThat(DrawerEquipText.rarityTint(new Equipment(1, ironSword()))).isSameAs(DrawerEquipText.TINT_WHITE);
        assertThat(DrawerEquipText.rarityTint(new Equipment(2, dragonHeart()))).isSameAs(DrawerEquipText.TINT_LEGENDARY);
        assertThat(DrawerEquipText.TINT_PENDING).isEqualTo(new Color(1f, 0.9f, 0.3f, 1f));
    }

    // —— 两段式穿戴待定态 ——

    @Test
    @DisplayName("点击装备格 → 待定：点空白取消；再点同一装备 = 取消；点其它装备 = 换待定（input §2.4）")
    void nextPendingTogglesAndSwitches() {
        Equipment a = sword(5);
        Equipment b = sword(6);
        assertThat(DrawerEquipText.nextPending(-1, null)).isEqualTo(-1);
        assertThat(DrawerEquipText.nextPending(-1, a)).isEqualTo(5);
        assertThat(DrawerEquipText.nextPending(5, a)).as("再点同一装备 = 取消").isEqualTo(-1);
        assertThat(DrawerEquipText.nextPending(5, b)).as("点其它装备 = 换待定").isEqualTo(6);
        assertThat(DrawerEquipText.nextPending(5, null)).as("点空白取消").isEqualTo(-1);
    }

    @Test
    @DisplayName("待定物品仍在背包：待定态保留")
    void keepsPendingWhileItemInInventory() {
        Player player = new Player(10);
        Equipment item = sword(5);
        player.addToInventory(item);
        EquipPendingState pending = new EquipPendingState();
        pending.set(5);

        DrawerEquipText.reconcilePending(pending, player);

        assertThat(pending.hasPending()).isTrue();
        assertThat(pending.pendingItemId()).isEqualTo(5);
    }

    @Test
    @DisplayName("待定物品已不在背包（已穿/卖出）：自动取消")
    void clearsPendingWhenItemGone() {
        Player player = new Player(10);
        player.addToInventory(sword(5)); // 入包后又移除（如穿上）
        player.removeFromInventory(player.getInventory().get(0));
        EquipPendingState pending = new EquipPendingState();
        pending.set(5);

        DrawerEquipText.reconcilePending(pending, player);

        assertThat(pending.hasPending()).isFalse();
    }

    @Test
    @DisplayName("背包里是其他物品（id 不匹配）：同样取消")
    void clearsPendingWhenOnlyOtherItemsRemain() {
        Player player = new Player(10);
        player.addToInventory(sword(6));
        EquipPendingState pending = new EquipPendingState();
        pending.set(5);

        DrawerEquipText.reconcilePending(pending, player);

        assertThat(pending.hasPending()).isFalse();
    }

    @Test
    @DisplayName("无待定态时调用为无操作")
    void noPendingIsNoOp() {
        Player player = new Player(10);
        EquipPendingState pending = new EquipPendingState();

        DrawerEquipText.reconcilePending(pending, player);

        assertThat(pending.hasPending()).isFalse();
        assertThat(pending.pendingItemId()).isEqualTo(-1);
    }

    // —— 装备大图预览目标优先级（批4：自 DrawerEquipSection.bind 内联逻辑抽为纯函数） ——

    @Test
    @DisplayName("预览目标：悬停槽物品优先于待定物品（即便待定的是另一件）")
    void previewPrefersHoveredOverPending() {
        Equipment hovered = sword(7);
        Equipment pending = sword(5);
        List<Equipment> inventory = Arrays.asList(pending, hovered);
        assertThat(DrawerEquipText.previewTarget(hovered, inventory, 5)).isSameAs(hovered);
    }

    @Test
    @DisplayName("预览目标：无悬停时回落到待定物品；待定物品已不在包 → 视为无（null）")
    void previewFallsBackToPendingWhileInInventory() {
        Equipment pending = sword(5);
        List<Equipment> inventory = Arrays.asList(sword(6), pending);
        assertThat(DrawerEquipText.previewTarget(null, inventory, 5)).isSameAs(pending);
        assertThat(DrawerEquipText.previewTarget(null, Arrays.asList(sword(6)), 5)).as("待定已失配").isNull();
    }

    @Test
    @DisplayName("预览目标：无悬停且无待定 → 无")
    void previewNoneWhenNeitherHoverNorPending() {
        assertThat(DrawerEquipText.previewTarget(null, Arrays.asList(sword(6)), -1)).isNull();
        assertThat(DrawerEquipText.previewTarget(null, Collections.<Equipment>emptyList(), -1)).isNull();
    }

    // —— 悬停归一（自 normalizeInventorySlot 迁入）与写门控 ——

    @Test
    @DisplayName("悬停归一：SHOPPING 放行；BATTLE/RESULT 期、拖拽中、空槽/越界/负值 → -1")
    void normalizeHoverCoversPhasesAndBounds() {
        assertThat(DrawerEquipText.normalizeHover(GamePhase.SHOPPING, 2, 3, false)).isEqualTo(2);
        assertThat(DrawerEquipText.normalizeHover(GamePhase.BATTLE, 2, 3, false)).isEqualTo(-1); // 置灰期
        assertThat(DrawerEquipText.normalizeHover(GamePhase.RESULT, 2, 3, false))
                .as("③ 自 K7 起仅 SHOPPING 可见——RESULT 期口径随之收紧").isEqualTo(-1);
        assertThat(DrawerEquipText.normalizeHover(GamePhase.SHOPPING, 2, 3, true)).as("拖拽中抑制").isEqualTo(-1);
        assertThat(DrawerEquipText.normalizeHover(GamePhase.SHOPPING, 3, 3, false)).isEqualTo(-1); // 空槽
        assertThat(DrawerEquipText.normalizeHover(GamePhase.SHOPPING, -1, 3, false)).isEqualTo(-1);
    }

    @Test
    @DisplayName("写操作门控（Q6）：仅备战期允许穿戴 / 卸下 / 待定")
    void writesAllowedOnlyWhileShopping() {
        assertThat(DrawerEquipText.writesAllowed(GamePhase.SHOPPING)).isTrue();
        assertThat(DrawerEquipText.writesAllowed(GamePhase.BATTLE)).isFalse();
        assertThat(DrawerEquipText.writesAllowed(GamePhase.RESULT)).isFalse();
        assertThat(DrawerEquipText.writesAllowed(GamePhase.RUN_END)).isFalse();
    }
}
