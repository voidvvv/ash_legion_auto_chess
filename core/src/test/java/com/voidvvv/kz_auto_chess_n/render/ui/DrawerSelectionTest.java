package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 抽屉选中态 / 装备页指纹纯逻辑测试（UI 抽屉化批4）。断言迁移口径：{@code UnitDetailDialogTest}（11 例）随弹窗
 * 退役，其纯逻辑语义原样迁至此——「过期自动收起」→「棋子已不在名单则清选中」、「卸下按钮按单位 id + 装备 id 序列
 * 指纹重建」→ {@link DrawerSelection#fingerprintChanged}。测试数只增不减。
 */
class DrawerSelectionTest {

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, false);
    }

    private static GameData emptyData() {
        return new GameData(new LinkedHashMap<String, UnitData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                new LinkedHashMap<String, EquipmentData>(),
                new ArrayList<String>());
    }

    private static Equipment item(int id, EquipmentSlot slot) {
        return new Equipment(id, new EquipmentData("eq_" + id, "装备" + id, slot, EquipmentRarity.WHITE,
                Collections.<EquipmentEffect>emptyList(), null));
    }

    private static RunContext context() {
        return new RunContext(new Player(0), new RunState(42L, "scene_forest", new SequentialIdIssuer()),
                emptyData(), new RandomGenerator(42L));
    }

    // —— 选中有效性（自 unitGone 四例迁入） ——

    @Test
    @DisplayName("席上棋子：未消失，选中保留")
    void unitOnBenchKeepsSelection() {
        RunContext ctx = context();
        ctx.getPlayer().addToBench(new Unit(1, tpl("u1"), 1));
        assertThat(DrawerSelection.unitGone(ctx, 1)).isFalse();
        assertThat(DrawerSelection.resolveSelectedId(ctx, 1)).isEqualTo(1);
    }

    @Test
    @DisplayName("场上棋子：未消失，选中保留")
    void deployedUnitKeepsSelection() {
        RunContext ctx = context();
        Unit unit = new Unit(2, tpl("u2"), 1);
        ctx.getPlayer().addToBench(unit);
        ctx.getPlayer().deploy(unit, 3, 5);
        assertThat(DrawerSelection.unitGone(ctx, 2)).isFalse();
        assertThat(DrawerSelection.resolveSelectedId(ctx, 2)).isEqualTo(2);
    }

    @Test
    @DisplayName("选中棋子被卖出/合并移出名单 → 清选中（-1）——原详情弹窗「过期自动收起」的抽屉形态")
    void removedUnitClearsSelection() {
        RunContext ctx = context();
        Unit unit = new Unit(3, tpl("u3"), 1);
        ctx.getPlayer().addToBench(unit);
        assertThat(DrawerSelection.resolveSelectedId(ctx, 3)).isEqualTo(3);
        ctx.getPlayer().removeUnit(unit);
        assertThat(DrawerSelection.unitGone(ctx, 3)).isTrue();
        assertThat(DrawerSelection.resolveSelectedId(ctx, 3)).isEqualTo(-1);
    }

    @Test
    @DisplayName("未知 id / 未选中（-1）同判过期：保持 -1")
    void unknownAndUnselectedResolveToCleared() {
        RunContext ctx = context();
        assertThat(DrawerSelection.unitGone(ctx, 999)).isTrue();
        assertThat(DrawerSelection.unitGone(ctx, -1)).isTrue();
        assertThat(DrawerSelection.resolveSelectedId(ctx, 999)).isEqualTo(-1);
        assertThat(DrawerSelection.resolveSelectedId(ctx, -1)).isEqualTo(-1);
    }

    // —— 装备 id 序列与指纹（自 sameEquippedIds 两例 + 按钮重建三例迁入） ——

    @Test
    @DisplayName("装备 id 序列比对：同序列 / 同为空 → 相等")
    void sameEquippedIdsMatchesEqualSequences() {
        assertThat(DrawerSelection.sameEquippedIds(Arrays.asList(1, 2), Arrays.asList(1, 2))).isTrue();
        assertThat(DrawerSelection.sameEquippedIds(
                Collections.<Integer>emptyList(), Collections.<Integer>emptyList())).isTrue();
    }

    @Test
    @DisplayName("装备 id 序列比对：元素不同 / 长度不同 / 顺序不同 → 不等")
    void sameEquippedIdsRejectsDifferentSequences() {
        assertThat(DrawerSelection.sameEquippedIds(Arrays.asList(1, 2), Arrays.asList(1, 3))).isFalse();
        assertThat(DrawerSelection.sameEquippedIds(Arrays.asList(1), Arrays.asList(1, 2))).isFalse();
        assertThat(DrawerSelection.sameEquippedIds(Arrays.asList(2, 1), Arrays.asList(1, 2))).isFalse();
    }

    @Test
    @DisplayName("装备 id 序列按穿着序生成；无装备 → 空序列")
    void equippedIdsFollowWearingOrder() {
        List<Equipment> equipped = Arrays.asList(item(5, EquipmentSlot.WEAPON),
                item(6, EquipmentSlot.ARMOR), item(7, EquipmentSlot.TRINKET));
        assertThat(DrawerSelection.equippedIds(equipped)).containsExactly(5, 6, 7);
        assertThat(DrawerSelection.equippedIds(Collections.<Equipment>emptyList())).isEmpty();
    }

    @Test
    @DisplayName("卸下钮指纹：单位同、装备 id 序列未变 → 不重建（保留按钮实例）")
    void unchangedEquippedIdsKeepFingerprint() {
        Unit unit = new Unit(1, tpl("u1"), 1);
        unit.equip(item(5, EquipmentSlot.WEAPON));
        List<Integer> ids = DrawerSelection.equippedIds(unit.getEquipped());
        assertThat(DrawerSelection.fingerprintChanged(1, ids, unit)).isFalse();
    }

    @Test
    @DisplayName("卸下钮指纹：穿上（序列变长）/ 卸下（序列变短或换件）→ 重建")
    void changedEquippedIdsInvalidateFingerprint() {
        Unit unit = new Unit(1, tpl("u1"), 1);
        unit.equip(item(5, EquipmentSlot.WEAPON));
        List<Integer> before = DrawerSelection.equippedIds(unit.getEquipped());
        unit.equip(item(6, EquipmentSlot.ARMOR)); // 穿上第二件
        assertThat(DrawerSelection.fingerprintChanged(1, before, unit)).isTrue();

        List<Integer> two = DrawerSelection.equippedIds(unit.getEquipped());
        unit.unequip(unit.getEquipped().get(0)); // 卸下武器
        assertThat(DrawerSelection.fingerprintChanged(1, two, unit)).isTrue();
    }

    @Test
    @DisplayName("卸下钮指纹：换单位（即便装备序列相同）/ 由无到有 / 由有到无 → 重建；无→无 → 不重建")
    void switchingUnitInvalidatesFingerprint() {
        Unit a = new Unit(1, tpl("u1"), 1);
        Unit b = new Unit(2, tpl("u2"), 1);
        a.equip(item(5, EquipmentSlot.WEAPON));
        b.equip(item(5, EquipmentSlot.WEAPON));
        List<Integer> ids = DrawerSelection.equippedIds(a.getEquipped());
        assertThat(DrawerSelection.fingerprintChanged(1, ids, b)).as("换单位").isTrue();
        assertThat(DrawerSelection.fingerprintChanged(-1, Collections.<Integer>emptyList(), b)).as("由无到有").isTrue();
        assertThat(DrawerSelection.fingerprintChanged(1, ids, null)).as("由有到无").isTrue();
        assertThat(DrawerSelection.fingerprintChanged(-1, Collections.<Integer>emptyList(), null)).as("无→无").isFalse();
    }

    @Test
    @DisplayName("行号 ↔ 装备槽：武器 0（上）/ 盔甲 1 / 饰品 2（沿 EquipmentSlot 声明序，与装备页三行一致）")
    void rowOfMapsSlotsInDeclarationOrder() {
        assertThat(DrawerSelection.rowOf(EquipmentSlot.WEAPON)).isZero();
        assertThat(DrawerSelection.rowOf(EquipmentSlot.ARMOR)).isEqualTo(1);
        assertThat(DrawerSelection.rowOf(EquipmentSlot.TRINKET)).isEqualTo(2);
        assertThat(DrawerGeometry.EQUIP_TAB_ROWS).isEqualTo(EquipmentSlot.values().length);
    }
}
