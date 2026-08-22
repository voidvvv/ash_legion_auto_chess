package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
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
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 棋子详情弹窗测试（CP25 + feedback04）：unitGone 纯函数直测——单位在名单（板/席）内
 * 保留弹窗，被卖出/合并移出后自动收起。卸下按钮重建判定（sameEquippedIds）亦纯函数直测；
 * 按钮实例保留行为经 headless 弹窗验证（Assets 仅 draw 路径使用，refresh 零 GL——传 null）。
 * 弹窗本体绘制走 lwjgl3 手验（Assets 构造需 GL——计划测试要点给出的静态抽取方案）。
 */
class UnitDetailDialogTest {

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, false);
    }

    private static RunContext context(Player player) {
        GameData empty = new GameData(new LinkedHashMap<String, UnitData>(),
                new LinkedHashMap<String, SkillData>(),
                new LinkedHashMap<String, SynergyData>(),
                new LinkedHashMap<String, SceneData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                new ArrayList<String>());
        return new RunContext(player, new RunState(42L, "scene_forest", new SequentialIdIssuer()),
                empty, new RandomGenerator(42L));
    }

    @Test
    @DisplayName("单位在备战席：未过期（弹窗保留）")
    void unitOnBenchKeepsDialog() {
        Player player = new Player(10);
        player.addToBench(new Unit(5, tpl("unit_a"), 1));

        assertThat(UnitDetailDialog.unitGone(context(player), 5)).isFalse();
    }

    @Test
    @DisplayName("单位已上棋盘：同样未过期")
    void deployedUnitKeepsDialog() {
        Player player = new Player(10);
        Unit unit = new Unit(5, tpl("unit_a"), 1);
        player.addToBench(unit); // deploy 前置：先入席再上板
        player.deploy(unit, 3, 4); // 玩家区 y 4~6

        assertThat(UnitDetailDialog.unitGone(context(player), 5)).isFalse();
    }

    @Test
    @DisplayName("单位被移出名单（卖出/合并）：过期（弹窗自动收起）")
    void removedUnitExpiresDialog() {
        Player player = new Player(10);
        Unit unit = new Unit(5, tpl("unit_a"), 1);
        player.addToBench(unit);
        player.removeUnit(unit);

        assertThat(UnitDetailDialog.unitGone(context(player), 5)).isTrue();
    }

    @Test
    @DisplayName("不存在的 id（含未打开过的 -1）：过期")
    void unknownIdIsGone() {
        Player player = new Player(10);

        assertThat(UnitDetailDialog.unitGone(context(player), 999)).isTrue();
        assertThat(UnitDetailDialog.unitGone(context(player), -1)).isTrue();
    }

    // —— feedback04：卸下按钮重建判定（sameEquippedIds 纯函数） ——

    @Test
    @DisplayName("装备 id 序列比对：同长同序相等 → true；空序列互等 → true")
    void sameEquippedIdsMatchesEqualSequences() {
        assertThat(UnitDetailDialog.sameEquippedIds(
                Arrays.asList(10, 11), Arrays.asList(10, 11))).isTrue();
        assertThat(UnitDetailDialog.sameEquippedIds(
                new ArrayList<Integer>(), new ArrayList<Integer>())).isTrue();
    }

    @Test
    @DisplayName("装备 id 序列比对：顺序互换 / 长度不同 / 值不同 → 均 false")
    void sameEquippedIdsRejectsDifferentSequences() {
        assertThat(UnitDetailDialog.sameEquippedIds(
                Arrays.asList(10, 11), Arrays.asList(11, 10))).isFalse();
        assertThat(UnitDetailDialog.sameEquippedIds(
                Arrays.asList(10), Arrays.asList(10, 11))).isFalse();
        assertThat(UnitDetailDialog.sameEquippedIds(
                Arrays.asList(10), Arrays.asList(12))).isFalse();
    }

    // —— feedback04：卸下按钮实例保留（修复每帧重建致 ClickListener 失焦点击失效） ——

    /** 装备模板夹具（白阶 +1 攻击；沿 EquipmentSystemTest 口径） */
    private static EquipmentData eq(String id, EquipmentSlot slot) {
        return new EquipmentData(id, "装" + id, slot, EquipmentRarity.WHITE,
                Arrays.asList(new EquipmentEffect(StatKey.ATTACK, EffectOp.ADD, 1f)), null);
    }

    /** 龙心镜像（equipments.json eq_dragon_heart：HP+400 + REGEN 0.02/5） */
    private static EquipmentData dragonHeart() {
        return new EquipmentData("eq_dragon_heart", "龙心", EquipmentSlot.ARMOR, EquipmentRarity.LEGENDARY,
                Arrays.asList(new EquipmentEffect(StatKey.HP, EffectOp.ADD, 400f)),
                new com.voidvvv.kz_auto_chess_n.data.EquipmentPassive(
                        com.voidvvv.kz_auto_chess_n.data.StatusType.REGEN, 0.02f, 5f));
    }

    /** 子节点快照（引用判等：Actor 未覆写 equals，containsExactly 即实例同一性） */
    private static Actor[] snapshotChildren(UnitDetailDialog dialog) {
        com.badlogic.gdx.utils.SnapshotArray<Actor> children = dialog.getChildren();
        Actor[] out = new Actor[children.size];
        for (int i = 0; i < children.size; i++) {
            out[i] = children.get(i);
        }
        return out;
    }

    @Test
    @DisplayName("装备 id 序列未变：再次 refresh 保留卸下按钮实例（touchDown/Up 命中同一 actor）")
    void unchangedEquippedIdsKeepButtonInstances() {
        Player player = new Player(10);
        Unit unit = new Unit(5, tpl("unit_a"), 1);
        player.addToBench(unit);
        unit.equip(new Equipment(10, eq("e_w", EquipmentSlot.WEAPON)));
        unit.equip(new Equipment(11, eq("e_a", EquipmentSlot.ARMOR)));
        RunContext ctx = context(player);
        UnitDetailDialog dialog = new UnitDetailDialog(new com.voidvvv.kz_auto_chess_n.command.CommandManager(), null,
                () -> ctx, null); // assets 仅 draw 路径使用：refresh 零 GL

        dialog.showUnit(5);
        dialog.refresh();
        Actor[] firstRefresh = snapshotChildren(dialog);
        assertThat(firstRefresh).hasSize(3); // 关闭按钮 + 两枚卸下按钮

        dialog.refresh(); // 序列未变 → 不重建
        assertThat(snapshotChildren(dialog)).containsExactly(firstRefresh);
    }

    @Test
    @DisplayName("卸下一件（id 序列变化）：按钮重建——数量随之减一且实例更换")
    void changedEquippedIdsRebuildButtons() {
        Player player = new Player(10);
        Unit unit = new Unit(5, tpl("unit_a"), 1);
        player.addToBench(unit);
        Equipment weapon = new Equipment(10, eq("e_w", EquipmentSlot.WEAPON));
        Equipment armor = new Equipment(11, eq("e_a", EquipmentSlot.ARMOR));
        unit.equip(weapon);
        unit.equip(armor);
        RunContext ctx = context(player);
        UnitDetailDialog dialog = new UnitDetailDialog(new com.voidvvv.kz_auto_chess_n.command.CommandManager(), null,
                () -> ctx, null);

        dialog.showUnit(5);
        dialog.refresh();
        Actor[] beforeUnequip = snapshotChildren(dialog);
        assertThat(beforeUnequip).hasSize(3);

        unit.unequip(armor); // 测试充当 systems 层（framework-internal 纪律的豁免主体，沿 BattleTestFixtures）
        dialog.refresh();
        Actor[] afterUnequip = snapshotChildren(dialog);
        assertThat(afterUnequip).hasSize(2); // 关闭按钮 + 一枚卸下按钮
        assertThat(afterUnequip[0]).isSameAs(beforeUnequip[0]); // 关闭按钮构造期一次、终身保留
        assertThat(afterUnequip[1]).isNotSameAs(beforeUnequip[1]); // 卸下按钮为重建新实例
    }

    @Test
    @DisplayName("弹窗换单位（showUnit 他 id）：按钮按新单位重建")
    void switchingUnitRebuildsButtons() {
        Player player = new Player(10);
        Unit unitA = new Unit(5, tpl("unit_a"), 1);
        Unit unitB = new Unit(6, tpl("unit_b"), 1);
        player.addToBench(unitA);
        player.addToBench(unitB);
        unitA.equip(new Equipment(10, eq("e_w", EquipmentSlot.WEAPON)));
        unitB.equip(new Equipment(12, eq("e_t", EquipmentSlot.TRINKET)));
        RunContext ctx = context(player);
        UnitDetailDialog dialog = new UnitDetailDialog(new com.voidvvv.kz_auto_chess_n.command.CommandManager(), null,
                () -> ctx, null);

        dialog.showUnit(5);
        dialog.refresh();
        Actor[] forUnitA = snapshotChildren(dialog);
        assertThat(forUnitA).hasSize(2);

        dialog.showUnit(6);
        dialog.refresh();
        Actor[] forUnitB = snapshotChildren(dialog);
        assertThat(forUnitB).hasSize(2);
        assertThat(forUnitB[0]).isSameAs(forUnitA[0]); // 关闭按钮不变
        assertThat(forUnitB[1]).isNotSameAs(forUnitA[1]); // 卸下按钮随单位重建
    }

    // —— feedback07：卸下按钮右侧效果列（effectSideLines 纯函数） ——

    @Test
    @DisplayName("效果列行集：龙心摘要折 12 列 × 截 2 行（贪心断点 = 恰好 12 列处换行）")
    void effectSideLinesDragonHeart() {
        assertThat(UnitDetailDialog.effectSideLines(new Equipment(30, dragonHeart())))
                .containsExactly("生命+400 · 被动：每 5 秒", "回复 2% 最大生命");
    }

    @Test
    @DisplayName("效果列行集：无效果无被动 = 空列表（不绘制）")
    void effectSideLinesEmptyTemplate() {
        EquipmentData empty = new EquipmentData("eq_x", "空", EquipmentSlot.WEAPON, EquipmentRarity.WHITE,
                java.util.Collections.<EquipmentEffect>emptyList(), null);
        assertThat(UnitDetailDialog.effectSideLines(new Equipment(31, empty))).isEmpty();
    }
}
